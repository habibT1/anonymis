import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { UserStatusService } from '../../services/user-status.service';
import { UserStatus, UserStatusType } from '../../model/user-status.model';
import { getAvatarColor, getInitials } from 'src/app/shared/utils/avatar.util';
import { LanguageService } from 'src/app/shared/services/language.service';

/**
 * Composant qui affiche la liste des utilisateurs avec leur statut.
 */
@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './user-list.component.html',
  styleUrls: ['./user-list.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UserListComponent implements OnInit {
  statuses = this.userStatusService.allStatuses();

  constructor(
    private userStatusService: UserStatusService,
    protected i18n: LanguageService,
  ) {}

  async ngOnInit() {
    await this.userStatusService.fetchStatuses();
  }

  /**
   * Retourne la classe CSS pour la pastille de statut.
   */
  getStatusClass(status: UserStatusType): string {
    switch (status) {
      case 'online':
        return 'status-online';
      case 'away':
        return 'status-away';
      case 'offline':
        return 'status-offline';
      default:
        return 'status-offline';
    }
  }

  /**
   * Track by pour optimiser le rendu de la liste.
   */
  trackByUsername(index: number, status: UserStatus): string {
    return status.username;
  }

  initials(username: string): string {
    return getInitials(username);
  }

  avatarColor(username: string): string {
    return getAvatarColor(username);
  }
}
