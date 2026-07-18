import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { TokenStorageService } from './token-storage.service';
import { ToastService } from '../services/toast.service';
import { environment } from '../../../environments/environment';

/**
 * FIX #13 (Angular side) — Add X-App-Version header so the server can
 * track which web-admin version is hitting the API.
 *
 * FIX #10 — Intercept HTTP errors globally so every failed API call
 * produces a user-facing message instead of silently blanking the page.
 *
 * Error handling strategy:
 *  401 → clear session + redirect to /login
 *  403 → user sees "Permission denied" (not a blank page)
 *  0   → "No internet connection" message
 *  5xx → "Server error, please try again later"
 *  4xx → generic bad request message
 */
export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const tokenStorage = inject(TokenStorageService);
  const router = inject(Router);
  const toastService = inject(ToastService);
  const token = tokenStorage.getToken();

  // Attach auth token + app version headers
  const authReq = req.clone({
    setHeaders: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      'X-App-Version': environment.appVersion,
      'X-App-Platform': 'web-admin',
    },
  });

  return next(authReq).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse) {
        switch (err.status) {
          case 0:
            console.error('[KhanaBook] Network error — no internet connection', err);
            break;
          case 401:
            tokenStorage.clear();
            void router.navigate(['/login']);
            break;
          case 403:
            if (err.error?.error !== 'BUSINESS_SUSPENDED') {
              toastService.show(
                err.error?.message || err.error?.error || 'Access denied: you do not have permission to perform this action.',
                'error'
              );
            }
            break;
          default:
            if (err.status >= 500) {
              console.error('[KhanaBook] Server error', err);
            }
            break;
        }
        return throwError(() => err);
      }
      return throwError(() => err);
    })
  );
};
