/**
 * Type de statut utilisateur
 */
export type UserStatusType = 'online' | 'offline' | 'away';

/**
 * Représente le statut d'un utilisateur
 */
export interface UserStatus {
  username: string;
  status: UserStatusType;
  lastUpdate: number;
}

/**
 * Requête pour mettre à jour le statut
 */
export interface UpdateStatusRequest {
  status: UserStatusType;
}
