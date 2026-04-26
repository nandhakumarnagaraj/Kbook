import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { AdminApiService } from '../../core/services/admin-api.service';
import { AdminBusinessDetail, AdminBusinessListItem } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';

@Component({
  selector: 'app-businesses-page',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="page-shell">
      <div class="toolbar">
        <div>
          <h2>Businesses</h2>
          <p class="muted">Business directory for platform admins.</p>
        </div>
        <button class="ghost-btn" (click)="loadBusinesses()">Refresh</button>
      </div>

      <div class="panel table-wrap" *ngIf="businesses.length; else loading">
        <table>
          <thead>
            <tr>
              <th>Business</th>
              <th>Owner</th>
              <th>Orders</th>
              <th>Menu</th>
              <th>Staff</th>
              <th>Updated</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let business of businesses" (click)="showDetails(business)" style="cursor:pointer">
              <td>
                <strong>{{ business.shopName || '-' }}</strong><br>
                <span class="muted">#{{ business.restaurantId }}</span>
              </td>
              <td>{{ business.ownerName || '-' }}</td>
              <td>{{ business.orderCount }}</td>
              <td>{{ business.menuCount }}</td>
              <td>{{ business.staffCount }}</td>
              <td>{{ formatDateValue(business.updatedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="panel loading" *ngIf="selectedDetail() as detail">
        <div class="section-head">
          <div>
            <h3>{{ detail.shopName }}</h3>
            <p class="muted">Restaurant ID {{ detail.restaurantId }}</p>
          </div>
          <span class="chip" [class.success]="detail.websiteEnabled">{{ detail.websiteEnabled ? 'Storefront Live' : 'Storefront Off' }}</span>
        </div>
        <div class="stats-grid">
          <article class="panel stat-card">
            <h3>Total Revenue</h3>
            <strong>{{ formatCurrencyValue(detail.totalRevenue) }}</strong>
          </article>
          <article class="panel stat-card">
            <h3>POS Orders</h3>
            <strong>{{ detail.posOrderCount }}</strong>
          </article>
          <article class="panel stat-card">
            <h3>Online Orders</h3>
            <strong>{{ detail.onlineOrderCount }}</strong>
          </article>
          <article class="panel stat-card">
            <h3>Timezone</h3>
            <strong>{{ detail.timezone || '-' }}</strong>
          </article>
        </div>
      </div>

      <ng-template #loading>
        <div class="panel loading">Loading businesses...</div>
      </ng-template>
    </div>
  `
})
export class BusinessesPageComponent {
  private readonly api = inject(AdminApiService);

  businesses: AdminBusinessListItem[] = [];
  readonly selectedDetail = signal<AdminBusinessDetail | null>(null);

  constructor() {
    this.loadBusinesses();
  }

  loadBusinesses(): void {
    this.api.getBusinesses().subscribe((data) => {
      this.businesses = data;
    });
  }

  showDetails(business: AdminBusinessListItem): void {
    this.api.getBusinessDetail(business.restaurantId).subscribe((detail) => {
      this.selectedDetail.set(detail);
    });
  }

  formatDateValue(value: number | null): string {
    return formatDate(value);
  }

  formatCurrencyValue(value: number): string {
    return formatCurrency(value);
  }
}
