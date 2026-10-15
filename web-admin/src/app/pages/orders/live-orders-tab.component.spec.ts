import { TestBed, ComponentFixture } from '@angular/core/testing';
import { of } from 'rxjs';

import { LiveOrdersTabComponent } from './live-orders-tab.component';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { BusinessOrder } from '../../core/models/api.models';

/**
 * The destructive action on a live-floor order card. Two things are pinned here
 * because both were wrong in the field: the label read "Void" (the owner asked for
 * "Cancel", the word the rest of the dialog already uses) and the button rendered as
 * a tinted chip that blended into the Details / Invoice pair beside it instead of the
 * solid red Sign out treatment the owner pointed at.
 *
 * The fill is asserted through getComputedStyle rather than the class name, because a
 * class assertion cannot tell a tinted chip from a solid button — the tokens are what
 * the eye actually sees.
 */
describe('LiveOrdersTabComponent — the card action row', () => {
  let fixture: ComponentFixture<LiveOrdersTabComponent>;
  let api: jasmine.SpyObj<BusinessApiService>;

  const order = {
    orderId: 22,
    orderCode: 'C22',
    customerName: 'Walk-in Guest',
    totalAmount: 318,
    createdAt: Date.now() - 6 * 60 * 1000,
    orderType: 'DINE_IN',
    orderStatus: 'draft',
    paymentStatus: 'pending',
    tableNumber: 'T9',
  } as unknown as BusinessOrder;

  beforeEach(() => {
    api = jasmine.createSpyObj<BusinessApiService>('BusinessApiService', [
      'getOrdersPaginated',
      'getOrderDetail',
      'voidBill',
    ]);
    api.getOrdersPaginated.and.returnValue(of({ content: [order] } as any));
    api.voidBill.and.returnValue(of({}));

    TestBed.configureTestingModule({
      imports: [LiveOrdersTabComponent],
      providers: [
        { provide: BusinessApiService, useValue: api },
        { provide: AuthService, useValue: { session: () => null } },
        { provide: ToastService, useValue: { show: jasmine.createSpy('show') } },
      ],
    });

    fixture = TestBed.createComponent(LiveOrdersTabComponent);
    fixture.detectChanges();
  });

  // The component polls every 30s; tear the fixture down so the interval stops.
  afterEach(() => fixture.destroy());

  function actionButton(label: string): HTMLButtonElement | undefined {
    const buttons = fixture.nativeElement.querySelectorAll('.card-action-btn') as NodeListOf<HTMLButtonElement>;
    return Array.from(buttons).find((button) => (button.textContent || '').trim() === label);
  }

  it('renders the active order as a card, so the action row exists to assert on', () => {
    expect(actionButton('Details')).withContext('the card action row renders').toBeTruthy();
  });

  it('labels the destructive action "Cancel", not "Void"', () => {
    const cancel = actionButton('Cancel');
    expect(cancel).withContext('the card offers a Cancel action').toBeTruthy();
    expect(actionButton('Void')).withContext('no action still reads "Void"').toBeUndefined();
    expect(cancel!.getAttribute('title')).toBe('Cancel this order');
  });

  it('paints it like the Sign out button: solid danger fill with white content', () => {
    const cancel = actionButton('Cancel')!;
    const style = getComputedStyle(cancel);

    // --kb-color-danger / --kb-color-on-primary, the same pair the sidebar's
    // .logout-btn uses.
    expect(style.backgroundColor).toBe('rgb(192, 57, 43)');
    expect(style.color).toBe('rgb(255, 255, 255)');
    expect(style.borderTopColor).toBe('rgb(192, 57, 43)');

    // Non-vacuity: the tinted chip this replaced is a different colour, so this
    // assertion can actually tell the two apart rather than passing on any button.
    const soft = getComputedStyle(document.documentElement).getPropertyValue('--kb-color-danger-soft').trim();
    expect(soft).not.toBe('');
    expect(style.backgroundColor).not.toBe('rgb(249, 231, 227)');
  });

  it('keeps the two neighbouring actions and the dialog it opens', () => {
    // The restyle must not have spread to the non-destructive actions.
    expect(getComputedStyle(actionButton('Invoice')!).color).not.toBe('rgb(255, 255, 255)');

    actionButton('Cancel')!.click();
    fixture.detectChanges();

    const title = fixture.nativeElement.querySelector('#cancel-modal-title') as HTMLElement;
    expect(title).withContext('the cancellation dialog opens').toBeTruthy();
    expect(title.textContent).toContain('C22');
  });
});
