# TireShop — internetowy sklep z oponami

TireShop to aplikacja sklepu internetowego umożliwiająca zakup opon jako gość lub zalogowany klient. Obejmuje katalog z filtrami, koszyk, składanie zamówień, symulator płatności oraz panele klienta, pracownika i administratora.

Frontend powstał w **Angularze**, a backend w **Javie i Spring Boot**. Dane są przechowywane w **PostgreSQL**. Angular komunikuje się z rzeczywistym REST API — ceny, dostępność, uprawnienia, płatności i statusy zamówień są sprawdzane po stronie backendu.

Projekt został przygotowany na zajęcia z budowania aplikacji internetowych. Wykorzystuje zasady omawiane na przykładach ExpressJS, dostosowane do Springa. Płatności są symulowane, a poczta w środowisku demo trafia do lokalnego podglądu Mailpit.

## Spis treści

- [Uruchomienie w jednej komendzie](#uruchomienie-w-jednej-komendzie)
- [Dane demo i krótki scenariusz prezentacji](#dane-demo-i-krótki-scenariusz-prezentacji)
- [Funkcjonalności i role](#funkcjonalności-i-role)
- [Przebieg zakupu i reguły sklepu](#przebieg-zakupu-i-reguły-sklepu)
- [Jak działa aplikacja od strony backendu](#jak-działa-aplikacja-od-strony-backendu)
- [Zastosowane technologie](#zastosowane-technologie)
- [Architektura i organizacja kodu](#architektura-i-organizacja-kodu)
- [Aktualny diagram bazy danych — ERD](#aktualny-diagram-bazy-danych--erd)
- [Najważniejsze rozwiązania techniczne](#najważniejsze-rozwiązania-techniczne)
- [Praca lokalna](#praca-lokalna)
- [Testy i ciągła integracja](#testy-i-ciągła-integracja)
- [Scenariusz nagrania demo — do 5 minut](#scenariusz-nagrania-demo--do-5-minut)
- [Zakres wersji demonstracyjnej](#zakres-wersji-demonstracyjnej)
- [Dokumentacja szczegółowa](#dokumentacja-szczegółowa)

## Uruchomienie w jednej komendzie

### Wymagania

- Docker z Docker Compose **2.20 lub nowszym**; na Windows można użyć Docker Desktop z kontenerami Linux.
- Uruchomiony silnik Dockera oraz wolne porty `4200`, `8081` i `8026`.
- Internet podczas pierwszego budowania, aby pobrać obrazy i zależności.

Do uruchomienia całego demo nie trzeba instalować lokalnie Javy, Mavena ani Node.js.

### Start

Otwórz terminal w głównym katalogu projektu, w którym znajduje się plik `compose.yaml`, i wykonaj:

```sh
docker compose up
```

Compose buduje frontend i backend, uruchamia PostgreSQL oraz Mailpit, wykonuje migracje bazy i przygotowuje dane demonstracyjne. Poczekaj na zakończenie budowania i uruchomienie backendu — pierwsze uruchomienie może potrwać kilka minut.

| Element | Adres |
|---|---|
| Sklep i panele użytkowników | [http://localhost:4200](http://localhost:4200) |
| Swagger UI — interaktywna dokumentacja API | [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html) |
| Specyfikacja OpenAPI | [http://localhost:8081/v3/api-docs](http://localhost:8081/v3/api-docs) |
| Stan backendu i połączenia z bazą | [http://localhost:8081/actuator/health](http://localhost:8081/actuator/health) |
| Mailpit — podgląd wiadomości | [http://localhost:8026](http://localhost:8026) |

### Konta demonstracyjne

| Rola | E-mail | Przykładowe zastosowanie |
|---|---|---|
| Klient | `customer@demo.test` | Zakup, edycja adresu, historia zamówień. |
| Klient z rozbudowaną historią | `history@demo.test` | Paginacja zamówień i płatności; zakup bez zapisanego adresu. |
| Pracownik | `employee@demo.test` | Produkty, magazyn i realizacja zamówień. |
| Administrator | `admin@demo.test` | Ceny, dopuszczenie do sprzedaży i zarządzanie kontami. |

Wspólne początkowe hasło jest generowane przy pierwszym starcie i zapisywane w **`.demo/password.txt`**. Nie ma stałego hasła w kodzie. Można też zarejestrować własnego klienta lub kupić jako gość.

Jeśli baza ma już administratora, demo wykorzystuje jego konto bez zmiany hasła; użyj dotychczasowych danych logowania. Hasła zmienione podczas prezentacji pozostają zachowane.

### Zatrzymanie i ponowne uruchomienie

- `Ctrl+C` zatrzymuje aplikację uruchomioną w terminalu.
- `docker compose up -d` uruchamia ją w tle; `docker compose stop` zatrzymuje usługi.
- Po zmianach w kodzie użyj `docker compose up --build`, aby przebudować obrazy.
- `docker compose logs -f backend` pokazuje logi backendu.

PostgreSQL przechowuje dane w wolumenie projektu Compose `tireshop-demo`. Katalog `.demo/` zawiera hasło początkowe i trwałe sekrety — zachowaj go razem z bazą. Zwykłe zatrzymanie kontenerów nie usuwa danych. Nie używaj `docker compose down -v`, jeśli chcesz je zachować.

### Aktualizacja istniejącego demo

Po pobraniu zmian uruchom z głównego katalogu:

```sh
docker compose up --build
```

Seed rozpoznaje wykonane wersje i dodaje brakujące przykłady jednorazowo. Rozszerzenie `presentation-v2` dodaje dziewięć produktów, konto `history@demo.test` oraz nowe zamówienia, zachowując wcześniejsze konta, ceny, koszyki i zamówienia. Nie trzeba usuwać bazy ani plików `.demo`. Przy uruchamianiu z IDE/Mavena nowe konta wymagają ponownego ustawienia `DEMO_PASSWORD`; Compose przekazuje je z istniejącego pliku automatycznie. Zajęty adres nowego konta powoduje przerwanie aktualizacji, zamiast przejęcia konta.

## Dane demo i krótki scenariusz prezentacji

Poniższe liczby dotyczą świeżej bazy, przed własnymi zakupami i zmianami podczas prezentacji.

| Dane | Co można pokazać |
|---|---|
| 24 produkty, w tym 21 w publicznym katalogu | Filtry czterech producentów, trzech rozmiarów i wszystkich sezonów, sortowanie oraz paginację. |
| `Demo 13`, `Demo 14`, `Demo 15` | Odpowiednio brak ceny, wyłączenie ze sprzedaży i zerowy stan magazynowy. |
| `customer@demo.test` | Zapisany adres, koszyk z 3 sztukami oraz 6 zamówień reprezentujących wszystkie statusy. |
| `history@demo.test` | Brak adresu w profilu, 21 zamówień oraz opłacone zamówienie z 21 próbami płatności: odrzucenia, anulowania i sukces. |
| Historia magazynu `Demo 16` | 23 wpisy: przyjęcie, korekty dodatnie i ujemne oraz wydania po zapłacie. |
| 3 zamówienia gości | Dwa opłacone z linkami śledzenia w Mailpit oraz jedno anulowane. |
| 30 zamówień i 50 wpisów audytu | Paginację zamówień oraz dziennik utworzeń i anulowań z filtrowaniem. |

Ogólna kolejność poznawania aplikacji (scenariusz filmu z limitem czasu znajduje się w sekcji [nagranie demo](#scenariusz-nagrania-demo--do-5-minut)):

1. **Gość:** wybierz oponę, przejdź przez koszyk i adres do symulatora. Odrzuć próbę, ponów płatność i potwierdź sukces. Otwórz link śledzenia oraz wiadomość w Mailpit.
2. **Klient:** dodaj sztukę do koszyka gościa i zaloguj się jako `customer@demo.test`. Na świeżym demo scalenie z trzema zapisanymi sztukami daje cztery sztuki i darmową dostawę. Pokaż też edycję adresu i historię zakupów.
3. **Historia płatności:** zaloguj `history@demo.test`, otwórz zamówienie opłacone (`PAID`) i wybierz **Pokaż historię płatności**. Przejdź na drugą stronę wyników.
4. **Pracownik:** w panelu otwórz `Demo 16` i historię magazynu. Następnie pokaż dostawę towaru oraz realizację zamówienia z numerem przesyłki.
5. **Administrator:** ustaw cenę i dopuszczenie do sprzedaży, otwórz **Dziennik zdarzeń**, pokaż filtry i kolejne strony. Zaproś nowego pracownika lub zainicjuj reset hasła, a wiadomość otwórz w Mailpit.

Nieopłacone przykładowe zamówienia wygasają po **15 minutach**; restart nie odnawia rezerwacji. Rejestrację, zaproszenie pracownika, reset hasła i scalanie koszyków wykonuje się podczas pokazu. Link śledzenia gościa nie daje dostępu do historii płatności — tę pokaż w kontekście nowego zakupu w tej samej karcie. Pełny scenariusz i zasady aktualizacji: [profil demo](docs/demo.md).

## Funkcjonalności i role

| Użytkownik | Dostępne funkcje |
|---|---|
| Gość | Katalog i szczegóły opon, filtry rozmiaru/sezonu/producenta, sortowanie cen, paginacja, koszyk, zakup bez konta, symulator płatności i śledzenie opłaconego zamówienia przez specjalny link. |
| Klient (`CUSTOMER`) | Rejestracja i logowanie, własny koszyk, scalenie koszyka gościa po zalogowaniu, edycja danych i adresu, zakup, historia i szczegóły własnych zamówień, status i numer przesyłki. |
| Pracownik (`EMPLOYEE`) | Dodawanie i edycja parametrów oraz zdjęcia produktu, przyjęcia i korekty magazynowe, historia magazynu, przegląd zamówień i danych dostawy, obsługa realizacji i numeru przesyłki. |
| Administrator (`ADMIN`) | Ustalanie cen, dopuszczanie i wycofywanie produktów ze sprzedaży, zapraszanie pracowników, inicjowanie resetu hasła klienta/pracownika oraz dziennik zdarzeń z filtrem i paginacją. |

Pracownik nie ustala cen. Administrator nie dziedziczy automatycznie uprawnień pracownika. Publiczna rejestracja tworzy wyłącznie konto klienta. Administrator otwiera **Panel personelu → Dziennik zdarzeń**, pracownik **Opony → wybrana opona → Pokaż historię magazynu**, a klient lub gość **szczegóły zamówienia → Pokaż historię płatności**. Wszystkie trzy widoki korzystają z API, mają paginację i odświeżanie. Publiczny link śledzenia gościa nie udostępnia historii płatności.

## Przebieg zakupu i reguły sklepu

1. Klient wybiera opony z katalogu i dodaje odpowiednią liczbę sztuk do koszyka.
2. Przechodzi do zakupu jako gość albo loguje się na konto. Koszyk gościa można połączyć z koszykiem konta.
3. Podaje kontakt i adres lub sprawdza dane pobrane z profilu. Widzi aktualne podsumowanie produktów, dostawy i całej kwoty.
4. Kliknięcie **„Zapłać”** tworzy zamówienie i rezerwuje towar na **15 minut**.
5. Symulator pozwala wybrać sukces, odrzucenie lub anulowanie próby płatności. Backend zapisuje wynik.
6. Po zapłacie klient śledzi zamówienie w swoim panelu, a gość otrzymuje link na ekranie potwierdzenia i w wiadomości e-mail.
7. Pracownik realizuje zamówienie, uzupełnia numer przesyłki i potwierdza doręczenie.

```text
PENDING_PAYMENT → PAID → PROCESSING → SHIPPED → DELIVERED
       ↓
   CANCELLED
```

Najważniejsze reguły:

- Ceny są podawane brutto w **PLN**. Dostawa kosztuje **20 zł dla 1–3 sztuk** i jest **bezpłatna od 4 sztuk**, niezależnie od modeli i producentów.
- Koszyk nie rezerwuje towaru. Nieopłacone zamówienie można anulować; po wygaśnięciu rezerwacji zostaje anulowane automatycznie.
- Odrzucenie lub anulowanie próby płatności pozwala ponowić ją do pierwotnego terminu rezerwacji, bez jego przedłużania.
- Backend oblicza kwoty i ponownie sprawdza dostępność. Zmiana ceny lub zawartości wymaga zatwierdzenia nowego podsumowania.
- Zamówienie zachowuje kopię produktów, cen i adresu. Późniejsze zmiany katalogu lub profilu nie zmieniają historii zakupów.
- Wysłanie wymaga numeru przesyłki. Pracownik może poprawić go przed doręczeniem.

## Jak działa aplikacja od strony backendu

### Od kliknięcia do zapisu w bazie

Przykład: klient zmienia ilość opon w koszyku. Angular wysyła `PUT /api/cart/items/{productId}` z ilością i nagłówkiem `Authorization: Bearer …`. Spring Security sprawdza token oraz rolę. Kontroler waliduje DTO i przekazuje serwisowi identyfikator klienta odczytany z uwierzytelnienia. Serwis sprawdza produkt, cenę i dostępność, a repozytorium zapisuje pozycję koszyka w PostgreSQL. Backend zwraca aktualną zawartość i wyliczone sumy, które Angular wyświetla na ekranie.

```text
Kliknięcie w Angularze → żądanie HTTP → Spring Security → kontroler i DTO
→ serwis i reguły biznesowe → repozytorium → PostgreSQL → odpowiedź JSON → widok
```

Niepoprawne dane, brak uprawnień lub konflikt dostępności kończą się odpowiednim błędem API. Frontend pokazuje komunikat; nie zastępuje kontroli wykonywanych przez backend. Dla gościa osobne endpointy używają tokenu koszyka `X-Cart-Token`, a operacje na zakupie wymagają odrębnego `X-Purchase-Token`.

### Zamówienie, magazyn i płatność

Koszyk zapisuje wybrane produkty i ilości, ale nie zajmuje towaru. Podsumowanie zakupu jest ważne 15 minut. Kliknięcie „Zapłać” ponownie sprawdza podsumowanie i dostępność, tworzy zamówienie oraz rozpoczyna osobną, 15-minutową rezerwację. Jedna transakcja obejmuje wszystkie pozycje; blokady w bazie chronią przed jednoczesnym sprzedaniem tych samych sztuk.

Próba płatności ma własny status, niezależny od realizacji zamówienia. Sukces zatwierdzony przez backend zmienia zamówienie na `PAID`, zmniejsza stan fizyczny i rezerwację oraz dopisuje wydanie magazynowe. Ponowienie tego samego wyniku nie wydaje towaru drugi raz. Anulowanie nieopłaconego zamówienia lub wygaśnięcie terminu zwalnia rezerwację; odrzucona próba płatności pozwala spróbować ponownie w pierwotnym terminie.

Usuwanie pozycji koszyka wykonuje rzeczywiste `DELETE`. Produkt można wycofać z oferty bez usuwania danych potrzebnych do historii. Zamówienia zachowują kopie cen, parametrów i adresu; anulowanie jest zmianą stanu, a nie kasowaniem historii.

### Logowanie i uprawnienia

**Uwierzytelnianie** odpowiada na pytanie „kto wykonuje żądanie”: po poprawnym logowaniu backend wydaje podpisany JWT ważny 15 minut. **Autoryzacja** określa „co ta osoba może zrobić”: klient zarządza swoimi danymi, pracownik magazynem i realizacją, a administrator cenami, ofertą i kontami pracowników. Backend sprawdza również właściciela konkretnego zasobu. Nie ma automatycznego dziedziczenia uprawnień pracownika przez administratora.

### Zdarzenia, zadania w tle i Mailpit

Utworzenie i anulowanie zamówienia publikują zdarzenia Springa. Listener audytu zapisuje wpis po zatwierdzeniu transakcji. Samo użycie zdarzenia lub listenera nie oznacza osobnego wątku: niezależne przetwarzanie w tle zapewniają tu zadania cykliczne, m.in. wysyłka wiadomości i zwalnianie wygasłych rezerwacji.

Po zapłacie gościa zadanie wysyłki linku trafia do `order_email_outbox` w tej samej transakcji co płatność. Scheduler sprawdza kolejkę co 15 sekund, wysyła wiadomość przez SMTP i w razie awarii ponawia ją do limitu pięciu prób. Awaria poczty nie cofa opłaconego zamówienia. Audyt ma prostszy mechanizm: błąd jego zapisu jest izolowany, ale nie ma automatycznego ponowienia utraconego wpisu.

**Mailpit to lokalny serwer SMTP z podglądem wiadomości w przeglądarce.** W demo backend wysyła do niego prawdziwą wiadomość SMTP, a Mailpit ją zatrzymuje zamiast dostarczać do zewnętrznej skrzynki. Adres testowy, np. `pokaz@demo.test`, nie musi istnieć. Pod [localhost:8026](http://localhost:8026) można zobaczyć odbiorcę, treść i otworzyć link śledzenia, zaproszenia lub resetu hasła. Link śledzenia daje gościowi wyłącznie odczyt statusu i numeru przesyłki. Zaproszenia oraz reset haseł są wysyłane synchronicznie, poza kolejką wiadomości o zamówieniach.

## Zastosowane technologie

Wersje odnoszą się do konfiguracji projektu.

| Obszar | Technologie i zastosowanie |
|---|---|
| Backend | **Java 25**, **Spring Boot 4.1.1**, Spring MVC — REST API. |
| Uwierzytelnianie | Spring Security, OAuth2 Resource Server — weryfikacja JWT Bearer i uprawnień. |
| Dane | **PostgreSQL 17**, Spring Data JPA/Hibernate oraz JDBC — trwały zapis, zapytania i blokady. |
| Migracje i walidacja | Flyway, Jakarta Bean Validation, ograniczenia bazy danych. |
| Dokumentacja i diagnostyka | Springdoc OpenAPI **3.1.1**, Swagger UI, Spring Boot Actuator. |
| Frontend | **Angular 21**, **TypeScript 5.9**, Router, HttpClient, Signals i RxJS. |
| Poczta | Spring Mail oraz **Mailpit** do lokalnego podglądu wiadomości. |
| Budowanie i uruchamianie | Maven Wrapper **3.9.9**, Node.js **24**, npm, Docker Compose; Nginx serwuje frontend w kontenerze. |
| Testy | JUnit, Mockito, AssertJ, Testcontainers z PostgreSQL/Mailpit; Vitest dla Angulara. |
| CI | GitHub Actions — testy, budowanie aplikacji i obrazów Docker. |

## Architektura i organizacja kodu

Backend jest **modularnym monolitem**: jedna aplikacja z domenami kont, katalogu, magazynu, koszyka, zamówień, płatności i audytu. Przepływ żądania prowadzi przez kontroler, serwis i repozytorium do PostgreSQL.

```mermaid
flowchart LR
    UI[Angular w przeglądarce] -->|REST /api| API[Spring Boot]
    API --> DB[(PostgreSQL)]
    API -->|SMTP| MAIL[Mailpit]
    SW[Swagger UI] --> API
```

W wersji Docker Nginx udostępnia Angulara i przekazuje `/api/` do backendu. Port `8081` daje również bezpośredni dostęp do API i Swaggera.

```text
TireShop/
├── backend/                 # Spring Boot, Maven Wrapper, Dockerfile
│   └── src/
│       ├── main/java/com/shop/tire/
│       │   ├── account/     # Konta, adresy, zaproszenia i reset haseł
│       │   ├── catalog/     # Opony i producenci
│       │   ├── inventory/   # Stany i ruchy magazynowe
│       │   ├── cart/        # Koszyki i scalanie
│       │   ├── order/       # Zakup, realizacja, śledzenie i kolejka poczty
│       │   ├── payment/     # Symulator płatności
│       │   ├── audit/       # Dziennik zdarzeń zamówień
│       │   ├── security/    # JWT i konfiguracja dostępu
│       │   ├── shared/      # Wspólna obsługa błędów i kontrakty
│       │   └── demo/        # Opcjonalne dane prezentacyjne
│       ├── main/resources/db/migration/  # Migracje Flyway
│       └── test/            # Testy jednostkowe i integracyjne
├── frontend/                # Angular, testy i konfiguracja Nginx
├── docs/                    # Kontrakty API i dokumentacja projektu
├── .github/workflows/ci.yml # Automatyczne sprawdzanie projektu
├── compose.yaml             # Całe demo
├── compose.local.yaml       # Usługi do lokalnej pracy nad backendem
└── AGENTS.md                # Uzgodnione wymagania i zasady backendu
```

Większe domeny mają podpakiety `controller`, `service`, `repository`, `dto`, `entity` i `model`. W razie potrzeby występują także `config`, `event` i `job`. Szczegóły: [struktura backendu](docs/backend-structure.md).

## Aktualny diagram bazy danych — ERD

Model odpowiada migracjom **V1–V11** w [backend/src/main/resources/db/migration](backend/src/main/resources/db/migration). Obejmuje **19 tabel aplikacji**; nie liczymy technicznej tabeli Flyway. Tabele sesji z V2 zostały usunięte przez V3, ponieważ logowanie korzysta z JWT.

Diagram podzielono na obszary dla czytelności; powtórzona nazwa oznacza tę samą tabelę. Pokazano wybrane pola, klucze i powiązania. `PK` oznacza klucz główny, `FK` obcy, `UK` unikalny. `||` oznacza dokładnie jeden, `o|` zero lub jeden, `o{` zero lub wiele, a `|{` jeden lub wiele. Złożony PK tworzą wspólnie pola oznaczone `PK` w jednej tabeli.

### Konta, katalog i stan magazynowy

```mermaid
erDiagram
    app_account ||--o| account_address : "adres profilu"
    app_account ||--o{ account_action_token : "zaproszenie lub reset"
    manufacturer ||--o{ tire_product : "producent"
    tire_product ||--|| inventory : "stan tworzony razem z produktem"

    app_account {
        uuid id PK
        varchar email UK
        varchar role
        varchar status
        bigint auth_version
    }
    account_address {
        uuid account_id PK, FK
        varchar recipient_name
        varchar street
        varchar city
    }
    account_action_token {
        uuid id PK
        uuid account_id FK
        varchar purpose
        varchar token_hash UK
        timestamptz expires_at
    }
    manufacturer {
        uuid id PK
        varchar name
        varchar normalized_name UK
    }
    tire_product {
        uuid id PK
        uuid manufacturer_id FK
        varchar model
        integer width_mm
        integer aspect_ratio
        integer rim_diameter_in
        varchar season
        numeric price_gross
        boolean on_sale
    }
    inventory {
        uuid product_id PK, FK
        integer on_hand
        integer reserved
        bigint version
    }
```

Produkt ma jeden stan magazynowy tworzony przez serwis w tej samej transakcji. Dostępność to `on_hand - reserved`; ograniczenie bazy pilnuje `0 <= reserved <= on_hand`. Ten sam wariant producenta, modelu, rozmiaru i sezonu nie może wystąpić drugi raz. Cena może być nieustalona, ale dopuszczenie do sprzedaży wymaga ceny dodatniej.

### Koszyk, zamówienie, płatności i realizacja

```mermaid
erDiagram
    app_account o|--o| shopping_cart : "account_id - koszyk konta"
    app_account o|--o{ shopping_cart : "merged_account_id - odbiorca scalenia"
    shopping_cart ||--o{ cart_item : "pozycje koszyka"
    tire_product ||--o{ cart_item : "wybrany produkt"
    shopping_cart ||--o{ checkout_quote : "podsumowania"
    app_account o|--o{ checkout_quote : "klient lub gosc"
    checkout_quote ||--o| customer_order : "najwyzej jedno zamowienie"
    app_account o|--o{ customer_order : "wlasciciel lub gosc"
    customer_order ||--|{ order_item : "pozycje zamowienia"
    tire_product ||--o{ order_item : "produkt z kopia danych"
    customer_order ||--o{ payment_attempt : "proby platnosci"
    customer_order ||--o{ order_access_token : "linki sledzenia"
    order_access_token ||--o| order_email_outbox : "zadanie wysylki"
    customer_order ||--o{ order_fulfillment_operation : "zmiany realizacji"
    app_account ||--o{ order_fulfillment_operation : "pracownik"
    tire_product ||--o{ inventory_change : "ruchy magazynowe"
    app_account o|--o{ inventory_change : "pracownik lub system"
    order_item o|--o| inventory_change : "jedno wydanie SALE"

    shopping_cart {
        uuid id PK
        uuid account_id FK, UK
        varchar guest_token_hash UK
        uuid merged_account_id FK
        timestamptz expires_at
    }
    cart_item {
        uuid cart_id PK, FK
        uuid product_id PK, FK
        integer quantity
    }
    checkout_quote {
        uuid id PK
        uuid cart_id FK
        uuid account_id FK
        jsonb snapshot
        timestamptz expires_at
    }
    customer_order {
        uuid id PK
        uuid quote_id FK, UK
        uuid checkout_key UK
        uuid account_id FK
        varchar status
        jsonb delivery
        numeric total_gross
        timestamptz reservation_expires_at
        varchar tracking_number
    }
    order_item {
        uuid order_id PK, FK
        uuid product_id PK, FK
        jsonb product_snapshot
        integer quantity
        numeric unit_price_gross
    }
    payment_attempt {
        uuid id PK
        uuid order_id FK
        uuid request_key
        varchar status
        numeric amount_gross
    }
    order_access_token {
        uuid id PK
        uuid order_id FK
        varchar token_hash UK
        timestamptz expires_at
    }
    order_email_outbox {
        uuid id PK, FK
        bytea payload
        varchar status
        integer attempts
        timestamptz next_attempt_at
    }
    order_fulfillment_operation {
        uuid id PK
        uuid order_id FK
        uuid actor_account_id FK
        varchar kind
        varchar from_status
        varchar to_status
    }
    inventory_change {
        uuid id PK
        uuid product_id FK
        uuid actor_account_id FK
        uuid order_id FK
        varchar type
        integer quantity_delta
    }
```

Koszyk i zamówienie gościa nie mają właściciela w `app_account`; dostęp opiera się na osobnych losowych tokenach. `merged_account_id` zachowuje wynik scalenia koszyka gościa, aby ponowienie nie podwajało ilości. Zamówienie zawiera co najmniej jedną pozycję, co egzekwuje logika zakupu. Para `(order_id, product_id)` w `inventory_change` jest złożonym FK do `order_item` i jest unikalna dla wydania po zapłacie. Przyjęcia i korekty nie mają `order_id`, a wydanie systemowe nie ma `actor_account_id`.

Jedno zamówienie może mieć wiele prób płatności, ale indeksy częściowe dopuszczają najwyżej jedną próbę oczekującą i jedną udaną. `order_fulfillment_operation` zapisuje wynik operacji pracownika dla bezpiecznych ponowień; nie jest pełną historią wszystkich statusów zamówienia.

### Audyt i pozostałe tabele techniczne

```mermaid
erDiagram
    audit_log {
        uuid id PK
        varchar event_type
        timestamptz occurred_at
        jsonb payload
    }
    tracking_request_limit {
        varchar key_hash PK
        timestamptz window_start
        integer requests
    }
    demo_seed_history {
        varchar name PK
        timestamptz applied_at
    }
```

Te trzy tabele nie mają FK do pozostałych tabel, dlatego nie łączymy ich sztucznymi relacjami. `audit_log` przechowuje w `payload` identyfikator zamówienia i wykonawcy oraz, przy anulowaniu, powód. `tracking_request_limit` ogranicza żądania odnowienia linku także dla nieistniejących numerów zamówień. `demo_seed_history` pamięta zastosowane wersje danych demo, aby restart ich nie odtwarzał. Historyczne decyzje projektowe opisuje [backend-design.md](docs/backend-design.md); aktualny model podsumowuje powyższy ERD, a pełną definicję określają migracje.

## Najważniejsze rozwiązania techniczne

- **SOLID, DRY i YAGNI:** podział odpowiedzialności między warstwy i domeny, wstrzykiwanie zależności przez konstruktor oraz współdzielenie reguł bez tworzenia dodatkowych frameworków.
- **Nowoczesna Java:** DTO głównie jako rekordy, enumy statusów, prywatne pola encji, `BigDecimal` dla pieniędzy, `Instant` i wstrzykiwany `Clock` dla czasu.
- **Spójne API:** globalny `@RestControllerAdvice`, odpowiedzi błędów `ProblemDetail` ze stałym `code`, walidacja żądań i odrzucanie nieznanych pól JSON. Encje JPA nie są zwracane bezpośrednio.
- **JWT zamiast sesji:** access token ważny 15 minut, podpis HS256, bez refresh tokenów. Backend sprawdza także aktualny stan i rolę konta. Wylogowanie unieważnia wszystkie dotychczasowe tokeny danego konta. Hasła są hashowane przez PBKDF2.
- **Ochrona danych:** kontrola roli i właściciela zasobu. Angular przechowuje JWT w pamięci; odświeżenie strony wymaga ponownego logowania. Koszyk gościa ma osobny token w `sessionStorage`, a zakup i link śledzenia korzystają z odrębnych tokenów dostępu.
- **Spójność zakupów:** transakcje i blokady w PostgreSQL chronią przed sprzedażą ponad stan. Klucze operacji pozwalają bezpiecznie ponawiać checkout, płatności i zmiany magazynowe. Rezerwacja wszystkich pozycji jest atomowa.
- **Migracje:** Flyway zarządza schematem, Hibernate sprawdza jego zgodność przez `ddl-auto=validate`. Seed demo jest oddzielony od migracji i nie resetuje danych przy restarcie.
- **Poczta po zapłacie:** kolejka w PostgreSQL ponawia wysyłkę linków śledzenia do pięciu prób. SMTP działa po zatwierdzeniu transakcji, więc awaria poczty nie cofa płatności. Treść kolejki jest szyfrowana osobnym sekretem.
- **Zdarzenia i audyt:** `OrderCreated` i `OrderCancelled` zasilają dziennik administratora. Audyt zapisuje się po commit w osobnej transakcji; błąd audytu nie cofa zamówienia. Jest to prosty mechanizm bez ponawiania utraconych wpisów.

Link śledzenia gościa jest ważny 90 dni i pozwala jedynie odczytać status oraz numer przesyłki. Odnowienie wysyła nowy link na e-mail zapisany w zamówieniu. Zaproszenia pracowników i resetowanie haseł korzystają z osobnych, jednorazowych linków; ich wysyłka jest synchroniczna, poza kolejką śledzenia.

## Praca lokalna

Do edycji backendu potrzebne są **JDK 25** i działający Docker, a do frontendu **Node.js 24** oraz npm. Maven jest dostarczany przez wrapper.

- Backend można uruchomić z IntelliJ przez `com.shop.tire.Application` albo Mavenem. Wymaga ustawienia `JWT_SECRET` i `TRACKING_SECRET`; profil `local` uruchamia PostgreSQL i Mailpit.
- Frontend uruchamia się z katalogu `frontend` przez `npm ci`, a następnie `npm start`.
- Proxy Angulara domyślnie wskazuje backend demo na porcie `8081`. Przy backendzie z IntelliJ/Mavena zmień cel w `frontend/proxy.conf.json` na `http://127.0.0.1:8080`.

Pełna instrukcja konfiguracji, generowania sekretów, pierwszego administratora i rozwiązywania problemów: **[praca lokalna i IntelliJ](docs/development.md)**. Obsługa oraz budowanie Angulara: [README frontendu](frontend/README.md).

## Testy i ciągła integracja

Z katalogu `backend`, w PowerShell:

```powershell
# Pełne sprawdzenie, wymagany działający Docker:
.\mvnw.cmd clean verify

# Tylko testy jednostkowe, bez Dockera:
.\mvnw.cmd -Punit test

# Tylko testy integracyjne:
.\mvnw.cmd -Pintegration verify
```

Na Linux/macOS użyj `sh ./mvnw` zamiast `.\mvnw.cmd`. Profile Maven `unit` i `integration` uruchamiaj osobno. Domyślne `verify` wykonuje oba zestawy.

Testy obejmują reguły koszyka i dostawy, JWT i uprawnienia, migracje, rezerwacje, płatności, realizację, audyt i seed. Sprawdzają także ponowienia, rollback, granice czasu i równoległą rywalizację o towar. Testcontainers tworzy izolowane kontenery PostgreSQL i Mailpit; testy nie korzystają z bazy demo. Własne sekrety do testów nie są wymagane. Pełna ścieżka HTTP obejmuje zakup klienta/gościa, pocztę SMTP i restart aplikacji.

Testy demo sprawdzają także aktualizację wcześniejszej wersji bez nadpisania kont, ceny i koszyka, kolizje adresów kont, brak duplikatów po restarcie oraz dostęp i paginację historii przez HTTP. Testy frontendu obejmują widoki historii płatności, magazynu i audytu.

Z katalogu `frontend`:

```sh
npm ci
npm test -- --watch=false
npm run build
```

[Workflow CI](.github/workflows/ci.yml) uruchamia się po pushu do `main` oraz dla pull requestów. Osobne zadania sprawdzają backend i frontend, a po testach budują ich obrazy Docker. Workflow nie wdraża aplikacji automatycznie. Aktualne wyniki są w zakładce [Actions](https://github.com/stanislaw-kopec/TireShop/actions).

## Scenariusz nagrania demo — do 5 minut

Plan zakłada **4 minuty 45 sekund treści i 15 sekund zapasu**. Film ma pokazać działające funkcje oraz krótko wyjaśnić architekturę, model danych, przepływ żądania, logowanie, uprawnienia i regułę biznesową. Nie trzeba nagrywać każdego endpointu ani wszystkich wariantów zakupu.

### Przygotowanie przed włączeniem nagrywania

1. Uruchom `docker compose up` wcześniej. Po zmianach kodu użyj `docker compose up --build` i poczekaj na gotowość aplikacji. Budowania ani pobierania zależności nie wliczaj do filmu.
2. Przygotuj karty: sklep jako gość, formularz logowania klienta, panel pracownika, panel administratora, [Mailpit](http://localhost:8026), README z ERD i [Swagger](http://localhost:8081/swagger-ui/index.html). Do personelu zaloguj się kilka minut przed nagraniem. Nie odświeżaj tych kart przez F5 — JWT jest w pamięci aplikacji, więc odświeżenie wymaga ponownego logowania.
3. W koszyku gościa przygotuj jedną sztukę dodatkowej opony. Podczas filmu dodasz trzy sztuki innego modelu, usuniesz przygotowaną pozycję i zmienisz pozostałą ilość z trzech na cztery. W ten sposób pokażesz dodawanie, edycję i usuwanie danych oraz próg darmowej dostawy.
4. Sprawdź dostępność przynajmniej czterech sztuk wybranego modelu. Przygotuj fikcyjne dane do szybkiego wpisania: `pokaz@demo.test`, telefon `500600700`, Jan Kowalski, Testowa 10, `00-001`, Warszawa, kraj `PL`. Numer mieszkania może pozostać pusty. Hasło kont odczytaj z `.demo/password.txt` poza nagraniem.
5. Dla pracownika przygotuj zamówienie `PAID` i znajdź produkt `Demo 16`. Dla administratora otwórz edycję dowolnej opony. Sprawdź, gdzie jest opłacone zamówienie konta `history@demo.test` z 21 próbami płatności. Nie opieraj pokazu na starym `PENDING_PAYMENT`: jego rezerwacja mogła już wygasnąć.
6. Ustaw czytelne powiększenie przeglądarki i przećwicz przełączanie kart ze stoperem. Każda próba nagrania tworzy prawdziwe dane demonstracyjne; restart ich nie usuwa. Jeśli wybrany model lub zamówienie zmieniło już stan podczas próby, przygotuj kolejny przykład.

### 0:00–0:20 — Cel aplikacji i uruchomienie

**Ekran:** katalog sklepu.

**Powiedz:**

> „TireShop to sklep z oponami, w którym można kupować jako gość lub klient. Frontend działa w Angularze, backend w Spring Boot, a dane zapisuję w PostgreSQL. Cały projekt uruchamia jedna komenda Docker Compose. Pokażę zakup oraz panele klienta, pracownika i administratora.”

### 0:20–0:55 — Filtry, koszyk i reguła dostawy

**Kliknij:** filtr sezonu lub rozmiaru → szczegóły dostępnej opony → dodaj 3 sztuki → koszyk. Usuń przygotowaną wcześniej dodatkową pozycję przyciskiem **Usuń**. Wskaż koszt dostawy dla pozostałych 3 sztuk, następnie zmień ich ilość na 4 i zatwierdź zmianę.

**Powiedz:**

> „Katalog ma filtry i sortowanie. W koszyku mogę dodawać, zmieniać ilości i usuwać pozycje. Po usunięciu dodatkowej pozycji mam trzy sztuki i dostawę za 20 zł. Zmieniam ilość na cztery — dostawa jest darmowa. Kwoty i dostępność sprawdza backend.”

### 0:55–1:55 — Zakup gościa i symulator płatności

**Kliknij:** **Przejdź dalej → Kup jako gość**. Uzupełnij przygotowany adres i kontakt → **Pokaż podsumowanie → Zapłać**. W symulatorze wybierz **Odrzuć płatność → Ponów płatność → Zatwierdź płatność**. Pokaż potwierdzenie i numer zamówienia.

**Powiedz podczas wypełniania i przechodzenia między ekranami:**

> „Gość podaje dane dostawy bez zakładania konta. Podsumowanie pokazuje pełną kwotę. Dopiero kliknięcie Zapłać tworzy zamówienie i rezerwuje towar na 15 minut. Symulator nie pobiera pieniędzy. Odrzucam pierwszą próbę i ponawiam płatność. Sukces potwierdza backend; ponowienie nie może wydać towaru drugi raz.”

### 1:55–2:30 — Logowanie klienta i historia

**Kliknij w osobnej karcie:** zaloguj `history@demo.test` → **Moje konto → Historia zamówień** → przygotowane zamówienie `PAID` → **Pokaż historię płatności**. Wskaż różne wyniki prób i przycisk kolejnej strony.

**Powiedz:**

> „Po zalogowaniu klient widzi własne dane i zamówienia. Historia płatności rozróżnia odrzucenia, anulowania i sukces, a listy mają paginację. Logowanie wydaje JWT ważny 15 minut. Backend sprawdza zarówno rolę, jak i właściciela zamówienia, więc klient nie może odczytać cudzych zakupów.”

### 2:30–2:55 — Wiadomość w Mailpit i link gościa

**Kliknij:** karta Mailpit → najnowsza wiadomość do `pokaz@demo.test` → link śledzenia. Wskaż odbiorcę wiadomości i status na stronie śledzenia.

**Powiedz:**

> „Mailpit przechwytuje lokalnie wiadomości wysyłane przez SMTP. Adres odbiorcy nie musi być prawdziwą skrzynką. Po zapłacie wiadomość trafia do kolejki i jest wysyłana w tle. Z tego linku gość może odczytać status bez logowania; sam numer zamówienia nie wystarcza.”

Jeżeli wiadomość jeszcze nie dotarła, przejdź do panelu pracownika i wróć do Mailpit w czasie zapasu. Scheduler sprawdza kolejkę co 15 sekund. Możesz też pokazać wcześniej przygotowaną wiadomość demo, wyraźnie wskazując, że dotyczy innego, wcześniejszego zamówienia.

### 2:55–3:35 — Pracownik: realizacja i magazyn

**Kliknij:** w przygotowanym zamówieniu `PAID` wybierz **Rozpocznij realizację**, wpisz numer `DEMO-2026-001` i kliknij **Oznacz jako wysłane**. Następnie pokaż **Opony → Demo 16 → Pokaż historię magazynu**.

**Powiedz:**

> „Pracownik realizuje opłacone zamówienia. Wysłanie wymaga numeru przesyłki, a backend pilnuje kolejności statusów. Pracownik zarządza także parametrami i stanem opon, ale nie cenami. Historia magazynu pokazuje przyjęcia, korekty i wydania po zapłacie.”

### 3:35–4:05 — Administrator: cena i audyt

**Kliknij:** w przygotowanej edycji opony zmień cenę, np. na `299,00`, i kliknij **Zapisz cenę**. Wskaż przycisk dopuszczenia lub wycofania ze sprzedaży. Wróć do panelu → **Dziennik zdarzeń** → filtr anulowanych zamówień.

**Powiedz:**

> „Administrator ustala cenę i dopuszczenie do sprzedaży. Może również tworzyć konta pracowników i inicjować reset hasła. Dziennik zdarzeń pokazuje utworzenia i anulowania zamówień. Zmiana ceny produktu nie zmienia kwot historycznych zakupów.”

### 4:05–4:45 — ERD, przepływ żądania i testy

**Pokaż:** drugi fragment ERD w tym README, następnie przygotowany Swagger. W diagramie wskaż `customer_order`, `order_item`, `tire_product` i `payment_attempt`; w Swaggerze grupy endpointów i **Authorize**. Nie wpisuj tokenu na nagraniu.

**Powiedz:**

> „Model łączy konto, koszyk, produkty, zamówienia i płatności. Zamówienie ma pozycje z kopią cen i może mieć wiele prób płatności. Backend jest modularnym monolitem: kontroler przyjmuje i waliduje żądanie, serwis wykonuje reguły biznesowe, a repozytorium zapisuje dane. Transakcje i blokady chronią magazyn. API dokumentuje Swagger, schemat wersjonuje Flyway, a testy jednostkowe i integracyjne sprawdzają reguły, uprawnienia oraz równoczesne zakupy.”

### 4:45–5:00 — Zapas i koniec nagrania

Zostaw ten czas na opóźnione przełączenie ekranu lub powrót do wiadomości. Nie rozpoczynaj kolejnego scenariusza. Jeśli film jest już kompletny, zakończ go przed pięcioma minutami. Przy przekroczeniu planu skróć pokaz historii magazynu i audytu; zachowaj zakup, logowanie, role, Mailpit i wyjaśnienie backendu. Nie czekaj na wygaśnięcie 15-minutowej rezerwacji i nie wykonuj dodatkowo całej rejestracji lub resetu hasła.

Po nagraniu sprawdź czytelność tekstu i dźwięku oraz długość filmu. Do oddania dołącz link do nagrania i repozytorium wraz z identyfikatorem ostatecznego commita lub tagu. Nie utożsamiaj bieżącego commita z wersją do oddania, dopóki nie zapiszesz wszystkich końcowych poprawek.

## Zakres wersji demonstracyjnej

Projekt realizuje podstawową ścieżkę działania sklepu. Symulator nie pobiera pieniędzy ani danych kart. Śledzenie pokazuje status ręcznie ustawiany przez pracownika i numer przesyłki, bez połączenia z API kuriera. Zdjęcia produktów są opcjonalnymi adresami HTTPS; nie ma uploadu plików.

Poza zakresem pozostają m.in. zwroty po zapłacie, faktury PDF, kupony, opinie, wiele magazynów i walut oraz prawdziwa bramka płatności. Domyślna konfiguracja Compose służy do lokalnej prezentacji. Wdrożenie publiczne wymaga własnej konfiguracji sekretów, bazy, poczty i HTTPS.

## Dokumentacja szczegółowa

| Dokument | Zawartość |
|---|---|
| [Demo](docs/demo.md) | Dane przykładowe i scenariusz prezentacji. |
| [Praca lokalna](docs/development.md) | IntelliJ, Maven, konfiguracja i rozwiązywanie problemów. |
| [Frontend](frontend/README.md) | Obsługa ekranów, komponenty i lokalne uruchomienie. |
| [Struktura backendu](docs/backend-structure.md) | Domeny, podpakiety i odpowiedzialności. |
| [Aktualny ERD](#aktualny-diagram-bazy-danych--erd) | Bieżące 19 tabel, klucze i relacje zgodne z migracjami V1–V11. |
| [Projekt bazy danych](docs/backend-design.md) | Historyczne decyzje i uzasadnienia modelu; aktualny ERD znajduje się w tym README. |
| [Konta i logowanie](docs/backend-api.md) | JWT, profil, adres, zaproszenia i reset hasła. |
| [Katalog i magazyn](docs/catalog-api.md) | Produkty, filtry, ceny i ruchy magazynowe. |
| [Koszyk](docs/cart-api.md) | Klient, gość, tokeny i scalanie. |
| [Zamówienia](docs/order-api.md) | Podsumowanie, checkout, rezerwacja i anulowanie. |
| [Płatności](docs/payment-api.md) | Próby, wyniki symulatora i ponowienia. |
| [Śledzenie](docs/tracking-api.md) | Linki gościa i kolejka poczty. |
| [Realizacja](docs/fulfillment-api.md) | Statusy pracownicze i numery przesyłek. |
| [Audyt](docs/audit-api.md) | Zdarzenia zamówień i API administratora. |
| [Zasady z laboratoriów](docs/lab-guidelines.md) | Przełożenie materiałów ExpressJS na Springa. |
| [Wymagania backendu](AGENTS.md) | Uzgodniony zakres i zasady implementacji. |
