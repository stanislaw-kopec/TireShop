# TireShop — aktualny model danych (ERD)

[Powrót do README](../README.md)


Model odpowiada migracjom **V1–V11** w [backend/src/main/resources/db/migration](../backend/src/main/resources/db/migration). Obejmuje **19 tabel aplikacji**; nie liczymy technicznej tabeli Flyway. Tabele sesji z V2 zostały usunięte przez V3, ponieważ logowanie korzysta z JWT.

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

Te trzy tabele nie mają FK do pozostałych tabel, dlatego nie łączymy ich sztucznymi relacjami. `audit_log` przechowuje w `payload` identyfikator zamówienia i wykonawcy oraz, przy anulowaniu, powód. `tracking_request_limit` ogranicza żądania odnowienia linku także dla nieistniejących numerów zamówień. `demo_seed_history` pamięta zastosowane wersje danych demo, aby restart ich nie odtwarzał. Historyczne decyzje projektowe opisuje [backend-design.md](backend-design.md); aktualny model podsumowuje powyższy ERD, a pełną definicję określają migracje.
