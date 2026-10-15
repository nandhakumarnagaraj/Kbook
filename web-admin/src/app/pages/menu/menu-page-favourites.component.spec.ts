import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { MenuPageComponent } from './menu-page.component';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { BusinessCategory, BusinessMenuItem } from '../../core/models/api.models';

/**
 * The favourites feature on the web admin: a star per dish, and one view that collects what has
 * been starred instead of making the owner scroll the whole catalog looking for stars.
 *
 * Three things these specs pin, because each one is a way the feature reads as broken rather
 * than as a bug:
 *   - the star writes through the dish's own sub-resource and moves only that dish;
 *   - a refused write leaves the dish exactly as it was (an optimistic star that the next
 *     reload reverses is how the owner stops trusting the star at all);
 *   - the favourites view actually filters, and still shows pinned categories - a view that
 *     shows everything is not a wishlist.
 */
const authStub = {
  session: () => ({ role: 'OWNER', restaurantId: 1, loginId: 'owner', restaurantName: 'Test Kitchen' }),
} as unknown as AuthService;

const routerStub = { navigate: () => Promise.resolve(true), events: of(), createUrlTree: () => null, serializeUrl: () => '' };

function dish(id: number, name: string, categoryId: number, favourite = false): BusinessMenuItem {
  return {
    menuItemId: id,
    categoryId,
    categoryName: categoryId === 1 ? 'Hot Drinks' : 'Beverages',
    name,
    description: `${name} description`,
    foodType: 'veg',
    basePrice: 40,
    available: true,
    stockStatus: 'in_stock',
    variantCount: 0,
    updatedAt: 1,
    favourite,
  };
}

describe('MenuPageComponent — favourites', () => {
  let api: jasmine.SpyObj<BusinessApiService>;
  let toast: jasmine.SpyObj<ToastService>;

  function mount(dishes: BusinessMenuItem[], categories: BusinessCategory[]): ComponentFixture<MenuPageComponent> {
    api = jasmine.createSpyObj<BusinessApiService>('BusinessApiService', [
      'getMenu',
      'getMenuCategories',
      'setMenuItemFavourite',
      'setCategoryFavourite',
    ]);
    api.getMenu.and.returnValue(of(dishes));
    api.getMenuCategories.and.returnValue(of(categories));
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['show']);

    TestBed.configureTestingModule({
      imports: [MenuPageComponent],
      providers: [
        provideHttpClient(),
        { provide: BusinessApiService, useValue: api },
        { provide: AuthService, useValue: authStub },
        { provide: ToastService, useValue: toast },
        { provide: Router, useValue: routerStub },
      ],
    });

    const fixture = TestBed.createComponent(MenuPageComponent);
    fixture.detectChanges();
    return fixture;
  }

  /** The rendered star of the row whose dish name matches, as `loadMenu` renders it. */
  function starFor(fixture: ComponentFixture<MenuPageComponent>, name: string): HTMLButtonElement {
    const rows = Array.from(fixture.nativeElement.querySelectorAll('.menu-table tbody tr')) as HTMLElement[];
    const row = rows.find((r) => r.textContent?.includes(name));
    expect(row).withContext(`${name} must render a row`).toBeTruthy();
    const star = row!.querySelector('.fav-btn') as HTMLButtonElement | null;
    expect(star).withContext(`${name} must render a star for the owner`).toBeTruthy();
    return star!;
  }

  it('stars the tapped dish and leaves every other dish alone', () => {
    const fixture = mount(
      [dish(11, 'Tea', 1), dish(12, 'Coffee', 1)],
      [{ categoryId: 1, name: 'Hot Drinks' }],
    );
    api.setMenuItemFavourite.and.returnValue(of({ ...dish(11, 'Tea', 1), favourite: true }));

    starFor(fixture, 'Tea').click();
    fixture.detectChanges();

    expect(api.setMenuItemFavourite).toHaveBeenCalledWith(11, true);
    const items = fixture.componentInstance.items;
    expect(items.find((i) => i.menuItemId === 11)!.favourite).toBe(true);
    expect(items.find((i) => i.menuItemId === 12)!.favourite).toBe(false);
  });

  it('adopts the server value rather than the value it asked for', () => {
    const fixture = mount([dish(11, 'Tea', 1)], [{ categoryId: 1, name: 'Hot Drinks' }]);
    // The server is authoritative and may answer differently (a concurrent change); the row
    // must follow the answer, not the request.
    api.setMenuItemFavourite.and.returnValue(of({ ...dish(11, 'Tea', 1), favourite: false }));

    starFor(fixture, 'Tea').click();
    fixture.detectChanges();

    expect(fixture.componentInstance.items[0].favourite).toBe(false);
  });

  it('leaves a dish unstarred when the write is refused', () => {
    const fixture = mount([dish(11, 'Tea', 1)], [{ categoryId: 1, name: 'Hot Drinks' }]);
    api.setMenuItemFavourite.and.returnValue(throwError(() => ({ error: { message: 'Only the owner can change favourites.' } })));

    starFor(fixture, 'Tea').click();
    fixture.detectChanges();

    expect(fixture.componentInstance.items[0].favourite).toBeFalsy();
    expect(toast.show).toHaveBeenCalledWith('Only the owner can change favourites.', 'error');
  });

  it('shows only the starred dishes and the pinned categories in the favourites view', () => {
    const fixture = mount(
      [dish(11, 'Tea', 1, true), dish(12, 'Coffee', 1), dish(13, 'Cola', 2)],
      [
        { categoryId: 2, name: 'Beverages', isFavourite: true },
        { categoryId: 1, name: 'Hot Drinks' },
      ],
    );

    fixture.componentInstance.toggleFavouritesOnly();
    fixture.detectChanges();

    const component = fixture.componentInstance;
    expect(component.pagedItems.map((i) => i.name)).toEqual(['Tea']);
    expect(component.favouriteCategories.map((c) => c.name)).toEqual(['Beverages']);
    expect(component.favouriteCount).toBe(2);

    const panel = fixture.nativeElement.querySelector('.fav-panel') as HTMLElement;
    expect(panel.textContent).toContain('Beverages');
    const renderedRows = fixture.nativeElement.querySelectorAll('.menu-table tbody tr');
    expect(renderedRows.length).toBe(1);
  });

  it('says nothing is starred yet instead of showing an empty table', () => {
    const fixture = mount([dish(12, 'Coffee', 1)], [{ categoryId: 1, name: 'Hot Drinks' }]);

    fixture.componentInstance.toggleFavouritesOnly();
    fixture.detectChanges();

    const panel = fixture.nativeElement.querySelector('.fav-panel') as HTMLElement;
    expect(panel.textContent).toContain('Nothing starred yet');
  });

  it('puts the whole menu back when the view is switched off', () => {
    const fixture = mount([dish(11, 'Tea', 1, true), dish(12, 'Coffee', 1)], [{ categoryId: 1, name: 'Hot Drinks' }]);

    fixture.componentInstance.toggleFavouritesOnly();
    fixture.componentInstance.toggleFavouritesOnly();
    fixture.detectChanges();

    expect(fixture.componentInstance.pagedItems.length).toBe(2);
    expect(fixture.nativeElement.querySelector('.fav-panel')).toBeNull();
  });
});
