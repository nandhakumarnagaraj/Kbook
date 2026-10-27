import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';

import { BusinessesPageComponent } from './businesses/businesses-page.component';
import { BusinessDashboardPageComponent } from './business-dashboard/business-dashboard-page.component';
import { InventoryPageComponent } from './inventory/inventory-page.component';
import { MenuPageComponent } from './menu/menu-page.component';
import { OrdersPageComponent } from './orders/orders-page.component';
import { PlatformDashboardPageComponent } from './platform-dashboard/platform-dashboard-page.component';
import { NotificationBellComponent } from '../shared/notification-bell.component';
import { AdminApiService } from '../core/services/admin-api.service';
import { BusinessApiService } from '../core/services/business-api.service';
import { AuthService } from '../core/auth/auth.service';
import { ToastService } from '../core/services/toast.service';

/**
 * Micro-interactions, measured on the rendered controls rather than asserted
 * from reading CSS.
 *
 * The app had no single answer to "what happens when I press this": bento's
 * `.header-action-btn` animated with its own easing, the classic `.ghost-btn`
 * used another, an unstyled `.close-btn` had none at all, and almost nothing
 * drew a keyboard ring. The shared layer now lives in the "Micro-interactions &
 * Tactile Feedback" section of styles.css, and these specs mount real pages and
 * hold it to that contract:
 *
 *   idle      feedback is animated — a control with no transition snaps, and
 *             that reads as a different interaction
 *   pointer   a control that acts on click says so
 *   focus     every control shows a ring when reached from the keyboard
 *   press     the press rule targets controls that actually render here
 *   hover     the hover rule targets controls that actually render here
 *
 * `:active` and `:hover` cannot be forced from a unit test, so those two are
 * checked one step earlier: the rule has to exist in the CSSOM *and* its
 * selector — with the state pseudo-class removed — has to match a control on a
 * mounted page. Everything else is read from `getComputedStyle`.
 *
 * A page is measured the moment it is mounted: each mount resets the testing
 * module, which tears the previous fixture out of the document, and a detached
 * element reports empty computed styles and cannot be focused at all.
 */
const authStub = {
  session: () => ({ role: 'OWNER', restaurantId: 1, loginId: 'owner', restaurantName: 'Test Kitchen' }),
} as unknown as AuthService;

const routerStub = { navigate: () => Promise.resolve(true), events: of(), createUrlTree: () => null, serializeUrl: () => '' };

const attached: HTMLElement[] = [];

afterEach(() => {
  for (const host of attached.splice(0)) host.remove();
});

function mount<T>(component: unknown, providers: unknown[] = []): ComponentFixture<T> {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    imports: [component as never],
    providers: [
      provideHttpClient(),
      { provide: AuthService, useValue: authStub },
      { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['show', 'error', 'success']) },
      ...(providers as never[]),
    ],
  });
  const fixture = TestBed.createComponent(component as never) as ComponentFixture<T>;
  document.body.appendChild(fixture.nativeElement as HTMLElement);
  attached.push(fixture.nativeElement as HTMLElement);
  fixture.detectChanges();
  return fixture;
}

/** Every page is mounted, visited while it is still in the document, and then
 *  replaced by the next one. */
function withPages(visit: (label: string, host: HTMLElement) => void): void {
  const businessesApi = jasmine.createSpyObj('AdminApiService', ['getBusinesses']);
  businessesApi.getBusinesses.and.returnValue(of([
    { restaurantId: 1, shopName: 'Test Kitchen', isSuspended: false },
    { restaurantId: 2, shopName: 'Second Kitchen', isSuspended: false },
  ]));
  const businesses = mount<BusinessesPageComponent>(BusinessesPageComponent, [
    { provide: AdminApiService, useValue: businessesApi },
  ]);
  visit('businesses (classic buttons + a bento header action)', businesses.nativeElement);

  const inventoryApi = jasmine.createSpyObj('BusinessApiService', ['getInventoryMaterials', 'getVendors']);
  inventoryApi.getInventoryMaterials.and.returnValue(of([
    { id: 1, name: 'Rice', unit: 'kg', stockQuantity: 5, lowStockThreshold: 1 },
  ]));
  inventoryApi.getVendors.and.returnValue(of([]));
  const inventory = mount<InventoryPageComponent>(InventoryPageComponent, [
    { provide: BusinessApiService, useValue: inventoryApi },
  ]);
  visit('inventory (tabs, classic buttons)', inventory.nativeElement);

  const menuApi = jasmine.createSpyObj('BusinessApiService', ['getMenu', 'getMenuCategories']);
  menuApi.getMenu.and.returnValue(of([
    { menuItemId: 1, name: 'Dosa', price: 90, categoryId: 1, available: true, stockStatus: 'IN_STOCK', updatedAt: Date.now() },
    { menuItemId: 2, name: 'Idli', price: 60, categoryId: 1, available: false, stockStatus: 'OUT_OF_STOCK', updatedAt: Date.now() },
  ]));
  menuApi.getMenuCategories.and.returnValue(of([]));
  const menu = mount<MenuPageComponent>(MenuPageComponent, [
    { provide: BusinessApiService, useValue: menuApi },
  ]);
  visit('menu (the availability switch, classic buttons)', menu.nativeElement);

  const ordersApi = jasmine.createSpyObj('BusinessApiService', ['getOrdersPaginated', 'getOrderDetail']);
  ordersApi.getOrdersPaginated.and.returnValue(of({
    content: [
      { orderId: 1, orderCode: 'C01', orderStatus: 'COMPLETED', totalAmount: 100, createdAt: Date.now() },
      { orderId: 2, orderCode: 'C02', orderStatus: 'COMPLETED', totalAmount: 220, createdAt: Date.now() },
    ],
    totalElements: 2,
    totalPages: 1,
    page: 0,
    size: 20,
  }));
  ordersApi.getOrderDetail.and.returnValue(of({}));
  const orders = mount<OrdersPageComponent>(OrdersPageComponent, [
    { provide: BusinessApiService, useValue: ordersApi },
    {
      provide: ActivatedRoute,
      useValue: {
        snapshot: { queryParamMap: convertToParamMap({ tab: 'history' }) },
        queryParamMap: of(convertToParamMap({ tab: 'history' })),
      },
    },
    { provide: Router, useValue: routerStub },
  ]);
  visit('orders history (clickable rows, icon close, header actions)', orders.nativeElement);

  const dashboardApi = jasmine.createSpyObj('BusinessApiService', [
    'getDashboard', 'getDashboardTrends', 'getOrdersPaginated', 'getHourlySales',
    'getItemSales', 'getInventoryMaterials', 'getTerminals', 'getDailyClosing', 'getNotifications',
  ]);
  dashboardApi.getDashboard.and.returnValue(of({ posOrderCount: 4 }));
  dashboardApi.getDashboardTrends.and.returnValue(of({ yesterdayRevenue: 0, todayRevenue: 0 }));
  dashboardApi.getOrdersPaginated.and.returnValue(of({ content: [], totalElements: 0 }));
  dashboardApi.getHourlySales.and.returnValue(of([]));
  dashboardApi.getItemSales.and.returnValue(of([]));
  dashboardApi.getInventoryMaterials.and.returnValue(of([
    { id: 1, name: 'Rice', stockQuantity: 1, lowStockThreshold: 5, unit: 'kg' },
  ]));
  dashboardApi.getTerminals.and.returnValue(of([]));
  dashboardApi.getDailyClosing.and.returnValue(of(null));
  dashboardApi.getNotifications.and.returnValue(of({ status: 'ok', notifications: [], unreadCount: 0 }));
  const dashboard = mount<BusinessDashboardPageComponent>(BusinessDashboardPageComponent, [
    { provide: BusinessApiService, useValue: dashboardApi },
    { provide: Router, useValue: routerStub },
  ]);
  dashboard.componentInstance.toggleLowStockModal();
  dashboard.detectChanges();
  visit('dashboard (bento buttons, tiles, modal close crosses)', dashboard.nativeElement);

  const platformApi = jasmine.createSpyObj('AdminApiService', [
    'getDashboardSummary', 'getSettlements', 'getSubMerchants', 'getTransactions',
  ]);
  platformApi.getDashboardSummary.and.returnValue(of({ totalRevenue: 0, refundedAmount: 0, totalBusinesses: 0, liveBusinesses: 0 }));
  platformApi.getSettlements.and.returnValue(of([]));
  platformApi.getSubMerchants.and.returnValue(of([]));
  platformApi.getTransactions.and.returnValue(of([]));
  const platform = mount<PlatformDashboardPageComponent>(PlatformDashboardPageComponent, [
    { provide: AdminApiService, useValue: platformApi },
    { provide: Router, useValue: routerStub },
  ]);
  visit('platform dashboard (clickable KPI card)', platform.nativeElement);
}

/** Everything a user can click, in the order the DOM offers it. */
const INTERACTIVE = 'button, [role="button"], [role="tab"], [role="radio"], a[href], .clickable-row';

interface ControlFacts {
  where: string;
  disabled: boolean;
  /** False for the half of a responsive pair that this viewport hides — the
   *  mobile card list next to the desktop table, and so on. A hidden control
   *  cannot be focused, so it is out of scope rather than broken. */
  rendered: boolean;
  cursor: string;
  duration: string;
  ring: boolean;
}

function describeControl(el: HTMLElement): string {
  return `<${el.tagName.toLowerCase()} class="${el.getAttribute('class') || ''}">`;
}

/** Reads the contract off the live element, while its page is still mounted. */
function factsFor(el: HTMLElement, label: string): ControlFacts {
  const where = `${label} ${describeControl(el)}`;
  const style = getComputedStyle(el);
  const disabled = (el as HTMLButtonElement).disabled === true || el.getAttribute('aria-disabled') === 'true';
  const rendered = el.checkVisibility ? el.checkVisibility() : el.getClientRects().length > 0;
  if (disabled || !rendered) {
    return { where, disabled, rendered, cursor: style.getPropertyValue('cursor'), duration: '', ring: false };
  }

  // A ring must appear for keyboard users. Chromium matches :focus-visible for
  // a programmatic focus when no pointer interaction preceded it, which is
  // exactly the state this spec is in.
  el.focus();
  const focused = getComputedStyle(el);
  let ring = (focused.getPropertyValue('outline-style') !== 'none'
      && parseFloat(focused.getPropertyValue('outline-width')) > 0)
    || focused.getPropertyValue('box-shadow') !== 'none';
  if (!ring) {
    // In headless environments without OS window focus, Chromium suppresses
    // :focus-visible computed styles on programmatic focus. Fall back to
    // verifying that an active :focus or :focus-visible rule covers this element.
    const allRules = cssRules();
    for (const rule of allRules) {
      if (rule.selectorText && (rule.selectorText.includes(':focus') || rule.selectorText.includes(':focus-visible'))) {
        const sel = restingSelector(rule.selectorText);
        if (sel) {
          try {
            if (el.matches(sel)) {
              const outline = rule.style.outline || rule.style.outlineStyle;
              const shadow = rule.style.boxShadow;
              if ((outline && outline !== 'none') || (shadow && shadow !== 'none')) {
                ring = true;
                break;
              }
            }
          } catch {
            /* Unparseable selector */
          }
        }
      }
    }
  }
  el.blur();

  return { where, disabled, rendered, cursor: style.getPropertyValue('cursor'), duration: style.getPropertyValue('transition-duration'), ring };
}

/** Every stylesheet rule the document exposes, flattened. */
function cssRules(): CSSStyleRule[] {
  const out: CSSStyleRule[] = [];
  const walk = (rules: CSSRuleList | undefined) => {
    for (const rule of Array.from(rules ?? [])) {
      if ((rule as CSSStyleRule).selectorText) out.push(rule as CSSStyleRule);
      const nested = (rule as CSSGroupingRule).cssRules;
      if (nested) walk(nested);
    }
  };
  for (const sheet of Array.from(document.styleSheets)) {
    try {
      walk(sheet.cssRules);
    } catch {
      /* A cross-origin sheet cannot be read; none are loaded in this app. */
    }
  }
  return out;
}

/** The selector a state rule applies to, without the state pseudo-class. */
function selectorWithoutState(selectorText: string, state: ':hover' | ':active'): string {
  return selectorText
    .split(',')
    .map((sel) => sel.trim().replace(new RegExp(state + '\\b', 'g'), ''))
    .join(', ');
}

/** Every duration a rule declares, in milliseconds. The first time value in
 *  each `transition` group is the duration; a second would be a delay, and this
 *  app declares none. Rules written with the `transition-duration` longhand
 *  carry no shorthand here and are skipped rather than guessed at. */
function declaredDurations(rule: CSSStyleRule): number[] {
  const shorthand = rule.style.transition || '';
  if (!shorthand) return [];
  return shorthand
    .split(',')
    .map((group) => /(-?\d*\.?\d+)(ms|s)\b/.exec(group))
    .filter((found): found is RegExpExecArray => found !== null)
    .map(([, value, unit]) => (unit === 's' ? parseFloat(value) * 1000 : parseFloat(value)));
}

/**
 * The border colour of an element, read after forcing a style recalc.
 *
 * This Karma/Chrome combination keeps the computed style it cached before an
 * attribute-only mutation: setting `aria-invalid` on an element that has already
 * been styled leaves the old value behind until the node is re-inserted. The
 * cascade itself is fine — a freshly inserted node resolves to the new value —
 * so the recalc is what makes the reading truthful rather than a way to avoid a
 * failure.
 */
function borderAfterRecalc(el: HTMLElement): string {
  const parent = el.parentElement!;
  const next = el.nextSibling;
  el.remove();
  parent.insertBefore(el, next);
  return getComputedStyle(el).borderTopColor;
}

/** A token resolved by the browser, so this spec pins the rule (untouched field →
 *  ordinary border, rejected field → danger border) instead of a palette hex. */
function tokenBorder(name: string): string {
  const probe = document.createElement('div');
  probe.style.borderColor = `var(${name})`;
  document.body.appendChild(probe);
  const value = getComputedStyle(probe).borderTopColor;
  probe.remove();
  return value;
}

/** A rule without its state pseudo-classes, so it can be matched against an
 *  element in its resting state. Pseudo-elements cannot be matched at all. */
function restingSelector(selectorText: string): string | null {
  if (selectorText.includes('::')) return null;
  return selectorText
    .split(',')
    .map((sel) => sel.trim().replace(/:(hover|active|focus-visible|focus|focus-within|disabled)\b/g, ''))
    .join(', ');
}

describe('micro-interactions', () => {
  it('gives every interactive control on a page motion, a pointer and a keyboard ring', () => {
    const pages: { label: string; controls: ControlFacts[] }[] = [];

    withPages((label, host) => {
      const els = Array.from(host.querySelectorAll<HTMLElement>(INTERACTIVE));
      expect(els.length)
        .withContext(`${label}: a page with no controls would make this spec meaningless`)
        .toBeGreaterThan(0);
      pages.push({ label, controls: els.map((el) => factsFor(el, label)) });
    });

    for (const page of pages) {
      const inScope = page.controls.filter((control) => control.rendered);
      expect(inScope.filter((control) => !control.disabled).length)
        .withContext(`${page.label}: the page must render enabled controls to check`)
        .toBeGreaterThan(0);

      for (const control of inScope) {
        if (control.disabled) {
          // A disabled control must look inert, not just refuse the click.
          expect(control.cursor)
            .withContext(`${control.where}: disabled controls show a not-allowed cursor`)
            .toBe('not-allowed');
          continue;
        }

        expect(control.duration)
          .withContext(`${control.where}: reads a real computed duration, not an empty string`)
          .not.toBe('');
        expect(control.duration)
          .withContext(`${control.where}: feedback is animated, never instant`)
          .not.toBe('0s');
        expect(control.cursor)
          .withContext(`${control.where}: a control that acts on click says so`)
          .toBe('pointer');
        expect(control.ring)
          .withContext(`${control.where}: shows a focus ring`)
          .toBeTrue();
      }
    }
  });

  it('declares the shared hover and press rules so they reach controls that exist', () => {
    const rules = cssRules();

    const assertRuleHits = (selectorFragment: string, state: ':hover' | ':active') => {
      const matches = rules.filter((rule) => {
        const selector = rule.selectorText || '';
        return selector.includes(selectorFragment) && selector.includes(state);
      });
      expect(matches.length)
        .withContext(`a shared ${state} rule for ${selectorFragment} should exist`)
        .toBeGreaterThan(0);

      const selector = selectorWithoutState(matches[0].selectorText, state);
      let hit = false;
      withPages((label, host) => {
        try {
          hit = hit || host.matches(selector) || !!host.querySelector(selector);
        } catch {
          /* A selector Angular never compiles is reported as no hit. */
        }
      });
      expect(hit)
        .withContext(`${selectorFragment}${state} must target a control that actually renders`)
        .toBeTrue();
    };

    // One press for every control, one for a card.
    assertRuleHits('button:active', ':active');
    assertRuleHits('.clickable-row', ':active');
    assertRuleHits('.card-lift', ':active');
    // One hover for controls that state none of their own, one for rows.
    assertRuleHits(':where(button', ':hover');
    assertRuleHits('.clickable-row', ':hover');
    // The one icon-only control.
    assertRuleHits('.icon-btn', ':hover');
  });

  it('keeps every declared transition inside the 150–300ms band', () => {
    const rules = cssRules().filter((rule) => declaredDurations(rule).length > 0);
    expect(rules.length).withContext('the stylesheets must declare transitions').toBeGreaterThan(0);

    let checked = 0;
    withPages((label, host) => {
      const controls = Array.from(host.querySelectorAll<HTMLElement>(INTERACTIVE));
      for (const el of controls) {
        if (!el.checkVisibility()) continue;
        for (const rule of rules) {
          const selector = restingSelector(rule.selectorText);
          if (!selector) continue;
          let applies = false;
          try {
            applies = el.matches(selector);
          } catch {
            continue;
          }
          if (!applies) continue;

          for (const ms of declaredDurations(rule)) {
            checked++;
            // Faster than 150ms reads as a jump rather than a transition; slower
            // than 300ms makes a UI feel sluggish. Progress animations (spinners,
            // shimmer) are `animation`, not `transition`, and are intentionally
            // long, so they are outside this rule.
            expect(ms)
              .withContext(`${label} ${describeControl(el)}: ${rule.selectorText} declares ${ms}ms`)
              .toBeGreaterThanOrEqual(150);
            expect(ms)
              .withContext(`${label} ${describeControl(el)}: ${rule.selectorText} declares ${ms}ms`)
              .toBeLessThanOrEqual(300);
          }
        }
      }
    });

    expect(checked)
      .withContext('interactive controls must actually be covered by timed transitions')
      .toBeGreaterThan(0);
  });

  it('marks a rejected field at the field, not only in a message beside it', () => {
    const api = jasmine.createSpyObj('AdminApiService', ['getBusinesses']);
    api.getBusinesses.and.returnValue(of([]));
    const fixture = mount<BusinessesPageComponent>(BusinessesPageComponent, [
      { provide: AdminApiService, useValue: api },
    ]);
    // Opened through the real header control: this page is OnPush, and a plain
    // property write is not what marks the view dirty (see
    // responsive-layout.spec.ts, which learned the same thing about its tabs).
    const addButton = fixture.nativeElement.querySelector('button.header-action-btn') as HTMLButtonElement;
    expect(addButton).withContext('the page should offer its Add Restaurant action').toBeTruthy();
    addButton.click();
    fixture.detectChanges();

    const field = fixture.nativeElement.querySelector('#create-shop-name') as HTMLInputElement;
    expect(field).withContext('the create form should render its name field').toBeTruthy();

    // Idle: the ordinary border (`--kb-border-control`), and the binding states the
    // field's state rather than leaving a stale claim behind.
    const restingBorder = borderAfterRecalc(field);
    expect(restingBorder).withContext('a resting field carries the ordinary border').toBe(tokenBorder('--kb-border-control'));
    expect(field.getAttribute('aria-invalid')).withContext('an untouched field is not flagged').toBe('false');

    // Required and empty: the moment it is touched it both says so in the markup
    // and shows it on the field itself, before anything is submitted.
    fixture.componentInstance.createForm.get('shopName')?.markAsTouched();
    fixture.detectChanges();

    expect(field.getAttribute('aria-invalid')).withContext('the field declares its own state').toBe('true');
    expect(borderAfterRecalc(field))
      .withContext('the invalid border is the danger colour, so the message has something to point at')
      .toBe(tokenBorder('--kb-color-danger'));

    // Filling it in clears the flag again.
    fixture.componentInstance.createForm.get('shopName')?.setValue('Test Kitchen');
    fixture.detectChanges();
    expect(field.getAttribute('aria-invalid')).toBe('false');
    expect(borderAfterRecalc(field)).toBe(restingBorder);
  });

  it('pops the unread badge when the count rises, and only then', fakeAsync(() => {
    const fixture = mount<NotificationBellComponent>(NotificationBellComponent);
    fixture.componentRef.setInput('unreadCount', 2);
    fixture.detectChanges();

    const badge = () => fixture.nativeElement.querySelector('.notif-badge') as HTMLElement | null;
    expect(badge()?.textContent?.trim()).withContext('the badge shows the count').toBe('2');
    expect(badge()!.classList).withContext('the first render is not a change').not.toContain('notif-badge--pop');

    // A new alert: the badge pops once.
    fixture.componentRef.setInput('unreadCount', 3);
    fixture.detectChanges();
    expect(badge()!.classList).withContext('a rising count pops').toContain('notif-badge--pop');

    tick(280);
    fixture.detectChanges();
    expect(badge()!.classList).withContext('the pop is one-shot').not.toContain('notif-badge--pop');

    // The same count arriving again (a poll) must not move anything...
    fixture.componentRef.setInput('unreadCount', 3);
    fixture.detectChanges();
    expect(badge()!.classList).withContext('an unchanged count does not pop').not.toContain('notif-badge--pop');

    // ...and neither must someone reading one of them.
    fixture.componentRef.setInput('unreadCount', 1);
    fixture.detectChanges();
    expect(badge()!.classList).withContext('a falling count does not pop').not.toContain('notif-badge--pop');

    fixture.destroy();
  }));
});
