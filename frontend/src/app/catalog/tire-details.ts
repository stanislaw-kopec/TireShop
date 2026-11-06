import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, combineLatest, startWith, switchMap } from 'rxjs';
import { CatalogApi, loadState, priceFormat } from './catalog-api';
import { TirePhoto } from './tire-photo';
import { AddToCart } from '../cart/add-to-cart';

@Component({
  imports: [RouterLink, TirePhoto, AddToCart],
  templateUrl: './tire-details.html',
  styles: `
    .back { display: inline-block; margin-top: 1.5rem; }
    article { display: grid; grid-template-columns: repeat(auto-fit,minmax(min(100%,20rem),1fr)); gap: 2rem; margin-block: 2rem; }
    app-tire-photo { min-height: 15rem; }
    h1 { margin-block: .4rem 1.5rem; }
    dl div { display: flex; justify-content: space-between; gap: 1rem; border-top: 1px solid #c9cfc6; padding: .7rem 0; }
    dd { margin: 0; font-weight: 600; }
    .price { font-size: 1.35rem; }
  `,
})
export class TireDetails {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(CatalogApi);
  protected readonly retry = new Subject<void>();
  protected readonly priceFormat = priceFormat;
  protected readonly result = toSignal(combineLatest([this.route.paramMap, this.retry.pipe(startWith(undefined))]).pipe(
    switchMap(([params]) => loadState(this.api.tire(params.get('id')!))),
  ), { requireSync: true });
}
