import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { FirebaseInitService } from './firebase.init';
import { KBNotificationService } from './kb-notification.service';
import { FCM_TOKEN_STORAGE_KEY, firebaseConfig, isFirebaseConfigured } from './firebase.config';

const realConfig = {
  apiKey: 'AIzaSyA-real-key',
  authDomain: 'kb.firebaseapp.com',
  projectId: 'kb-project',
  storageBucket: 'kb-project.appspot.com',
  messagingSenderId: '1234567890',
  appId: '1:1234567890:web:abcdef',
  measurementId: 'G-ABCDEF',
};

/**
 * The shipped environment files carry the Firebase template values. Treating
 * those as configuration made the app ask every first-time visitor for the
 * notification permission and then fail to obtain a token — an unavoidable
 * prompt, backed by a console error, for a feature that was never wired up.
 */
describe('firebase configuration guard', () => {
  afterEach(() => localStorage.removeItem(FCM_TOKEN_STORAGE_KEY));

  it('treats the shipped template values as not configured', () => {
    expect(isFirebaseConfigured(firebaseConfig)).withContext('this repo still ships the template values').toBe(false);
  });

  it('rejects each placeholder in turn', () => {
    expect(isFirebaseConfigured({ ...realConfig, apiKey: 'YOUR_API_KEY' }, 'real-vapid')).toBe(false);
    expect(isFirebaseConfigured({ ...realConfig, projectId: 'YOUR_PROJECT_ID' }, 'real-vapid')).toBe(false);
    expect(isFirebaseConfigured({ ...realConfig, appId: 'YOUR_APP_ID' }, 'real-vapid')).toBe(false);
    expect(isFirebaseConfigured({ ...realConfig, messagingSenderId: 'YOUR_MESSAGING_SENDER_ID' }, 'real-vapid')).toBe(false);
    expect(isFirebaseConfigured(realConfig, 'YOUR_VAPID_KEY')).toBe(false);
    expect(isFirebaseConfigured(realConfig, 'G-YOUR_MEASUREMENT_ID')).toBe(false);
  });

  it('accepts a fully filled configuration', () => {
    expect(isFirebaseConfigured(realConfig, 'BRealVapidKey')).toBe(true);
  });

  it('only asks for notification permission once the config is real', () => {
    // Deterministic prompt state: 'default' is the branch that would call
    // requestPermission(), so an unconfigured build must still not reach it.
    Object.defineProperty(window.Notification, 'permission', { value: 'default', configurable: true });
    const request = spyOn(window.Notification as any, 'requestPermission').and.returnValue(Promise.resolve('granted'));

    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(FirebaseInitService);
    service.initialize();

    if (isFirebaseConfigured()) {
      expect(request).withContext('a configured build should prompt').toHaveBeenCalled();
    } else {
      expect(request).withContext('an unconfigured build must not prompt for permission').not.toHaveBeenCalled();
    }
  });

  it('does not ask for permission from the notification service either', () => {
    Object.defineProperty(window.Notification, 'permission', { value: 'default', configurable: true });
    const request = spyOn(window.Notification as any, 'requestPermission').and.returnValue(Promise.resolve('granted'));

    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    // Its constructor runs initialize(), so injecting it is the app-start path.
    TestBed.inject(KBNotificationService);

    if (isFirebaseConfigured()) {
      expect(request).withContext('a configured build should prompt').toHaveBeenCalled();
    } else {
      expect(request).withContext('an unconfigured build must not prompt for permission').not.toHaveBeenCalled();
    }
  });

  it('shares one storage key between the writer and both readers', () => {
    // The token is written by FirebaseInitService and read by the initialiser and
    // by KBNotificationService. Three copies of the literal once sat in two files;
    // a drift there means the stored token is never found again.
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    localStorage.setItem(FCM_TOKEN_STORAGE_KEY, 'token-from-shared-key');

    expect(TestBed.inject(FirebaseInitService).getCurrentToken()).toBe('token-from-shared-key');
    expect(TestBed.inject(KBNotificationService).getCurrentToken()).toBe('token-from-shared-key');
  });
});
