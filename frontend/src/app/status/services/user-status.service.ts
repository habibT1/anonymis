import { Injectable, Signal, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from 'src/environments/environment';
import { WebSocketService } from 'src/app/chat/services/websocket.service';

import { UserStatus, UserStatusType, UpdateStatusRequest } from '../model/user-status.model';

/**
 * Service pour gérer les statuts des utilisateurs.
 */
@Injectable({
  providedIn: 'root',
})
export class UserStatusService {
  private readonly statusesSig = signal<UserStatus[]>([]);

  constructor(
      private http: HttpClient,
      private ws: WebSocketService,
  ) {
    this.ws.notifications$.subscribe(() => {
      this.fetchStatuses();
    });
  }

  /**
   * Récupère tous les statuts des utilisateurs.
   */
  async fetchStatuses(): Promise<void> {
    try {
      const statuses = await firstValueFrom(
          this.http.get<UserStatus[]>(
              `${environment.backendUrl}/status`,
              { withCredentials: true }
          )
      );
      this.statusesSig.set(statuses);
    } catch (error) {
      // Erreur silencieuse
    }
  }

  /**
   * Met à jour le statut de l'utilisateur connecté.
   */
  async updateMyStatus(status: UserStatusType): Promise<void> {
    try {
      const request: UpdateStatusRequest = { status };
      await firstValueFrom(
          this.http.put<UserStatus>(
              `${environment.backendUrl}/status`,
              request,
              { withCredentials: true }
          )
      );
    } catch (error) {
      // Erreur silencieuse
    }
  }

  /**
   * Retourne un signal contenant tous les statuts.
   */
  allStatuses(): Signal<UserStatus[]> {
    return this.statusesSig;
  }
}
