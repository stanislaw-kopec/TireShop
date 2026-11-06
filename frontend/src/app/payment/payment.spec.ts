import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../app.routes';
import { AccountService } from '../account/account';
import { PaymentApi } from './payment-api';

const id = 'order-a';
const path = '/api/guest/orders/' + id;
const payUrl = path + '/payments';
const history = payUrl + '?page=0&size=1';
const pending = { id: 'p1', orderId: id, amountGross: 220, currency: 'PLN', status: 'PENDING', simulation: true };
const order = { id, orderNumber: 'TS-order-a', status: 'PENDING_PAYMENT',
  reservationExpiresAt: '2030-01-01T10:15:00Z', snapshot: { totalGross: 220, items: [] } };
const route = '/zamowienia/gosc/' + id + '/platnosc';

describe('Symulator płatności', () => {
  let http: HttpTestingController;
  let api: PaymentApi;
  let harness: RouterTestingHarness;
  let auth: AccountService;
  beforeEach(async () => {
    sessionStorage.clear();
    sessionStorage.setItem('tireshop.purchase.' + id, 'purchase-test');
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(routes)] });
    http = TestBed.inject(HttpTestingController);
    api = TestBed.inject(PaymentApi);
    auth = TestBed.inject(AccountService);
    harness = await RouterTestingHarness.create();
  });
  afterEach(() => {
    if (auth.token()) auth.expire(auth.token()!);
    sessionStorage.clear();
    http.verify();
  });
  const page = () => harness.routeNativeElement!;
  const button = (label: string) => Array.from(page().querySelectorAll('button')).find(b => b.textContent.trim() === label)!;
  async function settle() {
    await new Promise(resolve => setTimeout(resolve, 0));
    await harness.fixture.whenStable();
    harness.detectChanges();
  }
  async function openAttempt() {
    await harness.navigateByUrl(route + '?success=true');
    http.expectOne(path).flush(order);
    await settle();
    http.expectOne(history).flush({ items: [pending] });
    await settle();
    expect(page().textContent).not.toContain('Płatność potwierdzona');
  }

  it('keeps the creation key after a lost response and picks up another pending attempt on conflict', async () => {
    const first = api.start('guest', id);
    const request = http.expectOne(payUrl);
    const body = request.request.body;
    expect(Object.keys(body)).toEqual(['requestKey']);
    expect(request.request.headers.get('X-Purchase-Token')).toBe('purchase-test');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.error(new ProgressEvent('error'));
    await expect(first).rejects.toBeTruthy();
    const repeated = api.resume('guest', id);
    const retry = http.expectOne(payUrl);
    expect(retry.request.body).toEqual(body);
    retry.flush(pending);
    expect(await repeated).toEqual(pending);
    const otherTab = api.start('guest', id);
    const next = http.expectOne(payUrl);
    expect(next.request.body.requestKey).not.toBe(body.requestKey);
    next.flush({ code: 'PAYMENT_PENDING' }, { status: 409, statusText: 'Conflict' });
    await settle();
    http.expectOne(history).flush({ items: [pending] });
    expect(await otherTab).toEqual(pending);
  });

  it.each([['Odrzuć płatność', 'FAILED'], ['Anuluj próbę', 'CANCELLED']])(
    'lets the guest retry after %s, using server state and the original reservation', async (label, outcome) => {
      await openAttempt();
      button(label).click();
      const req = http.expectOne(payUrl + '/p1/simulation-result');
      expect(req.request.body).toEqual({ outcome });
      req.flush({ ...pending, status: outcome });
      await settle();
      http.expectOne(path).flush(order);
      await settle();
      http.expectOne(history).flush({ items: [{ ...pending, status: outcome }] });
      await settle();
      button('Ponów płatność').click();
      const create = http.expectOne(payUrl);
      expect(Object.keys(create.request.body)).toEqual(['requestKey']);
      create.flush({ ...pending, id: 'p2' });
      await settle();
      expect(button('Zatwierdź płatność')).toBeTruthy();
    });

  it('verifies ambiguous success against the order and retrieves the guest tracking link on the receipt', async () => {
    await openAttempt();
    button('Zatwierdź płatność').click();
    const req = http.expectOne(payUrl + '/p1/simulation-result');
    expect(req.request.body).toEqual({ outcome: 'SUCCEEDED' });
    req.error(new ProgressEvent('error'));
    await settle();
    expect(page().textContent).not.toContain('Płatność potwierdzona');
    button('Sprawdź status płatności').click();
    http.expectOne(path).flush({ ...order, status: 'PAID' });
    await settle();
    http.expectOne(path).flush({ ...order, status: 'PAID' });
    await settle();
    const link = http.expectOne(path + '/tracking-link');
    expect(link.request.headers.get('X-Purchase-Token')).toBe('purchase-test');
    link.flush({ trackingUrl: '/track-order#token=' + 'x'.repeat(43) });
    await settle();
    expect(page().textContent).toContain('Płatność potwierdzona');
    expect(page().querySelector('a[href*="track-order"]')).toBeTruthy();
  });

  it('does not offer retries when the server rejects a result at the reservation deadline', async () => {
    await openAttempt();
    button('Zatwierdź płatność').click();
    http.expectOne(payUrl + '/p1/simulation-result').flush({ code: 'PAYMENT_EXPIRED' }, { status: 409, statusText: 'Conflict' });
    await settle();
    http.expectOne(path).flush({ ...order, status: 'CANCELLED' });
    await settle();
    expect(page().textContent).toContain('Anulowane');
    expect(button('Ponów płatność')).toBeUndefined();
  });

  it('uses only the customer JWT for an account order and shows its paid number without a guest link', async () => {
    const login = auth.login('customer@example.test', 'password-test');
    http.expectOne('/api/auth/login').flush({ accessToken: 'test-jwt', expiresIn: 900 });
    await Promise.resolve();
    http.expectOne('/api/account/me').flush({ id: 'customer-a', role: 'CUSTOMER' });
    await login;
    const accountPath = '/api/orders/' + id;
    const start = api.start('customer-a', id);
    const req = http.expectOne(accountPath + '/payments');
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-jwt');
    expect(req.request.headers.has('X-Purchase-Token')).toBe(false);
    req.flush(pending);
    await start;
    await harness.navigateByUrl('/zamowienia/konto/' + id);
    http.expectOne(accountPath).flush({ ...order, status: 'PAID' });
    await settle();
    expect(page().textContent).toContain(order.orderNumber);
    expect(page().textContent).toContain('Płatność potwierdzona');
    http.expectNone(path + '/tracking-link');
  });

  it('uses only the tracking header when opening the public link', async () => {
    const token = 'x'.repeat(43);
    await harness.navigateByUrl('/track-order#token=' + token);
    const req = http.expectOne('/api/order-tracking');
    expect(req.request.headers.keys()).toEqual(['X-Tracking-Token']);
    expect(req.request.headers.get('X-Tracking-Token')).toBe(token);
    req.flush({ orderNumber: order.orderNumber, status: 'SHIPPED', trackingNumber: 'SHIP-123' });
    await settle();
    expect(page().textContent).toContain('SHIP-123');
    expect(page().textContent).not.toContain(token);
  });
});
