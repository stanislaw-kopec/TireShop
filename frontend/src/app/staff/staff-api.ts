import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AccountService, bearer } from '../account/account';

export type { Page } from '../shared/page';
type Method = 'GET' | 'POST' | 'PUT' | 'PATCH';
type OperationBody = Record<string, string | number>;
type Resource = `inventory/${string}` | `order/${string}`;

export function staffError(error: unknown) {
  if (!(error instanceof HttpErrorResponse)) return 'Nie udało się wykonać operacji. Spróbuj ponownie.';
  const messages: Record<string, string> = {
    EMAIL_IN_USE: 'Konto z tym adresem już istnieje.', ACCOUNT_NOT_FOUND: 'Nie znaleziono konta.',
    RESET_NOT_ALLOWED: 'Nie można zresetować hasła tego konta.', PRICE_REQUIRED: 'Najpierw ustaw cenę opony.',
    INSUFFICIENT_STOCK: 'Zmiana narusza stan zarezerwowany lub dostępny.', STOCK_LIMIT_EXCEEDED: 'Przekroczono limit stanu magazynowego.',
    SHIPMENT_CHANGED: 'Numer przesyłki zmienił się. Odśwież dane przed kolejną korektą.',
    ORDER_TRANSITION_NOT_ALLOWED: 'Status zamówienia zmienił się. Odśwież zamówienie.',
    SHIPMENT_NOT_EDITABLE: 'Numer przesyłki można zmienić tylko przed doręczeniem.',
    INVALID_ACTION_TOKEN: 'Link został wykorzystany lub wygasł. Poproś administratora o nowy.',
  };
  return messages[error.error?.code] ?? ({
    400: 'Sprawdź wartości w formularzu.', 401: 'Zaloguj się ponownie.', 403: 'Brak uprawnień do tej operacji.',
    404: 'Nie znaleziono danych.', 409: 'Dane są sprzeczne z bieżącym stanem. Sprawdź je i spróbuj ponownie.',
    503: 'Usługa jest chwilowo niedostępna. Spróbuj ponownie.',
  } as Record<number, string>)[error.status] ?? 'Nie udało się potwierdzić wyniku. Sprawdź połączenie i ponów operację.';
}

export class FormAction {
  readonly busy = signal(false);
  readonly message = signal('');
  async run(action: () => Promise<void>, success = '') {
    if (this.busy()) return;
    this.busy.set(true);
    this.message.set('');
    try { await action(); this.message.set(success); }
    catch (error) { this.message.set(staffError(error)); }
    finally { this.busy.set(false); }
  }
}

@Injectable({ providedIn: 'root' })
export class StaffApi {
  private readonly http = inject(HttpClient);
  readonly auth = inject(AccountService);

  request<T>(method: Method, path: string, body?: unknown, params?: Record<string, string | number>) {
    const token = this.auth.token();
    if (!token) throw new HttpErrorResponse({ status: 401 });
    return firstValueFrom(this.http.request<T>(method, '/api/' + path, { body, params, headers: bearer(token) }));
  }

  private key(resource: Resource) { return 'tireshop.staff.operation.' + this.auth.profile()?.id + '.' + resource; }
  pending(resource: Resource): OperationBody | null {
    const data = sessionStorage.getItem(this.key(resource));
    return data ? JSON.parse(data) : null;
  }

  async apply(resource: Resource, change?: OperationBody) {
    const key = this.key(resource);
    let operation = this.pending(resource);
    if (!operation && change) {
      operation = { ...change, operationId: crypto.randomUUID() };
      sessionStorage.setItem(key, JSON.stringify(operation));
    }
    if (!operation) throw new Error('Missing operation');
    const saved = JSON.stringify(operation);
    const clear = () => {
      if (sessionStorage.getItem(key) === saved) sessionStorage.removeItem(key);
    };
    const [kind, id] = resource.split('/');
    try {
      if (kind === 'inventory') await this.request('POST', 'employee/inventory/' + id + '/movements', operation);
      else if ('expectedTrackingNumber' in operation) await this.request('PUT', 'employee/orders/' + id + '/shipment', operation);
      else await this.request('PATCH', 'employee/orders/' + id + '/status', operation);
      clear();
    } catch (error) {
      if (error instanceof HttpErrorResponse && [400, 403, 404, 409].includes(error.status)) clear();
      throw error;
    }
  }
}
