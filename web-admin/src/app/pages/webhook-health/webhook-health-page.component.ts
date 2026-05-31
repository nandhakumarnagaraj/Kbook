import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { AdminApiService } from '../../core/services/admin-api.service';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-webhook-health-page',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, MatButtonModule, MatTableModule, MatChipsModule, MatSnackBarModule, MatProgressSpinnerModule],
  template: `
    <div class="page-container">
      <div class="header-row">
        <div>
          <h1 class="page-title">Webhook Health</h1>
          <p class="page-subtitle">Monitor webhook delivery, retry queue, and dead letter status.</p>
        </div>
      </div>
      @if (health(); as h) {
        <div class="stats-grid">
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon green">check_circle</mat-icon><mat-card-title>{{ h.totalJobs }}</mat-card-title><mat-card-subtitle>Total Jobs</mat-card-subtitle></mat-card-header></mat-card>
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon amber">schedule</mat-icon><mat-card-title>{{ h.pendingRetries }}</mat-card-title><mat-card-subtitle>Pending Retries</mat-card-subtitle></mat-card-header></mat-card>
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon red">error</mat-icon><mat-card-title>{{ h.deadLetterCount }}</mat-card-title><mat-card-subtitle>Dead Letter</mat-card-subtitle></mat-card-header></mat-card>
        </div>
        <mat-card class="table-card">
          <mat-card-header><mat-card-title>Dead Letter Queue</mat-card-title></mat-card-header>
          <mat-card-content>
            @if (dlq()?.length) {
              <table mat-table [dataSource]="dlq()!">
                <ng-container matColumnDef="webhookType"><th mat-header-cell *matHeaderCellDef>Type</th><td mat-cell *matCellDef="let j">{{ j.webhookType }}</td></ng-container>
                <ng-container matColumnDef="attemptCount"><th mat-header-cell *matHeaderCellDef>Attempts</th><td mat-cell *matCellDef="let j">{{ j.attemptCount }}</td></ng-container>
                <ng-container matColumnDef="lastError"><th mat-header-cell *matHeaderCellDef>Error</th><td mat-cell *matCellDef="let j">{{ j.lastError }}</td></ng-container>
                <ng-container matColumnDef="action"><th mat-header-cell *matHeaderCellDef></th><td mat-cell *matCellDef="let j"><button mat-stroked-button color="primary" (click)="replay(j.id)"><mat-icon>replay</mat-icon>Replay</button></td></ng-container>
                <tr mat-header-row *matHeaderRowDef="['webhookType','attemptCount','lastError','action']"></tr>
                <tr mat-row *matRowDef="let row; columns: ['webhookType','attemptCount','lastError','action'];"></tr>
              </table>
            } @else { <div class="no-data">No dead letter jobs.</div> }
          </mat-card-content>
        </mat-card>
      } @else { <div class="loading"><mat-spinner diameter="40"></mat-spinner></div> }
    </div>
  `,
  styles: [`
    .page-container{padding:24px;max-width:1400px;margin:0 auto}
    .header-row{margin-bottom:24px}.page-title{margin:0 0 4px;font-size:1.75rem;font-weight:700;color:var(--ink)}.page-subtitle{margin:0;color:var(--muted);font-size:0.85rem}
    .stats-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:16px;margin-bottom:24px}
    .stat-card{border-radius:12px;border:none;box-shadow:0 4px 16px rgba(0,0,0,0.05)}
    ::ng-deep .stat-card .mat-mdc-card-header{padding:12px 16px!important;gap:12px!important}
    ::ng-deep .stat-card .mat-mdc-card-title{font-size:1.25rem!important;font-weight:700!important;margin:0!important}
    ::ng-deep .stat-card .mat-mdc-card-subtitle{font-size:0.8rem!important;color:var(--muted)!important;margin-top:2px!important}
    .stat-icon{width:36px;height:36px;border-radius:8px;font-size:18px;display:flex;align-items:center;justify-content:center}
    .green{background:#dcfce7;color:#16a34a}.amber{background:#fef3c7;color:#d97706}.red{background:#fee2e2;color:#dc2626}
    .table-card{border-radius:16px;border:none;box-shadow:0 4px 20px rgba(0,0,0,0.05)}table{width:100%}.no-data{padding:32px;text-align:center;color:var(--muted)}.loading{display:flex;justify-content:center;padding:60px}
  `]
})
export class WebhookHealthPageComponent {
  private readonly api = inject(AdminApiService);
  private readonly snack = inject(MatSnackBar);
  readonly health = toSignal(this.api.getWebhookHealth().pipe(catchError(() => of(null))));
  readonly dlq = signal<any[]>([]);
  constructor() {
    this.api.getDeadLetterJobs().pipe(catchError(() => of([]))).subscribe(j => this.dlq.set(j));
  }
  replay(id: number) {
    this.api.replayDeadLetter(id).subscribe({
      next: () => { this.snack.open('Replayed', 'Close', { duration: 2000 }); this.dlq.set([]); },
      error: () => this.snack.open('Failed', 'Close', { duration: 2000 })
    });
  }
}
