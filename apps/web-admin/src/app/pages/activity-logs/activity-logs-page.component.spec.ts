import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { ActivityLogsPageComponent } from './activity-logs-page.component';
import { AdminApiService } from '../../core/services/admin-api.service';
import { AdminActivityLogItem } from '../../core/models/api.models';

function activityLog(id: number, action: string): AdminActivityLogItem {
  return {
    id,
    action,
    outcome: 'success',
    userId: `user-${id}`,
    shopName: 'Test Kitchen',
    createdAt: Date.UTC(2026, 10, 21, 10, 0, 0),
  };
}

/**
 * The page used to stand four hardcoded events and invented KPI totals in for an
 * empty or failed response, so a broken request looked like a live audit trail.
 * These specs pin the honest behaviour: nothing is shown that the server did not
 * return, and a failure says so.
 */
describe('ActivityLogsPageComponent — real rows only', () => {
  let fixture: ComponentFixture<ActivityLogsPageComponent>;
  let component: ActivityLogsPageComponent;
  let adminApi: jasmine.SpyObj<AdminApiService>;

  async function mount(response: Observable<AdminActivityLogItem[]>) {
    adminApi = jasmine.createSpyObj('AdminApiService', ['getActivityLogs']);
    adminApi.getActivityLogs.and.returnValue(response);

    await TestBed.configureTestingModule({
      imports: [ActivityLogsPageComponent],
      providers: [
        provideRouter([]),
        { provide: AdminApiService, useValue: adminApi },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ActivityLogsPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('shows no rows and a zero total while the request is still in flight', async () => {
    await mount(new Subject<AdminActivityLogItem[]>());

    expect(component.loading()).toBeTrue();
    expect(component.displayLogs()).toEqual([]);
    expect(component.kpis()[0].value).toBe('0');
  });

  it('shows exactly the rows the server returned', async () => {
    await mount(of([activityLog(1, 'AUTH_LOGIN'), activityLog(2, 'TERMINAL_SYNC')]));

    expect(component.displayLogs().map((row) => row.id)).toEqual([1, 2]);
    expect(component.kpis()[0].value).toBe('2');
    expect(component.error()).toBeNull();
    expect(component.loading()).toBeFalse();
  });

  it('reports a failed request instead of substituting placeholder events', async () => {
    await mount(throwError(() => ({ error: { message: 'Forbidden' } })));

    expect(component.error()).toBe('Forbidden');
    expect(component.displayLogs()).toEqual([]);
    expect(component.kpis()[0].value).toBe('0');
    expect(component.loading()).toBeFalse();
  });
});
