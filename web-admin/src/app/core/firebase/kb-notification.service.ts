import { inject, Injectable, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { FirebaseInitService } from './firebase.init';

declare const Notification: any;

export type NotificationType = 
  | 'payment_received' 
  | 'qr_order' 
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

  async initialize(): Promise<void> {
    if (!isPlatformBrowser(this.platformId)) return;

    const permission = await this.requestPermission();
    if (permission === 'granted') {
      const token = await this.getFCMToken();
      if (token) {
        await this.registerTokenWithBackend(token);
      }
    }

    this.setupForegroundMessageHandler();
  }

  private async requestPermission(): Promise<'granted' | 'denied' | 'default'> {
    if (!('Notification' in window)) {
      return 'default';
    }

    const currentPermission = Notification.permission;
    if (currentPermission === 'granted') {
      return 'granted';
    }
    if (currentPermission === 'denied') {
      return 'denied';
    }

    const permission = await Notification.requestPermission();
    return permission;
  }

  private async getFCMToken(): Promise<string | null> {
    try {
      const storedToken = localStorage.getItem(this.FCM_TOKEN_KEY);
      if (storedToken) {
        return storedToken;
      }

      const token = this.firebaseInit.getCurrentToken();
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

  private async registerTokenWithBackend(token: string): Promise<void> {
    try {
      await this.http.post(`${environment.apiBaseUrl}/sync/register-fcm-token`, { token }).toPromise();
      console.log('FCM token registered with backend');
    } catch (error) {
      console.error('Error registering FCM token with backend:', error);
    }
  }

  private setupForegroundMessageHandler(): void {
    // Foreground messages are already handled by FirebaseInitService
    // This service focuses on permission management and backend registration
  }

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

    setTimeout(() => {
      notification.close();
    }, 10000);
  }

  handleIncomingMessage(payload: any): void {
    const type = payload.data?.type || 'system';
    const title = payload.notification?.title || payload.data?.title || 'KhanaBook';
    const body = payload.notification?.body || payload.data?.message || '';

    if (title && body) {
      this.showNotification(title, body, type);
    }
  }

  isPermissionGranted(): boolean {
    if (!isPlatformBrowser(this.platformId)) return false;
    return Notification.permission === 'granted';
  }

  getCurrentToken(): string | null {
    if (!isPlatformBrowser(this.platformId)) return null;
    return localStorage.getItem(this.FCM_TOKEN_KEY) || null;
  }

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