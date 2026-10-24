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
          <input class="field-control" [(ngModel)]="pwPhone" placeholder="Registered phone" maxlength="10" aria-label="Registered phone number" />
        </div>
        <div class="field" *ngIf="!pwOtpSent()">
          <button class="primary-btn" [disabled]="!pwPhone || pwPhone.length !== 10 || pwLoading()" (click)="requestPasswordOtp()">
            {{ pwLoading() ? 'Sending...' : 'Send OTP' }}
          </button>
        </div>
        <ng-container *ngIf="pwOtpSent()">
          <div class="field">
            <label>OTP</label>
            <input class="field-control" [(ngModel)]="pwOtp" placeholder="6-digit OTP" maxlength="6" aria-label="One-time password" autocomplete="one-time-code" />
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
