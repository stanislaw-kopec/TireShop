import { Location } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { Subject, catchError, combineLatest, map, of, startWith, switchMap } from 'rxjs';
import { Order, orderStatuses } from '../checkout/checkout-api';

interface Tracking { orderNumber: string; status: Order['status']; trackingNumber: string | null }

@Component({
  template: `
    <h1>Śledzenie zamówienia</h1>
    @let state = result();
    @if (state?.data; as order) {
      <section><h2>{{ statuses[order.status] }}</h2>
        <p>Numer zamówienia: {{ order.orderNumber }}</p>
        @if (order.trackingNumber) { <p>Numer przesyłki: {{ order.trackingNumber }}</p> }
      </section>
      <button (click)="refresh.next()">Odśwież status</button>
    } @else if (state?.error) {
      <p role="alert">{{ state?.error }}</p>
      @if (state?.retry) { <button (click)="refresh.next()">Spróbuj ponownie</button> }
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
      const invalid = { data: null, retry: false, error: 'Link jest niepełny lub wygasł. Otwórz link z potwierdzenia lub wiadomości e-mail.' };
      if (!token || !/^[A-Za-z0-9_-]{43}$/.test(token)) return of(invalid);
      return this.http.get<Tracking>('/api/order-tracking', { headers: { 'X-Tracking-Token': token } }).pipe(
        map(data => ({ data, retry: false, error: '' })),
        startWith({ data: null, retry: false, error: '' }),
        catchError(error => of(error instanceof HttpErrorResponse && error.status === 404 ? invalid
          : { data: null, retry: true, error: 'Nie udało się pobrać statusu zamówienia.' })),
      );
    }),
  ));
}
