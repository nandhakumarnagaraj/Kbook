import { Injectable, signal } from '@angular/core';

export type ThemeMode = 'light';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  readonly mode = signal<ThemeMode>('light');

  constructor() {
    this.applyTheme();
    try {
      localStorage.removeItem('kb_theme_mode');
    } catch {}
  }

  setMode(_mode: string): void {
    this.applyTheme();
  }

  toggle(): void {
    this.applyTheme();
  }

  private applyTheme(): void {
    if (typeof document === 'undefined') return;
    const root = document.documentElement;
    root.setAttribute('data-theme', 'light');
    root.style.colorScheme = 'light';
  }
}
