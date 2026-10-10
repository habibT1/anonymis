import { Component, ElementRef, OnInit, ViewChild, effect, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { KeyValuePipe } from '@angular/common';

import { AuthenticationService } from 'src/app/login/services/authentication.service';
import { LanguageService } from 'src/app/shared/services/language.service';
import { AssistantService, SessionExpiredError } from '../../services/assistant.service';
import { TokenTextComponent } from '../../components/token-text/token-text.component';
import { DocumentSummary, Exchange, MaskLevel } from '../../model/assistant.model';
import { plainText } from '../../utils/tokens.util';

@Component({
  selector: 'app-assistant-page',
  standalone: true,
  imports: [FormsModule, KeyValuePipe, TokenTextComponent],
  templateUrl: './assistant-page.component.html',
  styleUrls: ['./assistant-page.component.scss'],
})
export class AssistantPageComponent implements OnInit {
  @ViewChild('thread') private thread?: ElementRef<HTMLDivElement>;
  @ViewChild('fileInput') private fileInput?: ElementRef<HTMLInputElement>;

  username = this.auth.getUsername();
  question = '';
  dragOver = signal(false);
  uploadError = signal<string | null>(null);
  openAiViews = signal<Set<number>>(new Set());
  sidebarOpen = signal(false);
  listsOpen = signal(false);
  watchText = '';
  allowText = '';

  constructor(
    protected assistant: AssistantService,
    protected i18n: LanguageService,
    private auth: AuthenticationService,
    private router: Router,
  ) {
    // Fait défiler vers le dernier échange.
    effect(() => {
      this.assistant.exchanges();
      queueMicrotask(() => {
        const el = this.thread?.nativeElement;
        if (el) el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
      });
    });
  }

  async ngOnInit(): Promise<void> {
    await this.guard(async () => {
      await Promise.all([this.assistant.loadDocuments(), this.assistant.loadInfo()]);
    });
  }

  private async guard(action: () => Promise<void>): Promise<void> {
    try {
      await action();
    } catch (e) {
      if (e instanceof SessionExpiredError) {
        await this.auth.logout().catch(() => undefined);
        await this.router.navigate(['/']);
        return;
      }
      throw e;
    }
  }

  // --- Documents ---

  pickFile(): void {
    this.fileInput?.nativeElement.click();
  }

  async onFiles(files: FileList | null | undefined): Promise<void> {
    if (!files) return;
    this.uploadError.set(null);
    for (const file of Array.from(files)) {
      if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) {
        this.uploadError.set(`${file.name} : PDF uniquement`);
        continue;
      }
      await this.guard(async () => {
        try {
          await this.assistant.upload(file);
        } catch (e) {
          if (e instanceof SessionExpiredError) throw e;
          this.uploadError.set(`${file.name} : import impossible`);
        }
      });
    }
    if (this.fileInput) this.fileInput.nativeElement.value = '';
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
    this.onFiles(event.dataTransfer?.files);
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(true);
  }

  async remove(id: string): Promise<void> {
    await this.guard(() => this.assistant.remove(id));
  }

  async downloadRedacted(doc: DocumentSummary): Promise<void> {
    this.uploadError.set(null);
    await this.guard(async () => {
      try {
        await this.assistant.downloadRedacted(doc);
      } catch (e) {
        if (e instanceof SessionExpiredError) throw e;
        this.uploadError.set(`${doc.filename} : caviardage impossible`);
      }
    });
  }

  async exportAnswer(x: Exchange): Promise<void> {
    if (!x.response?.answer) return;
    await this.guard(() => this.assistant.exportAnswer(x.question, plainText(x.response!.answer)));
  }

  // --- Noms sensibles ---

  openLists(): void {
    this.watchText = this.assistant.watchlist().map((w) => `${w.term} : ${w.category}`).join('\n');
    this.allowText = this.assistant.allowlist().join('\n');
    this.listsOpen.set(true);
  }

  saveLists(): void {
    // Une ligne par terme ; « Projet Faucon : PROJET » pour choisir la catégorie (TERME_SENSIBLE par défaut).
    const watch = this.watchText.split('\n').map((l) => l.trim()).filter(Boolean).map((line) => {
      const i = line.lastIndexOf(':');
      const term = (i > 0 ? line.slice(0, i) : line).trim();
      const raw = i > 0 ? line.slice(i + 1).trim() : '';
      const category = raw.toUpperCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^A-Z0-9_]/g, '_').replace(/_+/g, '_') || 'TERME_SENSIBLE';
      return { term, category };
    }).filter((w) => w.term);
    const allow = this.allowText.split('\n').map((l) => l.trim()).filter(Boolean);
    this.assistant.setLists(watch, allow);
    this.openAiViews.set(new Set());
    this.listsOpen.set(false);
  }

  // --- Conversation ---

  async send(dryRun: boolean): Promise<void> {
    const q = this.question.trim();
    if (!q || this.assistant.busy()) return;
    this.question = '';
    await this.guard(() => this.assistant.ask(q, dryRun));
    if (dryRun) {
      // En aperçu, on ouvre directement la Vue IA du dernier échange.
      this.openAiViews.update((s) => new Set(s).add(this.assistant.exchanges().length - 1));
    }
  }

  async confirmPreview(index: number, exchange: Exchange): Promise<void> {
    // Retire l'aperçu et envoie la même question pour de vrai.
    this.assistant.exchanges.update((list) => list.filter((_, i) => i !== index));
    await this.guard(() => this.assistant.ask(exchange.question, false));
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send(false);
    }
  }

  toggleAiView(index: number): void {
    this.openAiViews.update((s) => {
      const next = new Set(s);
      if (next.has(index)) {
        next.delete(index);
      } else {
        next.add(index);
      }
      return next;
    });
  }

  setLevel(level: MaskLevel): void {
    this.assistant.setLevel(level);
    this.openAiViews.set(new Set());
  }

  newConversation(): void {
    this.assistant.reset();
    this.openAiViews.set(new Set());
  }

  async logout(): Promise<void> {
    await this.auth.logout().catch(() => undefined);
    await this.router.navigate(['/']);
  }
}
