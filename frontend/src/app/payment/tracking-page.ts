import { Location } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { Subject, catchError, combineLatest, map, of, startWith, switchMap } from 'rxjs';
import { Order, orderStatuses } from '../checkout/checkout-api';
import { RenewTracking } from './renew-tracking';

interface Tracking { orderNumber: string; status: Order['status']; trackingNumber: string | null }

@Component({
  imports: [RenewTracking],
  template: `
    <h1>Śledzenie zamówienia</h1>
    @let state = result();
    @if (state?.data; as order) {
      <section><h2>{{ statuses[order.status] }}</h2>
        <p>Numer zamówienia: {{ order.orderNumber }}</p>
        @if (order.trackingNumber) { <p>Numer przesyłki: {{ order.trackingNumber }}</p> }
      </section>
      <button (click)="refresh.next()">Odśwież status</button>
      <details><summary>Potrzebujesz nowego linku?</summary><app-renew-tracking [orderNumber]="order.orderNumber" /></details>
    } @else if (state?.error) {
      <p role="alert">{{ state?.error }}</p>
      @if (state?.retry) { <button (click)="refresh.next()">Spróbuj ponownie</button> }
      <app-renew-tracking />
    } @else if (state?.renew) {
      <app-renew-tracking />
    } @else { <p role="status">Pobieranie statusu…</p> }
  `,
  styleUrls: ['../account/forms.css', '../checkout/checkout.css'],
})
export class TrackingPage {
  private readonly route = inject(ActivatedRoute);
  private readonly location = inject(Location);
  private readonly http = inject(HttpClient);
  protected readonly statuses = orderStatuses;
  protected readonly refresh = new Subject<void>();
  private readonly token = this.route.fragment.pipe(map(fragment => {
    const token = new URLSearchParams(fragment ?? '').get('token');
    if (fragment) this.location.replaceState('/track-order');
    return token;
  }));
  protected readonly result = toSignal(combineLatest([this.token, this.refresh.pipe(startWith(undefined))]).pipe(
    switchMap(([token]) => {
      const invalid = { data: null, retry: false, renew: true, error: 'Link jest niepełny lub wygasł. Możesz poprosić o nowy.' };
      if (token === null) return of({ ...invalid, error: '' });
      if (!token || !/^[A-Za-z0-9_-]{43}$/.test(token)) return of(invalid);
      return this.http.get<Tracking>('/api/order-tracking', { headers: { 'X-Tracking-Token': token } }).pipe(
        map(data => ({ data, retry: false, renew: false, error: '' })),
        startWith({ data: null, retry: false, renew: false, error: '' }),
        catchError(error => of(error instanceof HttpErrorResponse && error.status === 404 ? invalid
          : { data: null, retry: true, renew: true, error: 'Nie udało się pobrać statusu zamówienia.' })),
      );
    }),
  ));
}
