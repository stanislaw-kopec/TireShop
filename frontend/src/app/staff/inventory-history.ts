import { DatePipe } from '@angular/common';
import { Component, OnChanges, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, defer, filter, map, of, startWith, switchMap } from 'rxjs';
import { Page } from '../shared/page';
import { StaffApi, staffError } from './staff-api';

interface Movement {
  operationId: string; productId: string; actorAccountId: string | null; orderId: string | null;
  type: 'RECEIPT' | 'CORRECTION' | 'SALE'; quantityDelta: number; reason: string;
  onHandAfter: number; reservedAfter: number; createdAt: string;
}

@Component({
  selector: 'app-inventory-history', imports: [DatePipe],
  templateUrl: './inventory-history.html', styleUrl: '../shared/history.css',
})
export class InventoryHistory implements OnChanges {
  readonly productId = input.required<string>();
  readonly revision = input(0);
  private readonly api = inject(StaffApi);
  protected readonly labels = { RECEIPT: 'Przyjęcie dostawy', CORRECTION: 'Korekta', SALE: 'Wydanie po płatności' };
  private readonly query = signal({ productId: '', page: 0, revision: 0 });
  protected readonly result = toSignal(toObservable(this.query).pipe(
    filter(q => !!q.productId),
    switchMap(q => defer(() => this.api.request<Page<Movement>>('GET',
      'employee/inventory/' + encodeURIComponent(q.productId) + '/movements', undefined, { page: q.page, size: 20 })).pipe(
      map(page => ({ page, loading: false, error: '' })),
      startWith({ page: null, loading: true, error: '' }),
      catchError(error => of({ page: null, loading: false, error: staffError(error) })),
    )),
  ));

  ngOnChanges() { this.query.set({ productId: this.productId(), page: 0, revision: this.revision() }); }
  protected go(page: number) { this.query.update(q => ({ ...q, page })); }
  protected refresh() { this.query.update(q => ({ ...q, revision: q.revision + 1 })); }
}
