import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessMenuItem } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';

@Component({
  selector: 'app-menu-page',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="page-shell">
      <div class="toolbar">
        <div>
          <h2>Menu</h2>
          <p class="muted">Current business menu and availability snapshot.</p>
        </div>
        <button class="ghost-btn" (click)="loadMenu()">Refresh</button>
      </div>

      <div class="panel table-wrap" *ngIf="loaded && items.length; else loading">
        <table>
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
            <tr *ngFor="let item of items">
              <td>
                <strong>{{ item.name }}</strong><br>
                <span class="muted">{{ item.description || '' }}</span>
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
      </div>

      <ng-template #loading>
        <div class="panel loading">{{ loaded ? 'No menu items found.' : 'Loading menu...' }}</div>
      </ng-template>
    </div>
  `
})
export class MenuPageComponent {
  private readonly api = inject(BusinessApiService);

  items: BusinessMenuItem[] = [];
  loaded = false;

  constructor() {
    this.loadMenu();
  }

  loadMenu(): void {
    this.loaded = false;
    this.api.getMenu().subscribe({
      next: (data) => { this.items = data; this.loaded = true; },
      error: () => { this.loaded = true; }
    });
  }

  formatCurrencyValue(value: number): string {
    return formatCurrency(value);
  }

  formatDateValue(value: number | null): string {
    return formatDate(value);
  }
}
