import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

@Component({
  selector: 'app-renew-tracking',
  imports: [FormsModule],
  template: `
    <form ngNativeValidate (ngSubmit)="submit(number.value, email.value)">
      <fieldset [disabled]="busy()"><legend>Nowy link do śledzenia</legend>
        <label>Numer zamówienia
          <input #number name="orderNumber" [value]="orderNumber()" placeholder="TS-…" required maxlength="39"
                 pattern="TS-[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}">
        </label>
        <label>E-mail podany przy zakupie<input #email name="email" type="email" autocomplete="email" maxlength="254" required></label>
        <button class="primary">Poproś o nowy link</button>
      </fieldset>
    </form>
    @if (message()) { <p role="status">{{ message() }}</p> }
  `,
  styleUrl: '../account/forms.css',
})
export class RenewTracking {
  readonly orderNumber = input('');
  private readonly http = inject(HttpClient);
  protected readonly busy = signal(false);
  protected readonly message = signal('');

  protected async submit(orderNumber: string, email: string) {
    if (this.busy()) return;
    this.busy.set(true);
    this.message.set('');
    try {
      await firstValueFrom(this.http.post('/api/order-tracking/link-requests', {
        orderNumber: orderNumber.trim(), email: email.trim(),
      }));
      this.message.set('Jeśli dane dotyczą opłaconego zamówienia gościa, wyślemy link na adres e-mail zapisany przy zakupie.');
    } catch (error) {
      this.message.set(error instanceof HttpErrorResponse && error.status === 400
        ? 'Sprawdź format numeru zamówienia i adresu e-mail.' : 'Nie udało się wysłać prośby. Spróbuj ponownie.');
    } finally {
      this.busy.set(false);
    }
  }
}
