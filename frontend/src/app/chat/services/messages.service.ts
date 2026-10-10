import { Injectable, Signal, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from 'src/environments/environment';
import { WebSocketService } from './websocket.service';

import { Message, NewMessageRequest } from '../model/message.model';

@Injectable({
  providedIn: 'root',
})
export class MessagesService {
  private readonly messagesSig = signal<Message[]>([]);

  constructor(
      private http: HttpClient,
      private ws: WebSocketService,
  ) {
    this.ws.notifications$.subscribe(async () => {
      const list = this.messagesSig();
      const lastId = list && list.length ? list[list.length - 1].id : undefined;
      try {
        await this.fetchMessages(lastId);
      } catch (error) {
        // Si le fromId n'existe pas (404), récupérer tous les messages
        if (error instanceof HttpErrorResponse && error.status === 404) {
          await this.fetchMessages();
        }
      }
    });
  }

  async publishMessage(payload: NewMessageRequest): Promise<void> {
    await firstValueFrom(
        this.http.post<Message>(
            `${environment.backendUrl}/messages`,
            payload,
            { withCredentials: true },
        ),
    );
  }

  async fetchMessages(fromId?: string): Promise<void> {
    const url =
        fromId != null
            ? `${environment.backendUrl}/messages?fromId=${encodeURIComponent(fromId)}`
            : `${environment.backendUrl}/messages`;

    const data = await firstValueFrom(
        this.http.get<Message[]>(url, { withCredentials: true }),
    );

    if (fromId != null) {
      this.messagesSig.update(list => [...list, ...data]);
    } else {
      this.messagesSig.set(data);
    }
  }

  allMessages(): Signal<Message[]> {
    return this.messagesSig;
  }
}