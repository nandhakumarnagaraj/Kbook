import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { environment } from '../../../environments/environment';

const API_BASE_URL = environment.apiBaseUrl;

/**
 * How often an open console says "still here". Matches the server's own row
 * throttle (PresenceService.HEARTBEAT_THROTTLE_MS), so a minute of pings writes
 * one row rather than 60.
 */
export const HEARTBEAT_INTERVAL_MS = 60_000;

/**
 * Web-session presence for the signed-in user.
 *
 * The dot this feeds means **"this browser session reached KhanaBook recently"**,
 * not "someone is at the keyboard" — nothing here can observe a person. It turns
 * grey when a heartbeat actually fails (server unreachable, network down) and
 * green on the next ack, so the state is a measurement rather than a permanent
 * green light that asserts nothing.
 *
 * A hidden tab stops pinging: a background tab has no visible state to keep
 * truthful, and resuming with a ping proves the session still works when the user
 * comes back. A 401 is not reported as "offline" — that is the auth layer's
 * business, and the interceptor is already handling it.
 */
@Injectable({ providedIn: 'root' })
export class PresenceService {
  private readonly http = inject(HttpClient);

  /** null until the first heartbeat resolves: no dot is painted on a guess. */
  readonly connected = signal<boolean | null>(null);

  private timer?: ReturnType<typeof setInterval>;
  private running = false;

  start(): void {
    if (this.running) return;
    this.running = true;
    document.addEventListener('visibilitychange', this.onVisibilityChange);
    window.addEventListener('online', this.onOnline);
    window.addEventListener('offline', this.onOffline);
    if (!document.hidden) this.ping();
    this.timer = setInterval(() => {
      if (!document.hidden) this.ping();
    }, HEARTBEAT_INTERVAL_MS);
  }

  stop(): void {
    if (this.timer !== undefined) {
      clearInterval(this.timer);
      this.timer = undefined;
    }
    if (!this.running) return;
    this.running = false;
    document.removeEventListener('visibilitychange', this.onVisibilityChange);
    window.removeEventListener('online', this.onOnline);
    window.removeEventListener('offline', this.onOffline);
    // A remount must not show the previous session's dot.
    this.connected.set(null);
  }

  /** One heartbeat. Public so a page can ask on demand without restarting the loop. */
  ping(): void {
    this.http.post<void>(`${API_BASE_URL}/auth/heartbeat`, null).subscribe({
      next: () => this.connected.set(true),
      error: (error: HttpErrorResponse) => {
        // 401 means the token is gone, not that the network is: stay silent and
        // let the auth interceptor navigate to the login page.
        if (error?.status === 401) {
          this.stop();
          return;
        }
        this.connected.set(false);
      }
    });
  }

  private readonly onVisibilityChange = (): void => {
    if (!document.hidden && this.running) this.ping();
  };

  private readonly onOnline = (): void => {
    if (this.running) this.ping();
  };

  private readonly onOffline = (): void => {
    this.connected.set(false);
  };
}
