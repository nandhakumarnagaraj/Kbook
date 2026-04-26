import { Injectable } from '@angular/core';
import { AuthSession } from '../models/session.model';

const STORAGE_KEY = 'khanabook.webAdmin.session';

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
}
