import { Injectable, signal, effect } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private readonly THEME_KEY = 'khanabook-theme';
  isDarkMode = signal<boolean>(this.loadTheme());

  constructor() {
    effect(() => {
      const isDark = this.isDarkMode();
      if (isDark) {
        document.body.classList.add('dark-theme');
      } else {
        document.body.classList.remove('dark-theme');
      }
      localStorage.setItem(this.THEME_KEY, isDark ? 'dark' : 'light');
    });
  }

  toggleTheme() {
    this.isDarkMode.update(v => !v);
  }

  private loadTheme(): boolean {
    const saved = localStorage.getItem(this.THEME_KEY);
    if (saved) {
      return saved === 'dark';
    }
    return window.matchMedia('(prefers-color-scheme: dark)').matches;
  }
}
