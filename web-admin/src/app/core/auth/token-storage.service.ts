import { Injectable } from '@angular/core';
import { AuthSession } from '../models/session.model';

// Use sessionStorage instead of localStorage to reduce XSS attack surface.
// Tokens are cleared automatically when the browser/tab closes.
const STORAGE_KEY = 'khanabook.webAdmin.v1.session';
const REFRESH_KEY = 'khanabook.webAdmin.v1.refresh';

@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  save(session: AuthSession): void {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  }

  get(): AuthSession | null {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as AuthSession;
    } catch {
      sessionStorage.removeItem(STORAGE_KEY);
      return null;
    }
  }

  clear(): void {
    sessionStorage.removeItem(STORAGE_KEY);
  }

  getToken(): string | null {
    return this.get()?.token ?? null;
  }

  // Refresh token is kept separately in the same sessionStorage (same XSS
  // posture as the access JWT): it is only stored so /auth/logout can hand
  // it back for server-side revocation. It is never used to refresh.
  saveRefreshToken(refreshToken: string): void {
    sessionStorage.setItem(REFRESH_KEY, refreshToken);
  }

  getRefreshToken(): string | null {
    return sessionStorage.getItem(REFRESH_KEY);
  }

  clearRefreshToken(): void {
    sessionStorage.removeItem(REFRESH_KEY);
  }
}
