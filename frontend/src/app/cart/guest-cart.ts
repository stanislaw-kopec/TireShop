import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { PublicTire } from '../catalog/catalog-api';

interface CartLine {
  product: Omit<PublicTire, 'priceGross'> & { priceGross: number | null };
  quantity: number;
  lineTotalGross: number | null;
  issue: 'NONE' | 'NOT_FOR_SALE' | 'INSUFFICIENT_STOCK';
}

export interface Cart {
  items: CartLine[];
  totalQuantity: number;
  itemsTotalGross: number | null;
  shippingGross: number;
  totalGross: number | null;
  currency: 'PLN';
  readyForCheckout: boolean;
}

const url = '/api/guest/cart';
const storageKey = 'tireshop.guest-cart-token';
const headers = (token: string) => ({ 'X-Cart-Token': token });
const itemUrl = (id: string) => url + '/items/' + encodeURIComponent(id);

function validQuantity(value: number) {
  return Number.isInteger(value) && value > 0 && value <= 2147483647;
}

@Injectable({ providedIn: 'root' })
export class GuestCart {
  private readonly http = inject(HttpClient);
  readonly cart = signal<Cart | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');

  refresh() {
    return this.run(async () => {
      const token = sessionStorage.getItem(storageKey);
      this.cart.set(token ? await this.read(token) : null);
    });
  }

  add(id: string, quantity: number) {
    return this.run(async () => {
      if (!validQuantity(quantity)) throw new RangeError();
      let token = sessionStorage.getItem(storageKey);
      let current: Cart;
      if (token) {
        current = await this.read(token);
      } else {
        const created = await firstValueFrom(this.http.post<{ token: string; cart: Cart }>(url, null));
        token = created.token;
        sessionStorage.setItem(storageKey, token);
        current = created.cart;
      }
      this.cart.set(current);
      const previous = current.items.find(item => item.product.id === id)?.quantity ?? 0;
      await this.put(token, id, previous + quantity);
    });
  }

  setQuantity(id: string, quantity: number) {
    return this.run(() => this.put(this.requireToken(), id, quantity));
  }

  remove(id: string) {
    return this.run(async () => {
      const result = await firstValueFrom(this.http.delete<Cart>(itemUrl(id), { headers: headers(this.requireToken()) }));
      this.cart.set(result);
    });
  }

  private read(token: string) {
    return firstValueFrom(this.http.get<Cart>(url, { headers: headers(token) }));
  }

  private async put(token: string, id: string, quantity: number) {
    if (!validQuantity(quantity)) throw new RangeError();
    const result = await firstValueFrom(this.http.put<Cart>(itemUrl(id), { quantity }, { headers: headers(token) }));
    this.cart.set(result);
  }

  private requireToken() {
    const token = sessionStorage.getItem(storageKey);
    if (!token) throw new HttpErrorResponse({ status: 404, error: { code: 'CART_NOT_FOUND' } });
    return token;
  }

  private async run(operation: () => Promise<void>): Promise<boolean> {
    if (this.busy()) return false;
    this.busy.set(true);
    this.error.set('');
    try {
      await operation();
      return true;
    } catch (error) {
      this.report(error);
      // A rejected write may mean that the available stock changed while the page was open.
      if (error instanceof HttpErrorResponse && error.status === 409) {
        try {
          this.cart.set(await this.read(this.requireToken()));
        } catch (refreshError) {
          this.report(refreshError);
        }
      }
      return false;
    } finally {
      this.busy.set(false);
    }
  }

  private report(error: unknown) {
    const code = error instanceof HttpErrorResponse ? error.error?.code : undefined;
    if (code === 'CART_NOT_FOUND') {
      sessionStorage.removeItem(storageKey);
      this.cart.set(null);
      this.error.set('Poprzedni koszyk jest już niedostępny. Możesz rozpocząć zakupy od nowa.');
    } else if (code === 'CART_PRODUCT_UNAVAILABLE' || code === 'PRODUCT_NOT_FOUND') {
      this.error.set('Opona jest niedostępna w wybranej ilości. Sprawdź stan lub zmniejsz liczbę sztuk.');
    } else if (code === 'CART_SIZE_LIMIT') {
      this.error.set('Koszyk może zawierać najwyżej 100 różnych opon.');
    } else if (code === 'CART_QUANTITY_LIMIT' || error instanceof RangeError) {
      this.error.set('Podaj dodatnią, całkowitą liczbę sztuk w dozwolonym zakresie.');
    } else if (error instanceof DOMException) {
      this.error.set('Aby korzystać z koszyka, zezwól przeglądarce na przechowywanie danych w tej karcie.');
    } else {
      this.error.set('Nie udało się potwierdzić zawartości koszyka. Sprawdź połączenie i odśwież koszyk.');
    }
  }
}
