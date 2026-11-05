# Symulator płatności — V7

To wyłącznie symulacja: nie pobiera pieniędzy, nie przyjmuje danych kart i nie integruje się z bankiem. Backend udostępnia API dla przyszłego osobnego ekranu Angulara. Teraz można przejść cały scenariusz w Swagger UI.

## Dostęp i endpointy

Prefiks klienta: `/api/orders/{orderId}/payments` — JWT Bearer konta CUSTOMER będącego właścicielem zamówienia.

Prefiks gościa: `/api/guest/orders/{orderId}/payments` — nagłówek `X-Purchase-Token` z podsumowania zakupu, ważny do pierwotnego terminu 24 h. Token koszyka, sam numer zamówienia ani ID płatności nie dają dostępu. Logowanie po zakupie gościnnym nie zmienia właściciela; nadal potrzebny jest token zakupu.

| Metoda i ścieżka względem prefiksu | Żądanie | Odpowiedź |
|---|---|---|
| POST | `{"requestKey":"UUID"}` | 201, próba i Location do jej odczytu |
| GET | query `page=0&size=20` | 200, strona historii prób |
| GET /{id} | — | 200, stan zapisanej próby |
| POST /{id}/simulation-result | `{"outcome":"SUCCEEDED"}`, `FAILED` lub `CANCELLED` | 200, zakończona próba |

Próba zawiera `id`, `orderId`, `amountGross`, `currency`, `status`, `createdAt`, `finishedAt`, `reason` i `simulation: true`. Czasy są UTC ISO 8601. Oczekująca próba ma `finishedAt` i `reason` równe null. Kwota brutto i PLN pochodzą wyłącznie z kopii zamówienia, łącznie z dostawą; późniejsza zmiana ceny katalogowej nie zmienia płatności. Nie przesyłaj kwot ani właściciela w żądaniu — nieznane pola są odrzucane.

Historia ma `items`, `page`, `size`, `totalElements`, `totalPages`. Strony od 0, maksymalnie 1 000 000, size 1–100. Sortowanie: createdAt malejąco, następnie id rosnąco. Odczyt nie zmienia stanu; przed wykonaniem zadania wygaszającego próba może jeszcze mieć PENDING. Wynik sukcesu zawsze ponownie sprawdza termin.

## Przebieg i ponowienia

1. Utwórz zamówienie zgodnie z [API zamówień](order-api.md). Jego rezerwacja już biegnie; rozpoczęcie płatności nie ustawia nowego terminu.
2. POST płatności z nowym losowym requestKey. Zachowaj ten klucz do ponowienia tego samego żądania po utracie odpowiedzi. Para (orderId, requestKey) jest unikalna. Ponowienie zwraca tę samą próbę i jej aktualny stan, również po jej zakończeniu (201 i ten sam Location).
3. Na przyszłym ekranie symulatora odczytaj próbę i wyślij wybrany wynik. Dozwolone przejścia: PENDING → SUCCEEDED / FAILED / CANCELLED. Nie ma przejść między stanami końcowymi.
4. SUCCEEDED w tej samej transakcji ustawia zamówienie na PAID, zapisuje paid_at, zmniejsza stan fizyczny i rezerwację oraz tworzy wpis SALE dla każdej pozycji.
5. FAILED lub CANCELLED kończą jedynie próbę. Zamówienie i jego rezerwacja pozostają aktywne do pierwotnego terminu. Nowa próba wymaga nowego requestKey. Jednocześnie może oczekiwać najwyżej jedna próba.
6. Powtórzenie identycznego końcowego wyniku zwraca zapisany rezultat, bez ponownego wydania towaru. Inny wynik oznacza konflikt. Powtórzenie już przyjętego sukcesu działa także po terminie rezerwacji; nie jest nową płatnością.

Powrót przeglądarki ani query typu success=true nie potwierdza zapłaty. Frontend pobiera stan próby i zamówienia z backendu. Endpoint simulation-result jest świadomie dostępną właścicielowi kontrolką symulatora, a nie webhookiem rzeczywistego operatora płatności.

Anulowanie zamówienia lub jego wygaśnięcie kończy oczekującą próbę jako CANCELLED z reason ORDER_CANCELLED lub EXPIRED. Próby zakończone wcześniej pozostają bez zmian. Wyniki symulatora zapisują reason SIMULATED_SUCCEEDED, SIMULATED_FAILED lub SIMULATED_CANCELLED.

## Spójność i błędy

Wszystkie zmiany płatności blokują najpierw ten sam wiersz zamówienia co anulowanie i zadanie wygaszania. Magazyn blokowany jest następnie w kolejności UUID produktów. Czas sukcesu jest sprawdzany ponownie po uzyskaniu wszystkich blokad magazynu: wymagane jest now < reservationExpiresAt, także gdy żądanie czekało na korektę stanu.

Zmiana zamówienia, próby, magazynu i wpisy SALE są jedną transakcją. Synchroniczne zdarzenie anulowania zamówienia kończy oczekującą próbę w tej samej transakcji. Nie ma brokera ani operacji w tle poza istniejącym zadaniem wygaszania.

Wygaśnięcie wykryte podczas POST płatności zwalnia rezerwację i zamyka próbę, nawet gdy odpowiedź to 409. Serwis zwraca taki wynik dopiero po zatwierdzeniu transakcji; kontroler mapuje go na ProblemDetail. Nieoczekiwany błąd zapisu cofa całą operację.

| HTTP / code | Znaczenie |
|---|---|
| 400 / INVALID_REQUEST, VALIDATION_FAILED | Niepoprawny JSON, enum, UUID lub paginacja |
| 401 / AUTHENTICATION_REQUIRED | Brak prawidłowego JWT dla ścieżki klienta |
| 403 / ACCESS_DENIED | Rola inna niż CUSTOMER na ścieżce klienta |
| 404 / ORDER_NOT_FOUND, PAYMENT_NOT_FOUND | Brak uprawnienia do zamówienia lub próba nie należy do niego |
| 409 / PAYMENT_PENDING | Inna próba oczekuje |
| 409 / ORDER_NOT_PAYABLE | Zamówienie opłacone, anulowane lub wygasłe |
| 409 / PAYMENT_EXPIRED | Termin minął podczas oczekiwania na blokady magazynu |
| 409 / PAYMENT_RESULT_CONFLICT | Sprzeczny wynik zakończonej próby |
| 409 / PAYMENT_AMOUNT_MISMATCH | Zapisana kwota/waluta próby nie odpowiada zamówieniu |

Pracownik i administrator nie mogą ręcznie ustawiać PAID ani wykonywać wydania SALE. W historii magazynu SALE ma autora-system (actorAccountId null), orderId i ujemną quantityDelta; przyjęcia/korekty mają autora-pracownika i orderId null.

V7 tworzy payment_attempt, dodaje paid_at i powiązanie SALE z pozycją zamówienia. Unikalne indeksy pozwalają na najwyżej jedną próbę PENDING i jedną SUCCEEDED na zamówienie; UNIQUE(order_id, product_id) chroni pojedyncze wydanie pozycji.

V8 dodaje [linki śledzenia gościa i e-mail po zapłacie](tracking-api.md). Gość pobiera pierwszy link przez GET /api/guest/orders/{id}/tracking-link z X-Purchase-Token; klient nadal korzysta z własnego zamówienia. V9 dodaje [realizację zamówień przez pracownika](fulfillment-api.md). Ekran Angulara pozostaje do implementacji.
