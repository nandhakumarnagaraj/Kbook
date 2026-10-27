import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { HelpSupportPageComponent } from './help-support-page.component';

/**
 * The page exists to route people to the screen that fixes their problem, so the
 * thing worth pinning is that every link still points at a real destination and
 * that nothing on it needs the API — help has to work when the backend does not.
 */
describe('HelpSupportPageComponent', () => {
  let fixture: ComponentFixture<HelpSupportPageComponent>;
  let host: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HelpSupportPageComponent],
      providers: [provideRouter([])],
    });
    fixture = TestBed.createComponent(HelpSupportPageComponent);
    fixture.detectChanges();
    host = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => fixture.destroy());

  function hrefs(): string[] {
    return Array.from(host.querySelectorAll('a[href]')).map((anchor) => anchor.getAttribute('href') ?? '');
  }

  it('names the page in the shared header', () => {
    expect(host.querySelector('.kb-page-header__title')?.textContent?.trim()).toBe('Help & Support');
  });

  it('sends the three start-here cards to the pages that do the work', () => {
    const cards = Array.from(host.querySelectorAll('.help-card')).map((card) => card.getAttribute('href'));
    expect(cards).toEqual(['/business/menu', '/business/terminals', '/business/daily-closing']);
  });

  it('opens every in-app question link on a destination the sidebar also lists', () => {
    const inApp = hrefs().filter((href) => href.startsWith('/business/'));
    expect(inApp.length).withContext('the FAQ has to point somewhere').toBeGreaterThan(0);
    const allowed = new Set([
      '/business/menu',
      '/business/terminals',
      '/business/daily-closing',
      '/business/orders',
      '/business/settings',
    ]);
    for (const href of inApp) {
      expect(allowed.has(href)).withContext(`${href} is not a business route`).toBeTrue();
    }
  });

  it('keeps the FAQ collapsed until asked, using native disclosure', () => {
    const questions = host.querySelectorAll('details.help-faq');
    expect(questions.length).toBeGreaterThanOrEqual(4);
    for (const question of Array.from(questions)) {
      expect(question.hasAttribute('open')).withContext('a wall of open answers is not help').toBeFalse();
      expect(question.querySelector('summary')).withContext('a disclosure needs a label').toBeTruthy();
    }
  });

  it('hands the last word to support in a new tab, with the opener severed', () => {
    const contact = host.querySelector('.help-contact-link') as HTMLAnchorElement;
    expect(contact).withContext('the contact action must exist').toBeTruthy();
    expect(contact.getAttribute('href')).toBe('https://khanabook.in/contact-us');
    expect(contact.getAttribute('target')).toBe('_blank');
    expect(contact.getAttribute('rel')).withContext('noopener stops the new tab reaching back').toContain('noopener');
  });

  it('filters FAQ items reactively by search query and category chips', () => {
    const component = fixture.componentInstance;
    const searchInput = host.querySelector('.faq-search__input') as HTMLInputElement;
    expect(searchInput).toBeTruthy();

    // Type "gst" into search input
    searchInput.value = 'gst';
    searchInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const questions = Array.from(host.querySelectorAll('details.help-faq'));
    const visible = questions.filter((q) => !q.hasAttribute('hidden'));
    expect(visible.length).toBe(1);
    expect(visible[0].textContent).toContain('GST');

    // Clear search
    const clearBtn = host.querySelector('.faq-search__clear') as HTMLButtonElement;
    expect(clearBtn).toBeTruthy();
    clearBtn.click();
    fixture.detectChanges();

    expect(questions.filter((q) => !q.hasAttribute('hidden')).length).toBe(6);

    // Filter by topic category
    component.selectCategory('hardware');
    fixture.detectChanges();

    const hardwareVisible = questions.filter((q) => !q.hasAttribute('hidden'));
    expect(hardwareVisible.length).toBe(1);
    expect(hardwareVisible[0].textContent).toContain('terminal');
  });
});
