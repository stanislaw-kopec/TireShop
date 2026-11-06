import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Account } from '../account/account';
import { FormAction, StaffApi } from './staff-api';

@Component({
  selector: 'app-admin-accounts', imports: [FormsModule], styleUrls: ['../account/forms.css', './staff.css'],
  template: `
    <form ngNativeValidate (ngSubmit)="invite()"><fieldset [disabled]="action.busy()"><legend>Zaproś pracownika</legend>
      <label>Imię<input name="firstName" required maxlength="100" [(ngModel)]="firstName"></label>
      <label>Nazwisko<input name="lastName" required maxlength="100" [(ngModel)]="lastName"></label>
      <label>E-mail pracownika<input name="email" type="email" required maxlength="254" [(ngModel)]="email"></label>
      <button class="primary">Wyślij zaproszenie</button>
    </fieldset></form>
    <form ngNativeValidate (ngSubmit)="find()"><fieldset [disabled]="action.busy()"><legend>Reset hasła</legend>
      <label>E-mail klienta lub pracownika<input name="searchEmail" type="email" required maxlength="254"
        [ngModel]="searchEmail" (ngModelChange)="searchEmail = $event; found.set(null)"></label>
      <button>Znajdź konto</button>
    </fieldset></form>
    @if (found(); as account) {
      <p>{{ account.firstName }} {{ account.lastName }} · {{ account.email }}</p>
      @if (account.role !== 'ADMIN' && account.status !== 'DISABLED') {
        <button [disabled]="action.busy()" (click)="reset(account)">{{ account.status === 'INVITED' ? 'Ponów zaproszenie' : 'Wyślij link do resetu hasła' }}</button>
      } @else { <p>Reset hasła jest niedostępny dla tego konta.</p> }
    }
    @if (action.message()) { <p role="status">{{ action.message() }}</p> }
  `,
})
export class AdminAccounts {
  private readonly api = inject(StaffApi);
  protected readonly action = new FormAction();
  protected readonly found = signal<Account | null>(null);
  protected firstName = '';
  protected lastName = '';
  protected email = '';
  protected searchEmail = '';

  protected invite() {
    return this.action.run(async () => {
      await this.api.request('POST', 'admin/employees', { firstName: this.firstName.trim(), lastName: this.lastName.trim(), email: this.email.trim() });
      this.firstName = this.lastName = this.email = '';
    }, 'Wysłano zaproszenie z linkiem do ustawienia hasła.');
  }
  protected find() {
    this.found.set(null);
    return this.action.run(async () => {
      this.found.set(await this.api.request<Account>('GET', 'admin/accounts', undefined, { email: this.searchEmail.trim() }));
    });
  }
  protected reset(account: Account) {
    return this.action.run(async () => {
      await this.api.request('POST', 'admin/accounts/' + account.id + '/password-reset');
      this.found.set(null);
    }, 'Wysłano wiadomość na adres konta.');
  }
}
