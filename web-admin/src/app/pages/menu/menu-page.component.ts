import { CommonModule } from '@angular/common';
import { Component, inject, signal, NgZone } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessMenuItem, MenuExtractionItem, MenuExtractionJob } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';

@Component({
  selector: 'app-menu-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-shell">
      <section class="panel page-hero">
        <h2>Menu</h2>
        <p class="muted">Current business menu with cleaner alignment for descriptions, pricing, and availability status.</p>
        <div class="hero-meta">
          <span class="chip">Catalog Review</span>
          <span class="chip success">Stock Visibility</span>
          <span class="chip">OCR Import</span>
        </div>
      </section>

      <div class="toolbar">
        <div>
          <h3>Menu Snapshot</h3>
          <p class="muted">Use this list to spot missing descriptions, low stock, and stale updates.</p>
        </div>
        <button class="ghost-btn" (click)="loadMenu()">Refresh</button>
      </div>

      <section class="panel filter-panel" *ngIf="loaded && items.length">
        <div class="filter-grid">
          <div class="filter-group">
            <label for="menu-search">Search</label>
            <input
              id="menu-search"
              class="field-control"
              type="text"
              [(ngModel)]="searchTerm"
              (ngModelChange)="resetPage()"
              placeholder="Search by item, category, or description"
            />
          </div>
          <div class="filter-group">
            <label for="menu-stock">Stock</label>
            <select
              id="menu-stock"
              class="field-select"
              [(ngModel)]="stockFilter"
              (ngModelChange)="resetPage()"
            >
              <option value="ALL">All stock states</option>
              <option value="IN_STOCK">In stock</option>
              <option value="RUNNING_LOW">Running low</option>
              <option value="OUT_OF_STOCK">Out of stock</option>
            </select>
          </div>
          <div class="filter-group">
            <label for="menu-availability">Availability</label>
            <select
              id="menu-availability"
              class="field-select"
              [(ngModel)]="availabilityFilter"
              (ngModelChange)="resetPage()"
            >
              <option value="ALL">All items</option>
              <option value="AVAILABLE">Available</option>
              <option value="UNAVAILABLE">Unavailable</option>
            </select>
          </div>
          <div class="filter-group">
            <label for="menu-size">Rows</label>
            <select
              id="menu-size"
              class="field-select"
              [(ngModel)]="pageSize"
              (ngModelChange)="resetPage()"
            >
              <option [ngValue]="5">5</option>
              <option [ngValue]="10">10</option>
              <option [ngValue]="20">20</option>
            </select>
          </div>
        </div>

        <div class="filter-summary">
          <p class="muted">{{ filteredItems.length }} of {{ items.length }} menu items</p>
          <button class="ghost-btn" (click)="clearFilters()">Clear filters</button>
        </div>
      </section>

      <div class="panel table-wrap" *ngIf="loaded && pagedItems.length; else loading">
        <table class="data-table">
          <thead>
            <tr>
              <th>Item</th>
              <th>Category</th>
              <th>Type</th>
              <th>Price</th>
              <th>Variants</th>
              <th>Availability</th>
              <th>Updated</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let item of pagedItems">
              <td>
                <div class="stacked-meta">
                  <strong>{{ item.name }}</strong>
                  <span class="muted">{{ item.description || 'No description added yet.' }}</span>
                </div>
              </td>
              <td>{{ item.categoryName || '-' }}</td>
              <td>{{ item.foodType || '-' }}</td>
              <td>{{ formatCurrencyValue(item.basePrice) }}</td>
              <td>{{ item.variantCount }}</td>
              <td>
                <span class="chip" [class.success]="item.available" [class.warn]="item.stockStatus === 'RUNNING_LOW'" [class.danger]="item.stockStatus === 'OUT_OF_STOCK'">
                  {{ item.stockStatus }}
                </span>
              </td>
              <td>{{ formatDateValue(item.updatedAt) }}</td>
            </tr>
          </tbody>
        </table>

        <div class="pagination-bar" *ngIf="filteredItems.length > pageSize">
          <p class="muted">Page {{ currentPage }} of {{ totalPages }}</p>
          <div class="pagination-controls">
            <button class="ghost-btn" [disabled]="currentPage === 1" (click)="goToPage(currentPage - 1)">Previous</button>
            <button class="ghost-btn" [disabled]="currentPage === totalPages" (click)="goToPage(currentPage + 1)">Next</button>
          </div>
        </div>
      </div>

      <ng-template #loading>
        <div class="panel loading">{{ loaded ? 'No menu items match the current filters.' : 'Loading menu...' }}</div>
      </ng-template>

      <section class="panel filter-panel ocr-panel">
        <div class="toolbar">
          <div>
            <h3>Import Menu from File</h3>
            <p class="muted">Upload a menu PDF or image (JPG/PNG, max 10 MB). We extract the item table and show a preview.</p>
          </div>
          <label class="primary-btn upload-btn">
            {{ uploading() ? 'Uploading...' : 'Choose file' }}
            <input type="file" accept=".pdf,.jpg,.jpeg,.png" (change)="onFileSelected($event)" [disabled]="uploading()" hidden />
          </label>
        </div>

        <div class="loading" *ngIf="uploading()">Uploading menu file...</div>

        <div class="ocr-progress" *ngIf="job() && (job()!.status === 'PENDING' || job()!.status === 'PROCESSING')">
          <p class="muted">Extracting items... ({{ job()!.status }})</p>
          <div class="spinner"></div>
        </div>

        <div class="ocr-error" *ngIf="job() && job()!.status === 'FAILED'">
          <p class="error-text">Extraction failed: {{ job()!.errorMessage || 'Unknown error' }}</p>
          <button class="ghost-btn" (click)="resetJob()">Try another file</button>
        </div>

        <div class="ocr-result" *ngIf="job() && job()!.status === 'COMPLETED'">
          <div class="result-header">
            <p class="muted">{{ extractedItems().length }} items extracted.</p>
            <span class="chip">Preview only</span>
          </div>
          <p class="hint-text">
            Review the extracted items below. Importing into the live menu happens on the
            Android POS app (sync). This web preview is read-only for v1.
          </p>

          <div class="panel table-wrap extracted-table" *ngIf="extractedItems().length">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Item</th>
                  <th>Half</th>
                  <th>Full</th>
                  <th>Price</th>
                  <th>Description</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let row of extractedItems()">
                  <td><strong>{{ row.itemName }}</strong></td>
                  <td>{{ row.halfPrice || '-' }}</td>
                  <td>{{ row.fullPrice || '-' }}</td>
                  <td>{{ row.price || '-' }}</td>
                  <td class="muted">{{ row.description || '-' }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="modal-actions">
            <button class="ghost-btn" (click)="resetJob()">Done</button>
          </div>
        </div>
      </section>

      <div class="toast" *ngIf="toast()">{{ toast() }}</div>
    </div>
  `,
  styles: [`
    .ocr-panel { margin-top: 1.25rem; }
    .upload-btn { display: inline-flex; align-items: center; cursor: pointer; }
    .primary-btn {
      background: var(--brand, #b56a2d);
      color: #fff;
      border: none;
      border-radius: 12px;
      padding: 0.6rem 1.1rem;
      font-weight: 600;
      cursor: pointer;
    }
    .primary-btn:disabled { opacity: 0.6; cursor: default; }
    .spinner {
      width: 26px; height: 26px;
      border: 3px solid rgba(181, 106, 45, 0.25);
      border-top-color: var(--brand, #b56a2d);
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
      margin-top: 0.5rem;
    }
    @keyframes spin { to { transform: rotate(360deg); } }
    .ocr-result { margin-top: 0.5rem; }
    .result-header { display: flex; align-items: center; gap: 0.75rem; }
    .extracted-table { margin-top: 0.75rem; }
    .hint-text { color: #6b7280; font-size: 0.85rem; margin: 0.4rem 0 0.75rem; }
    .modal-actions { display: flex; justify-content: flex-end; margin-top: 1rem; }
    .error-text { color: #b03030; font-size: 0.9rem; }
    .toast {
      position: fixed; bottom: 1.5rem; right: 1.5rem;
      background: #24170f; color: #fff;
      padding: 0.85rem 1.25rem; border-radius: 12px;
      box-shadow: 0 12px 30px rgba(0, 0, 0, 0.25);
      z-index: 1100; max-width: 340px;
    }
  `]
})
export class MenuPageComponent {
  private readonly api = inject(BusinessApiService);
  private readonly zone = inject(NgZone);

  items: BusinessMenuItem[] = [];
  loaded = false;

  searchTerm = '';
  stockFilter: 'ALL' | 'IN_STOCK' | 'RUNNING_LOW' | 'OUT_OF_STOCK' = 'ALL';
  availabilityFilter: 'ALL' | 'AVAILABLE' | 'UNAVAILABLE' = 'ALL';
  pageSize = 10;
  currentPage = 1;

  readonly uploading = signal(false);
  readonly job = signal<MenuExtractionJob | null>(null);
  readonly extractedItems = signal<MenuExtractionItem[]>([]);
  readonly toast = signal<string | null>(null);

  private pollTimer: ReturnType<typeof setInterval> | null = null;

  constructor() {
    this.loadMenu();
  }

  get filteredItems(): BusinessMenuItem[] {
    const search = this.searchTerm.trim().toLowerCase();

    return this.items.filter((item) => {
      const matchesSearch = !search || [
        item.name,
        item.categoryName ?? '',
        item.description ?? '',
        item.foodType ?? ''
      ].some((value) => value.toLowerCase().includes(search));

      const matchesStock = this.stockFilter === 'ALL' || item.stockStatus === this.stockFilter;
      const matchesAvailability =
        this.availabilityFilter === 'ALL' ||
        (this.availabilityFilter === 'AVAILABLE' && item.available) ||
        (this.availabilityFilter === 'UNAVAILABLE' && !item.available);

      return matchesSearch && matchesStock && matchesAvailability;
    });
  }

  get pagedItems(): BusinessMenuItem[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredItems.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filteredItems.length / this.pageSize));
  }

  loadMenu(): void {
    this.loaded = false;
    this.api.getMenu().subscribe({
      next: (data) => {
        this.items = data;
        this.loaded = true;
        this.currentPage = 1;
      },
      error: () => { this.loaded = true; }
    });
  }

  resetPage(): void {
    this.currentPage = 1;
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.stockFilter = 'ALL';
    this.availabilityFilter = 'ALL';
    this.pageSize = 10;
    this.currentPage = 1;
  }

  goToPage(page: number): void {
    this.currentPage = Math.min(Math.max(1, page), this.totalPages);
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    const allowed = /\.(pdf|jpe?g|png)$/i.test(file.name);
    if (!allowed) {
      this.notify('Unsupported file type. Use PDF, JPG, or PNG.');
      input.value = '';
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      this.notify('File exceeds the 10 MB limit.');
      input.value = '';
      return;
    }

    this.uploading.set(true);
    this.job.set(null);
    this.extractedItems.set([]);

    this.api.uploadMenuFile(file).subscribe({
      next: (res) => {
        this.uploading.set(false);
        this.pollJob(res.jobId);
      },
      error: (err) => {
        this.uploading.set(false);
        const e = err?.error?.error;
        this.notify(e || 'Upload failed. Please try again.');
      }
    });

    input.value = '';
  }

  private pollJob(jobId: number): void {
    this.stopPoll();
    this.pollTimer = setInterval(() => {
      this.api.getMenuJobStatus(jobId).subscribe({
        next: (job) => {
          this.zone.run(() => {
            this.job.set(job);
            if (job.status === 'COMPLETED') {
              this.parseExtracted(job.extractedDataJson);
              this.stopPoll();
            } else if (job.status === 'FAILED') {
              this.stopPoll();
            }
          });
        },
        error: () => {
          this.zone.run(() => this.notify('Could not read extraction status.'));
          this.stopPoll();
        }
      });
    }, 2000);
  }

  private parseExtracted(json: string | null): void {
    if (!json) {
      this.extractedItems.set([]);
      return;
    }
    try {
      const parsed = JSON.parse(json) as MenuExtractionItem[];
      this.extractedItems.set(Array.isArray(parsed) ? parsed : []);
    } catch {
      this.extractedItems.set([]);
    }
  }

  resetJob(): void {
    this.stopPoll();
    this.job.set(null);
    this.extractedItems.set([]);
  }

  private stopPoll(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  }

  private notify(message: string): void {
    this.toast.set(message);
    setTimeout(() => this.toast.set(null), 2600);
  }

  formatCurrencyValue(value: number): string {
    return formatCurrency(value);
  }

  formatDateValue(value: number | null): string {
    return formatDate(value);
  }
}
