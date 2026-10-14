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
 * read across a row (system stock vs physical count vs variance). These specs
 * pin its alignment through getComputedStyle, i.e. the real cascade of
 * styles.css + this component.
 *
 * The page used to centre this table on its own — every header and every cell,
 * horizontally and vertically — which is why a variance report looked nothing
 * like the material list beside it. The design is defined once now (the "Tables"
 * section of styles.css) and a page may only lay its columns out, so what is
 * pinned here is that every cell keeps its column's alignment.
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

    const table = fixture.nativeElement.querySelector('table.data-table') as HTMLTableElement | null;
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

  it('leaves every header label on the shared table design: left aligned', () => {
    const table = mountVariance();
    const headers = Array.from(table.querySelectorAll('thead th')) as Element[];

    expect(headers.length).toBe(5);
    expect(alignments(headers)).toEqual(['left', 'left', 'left', 'left', 'left']);
  });

  it('gives each column value the same alignment as its header', () => {
    const table = mountVariance();
    const headers = Array.from(table.querySelectorAll('thead th')) as Element[];
    const firstRow = Array.from(table.querySelectorAll('tbody tr:first-child td')) as Element[];

    expect(firstRow.length).toBe(headers.length);
    expect(alignments(firstRow)).toEqual(alignments(headers));
  });

  it('keeps every body cell on its column alignment and vertically centred', () => {
    const table = mountVariance();
    const headers = Array.from(table.querySelectorAll('thead th')) as Element[];
    const rows = Array.from(table.querySelectorAll('tbody tr')) as HTMLTableRowElement[];

    expect(rows.length).withContext('two rows').toBe(2);
    for (const row of rows) {
      const cells = Array.from(row.querySelectorAll('td')) as Element[];
      expect(cells.length).toBe(headers.length);
      cells.forEach((cell, column) => {
        const style = getComputedStyle(cell);
        expect(style.textAlign)
          .withContext(`row ${row.rowIndex} column ${column}: shares its header's alignment`)
          .toBe(getComputedStyle(headers[column]).textAlign);
        expect(style.verticalAlign).toBe('middle');
      });
    }
  });
});
