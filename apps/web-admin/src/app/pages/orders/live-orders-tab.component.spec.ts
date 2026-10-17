import { TestBed, ComponentFixture } from '@angular/core/testing';
import { of } from 'rxjs';

import { LiveOrdersTabComponent } from './live-orders-tab.component';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { BusinessOrder } from '../../core/models/api.models';

/**
 * Two things on this card were wrong in the field and are pinned here.
 *
 * 1. The status pill. The API sends title-cased labels (BusinessReadService.normalizeLabel:
 *    "Draft", "Pending") while the template compared against the lowercase stored value, so
 *    an open bill never matched and the pill printed the *payment* status instead — the live
 *    board read "Pending", the money's word, and the order's own state was never shown. The
 *    fixtures below therefore use the shape the API really sends, not the lowercase union.
 * 2. The destructive action. It read "Void" and rendered as a tinted chip that blended into
 *    the Details / Invoice pair beside it; it is now "Cancel" with the Sign out button's
 *    solid red fill. The fill is asserted through getComputedStyle, because a class name
 *    cannot tell a tinted chip from a solid button.
 */
describe('LiveOrdersTabComponent — the card action row', () => {
  let fixture: ComponentFixture<LiveOrdersTabComponent> | undefined;
  let api: jasmine.SpyObj<BusinessApiService>;

  /** The wire shape: statuses arrive title-cased. */
  function apiOrder(overrides: Partial<BusinessOrder> = {}): BusinessOrder {
    return {
      orderId: 22,
      orderCode: 'C22',
      customerName: 'Walk-in Guest',
      totalAmount: 318,
      createdAt: Date.now() - 6 * 60 * 1000,
      orderType: 'DINE_IN',
      orderStatus: 'Draft',
      paymentStatus: 'Pending',
      tableNumber: 'T9',
      ...overrides,
    } as unknown as BusinessOrder;
  }

  function mount(order: BusinessOrder): void {
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
  }

  // The component polls every 30s; tear the fixture down so the interval stops.
  afterEach(() => {
    fixture?.destroy();
    fixture = undefined;
  });

  function actionButton(label: string): HTMLButtonElement | undefined {
    const buttons = fixture!.nativeElement.querySelectorAll('.card-action-btn') as NodeListOf<HTMLButtonElement>;
    return Array.from(buttons).find((button) => (button.textContent || '').trim() === label);
  }

  it('renders the active order as a card, so the action row exists to assert on', () => {
    mount(apiOrder());
    expect(actionButton('Details')).withContext('the card action row renders').toBeTruthy();
  });

  it('shows the order state as "Preparing", not the payment word', () => {
    mount(apiOrder());
    const pill = fixture!.nativeElement.querySelector('.status-pill') as HTMLElement;

    expect((pill.textContent || '').trim())
      .withContext('the pill names the order state, not the payment status it used to print')
      .toBe('Preparing');
    expect(pill.classList).withContext('an open bill is flagged as the warning state').toContain('status-pill--warn');
    expect(pill.classList).withContext('the pending-payment style is applied from the payment field').toContain('status-pill--pending');
  });

  it('reads the status case-insensitively, so a lowercase payload still says Preparing', () => {
    mount(apiOrder({ orderStatus: 'draft', paymentStatus: 'pending' }));
    const pill = fixture!.nativeElement.querySelector('.status-pill') as HTMLElement;
    expect((pill.textContent || '').trim()).toBe('Preparing');
  });

  it('labels the destructive action "Cancel", not "Void"', () => {
    mount(apiOrder());
    const cancel = actionButton('Cancel');
    expect(cancel).withContext('the card offers a Cancel action').toBeTruthy();
    expect(actionButton('Void')).withContext('no action still reads "Void"').toBeUndefined();
    expect(cancel!.getAttribute('title')).toBe('Cancel this order');
  });

  it('paints it like the Sign out button: solid danger fill with white content', () => {
    mount(apiOrder());
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
    mount(apiOrder());
    // The restyle must not have spread to the non-destructive actions.
    expect(getComputedStyle(actionButton('Invoice')!).color).not.toBe('rgb(255, 255, 255)');

    actionButton('Cancel')!.click();
    fixture!.detectChanges();

    const title = fixture!.nativeElement.querySelector('#cancel-modal-title') as HTMLElement;
    expect(title).withContext('the cancellation dialog opens').toBeTruthy();
    expect(title.textContent).toContain('C22');
  });
});
