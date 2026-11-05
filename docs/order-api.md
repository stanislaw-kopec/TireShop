# Zamówienia, podsumowanie i rezerwacja

Etap V6: podsumowanie zakupu, utworzenie zamówienia, rezerwacja 15 minut, historia własnych zamówień, anulowanie nieopłaconych i automatyczne wygaśnięcie. V7 dodaje [symulator płatności](payment-api.md) i wydanie towaru po zapłacie. V8 dodaje [linki śledzenia gościa oraz pocztę](tracking-api.md). V9 dodaje [realizację przez pracownika](fulfillment-api.md) oraz trackingNumber w historii i szczegółach zamówienia (null przed wysyłką).

## Ścieżka API

| Operacja | Klient CUSTOMER — JWT | Gość |
|---|---|---|
| Podsumowanie | POST `/api/orders/preview` | POST `/api/guest/orders/preview`, nagłówek X-Cart-Token |
| Utworzenie | POST `/api/orders` | POST `/api/guest/orders`, nagłówek X-Purchase-Token |
| Szczegóły | GET `/api/orders/{id}` | GET `/api/guest/orders/{id}`, nagłówek X-Purchase-Token |
| Anulowanie | POST `/api/orders/{id}/cancellation` | POST `/api/guest/orders/{id}/cancellation`, nagłówek X-Purchase-Token |
| Historia | GET `/api/orders?page=0&size=20` | brak publicznej listy |

Podsumowanie i anulowanie zwracają 200. Utworzenie zamówienia zwraca 201 i Location wskazujące szczegóły; identyczne ponowienie ma ten sam wynik, ID i Location (również 201). Historia ma standardowe metadane stron, page 0–1000000 i size 1–100. Sortowanie: createdAt malejąco, ID rosnąco. Zawiera ID, numer, status, czas utworzenia i kwotę, bez pobierania wszystkich pozycji każdego zamówienia.

### 1. Przygotowanie podsumowania

```json
{
  "email": "klient@example.test",
  "phone": "123456789",
  "address": {
    "recipientName": "Jan Kowalski",
    "street": "Przykładowa",
    "buildingNumber": "10",
    "apartmentNumber": "2",
    "postalCode": "00-001",
    "city": "Warszawa",
    "countryCode": "PL"
  }
}
```

Gość podaje adres, e-mail i telefon. Klient może pominąć adres/e-mail — backend pobierze je z własnego konta. Jeśli adresu na koncie nie ma, odpowiedź 404 ADDRESS_NOT_FOUND oznacza konieczność uzupełnienia formularza. Telefon jest wymagany w żądaniu. Podanie innego adresu dla zakupu nie nadpisuje profilu. Adres ma walidację jak API kont, e-mail maksymalnie 254 znaki, telefon 1–30 znaków po przycięciu.

Backend zapisuje `checkout_quote` i zwraca `id`, `expiresAt` oraz `snapshot`: pozycje z opisem opon, ilości, jednostkowe ceny brutto, sumy, kontakt i adres dostawy. Podsumowanie jest ważne 15 minut. Nie powstaje zamówienie ani rezerwacja. Pusty koszyk albo niedostępna pozycja daje 409.

Dla gościa odpowiedź dodatkowo zawiera `purchaseToken` i `accessExpiresAt`. To nowy, losowy sekret ważny 24 godziny od podsumowania, przechowywany w bazie wyłącznie jako SHA-256. Odpowiedź ma Cache-Control: no-store. Zapisz token przed przejściem dalej, osobno od tokenu koszyka (w przyszłym Angularze w sessionStorage). Token nigdy nie trafia do URL, logów ani cookie. Dla konta te pola są null. Jeśli utracisz odpowiedź podsumowania, można pobrać nowe — to nie rezerwuje towaru.

### 2. Potwierdzenie „Zapłać”

```json
{
  "quoteId": "uuid-z-odpowiedzi-podsumowania",
  "checkoutKey": "nowy-uuid-dla-tego-zakupu"
}
```

Pokaż użytkownikowi snapshot przed wysłaniem żądania. Backend nie przyjmuje kwot, adresu ani statusu z tego żądania: potwierdzany jest zapisany serwerowo dokument. Ponownie sprawdza zawartość koszyka, ceny, parametry i dostępność pod blokadami. Zmiana zawartości/ceny/opisu daje 409 CHECKOUT_CHANGED; pobierz nowe podsumowanie i poproś o jego potwierdzenie. Upływ ważności daje QUOTE_EXPIRED. Nieodpowiednia dostępność daje CART_PRODUCT_UNAVAILABLE lub INSUFFICIENT_STOCK.

Jedna transakcja rezerwuje wszystkie pozycje, zapisuje `customer_order` i `order_item`, a następnie czyści zamówiony koszyk. Błąd dowolnego kroku wycofuje wszystko. Ceny i adres są kopiami niezależnymi od późniejszych zmian katalogu/profilu. Koszt dostawy pochodzi z istniejącej reguły koszyka: 1–3 sztuki 20 PLN, od 4 sztuk gratis, suma wszystkich pozycji. Ceny jednostkowe mają numeric(12,2), sumy numeric(24,2), aby sumowanie dopuszczalnych ilości nie powodowało przepełnienia kolumny kwoty jednostkowej.

Ponawiaj utracone żądanie z tym samym quoteId, checkoutKey i dowodem dostępu. Jedno podsumowanie może utworzyć tylko jedno zamówienie. Ten sam klucz z innym podsumowaniem daje CHECKOUT_KEY_REUSED (przy równoczesnym konflikcie UNIQUE może być DATA_CONFLICT), a inny klucz dla wykorzystanego podsumowania QUOTE_ALREADY_USED. Retry nie wymaga ponownego odczytu wyczyszczonego koszyka i nie przedłuża rezerwacji. Po anulowaniu retry zwraca to samo anulowane zamówienie; nowe zamówienie wymaga nowego koszyka/podsumowania.

### 3. Zamówienie i anulowanie

Odpowiedź: `id`, `orderNumber` (TS-UUID), `status`, `createdAt`, `reservationExpiresAt`, `cancellationReason`, `snapshot`. Numer nie jest sekretem dostępu. Zmiana statusu nie jest ogólną operacją PUT/PATCH.

Nowe zamówienie ma PENDING_PAYMENT. Rezerwacja trwa dokładnie 15 minut od utworzenia; nie zmienia stanu fizycznego ani rejestru inventory_change. Anulowanie przed terminem daje CANCELLED/CUSTOMER. Po terminie (również dokładnie w chwili wygaśnięcia) powodem jest EXPIRED. Ponowienie anulowania jest bezpieczne. Statusy PAID i późniejsze nie pozwalają anulować: 409 ORDER_NOT_CANCELLABLE. W tym etapie nie istnieje endpoint ustawiający PAID.

Zadanie w tle domyślnie co 15 sekund pobiera do 100 wygasłych zamówień i zwalnia je w osobnych transakcjach. Restart backendu nie gubi rezerwacji: termin jest w bazie. Kilka instancji zadania lub wyścig z anulowaniem nie powoduje podwójnego zwolnienia. GET jest tylko odczytem: od terminu pokazuje efektywny status CANCELLED/EXPIRED, nawet jeśli zadanie jeszcze nie zapisało zmiany. Dostępność magazynowa może pozostać pomniejszona do wykonania zadania; nie prowadzi to do sprzedaży ponad stan. Błąd zwolnienia pozostawia zamówienie do kolejnej próby.

Konfiguracja: `app.order.expiry-job-enabled` (domyślnie true) i `app.order.expiry-delay-ms` (domyślnie 15000). Testy wyłączają zadanie dla kontrolowanego wywoływania operacji i sprawdzają dokładną granicę czasu z wstrzykniętym Clock. Nie wyłączaj zadania podczas normalnego uruchomienia sklepu.

## Dostęp i spójność

Klient odczytuje/anuluje tylko swoje zamówienia. Gość używa tokenu zakupu konkretnego podsumowania/zamówienia, nie tokenu koszyka. Logowanie i scalenie koszyka po utworzeniu zamówienia nie przypisuje zakupu gościa do konta i nie odbiera dostępu przez token zakupu. Po 24 godzinach token wygasa; przyszły ograniczony link śledzenia po zapłacie będzie osobnym mechanizmem. Sam UUID zamówienia/quoteId, JWT innego klienta albo token innego zakupu daje 404 ORDER_NOT_FOUND. Operacje konta wymagają CUSTOMER (pracownik/admin nie dziedziczą tego uprawnienia).

Kolejność blokad checkoutu: podsumowanie → koszyk → produkty w porządku UUID → magazyn w tym samym porządku. Rezerwacja i odblokowanie magazynu są operacjami domeny inventory. Anulowanie/wygaśnięcie oraz płatność: zamówienie → magazyn w porządku UUID. Płatność sprawdza termin także po uzyskaniu blokad magazynu, niezależnie od zadania w tle. Anulowanie/wygaśnięcie kończy oczekującą próbę płatności w tej samej transakcji.

Migracja V6 dodaje checkout_quote, customer_order i order_item. Nie usuwa poprzednich danych. Wygasłe podsumowania pozostają w bazie; polityka ich sprzątania jest późniejszym zadaniem, bez usuwania podsumowań powiązanych z historią zamówień.
