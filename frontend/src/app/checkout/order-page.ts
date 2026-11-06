import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, catchError, combineLatest, defer, map, of, startWith, switchMap } from 'rxjs';
import { AccountService } from '../account/account';
import { priceFormat } from '../catalog/catalog-api';
import { CheckoutApi, Order, checkoutError } from './checkout-api';

@Component({
  imports: [RouterLink, DatePipe],
  template: `
    <a class="back" routerLink="/">← Wróć do katalogu</a>
    <h1>Zamówienie</h1>
    @let state = result();
    @if (state?.kind === 'login') { <a routerLink="/logowanie" [queryParams]="{ zamowienie: route.snapshot.paramMap.get('id') }">Zaloguj się na konto użyte przy zakupie</a> }
    @else if (state?.kind === 'error') { <p role="alert">{{ state?.message }}</p><button (click)="refresh.next()">Spróbuj ponownie</button> }
    @else if (state?.order; as order) {
      <section><h2>{{ statuses[order.status] }}</h2><p>Numer zamówienia <strong>{{ order.orderNumber }}</strong></p>
        <p>Kwota brutto: {{ money.format(order.snapshot.totalGross) }}</p>
        @if (order.status === 'PENDING_PAYMENT') {
          <p>Termin rezerwacji: {{ order.reservationExpiresAt | date:'dd.MM.yyyy, HH:mm:ss' }}.</p>
        }
        <button (click)="refresh.next()">Odśwież status</button>
      </section>
    } @else { <p role="status">Pobieranie zamówienia…</p> }
  `,
  styleUrls: ['../account/forms.css', './checkout.css'],
})
export class OrderPage {
  private readonly api = inject(CheckoutApi);
  private readonly auth = inject(AccountService);
  protected readonly route = inject(ActivatedRoute);
  protected readonly money = priceFormat;
  protected readonly statuses: Record<Order['status'], string> = {
    PENDING_PAYMENT: 'Oczekuje na płatność', CANCELLED: 'Anulowane', PAID: 'Opłacone',
    PROCESSING: 'W realizacji', SHIPPED: 'Wysłane', DELIVERED: 'Doręczone',
  };
  protected readonly refresh = new Subject<void>();
  protected readonly result = toSignal(combineLatest([
    this.route.paramMap, toObservable(this.auth.profile), this.refresh.pipe(startWith(undefined)),
  ]).pipe(switchMap(([params, profile]) => {
    const owner = this.route.snapshot.data['guest'] ? 'guest' : profile?.id;
    if (!owner) return of({ kind: 'login', order: null, message: '' });
    return defer(() => this.api.order(owner, params.get('id')!)).pipe(
      map(order => ({ kind: 'ready', order, message: '' })),
      startWith({ kind: 'loading', order: null, message: '' }),
      catchError(error => of({ kind: 'error', order: null, message: checkoutError(error) })),
    );
  })));
}
