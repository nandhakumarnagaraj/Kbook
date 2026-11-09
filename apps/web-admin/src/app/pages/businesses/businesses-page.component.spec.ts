import { TestBed, ComponentFixture } from '@angular/core/testing';
import { of } from 'rxjs';
import { BusinessesPageComponent } from './businesses-page.component';
import { AdminApiService } from '../../core/services/admin-api.service';
import { AdminBusinessListItem, AdminDashboardSummary } from '../../core/models/api.models';

const MOCK_BUSINESSES: AdminBusinessListItem[] = [
  {
    restaurantId: '6266448607388417708',
    shopName: 'Riyan Biryani restaurant',
    ownerName: 'Riyan Biryani Restaurant',
    ownerLoginId: '9008311511',
    whatsappNumber: '9008311511',
    email: '',
    websiteEnabled: false,
    isSuspended: false,
    staffCount: 1,
    menuCount: 3,
    orderCount: 2,
    updatedAt: 1794053117857,
    createdAt: 1794053060208,
    lastUsedAt: 1794053351791,
  },
  {
    restaurantId: '3382218869164782987',
    shopName: "Piquant Services's Restaurant",
    ownerName: 'Piquant Services',
    ownerLoginId: 'piquantitservices@gmail.com',
    whatsappNumber: '',
    email: 'piquantitservices@gmail.com',
    websiteEnabled: false,
    isSuspended: false,
    staffCount: 1,
    menuCount: 2,
    orderCount: 67,
    updatedAt: 1794047527397,
    createdAt: 1790857693026,
    lastUsedAt: 1794047527397,
  },
  {
    restaurantId: '7543726995687322883',
    shopName: "Kitchen BiryaniWale's Restaurant",
    ownerName: 'Kitchen BiryaniWale',
    ownerLoginId: 'kitchenbiryani@gmail.com',
    whatsappNumber: '',
    email: 'kitchenbiryani@gmail.com',
    websiteEnabled: false,
    isSuspended: true,
    staffCount: 1,
    menuCount: 1,
    orderCount: 0,
    updatedAt: 1794047527397,
    createdAt: 1790857693026,
    lastUsedAt: null,
  },
];

const MOCK_SUMMARY: AdminDashboardSummary = {
  totalBusinesses: 3,
  liveBusinesses: 0,
  totalStaff: 3,
  totalOrders: 69,
  totalRevenue: 37651.5,
  refundedOrders: 0,
  refundedAmount: 0,
};

describe('BusinessesPageComponent — real live platform directory', () => {
  let fixture: ComponentFixture<BusinessesPageComponent>;
  let component: BusinessesPageComponent;
  let adminApiSpy: jasmine.SpyObj<AdminApiService>;

  beforeEach(async () => {
    adminApiSpy = jasmine.createSpyObj('AdminApiService', [
      'getBusinesses',
      'getDashboardSummary',
      'getBusinessDetail',
      'suspendBusiness',
      'activateBusiness',
    ]);
    adminApiSpy.getBusinesses.and.returnValue(of(MOCK_BUSINESSES));
    adminApiSpy.getDashboardSummary.and.returnValue(of(MOCK_SUMMARY));
    adminApiSpy.getBusinessDetail.and.returnValue(of({
      ...MOCK_BUSINESSES[0],
      ownerWhatsappNumber: '9008311511',
      shopAddress: 'Sample Address',
      currency: 'INR',
      timezone: 'Asia/Kolkata',
      gstEnabled: false,
      gstin: '',
      fssaiNumber: '',
      posOrderCount: 2,
      totalRevenue: 1030,
    }));

    await TestBed.configureTestingModule({
      imports: [BusinessesPageComponent],
      providers: [{ provide: AdminApiService, useValue: adminApiSpy }],
    }).compileComponents();

    fixture = TestBed.createComponent(BusinessesPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders five live KPI tiles without a duplicated header badge', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('.kb-kpi').length).toBe(5);
    // The header chip used to restate the outlet count that the KPI row already
    // shows, and it forced the search onto a third header row.
    expect(el.querySelector('.chip--live')).toBeNull();
    expect(el.textContent).not.toContain('Live Platform Data');
    expect(component.businesses().length).toBe(3);
  });

  it('matches tab counts to the real outlet rows', () => {
    expect(component.tabCount('All Outlets')).toBe(3);
    expect(component.tabCount('Active')).toBe(2);
    expect(component.tabCount('Suspended')).toBe(1);
    expect(component.tabCount('With Orders')).toBe(2);
  });

  it('filters rows by search and status', () => {
    component.search = 'Riyan';
    expect(component.filteredRows().map((b) => b.shopName)).toEqual(['Riyan Biryani restaurant']);

    component.search = '';
    component.statusFilter = 'Suspended';
    expect(component.filteredRows().every((b) => b.isSuspended)).toBeTrue();
    expect(component.filteredRows().length).toBe(1);
  });

  it('clears every filter on reset', () => {
    component.search = 'Riyan';
    component.statusFilter = 'Suspended';
    component.reset();
    expect(component.filteredRows().length).toBe(3);
  });

  it('selects a business into the detail rail and closes it', () => {
    const first = component.pagedRows()[0];
    component.selectBusiness(first);
    expect(component.selected()?.restaurantId).toBe(first.restaurantId);
    component.selected.set(null);
    expect(component.selected()).toBeNull();
  });
});
