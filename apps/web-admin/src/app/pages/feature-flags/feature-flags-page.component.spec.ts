import { TestBed, ComponentFixture } from '@angular/core/testing';
import { of } from 'rxjs';
import { FeatureFlagsPageComponent } from './feature-flags-page.component';
import { AdminApiService } from '../../core/services/admin-api.service';
import { ToastService } from '../../core/services/toast.service';
import { FeatureFlagAdminItem } from '../../core/models/api.models';

/**
 * The page no longer owns a hardcoded sample catalogue: `loadFlags()` reads
 * `/admin/feature-flags` and maps each row through FLAG_METADATA. These specs
 * therefore drive the page from a stubbed AdminApiService and assert the
 * behaviour that actually runs — paging, filtering, selection and the draft
 * that follows the selected row.
 */
describe('FeatureFlagsPageComponent — reference editor layout', () => {
  let fixture: ComponentFixture<FeatureFlagsPageComponent>;
  let component: FeatureFlagsPageComponent;
  let adminApi: jasmine.SpyObj<AdminApiService>;

  const flag = (flagKey: string, defaultEnabled: boolean, killSwitched = false): FeatureFlagAdminItem => ({
    flagKey,
    defaultEnabled,
    killSwitched,
    description: flagKey,
    updatedAt: 0,
    effectiveState: defaultEnabled,
  });

  /** 28 server rows: 15 per page, mixed categories and enablement. */
  const serverFlags: FeatureFlagAdminItem[] = [
    flag('ai.menu.import', true),
    flag('easebuzz_upi_v2', false),
    flag('inventory.enabled', true),
    flag('staff.management', true, true),
    ...Array.from({ length: 24 }, (_, i) => flag(`custom.flag.${i + 1}`, i % 2 === 0)),
  ];

  beforeEach(async () => {
    adminApi = jasmine.createSpyObj<AdminApiService>('AdminApiService', [
      'getFeatureFlags',
      'setFeatureFlagDefault',
      'getFeatureFlagAudit',
    ]);
    adminApi.getFeatureFlags.and.returnValue(of(serverFlags));

    await TestBed.configureTestingModule({
      imports: [FeatureFlagsPageComponent],
      providers: [
        { provide: AdminApiService, useValue: adminApi },
        { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['show', 'error', 'success']) },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(FeatureFlagsPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders four KPI tiles and the live flags header chip', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('.kb-kpi').length).toBe(4);
    expect(el.textContent).toContain('Live PostgreSQL Flags');
  });

  it('renders one row per server flag, 15 per page', () => {
    expect(component.flags().length).toBe(serverFlags.length);
    expect(component.pagedRows().length).toBe(15);
    component.page.set(2);
    expect(component.pagedRows().length).toBe(serverFlags.length - 15);
  });

  it('maps metadata onto the server rows instead of inventing names', () => {
    const flag = component.flags().find((f) => f.key === 'easebuzz_upi_v2')!;
    expect(flag.name).toBe('Easebuzz Dynamic UPI v2');
    expect(flag.category).toBe('Payments');
    expect(flag.enabled).toBeFalse();

    const unmapped = component.flags().find((f) => f.key === 'custom.flag.1')!;
    expect(unmapped.name).toBe('custom.flag.1');
    expect(unmapped.category).toBe('Settings');
  });

  it('filters by category, status and search', () => {
    component.categoryFilter = 'Payments';
    expect(component.filteredRows().every((f) => f.category === 'Payments')).toBeTrue();
    expect(component.filteredRows().length).toBe(1);

    component.categoryFilter = '';
    component.statusFilter = 'off';
    expect(component.filteredRows().every((f) => !f.enabled)).toBeTrue();

    component.statusFilter = '';
    component.search = 'ai.menu';
    expect(component.filteredRows().map((f) => f.key)).toEqual(['ai.menu.import']);
  });

  it('selects the first flag on load and keeps the draft in sync', () => {
    const first = component.flags()[0];
    expect(component.selected()?.key).toBe(first.key);
    expect(component.draftEnabled()).toBe(first.enabled);

    component.toggleDraft();
    expect(component.draftEnabled()).toBe(!first.enabled);
  });

  it('writes the toggled default back to the server, not just to the row', () => {
    const flag = component.flags().find((f) => f.key === 'ai.menu.import')!;
    adminApi.setFeatureFlagDefault.and.returnValue(of(void 0));

    component.toggleFlag(flag);

    expect(adminApi.setFeatureFlagDefault).toHaveBeenCalledWith('ai.menu.import', false);
    expect(flag.enabled).toBeFalse();
  });

  it('derives the KPI counts from the loaded flags', () => {
    const kpis = component.kpis();
    const enabled = serverFlags.filter((f) => f.defaultEnabled).length;
    expect(kpis[0].value).toBe(serverFlags.length);
    expect(kpis[1].value).toBe(enabled);
    expect(kpis[2].value).toBe(serverFlags.length - enabled);
    expect(kpis[3].value).toBe(serverFlags.filter((f) => f.killSwitched).length);
  });
});
