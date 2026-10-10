import { Injectable, computed, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from 'src/environments/environment';

import {
  AskRequest,
  AskResponse,
  AssistantInfo,
  DetectionOptions,
  DocumentSummary,
  Exchange,
  MaskLevel,
} from '../model/assistant.model';

const WATCH_KEY = 'anonymis-watchlist';
const ALLOW_KEY = 'anonymis-allowlist';

function readList<T>(key: string): T[] {
  try {
    const v = JSON.parse(localStorage.getItem(key) ?? '[]');
    return Array.isArray(v) ? v : [];
  } catch {
    return [];
  }
}

function writeList(key: string, value: unknown[]): void {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // stockage indisponible (navigation privée) : la liste reste valable pour cette session
  }
}

/** Erreur 403 : la session a expiré, la page doit renvoyer vers la connexion. */
export class SessionExpiredError extends Error {}

@Injectable({ providedIn: 'root' })
export class AssistantService {
  private readonly api = environment.backendUrl;

  readonly documents = signal<DocumentSummary[]>([]);
  readonly selectedIds = signal<Set<string>>(new Set());
  readonly exchanges = signal<Exchange[]>([]);
  readonly conversationId = signal<string | null>(null);
  readonly level = signal<MaskLevel>('standard');
  readonly uploading = signal(false);
  readonly busy = computed(() => this.exchanges().some((e) => e.status === 'pending'));
  readonly info = signal<AssistantInfo | null>(null);
  /** Noms métier à toujours masquer, et termes à ne jamais masquer (gardés dans ce navigateur). */
  readonly watchlist = signal<{ term: string; category: string }[]>(readList(WATCH_KEY));
  readonly allowlist = signal<string[]>(readList(ALLOW_KEY));

  constructor(private http: HttpClient) {}

  private call<T>(request: Promise<T>): Promise<T> {
    return request.catch((e) => {
      if (e instanceof HttpErrorResponse && e.status === 403) {
        throw new SessionExpiredError();
      }
      throw e;
    });
  }

  async loadInfo(): Promise<void> {
    try {
      this.info.set(await firstValueFrom(this.http.get<AssistantInfo>(`${this.api}/assistant/info`)));
    } catch {
      this.info.set(null);
    }
  }

  async loadDocuments(): Promise<void> {
    const docs = await this.call(
      firstValueFrom(this.http.get<DocumentSummary[]>(`${this.api}/documents`, { withCredentials: true })),
    );
    this.documents.set(docs);
  }

  async upload(file: File): Promise<void> {
    const form = new FormData();
    form.append('file', file);
    this.uploading.set(true);
    try {
      const doc = await this.call(
        firstValueFrom(this.http.post<DocumentSummary>(`${this.api}/documents`, form, { withCredentials: true })),
      );
      this.documents.update((list) => [...list, doc]);
      this.selectedIds.update((s) => new Set(s).add(doc.id));
    } finally {
      this.uploading.set(false);
    }
  }

  async remove(id: string): Promise<void> {
    await this.call(firstValueFrom(this.http.delete(`${this.api}/documents/${id}`, { withCredentials: true })));
    this.documents.update((list) => list.filter((d) => d.id !== id));
    this.selectedIds.update((s) => {
      const next = new Set(s);
      next.delete(id);
      return next;
    });
  }

  /** PDF caviardé, avec les mêmes jetons que la conversation en cours. */
  async downloadRedacted(doc: DocumentSummary): Promise<void> {
    const params: Record<string, string> = { level: this.level() };
    const conv = this.conversationId();
    if (conv) params['conversationId'] = conv;
    const blob = await this.call(
      firstValueFrom(
        this.http.get(`${this.api}/documents/${doc.id}/redacted`, {
          params,
          responseType: 'blob',
          withCredentials: true,
        }),
      ),
    );
    saveBlob(blob, doc.filename.replace(/\.pdf$/i, '') + '-caviarde.pdf');
  }

  /** Réponse de l'assistant (avec les vrais noms) en PDF, générée sur le serveur, sans passer par l'IA. */
  async exportAnswer(question: string, answer: string): Promise<void> {
    const blob = await this.call(
      firstValueFrom(
        this.http.post(
          `${this.api}/export/pdf`,
          { title: question, text: answer },
          { responseType: 'blob', withCredentials: true },
        ),
      ),
    );
    saveBlob(blob, 'anonymis-reponse.pdf');
  }

  toggleSelected(id: string): void {
    this.selectedIds.update((s) => {
      const next = new Set(s);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  /** Nouvelle conversation : nouvelle table de correspondance côté serveur. */
  reset(): void {
    this.exchanges.set([]);
    this.conversationId.set(null);
  }

  /** Les listes sont fixées pour la conversation côté serveur : les changer en démarre une nouvelle. */
  setLists(watchlist: { term: string; category: string }[], allowlist: string[]): void {
    this.watchlist.set(watchlist);
    this.allowlist.set(allowlist);
    writeList(WATCH_KEY, watchlist);
    writeList(ALLOW_KEY, allowlist);
    this.reset();
  }

  setLevel(level: MaskLevel): void {
    // Le niveau est fixé pour toute la conversation côté serveur.
    if (level !== this.level()) {
      this.level.set(level);
      this.reset();
    }
  }

  async ask(question: string, dryRun: boolean): Promise<void> {
    const options: DetectionOptions = {
      level: this.level(),
      watchlist: this.watchlist(),
      allowlist: this.allowlist(),
    };
    const request: AskRequest = {
      conversationId: this.conversationId(),
      question,
      documentIds: [...this.selectedIds()],
      options,
      dryRun,
    };
    const index = this.exchanges().length;
    this.exchanges.update((list) => [...list, { question, status: 'pending' }]);

    const update = (patch: Partial<Exchange>) =>
      this.exchanges.update((list) => list.map((e, i) => (i === index ? { ...e, ...patch } : e)));

    try {
      const response = await this.call(
        firstValueFrom(this.http.post<AskResponse>(`${this.api}/assistant/ask`, request, { withCredentials: true })),
      );
      this.conversationId.set(response.conversationId);
      update({ status: dryRun ? 'preview' : 'done', response });
    } catch (e) {
      if (e instanceof SessionExpiredError) {
        throw e;
      }
      if (e instanceof HttpErrorResponse && e.status === 422) {
        update({ status: 'blocked', blockedTypes: e.error?.types ?? [], error: e.error?.message });
      } else if (e instanceof HttpErrorResponse) {
        update({ status: 'error', error: e.error?.message ?? `HTTP ${e.status}` });
      } else {
        update({ status: 'error', error: String(e) });
      }
    }
  }
}

function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
