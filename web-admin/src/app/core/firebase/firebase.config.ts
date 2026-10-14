import { environment } from '../../../environments/environment';

export const firebaseConfig = environment.firebase;

/**
 * localStorage key for the FCM registration token.
 *
 * It is written by FirebaseInitService and read again by KBNotificationService,
 * so the literal lives here once instead of in three places that can drift
 * apart and silently stop push notifications.
 */
export const FCM_TOKEN_STORAGE_KEY = 'kb_fcm_token';

/**
 * The committed environment files still ship the Firebase template values
 * ("YOUR_API_KEY", "YOUR_PROJECT_ID", "YOUR_VAPID_KEY"). Initialising the SDK
 * with them means every first-time visitor is asked for notification
 * permission and then fails to obtain a token, so callers check this first and
 * do nothing until the values are real.
 */
export function isFirebaseConfigured(
  config: typeof firebaseConfig = firebaseConfig,
  vapidKey: string = environment.vapidKey,
): boolean {
  const values = [config.apiKey, config.projectId, config.messagingSenderId, config.appId, vapidKey];
  return values.every((value) => !!value && !/^(YOUR_|G-YOUR_)/.test(value));
}
