import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { MenuPageComponent } from './menu-page.component';
import { BusinessApiService } from '../../core/services/business-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { BusinessCategory, BusinessMenuItem, UpdateMenuItemRequest } from '../../core/models/api.models';

/**
 * Emptying a category, which is the only way the owner can retire one.
 *
 * The delete button has always been disabled on a category that still holds dishes, and the
 * prompt behind it only said "Move or delete the dishes first" — with no way to move anything.
 * The bulk move replaces that dead end, so what these specs pin is the part that made it
 * unusable rather than cosmetic: every dish is sent with ONLY its category changed, a dish the
 * server refuses is named with the server's own reason and stays visible in the source category,
 * and the delete that was impossible a moment ago becomes the next step of the same flow.
 */
const authStub = {
  session: () => ({ role: 'OWNER', restaurantId: 1, loginId: 'owner', restaurantName: 'Test Kitchen' }),
} as unknown as AuthService;

const routerStub = { navigate: () => Promise.resolve(true), events: of(), createUrlTree: () => null, serializeUrl: () => '' };

function dish(menuItemId: number, name: string, categoryId: number): BusinessMenuItem {
  return {
    menuItemId,
    categoryId,
    categoryName: categoryId === 1 ? 'Hot Drinks' : 'Beverages',
    name,
    description: `${name} description`,
    foodType: 'non-veg',
    basePrice: 40,
    available: true,
    stockStatus: 'in_stock',
    variantCount: 0,
    updatedAt: 1,
  };
}

describe('MenuPageComponent — bulk move out of a category', () => {
  let api: jasmine.SpyObj<BusinessApiService>;
  let toast: jasmine.SpyObj<ToastService>;

  // State is applied before the first detectChanges: the modal markup in this page only
  // renders on that first pass (the same reason the other page specs here mount their
  // state up front), so an assertion on the rendered modal cannot follow an interaction.
  function mount(beforeRender?: (component: MenuPageComponent) => void): ComponentFixture<MenuPageComponent> {
    api = jasmine.createSpyObj<BusinessApiService>('BusinessApiService', [
      'getMenu',
      'getMenuCategories',
      'updateMenuItem',
      'deleteCategory',
    ]);
    api.getMenu.and.returnValue(of([dish(11, 'Tea', 1), dish(12, 'Coffee', 1), dish(13, 'Cola', 2)]));
    api.getMenuCategories.and.returnValue(of<BusinessCategory[]>([
      { categoryId: 1, name: 'Hot Drinks' },
      { categoryId: 2, name: 'Beverages' },
      { categoryId: 3, name: 'Desserts' }, // empty: nothing to move, and already deletable
    ]));
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
    beforeRender?.(fixture.componentInstance);
    fixture.detectChanges();
    return fixture;
  }

  /** Picks the source by name: the page re-sorts categories (favourites first) on load. */
  function sourceCategory(component: MenuPageComponent): BusinessCategory {
    const source = component.categories.find((cat) => cat.name === 'Hot Drinks');
    expect(source).withContext('Hot Drinks is the category holding dishes').toBeTruthy();
    return source!;
  }

  /** Opens the flow the way the category row does, with the destination picked. */
  function startMove(fixture: ComponentFixture<MenuPageComponent>, targetCategoryId = 2): MenuPageComponent {
    const component = fixture.componentInstance;
    component.showCategoriesModal = true;
    component.startBulkMove(sourceCategory(component));
    component.bulkMoveTargetCategoryId = targetCategoryId;
    fixture.detectChanges();
    return component;
  }

  it('offers the move on a category that cannot be deleted, and nothing on an empty one', () => {
    const fixture = mount((component) => { component.showCategoriesModal = true; });
    const rows = Array.from(fixture.nativeElement.querySelectorAll('.cat-list-row')) as HTMLElement[];
    expect(rows.length).withContext('every category renders in the Manage Categories list').toBe(3);

    const buttons = (row: HTMLElement) =>
      Array.from(row.querySelectorAll('button')).map((button) => ({ button: button as HTMLButtonElement, label: (button as HTMLButtonElement).textContent?.trim() }));

    const sourceRow = rows.find((row) => row.textContent?.includes('Hot Drinks'))!;
    const emptyRow = rows.find((row) => row.textContent?.includes('Desserts'))!;

    const moveButton = buttons(sourceRow).find((entry) => entry.label === 'Move dishes');
    expect(moveButton).withContext('a category holding dishes offers the way to empty it').toBeTruthy();
    expect(buttons(sourceRow).find((entry) => entry.label === 'Delete')!.button.disabled)
      .withContext('and still cannot be deleted directly')
      .toBeTrue();

    const emptyRowButtons = buttons(emptyRow);
    expect(emptyRowButtons.some((entry) => entry.label === 'Move dishes'))
      .withContext('an empty category has nothing to move')
      .toBeFalse();
    expect(emptyRowButtons.find((entry) => entry.label === 'Delete')!.button.disabled)
      .withContext('and is deletable in one step, as before')
      .toBeFalse();

    moveButton!.button.click();
    expect(fixture.componentInstance.bulkMoveSource?.name)
      .withContext('the button opens the flow for that category')
      .toBe('Hot Drinks');
  });

  it('renders the destination picker and the dish count', () => {
    const fixture = mount((component) => {
      component.showCategoriesModal = true;
      component.startBulkMove(sourceCategory(component));
    });

    const select = fixture.nativeElement.querySelector('#bulk-move-target') as HTMLSelectElement;
    expect(select).withContext('the destination picker renders').toBeTruthy();
    expect(Array.from(select.options).map((option) => option.textContent?.trim()))
      .withContext('the source category is not offered as its own destination')
      .toEqual(['Choose category…', 'Beverages', 'Desserts']);
    const action = Array.from(fixture.nativeElement.querySelectorAll('.modal-actions button'))
      .map((button) => (button as HTMLButtonElement).textContent?.trim())
      .find((label) => label?.startsWith('Move '));
    expect(action).withContext('the action states how many dishes it will move').toBe('Move 2 dish(es)');
  });

  it('offers only other categories as the destination', () => {
    const fixture = mount();
    const component = startMove(fixture);

    expect(component.bulkMoveTargetCategories.map((cat) => cat.name)).toEqual(['Beverages', 'Desserts']);
    expect(component.bulkMoveItems.map((item) => item.name)).toEqual(['Tea', 'Coffee']);
  });

  it('moves every dish, changing nothing but the category, and reports the count', () => {
    const fixture = mount();
    api.updateMenuItem.and.callFake((menuItemId: number, payload: UpdateMenuItemRequest) =>
      of({ ...dish(menuItemId, payload.name, payload.categoryId) })
    );
    const component = startMove(fixture);

    component.confirmBulkMove();

    expect(api.updateMenuItem).toHaveBeenCalledTimes(2);
    const [, payload] = api.updateMenuItem.calls.argsFor(0);
    expect(payload.categoryId).withContext('the destination is the only changed field').toBe(2);
    expect(payload.name).toBe('Tea');
    expect(payload.basePrice).toBe(40);
    expect(payload.description).toBe('Tea description');
    expect(payload.foodType).withContext('a non-veg dish must not silently become veg').toBe('non-veg');

    expect(component.bulkMoveSummary?.movedCount).toBe(2);
    expect(component.bulkMoveSummary?.failed).toEqual([]);
    expect(component.bulkMoveItems.length).withContext('the source category is empty now').toBe(0);
    expect(component.bulkMoveCanDelete).withContext('so it can be deleted in the same flow').toBeTrue();
    expect(toast.show).toHaveBeenCalledWith('Moved 2 dish(es) to Beverages', 'success');
  });

  it('names the dish the server refused and keeps it in the source category', () => {
    const fixture = mount();
    api.updateMenuItem.and.callFake((menuItemId: number, payload: UpdateMenuItemRequest) =>
      menuItemId === 11
        ? throwError(() => ({ error: { message: 'A dish called Tea is already in Beverages' } }))
        : of({ ...dish(menuItemId, payload.name, payload.categoryId) })
    );
    const component = startMove(fixture);

    component.confirmBulkMove();

    // The second dish is still attempted: one refusal must not abandon the rest.
    expect(api.updateMenuItem).toHaveBeenCalledTimes(2);
    expect(component.bulkMoveSummary?.movedCount).toBe(1);
    expect(component.bulkMoveSummary?.failed).toEqual([
      { name: 'Tea', reason: 'A dish called Tea is already in Beverages' },
    ]);
    expect(component.bulkMoveItems.map((item) => item.name)).withContext('Tea is still in Hot Drinks').toEqual(['Tea']);
    expect(component.bulkMoveCanDelete).withContext('a category that still holds a dish cannot be deleted').toBeFalse();
    expect(toast.show).toHaveBeenCalledWith(
      'Moved 1 of 2 dishes to Beverages. 1 could not be moved.',
      'error'
    );
  });

  it('falls back to a readable reason when the server sends no message', () => {
    const fixture = mount();
    api.updateMenuItem.and.returnValue(throwError(() => ({ status: 500 })));
    const component = startMove(fixture);

    component.confirmBulkMove();

    expect(component.bulkMoveSummary?.failed.map((failure) => failure.reason)).toEqual([
      'Failed to move this dish.',
      'Failed to move this dish.',
    ]);
  });

  it('does not move anything when no destination has been chosen', () => {
    const fixture = mount();
    const component = fixture.componentInstance;
    component.showCategoriesModal = true;
    component.startBulkMove(component.categories[0]);
    component.bulkMoveTargetCategoryId = null;

    component.confirmBulkMove();

    expect(api.updateMenuItem).not.toHaveBeenCalled();
    expect(component.bulkMoveSummary).toBeNull();
  });
});
