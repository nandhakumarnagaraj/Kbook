import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { StaffFormModalComponent } from './staff/staff-form-modal.component';
import { StaffPermissionsModalComponent } from './staff/staff-permissions-modal.component';
import { TerminalsPageComponent } from './terminals/terminals-page.component';
import { TaxSettingsSectionComponent } from './business-settings/tax-settings-section.component';
import { InventoryPageComponent } from './inventory/inventory-page.component';
import { BusinessApiService } from '../core/services/business-api.service';
import { AuthService } from '../core/auth/auth.service';
import { ToastService } from '../core/services/toast.service';

/**
 * Field-level invalid feedback, on the forms that were still missing it.
 *
 * The businesses create form set the pattern: a field states its own state
 * (`aria-invalid`) the moment it is rejected, and one shared rule in styles.css
 * turns that into the danger border, so the message beside it has something to
 * point at. Staff modals, the terminals dialogs, tax verification and the
 * inventory workflows had the message (or the disabled button) without the
 * field state.
 *
 * The rule these specs pin is the same everywhere: a resting field makes no
 * claim, a field that the user has left empty or filled invalidly flags itself,
 * and a valid value clears it again. A field that is red before the user has
 * touched it would be worse than no feedback at all, so every one of these
 * specs checks the resting state first.
 */
const FLAGGED_BORDER = 'rgb(192, 57, 43)';   // --kb-color-danger
const RESTING_BORDER = 'rgb(225, 217, 202)'; // --line-strong, on .field-control / .field-select

const authStub = {
  session: () => ({ role: 'OWNER', restaurantId: 1, loginId: 'owner', restaurantName: 'Test Kitchen' }),
} as unknown as AuthService;

const routerStub = { navigate: () => Promise.resolve(true), events: of(), createUrlTree: () => null, serializeUrl: () => '' };

const attached: HTMLElement[] = [];

afterEach(() => {
  for (const host of attached.splice(0)) host.remove();
});

function mount<T>(component: unknown, providers: unknown[] = [], initialDetect = true): ComponentFixture<T> {
  // Each form is mounted in the same spec, and a testing module can only be
  // configured once — so tear the previous one down instead of reusing it.
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    imports: [component as never],
    providers: [
      provideHttpClient(),
      { provide: AuthService, useValue: authStub },
      { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['show', 'error', 'success']) },
      { provide: Router, useValue: routerStub },
      ...(providers as never[]),
    ],
  });
  const fixture = TestBed.createComponent(component as never) as ComponentFixture<T>;
  document.body.appendChild(fixture.nativeElement as HTMLElement);
  attached.push(fixture.nativeElement as HTMLElement);
  // `initialDetect: false` is for the components with required inputs, which
  // Angular refuses to render before the first value is supplied.
  if (initialDetect) fixture.detectChanges();
  return fixture;
}

/**
 * The border colour of an element, read after forcing a style recalc.
 *
 * This Karma/Chrome combination keeps the computed style it cached before an
 * attribute-only mutation, so a node that was styled while `aria-invalid` was
 * still absent reads its old value until it is re-inserted. The cascade itself
 * is fine — a freshly inserted node resolves to the new value — so the recalc is
 * what makes the reading truthful rather than a way to avoid a failure.
 */
function borderAfterRecalc(el: HTMLElement): string {
  const parent = el.parentElement!;
  const next = el.nextSibling;
  el.remove();
  parent.insertBefore(el, next);
  return getComputedStyle(el).borderTopColor;
}

/** Leaves the field the way a user does: the blur is what marks it touched. */
function leave(el: HTMLElement): void {
  el.dispatchEvent(new Event('blur'));
}

/** Types into a field the way a user does, so ngModel and validators see it. */
function type(el: HTMLInputElement, value: string): void {
  el.value = value;
  el.dispatchEvent(new Event('input'));
}

function flagged(field: HTMLElement): boolean {
  return field.getAttribute('aria-invalid') === 'true';
}

describe('field-level invalid feedback, remaining forms', () => {
  it('staff form modal: the required name flags itself once it is left empty', () => {
    const fixture = mount<StaffFormModalComponent>(StaffFormModalComponent);
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    const field = fixture.nativeElement.querySelector('#staff-name') as HTMLInputElement;
    expect(field).withContext('the create form renders its name field').toBeTruthy();

    expect(flagged(field)).withContext('a resting field makes no claim').toBeFalse();
    expect(borderAfterRecalc(field)).toBe(RESTING_BORDER);

    leave(field);
    fixture.detectChanges();
    expect(flagged(field)).withContext('an empty required field flags itself').toBeTrue();
    expect(borderAfterRecalc(field))
      .withContext('and the shared rule turns that into the danger border')
      .toBe(FLAGGED_BORDER);

    type(field, 'Ravi');
    fixture.detectChanges();
    expect(flagged(field)).withContext('a valid value clears the flag').toBeFalse();
    expect(borderAfterRecalc(field)).toBe(RESTING_BORDER);

    fixture.destroy();
  });

  it('staff permissions modal: the template name flags itself after blur, not on open', () => {
    const fixture = mount<StaffPermissionsModalComponent>(StaffPermissionsModalComponent);
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
    fixture.componentInstance.showCreateTemplate.set(true);
    fixture.detectChanges();

    const field = fixture.nativeElement.querySelector('.modal-backdrop .field-control') as HTMLInputElement;
    expect(field).withContext('the save-as-template form renders its name field').toBeTruthy();

    expect(flagged(field)).withContext('opening the form does not flag an empty field').toBeFalse();

    leave(field);
    fixture.detectChanges();
    expect(flagged(field)).toBeTrue();
    expect(borderAfterRecalc(field)).toBe(FLAGGED_BORDER);

    type(field, 'Senior Cashier');
    fixture.detectChanges();
    expect(flagged(field)).toBeFalse();
    expect(borderAfterRecalc(field)).toBe(RESTING_BORDER);

    // A name of spaces is still not a name: the flag follows the rule Save
    // applies, not the looser one a required validator would.
    type(field, '   ');
    leave(field);
    fixture.detectChanges();
    expect(flagged(field)).toBeTrue();
    expect(borderAfterRecalc(field)).toBe(FLAGGED_BORDER);

    fixture.destroy();
  });

  it('terminals: renaming to nothing, and opening recovery without a device id, both flag the field', () => {
    const api = jasmine.createSpyObj('BusinessApiService', ['getTerminals', 'getTerminalRequests']);
    api.getTerminals.and.returnValue(of([
      { id: 1, terminalName: 'Front Counter', status: 'ACTIVE', isActive: true, deviceId: 'dev-1', terminalSeries: 'T-1', isPrimary: true },
    ]));
    api.getTerminalRequests.and.returnValue(of([]));

    const fixture = mount<TerminalsPageComponent>(TerminalsPageComponent, [
      { provide: BusinessApiService, useValue: api },
    ]);
    const terminal = fixture.componentInstance.terminals()[0];
    expect(terminal).withContext('the page should load its terminal').toBeTruthy();

    // Rename: the field starts with the current name, so it is only invalid once
    // the user empties it.
    fixture.componentInstance.startEdit(terminal);
    fixture.detectChanges();
    const rename = fixture.nativeElement.querySelector('input.field-control') as HTMLInputElement;
    expect(rename).withContext('the rename field replaces the name cell').toBeTruthy();
    expect(flagged(rename)).withContext('a pre-filled rename is not flagged').toBeFalse();

    type(rename, '   ');
    leave(rename);
    fixture.detectChanges();
    expect(flagged(rename)).withContext('a blank name flags itself').toBeTrue();
    expect(borderAfterRecalc(rename)).toBe(FLAGGED_BORDER);

    fixture.componentInstance.cancelEdit();
    fixture.detectChanges();

    // Recovery: the replacement device id is required to rebind the terminal.
    fixture.componentInstance.startRecovery(terminal);
    fixture.detectChanges();
    const deviceId = fixture.nativeElement.querySelector('#recovery-device-id') as HTMLInputElement;
    expect(deviceId).withContext('the recovery dialog renders its device id field').toBeTruthy();
    expect(flagged(deviceId)).withContext('opening recovery does not flag the empty field').toBeFalse();

    leave(deviceId);
    fixture.detectChanges();
    expect(flagged(deviceId)).toBeTrue();
    expect(borderAfterRecalc(deviceId)).toBe(FLAGGED_BORDER);

    type(deviceId, 'dev-2');
    fixture.detectChanges();
    expect(flagged(deviceId)).toBeFalse();
    expect(borderAfterRecalc(deviceId)).toBe(RESTING_BORDER);

    fixture.destroy();
  });

  it('tax settings: a GSTIN the registry rejects flags the field, a verified one does not', () => {
    const rejected = jasmine.createSpyObj('BusinessApiService', ['lookupGst']);
    rejected.lookupGst.and.returnValue(of({ valid: false, error: 'GSTIN is invalid or unregistered.' }));
    const rejectedFixture = mount<TaxSettingsSectionComponent>(TaxSettingsSectionComponent, [
      { provide: BusinessApiService, useValue: rejected },
    ], false);
    rejectedFixture.componentRef.setInput('tax', { gstEnabled: true, gstin: '29AAAAA0000A1Z5' });
    rejectedFixture.detectChanges();

    const field = rejectedFixture.nativeElement.querySelector('input.field-control') as HTMLInputElement;
    expect(field).toBeTruthy();
    expect(flagged(field)).withContext('an unverified GSTIN makes no claim yet').toBeFalse();

    (rejectedFixture.nativeElement.querySelector('button.action-btn') as HTMLButtonElement).click();
    rejectedFixture.detectChanges();

    expect(flagged(field)).withContext('a rejected GSTIN flags its field').toBeTrue();
    expect(borderAfterRecalc(field)).toBe(FLAGGED_BORDER);
    expect((rejectedFixture.nativeElement as HTMLElement).textContent)
      .withContext('and the rejection message is what the border points at')
      .toContain('GSTIN is invalid or unregistered.');
    rejectedFixture.destroy();

    const verified = jasmine.createSpyObj('BusinessApiService', ['lookupGst']);
    verified.lookupGst.and.returnValue(of({ valid: true, businessName: 'Test Traders', expiryDate: '2030-01-01' }));
    const verifiedFixture = mount<TaxSettingsSectionComponent>(TaxSettingsSectionComponent, [
      { provide: BusinessApiService, useValue: verified },
    ], false);
    verifiedFixture.componentRef.setInput('tax', { gstEnabled: true, gstin: '29AAAAA0000A1Z5' });
    verifiedFixture.detectChanges();

    const okField = verifiedFixture.nativeElement.querySelector('input.field-control') as HTMLInputElement;
    (verifiedFixture.nativeElement.querySelector('button.action-btn') as HTMLButtonElement).click();
    verifiedFixture.detectChanges();

    expect(flagged(okField)).withContext('a verified GSTIN is never flagged').toBeFalse();
    expect(borderAfterRecalc(okField)).toBe(RESTING_BORDER);
    verifiedFixture.destroy();
  });

  it('inventory: the material name and the stock-in quantity flag themselves', () => {
    const api = jasmine.createSpyObj('BusinessApiService', ['getInventoryMaterials', 'getVendors']);
    api.getInventoryMaterials.and.returnValue(of([
      { id: 1, name: 'Rice', unit: 'kg', stockQuantity: 5, lowStockThreshold: 1, costPerUnit: 90 },
    ]));
    api.getVendors.and.returnValue(of([]));

    const fixture = mount<InventoryPageComponent>(InventoryPageComponent, [
      { provide: BusinessApiService, useValue: api },
    ]);

    // Add Material: the name is required — Save stays disabled until it is
    // filled. Opened through the real control, because this page is OnPush and
    // a direct method call is not what marks the view dirty.
    const host = fixture.nativeElement as HTMLElement;
    (host.querySelector('button.header-action-btn--primary') as HTMLButtonElement).click();
    fixture.detectChanges();
    const name = host.querySelector('#material-name') as HTMLInputElement;
    expect(name).withContext('the add-material modal renders its name field').toBeTruthy();
    expect(flagged(name)).withContext('opening the modal does not flag the empty name').toBeFalse();

    leave(name);
    fixture.detectChanges();
    expect(flagged(name)).toBeTrue();
    expect(borderAfterRecalc(name)).toBe(FLAGGED_BORDER);

    type(name, 'Basmati Rice');
    fixture.detectChanges();
    expect(flagged(name)).toBeFalse();
    expect(borderAfterRecalc(name)).toBe(RESTING_BORDER);
    // Cancel out of the modal the way a user does.
    const cancel = Array.from(host.querySelectorAll('button.ghost-btn'))
      .find((button) => (button.textContent || '').includes('Cancel')) as HTMLButtonElement;
    cancel.click();
    fixture.detectChanges();

    // Stock In: quantity must be positive, and the record button says so by
    // staying disabled. The field has to explain what is missing.
    const stockActions = Array.from(host.querySelectorAll('button'))
      .find((button) => (button.textContent || '').includes('Stock actions')) as HTMLButtonElement;
    expect(stockActions).withContext('the material card offers its stock actions').toBeTruthy();
    stockActions.click();
    fixture.detectChanges();
    const stockInQty = (host.querySelector('input[type="number"].field-control') as HTMLInputElement) ?? null;
    expect(stockInQty)
      .withContext('the stock-in panel renders its quantity field first')
      .toBeTruthy();
    expect(flagged(stockInQty)).withContext('an untouched quantity is not flagged').toBeFalse();

    type(stockInQty, '0');
    leave(stockInQty);
    fixture.detectChanges();
    expect(flagged(stockInQty)).withContext('zero is not a stock-in quantity').toBeTrue();
    expect(borderAfterRecalc(stockInQty)).toBe(FLAGGED_BORDER);

    type(stockInQty, '12');
    fixture.detectChanges();
    expect(flagged(stockInQty)).toBeFalse();
    expect(borderAfterRecalc(stockInQty)).toBe(RESTING_BORDER);

    fixture.destroy();
  });
});
