import { inject, Injectable, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { initializeApp } from 'firebase/app';
import { getMessaging, getToken, onMessage } from 'firebase/messaging';
import { firebaseConfig } from './firebase.config';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class FirebaseInitService {

  private firebaseApp: any;
  private messaging: any;
  private token: string | null = null;

  private platformId = inject(PLATFORM_ID);

  constructor() {
    this.initialize();
  }

  initialize(): void {
    if (!isPlatformBrowser(this.platformId)) {
      console.log('Not running in browser, skipping Firebase init');
      return;
    }

    try {
      this.firebaseApp = initializeApp(firebaseConfig);
      this.messaging = getMessaging(this.firebaseApp);

      this.requestPermissionAndGetToken()
        .then(token => {
          if (token) {
            this.token = token;
            localStorage.setItem('kb_fcm_token', token);
            console.log('FCM token obtained and stored');
          }
        })
        .catch(err => console.error('Error obtaining FCM token:', err));

      this.setupForegroundHandler();
    } catch (error) {
      console.error('Failed to initialize Firebase:', error);
    }
  }

  private async requestPermissionAndGetToken(): Promise<string | null> {
    if (!('Notification' in window)) {
      console.log('Notification API not available');
      return null;
    }

    const currentPermission = (window as any).Notification.permission;
    let permission: string;

    if (currentPermission === 'granted') {
      permission = 'granted';
    } else if (currentPermission === 'denied') {
      console.log('Notification permission permanently denied');
      return null;
    } else {
      permission = await (window as any).Notification.requestPermission();
    }

    if (permission !== 'granted') {
      console.log('Notification permission not granted:', permission);
      return null;
    }

    try {
      const vapidKey = environment.vapidKey;
      const token = await getToken(this.messaging, { vapidKey });
      return token;
    } catch (error) {
      console.error('Error getting FCM token:', error);
      return null;
    }
  }

  private setupForegroundHandler(): void {
    if (typeof window !== 'undefined' && this.messaging) {
      onMessage(this.messaging, (payload: any) => {
        this.handleIncomingPayload(payload);
      });
    }
  }

  private handleIncomingPayload(payload: any): void {
    const type = payload.data?.type || 'system';
    const title = payload.notification?.title || payload.data?.title || 'KhanaBook';
    const body = payload.notification?.body || payload.data?.message || '';

    try {
      const notification = new Notification(title, {
        body,
        icon: '/assets/icons/khanabook-logo.svg',
        tag: type,
        requireInteraction: false
      });

      setTimeout(() => {
        notification.close();
      }, 10000);

      console.log('Foreground notification received:', title, body);
    } catch (err) {
      console.error('Error showing notification:', err);
    }
  }

  getCurrentToken(): string | null {
    return this.token || localStorage.getItem('kb_fcm_token') || null;
  }

  isPermissionGranted(): boolean {
    if (!isPlatformBrowser(this.platformId)) return false;
    return (window as any).Notification.permission === 'granted';
  }
}