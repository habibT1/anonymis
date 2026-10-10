import { Injectable, Signal, signal } from '@angular/core';
import { UserCredentials } from '../model/user-credentials';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from 'src/environments/environment';

export interface LoginResponse { username: string; }

@Injectable({
  providedIn: 'root',
})
export class AuthenticationService {
  static USERNAME_KEY = 'username';

  private username = signal<string | null>(null);

  constructor(private http: HttpClient) {
    this.username.set(localStorage.getItem(AuthenticationService.USERNAME_KEY));
  }

  async login(userCredentials: UserCredentials): Promise<LoginResponse> {
    const res = await firstValueFrom(
      this.http.post<LoginResponse>(
        `${environment.backendUrl}/auth/login`,
        userCredentials,
        { withCredentials: true },
      ),
    );

    const u = (res?.username ?? userCredentials.username).trim();
    localStorage.setItem(AuthenticationService.USERNAME_KEY, u);
    this.username.set(u);

    return res;
  }

  async logout(): Promise<void> {
    await firstValueFrom(
      this.http.post<void>(
        `${environment.backendUrl}/auth/logout`,
        {},
        { withCredentials: true },
      ),
    );

    localStorage.removeItem(AuthenticationService.USERNAME_KEY);
    this.username.set(null);
  }

  getUsername(): Signal<string | null> {
    return this.username;
  }

  isConnected(): boolean {
    return localStorage.getItem(AuthenticationService.USERNAME_KEY) !== null;
  }
}
