import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { PaginationBarComponent } from './pagination-bar.component';

@Component({
  standalone: true,
  imports: [PaginationBarComponent],
  template: `
    <app-pagination-bar
      [currentPage]="page()"
      [pageSize]="10"
      [totalItems]="total()"
      (pageChange)="page.set($event)"
    />
  `,
})
class HostComponent {
  readonly page = signal(1);
  readonly total = signal(48);
}

describe('PaginationBarComponent', () => {
  let host: HostComponent;
  let fixture: ReturnType<typeof TestBed.createComponent<HostComponent>>;

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function render(): void {
    fixture.detectChanges();
  }

  function navButton(label: 'Previous page' | 'Next page'): HTMLButtonElement {
    return el().querySelector(`button[aria-label="${label}"]`) as HTMLButtonElement;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
    render();
  });

  it('shows the item range for the first page', () => {
    expect(el().querySelector('.pagination-range')?.textContent?.replace(/\s+/g, ' ').trim())
      .toBe('Showing 1–10 of 48 items');
  });

  it('disables Previous on the first page and enables Next', () => {
    expect(navButton('Previous page').disabled).toBeTrue();
    expect(navButton('Next page').disabled).toBeFalse();
  });

  it('disables Next on the last page and shows a short final range', () => {
    host.page.set(5);
    render();
    expect(el().querySelector('.pagination-range')?.textContent?.replace(/\s+/g, ' ').trim())
      .toBe('Showing 41–48 of 48 items');
    expect(navButton('Next page').disabled).toBeTrue();
    expect(navButton('Previous page').disabled).toBeFalse();
  });

  it('highlights the current page only', () => {
    host.page.set(3);
    render();
    const active = el().querySelectorAll('.pagination-page--active');
    expect(active.length).toBe(1);
    expect(active[0].textContent?.trim()).toBe('3');
    expect(active[0].getAttribute('aria-current')).toBe('page');
  });

  it('navigates on Next, Previous and direct page clicks', () => {
    navButton('Next page').click();
    render();
    expect(host.page()).toBe(2);

    navButton('Previous page').click();
    render();
    expect(host.page()).toBe(1);

    (el().querySelector('button[aria-label="Page 4"]') as HTMLButtonElement).click();
    render();
    expect(host.page()).toBe(4);
  });

  it('shows an empty range and no page buttons for an empty dataset', () => {
    host.total.set(0);
    host.page.set(1);
    render();
    expect(el().querySelector('.pagination-range')?.textContent?.replace(/\s+/g, ' ').trim())
      .toBe('Showing 0 of 0 items');
    expect(navButton('Next page').disabled).toBeTrue();
    expect(navButton('Previous page').disabled).toBeTrue();
  });

  it('uses ellipses for large page counts and keeps the current page visible', () => {
    host.total.set(500);
    host.page.set(25);
    render();
    const labels = Array.from(el().querySelectorAll('.pagination-page')).map((b) => b.textContent?.trim());
    expect(labels).toContain('25');
    expect(labels[0]).toBe('1');
    expect(labels[labels.length - 1]).toBe('50');
    expect(el().querySelectorAll('.pagination-ellipsis').length).toBe(2);
  });
});
