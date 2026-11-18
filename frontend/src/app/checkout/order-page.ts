import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, catchError, combineLatest, defer, map, of, startWith, switchMap } from 'rxjs';
import { AccountService } from '../account/account';
import { priceFormat } from '../catalog/catalog-api';
import { CheckoutApi, Order, orderStatuses, checkoutError } from './checkout-api';
import { TrackingLink } from '../payment/tracking-link';
import { PaymentHistory } from '../payment/payment-history';

@Component({
  imports: [RouterLink, DatePipe, TrackingLink, PaymentHistory],
  template: `
    @if (route.snapshot.data['guest']) { <a class="back" routerLink="/">← Wróć do katalogu</a> }
    @else { <a class="back" routerLink="/konto/zamowienia">← Historia zamówień</a> }
    <h1>Zamówienie</h1>
    @let state = result();
    @if (state?.kind === 'login') { <a routerLink="/logowanie" [queryParams]="{ zamowienie: route.snapshot.paramMap.get('id') }">Zaloguj się na konto użyte przy zakupie</a> }
    @else if (state?.kind === 'error') { <p role="alert">{{ state?.message }}</p><button (click)="refresh.next()">Spróbuj ponownie</button> }
    @else if (state?.order; as order) {
      <section><h2>{{ statuses[order.status] }}</h2><p>Numer zamówienia <strong>{{ order.orderNumber }}</strong></p>
        @if (order.createdAt) { <p>Data zamówienia: {{ order.createdAt | date:'dd.MM.yyyy, HH:mm' }}</p> }
        @if (order.trackingNumber) { <p>Numer przesyłki: <strong>{{ order.trackingNumber }}</strong></p> }
        <p>Kwota brutto: {{ money.format(order.snapshot.totalGross) }}</p>
        @if (order.status === 'PENDING_PAYMENT') {
          <p>Termin rezerwacji: {{ order.reservationExpiresAt | date:'dd.MM.yyyy, HH:mm:ss' }}.</p>
          @if (!cancelling()) { <p><a [routerLink]="['platnosc']">Przejdź do symulatora płatności</a></p> }
          <button [disabled]="cancelling()" (click)="cancel(order)">Anuluj zamówienie</button>
        } @else if (order.status === 'CANCELLED') {
          @if (order.cancellationReason === 'EXPIRED') { <p>Termin rezerwacji upłynął.</p> }
          @else if (order.cancellationReason === 'CUSTOMER') { <p>Zamówienie anulowano na życzenie kupującego.</p> }
        } @else {
          <p>Płatność potwierdzona.</p>
          @if (route.snapshot.data['guest']) { <app-tracking-link [orderId]="order.id" /> }
          @else { <p><a routerLink="/konto/zamowienia">Zobacz swoje zamówienia</a></p> }
        }
        <button [disabled]="cancelling()" (click)="refresh.next()">Odśwież status</button>
        @if (notice()?.id === order.id) { <p role="status">{{ notice()?.text }}</p> }
      </section>
      @if (order.snapshot.items.length) {
        <section><h2>Zamówione opony</h2><ul>
          @for (line of order.snapshot.items; track line.product.id) {
            <li><strong>{{ line.product.manufacturer }} {{ line.product.model }}</strong>
              <div>{{ line.product.widthMm }}/{{ line.product.aspectRatio }} R{{ line.product.rimDiameterIn }}</div>
              <div>{{ line.quantity }} szt. × {{ money.format(line.unitPriceGross) }} = {{ money.format(line.lineTotalGross) }}</div>
            </li>
          }
        </ul><p>Dostawa: {{ money.format(order.snapshot.shippingGross) }}</p></section>
      }
      @if (order.snapshot.delivery; as delivery) {
        @let a = delivery.address;
        <section><h2>Adres dostawy</h2><p>{{ a.recipientName }}<br>{{ a.street }} {{ a.buildingNumber }}@if (a.apartmentNumber) {/{{ a.apartmentNumber }}}<br>
          {{ a.postalCode }} {{ a.city }}, {{ a.countryCode }}</p></section>
      }
      <button (click)="showHistory.set(!showHistory())" [attr.aria-expanded]="showHistory()">
        {{ showHistory() ? 'Ukryj historię płatności' : 'Pokaż historię płatności' }}
      </button>
      @if (showHistory()) { <app-payment-history [orderId]="order.id" [owner]="owner()" /> }
    } @else { <p role="status">Pobieranie zamówienia…</p> }
  `,
  styleUrls: ['../account/forms.css', './checkout.css'],
})
export class OrderPage {
  private readonly api = inject(CheckoutApi);
  private readonly life = inject(DestroyRef);
  private readonly auth = inject(AccountService);
  protected readonly route = inject(ActivatedRoute);
  protected readonly money = priceFormat;
  protected readonly statuses = orderStatuses;
  protected readonly refresh = new Subject<void>();
  protected readonly cancelling = signal(false);
  protected readonly showHistory = signal(false);
  protected readonly notice = signal<{ id: string; text: string } | null>(null);
  protected owner() { return this.route.snapshot.data['guest'] ? 'guest' : this.auth.profile()?.id ?? ''; }

  protected async cancel(order: Order) {
    const owner = this.route.snapshot.data['guest'] ? 'guest' : this.auth.profile()?.id;
    if (!owner || this.cancelling() || order.status !== 'PENDING_PAYMENT') return;
    this.cancelling.set(true);
    this.notice.set(null);
    try {
      await this.api.cancel(owner, order.id);
    } catch (error) {
      this.notice.set({ id: order.id, text: checkoutError(error) });
    } finally {
      this.cancelling.set(false);
      if (!this.life.destroyed && this.route.snapshot.paramMap.get('id') === order.id) this.refresh.next();
    }
  }
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
