# API katalogu i magazynu

Backend: `http://localhost:8080`. Swagger: `/swagger-ui/index.html`. Logowanie opisuje [API kont](backend-api.md). Operacje personelu wymagają JWT w `Authorization: Bearer <accessToken>`. Role `ADMIN` i `EMPLOYEE` pozostają niezależne.

## Endpointy

| Metoda i adres | Dostęp | Wynik |
|---|---|---|
| GET `/api/tires` | Publiczny | 200, strona opon dostępnych do zakupu |
| GET `/api/tires/{id}` | Publiczny | 200, opona; 404, jeśli nie jest dostępna w ofercie |
| GET `/api/manufacturers` | Publiczny | 200, strona producentów mających opony w publicznej ofercie |
| GET `/api/staff/tires` | EMPLOYEE lub ADMIN | 200, strona wszystkich produktów, także bez ceny/stanu i wycofanych |
| GET `/api/staff/tires/{id}` | EMPLOYEE lub ADMIN | 200, produkt; 404, jeśli nie istnieje |
| POST `/api/employee/tires` | EMPLOYEE | 201 + Location, nowy produkt i zerowy stan |
| PUT `/api/employee/tires/{id}` | EMPLOYEE | 200, zmiana parametrów i zdjęcia |
| PUT `/api/admin/tires/{id}/price` | ADMIN | 200, ustalenie ceny brutto za sztukę w PLN |
| PUT `/api/admin/tires/{id}/sale` | ADMIN | 200, dopuszczenie do sprzedaży lub wycofanie |
| GET `/api/employee/inventory/{productId}` | EMPLOYEE | 200, stan fizyczny, zarezerwowany i dostępny |
| POST `/api/employee/inventory/{productId}/movements` | EMPLOYEE | 200, zapis przyjęcia/korekty albo wynik wcześniej wykonanej operacji |
| GET `/api/employee/inventory/{productId}/movements` | EMPLOYEE | 200, strona historii zmian |

Nie ma usuwania produktów ani edycji/usuwania wpisów historii magazynu. ADMIN nie może tworzyć produktów, zmieniać ich parametrów ani magazynu. EMPLOYEE nie może ustalać cen ani zmieniać `onSale`. Wspólny odczyt `/api/staff/tires` pozwala obu rolom znaleźć produkt przed swoją operacją.

## Jak przygotować pierwszą ofertę

1. Uruchom backend i utwórz administratora przez opisany w README bootstrap. Zaloguj administratora, zaproś pracownika i ustaw jego hasło tokenem z Mailpit.
2. Zaloguj pracownika. Jego JWT wstaw w Swaggerze do **Authorize**. Utwórz oponę, następnie przyjmij dostawę. Identyfikator produktu odczytaj z odpowiedzi na utworzenie.
3. Zaloguj administratora i zastąp JWT w **Authorize**. Ustaw cenę produktu i `onSale: true`.
4. Odczytaj `/api/tires` i szczegóły produktu. Odczyt publiczny nie wymaga tokenu. Jeśli testujesz bez logowania, wyczyść pole **Authorize**; wygasły token jest odrzucany także na publicznej ścieżce.

Nie dodajemy domyślnych produktów ani kont pracowników podczas startu.

## Parametry produktu

POST `/api/employee/tires` i PUT `/api/employee/tires/{id}` przyjmują ten sam dokument:

```json
{
  "manufacturer": "Dębica",
  "model": "Frigo 2",
  "widthMm": 205,
  "aspectRatio": 55,
  "rimDiameterIn": 16,
  "season": "WINTER",
  "imageUrl": "https://images.example.test/frigo-2.jpg"
}
```

`imageUrl` jest opcjonalnym adresem HTTPS (maksymalnie 2048 znaków); przykład należy zastąpić adresem własnego zdjęcia. Backend zapisuje adres, nie pobiera ani nie przesyła plików. Brak/pusta wartość oznacza brak zdjęcia; frontend może wyświetlić zastępczy obraz. PUT zastępuje cały zestaw parametrów, więc pominięcie zdjęcia usuwa jego dotychczasowy adres. Cena, dopuszczenie do sprzedaży i stan pozostają niezmienione.

Producent ma 1–100 znaków, model 1–120; nazwy są normalizowane Unicode NFKC, przycinane, a serie białych znaków zamieniane na pojedynczą spację. Porównanie producenta/modelu ignoruje wielkość liter. Istniejący producent jest ponownie wykorzystywany; zachowuje pierwszą zapisaną nazwę do prezentacji. Produkt jest unikalny według producenta, modelu, szerokości, profilu, średnicy i sezonu. Duplikat zwraca 409.

Parametry muszą być liczbami całkowitymi: `widthMm` 100–500, `aspectRatio` 20–100, `rimDiameterIn` 10–30. To granice walidacji danych, nie lista wszystkich rzeczywiście produkowanych kombinacji. Sezon: `SUMMER`, `WINTER`, `ALL_SEASON`. Nie wprowadzamy indeksów nośności/prędkości, XL ani run-flat.

Odpowiedź produktu: `id`, `manufacturerId`, `manufacturer`, `model`, `widthMm`, `aspectRatio`, `rimDiameterIn`, `season`, `imageUrl`, `priceGross`, `currency: "PLN"`, `availableQuantity`, `onSale`. Nowy produkt ma `priceGross: null`, `onSale: false` i `availableQuantity: 0`. Tworzenie produktu oraz jego zerowego magazynu jest jedną transakcją.

## Cena i sprzedaż

PUT `/api/admin/tires/{id}/price`:

```json
{"priceGross": 299.90}
```

Cena jest liczbą JSON od 0.01 do 9999999999.99, z maksymalnie dwoma miejscami dziesiętnymi. Dodatkowe miejsca są odrzucane, a nie po cichu zaokrąglane. Zapis używa `BigDecimal` i `numeric(12,2)`. Ustawienie ceny samo nie włącza sprzedaży.

PUT `/api/admin/tires/{id}/sale`:

```json
{"onSale": true}
```

Włączenie sprzedaży bez ceny zwraca 409 `PRICE_REQUIRED`. Produkt może być dopuszczony do sprzedaży przy zerowym stanie, ale w publicznym katalogu pojawi się dopiero po dostawie. Wycofanie: `onSale: false`; produkt i magazyn pozostają w bazie.

Publiczna lista i szczegóły wymagają jednocześnie `onSale = true`, dodatniej ceny i `onHand - reserved > 0`. Dostępność jest informacyjna, nie rezerwuje towaru. Ponowne sprawdzanie przy zakupie będzie częścią etapu zamówień.

## Filtry, strony i sortowanie

GET `/api/tires` oraz `/api/staff/tires` przyjmują opcjonalne `manufacturerId` (UUID), `widthMm`, `aspectRatio`, `rimDiameterIn`, `season`. Filtry działają łącznie (AND); producent jest wybierany przez ID, nie dowolny fragment nazwy.

```text
/api/tires?widthMm=205&aspectRatio=55&rimDiameterIn=16&season=WINTER&page=0&size=20&sort=PRICE_ASC
```

`page`: 0–1000000, domyślnie 0. `size`: 1–100, domyślnie 20. `sort`: `PRICE_ASC` (domyślne) lub `PRICE_DESC`; przy równej cenie zawsze ID rosnąco. W widoku personelu produkty bez ceny są na końcu. Wszystkie listy zwracają:

```json
{"items": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0}
```

Strona poza końcem listy ma puste `items`. Pusta lista ma `totalPages: 0`. Publiczna lista producentów używa tych samych parametrów `page` i `size`, zwraca elementy `{ "id": "UUID", "name": "Dębica" }`, posortowane po znormalizowanej nazwie i ID. Obejmuje tylko marki z dostępnymi oponami. Historia magazynu jest sortowana od najnowszego czasu wpisu, przy remisie po ID rosnąco.

Katalog korzysta z projekcji SQL z JOIN; liczba odczytów dla listy nie rośnie wraz z liczbą produktów. Zapytanie zliczające i pobierające stronę może zobaczyć różne chwile przy równoległych zmianach oferty — paginacja nie gwarantuje niezmiennego obrazu katalogu między żądaniami.

## Dostawy, korekty i historia

POST `/api/employee/inventory/{productId}/movements`:

```json
{
  "operationId": "b7422ba6-d414-481b-8123-a3778fe9f0a0",
  "type": "RECEIPT",
  "quantityDelta": 8,
  "reason": "Przyjęcie dostawy z 4 listopada"
}
```

`operationId` to nowy UUID generowany przez klienta dla każdej nowej operacji. Przy ponowieniu po utracie odpowiedzi użyj **tego samego UUID i tych samych danych**. Powtórzenie przez tego samego pracownika zwróci 200 i pierwotny zapis, bez ponownego doliczenia sztuk. Zmiana danych, produktu lub autora przy tym samym ID oznacza 409. Nie używaj stale UUID z przykładu do różnych dostaw.

`RECEIPT` wymaga dodatniej liczby sztuk. `CORRECTION` pozwala na dodatnią albo ujemną zmianę; zero jest odrzucane. `reason`: wymagany opis 1–500 znaków po przycięciu. Nie przyjmujemy autora z body — wynika ze zweryfikowanego JWT. Stan fizyczny nie może spaść poniżej zarezerwowanego ani przekroczyć 2147483647.

Wynik operacji i element historii zawierają `operationId`, `productId`, `actorAccountId`, `type`, `quantityDelta`, `reason`, `onHandAfter`, `reservedAfter`, `createdAt`. Liczniki w tym wyniku opisują moment wykonania operacji, również przy jej późniejszym ponowieniu. Aktualny stan odczytaj z GET `/api/employee/inventory/{productId}`: `productId`, `onHand`, `reserved`, `available`.

Aktualizacja stanu i wpis historii korzystają ze wspólnej transakcji oraz blokady wiersza magazynu. Błąd zapisu wycofuje całość. Identyfikator operacji jest kluczem głównym wpisu historii, co zabezpiecza także równoległe użycie tego samego ID dla różnych produktów. Korekty chronią `reserved`; rezerwacje tworzy etap zamówień V6, natomiast wydania po płatności pozostają do implementacji.

## Błędy i granice etapu

Format `application/problem+json`, zgodny z API kont. Nieznane pola body są odrzucane. Liczby i booleany JSON nie mogą być tekstem (`"205"`, `"false"`); wartości całkowite nie przyjmują ułamków. Identyfikatory ścieżki i filtry są walidowane.

- 400 `INVALID_REQUEST`, `INVALID_PARAMETER`, `VALIDATION_FAILED` lub `INVALID_STOCK_CHANGE`: nieprawidłowe dane.
- 401 `AUTHENTICATION_REQUIRED`: brak prawidłowego JWT dla operacji personelu.
- 403 `ACCESS_DENIED`: niewłaściwa rola.
- 404 `PRODUCT_NOT_FOUND`: brak produktu lub brak jego publicznej dostępności.
- 409 `PRICE_REQUIRED`, `INSUFFICIENT_STOCK`, `STOCK_LIMIT_EXCEEDED`, `OPERATION_ID_REUSED` lub `DATA_CONFLICT`: niedozwolona zmiana albo konflikt zapisu. Równoczesne użycie jednego operationId dla różnych produktów może zwrócić ogólny `DATA_CONFLICT`; żadna z operacji nie nadpisuje wcześniejszej historii.

Migracja V4 dodaje `manufacturer`, `tire_product`, `inventory`, `inventory_change`. Nie zmienia migracji kont ani danych istniejącej bazy. Koszyk jest zaimplementowany w V5 i opisany w [API koszyka](cart-api.md). Zamówienia i rezerwacje są w V6: [API zamówień](order-api.md). Płatności i śledzenie przesyłki pozostają kolejnymi etapami.
