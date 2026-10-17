import { TestBed, ComponentFixture } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { OrderDetailModalComponent } from './order-detail-modal.component';
import { AuthService } from '../core/auth/auth.service';
import { ToastService } from '../core/services/toast.service';
import { BusinessApiService } from '../core/services/business-api.service';
import { BusinessOrder, OrderDetailResponse } from '../core/models/api.models';

/** A complete bill; only the two fields these tests vary are parameters. */
function orderDetail(orderStatus = 'Completed', paymentMethod = 'Upi'): OrderDetailResponse {
  const order: BusinessOrder = {
    sourceType: 'POS',
    orderType: 'dine_in',
    orderId: 42,
    orderCode: 'F-07',
    invoiceNumber: null,
    publicToken: '11111111-2222-3333-4444-555555555555',
    customerName: null,
    customerContact: null,
    orderStatus,
    paymentStatus: 'Success',
    paymentMethod,
    totalAmount: 350,
    gatewayPaidAmount: null,
    refundAmount: null,
    refundStatus: 'Not refunded',
    refundMode: null,
    cancelReason: null,
    manualRefundAllowed: false,
    gatewayRefundAllowed: false,
    createdAt: 1_700_000_000_000,
  };
  return { order, lineItems: [] };
}

describe('OrderDetailModalComponent — owner bill correction', () => {
  let fixture: ComponentFixture<OrderDetailModalComponent>;
  let api: jasmine.SpyObj<BusinessApiService>;
  let toast: jasmine.SpyObj<ToastService>;
  let role: string;

  beforeEach(() => {
    role = 'OWNER';
    api = jasmine.createSpyObj<BusinessApiService>('BusinessApiService', [
      'updateBillStatus',
      'updateBillPaymentMode',
    ]);
    api.updateBillStatus.and.returnValue(of(orderDetail().order));
    api.updateBillPaymentMode.and.returnValue(of(orderDetail().order));
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['show']);

    TestBed.configureTestingModule({
      imports: [OrderDetailModalComponent],
      providers: [
        { provide: AuthService, useValue: { session: () => ({ role }) } },
        { provide: ToastService, useValue: toast },
        { provide: BusinessApiService, useValue: api },
      ],
    });
  });

  function create(detail: OrderDetailResponse = orderDetail()): void {
    fixture = TestBed.createComponent(OrderDetailModalComponent);
    fixture.componentInstance.order = detail;
    fixture.detectChanges();
  }

  function toggle(): void {
    fixture.nativeElement.querySelector('.correction__toggle').click();
    fixture.detectChanges();
  }

  function setReason(value: string): void {
    fixture.componentInstance.reason.set(value);
  }

  it('hides the correction panel from anyone who is not the owner', () => {
    role = 'SHOP_STAFF';
    create();
    expect(fixture.nativeElement.querySelector('.correction__toggle')).toBeNull();
  });

  it('seeds the form from the bill the API returned', () => {
    create(orderDetail('Pending', 'Cash'));
    toggle();

    expect(fixture.componentInstance.statusDraft()).toBe('draft');
    expect(fixture.componentInstance.modeDraft()).toBe('cash');
  });

  it('refuses to save when nothing actually changed', () => {
    create();
    toggle();
    setReason('something meaningful');

    fixture.componentInstance.saveCorrection();

    expect(api.updateBillStatus).not.toHaveBeenCalled();
    expect(api.updateBillPaymentMode).not.toHaveBeenCalled();
    expect(fixture.componentInstance.editError()).toContain('Change the order status');
  });

  it('requires a reason, because the edit is audited', () => {
    create();
    toggle();
    fixture.componentInstance.statusDraft.set('cancelled');
    setReason('no');

    fixture.componentInstance.saveCorrection();

    expect(api.updateBillStatus).not.toHaveBeenCalled();
    expect(fixture.componentInstance.editError()).toContain('reason');
  });

  it('sends only the changed field and reports success', () => {
    const updated = jasmine.createSpy('billUpdated');
    create();
    fixture.componentInstance.billUpdated.subscribe(updated);
    toggle();
    fixture.componentInstance.statusDraft.set('cancelled');
    setReason('customer left without paying');

    fixture.componentInstance.saveCorrection();

    expect(api.updateBillStatus).toHaveBeenCalledWith(42, {
      orderStatus: 'cancelled',
      reason: 'customer left without paying',
    });
    expect(api.updateBillPaymentMode).not.toHaveBeenCalled();
    expect(updated).toHaveBeenCalled();
    expect(toast.show).toHaveBeenCalled();
    expect(fixture.componentInstance.editOpen()).toBeFalse();
  });

  it('sends the mode change and its split amounts for a part-payment mode', () => {
    create();
    toggle();
    fixture.componentInstance.modeDraft.set('part_payment_upi_cash');
    fixture.componentInstance.part1.set('200');
    fixture.componentInstance.part2.set('150');
    setReason('half UPI half cash');

    fixture.componentInstance.saveCorrection();

    expect(api.updateBillPaymentMode).toHaveBeenCalledWith(42, {
      paymentMode: 'part_payment_upi_cash',
      partAmount1: 200,
      partAmount2: 150,
      reason: 'half UPI half cash',
    });
  });

  it('demands both split amounts before submitting a part-payment mode', () => {
    create();
    toggle();
    fixture.componentInstance.modeDraft.set('part_payment_upi_cash');
    fixture.componentInstance.part1.set('200');
    setReason('half UPI half cash');

    fixture.componentInstance.saveCorrection();

    expect(api.updateBillPaymentMode).not.toHaveBeenCalled();
    expect(fixture.componentInstance.editError()).toContain('second split amount');
  });

  it('shows the server refusal verbatim so the owner learns why', () => {
    api.updateBillStatus.and.returnValue(
      throwError(() => ({ error: { error: 'Reopening a completed bill is not supported: the stock it consumed cannot be returned.' } }))
    );
    create();
    toggle();
    fixture.componentInstance.statusDraft.set('draft');
    setReason('owner mistake');

    fixture.componentInstance.saveCorrection();

    expect(fixture.componentInstance.editError()).toContain('Reopening a completed bill is not supported');
    expect(fixture.componentInstance.editOpen()).toBeTrue();
  });

  it('colours the status chip for the title-cased value the API actually returns', () => {
    create(orderDetail('Completed'));
    const chip = fixture.nativeElement.querySelector('.chip--success');
    expect(chip).not.toBeNull();
    expect(chip.textContent).toContain('Completed');
  });
});
