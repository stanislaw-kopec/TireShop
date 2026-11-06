import { Location } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AccountService } from './account';

@Component({
  imports: [FormsModule, RouterLink], styleUrl: './forms.css',
  template: `
    <h1>Ustaw hasło</h1>
    @if (done()) {
      <p role="status">Hasło zostało zapisane.</p><a routerLink="/logowanie">Przejdź do logowania</a>
    } @else if (token) {
      <form ngNativeValidate (ngSubmit)="submit()"><fieldset [disabled]="busy()">
        <label>Nowe hasło<input type="password" name="password" autocomplete="new-password" required minlength="12" maxlength="128" [(ngModel)]="password"></label>
        <small>Od 12 do 128 znaków.</small>
        <button class="primary">Zapisz hasło</button>
      </fieldset></form>
    } @else { <p>Link jest nieprawidłowy lub wygasł. Poproś administratora o nowy link.</p> }
    @if (error()) { <p role="alert">{{ error() }}</p> }
  `,
})
export class SetPassword {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AccountService);
  private readonly route = inject(ActivatedRoute);
  private readonly location = inject(Location);
  protected readonly busy = signal(false);
  protected readonly done = signal(false);
  protected readonly error = signal('');
  protected token: string | null;
  protected password = '';

  constructor() {
    const url = this.route.snapshot;
    const token = new URLSearchParams(url.fragment ?? '').get('token') ?? url.queryParamMap.get('token');
    if (url.fragment || url.queryParamMap.has('token')) this.location.replaceState('/set-password');
    this.token = token && /^[A-Za-z0-9_-]{43}$/.test(token) ? token : null;
  }

  protected async submit() {
    if (!this.token || this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      await firstValueFrom(this.http.post('/api/auth/password-actions/complete', { token: this.token, password: this.password }));
      this.done.set(true);
      this.auth.clearLocalLogin();
      this.token = null;
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.error?.code === 'INVALID_ACTION_TOKEN') this.token = null;
      else this.error.set('Nie udało się zapisać hasła. Sprawdź jego długość i spróbuj ponownie.');
    } finally {
      this.password = '';
      this.busy.set(false);
    }
  }
}
