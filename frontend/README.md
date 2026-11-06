# TireShop — frontend

Na stronie głównej działa publiczny katalog. Wybierz rozmiar, sezon lub producenta i kliknij „Pokaż wyniki”. Katalog sortuje opony po cenie i pokazuje po 6 na stronie. „Pokaż szczegóły” otwiera osobną stronę produktu. Adres zachowuje zastosowane filtry, sortowanie i stronę, więc można go odświeżyć lub wrócić do poprzednich wyników. [Plan wdrożenia](../docs/frontend-plan.md).

## Koszyk gościa

W szczegółach opony wybierz liczbę sztuk i kliknij „Dodaj do koszyka”. Link „Koszyk” w nagłówku prowadzi do `/koszyk`, gdzie możesz zmienić ilość, usunąć pozycję i sprawdzić kwotę brutto. Dostawa dla 1–3 sztuk kosztuje 20 zł, a od 4 sztuk jest bezpłatna, także dla różnych modeli. Aby to sprawdzić, dodaj dwie opony jednego modelu i jedną drugiego, odśwież kartę i zwiększ pierwszą pozycję do trzech.

Serwis `ShoppingCart` tworzy koszyk gościa przy pierwszym dodaniu i przechowuje w `sessionStorage` tej karty wyłącznie token koszyka. API zwraca bieżące kwoty, dostępność i liczbę sztuk. [Kontrakt API koszyka](../docs/cart-api.md).

## Konto klienta

Wybierz „Zaloguj się” w nagłówku. Możesz utworzyć własne konto albo użyć klienta demo. Po zalogowaniu zobaczysz koszyk konta. Jeśli w karcie był koszyk gościa, sklep połączy oba i pokaże nowe podsumowanie. Przy konflikcie wybierz koszyk konta lub gościa, zmień ilość albo usuń pozycję i kliknij „Połącz koszyki”. W „Moje konto” możesz zmienić dane i zapisać adres dostawy. [Dane do logowania w demo](../docs/demo.md#konta).

JWT pozostaje w pamięci Angulara do 15 minut. Po odświeżeniu strony lub wygaśnięciu tokenu zaloguj się ponownie, aby wrócić do koszyka konta. „Wyloguj się” unieważnia na serwerze wszystkie dotychczasowe tokeny tego konta.

## Jak działa komponent karty

W [szablonie katalogu](src/app/catalog/catalog-page.html) pętla `@for` tworzy instancję karty dla każdej opony z odpowiedzi API. W Reakcie podobny zapis to `<TireCard tire={tire} onDetailsRequested={t => showDetails(t.id)} />`.

```html
<app-tire-card [tire]="tire" (detailsRequested)="showDetails($event.id)" />
```

- [CatalogApi](src/app/catalog/catalog-api.ts) pobiera produkty i producentów z backendu. Dane jednej opony zamienia na model widoku, np. z trzech liczb tworzy rozmiar „205/55 R16”.
- [TireCard](src/app/catalog/tire-card/tire-card.ts) przyjmuje dane przez `input.required()`. To rola podobna do propsa w Reakcie. Gdy użytkownik klika przycisk, karta emituje wybraną oponę przez `detailsRequested`.
- [CatalogPage](src/app/catalog/catalog-page.ts) odbiera zdarzenie i otwiera trasę `/opony/:id`. [TireDetails](src/app/catalog/tire-details.ts) odczytuje identyfikator z adresu i pobiera aktualną cenę oraz dostępność. Dzięki temu szczegóły działają także po bezpośrednim otwarciu linku.
- Stan listy zależy od parametrów w URL. `switchMap` przełącza żądanie po zmianie parametrów, aby spóźniona odpowiedź nie zastąpiła aktualnych wyników.

## Demo

Uruchom `docker compose up` w głównym katalogu TireShop i otwórz [frontend](http://localhost:4200). Żądania do `/api/` na tym samym porcie trafiają do backendu. Po zmianach w kodzie kontenera użyj `docker compose up --build`.

## Praca lokalna

Z Node.js 24.11.1 i npm wykonaj te polecenia w katalogu `frontend`:

```sh
npm ci
npm start
```

Serwer deweloperski odświeża stronę pod adresem [localhost:4200](http://localhost:4200). Plik `proxy.conf.json` kieruje `/api/**` do backendu demo na porcie 8081. Dla backendu z IntelliJ ustaw w nim port 8080. Jeśli demo zajmuje port 4200, zatrzymaj jego frontend poleceniem `docker compose stop frontend` z głównego katalogu.

Testy uruchomisz przez `npm test -- --watch=false`, a wersję produkcyjną zbudujesz przez `npm run build`.
