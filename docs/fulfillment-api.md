# Realizacja zamówień przez pracownika — V9

Wszystkie endpointy wymagają JWT Bearer i roli EMPLOYEE. ADMIN nie dziedziczy tej roli; klient ani token gościa nie dają dostępu. Dane kontaktu i adresu są dostępne wyłącznie w szczegółach zamówienia, w zakresie potrzebnym do realizacji.

## Odczyt

| Endpoint | Wynik |
|---|---|
| GET /api/employee/orders | Strona zamówień klienta i gościa, opcjonalny filtr status |
| GET /api/employee/orders/{id} | Szczegóły z niezmienną kopią pozycji, cen, kontaktu i adresu oraz bieżącym trackingNumber |

Lista obsługuje page od 0 do 1 000 000, size od 1 do 100 (domyślnie 20). Sortowanie jest stałe: createdAt malejąco, następnie id rosnąco. Filtr status przyjmuje wartości OrderStatus: PENDING_PAYMENT, PAID, PROCESSING, SHIPPED, DELIVERED, CANCELLED.

Odpowiedź listy: items, page, size, totalElements, totalPages. Każda pozycja zawiera id, orderNumber, status, createdAt, totalGross, currency, trackingNumber. Lista używa jednego zapytania projekcji i jednego licznika; nie pobiera kolekcji pozycji ani adresów dla każdego wiersza. Szczegóły pobierają jedno zamówienie i jego pozycje.

Tak jak w API klienta, wygasłe PENDING_PAYMENT wyświetla się jako CANCELLED nawet przed wykonaniem zadania zwalniającego rezerwacje. Filtr uwzględnia ten sam stan. GET nie zapisuje zmian.

## Zmiana statusu

PATCH /api/employee/orders/{id}/status zwraca 200.

```json
{
  "operationId": "00000000-0000-0000-0000-000000000001",
  "status": "PROCESSING"
}
```

Dozwolone przejścia są ściśle kolejne:

| Z | Do | Numer przesyłki w żądaniu |
|---|---|---|
| PAID | PROCESSING | Nie podawaj |
| PROCESSING | SHIPPED | Wymagany trackingNumber |
| SHIPPED | DELIVERED | Nie podawaj — zachowany obecny |

Pracownik nie może ustawiać PAID, anulować zamówień ani pomijać lub cofać etapów. Płatność jest jedyną drogą do PAID. Nieopłacone i anulowane zamówienia nie mogą wejść w realizację. Doręczenie jest potwierdzane ręcznie; nie ma integracji kurierskiej.

Przykład wysyłki:

```json
{
  "operationId": "00000000-0000-0000-0000-000000000002",
  "status": "SHIPPED",
  "trackingNumber": "PARCEL-123"
}
```

Numer ma 1–100 znaków, zaczyna się literą ASCII lub cyfrą, dalej dopuszcza litery, cyfry oraz . _ / -. Brzegowe spacje są usuwane. Nie podawaj URL kuriera. Nie zakładamy globalnej unikalności numerów różnych przewoźników.

## Korekta numeru

Decyzja z 5 listopada 2026: numer jest wymagany przy wysyłce i można go poprawić do doręczenia. PUT /api/employee/orders/{id}/shipment działa wyłącznie w SHIPPED i zwraca 200.

```json
{
  "operationId": "00000000-0000-0000-0000-000000000003",
  "expectedTrackingNumber": "PARCEL-123",
  "trackingNumber": "PARCEL-456"
}
```

expectedTrackingNumber to numer odczytany przed edycją. Jeśli ktoś go już zmienił, backend zwraca 409 SHIPMENT_CHANGED. Odśwież zamówienie przed świadomą ponowną korektą. Korekta nie zmienia statusu, magazynu, płatności ani danych historycznych zakupu.

## Ponowienia i spójność

Każda zmiana wymaga nowego losowego UUID operationId. Zachowaj go przy ponowieniu tego samego żądania, np. po utracie odpowiedzi. Klucz jest unikalny wśród operacji realizacji i związany z zamówieniem, pracownikiem, typem operacji oraz znormalizowanymi danymi.

Odpowiedź zawiera operationId, orderId, status, trackingNumber i changedAt (UTC ISO 8601). Jest wynikiem danej operacji. Identyczne ponowienie zwraca ten sam wynik, również gdy zamówienie przeszło już dalej. Nie cofa to aktualnego stanu — po operacji pobierz świeże szczegóły, jeśli potrzebujesz bieżącego widoku.

Inny pracownik, inne zamówienie lub dane z tym samym operationId dają konflikt. Nowy klucz nie pozwala ponownie wykonać już zakończonego przejścia statusu. Równoległe użycie jednego klucza dla różnych zamówień chroni także klucz główny bazy; przegrana transakcja jest w całości wycofywana.

Zmiana blokuje wiersz zamówienia FOR UPDATE, tak samo jak płatność, anulowanie i wygaśnięcie. Stan jest sprawdzany po uzyskaniu blokady. Aktualizacja statusu/numeru i zapis operacji są jedną transakcją. Wyjątek podczas zapisu operacji cofa aktualizację zamówienia.

V9 dodaje order_fulfillment_operation oraz ograniczenia numeru przesyłki. Rejestr operacji służy ponowieniom i zapisuje autora, czas oraz rezultat; nie jest pełną historią wszystkich statusów (np. utworzenia, zapłaty i wygaśnięcia) i nie ma osobnego endpointu historii zmian. Nie wysyłamy kolejnych e-maili przy zmianie statusu — istniejący link gościa odczytuje aktualny stan.

trackingNumber dodano także do historii i szczegółów własnych zamówień klienta oraz szczegółów dostępnych przez kontekst zakupu gościa. Istniejący link śledzenia pokazuje aktualny status i poprawiony numer, bez danych osobowych.

## Błędy

ProblemDetail zawiera stały code:

- 400 INVALID_REQUEST / VALIDATION_FAILED / INVALID_PARAMETER — niepoprawne dane, enum, UUID lub paginacja;
- 400 TRACKING_NUMBER_REQUIRED — brak numeru przy SHIPPED lub podanie go przy innym przejściu;
- 401 AUTHENTICATION_REQUIRED — brak poprawnego JWT;
- 403 ACCESS_DENIED — brak roli EMPLOYEE;
- 404 ORDER_NOT_FOUND — nieznane zamówienie;
- 409 ORDER_TRANSITION_NOT_ALLOWED — niedozwolony kolejny etap;
- 409 SHIPMENT_NOT_EDITABLE — korekta poza SHIPPED;
- 409 SHIPMENT_CHANGED — nieaktualny poprzedni numer;
- 409 FULFILLMENT_OPERATION_REUSED — ponowne użycie klucza z innymi danymi lub wykonawcą;
- 409 DATA_CONFLICT — konflikt ograniczeń bazy, np. równoległe wykorzystanie klucza.

Nieznane pola, np. actorAccountId, accountId, ceny i adresy, są odrzucane. Tożsamość pracownika pochodzi wyłącznie ze zweryfikowanego JWT.
