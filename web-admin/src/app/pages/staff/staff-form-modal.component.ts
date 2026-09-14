import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule, ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { BusinessApiService } from '../../core/services/business-api.service';
import { ToastService } from '../../core/services/toast.service';
import { AuthService } from '../../core/auth/auth.service';
import { BusinessStaffItem, StaffCreatedResponse, StaffRole } from '../../core/models/api.models';

@Component({
  selector: 'app-staff-form-modal',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  styles: [`
    .modal-content { width: 100%; max-width: 460px; }
    .modal-content h3 { margin: 0 0 1.5rem; color: var(--kb-color-foreground); }
    .form-group { margin-bottom: 1rem; }
    .form-group label {
      display: block; margin-bottom: 0.25rem; font-size: 0.875rem;
      color: var(--kb-color-foreground); font-weight: 500;
    }
    .form-group .field-control, .form-group .field-select { width: 100%; box-sizing: border-box; }
    .field-error { color: var(--kb-color-error); font-size: 0.75rem; margin-top: 0.25rem; }
    .form-error {
      background: rgba(239, 68, 68, 0.08); border: 1px solid var(--kb-color-error);
      border-radius: 8px; padding: 0.75rem 1rem; color: var(--kb-color-error);
      font-size: 0.875rem; margin-bottom: 1rem;
    }
    .success-section { text-align: center; padding: var(--kb-space-4) 0; }
    .success-section h4 { color: var(--kb-color-primary); margin: 0 0 var(--kb-space-2); }
    .otp-note {
      background: var(--kb-color-surface); border: 1px solid var(--kb-color-primary);
      border-radius: var(--kb-radius-lg); padding: var(--kb-space-3); margin: var(--kb-space-3) 0;
      font-size: 0.85rem; color: var(--kb-color-foreground); line-height: 1.4;
    }
    .otp-note.warn { border-color: var(--kb-color-error); color: var(--kb-color-error); }
    .success-section p.muted { font-size: 0.76rem; color: var(--kb-color-muted-foreground); }
  `],
  template: `
    <div class="modal-backdrop" *ngIf="open()" (click)="close.emit()">
      <div class="modal-box modal-content" role="dialog" aria-modal="true" [attr.aria-labelledby]="isEdit() ? 'edit-staff-title' : 'create-staff-title'" (click)="$event.stopPropagation()">

        <!-- Success View (Create mode) -->
        <ng-container *ngIf="!isEdit() && createdStaff; else formView">
          <div class="success-section">
            <h4 id="create-staff-title">Staff Member Created</h4>
            <p><strong>{{ createdStaff.name }}</strong> ({{ createdStaff.role }})</p>
            <p *ngIf="createdStaff.otpSent" class="otp-note">
              A one-time code was sent to <strong>{{ createdStaff.phone }}</strong> on WhatsApp.
              Ask them to open the app, tap <strong>Forgot Password</strong>, and set their own password.
            </p>
            <p *ngIf="!createdStaff.otpSent" class="otp-note warn">
              The account was created, but the WhatsApp code could not be sent. Use “Resend code”
              below, or the staff member can tap <strong>Forgot Password</strong> in the app anytime.
            </p>
            <button type="button" class="ghost-btn" (click)="resendOtp()" [disabled]="resending()">
              {{ resending() ? 'Sending...' : 'Resend code' }}
            </button>
            <p class="muted">No password is stored or shared — the staff member chooses their own.</p>
          </div>
          <div class="modal-actions">
            <button class="primary-btn" (click)="close.emit()">Done</button>
          </div>
        </ng-container>

        <!-- Form View -->
        <ng-template #formView>
          <h3 [id]="isEdit() ? 'edit-staff-title' : 'create-staff-title'">{{ isEdit() ? 'Edit Staff Member' : 'Add Staff Member' }}</h3>

          <div class="form-error" *ngIf="formError()">{{ formError() }}</div>

          <form [formGroup]="form" (ngSubmit)="submit()">
            <div class="form-group">
              <label [for]="isEdit() ? 'edit-staff-name' : 'staff-name'">Name *</label>
              <input [id]="isEdit() ? 'edit-staff-name' : 'staff-name'" class="field-control" type="text" formControlName="name" placeholder="Full name" />
              <div class="field-error" *ngIf="form.get('name')?.touched && form.get('name')?.hasError('required')">Name is required.</div>
            </div>

            <div class="form-group">
              <label [for]="isEdit() ? 'edit-staff-phone' : 'staff-phone'">Phone (10 digits) *</label>
              <input [id]="isEdit() ? 'edit-staff-phone' : 'staff-phone'" class="field-control" type="text" formControlName="phone" placeholder="10-digit phone number" maxlength="10" />
              <div class="field-error" *ngIf="form.get('phone')?.touched && form.get('phone')?.hasError('required')">Phone is required.</div>
              <div class="field-error" *ngIf="form.get('phone')?.touched && form.get('phone')?.hasError('pattern') && !form.get('phone')?.hasError('required')">Phone must be exactly 10 digits.</div>
            </div>

            <div class="form-group">
              <label [for]="isEdit() ? 'edit-staff-role' : 'staff-role-select'">Role *</label>
              <select [id]="isEdit() ? 'edit-staff-role' : 'staff-role-select'" class="field-select" formControlName="role">
                <option value="" disabled *ngIf="!isEdit()">Select a role</option>
                <option value="SHOP_STAFF">Staff</option>
              </select>
              <div class="role-disabled-note" *ngIf="isEdit() && disableRoleForSelf()">Cannot change your own role</div>
            </div>

            <div class="form-group">
              <label [for]="isEdit() ? 'edit-staff-email' : 'staff-email'">Email (optional)</label>
              <input [id]="isEdit() ? 'edit-staff-email' : 'staff-email'" class="field-control" type="email" formControlName="email" placeholder="Email address" />
              <div class="field-error" *ngIf="form.get('email')?.touched && form.get('email')?.hasError('email')">Enter a valid email address.</div>
            </div>

            <div class="modal-actions">
              <button type="button" class="ghost-btn" (click)="close.emit()" [disabled]="submitting()">Cancel</button>
              <button type="submit" class="primary-btn" [disabled]="form.invalid || submitting()">
                {{ submitting() ? (isEdit() ? 'Saving...' : 'Creating...') : (isEdit() ? 'Save Changes' : 'Create Staff') }}
              </button>
            </div>
          </form>
        </ng-template>
      </div>
    </div>
  `
})
export class StaffFormModalComponent {
  private readonly api = inject(BusinessApiService);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);

  open = input(false);
  isEdit = input(false);
  editItem = input<BusinessStaffItem | null>(null);
  disableRoleForSelf = input(false);

  close = output<void>();
  saved = output<void>();

  submitting = signal(false);
  resending = signal(false);
  formError = signal('');
  createdStaff: StaffCreatedResponse | null = null;

  form = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    phone: ['', [Validators.required, Validators.pattern(/^\d{10}$/)]],
    role: ['', [Validators.required]],
    email: ['', [Validators.email]]
  });

  constructor() {
    // allowSignalWrites: this effect reacts to input signals (editItem/isEdit) and
    // synchronously resets the reactive form and clears formError/createdStaff. Those
    // signal writes are intentional and idempotent, so they are permitted here.
    // Without this flag Angular throws NG0600 the moment the modal is instantiated.
    effect(() => {
      const item = this.editItem();
      if (item && this.isEdit()) {
        this.form.reset({
          name: item.name,
          phone: item.whatsappNumber || item.loginId,
          role: item.role as StaffRole,
          email: item.email || ''
        });
        if (this.disableRoleForSelf()) {
          this.form.get('role')?.disable();
        } else {
          this.form.get('role')?.enable();
        }
      } else if (!this.isEdit()) {
        this.form.reset({ name: '', phone: '', role: '', email: '' });
      }
      this.formError.set('');
      this.createdStaff = null;
    }, { allowSignalWrites: true });
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) return;

    this.submitting.set(true);
    this.formError.set('');

    const formValue = this.form.value;
    const payload = {
      name: formValue.name!,
      phone: formValue.phone!,
      role: formValue.role! as StaffRole,
      ...(formValue.email ? { email: formValue.email } : {})
    };

    if (this.isEdit()) {
      const item = this.editItem();
      if (!item) return;
      this.api.updateStaff(item.userId, payload).subscribe({
        next: () => {
          this.submitting.set(false);
          this.saved.emit();
          this.close.emit();
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.handleHttpError(err);
        }
      });
    } else {
      this.api.createStaff(payload).subscribe({
        next: (response) => {
          this.createdStaff = response;
          this.submitting.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.handleHttpError(err);
        }
      });
    }
  }

  private handleHttpError(err: HttpErrorResponse): void {
    if (err.status === 409) {
      this.formError.set('This phone number is already registered. Please use a different number.');
    } else if (err.status === 400 && err.error?.fields) {
      this.formError.set(Object.values(err.error.fields).join('. '));
    } else {
      this.formError.set(err.error?.message || 'Failed to save staff member. Please try again.');
    }
  }

  resendOtp(): void {
    const phone = this.createdStaff?.phone;
    if (!phone || this.resending()) return;
    this.resending.set(true);
    this.auth.requestPasswordOtp(phone).subscribe({
      next: () => {
        this.resending.set(false);
        if (this.createdStaff) {
          this.createdStaff = { ...this.createdStaff, otpSent: true };
        }
        this.toast.show('A new code was sent on WhatsApp.', 'success');
      },
      error: () => {
        this.resending.set(false);
        this.toast.show('Could not send the code. Please try again.', 'error');
      }
    });
  }
}
