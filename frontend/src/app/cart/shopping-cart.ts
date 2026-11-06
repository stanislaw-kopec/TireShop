import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, linkedSignal, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AccountService, bearer } from '../account/account';
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

const storageKey = 'tireshop.guest-cart-token';
export const guestCartToken = () => sessionStorage.getItem(storageKey);
const guestUrl = '/api/guest/cart';
const customerUrl = '/api/cart';
type Owner = 'guest' | 'customer';
interface Target { url: string; headers: Record<string, string> }

function validQuantity(value: number) {
  return Number.isInteger(value) && value > 0 && value <= 2147483647;
}

@Injectable({ providedIn: 'root' })
export class ShoppingCart {
  private readonly http = inject(HttpClient);
  readonly auth = inject(AccountService);
  readonly owner = linkedSignal<Owner>(() => this.auth.token() ? 'customer' : 'guest');
  private readonly guest = signal<Cart | null>(null);
  private readonly customer = linkedSignal<string | null, Cart | null>({ source: this.auth.token, computation: () => null });
  readonly cart = computed(() => this.owner() === 'customer' ? this.customer() : this.guest());
  readonly count = computed(() => (this.auth.token() ? this.customer() : this.guest())?.totalQuantity);
  readonly mergeState = linkedSignal<string | null, 'none' | 'conflict' | 'retry'>({
    source: this.auth.token, computation: () => 'none',
  });
  readonly busy = linkedSignal<string | null, boolean>({ source: this.auth.token, computation: () => false });
  readonly error = linkedSignal<string | null, string>({ source: this.auth.token, computation: () => '' });
  readonly merged = linkedSignal<string | null, boolean>({ source: this.auth.token, computation: () => false });

  private target(owner: Owner): Target {
    if (owner === 'customer') {
      const token = this.auth.token();
      if (!token) throw new HttpErrorResponse({ status: 401 });
      return { url: customerUrl, headers: bearer(token) };
    }
    const token = sessionStorage.getItem(storageKey);
    if (!token) throw new HttpErrorResponse({ status: 404, error: { code: 'CART_NOT_FOUND' } });
    return { url: guestUrl, headers: { 'X-Cart-Token': token } };
  }

  private read(target: Target) {
    return firstValueFrom(this.http.get<Cart>(target.url, { headers: target.headers }));
  }

  private save(owner: Owner, cart: Cart | null, session: string | null) {
    if (session === this.auth.token()) (owner === 'customer' ? this.customer : this.guest).set(cart);
  }

  refresh() {
    const owner = this.owner();
    return this.run(async session => {
      const hasGuest = !!sessionStorage.getItem(storageKey);
      const cart = owner === 'guest' && !hasGuest ? null : await this.read(this.target(owner));
      this.save(owner, cart, session);
    });
  }

  async open() {
    if (this.auth.token() && sessionStorage.getItem(storageKey) && this.mergeState() === 'none') return this.merge();
    return this.refresh();
  }

  merge() {
    return this.run(async session => {
      if (!session) return;
      const guest = this.target('guest');
      const customer = this.target('customer');
      this.mergeState.set('retry');
      try {
        const merged = await firstValueFrom(this.http.post<Cart>(customerUrl + '/merge', null, {
          headers: { ...customer.headers, ...guest.headers },
        }));
        if (sessionStorage.getItem(storageKey) === guest.headers['X-Cart-Token']) {
          sessionStorage.removeItem(storageKey);
          this.guest.set(null);
        }
        if (session !== this.auth.token()) return;
        this.customer.set(merged);
        this.merged.set(true);
        this.owner.set('customer');
        this.mergeState.set('none');
      } catch (error) {
        if (session !== this.auth.token()) return;
        if (error instanceof HttpErrorResponse && error.status === 409) {
          const [accountCart, guestCart] = await Promise.all([this.read(customer), this.read(guest)]);
          if (session !== this.auth.token()) return;
          this.customer.set(accountCart);
          this.guest.set(guestCart);
          this.mergeState.set('conflict');
          return;
        }
        if (error instanceof HttpErrorResponse && error.error?.code === 'CART_NOT_FOUND') {
          sessionStorage.removeItem(storageKey);
          this.guest.set(null);
          this.mergeState.set('none');
          this.owner.set('customer');
          this.save('customer', await this.read(customer), session);
          if (session === this.auth.token()) this.error.set('Koszyk gościa wygasł lub został już połączony. Poniżej koszyk konta.');
          return;
        }
        throw error;
      }
    });
  }

  add(id: string, quantity: number) {
    const owner = this.auth.token() ? 'customer' : 'guest';
    return this.run(async session => {
      if (!validQuantity(quantity)) throw new RangeError();
      let current: Cart;
      if (owner === 'guest' && !sessionStorage.getItem(storageKey)) {
        const result = await firstValueFrom(this.http.post<{ token: string; cart: Cart }>(guestUrl, null));
        sessionStorage.setItem(storageKey, result.token);
        current = result.cart;
      } else {
        current = await this.read(this.target(owner));
      }
      if (session !== this.auth.token()) return;
      this.save(owner, current, session);
      const previous = current.items.find(line => line.product.id === id)?.quantity ?? 0;
      await this.change(owner, id, previous + quantity, session);
    });
  }

  setQuantity(id: string, quantity: number) {
    return this.run(session => this.change(this.owner(), id, quantity, session));
  }

  remove(id: string) {
    return this.run(session => this.change(this.owner(), id, null, session));
  }

  private async change(owner: Owner, id: string, quantity: number | null, session: string | null) {
    if (quantity !== null && !validQuantity(quantity)) throw new RangeError();
    const target = this.target(owner);
    const url = target.url + '/items/' + encodeURIComponent(id);
    try {
      const options = { headers: target.headers };
      const request = quantity === null ? this.http.delete<Cart>(url, options)
        : this.http.put<Cart>(url, { quantity }, options);
      this.save(owner, await firstValueFrom(request), session);
    } catch (error) {
      if (session === this.auth.token() && error instanceof HttpErrorResponse && error.status === 409) {
        this.save(owner, await this.read(target), session);
      }
      throw error;
    }
  }

  private async run(operation: (session: string | null) => Promise<void>) {
    if (this.busy()) return false;
    const session = this.auth.token();
    this.busy.set(true);
    this.error.set('');
    try {
      await operation(session);
      return session === this.auth.token();
    } catch (error) {
      if (session === this.auth.token()) this.report(error);
      return false;
    } finally {
      if (session === this.auth.token()) this.busy.set(false);
    }
  }

  private report(error: unknown) {
    const code = error instanceof HttpErrorResponse ? error.error?.code : undefined;
    if (code === 'CART_NOT_FOUND' && this.mergeState() !== 'retry') {
      sessionStorage.removeItem(storageKey);
      this.guest.set(null);
      this.owner.set(this.auth.token() ? 'customer' : 'guest');
      this.mergeState.set('none');
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
      this.error.set('Nie udało się potwierdzić zawartości koszyka. Sprawdź połączenie i spróbuj ponownie.');
    }
  }
}
