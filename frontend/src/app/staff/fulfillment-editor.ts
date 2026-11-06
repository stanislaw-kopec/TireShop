import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Order, orderStatuses } from '../checkout/checkout-api';
import { FormAction, StaffApi } from './staff-api';

@Component({
  selector: 'app-fulfillment-editor', imports: [FormsModule, DatePipe],
  styleUrls: ['../account/forms.css', './staff.css'],
  template: `
    <button [disabled]="action.busy()" (click)="close.emit()">← Lista zamówień</button>
    @if (action.message()) { <p role="status">{{ action.message() }}</p> }
    @if (order(); as o) {
      <h2>{{ o.orderNumber }}</h2>
      <p>{{ statuses[o.status] }} · {{ o.createdAt | date:'dd.MM.yyyy, HH:mm' }}</p>
      @let d = o.snapshot.delivery;
      <h3>Adres i kontakt</h3><p>{{ d.address.recipientName }}<br>
        {{ d.address.street }} {{ d.address.buildingNumber }}@if (d.address.apartmentNumber) {/{{ d.address.apartmentNumber }}}<br>
        {{ d.address.postalCode }} {{ d.address.city }}, {{ d.address.countryCode }}<br>{{ d.email }} · {{ d.phone }}</p>
      <h3>Pozycje do wydania</h3><ul>@for (line of o.snapshot.items; track line.product.id) {
        <li>{{ line.product.manufacturer }} {{ line.product.model }} · {{ line.product.widthMm }}/{{ line.product.aspectRatio }} R{{ line.product.rimDiameterIn }} · {{ line.quantity }} szt.</li>
      }</ul>
      @if (o.trackingNumber) { <p>Numer przesyłki: {{ o.trackingNumber }}</p> }
      @if (pending()) {
        <p>Poprzednia zmiana wymaga potwierdzenia.</p>
        <button [disabled]="action.busy()" (click)="change('retry')">Ponów ten sam zapis</button>
      } @else {
        @if (o.status === 'PAID') {
          <button class="primary" [disabled]="action.busy()" (click)="change('PROCESSING')">Rozpocznij realizację</button>
        }
        @if (o.status === 'PROCESSING' || o.status === 'SHIPPED') {
          <form ngNativeValidate (ngSubmit)="change(o.status === 'PROCESSING' ? 'SHIPPED' : 'shipment')">
            <fieldset [disabled]="action.busy()"><legend>Przesyłka</legend>
              <label>Numer przesyłki<input name="trackingNumber" required maxlength="100" [(ngModel)]="tracking"></label>
              <button class="primary">{{ o.status === 'PROCESSING' ? 'Oznacz jako wysłane' : 'Popraw numer przesyłki' }}</button>
            </fieldset>
          </form>
        }
        @if (o.status === 'SHIPPED') {
          <button [disabled]="action.busy()" (click)="change('DELIVERED')">Oznacz jako doręczone</button>
        }
      }
    }
    <p><button type="button" [disabled]="action.busy()" (click)="refresh()">Odśwież zamówienie</button></p>
  `,
})
export class FulfillmentEditor implements OnInit {
  readonly id = input.required<string>();
  readonly close = output<void>();
  private readonly api = inject(StaffApi);
  protected readonly action = new FormAction();
  protected readonly order = signal<Order | null>(null);
  protected readonly statuses = orderStatuses;
  protected tracking = '';

  ngOnInit() { this.refresh(); }
  protected refresh() { void this.action.run(() => this.load()); }
  private async load() {
    const order = await this.api.request<Order>('GET', 'employee/orders/' + this.id());
    this.order.set(order);
    this.tracking = order.trackingNumber ?? '';
  }
  protected pending() { return this.api.pending(`order/${this.id()}`); }

  protected change(next: 'PROCESSING' | 'SHIPPED' | 'DELIVERED' | 'shipment' | 'retry') {
    const order = this.order()!;
    return this.action.run(async () => {
      if (next === 'retry') await this.api.apply(`order/${order.id}`);
      else if (next === 'shipment') {
        await this.api.apply(`order/${order.id}`, { expectedTrackingNumber: order.trackingNumber!, trackingNumber: this.tracking.trim() });
      } else {
        const body: Record<string, string> = { status: next };
        if (next === 'SHIPPED') body['trackingNumber'] = this.tracking.trim();
        await this.api.apply(`order/${order.id}`, body);
      }
      await this.load();
    }, 'Zapisano zmianę zamówienia.');
  }
}
