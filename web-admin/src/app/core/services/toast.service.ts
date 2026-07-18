import { Injectable, signal } from '@angular/core';

export interface ToastMessage {
  text: string;
  type: 'info' | 'error' | 'success';
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly message = signal<ToastMessage | null>(null);

  private timer: ReturnType<typeof setTimeout> | null = null;

  show(text: string, type: ToastMessage['type'] = 'info', duration = 3500): void {
    if (this.timer) clearTimeout(this.timer);
    this.message.set({ text, type });
    this.timer = setTimeout(() => this.message.set(null), duration);
  }

  dismiss(): void {
    if (this.timer) clearTimeout(this.timer);
    this.message.set(null);
  }
}
