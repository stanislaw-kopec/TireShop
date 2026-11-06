import { HttpClient } from '@angular/common/http';
import { Component, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Subject, catchError, combineLatest, defer, map, of, startWith, switchMap } from 'rxjs';
import { CheckoutApi } from '../checkout/checkout-api';

@Component({
  selector: 'app-tracking-link',
  template: `
    @if (result(); as state) {
      @if (state.url) { <p><a [href]="state.url" referrerpolicy="no-referrer">Śledź zamówienie</a></p> }
      @else if (state.failed) {
        <p role="alert">Nie udało się pobrać linku do śledzenia.</p>
        <button (click)="retry.next()">Pobierz link ponownie</button>
      } @else { <p role="status">Pobieranie linku do śledzenia…</p> }
    }
  `,
})
export class TrackingLink {
  readonly orderId = input.required<string>();
  private readonly api = inject(CheckoutApi);
  private readonly http = inject(HttpClient);
  protected readonly retry = new Subject<void>();
  protected readonly result = toSignal(combineLatest([toObservable(this.orderId), this.retry.pipe(startWith(undefined))]).pipe(
    switchMap(([id]) => defer(() => {
      const { url, headers } = this.api.orderResource('guest', id);
      return this.http.get<{ trackingUrl: string }>(url + '/tracking-link', { headers });
    }).pipe(map(link => ({ url: link.trackingUrl, failed: false })), startWith({ url: '', failed: false }),
      catchError(() => of({ url: '', failed: true })))),
  ));
}
