import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HEARTBEAT_INTERVAL_MS, PresenceService } from './presence.service';
import { environment } from '../../../environments/environment';

/**
 * The dot exists to be a measurement, not decoration, so these tests pin the
 * things that would quietly turn it back into a permanent green light: that it
 * starts as unknown, follows the server's answer, goes grey on a real failure,
 * stays silent on a rejected token, and stops pinging in a hidden tab.
 */
describe('PresenceService', () => {
  let service: PresenceService;
  let httpMock: HttpTestingController;

  const URL = `${environment.apiBaseUrl}/auth/heartbeat`;

  function setHidden(hidden: boolean): void {
    Object.defineProperty(document, 'hidden', { configurable: true, get: () => hidden });
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PresenceService);
    httpMock = TestBed.inject(HttpTestingController);
    setHidden(false);
  });

  afterEach(() => {
    service.stop();
    httpMock.verify();
    // Restore the prototype getter the tests replaced.
    delete (document as unknown as { hidden?: boolean }).hidden;
  });

  it('sends one heartbeat on start, and only trusts the server for the state', () => {
    service.start();

    const request = httpMock.expectOne(URL);
    expect(request.request.method).toBe('POST');
    expect(service.connected()).withContext('no dot is painted on a guess').toBeNull();

    request.flush(null);
    expect(service.connected()).toBeTrue();
  });

  it('pings again on the interval', fakeAsync(() => {
    service.start();
    httpMock.expectOne(URL).flush(null);

    tick(HEARTBEAT_INTERVAL_MS - 1);
    expect(httpMock.match(URL).length).withContext('not before the interval elapses').toBe(0);

    tick(1);
    const second = httpMock.expectOne(URL);
    expect(second.request.method).toBe('POST');
    second.flush(null);
    service.stop();
  }));

  it('goes grey on a failed heartbeat and recovers on the next ack', fakeAsync(() => {
    service.start();
    httpMock.expectOne(URL).flush(null);
    expect(service.connected()).toBeTrue();

    tick(HEARTBEAT_INTERVAL_MS);
    httpMock.expectOne(URL).flush('down', { status: 500, statusText: 'Server Error' });
    expect(service.connected()).withContext('a dropped heartbeat is a measurement').toBeFalse();

    tick(HEARTBEAT_INTERVAL_MS);
    httpMock.expectOne(URL).flush(null);
    expect(service.connected()).toBeTrue();
    service.stop();
  }));

  it('does not report offline when the token is rejected, and stops pinging', fakeAsync(() => {
    service.start();

    httpMock.expectOne(URL).flush('nope', { status: 401, statusText: 'Unauthorized' });

    expect(service.connected()).withContext('401 is the auth layer\u2019s business, not this dot').toBeNull();
    tick(HEARTBEAT_INTERVAL_MS * 2);
    expect(httpMock.match(URL).length).withContext('a rejected token ends the loop instead of retrying').toBe(0);
  }));

  it('stays quiet in a hidden tab and pings on return', fakeAsync(() => {
    setHidden(true);
    service.start();
    expect(httpMock.match(URL).length).withContext('a background tab keeps no visible state truthful').toBe(0);

    tick(HEARTBEAT_INTERVAL_MS);
    expect(httpMock.match(URL).length).withContext('the interval skips a hidden tab too').toBe(0);

    setHidden(false);
    document.dispatchEvent(new Event('visibilitychange'));
    httpMock.expectOne(URL).flush(null);
    expect(service.connected()).toBeTrue();
    service.stop();
  }));

  it('clears the loop and the state when stopped', fakeAsync(() => {
    service.start();
    httpMock.expectOne(URL).flush(null);

    service.stop();
    tick(HEARTBEAT_INTERVAL_MS * 2);

    expect(httpMock.match(URL).length).withContext('a stopped loop sends nothing').toBe(0);
    expect(service.connected()).withContext('a remount must not inherit the old dot').toBeNull();
  }));
});
