import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { TokenStorageService } from './token-storage.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const tokenStorage = inject(TokenStorageService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse) {
        switch (err.status) {
          case 0:
            return throwError(() => new Error('No internet connection. Please check your network.'));

          case 401:
            tokenStorage.clear();
            void router.navigate(['/login']);
            return throwError(() => new Error('Your session has expired. Please log in again.'));

          case 403:
            void router.navigate(['/limited-access']);
            return throwError(() => new Error('You do not have permission to perform this action.'));

          case 404:
            return throwError(() => new Error('The requested resource was not found.'));

          case 409:
            return throwError(() => new Error('A conflict occurred. Please refresh and try again.'));

          case 422:
            return throwError(() => err);

          default:
            if (err.status >= 500) {
              return throwError(() => new Error('A server error occurred. Please try again later.'));
            }
            return throwError(() => new Error(err.message || 'An unexpected error occurred.'));
        }
      }
      return throwError(() => err);
    })
  );
};
