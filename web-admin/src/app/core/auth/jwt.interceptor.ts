import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { TokenStorageService } from './token-storage.service';

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
  const token = tokenStorage.getToken();

  // Attach auth token + app version headers
  const authReq = req.clone({
    setHeaders: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      'X-App-Version': '1',        // increment this on each release
      'X-App-Platform': 'web-admin',
    },
  });

  return next(authReq).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse) {
        switch (err.status) {
          case 0:
            console.error('[KhanaBook] Network error — no internet connection', err);
            // Propagate a friendly message components can display
            return throwError(() => new Error('No internet connection. Please check your network.'));

          case 401:
            tokenStorage.clear();
            void router.navigate(['/login']);
            return throwError(() => new Error('Your session has expired. Please log in again.'));

          case 403:
            return throwError(() => new Error('You do not have permission to perform this action.'));

          case 404:
            return throwError(() => new Error('The requested resource was not found.'));

          case 409:
            return throwError(() => new Error('A conflict occurred. Please refresh and try again.'));

          default:
            if (err.status >= 500) {
              console.error('[KhanaBook] Server error', err);
              return throwError(() => new Error('A server error occurred. Please try again later.'));
            }
            return throwError(() => new Error(err.message || 'An unexpected error occurred.'));
        }
      }
      return throwError(() => err);
    })
  );
};
