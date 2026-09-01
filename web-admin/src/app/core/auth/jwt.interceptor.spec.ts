import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { jwtInterceptor } from './jwt.interceptor';
import { TokenStorageService } from './token-storage.service';
import { ToastService } from '../services/toast.service';
import { environment } from '../../../environments/environment';

/**
 * The interceptor is the single choke point for auth headers and global HTTP
 * error handling. Regressions here silently break every API call, so these
 * tests pin both behaviours.
 */
describe('jwtInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let tokenStorage: jasmine.SpyObj<TokenStorageService>;
  let router: jasmine.SpyObj<Router>;
  let toast: jasmine.SpyObj<ToastService>;

  const URL = '/api/thing';

  beforeEach(() => {
    tokenStorage = jasmine.createSpyObj<TokenStorageService>('TokenStorageService', [
      'getToken',
      'clear',
    ]);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['show']);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([jwtInterceptor])),
        provideHttpClientTesting(),
        { provide: TokenStorageService, useValue: tokenStorage },
        { provide: Router, useValue: router },
        { provide: ToastService, useValue: toast },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('attaches Authorization + app headers when a token exists', () => {
    tokenStorage.getToken.and.returnValue('abc123');
    http.get(URL).subscribe();

    const req = httpMock.expectOne(URL);
    expect(req.request.headers.get('Authorization')).toBe('Bearer abc123');
    expect(req.request.headers.get('X-App-Version')).toBe(environment.appVersion);
    expect(req.request.headers.get('X-App-Platform')).toBe('web-admin');
    req.flush({});
  });

  it('omits Authorization when there is no token', () => {
    tokenStorage.getToken.and.returnValue(null);
    http.get(URL).subscribe();

    const req = httpMock.expectOne(URL);
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('on 401 clears session and redirects to /login', () => {
    tokenStorage.getToken.and.returnValue('tok');
    http.get(URL).subscribe({ error: () => {} });

    httpMock.expectOne(URL).flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(tokenStorage.clear).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('on 403 shows a permission-denied toast', () => {
    tokenStorage.getToken.and.returnValue('tok');
    http.get(URL).subscribe({ error: () => {} });

    httpMock
      .expectOne(URL)
      .flush({ message: 'nope' }, { status: 403, statusText: 'Forbidden' });

    expect(toast.show).toHaveBeenCalledWith('nope', 'error');
  });

  it('on 403 BUSINESS_SUSPENDED does NOT toast (handled elsewhere)', () => {
    tokenStorage.getToken.and.returnValue('tok');
    http.get(URL).subscribe({ error: () => {} });

    httpMock
      .expectOne(URL)
      .flush({ error: 'BUSINESS_SUSPENDED' }, { status: 403, statusText: 'Forbidden' });

    expect(toast.show).not.toHaveBeenCalled();
  });

  it('propagates the error to the caller', () => {
    tokenStorage.getToken.and.returnValue('tok');
    let errored = false;
    http.get(URL).subscribe({ error: () => (errored = true) });

    httpMock.expectOne(URL).flush({}, { status: 500, statusText: 'Server Error' });
    expect(errored).toBeTrue();
  });
});
