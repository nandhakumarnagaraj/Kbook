import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { TokenStorageService } from './token-storage.service';
import { AuthSession } from '../models/session.model';
import { environment } from '../../../environments/environment';

function session(role: string): AuthSession {
  return {
    token: 'jwt-token',
    restaurantId: 1,
    userName: 'Test',
    loginId: 'test@shop',
    userEmail: null,
    whatsappNumber: null,
    role,
  };
}

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let tokenStorage: jasmine.SpyObj<TokenStorageService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    tokenStorage = jasmine.createSpyObj<TokenStorageService>('TokenStorageService', [
      'save',
      'get',
      'clear',
      'getToken',
    ]);
    tokenStorage.get.and.returnValue(null);
    router = jasmine.createSpyObj<Router>('Router', ['navigate', 'navigateByUrl']);

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TokenStorageService, useValue: tokenStorage },
        { provide: Router, useValue: router },
      ],
    });

    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  describe('getLandingPath', () => {
    it('maps each role to its landing page', () => {
      expect(service.getLandingPath('KBOOK_ADMIN')).toBe('/admin/dashboard');
      expect(service.getLandingPath('OWNER')).toBe('/business/dashboard');
      expect(service.getLandingPath('SHOP_ADMIN')).toBe('/business/terminals');
    });

    it('falls back to /limited-access for unknown roles', () => {
      expect(service.getLandingPath('STAFF')).toBe('/limited-access');
      expect(service.getLandingPath(undefined)).toBe('/limited-access');
    });
  });

  describe('login', () => {
    it('saves the session, updates the signal, and routes by role', () => {
      const owner = session('OWNER');
      service.login({ loginId: 'test@shop', password: 'pw' }).subscribe();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`);
      expect(req.request.method).toBe('POST');
      req.flush(owner);

      expect(tokenStorage.save).toHaveBeenCalledWith(owner);
      expect(service.session()).toEqual(owner);
      expect(router.navigateByUrl).toHaveBeenCalledWith('/business/dashboard');
    });
  });

  describe('isAuthenticated', () => {
    it('is true only when a token is present', () => {
      tokenStorage.getToken.and.returnValue('tok');
      expect(service.isAuthenticated()).toBeTrue();
      tokenStorage.getToken.and.returnValue(null);
      expect(service.isAuthenticated()).toBeFalse();
    });
  });

  describe('logout', () => {
    it('clears storage, nulls the session, and navigates to /login', () => {
      tokenStorage.getToken.and.returnValue(null); // no revocation call
      service.logout();

      expect(tokenStorage.clear).toHaveBeenCalled();
      expect(service.session()).toBeNull();
      expect(router.navigate).toHaveBeenCalledWith(['/login']);
    });
  });
});
