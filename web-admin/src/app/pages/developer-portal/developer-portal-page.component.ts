import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AdminApiService } from '../../core/services/admin-api.service';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-developer-portal-page',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, MatButtonModule, MatChipsModule, MatExpansionModule, MatProgressSpinnerModule],
  template: `
    <div class="page-container">
      <div class="header-row">
        <div><h1 class="page-title">Developer Portal</h1><p class="page-subtitle">API documentation, webhook events, and rate limit info.</p></div>
        <div><span class="version-badge">v{{ docs()?.version }}</span></div>
      </div>
      @if (docs(); as d) {
        <div class="stats-grid">
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon green">vpn_key</mat-icon><mat-card-title>JWT Bearer</mat-card-title><mat-card-subtitle>Authentication</mat-card-subtitle></mat-card-header></mat-card>
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon amber">speed</mat-icon><mat-card-title>{{ rateLimit()?.remaining }}/100</mat-card-title><mat-card-subtitle>Requests Remaining</mat-card-subtitle></mat-card-header></mat-card>
          <mat-card class="stat-card"><mat-card-header><mat-icon mat-card-avatar class="stat-icon blue">code</mat-icon><mat-card-title>{{ d.endpoints.length }}</mat-card-title><mat-card-subtitle>Endpoint Groups</mat-card-subtitle></mat-card-header></mat-card>
        </div>
        <mat-card class="docs-card">
          <mat-card-header><mat-card-title>API Endpoints</mat-card-title></mat-card-header>
          <mat-card-content>
            <mat-accordion>
              @for (group of d.endpoints; track group.group) {
                <mat-expansion-panel>
                  <mat-expansion-panel-header><mat-panel-title>{{ group.group }}</mat-panel-title></mat-expansion-panel-header>
                  <table class="api-table">
                    <tr><th>Method</th><th>Path</th><th>Description</th><th>Auth</th></tr>
                    @for (ep of group.endpoints; track ep.path) {
                      <tr>
                        <td><span class="method-badge" [class]="ep.method">{{ ep.method }}</span></td>
                        <td class="path">{{ ep.path }}</td>
                        <td>{{ ep.description }}</td>
                        <td><span class="auth-badge">{{ ep.auth }}</span></td>
                      </tr>
                    }
                  </table>
                </mat-expansion-panel>
              }
            </mat-accordion>
          </mat-card-content>
        </mat-card>
        @if (webhookEvents(); as events) {
          <mat-card class="events-card">
            <mat-card-header><mat-card-title>Webhook Events</mat-card-title></mat-card-header>
            <mat-card-content>
              <div class="event-grid">
                @for (key of objectKeys(events); track key) {
                  <div class="event-item"><span class="event-name">{{ key }}</span><span class="event-desc">{{ events[key] }}</span></div>
                }
              </div>
            </mat-card-content>
          </mat-card>
        }
      } @else { <div class="loading"><mat-spinner diameter="40"></mat-spinner></div> }
    </div>
  `,
  styles: [`
    .page-container{padding:24px;max-width:1400px;margin:0 auto}
    .header-row{display:flex;justify-content:space-between;align-items:center;margin-bottom:24px}
    .page-title{margin:0 0 4px;font-size:1.75rem;font-weight:700;color:var(--ink)}.page-subtitle{margin:0;color:var(--muted);font-size:0.85rem}
    .version-badge{padding:6px 14px;border-radius:999px;background:var(--brand-soft);color:var(--brand);font-weight:700;font-size:0.85rem}
    .stats-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:16px;margin-bottom:24px}
    .stat-card{border-radius:12px;border:none;box-shadow:0 4px 16px rgba(0,0,0,0.05)}
    ::ng-deep .stat-card .mat-mdc-card-header{padding:12px 16px!important;gap:12px!important}
    ::ng-deep .stat-card .mat-mdc-card-title{font-size:1.25rem!important;font-weight:700!important;margin:0!important}
    ::ng-deep .stat-card .mat-mdc-card-subtitle{font-size:0.8rem!important;color:var(--muted)!important;margin-top:2px!important}
    .stat-icon{width:36px;height:36px;border-radius:8px;font-size:18px;display:flex;align-items:center;justify-content:center}
    .green{background:#dcfce7;color:#16a34a}.amber{background:#fef3c7;color:#d97706}.blue{background:#e0f2fe;color:#0284c7}
    .docs-card,.events-card{border-radius:16px;border:none;box-shadow:0 4px 20px rgba(0,0,0,0.05);margin-bottom:24px}
    .api-table{width:100%;border-collapse:collapse}
    .api-table th{text-align:left;padding:8px 12px;font-size:0.7rem;text-transform:uppercase;color:var(--muted);border-bottom:1px solid var(--line)}
    .api-table td{padding:8px 12px;font-size:0.85rem;border-bottom:1px solid var(--line)}
    .api-table .path{font-family:monospace;font-size:0.8rem}
    .method-badge{padding:2px 8px;border-radius:4px;font-size:0.7rem;font-weight:700;color:white}
    .method-badge.GET{background:#2563eb}.method-badge.POST{background:#16a34a}.method-badge.PUT{background:#d97706}.method-badge.DELETE{background:#dc2626}
    .auth-badge{padding:2px 8px;border-radius:4px;font-size:0.7rem;background:var(--bg-elevated);color:var(--muted)}
    .event-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:12px}
    .event-item{padding:12px 16px;border-radius:10px;background:var(--bg-elevated)}
    .event-name{display:block;font-family:monospace;font-weight:600;color:var(--brand);font-size:0.85rem;margin-bottom:4px}
    .event-desc{font-size:0.8rem;color:var(--muted)}
    .loading{display:flex;justify-content:center;padding:60px}
  `]
})
export class DeveloperPortalPageComponent {
  private readonly api = inject(AdminApiService);
  readonly docs = toSignal(this.api.getApiDocs().pipe(catchError(() => of(null))));
  readonly webhookEvents = toSignal(this.api.getWebhookEvents().pipe(catchError(() => of(null))));
  readonly rateLimit = toSignal(this.api.getRateLimits().pipe(catchError(() => of(null))));
  objectKeys(obj: any): string[] { return obj ? Object.keys(obj) : []; }
}
