# Dziennik zdarzeń administratora

`GET /api/admin/audit-log` wymaga JWT Bearer konta `ADMIN`. Endpoint udostępnia wpisy o utworzeniu i anulowaniu zamówienia. Parametry opisuje również Swagger UI.

| Parametr | Znaczenie |
|---|---|
| `eventType` | Opcjonalnie `ORDER_CREATED` albo `ORDER_CANCELLED`. Pominięcie zwraca oba typy. |
| `page` | Numer strony od 0, domyślnie 0, maksymalnie 1 000 000. |
| `size` | Liczba wpisów od 1 do 100, domyślnie 20. |

Przykład żądania `GET /api/admin/audit-log?eventType=ORDER_CANCELLED&page=0&size=20`.

Odpowiedź 200 zawiera `items`, `page`, `size`, `totalElements` i `totalPages`. Kolejność to `occurredAt DESC, id DESC`. Odczyt listy i liczby wpisów odbywa się na wspólnym obrazie danych w transakcji `REPEATABLE_READ`.

Każdy wpis ma `id` (UUID), `eventType`, `occurredAt` (czas zdarzenia ISO 8601 UTC) i `payload` z polami:

- `orderId` — identyfikator zamówienia,
- `actorAccountId` — identyfikator klienta wykonującego operację, `null` dla gościa lub systemowego wygaśnięcia,
- `reason` — `null` przy utworzeniu, `CUSTOMER` przy anulowaniu przez klienta/gościa albo `EXPIRED` przy wygaśnięciu rezerwacji.

Payload przechowuje wyłącznie powyższe pola. Błędy mają format `ProblemDetail` z `code`. Brak poprawnego JWT daje 401, rola CUSTOMER lub EMPLOYEE daje 403, a niepoprawny filtr lub paginacja daje 400 (`INVALID_PARAMETER` lub `VALIDATION_FAILED`).

## Zapis i transakcje

V11 tworzy tabelę `audit_log` z payloadem `jsonb` i indeksami dla obu sposobów odczytu. API umożliwia odczyt wpisów. Nowe zdarzenia dopisują kolejne wiersze.

`OrderService` publikuje `OrderCreated` po zapisaniu zamówienia i wyczyszczeniu koszyka. Istniejące `OrderCancelled` przekazuje też identyfikator wykonawcy. Identyczne ponowienie utworzenia/anulowania zwraca dotychczasowy wynik, a publikacja następuje wyłącznie przy pierwszej zmianie.

Listener audytu używa `@TransactionalEventListener(AFTER_COMMIT)` i wywołuje osobny serwis zapisujący w `REQUIRES_NEW`. Publikacja wewnątrz transakcji rejestruje przyszłą obsługę audytu. Dopiero commit uruchamia zapis. Rollback odrzuca tę obsługę. Taki podział pozwala zachować atomową obsługę prób płatności przez istniejący listener `OrderCancelled`.

Błąd zapisu audytu jest przechwytywany poza jego transakcją i logowany z identyfikatorem zdarzenia/zamówienia oraz nazwą klasy wyjątku. Odpowiedź zakupu lub anulowania zachowuje wynik zatwierdzonej operacji. Audyt działa w pamięci procesu do chwili zapisu — awaria pomiędzy commitem a zapisem może utracić wpis. Ten prosty mechanizm nie ponawia utraconych wpisów. Trwała kolejka wiadomości z linkami śledzenia ma własne reguły ponawiania.

## Sprawdzenie

Testy jednostkowe sprawdzają publikację utworzenia, brak publikacji przy błędzie, bezpieczne ponowienia, payload i izolację błędów listenera. Testy na PostgreSQL wykonują rzeczywiste żądania HTTP z JWT, sprawdzają uprawnienia, filtr i paginację, brak audytu po konflikcie i rollbacku, zapis po commit oraz odpowiedzi 201/200 przy wymuszonym błędzie zapisu do `audit_log`.

Weryfikacja z 18 listopada 2026: `mvnw.cmd verify` zakończone powodzeniem — 133 testy, bez błędów i pominięć, z testami integracyjnymi na PostgreSQL w Dockerze.

Podczas pokazu zaloguj klienta, złóż zamówienie, ponów identyczny checkout i anuluj nieopłacone zamówienie dwukrotnie. Następnie zaloguj administratora w Swaggerze i pobierz dziennik — nowe wpisy to jedno utworzenie i jedno anulowanie. Dane demo również generują wpisy przy pierwszym seedzie. Istniejąca baza otrzyma wpisy z operacji wykonanych po wdrożeniu V11.
