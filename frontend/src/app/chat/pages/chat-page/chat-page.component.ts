import { Component, OnInit, OnDestroy, HostListener, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AuthenticationService } from 'src/app/login/services/authentication.service';
import { MessagesService } from '../../services/messages.service';
import { WebSocketService } from '../../services/websocket.service';

import { MessagesComponent } from '../../components/messages/messages.component';
import { MessageFormComponent } from '../../components/message-form/message-form.component';
import { UserListComponent } from 'src/app/status/components/user-list/user-list.component';

import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';

import { NewMessageRequest } from '../../model/message.model';
import { HttpErrorResponse } from '@angular/common/http';
import { UserStatusService } from 'src/app/status/services/user-status.service';
import { LanguageService } from 'src/app/shared/services/language.service';

@Component({
  selector: 'app-chat-page',
  standalone: true,
  imports: [
    MessagesComponent,
    MessageFormComponent,
    UserListComponent,
    MatButtonModule,
    MatIconModule,
    MatToolbarModule,
  ],
  templateUrl: './chat-page.component.html',
  styleUrls: ['./chat-page.component.scss'],
})
export class ChatPageComponent implements OnInit, OnDestroy {

  messages = this.messagesService.allMessages();
  username = this.authenticationService.getUsername();
  sidebarOpen = signal(false);

  constructor(
      private messagesService: MessagesService,
      private authenticationService: AuthenticationService,
      private websocketService: WebSocketService,
      private userStatusService: UserStatusService,
      private router: Router,
      protected i18n: LanguageService,
  ) {}

  async ngOnInit() {
    this.websocketService.connect();
    try {
      await this.messagesService.fetchMessages();
      await this.userStatusService.updateMyStatus('online');
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 403) {
        await this.handleSessionExpired();
      }
    }
  }

  ngOnDestroy(): void {
    this.websocketService.disconnect();
    // Note: updateMyStatus('offline') is called in onLogout() before the JWT is invalidated
    // Calling it here would fail with 403 because the session is already terminated
  }

  @HostListener('document:visibilitychange')
  onVisibilityChange() {
    if (document.hidden) {
      this.userStatusService.updateMyStatus('away');
    } else {
      this.userStatusService.updateMyStatus('online');
    }
  }

  @HostListener('window:beforeunload')
  onBeforeUnload() {
    // Use sendBeacon to reliably send status update even when page is closing
    // sendBeacon is specifically designed to work during page unload
    const url = `${window.location.origin}/status`;
    const data = JSON.stringify({ status: 'offline' });
    navigator.sendBeacon(url, new Blob([data], { type: 'application/json' }));
  }


  toggleSidebar(): void {
    this.sidebarOpen.update((open) => !open);
  }

  closeSidebar(): void {
    this.sidebarOpen.set(false);
  }

  async onPublish(payload: { text: string; imageData: NewMessageRequest['imageData'] }) {
    const u = this.username();
    if (!u) return;

    const cleaned = payload.text?.trim() ?? '';
    const img = payload.imageData ?? null;

    if (!cleaned && !img) {
      return;
    }

    const request: NewMessageRequest = {
      text: cleaned,
      username: u,
      imageData: img,
    };

    try {
      await this.messagesService.publishMessage(request);
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 403) {
        await this.handleSessionExpired();
      }
    }
  }

  private async handleSessionExpired(): Promise<void> {
    await this.authenticationService.logout();
    await this.router.navigate(['/']);
  }

  async onLogout(): Promise<void> {
    await this.userStatusService.updateMyStatus('offline');
    await this.authenticationService.logout();
    this.router.navigate(['/login']);
  }
}