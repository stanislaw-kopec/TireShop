import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { Subject, combineLatest, map, startWith, switchMap, tap } from 'rxjs';
import { CatalogApi, CatalogFilters, Season, defaultFilters, loadState, seasons } from './catalog-api';
import { TireCard } from './tire-card/tire-card';

function integer(params: ParamMap, key: string, min: number, max: number) {
  const raw = params.get(key);
  const value = Number(raw);
  return raw && /^\d+$/.test(raw) && value >= min && value <= max ? value : null;
}

function readQuery(params: ParamMap) {
  const season = params.get('season') ?? '';
  const filters: CatalogFilters = {
    widthMm: integer(params, 'widthMm', 100, 500),
    aspectRatio: integer(params, 'aspectRatio', 20, 100),
    rimDiameterIn: integer(params, 'rimDiameterIn', 10, 30),
    manufacturerId: params.get('manufacturerId') ?? '',
    season: Object.hasOwn(seasons, season) ? season as Season : '',
    sort: params.get('sort') === 'PRICE_DESC' ? 'PRICE_DESC' : 'PRICE_ASC',
  };
  return { filters, page: integer(params, 'page', 0, 1000000) ?? 0 };
}

@Component({
  imports: [FormsModule, TireCard],
  templateUrl: './catalog-page.html',
  styleUrl: './catalog-page.css',
})
export class CatalogPage {
  private readonly api = inject(CatalogApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly retryList = new Subject<void>();
  protected readonly retryBrands = new Subject<void>();
  protected readonly seasonOptions = Object.entries(seasons);
  protected filters = { ...defaultFilters };

  private readonly query = this.route.queryParamMap.pipe(
    map(readQuery),
    tap(({ filters }) => { this.filters = { ...filters }; }),
  );
  protected readonly list = toSignal(combineLatest([this.query, this.retryList.pipe(startWith(undefined))]).pipe(
    switchMap(([{ filters, page }]) => loadState(this.api.tires(filters, page))),
  ), { requireSync: true });
  protected readonly brands = toSignal(this.retryBrands.pipe(
    startWith(undefined), switchMap(() => loadState(this.api.manufacturers())),
  ), { requireSync: true });

  protected apply(filters: CatalogFilters, page = 0) {
    const queryParams = Object.fromEntries(Object.entries(filters).filter(([key, value]) =>
      value !== null && value !== '' && !(key === 'sort' && value === 'PRICE_ASC')));
    void this.router.navigate(['/'], { queryParams: { ...queryParams, page: page || null } });
  }

  protected reset() {
    this.filters = { ...defaultFilters };
    this.apply(this.filters);
  }

  protected goToPage(page: number) {
    this.apply(readQuery(this.route.snapshot.queryParamMap).filters, page);
  }

  protected showDetails(id: string) {
    void this.router.navigate(['/opony', id], { queryParamsHandling: 'preserve' });
  }
}
