import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AccountService } from '../account/account';
import { Order, OrderSummary, orderStatuses } from '../checkout/checkout-api';
import { priceFormat } from '../catalog/catalog-api';
import { AdminAccounts } from './admin-accounts';
import { FulfillmentEditor } from './fulfillment-editor';
import { FormAction, Page, StaffApi } from './staff-api';
import { StaffTire, TireEditor } from './tire-editor';

type Tab = 'tires' | 'orders' | 'accounts';

@Component({
  selector: 'app-staff-workspace', imports: [FormsModule, AdminAccounts, FulfillmentEditor, TireEditor],
  templateUrl: './staff-panel.html', styleUrls: ['../account/forms.css', './staff.css'],
})
class StaffWorkspace implements OnInit {
  private readonly api = inject(StaffApi);
  protected readonly employee = this.api.auth.profile()?.role === 'EMPLOYEE';
  protected readonly action = new FormAction();
  protected readonly tab = signal<Tab>('tires');
  protected readonly tires = signal<Page<StaffTire> | null>(null);
  protected readonly orders = signal<Page<OrderSummary> | null>(null);
  protected readonly editing = signal(false);
  protected readonly selected = signal<StaffTire | null>(null);
  protected readonly orderId = signal('');
  protected readonly statuses = orderStatuses;
  protected readonly filters = Object.entries(orderStatuses);
  protected readonly money = priceFormat;
  protected status: Order['status'] | '' = '';

  ngOnInit() { this.list(0); }
  protected show(tab: Tab) {
    this.tab.set(tab);
    this.editing.set(false);
    this.orderId.set('');
    this.action.message.set('');
    if (tab !== 'accounts') this.list(0);
  }

  protected list(page: number) {
    const tab = this.tab();
    this.editing.set(false);
    this.orderId.set('');
    this.tires.set(null);
    this.orders.set(null);
    void this.action.run(async () => {
      if (tab === 'tires') {
        this.tires.set(await this.api.request<Page<StaffTire>>('GET', 'staff/tires', undefined, { page, size: 20 }));
      } else if (tab === 'orders') {
        const params: Record<string, string | number> = { page, size: 20 };
        if (this.status) params['status'] = this.status;
        this.orders.set(await this.api.request<Page<OrderSummary>>('GET', 'employee/orders', undefined, params));
      }
    });
  }

  protected edit(tire: StaffTire | null) {
    this.selected.set(tire);
    this.editing.set(true);
  }
}

@Component({
  imports: [StaffWorkspace, RouterLink],
  template: `
    @if (auth.staff()) { <app-staff-workspace /> }
    @else {
      <h1>Panel personelu</h1>
      @if (auth.profile()) { <p>Ta strona jest dostępna dla pracowników i administratorów.</p> }
      @else { <a routerLink="/logowanie">Zaloguj się do panelu</a> }
    }
  `,
})
export class StaffPanel { protected readonly auth = inject(AccountService); }
