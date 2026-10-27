import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { StaffFormModalComponent } from './staff/staff-form-modal.component';
import { StaffPermissionsModalComponent } from './staff/staff-permissions-modal.component';
import { TerminalsPageComponent } from './terminals/terminals-page.component';
import { TaxSettingsSectionComponent } from './business-settings/tax-settings-section.component';
import { InventoryPageComponent } from './inventory/inventory-page.component';
import { BusinessesPageComponent } from './businesses/businesses-page.component';
import { BusinessApiService } from '../core/services/business-api.service';
import { AdminApiService } from '../core/services/admin-api.service';
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
/**
 * The border a field carries, read from the tokens rather than from literals.
 *
 * These two used to be hard-coded rgb() values from the palette of the day, so a
 * palette change failed eleven specs whose actual guarantee — "a rejected field
 * takes the danger border, an untouched one takes the ordinary border" — was
 * still true. Resolving the token in the browser keeps that guarantee and stops
 * the spec from freezing a hex.
 */
function tokenBorder(name: string): string {
  const probe = document.createElement('div');
  probe.style.borderColor = `var(${name})`;
  document.body.appendChild(probe);
  const value = getComputedStyle(probe).borderTopColor;
  probe.remove();
  return value;
}
const flaggedBorder = () => tokenBorder('--kb-color-danger');
const restingBorder = () => tokenBorder('--kb-border-control');

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
    expect(borderAfterRecalc(field)).toBe(restingBorder());

    leave(field);
    fixture.detectChanges();
    expect(flagged(field)).withContext('an empty required field flags itself').toBeTrue();
    expect(borderAfterRecalc(field))
      .withContext('and the shared rule turns that into the danger border')
      .toBe(flaggedBorder());

    type(field, 'Ravi');
    fixture.detectChanges();
    expect(flagged(field)).withContext('a valid value clears the flag').toBeFalse();
    expect(borderAfterRecalc(field)).toBe(restingBorder());

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
    expect(borderAfterRecalc(field)).toBe(flaggedBorder());

    type(field, 'Senior Cashier');
    fixture.detectChanges();
    expect(flagged(field)).toBeFalse();
    expect(borderAfterRecalc(field)).toBe(restingBorder());

    // A name of spaces is still not a name: the flag follows the rule Save
    // applies, not the looser one a required validator would.
    type(field, '   ');
    leave(field);
    fixture.detectChanges();
    expect(flagged(field)).toBeTrue();
    expect(borderAfterRecalc(field)).toBe(flaggedBorder());
    expect(messageFor(field)).withContext('and the flag explains itself').toBe('Template name is required.');

    expect(danglingReferences(fixture.nativeElement)).toEqual([]);
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
    expect(borderAfterRecalc(rename)).toBe(flaggedBorder());
    expect(messageFor(rename)).withContext('and says what is missing').toBe('Terminal name is required.');

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
    expect(borderAfterRecalc(deviceId)).toBe(flaggedBorder());
    expect(messageFor(deviceId)).toBe('Enter the device ID shown on the new device.');

    type(deviceId, 'dev-2');
    fixture.detectChanges();
    expect(flagged(deviceId)).toBeFalse();
    expect(borderAfterRecalc(deviceId)).toBe(restingBorder());
    expect(deviceId.getAttribute('aria-describedby'))
      .withContext('a filled-in device id has nothing to answer for')
      .toBeNull();

    expect(danglingReferences(fixture.nativeElement)).toEqual([]);
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
    expect(borderAfterRecalc(field)).toBe(flaggedBorder());
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
    expect(borderAfterRecalc(okField)).toBe(restingBorder());
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
    expect(borderAfterRecalc(name)).toBe(flaggedBorder());
    expect(messageFor(name)).withContext('the flag explains itself').toBe('Material name is required.');

    type(name, 'Basmati Rice');
    fixture.detectChanges();
    expect(flagged(name)).toBeFalse();
    expect(borderAfterRecalc(name)).toBe(restingBorder());
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
    expect(borderAfterRecalc(stockInQty)).toBe(flaggedBorder());
    expect(messageFor(stockInQty))
      .withContext('a disabled button is not an explanation')
      .toBe('Enter a quantity greater than zero.');

    type(stockInQty, '12');
    fixture.detectChanges();
    expect(flagged(stockInQty)).toBeFalse();
    expect(borderAfterRecalc(stockInQty)).toBe(restingBorder());

    expect(danglingReferences(host)).toEqual([]);
    fixture.destroy();
  });
});

/**
 * The other half of field-level feedback: a flagged field has to say *which* rule it
 * broke. The red border marks the control, but only a message -- named by the control's
 * `aria-describedby` -- tells a screen reader why. The UPI handle already worked this
 * way; these specs pin that every field with a message beside it names it, and that no
 * field ever names an element that is not in the document.
 */
function describedByIds(el: Element): string[] {
  return (el.getAttribute('aria-describedby') ?? '').split(/\s+/).filter(Boolean);
}

/** What a screen reader resolves from the field, or null when it names nothing usable. */
function messageFor(el: Element): string | null {
  const ids = describedByIds(el);
  if (!ids.length) return null;
  const texts = ids.map((id) => document.getElementById(id)?.textContent?.replace(/\s+/g, ' ').trim() ?? null);
  return texts.every((text) => text) ? texts.join(' ') : null;
}

/** A reference to an id that is not on screen announces nothing. */
function danglingReferences(host: HTMLElement): string[] {
  return Array.from(host.querySelectorAll('[aria-describedby]'))
    .filter((el) => describedByIds(el).some((id) => !document.getElementById(id)))
    .map((el) => `${el.tagName.toLowerCase()}${el.id ? `#${el.id}` : ''} names "${el.getAttribute('aria-describedby')}"`);
}

describe('a flagged field names the rule it broke', () => {
  it('staff form modal: the name and the phone each name their own message', () => {
    const fixture = mount<StaffFormModalComponent>(StaffFormModalComponent);
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    const name = fixture.nativeElement.querySelector('#staff-name') as HTMLInputElement;
    expect(name.getAttribute('aria-describedby'))
      .withContext('a resting field names nothing')
      .toBeNull();

    leave(name);
    fixture.detectChanges();
    expect(messageFor(name)).toBe('Name is required.');

    const phone = fixture.nativeElement.querySelector('#staff-phone') as HTMLInputElement;
    leave(phone);
    fixture.detectChanges();
    expect(messageFor(phone)).withContext('the empty rule').toBe('Phone is required.');

    type(phone, '12345');
    fixture.detectChanges();
    expect(messageFor(phone)).withContext('the ten-digit rule replaces it').toBe('Phone must be exactly 10 digits.');

    type(phone, '9876543210');
    fixture.detectChanges();
    expect(phone.getAttribute('aria-describedby'))
      .withContext('a field that has nothing to answer for names nothing')
      .toBeNull();

    expect(danglingReferences(fixture.nativeElement)).toEqual([]);
    fixture.destroy();
  });

  it('businesses: every field in the create form names its message', () => {
    const admin = jasmine.createSpyObj('AdminApiService', ['getBusinesses']);
    admin.getBusinesses.and.returnValue(of([]));

    const fixture = mount<BusinessesPageComponent>(BusinessesPageComponent, [
      { provide: AdminApiService, useValue: admin },
    ]);
    // Opened through the real control: this page is OnPush, so assigning the flag does
    // not mark the view dirty and the modal would never be stamped into the DOM.
    const host = fixture.nativeElement as HTMLElement;
    (host.querySelector('button.header-action-btn--primary') as HTMLButtonElement).click();
    fixture.detectChanges();

    const shop = host.querySelector('#create-shop-name') as HTMLInputElement;
    leave(shop);
    fixture.detectChanges();
    expect(messageFor(shop)).toBe('Restaurant name is required.');

    const phone = host.querySelector('#create-owner-phone') as HTMLInputElement;
    leave(phone);
    fixture.detectChanges();
    expect(messageFor(phone)).toBe('Phone number is required.');
    type(phone, '12345');
    fixture.detectChanges();
    expect(messageFor(phone)).toBe('Must be exactly 10 digits.');

    const password = host.querySelector('#create-initial-pwd') as HTMLInputElement;
    leave(password);
    fixture.detectChanges();
    expect(messageFor(password)).toBe('Initial password is required.');
    type(password, '12345');
    fixture.detectChanges();
    expect(messageFor(password)).toBe('Password must be at least 6 characters.');

    const email = host.querySelector('#create-owner-email') as HTMLInputElement;
    type(email, 'not-an-email');
    leave(email);
    fixture.detectChanges();
    expect(messageFor(email)).toBe('Enter a valid email address.');

    expect(danglingReferences(host)).toEqual([]);
    fixture.destroy();
  });

  it('terminals: a refused recovery names the reason the server gave', () => {
    const api = jasmine.createSpyObj('BusinessApiService', ['getTerminals', 'getTerminalRequests', 'recoverTerminal']);
    api.getTerminals.and.returnValue(of([
      { id: 1, terminalName: 'Front Counter', status: 'ACTIVE', isActive: true, deviceId: 'dev-1', terminalSeries: 'T-1', isPrimary: true },
    ]));
    api.getTerminalRequests.and.returnValue(of([]));
    api.recoverTerminal.and.returnValue(
      throwError(() => ({ error: { message: 'Device already bound to another terminal.' } }))
    );

    const fixture = mount<TerminalsPageComponent>(TerminalsPageComponent, [
      { provide: BusinessApiService, useValue: api },
    ]);
    const terminal = fixture.componentInstance.terminals()[0];
    fixture.componentInstance.startRecovery(terminal);
    fixture.detectChanges();

    const deviceId = fixture.nativeElement.querySelector('#recovery-device-id') as HTMLInputElement;
    type(deviceId, 'dev-2');
    fixture.detectChanges();
    expect(deviceId.getAttribute('aria-describedby'))
      .withContext('nothing has failed yet, so the field names nothing')
      .toBeNull();

    const dialog = deviceId.closest('.modal-box') as HTMLElement;
    (dialog.querySelector('.modal-actions .primary-btn') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(messageFor(deviceId)).toContain('Device already bound to another terminal.');
    expect(danglingReferences(fixture.nativeElement)).toEqual([]);
    fixture.destroy();
  });

  it('inventory workflows: the recipe and purchase-order fields name their messages', () => {
    const api = jasmine.createSpyObj('BusinessApiService', [
      'getInventoryMaterials',
      'getVendors',
      'getMenu',
      'getPurchaseOrders',
    ]);
    api.getInventoryMaterials.and.returnValue(of([
      { id: 1, name: 'Rice', unit: 'kg', stockQuantity: 5, lowStockThreshold: 1, costPerUnit: 90 },
    ]));
    api.getVendors.and.returnValue(of([]));
    api.getMenu.and.returnValue(of([{ menuItemId: 7, name: 'Paneer Tikka' }]));
    api.getPurchaseOrders.and.returnValue(of([]));

    const fixture = mount<InventoryPageComponent>(InventoryPageComponent, [
      { provide: BusinessApiService, useValue: api },
    ]);
    const host = fixture.nativeElement as HTMLElement;
    const tab = (label: string) =>
      Array.from(host.querySelectorAll('button.tab'))
        .find((button) => (button.textContent || '').trim() === label) as HTMLButtonElement;

    // The two required selects on the recipes tab: a control with no value yet is only
    // marked once the user has been through it, and then it has to say what to choose.
    tab('Item Recipes').click();
    fixture.detectChanges();
    const menuItem = host.querySelector('#recipe-menu-item') as HTMLSelectElement;
    expect(menuItem).withContext('the recipes tab renders its menu-item select').toBeTruthy();

    leave(menuItem);
    fixture.detectChanges();
    expect(flagged(menuItem)).toBeTrue();
    expect(messageFor(menuItem)).toBe('Choose the menu item this recipe belongs to.');

    const ingredient = host.querySelector('#recipe-material') as HTMLSelectElement;
    leave(ingredient);
    fixture.detectChanges();
    expect(messageFor(ingredient)).toBe('Choose the ingredient this recipe uses.');

    tab('Purchase Orders').click();
    fixture.detectChanges();
    const poMaterial = host.querySelector('#po-material-0') as HTMLSelectElement;
    expect(poMaterial).withContext('the purchase-order tab renders its first line').toBeTruthy();

    leave(poMaterial);
    fixture.detectChanges();
    expect(flagged(poMaterial)).toBeTrue();
    expect(messageFor(poMaterial)).toBe('Choose a material for this line.');

    expect(danglingReferences(host)).toEqual([]);
    fixture.destroy();
  });

  it('tax settings: a rejected GSTIN names the rejection it came back with', () => {
    const rejected = jasmine.createSpyObj('BusinessApiService', ['lookupGst']);
    rejected.lookupGst.and.returnValue(of({ valid: false, error: 'GSTIN is invalid or unregistered.' }));

    const fixture = mount<TaxSettingsSectionComponent>(TaxSettingsSectionComponent, [
      { provide: BusinessApiService, useValue: rejected },
    ], false);
    fixture.componentRef.setInput('tax', { gstEnabled: true, gstin: '29AAAAA0000A1Z5' });
    fixture.detectChanges();

    const field = fixture.nativeElement.querySelector('input.field-control') as HTMLInputElement;
    expect(field.getAttribute('aria-describedby'))
      .withContext('an unverified GSTIN names nothing')
      .toBeNull();

    (fixture.nativeElement.querySelector('button.action-btn') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(messageFor(field)).toContain('GSTIN is invalid or unregistered.');
    expect(danglingReferences(fixture.nativeElement)).toEqual([]);
    fixture.destroy();
  });
});
