import { CanActivateFn, Router, Routes } from '@angular/router';
import { inject } from '@angular/core';
import { AccountService } from './account/account';

import { CatalogPage } from './catalog/catalog-page';
import { TireDetails } from './catalog/tire-details';
import { CartPage } from './cart/cart-page';
import { LoginPage } from './account/login-page';
import { ProfilePage } from './account/profile-page';
import { OrderHistory } from './account/order-history';
import { CheckoutPage } from './checkout/checkout-page';
import { OrderPage } from './checkout/order-page';
import { PaymentPage } from './payment/payment-page';
import { TrackingPage } from './payment/tracking-page';

const shopperRoute: CanActivateFn = () => !inject(AccountService).staff() || inject(Router).parseUrl('/personel');

export const routes: Routes = [
  { path: 'personel', loadComponent: () => import('./staff/staff-panel').then(m => m.StaffPanel), title: 'Panel personelu | TireShop' },
  { path: 'set-password', loadComponent: () => import('./account/set-password').then(m => m.SetPassword), title: 'Ustaw hasło | TireShop' },
  { path: '', pathMatch: 'full', component: CatalogPage, title: 'Katalog opon | TireShop' },
  { path: 'opony/:id', component: TireDetails, title: 'Szczegóły opony | TireShop' },
  { path: 'koszyk', canActivate: [shopperRoute], component: CartPage, title: 'Koszyk | TireShop' },
  { path: 'zakup', canActivate: [shopperRoute], component: CheckoutPage, title: 'Adres i zamówienie | TireShop' },
  { path: 'track-order', component: TrackingPage, title: 'Śledzenie zamówienia | TireShop' },
  { path: 'zamowienia/gosc/:id/platnosc', component: PaymentPage, data: { guest: true }, title: 'Symulator płatności | TireShop' },
  { path: 'zamowienia/konto/:id/platnosc', canActivate: [shopperRoute], component: PaymentPage, title: 'Symulator płatności | TireShop' },
  { path: 'zamowienia/gosc/:id', component: OrderPage, data: { guest: true }, title: 'Zamówienie gościa | TireShop' },
  { path: 'zamowienia/konto/:id', canActivate: [shopperRoute], component: OrderPage, title: 'Zamówienie | TireShop' },
  { path: 'logowanie', component: LoginPage, title: 'Logowanie | TireShop' },
  { path: 'konto', canActivate: [shopperRoute], component: ProfilePage, title: 'Moje konto | TireShop' },
  { path: 'konto/zamowienia', canActivate: [shopperRoute], component: OrderHistory, title: 'Historia zamówień | TireShop' },
];
