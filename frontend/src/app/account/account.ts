import { HttpClient, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { catchError, firstValueFrom, throwError } from 'rxjs';

export interface Profile {
  firstName: string;
  lastName: string;
  phone: string | null;
}
export interface Account extends Profile {
  id: string;
  email: string;
  role: 'CUSTOMER' | 'EMPLOYEE' | 'ADMIN';
  status: 'ACTIVE' | 'DISABLED' | 'INVITED';
}
export interface Address {
  recipientName: string;
  street: string;
  buildingNumber: string;
  apartmentNumber: string | null;
  postalCode: string;
  city: string;
  countryCode: string;
}

export const bearer = (token: string) => ({ Authorization: 'Bearer ' + token });

export function accountError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.error?.code === 'EMAIL_IN_USE') return 'Ten adres e-mail jest już zajęty.';
    if (error.status === 401) return 'Sprawdź e-mail i hasło lub zaloguj się ponownie.';
    if (error.status === 403) return 'Ta operacja wymaga konta klienta.';
    if (error.status === 400) return 'Sprawdź poprawność i długość wpisanych danych.';
  }
  return 'Nie udało się połączyć ze sklepem. Spróbuj ponownie.';
}

@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);
  private readonly session = signal<{ token: string; account: Account } | null>(null);
  readonly token = computed(() => this.session()?.token ?? null);
  readonly profile = computed(() => this.session()?.account ?? null);
  readonly staff = computed(() => this.profile()?.role === 'ADMIN' || this.profile()?.role === 'EMPLOYEE');
  readonly notice = signal('');
  private timer?: ReturnType<typeof setTimeout>;

  register(email: string, password: string, firstName: string, lastName: string) {
    return firstValueFrom(this.http.post('/api/auth/register', { email, password, firstName, lastName }));
  }

  async login(email: string, password: string) {
    const result = await firstValueFrom(this.http.post<{ accessToken: string; expiresIn: number }>(
      '/api/auth/login', { email, password }));
    const deadline = Date.now() + result.expiresIn * 1000;
    const account = await firstValueFrom(this.http.get<Account>('/api/account/me', { headers: bearer(result.accessToken) }));
    clearTimeout(this.timer);
    this.notice.set('');
    this.session.set({ token: result.accessToken, account });
    this.timer = setTimeout(() => this.expire(result.accessToken), Math.max(0, deadline - Date.now()));
  }

  expire(token: string) {
    if (this.token() !== token) return;
    this.clearLocalLogin();
    this.notice.set('Logowanie wygasło. Zaloguj się ponownie, aby wrócić do swojego konta.');
  }

  async logout() {
    const token = this.token();
    if (!token) return;
    await firstValueFrom(this.http.post('/api/auth/logout', null, { headers: bearer(token) }));
    if (this.token() === token) this.clearLocalLogin();
  }

  async updateProfile(profile: Profile) {
    const token = this.requireToken();
    const account = await firstValueFrom(this.http.put<Account>('/api/account/me', profile, { headers: bearer(token) }));
    if (this.token() === token) this.session.set({ token, account });
  }

  async address() {
    try {
      return await firstValueFrom(this.http.get<Address>('/api/account/me/address', { headers: bearer(this.requireToken()) }));
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 404 && error.error?.code === 'ADDRESS_NOT_FOUND') return null;
      throw error;
    }
  }

  saveAddress(address: Address) {
    return firstValueFrom(this.http.put<Address>('/api/account/me/address', address, { headers: bearer(this.requireToken()) }));
  }

  private requireToken() {
    const token = this.token();
    if (!token) throw new HttpErrorResponse({ status: 401 });
    return token;
  }

  clearLocalLogin() {
    clearTimeout(this.timer);
    this.notice.set('');
    this.session.set(null);
  }
}

// Only the request carrying the current token may end this login.
export const handleExpiredLogin: HttpInterceptorFn = (req, next) => {
  const auth = inject(AccountService);
  const token = auth.token();
  return next(req).pipe(catchError(error => {
    if (error.status === 401 && token && req.headers.get('Authorization') === bearer(token).Authorization) auth.expire(token);
    return throwError(() => error);
  }));
};
