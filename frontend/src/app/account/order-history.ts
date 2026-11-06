import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, catchError, combineLatest, defer, map, of, startWith, switchMap } from 'rxjs';
import { priceFormat } from '../catalog/catalog-api';
import { CheckoutApi, checkoutError, orderStatuses } from '../checkout/checkout-api';
import { AccountService } from './account';

@Component({
  imports: [RouterLink, DatePipe],
  template: `
    <a class="back" routerLink="/konto">← Moje konto</a>
    <h1>Historia zamówień</h1>
    @let state = result();
    @if (!auth.profile()) {
      <a routerLink="/logowanie" [queryParams]="{ powrot: 'historia' }">Zaloguj się, aby zobaczyć swoje zamówienia</a>
    } @else if (state?.error) {
      <p role="alert">{{ state?.error }}</p><button (click)="refresh.next()">Spróbuj ponownie</button>
    } @else if (state?.data; as list) {
      @if (list.items.length) {
        <ul>@for (order of list.items; track order.id) {
          <li><a [routerLink]="['/zamowienia/konto', order.id]">{{ order.orderNumber }}</a>
            <p>{{ order.createdAt | date:'dd.MM.yyyy, HH:mm' }} · {{ statuses[order.status] }} · {{ money.format(order.totalGross) }}</p>
            @if (order.trackingNumber) { <p>Numer przesyłki: {{ order.trackingNumber }}</p> }
          </li>
        }</ul>
        @if (list.totalPages > 1) {
          <nav class="actions" aria-label="Strony zamówień">
            @if (list.page > 0) { <a [routerLink]="[]" [queryParams]="{ strona: list.page }">Poprzednia</a> }
            <span>Strona {{ list.page + 1 }} z {{ list.totalPages }}</span>
            @if (list.page + 1 < list.totalPages) { <a [routerLink]="[]" [queryParams]="{ strona: list.page + 2 }">Następna</a> }
          </nav>
        }
      } @else if (list.page > 0) {
        <p>Na tej stronie nie ma zamówień.</p><a routerLink="/konto/zamowienia">Pierwsza strona</a>
      } @else {
        <p>Nie masz jeszcze zamówień.</p><a routerLink="/">Przejdź do katalogu</a>
      }
    } @else { <p role="status">Pobieranie zamówień…</p> }
  `,
  styleUrls: ['./forms.css', '../checkout/checkout.css'],
  styles: 'li a { overflow-wrap: anywhere; }',
})
export class OrderHistory {
  protected readonly auth = inject(AccountService);
  private readonly api = inject(CheckoutApi);
  private readonly route = inject(ActivatedRoute);
  protected readonly refresh = new Subject<void>();
  protected readonly money = priceFormat;
  protected readonly statuses = orderStatuses;
  protected readonly result = toSignal(combineLatest([
    toObservable(this.auth.profile), this.route.queryParamMap, this.refresh.pipe(startWith(undefined)),
  ]).pipe(switchMap(([account, params]) => {
    const empty = { data: null, error: '' };
    if (!account) return of(empty);
    const page = Number(params.get('strona')) - 1;
    const index = Number.isInteger(page) && page >= 0 && page <= 1000000 ? page : 0;
    return defer(() => this.api.history(account.id, index)).pipe(map(data => ({ data, error: '' })),
      startWith(empty), catchError(error => of({ data: null, error: checkoutError(error) })));
  })));
}
