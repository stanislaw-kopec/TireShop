import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../app.routes';
import { AccountService } from './account';

const id = '11111111-2222-4333-8444-555555555555';
const order = { id, orderNumber: 'TS-' + id, status: 'PENDING_PAYMENT', createdAt: '2026-11-01T10:00:00Z',
  reservationExpiresAt: '2026-11-01T10:15:00Z', totalGross: 235, snapshot: { items: [], totalGross: 235 } };

describe('Historia, anulowanie i odnowienie śledzenia', () => {
  let http: HttpTestingController;
  let auth: AccountService;
  let harness: RouterTestingHarness;
  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(routes)] });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AccountService);
    harness = await RouterTestingHarness.create();
  });
  afterEach(() => {
    if (auth.token()) auth.expire(auth.token()!);
    sessionStorage.removeItem('tireshop.purchase.' + id);
    http.verify();
  });
  const page = () => harness.routeNativeElement!;
  const button = (text: string) => Array.from(page().querySelectorAll('button')).find(b => b.textContent.trim() === text);
  async function settle() {
    await new Promise(resolve => setTimeout(resolve, 0));
    await harness.fixture.whenStable();
    harness.detectChanges();
  }
  async function login() {
    const result = auth.login('customer@example.test', 'password-test');
    http.expectOne('/api/auth/login').flush({ accessToken: 'jwt-test', expiresIn: 900 });
    await Promise.resolve();
    http.expectOne('/api/account/me').flush({ id: 'customer-id', role: 'CUSTOMER' });
    await result;
  }

  it('keeps history private, paginates on the server and opens the selected order', async () => {
    await harness.navigateByUrl('/konto/zamowienia');
    expect(page().textContent).toContain('Zaloguj się');
    http.expectNone(req => req.url === '/api/orders');
    await login();
    await settle();
    const first = http.expectOne('/api/orders?page=0&size=20');
    expect(first.request.headers.get('Authorization')).toBe('Bearer jwt-test');
    first.flush({ items: [order], page: 0, totalPages: 2 });
    await settle();
    page().querySelector<HTMLAnchorElement>('nav a')!.click();
    await settle();
    const shipped = { ...order, status: 'SHIPPED', trackingNumber: 'SHIP-123' };
    http.expectOne('/api/orders?page=1&size=20').flush({ items: [shipped], page: 1, totalPages: 2 });
    await settle();
    expect(page().textContent).toContain('Strona 2 z 2');
    expect(page().textContent).toContain('SHIP-123');
    page().querySelector<HTMLAnchorElement>('li a')!.click();
    await settle();
    http.expectOne('/api/orders/' + id).flush(shipped);
    await settle();
    expect(page().textContent).toContain('Wysłane');
    expect(page().textContent).toContain('SHIP-123');
    expect(button('Anuluj zamówienie')).toBeUndefined();
  });

  it.each(['guest', 'customer'])('cancels a pending order with the correct credentials for %s', async owner => {
    if (owner === 'customer') await login();
    else sessionStorage.setItem('tireshop.purchase.' + id, 'purchase-test');
    const url = (owner === 'guest' ? '/api/guest/orders/' : '/api/orders/') + id;
    await harness.navigateByUrl('/zamowienia/' + (owner === 'guest' ? 'gosc/' : 'konto/') + id);
    http.expectOne(url).flush(order);
    await settle();
    const cancel = button('Anuluj zamówienie')!;
    cancel.click(); cancel.click();
    const post = http.expectOne({ method: 'POST', url: url + '/cancellation' });
    expect(post.request.headers.get('Authorization')).toBe(owner === 'customer' ? 'Bearer jwt-test' : null);
    expect(post.request.headers.get('X-Purchase-Token')).toBe(owner === 'guest' ? 'purchase-test' : null);
    const cancelled = { ...order, status: 'CANCELLED', cancellationReason: 'CUSTOMER' };
    post.flush(cancelled);
    await settle();
    http.expectOne(url).flush(cancelled);
    await settle();
    expect(page().textContent).toContain('Anulowane');
    expect(button('Anuluj zamówienie')).toBeUndefined();
  });

  it('reloads the authoritative state when payment wins the race with cancellation', async () => {
    await login();
    const url = '/api/orders/' + id;
    await harness.navigateByUrl('/zamowienia/konto/' + id);
    http.expectOne(url).flush(order);
    await settle();
    button('Anuluj zamówienie')!.click();
    http.expectOne(url + '/cancellation').flush({ code: 'ORDER_NOT_CANCELLABLE' }, { status: 409, statusText: 'Conflict' });
    await settle();
    http.expectOne(url).flush({ ...order, status: 'PAID' });
    await settle();
    expect(page().textContent).toContain('Opłacone');
    expect(page().textContent).toContain('Status zamówienia zmienił się');
    expect(button('Anuluj zamówienie')).toBeUndefined();
  });

  it('accepts renewal after an expired link and sends only order number and original email', async () => {
    await harness.navigateByUrl('/track-order#token=' + 'x'.repeat(43));
    http.expectOne('/api/order-tracking').flush({ code: 'TRACKING_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await settle();
    page().querySelector<HTMLInputElement>('[name=orderNumber]')!.value = order.orderNumber;
    page().querySelector<HTMLInputElement>('[name=email]')!.value = 'guest@example.test';
    page().querySelector('form')!.requestSubmit();
    const req = http.expectOne({ method: 'POST', url: '/api/order-tracking/link-requests' });
    expect(req.request.body).toEqual({ orderNumber: order.orderNumber, email: 'guest@example.test' });
    expect(req.request.headers.keys()).toEqual([]);
    req.flush({}, { status: 202, statusText: 'Accepted' });
    await settle();
    expect(page().textContent).toContain('Jeśli dane dotyczą opłaconego zamówienia gościa');
    expect(page().querySelector('a[href*="token="]')).toBeNull();
  });
});
