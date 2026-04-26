import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessStaffItem } from '../../core/models/api.models';
import { formatDate } from '../../shared/formatters';

@Component({
  selector: 'app-staff-page',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="page-shell">
      <div class="toolbar">
        <div>
          <h2>Staff</h2>
          <p class="muted">Business users and current roles.</p>
        </div>
        <button class="ghost-btn" (click)="loadStaff()">Refresh</button>
      </div>

      <div class="panel table-wrap" *ngIf="staff.length; else loading">
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Login ID</th>
              <th>Role</th>
              <th>Contact</th>
              <th>Status</th>
              <th>Updated</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let item of staff">
              <td>{{ item.name }}</td>
              <td>{{ item.loginId }}</td>
              <td><span class="chip">{{ item.role }}</span></td>
              <td>{{ item.whatsappNumber || item.email || '-' }}</td>
              <td>
                <span class="chip" [class.success]="item.active" [class.danger]="!item.active">
                  {{ item.active ? 'Active' : 'Inactive' }}
                </span>
              </td>
              <td>{{ formatDateValue(item.updatedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <ng-template #loading>
        <div class="panel loading">Loading staff...</div>
      </ng-template>
    </div>
  `
})
export class StaffPageComponent {
  private readonly api = inject(BusinessApiService);

  staff: BusinessStaffItem[] = [];

  constructor() {
    this.loadStaff();
  }

  loadStaff(): void {
    this.api.getStaff().subscribe((data) => {
      this.staff = data;
    });
  }

  formatDateValue(value: number | null): string {
    return formatDate(value);
  }
}
