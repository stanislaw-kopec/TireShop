import { Routes } from '@angular/router';

import { CatalogPage } from './catalog/catalog-page';
import { TireDetails } from './catalog/tire-details';

export const routes: Routes = [
  { path: '', pathMatch: 'full', component: CatalogPage, title: 'Katalog opon | TireShop' },
  { path: 'opony/:id', component: TireDetails, title: 'Szczegóły opony | TireShop' },
];
