import { Component } from '@angular/core';

@Component({
  selector: 'app-shadcn-demo',
  standalone: true,
  template: `
    <div class="page-shell">
      <div class="panel">
        <h2>Shadcn-style Button Demo — Angular</h2>
        <p>Angular native demo mimicking shadcn/ui button variants using existing design tokens.</p>
        <div class="btn-group">
          <button class="btn btn-primary">Primary</button>
          <button class="btn btn-secondary">Secondary</button>
          <button class="btn btn-outline">Outline</button>
          <button class="btn btn-ghost">Ghost</button>
          <span class="badge">Live Sync Ready</span>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .btn-group { display:flex; gap:12px; flex-wrap:wrap; align-items:center; }
    .btn { padding:10px 16px; border-radius:8px; border:1px solid var(--line); background:var(--panel); cursor:pointer; font-weight:500; }
    .btn-primary { background:var(--kb-color-primary); color:white; border-color:var(--kb-color-primary); }
    .btn-secondary { background:var(--kb-color-surface-2); }
    .btn-outline { background:transparent; }
    .btn-ghost { background:transparent; border-color:transparent; }
    .badge { padding:4px 10px; border-radius:999px; background:var(--kb-color-surface-2); font-size:0.8rem; }
  `]
})
export class ShadcnDemoComponent {}
