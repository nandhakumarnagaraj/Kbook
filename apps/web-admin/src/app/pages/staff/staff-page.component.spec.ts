import { TestBed, ComponentFixture } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { StaffPageComponent } from './staff-page.component';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { BusinessStaffItem } from '../../core/models/api.models';

describe('StaffPageComponent — Reset Password UI', () => {
  let fixture: ComponentFixture<StaffPageComponent>;
  let component: StaffPageComponent;
  let api: jasmine.SpyObj<BusinessApiService>;
  let auth: jasmine.SpyObj<AuthService>;
  let toast: jasmine.SpyObj<ToastService>;

  const mockStaff: BusinessStaffItem[] = [
    {
      userId: 101,
      name: 'Owner User',
      loginId: '9876543210',
      email: 'owner@example.com',
      whatsappNumber: '9876543210',
      role: 'OWNER',
      active: true,
      updatedAt: 1700000000000,
    },
    {
      userId: 102,
      name: 'Ramesh Staff',
      loginId: '9876543211',
      email: 'ramesh@example.com',
      whatsappNumber: '9876543211',
      role: 'SHOP_STAFF',
      active: true,
      updatedAt: 1700000000000,
    },
    {
      userId: 103,
      name: 'Suresh Inactive',
      loginId: '9876543212',
      email: null,
      whatsappNumber: '9876543212',
      role: 'SHOP_STAFF',
      active: false,
      updatedAt: 1700000000000,
    },
  ];

  beforeEach(() => {
    api = jasmine.createSpyObj<BusinessApiService>('BusinessApiService', [
      'getStaff',
      'resendStaffCredentials',
      'activateStaff',
      'deactivateStaff',
      'revokeStaffSessions',
    ]);
    api.getStaff.and.returnValue(of(mockStaff));
    api.resendStaffCredentials.and.returnValue(of({ userId: 102, phone: '9876543211' }));

    auth = jasmine.createSpyObj<AuthService>('AuthService', ['session']);
    auth.session.and.returnValue({
      token: 'mock-jwt-token',
      refreshToken: 'mock-refresh-token',
      role: 'OWNER',
      restaurantId: 1,
      userName: 'Owner User',
      loginId: '9876543210',
      userEmail: 'owner@example.com',
      whatsappNumber: '9876543210',
    });

    toast = jasmine.createSpyObj<ToastService>('ToastService', ['show']);

    TestBed.configureTestingModule({
      imports: [StaffPageComponent],
      providers: [
        { provide: BusinessApiService, useValue: api },
        { provide: AuthService, useValue: auth },
        { provide: ToastService, useValue: toast },
      ],
    });

    fixture = TestBed.createComponent(StaffPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders Reset Password button for active staff, but not for owner or inactive staff', () => {
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    const resetButtons = buttons.filter((b) => b.textContent?.trim().includes('Reset Password'));

    // Should render for active staff (Ramesh) in table and mobile card
    expect(resetButtons.length).toBeGreaterThan(0);

    // Should not render Reset Password for the owner row
    const ownerRow = fixture.nativeElement.querySelector('tbody tr:first-child');
    expect(ownerRow?.textContent).toContain('Owner');
    expect(ownerRow?.querySelector('.action-cell button')).toBeNull();
  });

  it('opens confirmation dialog on clicking Reset Password', () => {
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    const resetBtn = buttons.find((b) => b.textContent?.trim().includes('Reset Password'));
    expect(resetBtn).toBeTruthy();

    resetBtn?.click();
    fixture.detectChanges();

    expect(component.staffToResetPassword).toEqual(mockStaff[1]);
    const dialog = fixture.nativeElement.querySelector('app-confirm-dialog');
    expect(dialog).toBeTruthy();
  });

  it('calls resendStaffCredentials on confirmation and notifies toast on success', () => {
    const ramesh = mockStaff[1];
    component.requestResetPassword(ramesh);
    component.confirmResetPassword();

    expect(api.resendStaffCredentials).toHaveBeenCalledWith(102);
    expect(toast.show).toHaveBeenCalledWith(
      jasmine.stringMatching(/password was generated and sent to Ramesh Staff/i),
      'success'
    );
  });

  it('shows error toast when resendStaffCredentials fails', () => {
    api.resendStaffCredentials.and.returnValue(
      throwError(() => ({ error: { message: 'Network error or WhatsApp delivery failed' } }))
    );

    const ramesh = mockStaff[1];
    component.requestResetPassword(ramesh);
    component.confirmResetPassword();

    expect(api.resendStaffCredentials).toHaveBeenCalledWith(102);
    expect(toast.show).toHaveBeenCalledWith(
      'Network error or WhatsApp delivery failed',
      'error'
    );
  });
});
