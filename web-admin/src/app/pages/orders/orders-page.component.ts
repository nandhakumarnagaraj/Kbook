import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal, ViewChild, AfterViewInit, TemplateRef } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatPaginatorModule, MatPaginator } from '@angular/material/paginator';
import { MatSortModule, MatSort } from '@angular/material/sort';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTabsModule } from '@angular/material/tabs';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { DragDropModule, CdkDragDrop } from '@angular/cdk/drag-drop';
import { SelectionModel } from '@angular/cdk/collections';
import { BusinessApiService } from '../../core/services/business-api.service';
import { BusinessOrder, MarketplaceOrder } from '../../core/models/api.models';
import { formatCurrency, formatDate } from '../../shared/formatters';

type OrderTab = 'pos' | 'online';
type OnlineView = 'table' | 'kanban';

@Component({
  selector: 'app-orders-page',
  standalone: true,
  imports: [
    CommonModule, 
    FormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTabsModule,
    MatDialogModule,
    MatCheckboxModule,
    MatChipsModule,
    MatTooltipModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    MatDividerModule,
    DragDropModule
  ],
  template: `
    <div class="page-container">
      <div class="header-row">
        <div class="header-left">
          <h1 class="page-title">Order Management</h1>
          <p class="page-subtitle">Unified dashboard for POS and marketplace orders.</p>
        </div>
        <div class="header-actions">
          <button mat-flat-button color="primary" (click)="loadAllData()">
            <mat-icon>refresh</mat-icon>
            Sync All Data
          </button>
        </div>
      </div>

      <mat-tab-group (selectedTabChange)="onTabChange($event)" class="main-tabs">
        <!-- POS ORDERS TAB -->
        <mat-tab label="Store (POS)">
          <div class="tab-content">
            <div class="stats-row" *ngIf="orders.length">
              <mat-card class="mini-stat">
                <mat-icon mat-card-avatar>receipt</mat-icon>
                <div class="stat-copy">
                  <div class="val">{{ orders.length }}</div>
                  <div class="lab">Total Orders</div>
                </div>
              </mat-card>
              <mat-card class="mini-stat">
                <mat-icon mat-card-avatar>payments</mat-icon>
                <div class="stat-copy">
                  <div class="val">{{ formatCurrencyValue(totalRevenue()) }}</div>
                  <div class="lab">Total Revenue</div>
                </div>
              </mat-card>
            </div>

            <mat-card class="filter-card mat-elevation-z1">
              <mat-card-content class="filter-row">
                <mat-form-field appearance="outline" class="search-field">
                  <mat-label>Quick Search</mat-label>
                  <mat-icon matPrefix>search</mat-icon>
                  <input matInput (keyup)="applyPosFilter($event)" placeholder="Search code, customer, method..." #posInput>
                </mat-form-field>

                <mat-form-field appearance="outline" class="filter-field">
                  <mat-label>Status</mat-label>
                  <mat-select [(ngModel)]="orderStatusFilter" (selectionChange)="applyPosFilters()">
                    <mat-option value="ALL">All Statuses</mat-option>
                    <mat-option *ngFor="let status of businessOrderStatuses" [value]="status">{{ status }}</mat-option>
                  </mat-select>
                </mat-form-field>

                <div class="spacer"></div>
                
                <button mat-stroked-button color="primary" (click)="exportPdf()" [disabled]="exportingPdf">
                  <mat-icon>picture_as_pdf</mat-icon>
                  Export PDF
                </button>
              </mat-card-content>

              <div class="selection-banner" *ngIf="selection.selected.length > 0">
                <span class="banner-text">{{ selection.selected.length }} orders selected for bulk action</span>
                <div class="spacer"></div>
                <button mat-button (click)="clearSelection()">Cancel</button>
                <button mat-flat-button color="warn" (click)="openBulkRefund()">
                  <mat-icon>currency_exchange</mat-icon>
                  Process Bulk Refund
                </button>
              </div>
            </mat-card>

            <div class="table-container mat-elevation-z2">
              <div class="loading-overlay" *ngIf="!ordersLoaded">
                <mat-spinner diameter="40"></mat-spinner>
              </div>

              <table mat-table [dataSource]="posDataSource" matSort #posSort="matSort">
                <ng-container matColumnDef="select">
                  <th mat-header-cell *matHeaderCellDef>
                    <mat-checkbox (change)="$event ? masterToggle() : null"
                                  [checked]="selection.hasValue() && isAllSelected()"
                                  [indeterminate]="selection.hasValue() && !isAllSelected()">
                    </mat-checkbox>
                  </th>
                  <td mat-cell *matCellDef="let row">
                    <mat-checkbox (click)="$event.stopPropagation()"
                                  (change)="$event ? selection.toggle(row) : null"
                                  [checked]="selection.isSelected(row)">
                    </mat-checkbox>
                  </td>
                </ng-container>

                <ng-container matColumnDef="orderCode">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header> Order ID </th>
                  <td mat-cell *matCellDef="let order"> 
                    <div class="id-cell">
                      <span class="code">{{ order.orderCode }}</span>
                      <span class="source-tag">{{ order.sourceType }}</span>
                    </div>
                  </td>
                </ng-container>

                <ng-container matColumnDef="customer">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header> Customer </th>
                  <td mat-cell *matCellDef="let order">
                    <div class="customer-cell">
                      <span class="name">{{ order.customerName || '-' }}</span>
                      <span class="contact">{{ order.customerContact || '' }}</span>
                    </div>
                  </td>
                </ng-container>

                <ng-container matColumnDef="status">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header> Status </th>
                  <td mat-cell *matCellDef="let order">
                    <span class="status-badge" [class]="getPosStatusClass(order.orderStatus)">
                      {{ order.orderStatus }}
                    </span>
                  </td>
                </ng-container>

                <ng-container matColumnDef="payment">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header> Payment </th>
                  <td mat-cell *matCellDef="let order">
                    <div class="payment-cell">
                      <span class="method">{{ order.paymentMethod }}</span>
                      <span class="p-status" [class.refunded]="order.paymentStatus === 'Refunded'">{{ order.paymentStatus }}</span>
                    </div>
                  </td>
                </ng-container>

                <ng-container matColumnDef="total">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header> Total </th>
                  <td mat-cell *matCellDef="let order">
                    <div class="total-cell">
                      <span class="amount">{{ formatCurrencyValue(order.totalAmount) }}</span>
                      <span class="refunded-amount" *ngIf="order.refundAmount > 0">-{{ formatCurrencyValue(order.refundAmount) }}</span>
                    </div>
                  </td>
                </ng-container>

                <ng-container matColumnDef="createdAt">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header> Date </th>
                  <td mat-cell *matCellDef="let order"> {{ formatDateValue(order.createdAt) }} </td>
                </ng-container>

                <ng-container matColumnDef="actions">
                  <th mat-header-cell *matHeaderCellDef></th>
                  <td mat-cell *matCellDef="let order">
                    <button mat-icon-button color="warn" *ngIf="order.manualRefundAllowed" 
                            matTooltip="Record Manual Refund"
                            (click)="openManualRefund(order)">
                      <mat-icon>undo</mat-icon>
                    </button>
                  </td>
                </ng-container>

                <tr mat-header-row *matHeaderRowDef="posDisplayedColumns"></tr>
                <tr mat-row *matRowDef="let row; columns: posDisplayedColumns;" class="order-row"></tr>
              </table>

              <mat-paginator #posPaginator [pageSizeOptions]="[10, 25, 50]"></mat-paginator>
            </div>
          </div>
        </mat-tab>

        <!-- ONLINE ORDERS TAB -->
        <mat-tab label="Online (Marketplace)">
          <div class="tab-content">
            <div class="online-counts-row" *ngIf="onlineCounts">
              <mat-card class="status-box clickable" (click)="filterOnlineStatus('PENDING')" [class.active]="onlineStatusFilter === 'PENDING'">
                <div class="count warn">{{ onlineCounts.pending }}</div>
                <div class="label">Pending</div>
              </mat-card>
              <mat-card class="status-box clickable" (click)="filterOnlineStatus('ACCEPTED')" [class.active]="onlineStatusFilter === 'ACCEPTED'">
                <div class="count primary">{{ onlineCounts.accepted }}</div>
                <div class="label">Accepted</div>
              </mat-card>
              <mat-card class="status-box clickable" (click)="filterOnlineStatus('READY')" [class.active]="onlineStatusFilter === 'READY'">
                <div class="count info">{{ onlineCounts.ready }}</div>
                <div class="label">Ready</div>
              </mat-card>
              <mat-card class="status-box clickable" (click)="filterOnlineStatus('COMPLETED')" [class.active]="onlineStatusFilter === 'COMPLETED'">
                <div class="count success">{{ onlineCounts.completed }}</div>
                <div class="label">Completed</div>
              </mat-card>
              <mat-card class="status-box clickable" (click)="filterOnlineStatus('REJECTED')" [class.active]="onlineStatusFilter === 'REJECTED'">
                <div class="count danger">{{ onlineCounts.rejected }}</div>
                <div class="label">Rejected</div>
              </mat-card>
            </div>

            <div class="view-actions">
              <div class="view-toggle">
                 <button mat-button [class.selected]="onlineView === 'table'" (click)="onlineView = 'table'">
                    <mat-icon>table_rows</mat-icon> Table
                 </button>
                 <button mat-button [class.selected]="onlineView === 'kanban'" (click)="onlineView = 'kanban'">
                    <mat-icon>dashboard_customize</mat-icon> Board
                 </button>
              </div>
              <div class="spacer"></div>
              <button mat-stroked-button color="primary" (click)="exportMarketplacePdf()">
                <mat-icon>picture_as_pdf</mat-icon> Export Board
              </button>
            </div>

            <div class="table-container mat-elevation-z2" *ngIf="onlineView === 'table'">
              <div class="loading-overlay" *ngIf="!marketplaceOrdersLoaded">
                <mat-spinner diameter="40"></mat-spinner>
              </div>
              <table mat-table [dataSource]="onlineDataSource" matSort #onlineSort="matSort">
                <ng-container matColumnDef="platform">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header> Platform </th>
                  <td mat-cell *matCellDef="let order">
                    <span class="platform-badge" [class]="order.platform.toLowerCase()">{{ order.platform }}</span>
                  </td>
                </ng-container>

                <ng-container matColumnDef="platformOrderId">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header> Order ID </th>
                  <td mat-cell *matCellDef="let order"> #{{ order.platformOrderId }} </td>
                </ng-container>

                <ng-container matColumnDef="customer">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header> Customer </th>
                  <td mat-cell *matCellDef="let order">
                    <div class="customer-cell">
                      <span class="name">{{ order.customerName || '-' }}</span>
                      <span class="contact">{{ order.customerPhone || '' }}</span>
                    </div>
                  </td>
                </ng-container>

                <ng-container matColumnDef="totalAmount">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header> Amount </th>
                  <td mat-cell *matCellDef="let order"> {{ formatCurrencyValue(order.totalAmount) }} </td>
                </ng-container>

                <ng-container matColumnDef="orderStatus">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header> Status </th>
                  <td mat-cell *matCellDef="let order">
                    <span class="status-badge" [class]="getOnlineStatusClass(order.orderStatus)">
                      {{ order.orderStatus }}
                    </span>
                  </td>
                </ng-container>

                <ng-container matColumnDef="createdAt">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header> Time </th>
                  <td mat-cell *matCellDef="let order"> {{ formatDateValue(order.createdAt) }} </td>
                </ng-container>

                <ng-container matColumnDef="actions">
                  <th mat-header-cell *matHeaderCellDef> Actions </th>
                  <td mat-cell *matCellDef="let order">
                    <div class="action-btn-row">
                      <button mat-flat-button color="primary" *ngIf="order.orderStatus === 'PENDING'" (click)="acceptOrder(order)">Accept</button>
                      <button mat-button color="warn" *ngIf="order.orderStatus === 'PENDING'" (click)="startReject(order)">Reject</button>
                      <button mat-flat-button color="accent" *ngIf="order.orderStatus === 'ACCEPTED'" (click)="markReady(order)">Mark Ready</button>
                      <button mat-flat-button color="success" *ngIf="order.orderStatus === 'READY'" (click)="completeOrder(order)" style="background-color: #16a34a; color: white;">Complete</button>
                    </div>
                  </td>
                </ng-container>

                <tr mat-header-row *matHeaderRowDef="onlineDisplayedColumns"></tr>
                <tr mat-row *matRowDef="let row; columns: onlineDisplayedColumns;" class="order-row"></tr>
              </table>
              <mat-paginator #onlinePaginator [pageSizeOptions]="[10, 25, 50]"></mat-paginator>
            </div>

            <div class="kanban-view" *ngIf="onlineView === 'kanban'">
              <div class="kanban-col" *ngFor="let col of kanbanColumns">
                <div class="col-header">
                  <span class="dot" [class]="col.status.toLowerCase()"></span>
                  {{ col.label }}
                  <span class="badge">{{ columnOrders(col.status).length }}</span>
                </div>
                <div class="col-list">
                  <mat-card class="kanban-card" *ngFor="let order of columnOrders(col.status)">
                    <div class="k-card-header">
                      <span class="platform-mini" [class]="order.platform.toLowerCase()">#{{ order.platformOrderId }}</span>
                      <span class="total">{{ formatCurrencyValue(order.totalAmount) }}</span>
                    </div>
                    <div class="k-card-body">
                      <div class="cust">{{ order.customerName || 'Walk-in' }}</div>
                      <div class="time">{{ formatDateValue(order.createdAt) }}</div>
                      <div class="ready-info" *ngIf="order.readyAt">Ready since {{ formatDateValue(order.readyAt) }}</div>
                    </div>
                    <mat-card-actions align="end" *ngIf="['PENDING', 'ACCEPTED', 'READY'].includes(order.orderStatus)">
                      <button mat-button color="primary" *ngIf="order.orderStatus === 'PENDING'" (click)="acceptOrder(order)">Accept</button>
                      <button mat-button color="warn" *ngIf="order.orderStatus === 'PENDING'" (click)="startReject(order)">Reject</button>
                      <button mat-button color="accent" *ngIf="order.orderStatus === 'ACCEPTED'" (click)="markReady(order)">Ready</button>
                      <button mat-button color="success" *ngIf="order.orderStatus === 'READY'" (click)="completeOrder(order)">Complete</button>
                    </mat-card-actions>
                  </mat-card>
                </div>
              </div>
            </div>
          </div>
        </mat-tab>
      </mat-tab-group>
    </div>

    <!-- Refund Dialog -->
    <ng-template #refundDialog>
      <h2 mat-dialog-title>{{ bulkRefundMode ? 'Bulk Refund Recording' : 'Refund Recording' }}</h2>
      <mat-dialog-content>
        <div class="refund-summary">
          <p *ngIf="!bulkRefundMode">Order: <strong>{{ refundTarget?.orderCode }}</strong></p>
          <p *ngIf="bulkRefundMode">Total Selected: <strong>{{ selection.selected.length }} orders</strong></p>
          <p>Total Refundable: <strong>{{ formatCurrencyValue(bulkRefundMode ? bulkRefundTotal : (refundTarget?.totalAmount || 0)) }}</strong></p>
        </div>
        
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Refund Amount</mat-label>
          <input matInput type="number" [(ngModel)]="refundAmountInput" [max]="bulkRefundMode ? bulkRefundTotal : (refundTarget?.totalAmount || 0)" min="0.01">
          <span matPrefix>₹&nbsp;</span>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Refund Reason</mat-label>
          <textarea matInput [(ngModel)]="refundReasonInput" placeholder="Enter reason for tracking..."></textarea>
        </mat-form-field>
        <p class="dialog-hint">Note: This only records the refund in KhanaBook. Ensure money is returned via original method.</p>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button mat-dialog-close>Cancel</button>
        <button mat-flat-button color="warn" [disabled]="refunding || !refundAmountInput" (click)="confirmRefund()">
          {{ refunding ? 'Processing...' : 'Record Refund' }}
        </button>
      </mat-dialog-actions>
    </ng-template>

    <!-- Reject Dialog -->
    <ng-template #rejectDialog>
      <h2 mat-dialog-title>Reject Marketplace Order</h2>
      <mat-dialog-content>
        <p>Order #{{ rejectTarget?.platformOrderId }} from {{ rejectTarget?.platform }} will be rejected.</p>
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Rejection Reason</mat-label>
          <mat-select [(ngModel)]="rejectReasonInput">
            <mat-option value="OUT_OF_STOCK">Out of Stock</mat-option>
            <mat-option value="KITCHEN_CLOSED">Kitchen Closed</mat-option>
            <mat-option value="TOO_BUSY">Store too busy</mat-option>
            <mat-option value="OTHER">Other</mat-option>
          </mat-select>
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button mat-dialog-close>Cancel</button>
        <button mat-flat-button color="warn" [disabled]="rejecting" (click)="confirmReject()">
          {{ rejecting ? 'Rejecting...' : 'Confirm Reject' }}
        </button>
      </mat-dialog-actions>
    </ng-template>
  `,
  styles: [`
    .page-container { padding: 24px; max-width: 1400px; margin: 0 auto; }
    .header-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 28px; }
    .page-title { margin: 0; font-size: 2rem; font-weight: 800; color: var(--ink); letter-spacing: -0.5px; }
    .page-subtitle { margin: 4px 0 0; color: var(--muted); font-size: 0.95rem; }

    .main-tabs { margin-top: 16px; }
    .tab-content { padding-top: 24px; }

    .stats-row { display: flex; gap: 24px; margin-bottom: 32px; }
    .mini-stat { 
      flex: 1; 
      position: relative;
      border-radius: var(--radius-lg); 
      border: 1px solid var(--line); 
      background: var(--panel);
      backdrop-filter: blur(12px);
      box-shadow: var(--shadow-sm); 
      display: flex; 
      align-items: center; 
      padding: 12px 18px; 
      gap: 12px; 
      transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
      overflow: hidden;
    }
    .mini-stat:hover {
      transform: translateY(-2px);
      box-shadow: var(--shadow-md);
    }
    .mini-stat mat-icon { 
      background: var(--brand-soft); 
      color: var(--brand); 
      width: 36px; 
      height: 36px; 
      line-height: 36px; 
      text-align: center; 
      border-radius: var(--radius-md); 
      font-size: 18px;
      transition: all 0.3s ease;
    }
    .mini-stat:hover mat-icon {
      transform: scale(1.05) rotate(4deg);
    }
    .mini-stat .val { font-size: 1.25rem; font-weight: 800; color: var(--ink); line-height: 1; }
    .mini-stat .lab { font-size: 0.75rem; color: var(--muted); margin-top: 2px; font-weight: 600; }

    .filter-card { 
      margin-bottom: 24px; 
      border-radius: var(--radius-xl); 
      border: 1px solid var(--line); 
      background: var(--panel);
      backdrop-filter: blur(12px);
      box-shadow: var(--shadow-md); 
    }
    .filter-row { display: flex; align-items: center; gap: 16px; padding: 16px 20px !important; }
    .search-field { flex: 1; max-width: 360px; }
    .filter-field { width: 160px; }
    ::ng-deep .filter-row .mat-mdc-form-field-subscript-wrapper { display: none; }
    
    .selection-banner { padding: 16px 24px; background: rgba(239, 68, 68, 0.08); border: 1px solid rgba(239, 68, 68, 0.15); border-radius: var(--radius-lg); margin-top: 12px; display: flex; align-items: center; gap: 16px; animation: slideIn 0.2s ease; }
    .banner-text { font-weight: 700; color: #dc2626; }

    .table-container { 
      position: relative; 
      background: var(--panel); 
      border-radius: var(--radius-xl); 
      border: 1px solid var(--line);
      box-shadow: var(--shadow-md); 
      overflow: hidden; 
    }
    .loading-overlay { position: absolute; inset: 0; background: rgba(255,255,255,0.7); z-index: 10; display: flex; align-items: center; justify-content: center; }
    table { width: 100%; background: transparent; }

    ::ng-deep table th.mat-mdc-header-cell {
      background: var(--panel) !important;
      font-weight: 700 !important;
      color: var(--ink) !important;
      text-transform: uppercase !important;
      font-size: 0.75rem !important;
      letter-spacing: 0.5px !important;
      border-bottom: 2px solid var(--line) !important;
      padding: 16px !important;
    }
    ::ng-deep table td.mat-mdc-cell {
      padding: 16px !important;
      border-bottom: 1px solid var(--line) !important;
      color: var(--ink-secondary) !important;
      font-size: 0.9rem !important;
    }

    .order-row { cursor: default; transition: all 0.2s ease; background: transparent; }
    .order-row:hover { background: var(--panel-hover) !important; }

    .id-cell { display: flex; flex-direction: column; gap: 2px; }
    .id-cell .code { font-weight: 700; color: var(--ink); }
    .id-cell .source-tag { font-size: 0.65rem; font-weight: 800; text-transform: uppercase; background: var(--brand-soft); color: var(--brand); padding: 2px 6px; border-radius: 4px; width: fit-content; }

    .customer-cell { display: flex; flex-direction: column; }
    .customer-cell .name { font-weight: 700; color: var(--ink); }
    .customer-cell .contact { font-size: 0.75rem; color: var(--muted); margin-top: 2px; }

    .status-badge { padding: 4px 12px; border-radius: 999px; font-size: 0.72rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px; }
    .status-badge.success { background: rgba(34, 197, 94, 0.12); color: #16a34a; }
    .status-badge.warn { background: rgba(245, 158, 11, 0.12); color: #d97706; }
    .status-badge.danger { background: rgba(239, 68, 68, 0.12); color: #dc2626; }
    .status-badge.info { background: rgba(2, 132, 199, 0.12); color: #0284c7; }

    .payment-cell { display: flex; flex-direction: column; }
    .payment-cell .method { font-weight: 600; color: var(--ink); }
    .p-status { font-size: 0.75rem; color: var(--muted); margin-top: 2px; }
    .p-status.refunded { color: #dc2626; font-weight: 700; }

    .total-cell { display: flex; flex-direction: column; align-items: flex-end; }
    .total-cell .amount { font-weight: 700; font-size: 1rem; color: var(--ink); }
    .refunded-amount { color: #dc2626; font-size: 0.75rem; font-weight: 700; margin-top: 2px; }

    .online-counts-row { display: grid; grid-template-columns: repeat(5, 1fr); gap: 16px; margin-bottom: 24px; }
    .status-box { 
      padding: 12px 16px; 
      text-align: center; 
      border-radius: var(--radius-lg); 
      border: 1px solid var(--line); 
      background: var(--panel);
      backdrop-filter: blur(12px);
      box-shadow: var(--shadow-sm); 
      cursor: pointer; 
      transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
      overflow: hidden;
    }
    .status-box:hover { 
      transform: translateY(-2px); 
      box-shadow: var(--shadow-md); 
    }
    .status-box.active { background: var(--brand); border-color: var(--brand-light); }
    .status-box.active .count, .status-box.active .label { color: #fff !important; }
    
    .status-box .count { font-size: 1.4rem; font-weight: 800; line-height: 1; }
    .status-box .label { font-size: 0.7rem; font-weight: 800; text-transform: uppercase; margin-top: 4px; opacity: 0.8; letter-spacing: 0.5px; }
    
    .count.primary { color: #0284c7; }
    .count.success { color: #16a34a; }
    .count.warn { color: #d97706; }
    .count.info { color: #0d9488; }
    .count.danger { color: #dc2626; }

    .view-actions { display: flex; align-items: center; margin-bottom: 20px; gap: 16px; }
    .view-toggle { background: var(--bg); border: 1px solid var(--line); padding: 4px; border-radius: var(--radius-md); display: flex; }
    .view-toggle button { border-radius: var(--radius-sm); font-weight: 700; padding: 0 16px; color: var(--muted); }
    .view-toggle button.selected { background: var(--panel); box-shadow: var(--shadow-sm); color: var(--brand); border: 1px solid var(--line); }

    .platform-badge { padding: 4px 12px; border-radius: 6px; font-weight: 800; font-size: 0.7rem; text-transform: uppercase; }
    .platform-badge.swiggy { background: #fff7ed; color: #ea580c; border: 1px solid #fdba74; }
    .platform-badge.zomato { background: #fef2f2; color: #dc2626; border: 1px solid #fecaca; }

    .action-btn-row { display: flex; gap: 8px; }

    .kanban-view { display: grid; grid-template-columns: repeat(5, 1fr); gap: 16px; }
    .kanban-col { 
      background: var(--bg); 
      border: 1px solid var(--line);
      border-radius: var(--radius-xl); 
      padding: 12px; 
      min-height: 600px; 
      display: flex; 
      flex-direction: column; 
      gap: 12px; 
    }
    .col-header { 
      display: flex; 
      align-items: center; 
      gap: 8px; 
      font-weight: 800; 
      font-size: 0.8rem; 
      color: var(--ink-secondary); 
      text-transform: uppercase; 
      margin-bottom: 4px; 
      letter-spacing: 0.5px;
    }
    .col-header .dot { width: 8px; height: 8px; border-radius: 50%; background: #94a3b8; }
    .col-header .dot.pending { background: #d97706; }
    .col-header .dot.accepted { background: #0284c7; }
    .col-header .dot.ready { background: #0d9488; }
    .col-header .dot.completed { background: #16a34a; }
    .col-header .dot.rejected { background: #dc2626; }
    .col-header .badge { 
      margin-left: auto; 
      background: var(--brand-soft); 
      color: var(--brand); 
      padding: 2px 8px; 
      border-radius: 999px; 
      font-size: 0.75rem; 
      font-weight: 700;
    }

    .kanban-card { 
      border: 1px solid var(--line) !important; 
      background: var(--panel) !important;
      border-radius: var(--radius-lg) !important;
      box-shadow: var(--shadow-sm); 
      transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
      padding: 16px;
    }
    .kanban-card:hover {
      transform: translateY(-4px) scale(1.02);
      box-shadow: var(--shadow-md);
      border-color: var(--brand-soft) !important;
    }
    .k-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
    .platform-mini { font-size: 0.65rem; font-weight: 800; padding: 2px 6px; border-radius: 4px; border: 1px solid transparent; }
    .platform-mini.swiggy { background: #fff7ed; color: #ea580c; border-color: #fdba74; }
    .platform-mini.zomato { background: #fef2f2; color: #dc2626; border-color: #fecaca; }
    .k-card-header .total { font-weight: 800; color: var(--ink); }
    .k-card-body .cust { font-weight: 700; margin-bottom: 4px; color: var(--ink); }
    .k-card-body .time { font-size: 0.7rem; color: var(--muted); }
    .ready-info { margin-top: 8px; color: #10b981; font-weight: 700; font-size: 0.75rem; }

    .full-width { width: 100%; margin-bottom: 16px; }
    .refund-summary { padding: 16px; background: var(--bg); border: 1px solid var(--line); border-radius: var(--radius-md); margin-bottom: 24px; }
    .refund-summary p { margin: 4px 0; color: var(--ink); }
    .dialog-hint { font-size: 0.75rem; color: var(--muted); font-style: italic; margin-top: 8px; }

    @keyframes slideIn { from { opacity: 0; transform: translateY(-10px); } to { opacity: 1; transform: translateY(0); } }
    .spacer { flex: 1; }

    @media (max-width: 1200px) {
      .kanban-view { grid-template-columns: repeat(3, 1fr); }
      .online-counts-row { grid-template-columns: repeat(3, 1fr); }
    }
    @media (max-width: 900px) {
      .kanban-view { grid-template-columns: repeat(2, 1fr); }
      .online-counts-row { grid-template-columns: repeat(2, 1fr); }
    }
    @media (max-width: 768px) {
      .header-row { flex-direction: column; gap: 16px; }
      .filter-row { flex-direction: column; align-items: stretch; }
      .search-field { max-width: none; }
      .kanban-view { grid-template-columns: 1fr; }
      .stats-row { flex-direction: column; }
    }
  `]
})
export class OrdersPageComponent implements AfterViewInit {
  private readonly api = inject(BusinessApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  @ViewChild('posPaginator') posPaginator!: MatPaginator;
  @ViewChild('posSort') posSort!: MatSort;
  @ViewChild('onlinePaginator') onlinePaginator!: MatPaginator;
  @ViewChild('onlineSort') onlineSort!: MatSort;
  @ViewChild('refundDialog') refundDialogTemplate!: TemplateRef<any>;
  @ViewChild('rejectDialog') rejectDialogTemplate!: TemplateRef<any>;

  activeTab: OrderTab = 'pos';
  onlineView: OnlineView = 'kanban';

  // POS orders
  orders: BusinessOrder[] = [];
  ordersLoaded = false;
  posDataSource = new MatTableDataSource<BusinessOrder>([]);
  posDisplayedColumns = ['select', 'orderCode', 'customer', 'status', 'payment', 'total', 'createdAt', 'actions'];

  selection = new SelectionModel<BusinessOrder>(true, []);

  orderStatusFilter = 'ALL';
  orderSourceFilter = 'ALL';

  // Online orders
  marketplaceOrders: MarketplaceOrder[] = [];
  marketplaceOrdersLoaded = false;
  onlineCounts: { pending: number; accepted: number; ready: number; completed: number; rejected: number } | null = null;
  onlineDataSource = new MatTableDataSource<MarketplaceOrder>([]);
  onlineDisplayedColumns = ['platform', 'platformOrderId', 'customer', 'totalAmount', 'orderStatus', 'createdAt', 'actions'];

  onlineStatusFilter: string | null = null;

  // Refund/Reject
  refundTarget: BusinessOrder | null = null;
  bulkRefundMode = false;
  bulkRefundTotal = 0;
  refundAmountInput: number | null = null;
  refundReasonInput = '';
  refunding = false;

  rejectTarget: MarketplaceOrder | null = null;
  rejectReasonInput = '';
  rejecting = false;

  exportingPdf = false;

  readonly kanbanColumns = [
    { label: 'Pending', status: 'PENDING' },
    { label: 'Accepted', status: 'ACCEPTED' },
    { label: 'Ready', status: 'READY' },
    { label: 'Completed', status: 'COMPLETED' },
    { label: 'Rejected', status: 'REJECTED' }
  ];

  constructor() {
    this.loadOrders();
    this.loadMarketplaceCounts();
  }

  ngAfterViewInit() {
    this.posDataSource.paginator = this.posPaginator;
    this.posDataSource.sort = this.posSort;
    this.onlineDataSource.paginator = this.onlinePaginator;
    this.onlineDataSource.sort = this.onlineSort;

    this.posDataSource.filterPredicate = (data, filter) => {
      const search = filter.toLowerCase();
      const matchesSearch = !search || [
        data.orderCode,
        data.customerName || '',
        data.customerContact || '',
        data.paymentMethod,
        data.paymentStatus
      ].some(v => v.toLowerCase().includes(search));

      const matchesStatus = this.orderStatusFilter === 'ALL' || data.orderStatus === this.orderStatusFilter;
      const matchesSource = this.orderSourceFilter === 'ALL' || data.sourceType === this.orderSourceFilter;

      return matchesSearch && matchesStatus && matchesSource;
    };
  }

  loadAllData() {
    this.loadOrders();
    this.loadMarketplaceOrders();
    this.loadMarketplaceCounts();
  }

  onTabChange(event: any) {
    this.activeTab = event.index === 0 ? 'pos' : 'online';
    if (this.activeTab === 'online') {
      this.loadMarketplaceOrders();
      this.loadMarketplaceCounts();
    }
  }

  // POS Methods
  loadOrders(): void {
    this.ordersLoaded = false;
    this.api.getOrders().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => {
        this.orders = data;
        this.posDataSource.data = data;
        this.ordersLoaded = true;
      },
      error: () => { this.ordersLoaded = true; }
    });
  }

  applyPosFilter(event: Event) {
    const filterValue = (event.target as HTMLInputElement).value;
    this.posDataSource.filter = filterValue.trim().toLowerCase();
  }

  applyPosFilters() {
    const current = this.posDataSource.filter;
    this.posDataSource.filter = '';
    this.posDataSource.filter = current;
  }

  get businessOrderStatuses(): string[] {
    return [...new Set(this.orders.map(o => o.orderStatus))].sort();
  }

  totalRevenue = () => this.orders.reduce((sum, o) => sum + (o.totalAmount || 0), 0);

  // Selection
  isAllSelected() {
    const numSelected = this.selection.selected.length;
    const numRows = this.posDataSource.filteredData.length;
    return numSelected === numRows;
  }

  masterToggle() {
    this.isAllSelected() ?
        this.selection.clear() :
        this.posDataSource.filteredData.forEach(row => this.selection.select(row));
  }

  clearSelection() {
    this.selection.clear();
  }

  openBulkRefund() {
    const selected = this.selection.selected.filter(o => o.manualRefundAllowed);
    if (selected.length === 0) return;

    this.bulkRefundMode = true;
    this.bulkRefundTotal = selected.reduce((sum, o) => sum + (o.totalAmount || 0), 0);
    this.refundAmountInput = this.bulkRefundTotal;
    this.refundReasonInput = 'Bulk refund processed';
    this.dialog.open(this.refundDialogTemplate, { width: '450px' });
  }

  openManualRefund(order: BusinessOrder) {
    this.bulkRefundMode = false;
    this.refundTarget = order;
    this.refundAmountInput = order.totalAmount;
    this.refundReasonInput = '';
    this.dialog.open(this.refundDialogTemplate, { width: '450px' });
  }

  confirmRefund() {
    if (this.bulkRefundMode) {
      this.processBulkRefund();
      return;
    }
    if (!this.refundTarget || !this.refundAmountInput) return;

    this.refunding = true;
    this.api.manualRefundOrder(this.refundTarget.orderId, {
      refundAmount: this.refundAmountInput,
      reason: this.refundReasonInput || 'Refund handled manually'
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.refunding = false;
        this.dialog.closeAll();
        this.loadOrders();
        this.snackBar.open('Refund recorded successfully', 'Close', { duration: 3000 });
      },
      error: (err) => {
        this.refunding = false;
        this.snackBar.open(err?.error?.error || 'Failed to record refund', 'Close');
      }
    });
  }

  processBulkRefund() {
    const selected = this.selection.selected.filter(o => o.manualRefundAllowed);
    this.refunding = true;
    
    let count = 0;
    selected.forEach(o => {
      this.api.manualRefundOrder(o.orderId, {
        refundAmount: o.totalAmount,
        reason: this.refundReasonInput || 'Bulk refund'
      }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => {
          count++;
          if (count === selected.length) this.finalizeBulkRefund(count);
        },
        error: () => {
          count++;
          if (count === selected.length) this.finalizeBulkRefund(count);
        }
      });
    });
  }

  private finalizeBulkRefund(count: number) {
    this.refunding = false;
    this.dialog.closeAll();
    this.selection.clear();
    this.loadOrders();
    this.snackBar.open(`Processed ${count} refunds`, 'Close', { duration: 3000 });
  }

  // Online Methods
  loadMarketplaceOrders(): void {
    this.marketplaceOrdersLoaded = false;
    this.api.getMarketplaceOrders().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => {
        this.marketplaceOrders = data;
        this.onlineDataSource.data = data;
        this.marketplaceOrdersLoaded = true;
      },
      error: () => {
        this.marketplaceOrders = [];
        this.onlineDataSource.data = [];
        this.marketplaceOrdersLoaded = true;
      }
    });
  }

  loadMarketplaceCounts(): void {
    this.api.getMarketplaceOrderCounts().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => { this.onlineCounts = data; },
      error: () => { this.onlineCounts = null; }
    });
  }

  filterOnlineStatus(status: string) {
    this.onlineStatusFilter = this.onlineStatusFilter === status ? null : status;
    this.onlineDataSource.filter = this.onlineStatusFilter || '';
  }

  columnOrders(status: string): MarketplaceOrder[] {
    return this.marketplaceOrders.filter(o => o.orderStatus === status);
  }

  acceptOrder(order: MarketplaceOrder): void {
    this.api.acceptMarketplaceOrder(order.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.loadMarketplaceOrders(); this.loadMarketplaceCounts(); }
    });
  }

  startReject(order: MarketplaceOrder): void {
    this.rejectTarget = order;
    this.rejectReasonInput = 'OUT_OF_STOCK';
    this.dialog.open(this.rejectDialogTemplate, { width: '400px' });
  }

  confirmReject(): void {
    if (!this.rejectTarget) return;
    this.rejecting = true;
    this.api.rejectMarketplaceOrder(this.rejectTarget.id, this.rejectReasonInput).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.rejecting = false;
        this.dialog.closeAll();
        this.loadMarketplaceOrders();
        this.loadMarketplaceCounts();
      },
      error: () => { this.rejecting = false; }
    });
  }

  markReady(order: MarketplaceOrder): void {
    this.api.markMarketplaceOrderReady(order.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.loadMarketplaceOrders(); this.loadMarketplaceCounts(); }
    });
  }

  completeOrder(order: MarketplaceOrder): void {
    this.api.completeMarketplaceOrder(order.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.loadMarketplaceOrders(); this.loadMarketplaceCounts(); }
    });
  }

  // Formatters
  formatCurrencyValue(v: number): string { return formatCurrency(v); }
  formatDateValue(v: number | null): string { return formatDate(v); }

  getPosStatusClass(status: string) {
    const s = status.toLowerCase();
    if (s === 'completed') return 'success';
    if (s === 'cancelled') return 'danger';
    if (s === 'draft') return 'warn';
    return 'info';
  }

  getOnlineStatusClass(status: string) {
    switch(status) {
      case 'PENDING': return 'warn';
      case 'ACCEPTED': return 'info';
      case 'READY': return 'info';
      case 'COMPLETED': return 'success';
      case 'REJECTED': return 'danger';
      default: return '';
    }
  }

  // Export
  async exportPdf() {
    this.exportingPdf = true;
    try {
      const { default: jsPDF } = await import('jspdf');
      const { default: autoTable } = await import('jspdf-autotable');
      const doc = new jsPDF('l', 'mm', 'a4');
      const data = this.posDataSource.filteredData.map(o => [
        o.orderCode, o.customerName || '-', o.orderStatus, o.paymentStatus, formatCurrency(o.totalAmount), formatDate(o.createdAt)
      ]);
      autoTable(doc, {
        head: [['Order', 'Customer', 'Status', 'Payment', 'Total', 'Date']],
        body: data,
        headStyles: { fillColor: [199, 115, 47] }
      });
      doc.save(`store-orders-${new Date().toISOString().slice(0, 10)}.pdf`);
    } finally { this.exportingPdf = false; }
  }

  async exportMarketplacePdf() {
    this.exportingPdf = true;
    try {
      const { default: jsPDF } = await import('jspdf');
      const { default: autoTable } = await import('jspdf-autotable');
      const doc = new jsPDF('l', 'mm', 'a4');
      const data = this.marketplaceOrders.map(o => [
        o.platform, o.platformOrderId, o.customerName || '-', formatCurrency(o.totalAmount), o.orderStatus, formatDate(o.createdAt)
      ]);
      autoTable(doc, {
        head: [['Platform', 'Order ID', 'Customer', 'Amount', 'Status', 'Date']],
        body: data,
        headStyles: { fillColor: [199, 115, 47] }
      });
      doc.save(`marketplace-orders-${new Date().toISOString().slice(0, 10)}.pdf`);
    } finally { this.exportingPdf = false; }
  }
}
