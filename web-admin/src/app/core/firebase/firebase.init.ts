import { inject, Injectable, PLATFORM_ID, injectableTokens } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { lastValueFrom, firstValueFrom, Observable, of, from, pipe } from 'rxjs';
import { map, take, catchError, switchMap } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';
import { initializeApp } from 'firebase/app';
import { getMessaging, getToken, onMessage } from 'firebase/messaging';

// Disable zone running for Firebase Messaging to avoid conflicts
// We'll manually run change detection when needed

@Injectable({
  providedIn: 'root'
})
export class FirebaseInitService {

  private firebaseApp: any;
  private messaging: any;
  private token: string | null = null;

  constructor(@Inject(PLATFORM_ID) private platformId: Object) {
    this.initialize();
  }

  initialize(): void {
    if (!isPlatformBrowser(this.platformId)) {
      console.log('Not running in browser, skipping Firebase init');
      return;
    }

    // Firebase config - in production, this would come from environment.ts
    const firebaseConfig = {
      apiKey: "YOUR_API_KEY",
      authDomain: "YOUR_PROJECT_ID.firebaseapp.com",
      projectId: "YOUR_PROJECT_ID",
      storageBucket: "YOUR_PROJECT_ID.appspot.com",
      messagingSenderId: "YOUR_MESSAGING_SENDER_ID",
      appId: "YOUR_APP_ID",
      measurementId: "G-YOUR_MEASUREMENT_ID"
    };

    try {
      this.firebaseApp = initializeApp(firebaseConfig);
      this.messaging = getMessaging(this.firebaseApp);

      // Request notification permission and get token
      this.requestPermissionAndGetToken()
        .then(token => {
          if (token) {
            this.token = token;
            localStorage.setItem('kb_fcm_token', token);
            console.log('FCM token obtained and stored');
          }
        })
        .catch(err => console.error('Error obtaining FCM token:', err));

      // Set up foreground message handler
      this.setupForegroundHandler();
    } catch (error) {
      console.error('Failed to initialize Firebase:', error);
    }
  }

  private async requestPermissionAndGetToken(): Promise<string | null> {
    // Check if Notification API is available
    if (!('Notification' in window)) {
      console.log('Notification API not available');
      return null;
    }

    // Request permission if needed
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

    // Get FCM token with VAPID key
    try {
      // Use the messagingSenderId as vapidKey for simplicity
      // In production, use proper VAPID key from Firebase console
      const vapidKey = "BCm1l53u main technologyg2Wkg6J2xZ2l5ex1eT2Fc9l5xZ2l5ex1eT2Fc9l5xZ2l5ex1eT2Fc9l5xZ2l5ex1e";
      const token = await getToken(this.messaging, { vapidKey });
      return token;
    } catch (error) {
      console.error('Error getting FCM token:', error);
      return null;
    }
  }

  private setupForegroundHandler(): void {
    // Set up foreground message handler
    // @ts-ignore - Firebase Messaging API onMessage
    if (typeof window !== 'undefined' && this.messaging) {
      // @ts-ignore
      onMessage(this.messaging, (payload: any) => {
        // Dispatch event or call method to handle foreground message
        this.handleIncomingPayload(payload);
      });
    }
  }

  private handleIncomingPayload(payload: any): void {
    const type = payload.data?.type || 'system';
    const title = payload.notification?.title || payload.data?.title || 'KhanaBook';
    const body = payload.notification?.body || payload.data?.message || '';

    // Create a simple notification
    try {
      const notification = new Notification(title, {
        body,
        icon: '/assets/icons/khanabook-logo.svg',
        tag: type,
        requireInteraction: false,
        timestamp: Date.now()
      });

      // Auto-close after 10 seconds
      setTimeout(() => {
        notification.close();
      }, 10000);

      console.log('Foreground notification received:', title, body);
    } catch (err) {
      console.error('Error showing notification:', err);
    }
  }

  /** Get the current FCM token */
  getCurrentToken(): string | null {
    return this.token || localStorage.getItem('kb_fcm_token') || null;
  }

  /** Check if notification permission is granted */
  isPermissionGranted(): boolean {
    if (!isPlatformBrowser(this.platformId)) return false;
    return (window as any).Notification.permission === 'granted';
  }
}