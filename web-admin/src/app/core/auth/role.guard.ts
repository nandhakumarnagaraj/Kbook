import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  if (authService.isAuthenticated()) {
    return true;
  }
  void router.navigate(['/login']);
  return false;
};

export const roleGuard: CanActivateFn = (route: ActivatedRouteSnapshot) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const allowedRoles = route.data['roles'] as string[] | undefined;
  const session = authService.session();

  if (!session) {
    void router.navigate(['/login']);
    return false;
  }

  if (!allowedRoles || allowedRoles.includes(session.role)) {
    return true;
  }

  authService.navigateByRole(session.role);
  return false;
};
