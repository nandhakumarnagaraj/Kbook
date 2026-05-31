import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AdminApiService } from '../../core/services/admin-api.service';
import { formatCurrency } from '../../shared/formatters';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-onboarding-tracker-page',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, MatButtonModule, MatProgressBarModule, MatSnackBarModule, MatProgressSpinnerModule],
  template: `
    <div class="page-container">
      <div class="header-row">
        <div><h1 class="page-title">Onboarding Tracker</h1><p class="page-subtitle">Track sub-merchant onboarding progress from setup to live.</p></div>
        <div><button mat-flat-button color="primary" (click)="prefill()"><mat-icon>auto_fix_high</mat-icon>Prefill from Profile</button></div>
      </div>
      @if (progress(); as p) {
        <div class="stats-grid">
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon" [class.green]="p.isLive" [class.amber]="!p.isLive">{{ p.isLive ? 'check_circle' : 'hourglass_top' }}</mat-icon><mat-card-title>{{ p.completedSteps }}/{{ p.totalSteps }}</mat-card-title><mat-card-subtitle>{{ p.isLive ? 'Live & Active' : 'Setup Incomplete' }}</mat-card-subtitle></mat-card-header></mat-card>
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon blue">shopping_bag</mat-icon><mat-card-title>{{ p.totalOrders }}</mat-card-title><mat-card-subtitle>Total Orders</mat-card-subtitle></mat-card-header></mat-card>
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon amber">payments</mat-icon><mat-card-title>{{ formatCurrencyValue(p.totalRevenue) }}</mat-card-title><mat-card-subtitle>Total Revenue</mat-card-subtitle></mat-card-header></mat-card>
        </div>
        <mat-card class="progress-card">
          <mat-card-header><mat-card-title>Onboarding Steps</mat-card-title></mat-card-header>
          <mat-card-content>
            <div class="steps">
              @for (step of p.steps; track step.key) {
                <div class="step" [class.complete]="step.status === 'complete'" [class.pending]="step.status === 'pending'">
                  <div class="step-icon"><mat-icon>{{ step.status === 'complete' ? 'check_circle' : 'radio_button_unchecked' }}</mat-icon></div>
                  <div class="step-label">{{ step.label }}</div>
                </div>
              }
            </div>
            <mat-progress-bar mode="determinate" [value]="(p.completedSteps / p.totalSteps) * 100" class="progress-bar"></mat-progress-bar>
          </mat-card-content>
        </mat-card>
      } @else { <div class="loading"><mat-spinner diameter="40"></mat-spinner></div> }
    </div>
  `,
  styles: [`
    .page-container{padding:24px;max-width:1400px;margin:0 auto}
    .header-row{display:flex;justify-content:space-between;align-items:center;margin-bottom:24px}
    .page-title{margin:0 0 4px;font-size:1.75rem;font-weight:700;color:var(--ink)}.page-subtitle{margin:0;color:var(--muted);font-size:0.85rem}
    .stats-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:16px;margin-bottom:24px}
    .stat-card{border-radius:12px;border:none;box-shadow:0 4px 16px rgba(0,0,0,0.05)}
    ::ng-deep .stat-card .mat-mdc-card-header{padding:12px 16px!important;gap:12px!important}
    ::ng-deep .stat-card .mat-mdc-card-title{font-size:1.25rem!important;font-weight:700!important;margin:0!important}
    ::ng-deep .stat-card .mat-mdc-card-subtitle{font-size:0.8rem!important;color:var(--muted)!important;margin-top:2px!important}
    .stat-icon{width:36px;height:36px;border-radius:8px;font-size:18px;display:flex;align-items:center;justify-content:center}
    .green{background:#dcfce7;color:#16a34a}.amber{background:#fef3c7;color:#d97706}.blue{background:#e0f2fe;color:#0284c7}
    .progress-card{border-radius:16px;border:none;box-shadow:0 4px 20px rgba(0,0,0,0.05)}
    .steps{display:flex;gap:16px;flex-wrap:wrap;margin:16px 0}
    .step{display:flex;align-items:center;gap:8px;padding:8px 16px;border-radius:999px;font-size:0.85rem;font-weight:500;border:1px solid var(--line)}
    .step.complete{background:#dcfce7;color:#16a34a;border-color:#bbf7d0}.step.pending{background:var(--bg-elevated);color:var(--muted)}
    .step-icon mat-icon{font-size:18px;width:18px;height:18px}
    .progress-bar{height:8px;border-radius:4px;margin-top:16px}
    .no-data{padding:32px;text-align:center;color:var(--muted)}.loading{display:flex;justify-content:center;padding:60px}
  `]
})
export class OnboardingTrackerPageComponent {
  private readonly api = inject(AdminApiService);
  private readonly snack = inject(MatSnackBar);
  readonly progress = toSignal(this.api.getOnboardingProgress().pipe(catchError(() => of(null))));
  prefill() { this.api.prefillFromProfile().subscribe({ next: () => { this.snack.open('Profile prefilled', 'Close', { duration: 2000 }); }, error: () => this.snack.open('Failed', 'Close', { duration: 2000 }) }); }
  formatCurrencyValue(v: number): string { return formatCurrency(v); }
}
