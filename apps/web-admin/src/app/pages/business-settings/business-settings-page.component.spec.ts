import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { BusinessSettingsPageComponent } from './business-settings-page.component';
import { BusinessApiService } from '../../core/services/business-api.service';
import { ToastService } from '../../core/services/toast.service';

/**
 * The settings screen is one scroll with a secondary configuration nav. These
 * specs pin the contract the redesign relies on: the nav lists the sections that
 * actually exist, it scrolls to and marks the selected one, and the single Save
 * action refuses invalid input before it reaches the API.
 */
function profileResponse(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    shopName: 'Test Kitchen',
    shopAddress: '12 MG Road, Bengaluru',
    whatsappNumber: '9876543210',
    email: 'owner@test.com',
    logoUrl: '',
    gstEnabled: true,
    gstin: '29ABCDE1234F1Z5',
    gstPercentage: 5,
    customTaxName: '',
    customTaxPercentage: 0,
    fssaiNumber: '12345678901234',
    fssaiExpiryDate: '',
    gstExpiryDate: '',
    upiEnabled: true,
    upiHandle: 'test@upi',
    upiMobile: '9876543210',
    cashEnabled: true,
    posEnabled: false,
    orderPaymentFlowMode: 'pay_before_food',
    invoiceFooter: 'Thank you',
    reviewUrl: 'https://g.page/r/test/review',
    ...overrides
  };
}

function apiStub(overrides: Record<string, unknown> = {}) {
  return {
    getProfile: jasmine.createSpy('getProfile').and.returnValue(of(profileResponse(overrides))),
    updateProfile: jasmine.createSpy('updateProfile').and.returnValue(of(profileResponse(overrides))),
    uploadLogo: jasmine.createSpy('uploadLogo').and.returnValue(of({ url: '' })),
    deleteLogo: jasmine.createSpy('deleteLogo').and.returnValue(of(undefined)),
    getPaymentConfig: jasmine.createSpy('getPaymentConfig').and.returnValue(of({})),
    updatePaymentConfig: jasmine.createSpy('updatePaymentConfig').and.returnValue(of({})),
    lookupGst: jasmine.createSpy('lookupGst').and.returnValue(of({})),
    lookupFssai: jasmine.createSpy('lookupFssai').and.returnValue(of({}))
  };
}

describe('BusinessSettingsPageComponent — configuration shell', () => {
  let fixture: ComponentFixture<BusinessSettingsPageComponent>;
  let component: BusinessSettingsPageComponent;
  let api: ReturnType<typeof apiStub>;
  let toast: jasmine.SpyObj<ToastService>;

  function mount(overrides: Record<string, unknown> = {}): void {
    api = apiStub(overrides);
    toast = jasmine.createSpyObj('ToastService', ['show', 'error', 'success', 'info']);

    TestBed.configureTestingModule({
      imports: [BusinessSettingsPageComponent],
      providers: [
        provideHttpClient(),
        provideRouter([]),
        { provide: BusinessApiService, useValue: api },
        { provide: ToastService, useValue: toast }
      ]
    });

    fixture = TestBed.createComponent(BusinessSettingsPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function root(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    spyOn(HTMLElement.prototype, 'scrollIntoView').and.callFake(() => undefined);
    mount();
  });

  it('lists exactly the sections that exist on the page, in order', () => {
    const links = Array.from(root().querySelectorAll('.settings-nav__list a'));
    expect(links.map((link) => link.textContent?.trim()))
      .toEqual([
        'Restaurant Profile',
        'Tax & FSSAI',
        'Payment Configuration',
        'Invoice & Billing',
        'WhatsApp & SMS',
        'Review & Feedback',
      ]);

    expect(root().querySelector('#profile')).toBeTruthy();
    expect(root().querySelector('#tax')).toBeTruthy();
    expect(root().querySelector('#payments')).toBeTruthy();
  });

  it('links Menu Configuration to the existing menu workspace instead of duplicating it', () => {
    const menu = root().querySelector('.settings-nav__link--out') as HTMLAnchorElement;
    expect(menu.getAttribute('href')).toBe('/business/menu');
    expect(menu.textContent).toContain('Menu Configuration');
  });

  it('marks the selected section and scrolls to it', () => {
    const links = Array.from(root().querySelectorAll<HTMLAnchorElement>('.settings-nav__list a'));
    links[1].click();
    fixture.detectChanges();

    expect(component.activeSection()).toBe('tax');
    expect(links[1].classList).toContain('settings-nav__link--active');
    expect(links[1].getAttribute('aria-current')).toBe('true');
    expect(links[0].getAttribute('aria-current')).toBeNull();
    expect(HTMLElement.prototype.scrollIntoView).toHaveBeenCalled();
  });

  it('renders the loaded profile into the grouped form', async () => {
    // Template-driven ngModel writes into the control on a microtask.
    await fixture.whenStable();
    fixture.detectChanges();
    const name = root().querySelector('#field-shopName') as HTMLInputElement;
    const address = root().querySelector('#field-shopAddress') as HTMLTextAreaElement;
    expect(name.value).toBe('Test Kitchen');
    expect(address.value).toBe('12 MG Road, Bengaluru');
  });

  it('refuses to save without a restaurant name and says why', () => {
    component.profile.update((p) => (p ? { ...p, shopName: '   ' } : p));
    component.saveProfile();
    fixture.detectChanges();

    expect(api.updateProfile).not.toHaveBeenCalled();
    expect(root().textContent).toContain('Restaurant name is required.');
    expect(toast.show).toHaveBeenCalledWith('Fix the highlighted fields before saving.', 'error');
  });

  it('rejects a review link that is not a web URL', () => {
    component.profile.update((p) => (p ? { ...p, reviewUrl: 'g.page/review' } : p));
    component.saveProfile();
    fixture.detectChanges();

    expect(api.updateProfile).not.toHaveBeenCalled();
    expect(root().textContent).toContain('Enter a full link starting with https://.');
  });

  it('saves the whole profile and never sends a field the API ignores', () => {
    component.profile.update((p) => (p ? { ...p, invoiceFooter: 'See you soon' } : p));
    component.saveProfile();

    expect(api.updateProfile).toHaveBeenCalledTimes(1);
    const payload = api.updateProfile.calls.mostRecent().args[0];
    expect(payload.invoiceFooter).toBe('See you soon');
    expect(payload.shopName).toBe('Test Kitchen');
    expect(payload.reviewUrl).toBe('https://g.page/r/test/review');
    expect('country' in payload).toBeFalse();
  });

  it('keeps digits only when a pasted phone number carries a country code', () => {
    component.onPhoneChange('+91 98765 43210');
    expect(component.profile()?.whatsappNumber).toBe('9876543210');
  });

  it('clears a field error as soon as that field is edited', () => {
    component.profile.update((p) => (p ? { ...p, shopName: '' } : p));
    component.saveProfile();
    expect(component.errors()['shopName']).toBeTruthy();

    component.profile.update((p) => (p ? { ...p, shopName: 'New name' } : p));
    component.onFieldChange('shopName');
    expect(component.errors()['shopName']).toBeUndefined();
  });
});
