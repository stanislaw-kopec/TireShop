import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CheckoutApi, checkoutError } from '../checkout/checkout-api';

export type Outcome = 'SUCCEEDED' | 'FAILED' | 'CANCELLED';
export interface Payment {
  id: string;
  orderId: string;
  amountGross: number;
  currency: 'PLN';
  status: 'PENDING' | Outcome;
}

export function paymentError(error: unknown) {
  if (error instanceof HttpErrorResponse) {
    switch (error.error?.code) {
      case 'ORDER_NOT_PAYABLE': case 'PAYMENT_EXPIRED': return 'Zamówienie nie oczekuje już na płatność. Sprawdź jego aktualny status.';
      case 'PAYMENT_RESULT_CONFLICT': return 'Ta próba ma już zapisany wynik. Sprawdź status płatności.';
      case 'PAYMENT_AMOUNT_MISMATCH': return 'Kwota próby różni się od kwoty zamówienia.';
      case 'PAYMENT_NOT_FOUND': return 'Nie znaleziono tej próby płatności.';
    }
  }
  return checkoutError(error);
}

@Injectable({ providedIn: 'root' })
export class PaymentApi {
  private readonly http = inject(HttpClient);
  private readonly checkout = inject(CheckoutApi);

  private key(owner: string, id: string) { return 'tireshop.payment.' + owner + '.' + id; }

  async latest(owner: string, id: string) {
    const { url, headers } = this.checkout.orderResource(owner, id);
    const page = await firstValueFrom(this.http.get<{ items: Payment[] }>(url + '/payments', {
      headers, params: { page: 0, size: 1 },
    }));
    return page.items[0] ?? null;
  }

  async resume(owner: string, id: string) {
    if (sessionStorage.getItem(this.key(owner, id))) return this.start(owner, id);
    return await this.latest(owner, id) ?? await this.start(owner, id);
  }

  async start(owner: string, id: string): Promise<Payment> {
    const { url, headers } = this.checkout.orderResource(owner, id);
    const key = this.key(owner, id);
    const requestKey = sessionStorage.getItem(key) ?? crypto.randomUUID();
    sessionStorage.setItem(key, requestKey);
    try {
      const payment = await firstValueFrom(this.http.post<Payment>(url + '/payments', { requestKey }, { headers }));
      sessionStorage.removeItem(key);
      return payment;
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.error?.code === 'PAYMENT_PENDING') {
        // Another tab started the attempt first. Resume that server-side attempt.
        const existing = await this.latest(owner, id);
        if (existing) {
          sessionStorage.removeItem(key);
          return existing;
        }
      }
      throw error;
    }
  }

  result(owner: string, id: string, paymentId: string, outcome: Outcome) {
    const { url, headers } = this.checkout.orderResource(owner, id);
    return firstValueFrom(this.http.post<Payment>(url + '/payments/' + encodeURIComponent(paymentId) + '/simulation-result',
      { outcome }, { headers }));
  }
}
