import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../app.routes';
import { AccountService } from '../account/account';
import { StaffApi } from './staff-api';

const tire = { id: 'product', manufacturer: 'Test', model: 'Frigo', widthMm: 205, aspectRatio: 55, rimDiameterIn: 16,
  season: 'WINTER', imageUrl: null, availableQuantity: 2, priceGross: null, onSale: false };

describe('Panele personelu', () => {
  let http: HttpTestingController;
  let auth: AccountService;
  let api: StaffApi;
  let harness: RouterTestingHarness;
  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(routes)] });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AccountService);
    api = TestBed.inject(StaffApi);
    harness = await RouterTestingHarness.create();
  });
  afterEach(() => { auth.clearLocalLogin(); sessionStorage.clear(); http.verify(); });
  const page = () => harness.routeNativeElement!;
  const button = (text: string) => Array.from(page().querySelectorAll('button')).find(b => b.textContent.trim() === text)!;
  async function settle() {
    await new Promise(resolve => setTimeout(resolve, 0));
    await harness.fixture.whenStable();
    harness.detectChanges();
  }
  async function login(role: 'EMPLOYEE' | 'ADMIN' | 'CUSTOMER') {
    const action = auth.login('user@example.test', 'staff-test-password');
    http.expectOne('/api/auth/login').flush({ accessToken: 'test-jwt', expiresIn: 900 });
    await Promise.resolve();
    http.expectOne('/api/account/me').flush({ id: 'user', role });
    await action;
  }

  it.each(['EMPLOYEE', 'ADMIN'] as const)('redirects a %s away from checkout and shows only allowed editing controls', async role => {
    sessionStorage.setItem('tireshop.guest-cart-token', 'guest-proof');
    await login(role);
    await harness.navigateByUrl('/koszyk');
    expect(TestBed.inject(Router).url).toBe('/personel');
    const list = http.expectOne(req => req.url === '/api/staff/tires');
    expect(list.request.headers.get('Authorization')).toBe('Bearer test-jwt');
    list.flush({ items: [tire], page: 0, totalPages: 1 });
    await settle();
    expect(!!button('Dodaj oponę')).toBe(role === 'EMPLOYEE');
    expect(!!button('Konta')).toBe(role === 'ADMIN');
    button('Test Frigo').click();
    await settle();
    if (role === 'EMPLOYEE') {
      http.expectOne('/api/employee/inventory/product').flush({ onHand: 3, reserved: 1, available: 2 });
      await settle();
      expect(button('Zapisz parametry')).toBeTruthy();
      expect(button('Zapisz cenę')).toBeUndefined();
    } else {
      expect(button('Zapisz parametry')).toBeUndefined();
      expect(button('Dopuść do sprzedaży').disabled).toBe(true);
      expect(button('Zapisz cenę')).toBeTruthy();
    }
    expect(sessionStorage.getItem('tireshop.guest-cart-token')).toBe('guest-proof');
    http.expectNone('/api/cart/merge');
    auth.expire('test-jwt');
    await settle();
    expect(page().textContent).toContain('Zaloguj się do panelu');
    expect(page().textContent).not.toContain('Test Frigo');
  });

  it('does not request staff data for a customer', async () => {
    await login('CUSTOMER');
    await harness.navigateByUrl('/personel');
    expect(page().textContent).toContain('dostępna dla pracowników');
    http.expectNone(req => req.url.startsWith('/api/staff'));
  });

  const operations: { resource: `inventory/${string}` | `order/${string}`; method: string; path: string; body: Record<string, string | number> }[] = [
    { resource: 'inventory/product' as const, method: 'POST', path: '/api/employee/inventory/product/movements',
      body: { type: 'RECEIPT', quantityDelta: 4, reason: 'Dostawa' } },
    { resource: 'order/test' as const, method: 'PUT', path: '/api/employee/orders/test/shipment',
      body: { expectedTrackingNumber: 'OLD', trackingNumber: 'NEW' } },
  ];
  it.each(operations)('replays the exact $method after a lost response for $resource', async ({ resource, method, path, body }) => {
    await login('EMPLOYEE');
    const first = api.apply(resource, body);
    const req = http.expectOne({ method, url: path });
    expect(req.request.body).toEqual({ ...body, operationId: expect.any(String) });
    req.error(new ProgressEvent('error'));
    await expect(first).rejects.toBeTruthy();
    const retry = api.apply(resource);
    const repeated = http.expectOne({ method, url: path });
    expect(repeated.request.body).toEqual(req.request.body);
    repeated.flush({});
    await retry;
    expect(api.pending(resource)).toBeNull();
  });

  it('submits a mail token once without credentials or trimming the password', async () => {
    const token = 'a'.repeat(43);
    await harness.navigateByUrl('/set-password#token=' + token);
    const password = page().querySelector<HTMLInputElement>('[name=password]')!;
    password.value = '  new-password  ';
    password.dispatchEvent(new Event('input', { bubbles: true }));
    const submit = button('Zapisz hasło');
    submit.click(); submit.click();
    const req = http.expectOne('/api/auth/password-actions/complete');
    expect(req.request.body).toEqual({ token, password: '  new-password  ' });
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    expect(page().textContent).toContain('Hasło zostało zapisane.');
    expect(page().querySelector('[name=password]')).toBeNull();
  });
});
