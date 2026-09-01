import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, UrlTree } from '@angular/router';
import { runInInjectionContext, Injector } from '@angular/core';
import { authGuard, roleGuard } from './role.guard';
import { AuthService } from './auth.service';

/**
 * Authorization is the highest-risk logic in web-admin: a bug here lets the
 * wrong role reach a page it should not. These tests pin the guard behaviour.
 */
describe('auth guards', () => {
  let injector: Injector;
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  // Sentinel UrlTrees so we can assert *which* redirect was chosen.
  const loginTree = { toString: () => '/login' } as unknown as UrlTree;
  const landingTree = { toString: () => '/landing' } as unknown as UrlTree;

  beforeEach(() => {
    authService = jasmine.createSpyObj<AuthService>('AuthService', [
      'isAuthenticated',
      'session',
      'getLandingPath',
    ]);
    router = jasmine.createSpyObj<Router>('Router', ['parseUrl']);

    router.parseUrl.and.callFake((url: string) =>
      url === '/login' ? loginTree : landingTree
    );

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
      ],
    });
    injector = TestBed.inject(Injector);
  });

  function makeRoute(roles?: string[]): ActivatedRouteSnapshot {
    return { data: roles ? { roles } : {} } as unknown as ActivatedRouteSnapshot;
  }

  describe('authGuard', () => {
    it('allows an authenticated user', () => {
      authService.isAuthenticated.and.returnValue(true);
      const result = runInInjectionContext(injector, () =>
        authGuard(makeRoute(), {} as any)
      );
      expect(result).toBe(true);
    });

    it('redirects an unauthenticated user to /login', () => {
      authService.isAuthenticated.and.returnValue(false);
      const result = runInInjectionContext(injector, () =>
        authGuard(makeRoute(), {} as any)
      );
      expect(result).toBe(loginTree);
      expect(router.parseUrl).toHaveBeenCalledWith('/login');
    });
  });

  describe('roleGuard', () => {
    it('redirects to /login when there is no session', () => {
      authService.session.and.returnValue(null);
      const result = runInInjectionContext(injector, () =>
        roleGuard(makeRoute(['OWNER']), {} as any)
      );
      expect(result).toBe(loginTree);
    });

    it('allows access when the route has no role restriction', () => {
      authService.session.and.returnValue({ role: 'OWNER' } as any);
      const result = runInInjectionContext(injector, () =>
        roleGuard(makeRoute(undefined), {} as any)
      );
      expect(result).toBe(true);
    });

    it('allows access when the session role is in the allowed list', () => {
      authService.session.and.returnValue({ role: 'SHOP_ADMIN' } as any);
      const result = runInInjectionContext(injector, () =>
        roleGuard(makeRoute(['OWNER', 'SHOP_ADMIN']), {} as any)
      );
      expect(result).toBe(true);
    });

    it('redirects to the role landing page when the role is NOT allowed', () => {
      authService.session.and.returnValue({ role: 'SHOP_ADMIN' } as any);
      authService.getLandingPath.and.returnValue('/business/terminals');
      const result = runInInjectionContext(injector, () =>
        roleGuard(makeRoute(['OWNER']), {} as any)
      );
      expect(result).toBe(landingTree);
      expect(authService.getLandingPath).toHaveBeenCalledWith('SHOP_ADMIN');
      expect(router.parseUrl).toHaveBeenCalledWith('/business/terminals');
    });
  });
});
