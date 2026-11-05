# Koszyk klienta i gościa

Decyzja z 5 listopada 2026: koszyk gościa jest identyfikowany osobnym losowym tokenem, bez cookie i bez sesji logowania. Przyszły Angular przechowuje go w `sessionStorage`, czyli w obrębie karty (z zachowaniem po odświeżeniu). Zamknięcie karty usuwa lokalny dostęp; backend nie dostaje wiarygodnego zdarzenia zamknięcia. Niezależnie od przeglądarki token wygasa po 24 godzinach od utworzenia, bez przedłużania. Na tym etapie wygasłe rekordy pozostają w bazie, ale nie dają dostępu do koszyka.

Konto nadal używa JWT ważnego 15 minut. Wygaśnięcie JWT/wylogowanie nie usuwa koszyka klienta; po ponownym logowaniu odczytujesz ten sam koszyk. Koszyk konta jest dostępny wyłącznie dla CUSTOMER. Nie przyjmujemy ID właściciela w body ani query.

## Endpointy

| Metoda i ścieżka | Dostęp | Wynik |
|---|---|---|
| POST `/api/guest/cart` | publiczny | 201 + Location, `token`, `expiresAt`, pusty `cart` |
| GET `/api/guest/cart` | X-Cart-Token | 200, bieżący koszyk |
| PUT `/api/guest/cart/items/{productId}` | X-Cart-Token | 200, koszyk po ustawieniu ilości |
| DELETE `/api/guest/cart/items/{productId}` | X-Cart-Token | 200, koszyk po usunięciu pozycji |
| GET `/api/cart` | JWT CUSTOMER | 200, własny koszyk; pusty, jeśli jeszcze nie zapisano pozycji |
| PUT `/api/cart/items/{productId}` | JWT CUSTOMER | 200, koszyk po ustawieniu ilości |
| DELETE `/api/cart/items/{productId}` | JWT CUSTOMER | 200, koszyk po usunięciu pozycji |
| POST `/api/cart/merge` | JWT CUSTOMER **oraz** X-Cart-Token | 200, koszyk klienta po scaleniu |

POST tworzący koszyk gościa nie wymaga body. PUT przyjmuje wyłącznie `{"quantity": 3}`. To **docelowa ilość**, nie przyrost: dwukrotne PUT z 3 pozostawia 3 sztuki. Dla przycisku „dodaj” frontend wysyła dotychczasową ilość + wybór użytkownika. Ilość jest dodatnią liczbą całkowitą; zero nie usuwa pozycji, do usuwania służy DELETE. Ponowienie DELETE nie zmienia wyniku. Maksymalnie 100 różnych produktów w koszyku; odpowiedź zawiera wszystkie pozycje w kolejności ID produktu. Nie przyjmujemy cen, ról ani właściciela z żądania.

## Podsumowanie

Odpowiedź zawiera `items` (produkt, `quantity`, `lineTotalGross`, `issue`), `totalQuantity`, `itemsTotalGross`, `shippingGross`, `totalGross`, `currency: "PLN"`, `readyForCheckout`. Produkt zawiera aktualną cenę i dostępność z katalogu. Kwoty są brutto, liczone przez backend przy użyciu BigDecimal. Dostawa: 1–3 sztuki 20,00 PLN, od 4 sztuk 0,00 PLN, niezależnie od produktów. Pusty koszyk ma kwoty 0,00 i `readyForCheckout: false`.

Zmiana katalogu lub magazynu nie usuwa zapisanej pozycji. `issue` ma wartości `NONE`, `NOT_FOR_SALE`, `INSUFFICIENT_STOCK`. Niedostępna pozycja wyłącza `readyForCheckout`; można ją usunąć. Jeśli produkt nie ma ceny, kwota pozycji oraz suma produktów i całego koszyka są `null`, a nie zerem. Aktualizacja ilości wymaga bieżącej dostępności wybranej pozycji. Stan jest informacyjny: koszyk **nie rezerwuje** opon. Zamówienie będzie musiało ponownie sprawdzić i atomowo zarezerwować towar.

## Logowanie i scalanie

1. Zachowaj token gościa podczas logowania/rejestracji. Logowanie samo nie zmienia koszyka ani nie konsumuje tokenu gościa.
2. Po uzyskaniu JWT wywołaj POST `/api/cart/merge` z `Authorization: Bearer ...` i `X-Cart-Token: ...`.
3. Backend sumuje ilości identycznych produktów. Jeśli zabraknie sztuk lub produkt wycofano, zwraca 409 `CART_MERGE_UNAVAILABLE`; **oba koszyki pozostają bez zmian**. Odczytaj oba, popraw ilości lub usuń niedostępne pozycje, ponów scalanie.
4. Po sukcesie pokaż zwróconą zawartość i przeliczoną dostawę. Dopiero wtedy usuń token gościa z sessionStorage.

Udane scalanie zużywa koszyk gościa. Ponowienie z tym samym tokenem i tym samym kontem zwraca aktualny koszyk klienta bez ponownego dodawania. Inne konto oraz zwykłe operacje gościa dostają 404. Potwierdzenie scalenia jest dostępne do pierwotnego terminu tokenu. Równoczesne operacje blokują wiersze koszyka (przy scalaniu najpierw gość, potem konto); całość jest jedną transakcją.

## Bezpieczeństwo i błędy

Token gościa to 32 losowe bajty zapisane jako Base64URL (43 znaki), w bazie przechowujemy tylko SHA-256. To osobny sekret uprawniający do jednego koszyka, nie JWT konta ani przyszły link do zamówienia. Nie przekazuj go w URL/cookie ani nie loguj nagłówka `X-Cart-Token`. Token jest zwracany tylko przy utworzeniu, z `Cache-Control: no-store`. API nie ustawia cookies. Poza lokalnym środowiskiem używaj HTTPS. CORS dopuszcza ten nagłówek tylko dla skonfigurowanych originów; nie włączamy `withCredentials`.

- 400: walidacja ilości/UUID/JSON, nieznane pola.
- 401: brak poprawnego JWT dla koszyka konta.
- 403: rola inna niż CUSTOMER dla koszyka konta.
- 404 `CART_NOT_FOUND`: brakujący, niepoprawny, wygasły lub zużyty token; nie ujawniamy przyczyny. `PRODUCT_NOT_FOUND`: produkt nie istnieje.
- 409 `CART_PRODUCT_UNAVAILABLE`, `CART_MERGE_UNAVAILABLE`, `CART_SIZE_LIMIT`, `CART_QUANTITY_LIMIT`: konflikt dostępności lub limitów, bez częściowego zapisu.

Swagger ma dwa pola Authorize: `bearerAuth` dla JWT oraz `guestCartToken` dla tokenu koszyka. Scalanie wymaga obu. Wygasły JWT należy usunąć z Authorize także podczas testowania publicznych operacji gościa.

Migracja V5 dodaje `shopping_cart` oraz `cart_item`. Checkout i 15-minutowe rezerwacje są w V6: [API zamówień](order-api.md). Gotowość koszyka jest podpowiedzią; złożenie zamówienia ponownie sprawdza ceny i dostępność. V7 udostępnia [symulator płatności](payment-api.md).
