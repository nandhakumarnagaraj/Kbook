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
      <h3>Change Password</h3>
      <p class="muted">Update your account password using OTP verification.</p>
      <div class="form-grid" style="max-width:400px;">
        <div class="field">
          <label>Phone Number</label>
          <input class="field-control" [(ngModel)]="pwPhone" placeholder="Registered phone" maxlength="10" />
        </div>
        <div class="field" *ngIf="!pwOtpSent">
          <button class="primary-btn" [disabled]="!pwPhone || pwPhone.length !== 10 || pwLoading" (click)="requestPasswordOtp()">
            {{ pwLoading ? 'Sending...' : 'Send OTP' }}
          </button>
        </div>
        <ng-container *ngIf="pwOtpSent">
          <div class="field">
            <label>OTP</label>
            <input class="field-control" [(ngModel)]="pwOtp" placeholder="6-digit OTP" maxlength="6" />
          </div>
          <div class="field">
            <label>New Password</label>
            <input class="field-control" type="password" [(ngModel)]="pwNew" placeholder="Min 6 characters" />
          </div>
          <div class="field">
            <button class="primary-btn" [disabled]="!pwOtp || pwOtp.length !== 6 || !pwNew || pwNew.length < 6 || pwLoading" (click)="resetPassword()">
              {{ pwLoading ? 'Updating...' : 'Update Password' }}
            </button>
          </div>
        </ng-container>
        <p class="error-text" *ngIf="pwError">{{ pwError }}</p>
        <p class="success-text" *ngIf="pwSuccess">{{ pwSuccess }}</p>
      </div>
    </section>
  `,
  styles: [`
    .settings-section { margin-bottom: var(--kb-space-3); }
    .settings-section h3 { margin: 0 0 var(--kb-space-2); color: var(--kb-color-foreground); }
    .form-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr));
      gap: var(--kb-space-3);
    }
    .field label { display: block; font-size: 0.85rem; color: var(--kb-color-foreground); font-weight: 500; margin-bottom: var(--kb-space-1); }
    .success-text { color: var(--kb-color-success); font-size: 0.85rem; }
    .error-text { color: var(--kb-color-error); font-size: 0.85rem; }
  `]
})
export class PasswordChangeSectionComponent {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);

  pwPhone = '';
  pwOtp = '';
  pwNew = '';
  pwOtpSent = false;
  pwLoading = false;
  pwError = '';
  pwSuccess = '';

  requestPasswordOtp(): void {
    this.pwLoading = true;
    this.pwError = '';
    this.http.post(`${API}/auth/forgot-password/request-otp`, { phone: this.pwPhone }).subscribe({
      next: () => { this.pwLoading = false; this.pwOtpSent = true; },
      error: () => { this.pwLoading = false; this.pwOtpSent = true; }
    });
  }

  resetPassword(): void {
    this.pwLoading = true;
    this.pwError = '';
    this.pwSuccess = '';
    this.http.post<any>(`${API}/auth/forgot-password/verify-otp`, { phone: this.pwPhone, otp: this.pwOtp }).subscribe({
      next: (res) => {
        this.http.post(`${API}/auth/forgot-password/reset-password`, { tempToken: res.tempToken, newPassword: this.pwNew }).subscribe({
          next: () => {
            this.pwLoading = false;
            this.pwSuccess = 'Password updated successfully. Use the new password on your next login.';
            this.pwOtp = '';
            this.pwNew = '';
            this.pwOtpSent = false;
          },
          error: () => { this.pwLoading = false; this.pwError = 'Failed to update password. Try again.'; }
        });
      },
      error: () => { this.pwLoading = false; this.pwError = 'Invalid OTP. Please check and try again.'; }
    });
  }
}
