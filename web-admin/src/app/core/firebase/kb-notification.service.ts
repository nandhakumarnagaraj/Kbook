import { inject, Injectable, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { lastValueFrom, firstValueFrom, Observable, of, from } from 'rxjs';
import { map, take, catchError } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { FirebaseInitService } from './firebase.init';
import { kbColorPalette } from '../styles';

declare const Notification: any;

export type NotificationType = 
  | 'payment_received' 
  | 'qr_order' 
  | 'marketplace_order' 
  | 'refund' 
  | 'kyc' 
  | 'settlement' 
  | 'inventory_low' 
  | 'permission_request' 
  | 'permission_approved' 
  | 'permission_rejected'
  | 'system';

export type NotificationColor = 
  | 'green' 
  | 'red' 
  | 'violet' 
  | 'blue' 
  | 'saffron' 
  | 'purple';

export interface FCMTokenResponse {
  token: string;
  success: boolean;
}

export interface SentNotification {
  id: string;
  title: string;
  body: string;
  type: NotificationType;
  timestamp: number;
}

const NOTIFICATION_COLORS: Record<NotificationType, NotificationColor> = {
  'payment_received': 'green',
  'qr_order': 'saffron',
  'marketplace_order': 'saffron',
  'refund': 'red',
  'kyc': 'violet',
  'settlement': 'blue',
  'inventory_low': 'purple',
  'permission_request': 'purple',
  'permission_approved': 'green',
  'permission_rejected': 'red',
  'system': 'purple'
};

const NOTIFICATION_COLOR_INTENSITY: Record<NotificationColor, number> = {
  'green': 0xFF16A34A,
  'red': 0xFFEF4444,
  'violet': 0xFF8B5CF6,
  'blue': 0xFF0284C7,
  'saffron': 0xFFF97316,
  'purple': 0xFF7C5CDB
};

@Injectable({
  providedIn: 'root'
})
export class KBNotificationService {

  private readonly NOTIFICATION_PERMISSION_KEY = 'kb_notification_permission';
  private readonly FCM_TOKEN_KEY = 'kb_fcm_token';

  private firebaseInit = inject(FirebaseInitService);
  private platformId = inject(PLATFORM_ID);
  private http = inject(HttpClient);

  constructor() {
    this.initialize().catch(console.error);
  }

  /**
   * Initialize FCM and request notification permission
   */
  async initialize(): Promise<void> {
    if (!isPlatformBrowser(this.platformId)) return;

    // Request notification permission
    const permission = await this.requestPermission();
    if (permission === 'granted') {
      // Get FCM token
      const token = await this.getFCMToken();
      if (token) {
        await this.registerTokenWithBackend(token);
      }
    }

    // Set up foreground message handler
    this.setupForegroundMessageHandler();
  }

  /**
   * Request browser notification permission
   */
  private async requestPermission(): Promise<'granted' | 'denied' | 'default'> {
    if (!('Notification' in window)) {
      return 'default';
    }

    // Check existing permission
    const currentPermission = Notification.permission;
    if (currentPermission === 'granted') {
      return 'granted';
    }
    if (currentPermission === 'denied') {
      return 'denied';
    }

    // Request new permission
    const permission = await Notification.requestPermission();
    return permission;
  }

  /**
   * Get FCM token from the browser
   */
  private async getFCMToken(): Promise<string | null> {
    try {
      // Check if we already have a token stored
      const storedToken = localStorage.getItem(this.FCM_TOKEN_KEY);
      if (storedToken) {
        return storedToken;
      }

      // Get token from Firebase
      const messaging = this.firebaseInit.messaging;
      if (!messaging) {
        console.log('Firebase messaging not initialized');
        return null;
      }

      const token = await this.firebaseInit.getToken();
      if (token) {
        localStorage.setItem(this.FCM_TOKEN_KEY, token);
        return token;
      }
      return null;
    } catch (error) {
      console.error('Error getting FCM token:', error);
      return null;
    }
  }

  /**
   * Register the FCM token with the backend API
   */
  private async registerTokenWithBackend(token: string): Promise<void> {
    try {
      await this.http.post(`${environment.apiBaseUrl}/sync/register-fcm-token`, { token }).toPromise();
      console.log('FCM token registered with backend');
    } catch (error) {
      console.error('Error registering FCM token with backend:', error);
    }
  }

  /**
   * Set up foreground message handler for web notifications
   */
  private setupForegroundMessageHandler(): void {
    // @ts-ignore - Firebase Messaging API
    if (typeof window !== 'undefined' && 'onmessage' in (window as any).firebase) {
      // @ts-ignore
      const messaging: any = window.firebase.messaging();
      messaging.onMessage((payload: any) => {
        this.showNotification(payload);
      });
    }
  }

  /**
   * Show a local notification (fallback when FCM not available)
   */
  showNotification(
    title: string, 
    body: string, 
    type: NotificationType = 'system',
    icon: string = 'assets/icons/khanabook-logo.svg'
  ): void {
    if (!isPlatformBrowser(this.platformId)) return;
    if (Notification.permission !== 'granted') return;

    const colorIntensity = NOTIFICATION_COLOR_INTENSITY[NOTIFICATION_COLORS[type]] || NOTIFICATION_COLOR_INTENSITY['purple'];

    const notification = new Notification(title, {
      body,
      icon,
      color: `#${colorIntensity.toString(16).padStart(6, '0')}`,
      tag: type,
      requireInteraction: true,
      timestamp: Date.now()
    });

    // Auto-close after 10 seconds
    setTimeout(() => {
      notification.close();
    }, 10000);
  }

  /**
   * Handle incoming FCM message and show notification
   */
  handleIncomingMessage(payload: any): void {
    const type = payload.data?.type || 'system';
    const title = payload.notification?.title || payload.data?.title || 'KhanaBook';
    const body = payload.notification?.body || payload.data?.message || '';

    if (title && body) {
      this.showNotification(title, body, type);
    }
  }

  /**
   * Check if notification permission is granted
   */
  isPermissionGranted(): boolean {
    if (!isPlatformBrowser(this.platformId)) return false;
    return Notification.permission === 'granted';
  }

  /**
   * Get the current FCM token
   */
  getCurrentToken(): string | null {
    if (!isPlatformBrowser(this.platformId)) return null;
    return localStorage.getItem(this.FCM_TOKEN_KEY) || null;
  }

  /**
   * Request permission and get token, then show a test notification
   */
  async requestAndTest(): Promise<void> {
    if (!isPlatformBrowser(this.platformId)) return;

    const permission = await this.requestPermission();
    if (permission === 'granted') {
      const token = await this.getFCMToken();
      if (token) {
        await this.registerTokenWithBackend(token);
        this.showNotification(
          'KhanaBook',
          'Push notifications enabled successfully!',
          'system'
        );
      }
    }
  }
}