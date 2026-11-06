import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NavigationEnd, Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { filter, firstValueFrom } from 'rxjs';
import { routes } from './app.routes';

const tire = {
  id: 'test-tire', model: 'Frigo 2', manufacturer: 'Dębica', season: 'WINTER',
  widthMm: 205, aspectRatio: 55, rimDiameterIn: 16, priceGross: 259,
  availableQuantity: 12, imageUrl: null,
};
const page = <T>(items: T[], index = 0, totalElements = items.length, totalPages = items.length ? 1 : 0) =>
  ({ items, page: index, size: 6, totalElements, totalPages });

describe('Publiczny katalog', () => {
  let http: HttpTestingController;
  let router: Router;
  let harness: RouterTestingHarness;
  const listRequest = () => http.expectOne(req => req.url === '/api/tires');
  const brandsRequest = () => http.expectOne(req => req.url === '/api/manufacturers');
  const element = () => harness.routeNativeElement!;
  const button = (text: string) => Array.from(element().querySelectorAll('button')).find(b => b.textContent.includes(text))!;
  async function navigate(action: () => void) {
    const done = firstValueFrom(router.events.pipe(filter(e => e instanceof NavigationEnd)));
    action();
    await done;
    harness.detectChanges();
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();
  });
  afterEach(() => http.verify());

  it('applies all filters, resets pagination, opens details and returns to the same results', async () => {
    await harness.navigateByUrl('/?page=1');
    brandsRequest().flush(page([{ id: 'brand', name: 'Dębica' }]));
    listRequest().flush(page([tire], 1, 8, 2));
    await harness.fixture.whenStable();
    harness.detectChanges();

    for (const [name, value] of Object.entries({
      widthMm: '205', aspectRatio: '55', rimDiameterIn: '16', manufacturerId: 'brand', season: 'WINTER', sort: 'PRICE_DESC',
    })) {
      const control = element().querySelector<HTMLInputElement | HTMLSelectElement>('[name="' + name + '"]')!;
      control.value = value;
      control.dispatchEvent(new Event(control.tagName === 'SELECT' ? 'change' : 'input'));
    }
    await navigate(() => button('Pokaż wyniki').click());
    const first = listRequest();
    expect(Object.fromEntries(first.request.params.keys().map(key => [key, first.request.params.get(key)]))).toEqual({
      widthMm: '205', aspectRatio: '55', rimDiameterIn: '16', manufacturerId: 'brand', season: 'WINTER',
      sort: 'PRICE_DESC', size: '6', page: '0',
    });
    first.flush(page([tire], 0, 8, 2));
    harness.detectChanges();
    await navigate(() => button('Następna').click());
    const second = listRequest();
    expect(second.request.params.get('page')).toBe('1');
    second.flush(page([tire], 1, 8, 2));
    harness.detectChanges();
    const listUrl = router.url;
    await navigate(() => button('Pokaż szczegóły').click());
    http.expectOne('/api/tires/test-tire').flush({ ...tire, availableQuantity: 3 });
    harness.detectChanges();
    expect(element().textContent).toContain('3 szt.');
    expect(element().textContent).toContain('205/55 R16');
    expect(element().querySelector('img')?.getAttribute('src')).toBe('/tire-outline.svg');
    await navigate(() => element().querySelector('a')!.click());
    expect(router.url).toBe(listUrl);
    brandsRequest().flush(page([{ id: 'brand', name: 'Dębica' }]));
    listRequest().flush(page([tire], 1, 8, 2));
    await harness.fixture.whenStable();
    expect(element().querySelector<HTMLInputElement>('[name=widthMm]')!.value).toBe('205');
  });

  it('handles loading, retry, empty results and resetting filters', async () => {
    await harness.navigateByUrl('/?season=WINTER');
    expect(element().textContent).toContain('Pobieranie opon');
    brandsRequest().flush(page([]));
    listRequest().flush({}, { status: 503, statusText: 'Unavailable' });
    harness.detectChanges();
    button('Spróbuj ponownie').click();
    listRequest().flush(page([]));
    harness.detectChanges();
    expect(element().textContent).toContain('Brak opon');
    await navigate(() => button('Pokaż wszystkie opony').click());
    const request = listRequest();
    expect(request.request.params.has('season')).toBe(false);
    request.flush(page([tire]));
    harness.detectChanges();
    expect(element().textContent).toContain('Frigo 2');
  });

  it('loads details directly, retries a failed request and explains a withdrawn offer', async () => {
    await harness.navigateByUrl('/opony/test-tire');
    http.expectOne('/api/tires/test-tire').flush({}, { status: 503, statusText: 'Unavailable' });
    harness.detectChanges();
    button('Spróbuj ponownie').click();
    http.expectOne('/api/tires/test-tire').flush({}, { status: 404, statusText: 'Not Found' });
    harness.detectChanges();
    expect(element().textContent).toContain('Opona niedostępna');
    expect(element().querySelector('a')?.getAttribute('href')).toBe('/');
  });

  it('fetches additional manufacturer pages and accepts missing product photos', async () => {
    await harness.navigateByUrl('/');
    brandsRequest().flush(page([{ id: 'one', name: 'Pierwsza' }], 0, 101, 2));
    const next = brandsRequest();
    expect(next.request.params.get('page')).toBe('1');
    next.flush(page([{ id: 'two', name: 'Ostatnia' }], 1, 101, 2));
    listRequest().flush(page([{ ...tire, imageUrl: 'https://example.test/tire.jpg' }]));
    harness.detectChanges();
    expect(element().textContent).toContain('Ostatnia');
    const photo = element().querySelector('app-tire-photo img')!;
    expect(photo.getAttribute('src')).toBe('https://example.test/tire.jpg');
    photo.dispatchEvent(new Event('error'));
    harness.detectChanges();
    expect(element().querySelector('app-tire-photo img')?.getAttribute('src')).toBe('/tire-outline.svg');
  });
});
