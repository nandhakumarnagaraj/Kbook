import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ToastService } from '../../core/services/toast.service';
import { environment } from '../../../environments/environment';

const API = environment.apiBaseUrl;

@Component({
  selector: 'app-password-change-section',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="panel settings-section">
      <div class="section-title-wrap">
        <div class="section-icon">
          <svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
            <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
          </svg>
        </div>
        <div>
          <h3>Security &amp; Password Management</h3>
          <p class="muted">Update your web admin access credentials via verified one-time password (OTP).</p>
        </div>
      </div>
      <div class="form-grid password-form">
        <div class="field">
          <label>Phone Number</label>
          <input class="field-control tabular-num" [(ngModel)]="pwPhone" placeholder="Registered 10-digit phone" maxlength="10" aria-label="Registered phone number" />
        </div>
        <div class="field password-otp-field" *ngIf="!pwOtpSent()">
          <button class="primary-btn" [disabled]="!pwPhone || pwPhone.length !== 10 || pwLoading()" (click)="requestPasswordOtp()">
            {{ pwLoading() ? 'Sending OTP...' : 'Send OTP' }}
          </button>
        </div>
        <ng-container *ngIf="pwOtpSent()">
          <div class="field">
            <label>OTP</label>
            <input class="field-control tabular-num" [(ngModel)]="pwOtp" placeholder="6-digit OTP" maxlength="6" aria-label="One-time password" autocomplete="one-time-code" />
          </div>
          <div class="field">
            <label>New Password</label>
            <input class="field-control" type="password" [(ngModel)]="pwNew" placeholder="Min 6 characters" aria-label="New password" autocomplete="new-password" />
          </div>
          <div class="field">
            <button class="primary-btn" [disabled]="!pwOtp || pwOtp.length !== 6 || !pwNew || pwNew.length < 6 || pwLoading()" (click)="resetPassword()">
              {{ pwLoading() ? 'Updating...' : 'Update Password' }}
            </button>
          </div>
        </ng-container>
        <p class="error-text" id="pw-error" role="alert" *ngIf="pwError()">{{ pwError() }}</p>
        <p class="success-text" role="status" *ngIf="pwSuccess()">{{ pwSuccess() }}</p>
      </div>
    </section>
  `,
  styles: [`
    .success-text { color: var(--kb-color-success); font-size: var(--kb-text-md); }
    .error-text { color: var(--kb-color-error); font-size: var(--kb-text-md); }
    .password-form { max-width: 440px; }
    .password-otp-field { display: flex; align-items: flex-end; }
  `]
})
export class PasswordChangeSectionComponent {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);

  pwPhone = '';
  pwOtp = '';
  pwNew = '';
  readonly pwOtpSent = signal(false);
  readonly pwLoading = signal(false);
  readonly pwError = signal('');
  readonly pwSuccess = signal('');

  requestPasswordOtp(): void {
    this.pwLoading.set(true);
    this.pwError.set('');
    this.http.post(`${API}/auth/forgot-password/request-otp`, { phone: this.pwPhone }).subscribe({
      next: () => {
        this.pwLoading.set(false);
        this.pwOtpSent.set(true);
      },
      error: () => {
        this.pwLoading.set(false);
        this.pwOtpSent.set(true);
      }
    });
  }

  resetPassword(): void {
    this.pwLoading.set(true);
    this.pwError.set('');
    this.pwSuccess.set('');
    this.http.post<any>(`${API}/auth/forgot-password/verify-otp`, { phone: this.pwPhone, otp: this.pwOtp }).subscribe({
      next: (res) => {
        this.http.post(`${API}/auth/forgot-password/reset-password`, { tempToken: res.tempToken, newPassword: this.pwNew }).subscribe({
          next: () => {
            this.pwLoading.set(false);
            this.pwSuccess.set('Password updated successfully. Use the new password on your next login.');
            this.toast.success('Password updated successfully.');
            this.pwOtp = '';
            this.pwNew = '';
            this.pwOtpSent.set(false);
          },
          error: () => {
            this.pwLoading.set(false);
            this.pwError.set('Failed to update password. Try again.');
          }
        });
      },
      error: () => {
        this.pwLoading.set(false);
        this.pwError.set('Invalid OTP. Please check and try again.');
      }
    });
  }
}
