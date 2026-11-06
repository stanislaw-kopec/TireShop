import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AccountService, Address, bearer } from '../account/account';
import type { PublicTire } from '../catalog/catalog-api';
import { guestCartToken } from '../cart/shopping-cart';

export interface Delivery { email: string; phone: string; address: Address }
interface Snapshot {
  items: { product: Omit<PublicTire, 'priceGross' | 'availableQuantity'>; quantity: number; unitPriceGross: number; lineTotalGross: number }[];
  delivery: Delivery;
  itemsTotalGross: number;
  shippingGross: number;
  totalGross: number;
  currency: string;
}
export interface Quote { id: string; expiresAt: string; snapshot: Snapshot; purchaseToken: string | null; accessExpiresAt: string | null }
export interface Order {
  id: string; orderNumber: string; status: 'PENDING_PAYMENT' | 'CANCELLED' | 'PAID' | 'PROCESSING' | 'SHIPPED' | 'DELIVERED';
  reservationExpiresAt: string; snapshot: Snapshot; createdAt: string;
  trackingNumber: string | null; cancellationReason: 'CUSTOMER' | 'EXPIRED' | null;
}
export type OrderSummary = Pick<Order, 'id' | 'orderNumber' | 'status' | 'createdAt' | 'trackingNumber'>
  & { totalGross: number; currency: string };
interface OrderList { items: OrderSummary[]; page: number; totalPages: number }
export interface Attempt {
  quoteId: string; checkoutKey: string; purchaseToken: string | null; submitted: boolean; orderId?: string;
}

export const orderStatuses: Record<Order['status'], string> = {
  PENDING_PAYMENT: 'Oczekuje na płatność', CANCELLED: 'Anulowane', PAID: 'Opłacone',
  PROCESSING: 'W realizacji', SHIPPED: 'Wysłane', DELIVERED: 'Doręczone',
};

const base = (owner: string) => owner === 'guest' ? '/api/guest/orders' : '/api/orders';
const attemptKey = (owner: string) => 'tireshop.checkout.' + owner;
const purchaseKey = (orderId: string) => 'tireshop.purchase.' + orderId;

export function checkoutError(error: unknown) {
  if (error instanceof HttpErrorResponse) {
    switch (error.error?.code) {
      case 'CHECKOUT_CHANGED': return 'Koszyk lub cena uległy zmianie. Pobierz nowe podsumowanie i sprawdź kwotę.';
      case 'QUOTE_EXPIRED': return 'Podsumowanie wygasło. Pobierz nowe, aby sprawdzić aktualną cenę.';
      case 'EMPTY_CART': return 'Koszyk jest pusty. Wróć do katalogu, aby wybrać opony.';
      case 'CART_PRODUCT_UNAVAILABLE': case 'INSUFFICIENT_STOCK': return 'Wybrana liczba opon jest już niedostępna. Popraw ilości w koszyku.';
      case 'CART_NOT_FOUND': return 'Koszyk gościa wygasł. Wróć do koszyka, aby zacząć ponownie.';
      case 'ORDER_NOT_FOUND': return 'Brak dostępu do tego zakupu lub upłynął termin dostępu.';
      case 'ORDER_NOT_CANCELLABLE': return 'Status zamówienia zmienił się. Anulowanie jest możliwe tylko przed zapłatą.';
    }
    if (error.status === 401) return 'Zaloguj się ponownie na konto użyte przy zakupie.';
    if (error.status === 403) return 'Brak uprawnień do tego zakupu.';
    if (error.status === 400) return 'Sprawdź dane kontaktowe i adres dostawy.';
    if (error.status === 409) return 'Nie można zatwierdzić tego podsumowania. Sprawdź ponownie koszyk.';
  }
  return 'Nie udało się potwierdzić wyniku. Spróbuj ponownie.';
}

@Injectable({ providedIn: 'root' })
export class CheckoutApi {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AccountService);

  history(accountId: string, page: number) {
    const headers = this.accountHeaders(accountId);
    return this.http.get<OrderList>('/api/orders', { headers, params: { page, size: 20 } });
  }

  cancel(owner: string, id: string) {
    const { url, headers } = this.orderResource(owner, id);
    return firstValueFrom(this.http.post<Order>(url + '/cancellation', null, { headers }));
  }

  private headers(owner: string, guestHeader: string, guestToken: string | null): Record<string, string> {
    if (owner === 'guest') {
      if (!guestToken) throw new HttpErrorResponse({ status: 404, error: { code: guestHeader === 'X-Cart-Token' ? 'CART_NOT_FOUND' : 'ORDER_NOT_FOUND' } });
      return { [guestHeader]: guestToken };
    }
    return this.accountHeaders(owner);
  }

  private accountHeaders(owner: string) {
    const jwt = this.auth.token();
    if (!jwt || owner !== this.auth.profile()?.id) throw new HttpErrorResponse({ status: 401 });
    return bearer(jwt);
  }

  attempt(owner: string): Attempt | null {
    try {
      const raw = sessionStorage.getItem(attemptKey(owner));
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  }

  async preview(owner: string, delivery: Delivery) {
    const headers = this.headers(owner, 'X-Cart-Token', owner === 'guest' ? guestCartToken() : null);
    const quote = await firstValueFrom(this.http.post<Quote>(base(owner) + '/preview', delivery, { headers }));
    if (this.attempt(owner)?.submitted) throw new Error('Checkout already submitted');
    const attempt: Attempt = { quoteId: quote.id, checkoutKey: crypto.randomUUID(), purchaseToken: quote.purchaseToken, submitted: false };
    sessionStorage.setItem(attemptKey(owner), JSON.stringify(attempt));
    return quote;
  }

  async confirm(owner: string, quoteId: string) {
    const attempt = this.attempt(owner);
    if (!attempt || attempt.quoteId !== quoteId) throw new Error('Checkout changed');
    const headers = this.headers(owner, 'X-Purchase-Token', attempt.purchaseToken);
    attempt.submitted = true;
    sessionStorage.setItem(attemptKey(owner), JSON.stringify(attempt));
    let order: Order;
    try {
      order = await firstValueFrom(this.http.post<Order>(base(owner), {
        quoteId, checkoutKey: attempt.checkoutKey,
      }, { headers }));
    } catch (error) {
      // Network failures and 5xx may arrive after commit. Keep the original proof and key.
      if (error instanceof HttpErrorResponse && [400, 404, 409].includes(error.status)) this.forget(owner, quoteId);
      throw error;
    }
    if (attempt.purchaseToken) sessionStorage.setItem(purchaseKey(order.id), attempt.purchaseToken);
    sessionStorage.setItem(attemptKey(owner), JSON.stringify({ ...attempt, orderId: order.id }));
    return order;
  }

  orderResource(owner: string, id: string) {
    const token = owner === 'guest' ? sessionStorage.getItem(purchaseKey(id)) : null;
    const headers = this.headers(owner, 'X-Purchase-Token', token);
    return { url: base(owner) + '/' + encodeURIComponent(id), headers };
  }

  async order(owner: string, id: string) {
    const { url, headers } = this.orderResource(owner, id);
    const order = await firstValueFrom(this.http.get<Order>(url, { headers }));
    const attempt = this.attempt(owner);
    if (attempt?.orderId === id) this.forget(owner, attempt.quoteId);
    return order;
  }

  private forget(owner: string, quoteId: string) {
    if (this.attempt(owner)?.quoteId === quoteId) sessionStorage.removeItem(attemptKey(owner));
  }
}
