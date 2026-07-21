import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { AuthSession, LoginRequest } from '../models/session.model';
import { TokenStorageService } from './token-storage.service';
import { environment } from '../../../environments/environment';

const API_BASE_URL = environment.apiBaseUrl;

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly tokenStorage = inject(TokenStorageService);

  readonly session = signal<AuthSession | null>(this.tokenStorage.get());

  login(payload: LoginRequest) {
    return this.http.post<AuthSession>(`${API_BASE_URL}/auth/login`, payload).pipe(
      tap((session) => {
        this.tokenStorage.save(session);
        this.session.set(session);
      }),
      map((session) => {
        this.navigateByRole(session.role);
        return session;
      })
    );
  }

  googleLogin(idToken: string) {
    return this.http.post<AuthSession>(`${API_BASE_URL}/auth/google`, { idToken }).pipe(
      tap((session) => {
        this.tokenStorage.save(session);
        this.session.set(session);
      }),
      map((session) => {
        this.navigateByRole(session.role);
        return session;
      })
    );
  }

  logout(): void {
    const token = this.tokenStorage.getToken();
    if (token) {
      this.http.post(`${API_BASE_URL}/auth/logout`, {}).subscribe({
        error: (err) => console.warn('Logout revocation call failed', err)
      });
    }
    this.tokenStorage.clear();
    this.session.set(null);
    void this.router.navigate(['/login']);
  }

  isAuthenticated(): boolean {
    return !!this.tokenStorage.getToken();
  }

  getLandingPath(role = this.session()?.role): string {
    if (role === 'KBOOK_ADMIN') return '/admin/dashboard';
    if (role === 'OWNER') return '/business/dashboard';
    if (role === 'SHOP_ADMIN') return '/business/terminals';
    return '/limited-access';
  }

  navigateByRole(role: string): void {
    void this.router.navigateByUrl(this.getLandingPath(role));
  }

  requestPasswordOtp(phone: string): Observable<void> {
    return this.http.post<void>(`${API_BASE_URL}/auth/forgot-password/request-otp`, { phone });
  }

  verifyPasswordOtp(phone: string, otp: string): Observable<{ tempToken: string }> {
    return this.http.post<{ tempToken: string }>(`${API_BASE_URL}/auth/forgot-password/verify-otp`, { phone, otp });
  }

  resetPassword(tempToken: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${API_BASE_URL}/auth/forgot-password/reset-password`, { tempToken, newPassword });
  }
}
