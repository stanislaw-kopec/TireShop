import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AccountService } from './account/account';
import { routes } from './app.routes';

const tire = { id: 'product', manufacturer: 'Test', model: 'Frigo', widthMm: 205, aspectRatio: 55, rimDiameterIn: 16,
  season: 'WINTER', imageUrl: null, availableQuantity: 2, priceGross: 100, onSale: true };
const order = { id: 'order', orderNumber: 'TS-order', status: 'PENDING_PAYMENT',
  reservationExpiresAt: '2030-01-01T10:15:00Z', snapshot: { totalGross: 220, items: [] } };
const created = { id: 'event', eventType: 'ORDER_CREATED', occurredAt: '2026-11-18T10:00:00Z',
  payload: { orderId: 'order', actorAccountId: null, reason: null } };
const movement = { operationId: 'movement', productId: 'product', actorAccountId: 'employee', orderId: null,
  type: 'RECEIPT', quantityDelta: 4, reason: 'Dostawa testowa', onHandAfter: 4, reservedAfter: 0, createdAt: '2026-11-18T10:00:00Z' };
const payment = { id: 'attempt', orderId: 'order', amountGross: 220, currency: 'PLN', status: 'FAILED',
  createdAt: '2026-11-18T10:00:00Z', finishedAt: '2026-11-18T10:01:00Z', reason: 'SIMULATED_FAILED', simulation: true };
const list = (items: unknown[], page = 0, totalPages = 1) => ({ items, page, size: 20, totalPages, totalElements: totalPages > 1 ? 21 : items.length });

describe('Widoki historii połączone z API', () => {
  let http: HttpTestingController;
  let auth: AccountService;
  let harness: RouterTestingHarness;
  beforeEach(async () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(routes)] });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AccountService);
    harness = await RouterTestingHarness.create();
  });
  afterEach(() => { auth.clearLocalLogin(); sessionStorage.clear(); http.verify(); });
  const page = () => harness.routeNativeElement!;
  const button = (label: string) => Array.from(page().querySelectorAll('button')).find(b => b.textContent.trim() === label)!;
  async function settle() {
    await new Promise(resolve => setTimeout(resolve, 0));
    await harness.fixture.whenStable();
    harness.detectChanges();
  }
  async function login(role: 'CUSTOMER' | 'EMPLOYEE' | 'ADMIN') {
    const action = auth.login('user@example.test', 'test-password');
    http.expectOne('/api/auth/login').flush({ accessToken: 'test-jwt', expiresIn: 900 });
    await Promise.resolve();
    http.expectOne('/api/account/me').flush({ id: 'user', role });
    await action;
  }
  async function staff(role: 'EMPLOYEE' | 'ADMIN') {
    await login(role);
    await harness.navigateByUrl('/personel');
    http.expectOne('/api/staff/tires?page=0&size=20').flush(list([tire]));
    await settle();
  }
  function select(value: string) {
    const input = page().querySelector('select')!;
    input.value = value;
    input.dispatchEvent(new Event('change', { bubbles: true }));
  }

  it('opens admin audit, paginates and ignores a late response after changing the filter', async () => {
    await staff('ADMIN');
    button('Dziennik zdarzeń').click();
    await settle();
    const first = http.expectOne('/api/admin/audit-log?page=0&size=20');
    expect(first.request.headers.get('Authorization')).toBe('Bearer test-jwt');
    first.flush(list([created], 0, 2));
    await settle();
    expect(page().textContent).toContain('Gość');
    expect(page().textContent).toContain('TS-order');
    button('Następna').click();
    await settle();
    const late = http.expectOne('/api/admin/audit-log?page=1&size=20');
    select('ORDER_CANCELLED');
    await settle();
    const filtered = http.expectOne('/api/admin/audit-log?page=0&size=20&eventType=ORDER_CANCELLED');
    filtered.flush(list([{ ...created, eventType: 'ORDER_CANCELLED', payload: { ...created.payload, reason: 'EXPIRED' } }]));
    await settle();
    expect(page().textContent).toContain('Wygaśnięcie rezerwacji');
    expect(page().textContent).toContain('System');
    late.flush(list([{ ...created, payload: { ...created.payload, orderId: 'obsolete' } }], 1, 2));
    await settle();
    expect(page().textContent).not.toContain('TS-obsolete');
    select('');
    await settle();
    http.expectOne('/api/admin/audit-log?page=0&size=20').flush(list([]));
    await settle();
    expect(page().textContent).toContain('Brak zdarzeń');
    auth.expire('test-jwt');
    await settle();
    expect(page().textContent).toContain('Zaloguj się do panelu');
    expect(page().querySelector('app-admin-audit')).toBeNull();
  });

  it('shows a recoverable audit error without leaving stale entries visible', async () => {
    await staff('ADMIN');
    button('Dziennik zdarzeń').click();
    await settle();
    http.expectOne('/api/admin/audit-log?page=0&size=20').flush(list([created]));
    await settle();
    button('Odśwież dziennik').click();
    await settle();
    http.expectOne('/api/admin/audit-log?page=0&size=20').flush({}, { status: 503, statusText: 'Unavailable' });
    await settle();
    expect(page().textContent).toContain('Usługa jest chwilowo niedostępna');
    expect(page().textContent).not.toContain('TS-order');
    button('Ponów pobieranie dziennika').click();
    await settle();
    http.expectOne('/api/admin/audit-log?page=0&size=20').flush(list([]));
    await settle();
    expect(page().textContent).toContain('Brak zdarzeń');
  });

  it('offers stock history only to employees, paginates and reloads after a stock change', async () => {
    await staff('EMPLOYEE');
    expect(button('Dziennik zdarzeń')).toBeUndefined();
    button('Test Frigo').click();
    await settle();
    http.expectOne('/api/employee/inventory/product').flush({ onHand: 4, reserved: 0, available: 4 });
    await settle();
    button('Pokaż historię magazynu').click();
    await settle();
    const first = http.expectOne('/api/employee/inventory/product/movements?page=0&size=20');
    expect(first.request.headers.get('Authorization')).toBe('Bearer test-jwt');
    first.flush(list([movement], 0, 2));
    await settle();
    expect(page().textContent).toContain('+4 szt.');
    expect(page().textContent).toContain('Dostawa testowa');
    button('Następna').click();
    await settle();
    http.expectOne('/api/employee/inventory/product/movements?page=1&size=20').flush(list([
      { ...movement, type: 'SALE', quantityDelta: -1, actorAccountId: null, orderId: 'order' },
    ], 1, 2));
    await settle();
    expect(page().textContent).toContain('Wydanie po płatności: -1 szt.');
    expect(page().textContent).toContain('TS-order');
    for (const [name, value] of [['delta', '3'], ['reason', 'Kolejna dostawa']]) {
      const input = page().querySelector<HTMLInputElement>(`[name=${name}]`)!;
      input.value = value;
      input.dispatchEvent(new Event('input', { bubbles: true }));
    }
    await settle();
    page().querySelector<HTMLInputElement>('[name=delta]')!.closest('form')!.requestSubmit();
    http.expectOne({ method: 'POST', url: '/api/employee/inventory/product/movements' }).flush({});
    await settle();
    http.expectOne('/api/employee/inventory/product').flush({ onHand: 7, reserved: 0, available: 7 });
    await settle();
    http.expectOne('/api/employee/inventory/product/movements?page=0&size=20').flush(list([{ ...movement, quantityDelta: 3, reason: 'Kolejna dostawa' }]));
    await settle();
    expect(page().textContent).toContain('Kolejna dostawa');
    expect(page().textContent).not.toContain('Wydanie po płatności: -1 szt.');
    http.expectNone(req => req.url.startsWith('/api/admin/'));
  });

  it('does not offer employee stock history in the administrator editor', async () => {
    await staff('ADMIN');
    button('Test Frigo').click();
    await settle();
    expect(button('Pokaż historię magazynu')).toBeUndefined();
    http.expectNone(req => req.url.startsWith('/api/employee/'));
  });

  it.each(['guest', 'customer'])('shows and paginates payment attempts with only the right credentials for %s', async owner => {
    // A guest order stays a guest purchase even if the browser also has a logged-in customer.
    await login('CUSTOMER');
    sessionStorage.setItem('tireshop.purchase.order', 'purchase-test');
    const url = owner === 'guest' ? '/api/guest/orders/order' : '/api/orders/order';
    await harness.navigateByUrl('/zamowienia/' + (owner === 'guest' ? 'gosc/' : 'konto/') + 'order');
    http.expectOne(url).flush(order);
    await settle();
    button('Pokaż historię płatności').click();
    await settle();
    const request = http.expectOne(url + '/payments?page=0&size=20');
    expect(request.request.headers.get('Authorization')).toBe(owner === 'customer' ? 'Bearer test-jwt' : null);
    expect(request.request.headers.get('X-Purchase-Token')).toBe(owner === 'guest' ? 'purchase-test' : null);
    request.flush(list([payment, { ...payment, id: 'second', status: 'SUCCEEDED', reason: 'SIMULATED_SUCCEEDED' }], 0, 2));
    await settle();
    expect(page().textContent).toContain('Odrzucona');
    expect(page().textContent).toContain('Udana');
    expect(page().textContent).toContain('Potwierdzenie w symulatorze');
    expect(page().textContent).not.toContain('purchase-test');
    button('Następna').click();
    await settle();
    http.expectOne(url + '/payments?page=1&size=20').flush(list([{ ...payment, status: 'CANCELLED', reason: 'EXPIRED' }], 1, 2));
    await settle();
    expect(page().textContent).toContain('Wygaśnięcie rezerwacji');
    button('Odśwież historię płatności').click();
    await settle();
    http.expectOne(url + '/payments?page=1&size=20').flush({ code: 'ORDER_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await settle();
    expect(page().textContent).toContain('Brak dostępu do tego zakupu');
    expect(page().textContent).not.toContain('Wygaśnięcie rezerwacji');
    button('Ponów pobieranie historii płatności').click();
    await settle();
    http.expectOne(url + '/payments?page=1&size=20').flush(list([], 1, 2));
    await settle();
    expect(page().textContent).toContain('Brak prób płatności');
    http.expectNone(req => req.method !== 'GET');
    if (owner === 'customer') {
      auth.expire('test-jwt');
      await settle();
      expect(page().querySelector('app-payment-history')).toBeNull();
    }
  });

  it('does not expose payment history without purchase access or through the public tracking link', async () => {
    await harness.navigateByUrl('/zamowienia/gosc/order');
    await settle();
    expect(button('Pokaż historię płatności')).toBeUndefined();
    http.expectNone(req => req.url.includes('/payments'));
    await harness.navigateByUrl('/track-order#token=' + 'x'.repeat(43));
    http.expectOne('/api/order-tracking').flush({ orderNumber: 'TS-order', status: 'PAID', trackingNumber: null });
    await settle();
    expect(button('Pokaż historię płatności')).toBeUndefined();
    http.expectNone(req => req.url.includes('/payments'));
  });
});
