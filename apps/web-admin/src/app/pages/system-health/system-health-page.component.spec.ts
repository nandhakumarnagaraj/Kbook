import { TestBed, ComponentFixture } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { SystemHealthPageComponent } from './system-health-page.component';
import { AdminApiService } from '../../core/services/admin-api.service';

const BACKUP = {
  totalSnapshots: 5,
  latestBackup: '2026-11-08',
  databaseSize: '25 MB',
  retentionPolicy: '7 days',
  backups: [],
};

describe('SystemHealthPageComponent — measured signals only', () => {
  let fixture: ComponentFixture<SystemHealthPageComponent>;
  let component: SystemHealthPageComponent;

  function setup(hooks: unknown, backups: unknown, hooksDown = false, backupsDown = false): void {
    const api = jasmine.createSpyObj<AdminApiService>('AdminApiService', ['getWebhookHealth', 'getBackupStatus']);
    // The component only reads these as nullable payloads, so the spec feeds
    // either a fixture or an error without mirroring the API interfaces.
    api.getWebhookHealth.and.returnValue((hooksDown ? throwError(() => new Error('down')) : of(hooks)) as never);
    api.getBackupStatus.and.returnValue((backupsDown ? throwError(() => new Error('down')) : of(backups)) as never);

    TestBed.configureTestingModule({
      imports: [SystemHealthPageComponent],
      providers: [{ provide: AdminApiService, useValue: api }],
    });
    fixture = TestBed.createComponent(SystemHealthPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('renders the figures the endpoints actually returned', () => {
    // The real /admin/webhooks/health payload: retry counts, no status field.
    setup({ deadLetterCount: 0, pendingRetries: 1, totalJobs: 12 }, BACKUP);
    expect(component.kpis().map((kpi) => kpi.value)).toEqual(['Operational', 5, '08 Nov 2026', '25 MB', '7 days']);
    expect(component.banner().title).toBe('Measured services operational');
    expect(component.services().length).toBe(3);
    expect(component.services().every((row) => row.state === 'operational')).toBeTrue();
  });

  it('reports degradation from a dead-letter backlog', () => {
    setup({ deadLetterCount: 3, pendingRetries: 0, totalJobs: 12 }, BACKUP);
    expect(component.banner().title).toBe('Service degradation reported');
    expect(component.kpis()[0].value).toBe('Degraded');
  });

  it('does not call webhooks healthy when only backups answered', () => {
    setup(null, BACKUP, true, false);
    expect(component.banner().title).toBe('Webhook health unavailable');
    expect(component.kpis()[0].value).toBe('Unknown');
    expect(component.kpis()[1].value).toBe(5);
  });

  it('admits when neither endpoint answers', () => {
    setup(null, null, true, true);
    expect(component.banner().title).toBe('Health data unavailable');
    expect(component.kpis().map((kpi) => kpi.value)).toEqual(['Unknown', '—', '—', '—', '—']);
    expect(component.services().every((row) => row.state === 'unknown')).toBeTrue();
  });

  it('does not reformat a backup value it cannot parse', () => {
    setup({ status: 'ok' }, { ...BACKUP, latestBackup: 'nightly' });
    expect(component.kpis()[2].value).toBe('nightly');
  });
});
