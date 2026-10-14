import { TestBed, ComponentFixture } from '@angular/core/testing';
import { of } from 'rxjs';

import { InventoryPageComponent } from './inventory-page.component';
import { BusinessApiService } from '../../core/services/business-api.service';
import { ToastService } from '../../core/services/toast.service';

/**
 * The empty "no materials" state used to pass `description` and a Material Icons
 * ligature name to <app-empty-state>, but the component only declares `text` and
 * renders `actionLabel`-gated actions. The unknown attribute was silently dropped
 * (strictTemplates does not check plain static attributes), so the description and
 * the Add Material call-to-action never appeared. These specs guard the real
 * contract: the copy comes from `text` and the action is reachable.
 */
describe('InventoryPageComponent — empty materials state', () => {
  let fixture: ComponentFixture<InventoryPageComponent>;
  let api: jasmine.SpyObj<BusinessApiService>;

  beforeEach(() => {
    api = jasmine.createSpyObj<BusinessApiService>('BusinessApiService', [
      'getInventoryMaterials',
      'getVendors',
    ]);
    api.getInventoryMaterials.and.returnValue(of([]));
    api.getVendors.and.returnValue(of([]));

    TestBed.configureTestingModule({
      imports: [InventoryPageComponent],
      providers: [
        { provide: BusinessApiService, useValue: api },
        { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['show']) },
      ],
    });
  });

  function mountEmpty(): HTMLElement {
    fixture = TestBed.createComponent(InventoryPageComponent);
    fixture.detectChanges();
    const emptyState = fixture.nativeElement.querySelector('app-empty-state') as HTMLElement | null;
    expect(emptyState).withContext('empty state should render when there are no materials').toBeTruthy();
    return emptyState!;
  }

  it('renders the description via the component text input', () => {
    const emptyState = mountEmpty();
    const text = emptyState.querySelector('.empty-text');
    expect(text?.textContent).toContain('Add raw materials to track stock levels.');
  });

  it('renders an emoji icon instead of a Material Icons ligature name', () => {
    const emptyState = mountEmpty();
    expect(emptyState.querySelector('.empty-icon')?.textContent?.trim()).toBe('📦');
  });

  it('renders the Add Material action and opens the material modal when clicked', () => {
    const emptyState = mountEmpty();
    const actionButton = emptyState.querySelector('.empty-action button') as HTMLButtonElement | null;
    expect(actionButton).withContext('action button should render when actionLabel is set').toBeTruthy();
    expect(actionButton!.textContent).toContain('Add Material');

    actionButton!.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.showMaterialModal).toBe(true);
  });
});

/**
 * The variance report is the one real table on this page, and its values are
 * read across a row (system stock vs physical count vs variance). Column labels
 * used to sit left-aligned above values that did not share their column's
 * alignment, so a row was hard to scan. These specs pin the alignment through
 * getComputedStyle, i.e. the real cascade of styles.css + this component.
 */
describe('InventoryPageComponent — variance table alignment', () => {
  let fixture: ComponentFixture<InventoryPageComponent>;

  function mountVariance(): HTMLTableElement {
    const api = jasmine.createSpyObj<BusinessApiService>('BusinessApiService', [
      'getInventoryMaterials',
      'getVendors',
    ]);
    api.getInventoryMaterials.and.returnValue(of([]));
    api.getVendors.and.returnValue(of([]));

    TestBed.configureTestingModule({
      imports: [InventoryPageComponent],
      providers: [
        { provide: BusinessApiService, useValue: api },
        { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['show']) },
      ],
    });

    fixture = TestBed.createComponent(InventoryPageComponent);
    fixture.componentInstance.activeTab = 'variance';
    fixture.componentInstance.varianceData = [
      { materialName: 'Basmati Rice', systemStock: 12, physicalCount: 10, variance: -2, variancePct: -16.7 },
      { materialName: 'Paneer', systemStock: 4, physicalCount: 5, variance: 1, variancePct: 25 },
    ];
    fixture.detectChanges();

    const table = fixture.nativeElement.querySelector('table.styled') as HTMLTableElement | null;
    expect(table).withContext('variance table should render once rows exist').toBeTruthy();
    return table!;
  }

  function alignments(cells: Element[]): string[] {
    return cells.map((cell) => getComputedStyle(cell).textAlign);
  }

  afterEach(() => {
    if (fixture) {
      fixture.destroy();
    }
  });

  it('centers every header label', () => {
    const table = mountVariance();
    const headers = Array.from(table.querySelectorAll('thead th')) as Element[];

    expect(headers.length).toBe(5);
    expect(alignments(headers)).toEqual(['center', 'center', 'center', 'center', 'center']);
  });

  it('gives each column value the same alignment as its header', () => {
    const table = mountVariance();
    const headers = Array.from(table.querySelectorAll('thead th')) as Element[];
    const firstRow = Array.from(table.querySelectorAll('tbody tr:first-child td')) as Element[];

    expect(firstRow.length).toBe(headers.length);
    expect(alignments(firstRow)).toEqual(alignments(headers));
  });

  it('centers every body cell horizontally and vertically', () => {
    const table = mountVariance();
    const cells = Array.from(table.querySelectorAll('tbody td')) as Element[];

    expect(cells.length).withContext('two rows of five cells').toBe(10);
    for (const cell of cells) {
      const style = getComputedStyle(cell);
      expect(style.textAlign).toBe('center');
      expect(style.verticalAlign).toBe('middle');
    }
  });
});
