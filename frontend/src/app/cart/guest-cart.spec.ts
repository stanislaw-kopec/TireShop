import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../app.routes';
import { Cart, GuestCart } from './guest-cart';

const url = '/api/guest/cart';
const storageKey = 'tireshop.guest-cart-token';
const product = { id: 'tire-a', manufacturer: 'Dębica', model: 'Frigo', widthMm: 205, aspectRatio: 55,
  rimDiameterIn: 16, season: 'WINTER' as const, priceGross: 100, availableQuantity: 8, imageUrl: null };
const empty: Cart = { items: [], totalQuantity: 0, itemsTotalGross: 0, shippingGross: 0, totalGross: 0,
  currency: 'PLN', readyForCheckout: false };
const three: Cart = {
  ...empty, items: [
    { product, quantity: 2, lineTotalGross: 200, issue: 'NONE' },
    { product: { ...product, id: 'tire-b', model: 'Druga' }, quantity: 1, lineTotalGross: 100, issue: 'NONE' },
  ], totalQuantity: 3, itemsTotalGross: 300, shippingGross: 20, totalGross: 320, readyForCheckout: true,
};
const four: Cart = { ...three, items: [{ ...three.items[0], quantity: 3, lineTotalGross: 300 }, three.items[1]],
  totalQuantity: 4, itemsTotalGross: 400, shippingGross: 0, totalGross: 400 };

describe('Koszyk gościa', () => {
  let http: HttpTestingController;
  let store: GuestCart;
  beforeEach(() => {
    sessionStorage.removeItem(storageKey);
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    store = TestBed.inject(GuestCart);
  });
  afterEach(() => {
    sessionStorage.removeItem(storageKey);
    http.verify();
  });

  it('creates on first add only, ignores a double click and sets the combined target on the next add', async () => {
    await store.refresh();
    http.expectNone(url);
    const first = store.add(product.id, 2);
    const duplicate = store.add(product.id, 2);
    const create = http.expectOne({ method: 'POST', url });
    expect(create.request.headers.has('X-Cart-Token')).toBe(false);
    create.flush({ token: 'guest-secret', cart: empty });
    await Promise.resolve();
    const put = http.expectOne({ method: 'PUT', url: url + '/items/tire-a' });
    expect(put.request.headers.get('X-Cart-Token')).toBe('guest-secret');
    expect(put.request.body).toEqual({ quantity: 2 });
    put.flush({ ...empty, items: [three.items[0]], totalQuantity: 2 });
    expect(await first).toBe(true);
    expect(await duplicate).toBe(false);
    expect(sessionStorage.getItem(storageKey)).toBe('guest-secret');

    const next = store.add(product.id, 1);
    http.expectOne({ method: 'GET', url }).flush(three);
    await Promise.resolve();
    const combined = http.expectOne({ method: 'PUT', url: url + '/items/tire-a' });
    expect(combined.request.body).toEqual({ quantity: 3 });
    combined.flush(four);
    expect(await next).toBe(true);
    expect(store.cart()?.totalQuantity).toBe(4);
  });

  it('restores a cart from the tab token, shows server shipping, edits and removes a line', async () => {
    sessionStorage.setItem(storageKey, 'guest-secret');
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/koszyk');
    const get = http.expectOne({ method: 'GET', url });
    expect(get.request.headers.get('X-Cart-Token')).toBe('guest-secret');
    get.flush(three);
    await expect.poll(() => store.busy()).toBe(false);
    await harness.fixture.whenStable();
    harness.detectChanges();
    const page = harness.routeNativeElement!;
    expect(page.querySelector('aside')!.textContent).toContain('20,00');
    const row = page.querySelector('li')!;
    row.querySelector('input')!.value = '3';
    row.querySelector<HTMLButtonElement>('form button')!.click();
    const save = http.expectOne({ method: 'PUT', url: url + '/items/tire-a' });
    expect(save.request.body).toEqual({ quantity: 3 });
    save.flush(four);
    await expect.poll(() => store.busy()).toBe(false);
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(page.querySelector('aside')!.textContent).toContain('Bezpłatna');
    row.querySelector<HTMLButtonElement>('button[type="button"]')!.click();
    const remove = http.expectOne({ method: 'DELETE', url: url + '/items/tire-a' });
    expect(remove.request.headers.get('X-Cart-Token')).toBe('guest-secret');
    remove.flush({ ...three, items: [three.items[1]], totalQuantity: 1, itemsTotalGross: 100, totalGross: 120 });
    await expect.poll(() => store.busy()).toBe(false);
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(page.querySelectorAll('li').length).toBe(1);
    expect(page.querySelector('aside')!.textContent).toContain('20,00');
    page.querySelector<HTMLButtonElement>('li button[type="button"]')!.click();
    http.expectOne({ method: 'DELETE', url: url + '/items/tire-b' }).flush(empty);
    await expect.poll(() => store.busy()).toBe(false);
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(page.textContent).toContain('Twój koszyk jest pusty');
  });

  it('keeps the token on stock conflict, reloads line issues and clears only an unavailable cart', async () => {
    sessionStorage.setItem(storageKey, 'guest-secret');
    const action = store.setQuantity(product.id, 5);
    http.expectOne(url + '/items/tire-a').flush({ code: 'CART_PRODUCT_UNAVAILABLE' }, { status: 409, statusText: 'Conflict' });
    // Allow both the PUT helper and the operation wrapper to observe the rejection.
    await new Promise(resolve => setTimeout(resolve, 0));
    http.expectOne(url).flush({ ...three, items: [{ ...three.items[0], issue: 'INSUFFICIENT_STOCK' }], readyForCheckout: false });
    expect(await action).toBe(false);
    expect(store.error()).toContain('niedostępna');
    expect(store.cart()?.items[0].quantity).toBe(2);
    expect(sessionStorage.getItem(storageKey)).toBe('guest-secret');

    const offline = store.refresh();
    http.expectOne(url).error(new ProgressEvent('error'));
    expect(await offline).toBe(false);
    expect(sessionStorage.getItem(storageKey)).toBe('guest-secret');
    const expired = store.refresh();
    http.expectOne(url).flush({ code: 'CART_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    expect(await expired).toBe(false);
    expect(sessionStorage.getItem(storageKey)).toBeNull();
    expect(store.cart()).toBeNull();
  });

  it('does not create a cart for invalid quantities', async () => {
    for (const value of [0, -1, 1.5, NaN]) expect(await store.add(product.id, value)).toBe(false);
    http.expectNone(url);
  });
});
