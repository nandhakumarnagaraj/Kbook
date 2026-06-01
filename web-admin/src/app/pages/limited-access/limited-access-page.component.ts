import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-limited-access-page',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, MatButtonModule, RouterModule],
  template: `
    <div class="page-container">
      <mat-card class="limited-card mat-elevation-z4">
        <mat-card-header>
          <mat-icon mat-card-avatar class="warn-icon">gpp_maybe</mat-icon>
          <mat-card-title>Access Restricted</mat-card-title>
          <mat-card-subtitle>Insufficient Permissions</mat-card-subtitle>
        </mat-card-header>
        
        <mat-card-content>
          <p class="notice-text">
            Web admin console access is strictly limited to <strong>Platform Administrators</strong> and <strong>Business Owners</strong>.
          </p>
          <p class="detail-text">
            It appears your current account role does not have the necessary permissions to access this management dashboard. 
            If you believe this is an error, please contact support or your account manager for a role migration.
          </p>
          
          <div class="restriction-tags">
             <span class="tag role">Role restricted</span>
             <span class="tag action">Migration required</span>
          </div>
        </mat-card-content>
        
        <mat-card-actions align="end">
          <a mat-button color="primary" routerLink="/login">Back to Login</a>
        </mat-card-actions>
      </mat-card>
    </div>
  `,
  styles: [`
    .page-container {
      height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
      background: var(--bg);
    }

    .limited-card {
      max-width: 540px;
      width: 100%;
      border-radius: 20px;
      border: none;
      padding: 16px;
    }

    .warn-icon {
      background: var(--warn-bg);
      color: var(--warn);
      width: 44px;
      height: 44px;
      line-height: 44px;
      text-align: center;
      border-radius: 12px;
    }

    .notice-text {
      font-size: 1.1rem;
      color: var(--ink);
      line-height: 1.5;
      margin-top: 16px;
    }

    .detail-text {
      font-size: 0.95rem;
      color: var(--muted);
      margin: 16px 0 24px;
    }

    .restriction-tags {
      display: flex;
      gap: 12px;
      margin-bottom: 16px;
    }

    .tag {
      font-size: 0.75rem;
      font-weight: 700;
      padding: 4px 12px;
      border-radius: 6px;
      text-transform: uppercase;
    }

    .tag.role { background: var(--danger-bg); color: var(--danger); }
    .tag.action { background: var(--bg-elevated); color: var(--muted); }
  `]
})
export class LimitedAccessPageComponent {}
