import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { ApiStateComponent } from './api-state.component';

@Component({
  standalone: true,
  imports: [ApiStateComponent],
  template: `
    <app-api-state
      [loading]="loading"
      [error]="error"
      [message]="message"
      [title]="title"
      [meta]="meta"
      (retry)="onRetry()"
    >
      <div id="projected-content">Real content</div>
    </app-api-state>
  `,
})
class TestHostComponent {
  loading = false;
  error: string | null = null;
  message: string | null = null;
  title = "We couldn't load this content";
  meta: string | null = null;
  retried = false;

  onRetry(): void {
    this.retried = true;
  }
}

describe('ApiStateComponent', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let host: TestHostComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent, ApiStateComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders projected content when neither loading nor error is set', () => {
    const el = fixture.nativeElement.querySelector('#projected-content');
    expect(el).toBeTruthy();
    expect(el.textContent).toContain('Real content');
  });

  it('renders spinner and aria-busy when loading is true', () => {
    host.loading = true;
    fixture.detectChanges();

    const loadingEl = fixture.nativeElement.querySelector('.api-state-loading');
    expect(loadingEl).toBeTruthy();
    expect(loadingEl.getAttribute('aria-busy')).toBe('true');
    expect(fixture.nativeElement.querySelector('#projected-content')).toBeNull();
  });

  it('renders state-panel with role="alert" when error is set', () => {
    host.error = 'Unable to connect to service.';
    fixture.detectChanges();

    const alertEl = fixture.nativeElement.querySelector('.state-panel');
    expect(alertEl).toBeTruthy();
    expect(alertEl.getAttribute('role')).toBe('alert');
    expect(alertEl.textContent).toContain('Unable to connect to service.');
    expect(fixture.nativeElement.querySelector('#projected-content')).toBeNull();
  });

  it('accepts message input as an alias for error', () => {
    host.error = null;
    host.message = 'Network error occurred';
    fixture.detectChanges();

    const alertEl = fixture.nativeElement.querySelector('.state-panel');
    expect(alertEl).toBeTruthy();
    expect(alertEl.textContent).toContain('Network error occurred');
  });

  it('detects offline styling when error mentions offline or network', () => {
    host.error = 'No internet connection. You are offline.';
    fixture.detectChanges();

    const alertEl = fixture.nativeElement.querySelector('.state-panel');
    expect(alertEl.classList).toContain('state-panel--offline');
  });

  it('emits retry when the action button is clicked', () => {
    host.error = 'Something went wrong';
    fixture.detectChanges();

    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-retry');
    expect(btn).toBeTruthy();
    btn.click();
    expect(host.retried).toBeTrue();
  });
});
