import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';

import { SidebarLayoutComponent } from './sidebar-layout.component';
import { AuthService } from '../../core/auth/auth.service';
import { BusinessApiService } from '../../core/services/business-api.service';

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
      { provide: HttpClient, useClass: HttpClientStub },
    ],
  });
}

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

  it('sets a peek tip when hovering a link while collapsed on desktop', () => {
    const { comp } = create();
    comp.collapsed.set(true);
    const fakeEvent = {
      currentTarget: { getBoundingClientRect: () => ({ right: 70, top: 10, height: 44 }) },
    } as unknown as MouseEvent;
    comp.peek(fakeEvent, { label: 'Reports', path: '/business/reports', icon: '', iconKey: 'reports' });
    expect(comp.peekTip()?.label).toBe('Reports');
    expect(comp.peekTip()?.x).toBe(80);
    expect(comp.peekTip()?.y).toBe(32);
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

  it('clears the peek tip on toggle and on hidePeek', () => {
    const { comp } = create();
    comp.collapsed.set(true);
    comp.peekTip.set({ label: 'x', x: 1, y: 2 });
    comp.toggleCollapsed();
    expect(comp.peekTip()).toBeNull();
    comp.peekTip.set({ label: 'y', x: 1, y: 2 });
    comp.hidePeek();
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
