import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../app.routes';
import { Cart, ShoppingCart } from '../cart/shopping-cart';
import { AccountService, handleExpiredLogin } from './account';

const user = { id: 'a', role: 'CUSTOMER', email: 'klient@example.test', firstName: 'Jan', lastName: 'Test', phone: null };
const guestKey = 'tireshop.guest-cart-token';
const cart: Cart = { items: [], totalQuantity: 0, itemsTotalGross: 0, shippingGross: 0, totalGross: 0,
  currency: 'PLN', readyForCheckout: false };
const product = { id: 'p', model: 'Frigo', manufacturer: 'Dębica', season: 'WINTER' as const, widthMm: 205,
  aspectRatio: 55, rimDiameterIn: 16, priceGross: 100, imageUrl: null, availableQuantity: 4 };
function basket(n: number): Cart {
  return { ...cart, items: [{ product, quantity: n, lineTotalGross: n * 100, issue: 'NONE' }], totalQuantity: n,
    itemsTotalGross: n * 100, shippingGross: n < 4 ? 20 : 0, totalGross: n * 100 + (n < 4 ? 20 : 0), readyForCheckout: true };
}

describe('Konto klienta i łączenie koszyków', () => {
  let http: HttpTestingController;
  let auth: AccountService;
  let store: ShoppingCart;

  beforeEach(() => {
    sessionStorage.removeItem(guestKey);
    TestBed.configureTestingModule({ providers: [provideRouter(routes),
      provideHttpClient(withInterceptors([handleExpiredLogin])), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AccountService);
    store = TestBed.inject(ShoppingCart);
  });

  afterEach(() => {
    if (auth.token()) auth.expire(auth.token()!);
    sessionStorage.removeItem(guestKey);
    http.verify();
  });

  async function login(expiresIn = 900) {
    const done = auth.login(user.email, ' test password ');
    const req = http.expectOne('/api/auth/login');
    expect(req.request.headers.has('Authorization')).toBe(false);
    expect(req.request.body.password).toBe(' test password ');
    req.flush({ accessToken: 'test-jwt', expiresIn });
    await Promise.resolve();
    const profile = http.expectOne('/api/account/me');
    expect(profile.request.headers.get('Authorization')).toBe('Bearer test-jwt');
    profile.flush(user);
    await done;
  }

  it('registers without a role and signs in separately; logs out on the backend', async () => {
    const registered = auth.register(user.email, ' test password ', 'Jan', 'Test');
    const req = http.expectOne('/api/auth/register');
    expect(req.request.body).toEqual({ email: user.email, password: ' test password ', firstName: 'Jan', lastName: 'Test' });
    req.flush(user);
    await registered;
    expect(auth.token()).toBeNull();
    await login();
    expect(JSON.stringify(sessionStorage)).not.toContain('test-jwt');
    expect(JSON.stringify(localStorage)).not.toContain('test-jwt');
    const out = auth.logout();
    const logout = http.expectOne({ method: 'POST', url: '/api/auth/logout' });
    expect(logout.request.headers.get('Authorization')).toBe('Bearer test-jwt');
    logout.flush(null);
    await out;
    expect(auth.profile()).toBeNull();
  });

  it('recovers both carts on conflict, edits the guest cart without JWT and retries the merge', async () => {
    sessionStorage.setItem(guestKey, 'guest-key');
    await login();
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/koszyk');
    const req = http.expectOne('/api/cart/merge');
    expect(req.request.headers.get('X-Cart-Token')).toBe('guest-key');
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-jwt');
    req.flush({ code: 'CART_MERGE_UNAVAILABLE' }, { status: 409, statusText: 'Conflict' });
    await Promise.resolve();
    http.expectOne('/api/cart').flush(basket(3));
    const guest = http.expectOne('/api/guest/cart');
    expect(guest.request.headers.has('Authorization')).toBe(false);
    guest.flush(basket(2));
    await expect.poll(() => store.busy()).toBe(false);
    harness.detectChanges();
    const page = harness.routeNativeElement!;
    const button = (name: string) => Array.from(page.querySelectorAll('button')).find(b => b.textContent.trim() === name)!;
    expect(sessionStorage.getItem(guestKey)).toBe('guest-key');
    button('Koszyk gościa').click();
    harness.detectChanges();
    const quantity = page.querySelector<HTMLInputElement>('li input')!;
    expect(quantity.value).toBe('2');
    quantity.value = '1';
    button('Zapisz').click();
    const edit = http.expectOne({ method: 'PUT', url: '/api/guest/cart/items/p' });
    expect(edit.request.headers.has('Authorization')).toBe(false);
    expect(edit.request.body).toEqual({ quantity: 1 });
    edit.flush(basket(1));
    await expect.poll(() => store.busy()).toBe(false);
    harness.detectChanges();
    button('Połącz koszyki').click();
    http.expectOne('/api/cart/merge').flush(basket(4));
    await expect.poll(() => store.busy()).toBe(false);
    harness.detectChanges();
    expect(page.querySelector('aside')!.textContent).toContain('Bezpłatna');
    expect(store.owner()).toBe('customer');
    expect(sessionStorage.getItem(guestKey)).toBeNull();
  });

  it('reuses the guest token if the merge response is lost, then edits only the customer cart', async () => {
    sessionStorage.setItem(guestKey, 'guest-key');
    await login();
    const first = store.open();
    http.expectOne('/api/cart/merge').error(new ProgressEvent('error'));
    expect(await first).toBe(false);
    expect(store.mergeState()).toBe('retry');
    expect(sessionStorage.getItem(guestKey)).toBe('guest-key');
    const retry = store.merge();
    http.expectOne('/api/cart/merge').flush(basket(1));
    await retry;
    const add = store.add('p', 1);
    http.expectOne('/api/cart').flush(basket(1));
    await Promise.resolve();
    const put = http.expectOne('/api/cart/items/p');
    expect(put.request.headers.has('X-Cart-Token')).toBe(false);
    expect(put.request.headers.get('Authorization')).toBe('Bearer test-jwt');
    expect(put.request.body).toEqual({ quantity: 2 });
    put.flush(basket(2));
    await add;
    expect(store.count()).toBe(2);
  });

  it('supports an initially missing address and handles session revocation without showing customer data', async () => {
    await login();
    const initial = auth.address();
    http.expectOne('/api/account/me/address').flush({ code: 'ADDRESS_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    expect(await initial).toBeNull();
    const address = { recipientName: 'Jan Test', street: 'Letnia', buildingNumber: '2', apartmentNumber: null,
      postalCode: '00-100', city: 'Warszawa', countryCode: 'PL' };
    const saved = auth.saveAddress(address);
    const put = http.expectOne({ method: 'PUT', url: '/api/account/me/address' });
    expect(put.request.body).toEqual(address);
    put.flush(address);
    expect(await saved).toEqual(address);
    const read = store.refresh();
    http.expectOne('/api/cart').flush(basket(3));
    await read;
    const edit = auth.updateProfile({ firstName: 'Jan', lastName: 'Test', phone: '123' });
    http.expectOne('/api/account/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    await expect(edit).rejects.toBeTruthy();
    expect(auth.token()).toBeNull();
    expect(store.cart()).toBeNull();
    expect(auth.notice()).toContain('Zaloguj się ponownie');
  });

  it('requires login again when the issued token reaches its lifetime', async () => {
    vi.useFakeTimers();
    try {
      await login();
      vi.advanceTimersByTime(899999);
      expect(auth.profile()?.id).toBe('a');
      vi.advanceTimersByTime(1);
      expect(auth.profile()).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });
});
