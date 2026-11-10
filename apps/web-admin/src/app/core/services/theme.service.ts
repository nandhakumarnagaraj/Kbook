import { Injectable, signal, effect } from '@angular/core';

export type ThemeMode = 'light' | 'dark' | 'system';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private readonly storageKey = 'kb_theme_mode';
  readonly mode = signal<ThemeMode>(this.getInitialMode());

  constructor() {
    this.applyTheme(this.mode());

    // Listen to OS preference changes if in system mode
    if (typeof window !== 'undefined' && window.matchMedia) {
      window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
        if (this.mode() === 'system') {
          this.applyTheme('system');
        }
      });
    }

    // Effect to update DOM and storage when mode signal changes
    effect(() => {
      const current = this.mode();
      this.applyTheme(current);
      try {
        localStorage.setItem(this.storageKey, current);
      } catch {}
    });
  }

  setMode(mode: ThemeMode): void {
    this.mode.set(mode);
  }

  toggle(): void {
    const next = this.mode() === 'dark' ? 'light' : 'dark';
    this.setMode(next);
  }

  private getInitialMode(): ThemeMode {
    if (typeof window === 'undefined') return 'light';
    try {
      const saved = localStorage.getItem(this.storageKey) as ThemeMode | null;
      if (saved && (saved === 'light' || saved === 'dark' || saved === 'system')) {
        return saved;
      }
    } catch {}
    return 'system';
  }

  private applyTheme(mode: ThemeMode): void {
    if (typeof document === 'undefined') return;
    const root = document.documentElement;
    let effective = mode;
    if (mode === 'system') {
      const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
      effective = prefersDark ? 'dark' : 'light';
    }
    root.setAttribute('data-theme', effective);
    root.style.colorScheme = effective;
  }
}
