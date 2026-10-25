import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';

import { SidebarLayoutComponent } from './sidebar-layout.component';
import { AuthService } from '../../core/auth/auth.service';
import { BusinessApiService } from '../../core/services/business-api.service';
import { PresenceService } from '../../core/services/presence.service';

const COLLAPSE_KEY = 'kb.sidebar.collapsed';

/* The component injects AuthService / BusinessApiService only for the session
   signal and (OWNER only) profile fetch; the tests below never exercise real
   HTTP, so value-mocks with Angular signal-shaped fields are enough. */
class AuthServiceStub {
  session = signal({ userName: 'Test Owner', role: 'OWNER', loginId: '9999999999' });
  logout = jasmine.createSpy('logout');
}

class BusinessApiServiceStub {
  getProfile = jasmine.createSpy('getProfile').and.returnValue(of({ orderPaymentFlowMode: 'pay_before_food' }));
}

class HttpClientStub {}

/* The heartbeat is a loop with timers and HTTP; the shell's job is only to start
   and stop it, so the shell tests use a value stub instead of the real service. */
class PresenceServiceStub {
  connected = signal<boolean | null>(null);
  start = jasmine.createSpy('start');
  stop = jasmine.createSpy('stop');
}

/* Shared module config: the component injects AuthService (session signal,
   logout) and BusinessApiService (OWNER profile fetch) at construction, so both
   are stubbed plus a bare HttpClient for their own constructor deps. */
function configureSidebarTestingModule(): void {
  TestBed.configureTestingModule({
    imports: [SidebarLayoutComponent],
    providers: [
      provideRouter([]),
      { provide: AuthService, useClass: AuthServiceStub },
      { provide: BusinessApiService, useClass: BusinessApiServiceStub },
      { provide: PresenceService, useClass: PresenceServiceStub },
      { provide: HttpClient, useClass: HttpClientStub },
    ],
  });
}

const PHONE = 390;
let restoreViewport: (() => void) | null = null;

/** CSS media queries read the viewport, so a phone-width assertion has to drive
    the Karma iframe, the same way responsive-layout.spec.ts does. */
function setViewport(width: number, height = 900): void {
  const frame = window.frameElement as HTMLIFrameElement | null;
  if (!frame) {
    throw new Error('this spec needs the Karma iframe to drive a real viewport');
  }
  if (!restoreViewport) {
    const style = frame.style;
    const width0 = style.width;
    const height0 = style.height;
    restoreViewport = () => {
      style.width = width0;
      style.height = height0;
    };
  }
  frame.style.width = `${width}px`;
  frame.style.height = `${height}px`;
}

afterEach(() => {
  restoreViewport?.();
  restoreViewport = null;
});

describe('SidebarLayoutComponent — collapsible sidebar', () => {
  beforeEach(() => {
    localStorage.removeItem(COLLAPSE_KEY);
    configureSidebarTestingModule();
  });

  function create(desktop = true) {
    const fixture = TestBed.createComponent(SidebarLayoutComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges(); // runs ngOnInit, which resets isMobileView from the (800px) karma viewport
    comp.isMobileView.set(!desktop); // normalize the view mode the test cares about
    fixture.detectChanges();
    return { fixture, comp };
  }

  it('defaults to expanded when no stored preference exists', () => {
    const { comp } = create();
    expect(comp.collapsed()).toBeFalse();
    expect(document.querySelector('.sidebar--collapsed')).toBeNull();
  });

  it('restores the collapsed preference from localStorage', () => {
    localStorage.setItem(COLLAPSE_KEY, '1');
    const { comp, fixture } = create();
    expect(comp.collapsed()).toBeTrue();
    // Desktop view: both the grid and the sidebar must carry the collapsed classes
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('.layout-shell--collapsed')).not.toBeNull();
    expect(root.querySelector('.sidebar--collapsed')).not.toBeNull();
  });

  it('applies collapsed classes to grid and sidebar when toggled on desktop', () => {
    const { comp, fixture } = create();
    comp.toggleCollapsed();
    fixture.detectChanges();
    expect(comp.collapsed()).toBeTrue();
    expect(localStorage.getItem(COLLAPSE_KEY)).toBe('1');
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('.layout-shell--collapsed')).not.toBeNull();
    expect(root.querySelector('.sidebar--collapsed')).not.toBeNull();
  });

  it('writes the cleared preference when expanded again', () => {
    const { comp } = create();
    comp.toggleCollapsed();
    comp.toggleCollapsed();
    expect(comp.collapsed()).toBeFalse();
    expect(localStorage.getItem(COLLAPSE_KEY)).toBe('0');
  });

  it('does not apply collapsed classes in the mobile drawer, even when stored', () => {
    localStorage.setItem(COLLAPSE_KEY, '1');
    const { comp, fixture } = create(false); // mobile view
    expect(comp.collapsed()).toBeTrue(); // data still restored
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('.layout-shell--collapsed')).toBeNull();
    expect(root.querySelector('.sidebar--collapsed')).toBeNull();
  });

  it('hides nav labels when collapsed (icon rail renders)', () => {
    localStorage.setItem(COLLAPSE_KEY, '1');
    const { fixture } = create();
    const root = fixture.nativeElement as HTMLElement;
    const label = root.querySelector('.nav-link__label');
    expect(label).not.toBeNull(); // element exists
    // Visibility is CSS-scoped (media query); the guard is the class on <aside>
    expect(root.querySelector('.sidebar--collapsed')).not.toBeNull();
  });

  it('updates the accessibility labels with state on the toggle button', () => {
    const { comp, fixture } = create();
    const btn = (fixture.nativeElement as HTMLElement).querySelector('.sidebar-toggle') as HTMLButtonElement;
    expect(btn.getAttribute('aria-label')).toBe('Collapse sidebar');
    expect(btn.getAttribute('aria-expanded')).toBe('true');
    comp.toggleCollapsed();
    fixture.detectChanges();
    expect(btn.getAttribute('aria-label')).toBe('Expand sidebar');
    expect(btn.getAttribute('aria-expanded')).toBe('false');
  });

  /** A synthetic peek event: `type` is what decides whether the dwell applies. */
  function peekEvent(type: 'mouseenter' | 'focus') {
    return {
      type,
      currentTarget: { getBoundingClientRect: () => ({ right: 70, top: 10, height: 44 }) },
    } as unknown as MouseEvent;
  }

  const REPORTS = { label: 'Reports', path: '/business/reports', icon: '', iconKey: 'reports' };

  it('waits out the pointer dwell before showing the peek chip', fakeAsync(() => {
    const { comp } = create();
    comp.collapsed.set(true);

    comp.peek(peekEvent('mouseenter'), REPORTS);
    expect(comp.peekTip()).withContext('nothing on the first frame of a hover').toBeNull();
    tick(279);
    expect(comp.peekTip()).withContext('still dwelling').toBeNull();
    tick(1);

    expect(comp.peekTip()?.label).toBe('Reports');
    expect(comp.peekTip()?.x).toBe(80);
    expect(comp.peekTip()?.y).toBe(32);
  }));

  it('shows the peek chip at once for keyboard focus, and it names the target', fakeAsync(() => {
    const { comp, fixture } = create();
    comp.collapsed.set(true);

    comp.peek(peekEvent('focus'), REPORTS);
    fixture.detectChanges();

    const chip = (fixture.nativeElement as HTMLElement).querySelector('.nav-peek');
    expect(comp.peekTip()).withContext('a keyboard user has already committed — no wait').not.toBeNull();
    expect(chip?.getAttribute('role')).toBe('tooltip');
    expect(chip?.textContent?.trim()).toBe('Reports');
    comp.hidePeek();
  }));

  it('cancels a pending dwell when the pointer leaves before it fires', fakeAsync(() => {
    const { comp } = create();
    comp.collapsed.set(true);

    comp.peek(peekEvent('mouseenter'), REPORTS);
    comp.hidePeek();
    tick(400);

    expect(comp.peekTip()).withContext('a cancelled timer must not paint a chip later').toBeNull();
  }));

  it('clamps the chip so it cannot leave the viewport', fakeAsync(() => {
    const { comp } = create();
    comp.collapsed.set(true);

    comp.peek({
      type: 'focus',
      currentTarget: { getBoundingClientRect: () => ({ right: innerWidth - 40, top: 10, height: 44 }) },
    } as unknown as MouseEvent, REPORTS);

    const tip = comp.peekTip()!;
    expect(tip.maxWidth).withContext('wide enough for a label to wrap sensibly').toBeGreaterThanOrEqual(120);
    expect(tip.x + tip.maxWidth).withContext('right edge stays inside the viewport').toBeLessThanOrEqual(innerWidth - 12);
    comp.hidePeek();
  }));

  it('keeps the nine navigation destinations in their approved order', () => {
    const { comp } = create();
    expect(comp.links().map((link) => link.label)).toEqual([
      'Dashboard', 'Orders', 'Menu', 'Staff', 'Inventory', 'Terminals', 'Daily Closing', 'Reports', 'Settings',
    ]);
  });

  it('starts the presence heartbeat with the shell and stops it on destroy', () => {
    const { fixture } = create();
    const presence = TestBed.inject(PresenceService) as unknown as PresenceServiceStub;

    expect(presence.start).withContext('the session is live, so presence starts').toHaveBeenCalled();

    fixture.destroy();
    expect(presence.stop).withContext('a destroyed shell must not keep a timer alive').toHaveBeenCalled();
  });

  it('paints no presence dot until the server answers, then states it in text too', () => {
    const { comp, fixture } = create();
    const presence = TestBed.inject(PresenceService) as unknown as PresenceServiceStub;
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('.presence-dot')).withContext('unknown is not connected').toBeNull();
    expect(comp.presenceLabel()).withContext('nothing is announced before the first ack').toBe('');

    presence.connected.set(true);
    fixture.detectChanges();
    expect(root.querySelector('.presence-dot')?.classList.contains('presence-dot--offline')).toBeFalse();
    expect(root.querySelector('.presence-status')?.textContent?.trim()).toBe('Connected to KhanaBook');

    presence.connected.set(false);
    fixture.detectChanges();
    expect(root.querySelector('.presence-dot')?.classList.contains('presence-dot--offline'))
      .withContext('offline is its own dot state')
      .toBeTrue();
    expect(root.querySelector('.presence-status')?.textContent?.trim())
      .withContext('colour alone is not a signal')
      .toBe('Not connected to KhanaBook');
  });

  it('labels the sign-out control for screen readers', () => {
    const { fixture } = create();
    const btn = (fixture.nativeElement as HTMLElement).querySelector('.logout-btn') as HTMLButtonElement;
    expect(btn.getAttribute('aria-label')).toBe('Sign out');
  });

  it('suppresses the peek tip when not collapsed', () => {
    const { comp } = create();
    comp.collapsed.set(false);
    comp.peek({
      currentTarget: { getBoundingClientRect: () => ({ right: 70, top: 10, height: 44 }) },
    } as unknown as MouseEvent,
      { label: 'Reports', path: '/business/reports', icon: '', iconKey: 'reports' });
    expect(comp.peekTip()).toBeNull();
  });

  it('clears the peek tip on toggle, on hidePeek, and on page scroll', () => {
    const { comp } = create();
    comp.collapsed.set(true);
    comp.peekTip.set({ label: 'x', x: 1, y: 2, maxWidth: 120 });
    comp.toggleCollapsed();
    expect(comp.peekTip()).toBeNull();
    comp.peekTip.set({ label: 'y', x: 1, y: 2, maxWidth: 120 });
    comp.hidePeek();
    expect(comp.peekTip()).toBeNull();
    // A fixed-position chip would otherwise hang in place while the page moves.
    comp.peekTip.set({ label: 'z', x: 1, y: 2, maxWidth: 120 });
    comp.onScroll();
    expect(comp.peekTip()).toBeNull();
  });

  it('toggles via the Ctrl+Shift+B key shortcut on desktop', () => {
    const { comp } = create();
    const ev = new KeyboardEvent('keydown', { key: 'B', ctrlKey: true, shiftKey: true, bubbles: true });
    document.dispatchEvent(ev);
    expect(comp.collapsed()).toBeTrue();
    document.dispatchEvent(ev);
    expect(comp.collapsed()).toBeFalse();
  });

  it('does not toggle via the shortcut in the mobile view', () => {
    const { comp } = create(false);
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'B', ctrlKey: true, shiftKey: true, bubbles: true }));
    expect(comp.collapsed()).toBeFalse();
  });

  it('paints the open drawer above the topbar and its scrim on a phone', () => {
    // Regression: the drawer was z-index 100 and the sticky topbar 101, so the
    // topbar's opaque surface covered the drawer's brand row and close button
    // while the topbar was inert. Both now read rungs of the ladder in styles.css.
    setViewport(PHONE);
    const { fixture, comp } = create(false);
    comp.menuOpen.set(true);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    const layer = (selector: string): number => {
      const el = root.querySelector(selector) as HTMLElement | null;
      expect(el).withContext(`${selector} renders`).toBeTruthy();
      return Number(getComputedStyle(el!).zIndex);
    };

    const topbar = layer('.topbar');
    const scrim = layer('.sidebar-backdrop');
    const drawer = layer('.sidebar');

    expect(drawer).withContext('the drawer header must not disappear under the topbar').toBeGreaterThan(topbar);
    expect(scrim).withContext('the scrim dims the shell, topbar included').toBeGreaterThan(topbar);
    expect(drawer).withContext('the drawer sits above its own scrim').toBeGreaterThan(scrim);
  });

  it('signs out through the auth service', () => {
    const auth = TestBed.inject(AuthService) as unknown as AuthServiceStub;
    const { comp } = create();
    comp.logout();
    expect(auth.logout).toHaveBeenCalled();
  });
});

describe('SidebarLayoutComponent — bootstrap deps', () => {
  beforeEach(() => {
    localStorage.removeItem(COLLAPSE_KEY);
    configureSidebarTestingModule();
  });

  it('creates the layout', () => {
    const fixture = TestBed.createComponent(SidebarLayoutComponent);
    fixture.detectChanges();
    fixture.componentInstance.isMobileView.set(false);
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
    expect((fixture.nativeElement as HTMLElement).querySelector('.sidebar')).not.toBeNull();
  });
});
