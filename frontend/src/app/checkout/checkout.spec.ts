import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../app.routes';
import { AccountService } from '../account/account';
import { CheckoutApi } from './checkout-api';

const guest = '/api/guest/orders';
const address = { recipientName: 'Jan Test', street: 'Testowa', buildingNumber: '1', apartmentNumber: null,
  postalCode: '00-001', city: 'Warszawa', countryCode: 'PL' };
const delivery = { email: 'jan@example.test', phone: '123456789', address };
const snapshot = { items: [{ product: { id: 'tire', manufacturer: 'Dębica', model: 'Frigo', season: 'WINTER',
  widthMm: 205, aspectRatio: 55, rimDiameterIn: 16 }, quantity: 1, unitPriceGross: 200, lineTotalGross: 200 }],
  delivery, itemsTotalGross: 200, shippingGross: 20, totalGross: 220, currency: 'PLN' };
const quote = { id: 'quote-a', expiresAt: '2030-01-01T10:15:00Z', snapshot, purchaseToken: 'purchase-secret', accessExpiresAt: null };
const order = { id: 'order-a', orderNumber: 'TS-order-a', status: 'PENDING_PAYMENT', reservationExpiresAt: quote.expiresAt, snapshot };

describe('Składanie zamówienia', () => {
  let http: HttpTestingController;
  let api: CheckoutApi;
  let auth: AccountService;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    api = TestBed.inject(CheckoutApi);
    auth = TestBed.inject(AccountService);
    harness = await RouterTestingHarness.create();
  });
  afterEach(() => {
    if (auth.token()) auth.expire(auth.token()!);
    sessionStorage.clear();
    http.verify();
  });

  const page = () => harness.routeNativeElement!;
  function fill(name: string, value: string) {
    const el = page().querySelector<HTMLInputElement>('[name="' + name + '"]')!;
    el.value = value;
    el.dispatchEvent(new Event('input', { bubbles: true }));
  }
  async function settle() {
    // Let the HTTP promise chain update the signals before waiting for Angular's render.
    await new Promise(resolve => setTimeout(resolve, 0));
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  it('shows the guest quote before ordering and requires a new confirmation after a price change', async () => {
    sessionStorage.setItem('tireshop.guest-cart-token', 'cart-secret');
    await harness.navigateByUrl('/zakup');
    page().querySelector('button')!.click();
    await settle();
    fill('email', delivery.email);
    fill('phone', delivery.phone);
    for (const [name, value] of Object.entries(address)) if (value) fill(name, value);
    page().querySelector('form')!.requestSubmit();
    const preview = http.expectOne(guest + '/preview');
    expect(preview.request.headers.get('X-Cart-Token')).toBe('cart-secret');
    expect(preview.request.body).toEqual(delivery);
    preview.flush(quote);
    await settle();
    expect(page().textContent).toContain('220,00');
    expect(page().textContent).toContain('Warszawa');
    http.expectNone(guest);
    const pay = page().querySelector<HTMLButtonElement>('button.primary')!;
    pay.click(); pay.click();
    const req = http.expectOne(guest);
    expect(req.request.headers.get('X-Purchase-Token')).toBe('purchase-secret');
    expect(req.request.headers.has('X-Cart-Token')).toBe(false);
    expect(Object.keys(req.request.body).sort()).toEqual(['checkoutKey', 'quoteId']);
    req.flush({ code: 'CHECKOUT_CHANGED' }, { status: 409, statusText: 'Conflict' });
    await settle();
    expect(page().textContent).toContain('Pobierz nowe podsumowanie');
    page().querySelector('form')!.requestSubmit();
    http.expectOne(guest + '/preview').flush({ ...quote, id: 'quote-b', snapshot: { ...snapshot, totalGross: 230 } });
    await settle();
    expect(page().textContent).toContain('230,00');
    http.expectNone(guest);
  });

  it('restores a submitted guest checkout, retries with the same key and opens the order with its purchase token', async () => {
    sessionStorage.setItem('tireshop.guest-cart-token', 'cart-secret');
    const preview = api.preview('guest', delivery);
    http.expectOne(guest + '/preview').flush(quote);
    await preview;
    const submit = api.confirm('guest', quote.id);
    const first = http.expectOne(guest);
    const body = first.request.body;
    first.error(new ProgressEvent('error'));
    await expect(submit).rejects.toBeTruthy();

    await harness.navigateByUrl('/zakup');
    expect(page().textContent).toContain('Ponów potwierdzenie');
    page().querySelector('button')!.click();
    const retry = http.expectOne(guest);
    expect(retry.request.body).toEqual(body);
    expect(retry.request.headers.get('X-Purchase-Token')).toBe('purchase-secret');
    retry.flush(order);
    await settle();
    http.expectOne('/api/guest/cart').flush({ items: [], totalQuantity: 0 });
    const receipt = http.expectOne(guest + '/' + order.id);
    expect(receipt.request.headers.get('X-Purchase-Token')).toBe('purchase-secret');
    receipt.flush(order);
    await settle();
    http.expectOne(guest + '/' + order.id + '/payments?page=0&size=1').flush({ items: [] });
    await settle();
    http.expectOne({ method: 'POST', url: guest + '/' + order.id + '/payments' })
      .flush({ id: 'payment-a', orderId: order.id, amountGross: 220, status: 'PENDING' });
    await settle();
    expect(page().textContent).toContain('Symulator płatności');
    expect(page().textContent).toContain(order.orderNumber);
    expect(api.attempt('guest')).toBeNull();
  });

  async function login() {
    const result = auth.login(delivery.email, 'test-password');
    http.expectOne('/api/auth/login').flush({ accessToken: 'account-test', expiresIn: 900 });
    await Promise.resolve();
    http.expectOne('/api/account/me').flush({ id: 'customer', role: 'CUSTOMER', email: delivery.email, phone: delivery.phone });
    await result;
  }

  it('uses the customer address and JWT, accepts a one-order address edit and excludes guest credentials', async () => {
    await login();
    await harness.navigateByUrl('/zakup');
    http.expectOne('/api/account/me/address').flush(address);
    await settle();
    expect(page().querySelector<HTMLInputElement>('[name=city]')!.value).toBe('Warszawa');
    fill('city', 'Gdynia');
    page().querySelector('form')!.requestSubmit();
    const preview = http.expectOne('/api/orders/preview');
    expect(preview.request.body.address.city).toBe('Gdynia');
    expect(preview.request.headers.get('Authorization')).toBe('Bearer account-test');
    expect(preview.request.headers.has('X-Cart-Token')).toBe(false);
    preview.flush({ ...quote, purchaseToken: null, snapshot: { ...snapshot, delivery: { ...delivery, address: { ...address, city: 'Gdynia' } } } });
    await settle();
    expect(page().textContent).toContain('Gdynia');
    page().querySelector<HTMLButtonElement>('button.primary')!.click();
    const submit = http.expectOne('/api/orders');
    expect(submit.request.headers.get('Authorization')).toBe('Bearer account-test');
    expect(submit.request.headers.has('X-Purchase-Token')).toBe(false);
    submit.flush({ code: 'QUOTE_EXPIRED' }, { status: 409, statusText: 'Conflict' });
    await settle();
    expect(page().textContent).toContain('Podsumowanie wygasło');
    http.expectNone(req => req.method === 'PUT');
  });

  it('opens the address form for a customer without a saved address', async () => {
    await login();
    await harness.navigateByUrl('/zakup');
    http.expectOne('/api/account/me/address').flush({ code: 'ADDRESS_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await settle();
    expect(page().querySelector<HTMLInputElement>('[name=city]')!.value).toBe('');
    expect(page().querySelector<HTMLInputElement>('[name=email]')!.value).toBe(delivery.email);
    http.expectNone('/api/orders/preview');
  });
});
