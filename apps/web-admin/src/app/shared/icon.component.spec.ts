import { TestBed } from '@angular/core/testing';
import { IconComponent } from './icon.component';

const renderedMarkup = (comp: IconComponent) => (comp.safeHtml as string | null);

describe('IconComponent', () => {
  function render(name?: string) {
    const fixture = TestBed.createComponent(IconComponent);
    const comp = fixture.componentInstance;
    // setInput goes through Angular's input pipeline (fires ngOnChanges) the
    // same way a template binding does — plain property assignment does not.
    if (name) fixture.componentRef.setInput('name', name);
    fixture.detectChanges();
    return { fixture, comp };
  }

  it('renders the SVG for a known icon name', () => {
    const { comp } = render('dashboard');
    const svg = renderedMarkup(comp);
    expect(svg).toBeTruthy();
    const built = document.createElement('div');
    built.innerHTML = svg ?? '';
    expect(built.querySelector('rect')).not.toBeNull();
  });

  it('renders nothing for an unknown icon name', () => {
    const { comp } = render('does-not-exist');
    expect(renderedMarkup(comp)).toBeNull();
  });

  it('renders nothing before the name input is assigned (regression: constructor-time lookup)', () => {
    const { fixture, comp } = render();
    expect(renderedMarkup(comp)).toBeNull();
    // Assigning the name later (as Angular does when binding an @Input) must still render
    fixture.componentRef.setInput('name', 'orders');
    fixture.detectChanges();
    const svgAfter = renderedMarkup(comp);
    expect(svgAfter).toBeTruthy();
    const built = document.createElement('div');
    built.innerHTML = svgAfter ?? '';
    expect(built.querySelector('path')).not.toBeNull();
  });

  it('resolves every icon name mapped by the nav links in both roles', () => {
    /* These names come from SidebarLayoutComponent.links(); the OWNER set plus
       the KBOOK_ADMIN set. Adding a nav link with a missing icon is a silent
       visual bug, so protect the full mapping here. */
    const required = [
      'dashboard', 'orders', 'menu', 'staff', 'inventory',
      'terminals', 'payments', 'reports', 'settings', 'business', 'flag',
    ];
    const { fixture, comp } = render();
    for (const name of required) {
      fixture.componentRef.setInput('name', name);
      fixture.detectChanges();
      const rendered = renderedMarkup(comp);
      expect(rendered).withContext(`icon "${name}" must resolve to markup`).toBeTruthy();
    }
  });
});
