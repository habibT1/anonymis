import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';
import { environment } from 'src/environments/environment';

export type WebSocketEvent = 'notif';

@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private socket: WebSocket | null = null;
  private reconnectTimeout: any = null;
  private shouldReconnect = false;
  private readonly RECONNECT_DELAY = 2000;

  private notifSubject = new Subject<WebSocketEvent>();
  readonly notifications$ = this.notifSubject.asObservable();

  connect(): void {
    if (
        this.socket &&
        (this.socket.readyState === WebSocket.OPEN ||
            this.socket.readyState === WebSocket.CONNECTING)
    ) {
      return;
    }

    this.shouldReconnect = true;
    this.attemptConnection();
  }

  private attemptConnection(): void {
    const base = environment.wsUrl ?? `ws://${location.hostname}:8080`;
    const wsUrl = base.endsWith('/notifications')
        ? base
        : `${base}/notifications`;

    this.socket = new WebSocket(wsUrl);

    this.socket.onopen = () => {
      // Connexion établie
    };

    this.socket.onmessage = (ev) => {
      this.notifSubject.next('notif');
    };

    this.socket.onclose = () => {
      this.socket = null;

      if (this.shouldReconnect) {
        this.scheduleReconnect();
      }
    };

    this.socket.onerror = () => {
      // Erreur silencieuse
    };
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
    }

    this.reconnectTimeout = setTimeout(() => {
      this.attemptConnection();
    }, this.RECONNECT_DELAY);
  }

  disconnect(): void {
    this.shouldReconnect = false;

    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = null;
    }

    if (this.socket) {
      this.socket.close();
    }
    this.socket = null;
  }
}