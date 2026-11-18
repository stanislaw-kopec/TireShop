import { DatePipe } from '@angular/common';
import { Component, OnChanges, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, defer, filter, map, of, startWith, switchMap } from 'rxjs';
import { priceFormat } from '../catalog/catalog-api';
import { PaymentApi, paymentError } from './payment-api';

@Component({
  selector: 'app-payment-history', imports: [DatePipe],
  templateUrl: './payment-history.html', styleUrl: '../shared/history.css',
})
export class PaymentHistory implements OnChanges {
  readonly orderId = input.required<string>();
  readonly owner = input.required<string>();
  private readonly api = inject(PaymentApi);
  protected readonly money = priceFormat;
  protected readonly statuses = { PENDING: 'Oczekuje na wynik', SUCCEEDED: 'Udana', FAILED: 'Odrzucona', CANCELLED: 'Anulowana' };
  protected readonly reasons: Partial<Record<string, string>> = {
    EXPIRED: 'Wygaśnięcie rezerwacji', ORDER_CANCELLED: 'Anulowanie zamówienia',
    SIMULATED_FAILED: 'Odrzucenie w symulatorze', SIMULATED_CANCELLED: 'Anulowanie w symulatorze',
    SIMULATED_SUCCEEDED: 'Potwierdzenie w symulatorze',
  };
  private readonly query = signal({ owner: '', orderId: '', page: 0, revision: 0 });
  protected readonly result = toSignal(toObservable(this.query).pipe(
    filter(q => !!q.owner && !!q.orderId),
    switchMap(q => defer(() => this.api.history(q.owner, q.orderId, q.page)).pipe(
      map(page => ({ page, loading: false, error: '' })),
      startWith({ page: null, loading: true, error: '' }),
      catchError(error => of({ page: null, loading: false, error: paymentError(error) })),
    )),
  ));

  ngOnChanges() { this.query.set({ owner: this.owner(), orderId: this.orderId(), page: 0, revision: 0 }); }
  protected go(page: number) { this.query.update(q => ({ ...q, page })); }
  protected refresh() { this.query.update(q => ({ ...q, revision: q.revision + 1 })); }
}
