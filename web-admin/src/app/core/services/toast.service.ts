import { Injectable, signal } from '@angular/core';

export interface ToastMessage {
  text: string;
  type: 'info' | 'error' | 'success';
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly message = signal<ToastMessage | null>(null);

  private timer: ReturnType<typeof setTimeout> | null = null;

  show(text: string, type: ToastMessage['type'] = 'info', duration?: number): void {
    if (this.timer) clearTimeout(this.timer);
    this.message.set({ text, type });
    const resolvedDuration = duration ?? (type === 'error' ? 7000 : 3500);
    this.timer = setTimeout(() => this.message.set(null), resolvedDuration);
  }

  error(text: string, duration = 7000): void {
    this.show(text, 'error', duration);
  }

  success(text: string, duration = 3500): void {
    this.show(text, 'success', duration);
  }

  info(text: string, duration = 3500): void {
    this.show(text, 'info', duration);
  }

  dismiss(): void {
    if (this.timer) clearTimeout(this.timer);
    this.message.set(null);
  }
}
