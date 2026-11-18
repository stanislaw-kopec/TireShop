import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { catchError, defer, map, of, startWith, switchMap } from 'rxjs';
import { Page } from '../shared/page';
import { StaffApi, staffError } from './staff-api';

type EventType = 'ORDER_CREATED' | 'ORDER_CANCELLED';
interface AuditEntry {
  id: string;
  eventType: EventType;
  occurredAt: string;
  payload: { orderId: string; actorAccountId: string | null; reason: 'CUSTOMER' | 'EXPIRED' | null };
}

@Component({
  selector: 'app-admin-audit', imports: [DatePipe, FormsModule],
  templateUrl: './admin-audit.html', styleUrl: '../shared/history.css',
})
export class AdminAudit {
  private readonly api = inject(StaffApi);
  protected readonly labels = { ORDER_CREATED: 'Utworzenie zamówienia', ORDER_CANCELLED: 'Anulowanie zamówienia' };
  protected readonly query = signal<{ page: number; eventType: EventType | ''; revision: number }>({ page: 0, eventType: '', revision: 0 });
  protected readonly result = toSignal(toObservable(this.query).pipe(switchMap(query => {
    const params: Record<string, string | number> = { page: query.page, size: 20 };
    if (query.eventType) params['eventType'] = query.eventType;
    return defer(() => this.api.request<Page<AuditEntry>>('GET', 'admin/audit-log', undefined, params)).pipe(
      map(page => ({ page, loading: false, error: '' })),
      startWith({ page: null, loading: true, error: '' }),
      catchError(error => of({ page: null, loading: false, error: staffError(error) })),
    );
  })));

  protected filter(eventType: EventType | '') { this.query.update(q => ({ ...q, eventType, page: 0 })); }
  protected go(page: number) { this.query.update(q => ({ ...q, page })); }
  protected refresh() { this.query.update(q => ({ ...q, revision: q.revision + 1 })); }
}
