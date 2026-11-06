import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { EMPTY, Observable, catchError, expand, map, of, reduce, startWith } from 'rxjs';

export const seasons = { SUMMER: 'Letnia', WINTER: 'Zimowa', ALL_SEASON: 'Całoroczna' } as const;
export type Season = keyof typeof seasons;
export type PriceSort = 'PRICE_ASC' | 'PRICE_DESC';

export interface CatalogFilters {
  widthMm: number | null;
  aspectRatio: number | null;
  rimDiameterIn: number | null;
  manufacturerId: string;
  season: Season | '';
  sort: PriceSort;
}

export const defaultFilters: CatalogFilters = {
  widthMm: null, aspectRatio: null, rimDiameterIn: null, manufacturerId: '', season: '', sort: 'PRICE_ASC',
};

export interface TireCardData {
  id: string;
  manufacturer: string;
  model: string;
  size: string;
  seasonLabel: string;
  priceGross: number;
  availableQuantity: number;
  imageUrl: string | null;
}

interface PublicTire {
  id: string;
  manufacturer: string;
  model: string;
  widthMm: number;
  aspectRatio: number;
  rimDiameterIn: number;
  season: Season;
  priceGross: number;
  availableQuantity: number;
  imageUrl: string | null;
}

interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
interface Manufacturer { id: string; name: string; }

function toCard(tire: PublicTire): TireCardData {
  return { ...tire, size: `${tire.widthMm}/${tire.aspectRatio} R${tire.rimDiameterIn}`, seasonLabel: seasons[tire.season] };
}

export const priceFormat = new Intl.NumberFormat('pl-PL', { style: 'currency', currency: 'PLN' });

export type LoadState<T> = { status: 'loading' } | { status: 'ready'; data: T } | { status: 'error'; httpStatus: number };

export function loadState<T>(request: Observable<T>): Observable<LoadState<T>> {
  return request.pipe(
    map(data => ({ status: 'ready' as const, data })),
    startWith({ status: 'loading' as const }),
    catchError((error: HttpErrorResponse) => of({ status: 'error' as const, httpStatus: error.status })),
  );
}

@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);

  tires(filters: CatalogFilters, page: number) {
    const params: Record<string, string | number> = { page, size: 6 };
    for (const [key, value] of Object.entries(filters)) {
      if (value !== '' && value !== null) params[key] = value;
    }
    return this.http.get<Page<PublicTire>>('/api/tires', { params }).pipe(
      map(result => ({ ...result, items: result.items.map(toCard) })),
    );
  }

  tire(id: string) {
    return this.http.get<PublicTire>('/api/tires/' + encodeURIComponent(id)).pipe(map(toCard));
  }

  manufacturers() {
    const getPage = (page: number) => this.http.get<Page<Manufacturer>>('/api/manufacturers', { params: { page, size: 100 } });
    return getPage(0).pipe(
      expand(page => page.page + 1 < page.totalPages ? getPage(page.page + 1) : EMPTY),
      reduce((items, page) => items.concat(page.items), [] as Manufacturer[]),
    );
  }
}
