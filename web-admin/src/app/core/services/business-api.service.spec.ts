import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { BusinessApiService } from './business-api.service';
import { environment } from '../../../environments/environment';

/**
 * The favourite star is the only web-admin write that does NOT go through the ordinary
 * category form, so these tests pin the two things a silent regression would break:
 * the URL/method the owner-gated endpoint is published on, and the flag surviving the
 * category list response (without it the star renders as unstarred forever).
 */
describe('BusinessApiService category favourites', () => {
  let service: BusinessApiService;
  let httpMock: HttpTestingController;

  const CATEGORY_URL = `${environment.apiBaseUrl}/business/menu/categories`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [BusinessApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BusinessApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('pins a category with a PUT to the favourite sub-resource', () => {
    service.setCategoryFavourite(42, true).subscribe();

    const req = httpMock.expectOne(`${CATEGORY_URL}/42/favourite`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ isFavourite: true });
    req.flush({ categoryId: 42, name: 'Starters', isFavourite: true });
  });

  it('unpins a category by sending the flag as false', () => {
    service.setCategoryFavourite(42, false).subscribe();

    const req = httpMock.expectOne(`${CATEGORY_URL}/42/favourite`);
    // The server rejects a body without the key rather than treating "absent" as "unstar".
    expect(req.request.body).toEqual({ isFavourite: false });
    req.flush({ categoryId: 42, name: 'Starters', isFavourite: false });
  });

  it('surfaces the server-confirmed value to the caller', () => {
    let result: { isFavourite?: boolean } | undefined;
    service.setCategoryFavourite(7, true).subscribe((r) => (result = r));

    httpMock
      .expectOne(`${CATEGORY_URL}/7/favourite`)
      .flush({ categoryId: 7, name: 'Beverages', isFavourite: true });

    expect(result?.isFavourite).toBe(true);
  });

  it('carries isFavourite through the category list response', () => {
    let categories: { name: string; isFavourite?: boolean }[] = [];
    service.getMenuCategories().subscribe((c) => (categories = c));

    httpMock.expectOne(CATEGORY_URL).flush([
      { categoryId: 7, name: 'Beverages', isFavourite: true },
      { categoryId: 8, name: 'Mains', isFavourite: false },
    ]);

    expect(categories.length).toBe(2);
    expect(categories[0].isFavourite).toBe(true);
    expect(categories[1].isFavourite).toBe(false);
  });
});
