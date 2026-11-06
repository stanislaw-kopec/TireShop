import { Routes } from '@angular/router';

import { CatalogPage } from './catalog/catalog-page';
import { TireDetails } from './catalog/tire-details';
import { CartPage } from './cart/cart-page';
import { LoginPage } from './account/login-page';
import { ProfilePage } from './account/profile-page';
import { CheckoutPage } from './checkout/checkout-page';
import { OrderPage } from './checkout/order-page';

export const routes: Routes = [
  { path: '', pathMatch: 'full', component: CatalogPage, title: 'Katalog opon | TireShop' },
  { path: 'opony/:id', component: TireDetails, title: 'Szczegóły opony | TireShop' },
  { path: 'koszyk', component: CartPage, title: 'Koszyk | TireShop' },
  { path: 'zakup', component: CheckoutPage, title: 'Adres i zamówienie | TireShop' },
  { path: 'zamowienia/gosc/:id', component: OrderPage, data: { guest: true }, title: 'Zamówienie gościa | TireShop' },
  { path: 'zamowienia/konto/:id', component: OrderPage, title: 'Zamówienie | TireShop' },
  { path: 'logowanie', component: LoginPage, title: 'Logowanie | TireShop' },
  { path: 'konto', component: ProfilePage, title: 'Moje konto | TireShop' },
];
