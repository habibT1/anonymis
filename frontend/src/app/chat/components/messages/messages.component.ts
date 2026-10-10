import {
  Component,
  ViewChild,
  ElementRef,
  AfterViewInit,
  ChangeDetectionStrategy,
  effect,
  input,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { Message } from '../../model/message.model';
import { getAvatarColor, getInitials } from 'src/app/shared/utils/avatar.util';
import { LanguageService } from 'src/app/shared/services/language.service';

@Component({
  selector: 'app-messages',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './messages.component.html',
  styleUrls: ['./messages.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MessagesComponent implements AfterViewInit {
  messages = input.required<Message[]>();
  currentUsername = input<string | null>(null);

  @ViewChild('viewport') private viewport!: ElementRef<HTMLDivElement>;

  private prevLen = 0;

  // === ajout: état d'aperçu d'image ===
  selectedImageUrl: string | null = null;

  constructor(protected i18n: LanguageService) {
    effect(() => {
      const list = this.messages() ?? [];
      queueMicrotask(() => this.scrollToBottomIfNeeded(list.length));
    });
  }

  ngAfterViewInit(): void {
    this.scrollToBottom(true);
    this.prevLen = this.messages()?.length ?? 0;
  }

  private scrollToBottomIfNeeded(currentLen: number): void {
    if (!this.viewport) return;

    const el = this.viewport.nativeElement;
    const nearBottom =
        el.scrollHeight - el.scrollTop - el.clientHeight < 80;

    if (currentLen > this.prevLen) {
      this.scrollToBottom(nearBottom);
      this.prevLen = currentLen;
    }
  }

  private scrollToBottom(immediate = false): void {
    const el = this.viewport?.nativeElement;
    if (!el) return;

    el.scrollTo({
      top: el.scrollHeight,
      behavior: immediate ? 'auto' : 'smooth',
    });
  }

  trackByMessageId(index: number, msg: Message): string | undefined {
    return msg.id;
  }

  isOwn(msg: Message): boolean {
    return !!this.currentUsername() && msg.username === this.currentUsername();
  }

  initials(username: string): string {
    return getInitials(username);
  }

  avatarColor(username: string): string {
    return getAvatarColor(username);
  }

  showDateHeader(list: Message[], i: number): boolean {
    if (!list || list.length === 0) return false;
    if (i === 0) return true;

    const prevDay = new Date(list[i - 1].timestamp).setHours(0, 0, 0, 0);
    const currDay = new Date(list[i].timestamp).setHours(0, 0, 0, 0);

    return prevDay !== currDay;
  }


  openPreview(url: string): void {
    this.selectedImageUrl = url;
  }


  closePreview(): void {
    this.selectedImageUrl = null;
  }
}