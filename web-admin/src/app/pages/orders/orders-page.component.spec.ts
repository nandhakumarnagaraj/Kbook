import { Component, EventEmitter, Input, Output } from '@angular/core';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, Subject } from 'rxjs';

import { OrdersPageComponent } from './orders-page.component';
import { LiveOrdersTabComponent } from './live-orders-tab.component';
import { OrderHistoryTabComponent } from './order-history-tab.component';

/**
 * Lightweight stand-ins for the two heavy tabs. The real LiveOrdersTabComponent
 * starts a 30-second poll and both call the API on init, which is exactly what
 * these tests must NOT do — they assert which tab the shell mounts.
 *
 * A stub must still declare every input and output the shell binds, or the page's
 * template fails to compile (NG0303) and the whole spec file dies — which is how the
 * missing searchQuery pair went unnoticed. The drift guard below pins that.
 */
@Component({ selector: 'app-live-orders-tab', standalone: true, template: `<div class="stub-live">live board</div>` })
class LiveOrdersTabStub {}

@Component({ selector: 'app-order-history-tab', standalone: true, template: `<div class="stub-history">history table</div>` })
class OrderHistoryTabStub {
  @Input() searchQuery = '';
  @Output() searchQueryChange = new EventEmitter<string>();
}

describe('OrdersPageComponent (Orders + Active Orders merged into tabs)', () => {
  let fixture: ComponentFixture<OrdersPageComponent>;
  let router: jasmine.SpyObj<Router>;
  let queryParams: Subject<ReturnType<typeof convertToParamMap>>;
  let initialTab: string | null;

  beforeEach(() => {
    queryParams = new Subject();
    initialTab = null;
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);

    const routeStub = {
      snapshot: { queryParamMap: { get: () => initialTab } },
      queryParamMap: queryParams.asObservable(),
    };

    TestBed.configureTestingModule({
      imports: [OrdersPageComponent],
      providers: [
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: routeStub },
      ],
    });

    // Swap the real tabs for stubs so the shell's tab logic is tested in isolation.
    TestBed.overrideComponent(OrdersPageComponent, {
      remove: { imports: [LiveOrdersTabComponent, OrderHistoryTabComponent] },
      add: { imports: [LiveOrdersTabStub, OrderHistoryTabStub] },
    });

    fixture = TestBed.createComponent(OrdersPageComponent);
    fixture.detectChanges();
  });

  function tabButtons(): HTMLButtonElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('.orders-tab'));
  }

  it('opens on the Live Floor tab by default', () => {
    expect(fixture.componentInstance.tab()).toBe('live');
    expect(fixture.nativeElement.querySelector('.stub-live')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.stub-history')).toBeNull();
  });

  it('renders the history tab when the URL asks for ?tab=history', () => {
    // Rebuild with the deep-link value the redirect target / bookmarks use.
    TestBed.resetTestingModule();
    initialTab = 'history';
    const deepRoute = {
      snapshot: { queryParamMap: { get: () => initialTab } },
      queryParamMap: of(convertToParamMap({ tab: 'history' })),
    };
    TestBed.configureTestingModule({
      imports: [OrdersPageComponent],
      providers: [
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: deepRoute },
      ],
    });
    TestBed.overrideComponent(OrdersPageComponent, {
      remove: { imports: [LiveOrdersTabComponent, OrderHistoryTabComponent] },
      add: { imports: [LiveOrdersTabStub, OrderHistoryTabStub] },
    });

    const deepFixture = TestBed.createComponent(OrdersPageComponent);
    deepFixture.detectChanges();

    expect(deepFixture.componentInstance.tab()).toBe('history');
    expect(deepFixture.nativeElement.querySelector('.stub-history')).toBeTruthy();
    expect(deepFixture.nativeElement.querySelector('.stub-live')).toBeNull();
  });

  it('switches tab on click and records the tab in the URL', () => {
    tabButtons()[1].click();
    fixture.detectChanges();

    expect(fixture.componentInstance.tab()).toBe('history');
    expect(fixture.nativeElement.querySelector('.stub-history')).toBeTruthy();
    // The live board must be torn down so its 30s polling stops.
    expect(fixture.nativeElement.querySelector('.stub-live')).toBeNull();

    expect(router.navigate).toHaveBeenCalledWith([], {
      relativeTo: jasmine.anything(),
      queryParams: { tab: 'history' },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  });

  it('does not navigate again when the already-active tab is clicked', () => {
    tabButtons()[0].click();
    fixture.detectChanges();

    expect(router.navigate).not.toHaveBeenCalled();
    expect(fixture.componentInstance.tab()).toBe('live');
  });

  it('follows browser back/forward by tracking the query param stream', () => {
    queryParams.next(convertToParamMap({ tab: 'history' }));
    fixture.detectChanges();
    expect(fixture.componentInstance.tab()).toBe('history');

    queryParams.next(convertToParamMap({ tab: 'live' }));
    fixture.detectChanges();
    expect(fixture.componentInstance.tab()).toBe('live');
  });

  it('keeps the tab stubs in step with the components they stand in for', () => {
    // Read the compiled component def, i.e. the same inputs/outputs Angular binds against.
    function ioKeys(component: unknown): { inputs: string[]; outputs: string[] } {
      const def = (component as { ɵcmp?: { inputs?: Record<string, unknown>; outputs?: Record<string, unknown> } }).ɵcmp;
      if (!def) throw new Error('component has no compiled definition');
      return { inputs: Object.keys(def.inputs ?? {}), outputs: Object.keys(def.outputs ?? {}) };
    }

    const pairs: { real: unknown; stub: unknown; name: string }[] = [
      { real: LiveOrdersTabComponent, stub: LiveOrdersTabStub, name: 'LiveOrdersTabComponent' },
      { real: OrderHistoryTabComponent, stub: OrderHistoryTabStub, name: 'OrderHistoryTabComponent' },
    ];

    for (const { real, stub, name } of pairs) {
      const realIo = ioKeys(real);
      const stubIo = ioKeys(stub);
      for (const input of realIo.inputs) {
        expect(stubIo.inputs)
          .withContext(`${name}: the stub must accept the input "${input}" the page may bind`)
          .toContain(input);
      }
      for (const output of realIo.outputs) {
        expect(stubIo.outputs)
          .withContext(`${name}: the stub must declare the output "${output}" the page may bind`)
          .toContain(output);
      }
    }
  });

  it('exposes only the visible panel as a tabpanel', () => {
    const panels = fixture.nativeElement.querySelectorAll('[role="tabpanel"]');
    expect(panels.length).toBe(1);
    expect(panels[0].id).toBe('orders-panel-live');

    tabButtons()[1].click();
    fixture.detectChanges();
    const after = fixture.nativeElement.querySelectorAll('[role="tabpanel"]');
    expect(after.length).toBe(1);
    expect(after[0].id).toBe('orders-panel-history');
  });
});
