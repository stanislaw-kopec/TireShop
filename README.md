# TireShop

## Backend — uruchomienie lokalne

Wymagania: JDK 25 (`JAVA_HOME` wskazujący katalog JDK) oraz uruchomiony Docker Desktop z kontenerami Linux. Maven pobiera się automatycznie przez wrapper.

Z katalogu projektu w PowerShell:

```powershell
cd backend
# Tylko przy pierwszej konfiguracji: wygeneruj sekrety bez wyświetlania w logach.
# Zachowaj wartości poza Git; w kolejnych terminalach ustaw te same sekrety.
$jwtBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($jwtBytes)
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
$trackingBytes = New-Object byte[] 32
$rng.GetBytes($trackingBytes)
$env:TRACKING_SECRET = [Convert]::ToBase64String($trackingBytes)
$rng.Dispose()
.\mvnw.cmd spring-boot:run
```

Domyślny profil `local` uruchamia PostgreSQL 17 oraz Mailpit (lokalny podgląd poczty) przez Docker Compose. Pierwszy start wymaga internetu do pobrania zależności i obrazów. Porty 8080, 15432, 1025 i 8025 muszą być wolne.

- Backend: http://localhost:8080
- Powitanie (`GET`, tekst `Hello World!`): http://localhost:8080/api/hello
- Stan aplikacji i połączenia z bazą: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI: http://localhost:8080/v3/api-docs
- Podgląd lokalnej poczty (konta oraz linki śledzenia po zapłacie): http://localhost:8025

Backend obejmuje konta, logowanie JWT, adresy, zaproszenia pracowników, resetowanie haseł, katalog, magazyn, koszyk, zamówienia z 15-minutową rezerwacją, symulator płatności, linki śledzenia gościa z pocztą po zapłacie oraz realizację zamówień przez pracownika. Pracownik tworzy/edytuje produkty i zapisuje dostawy lub korekty, administrator ustala ceny i dopuszcza sprzedaż. Publiczny katalog ma filtry, paginację i sortowanie po cenie. Backend nie udostępnia strony pod `/`.

V9 dodaje [API realizacji zamówień](docs/fulfillment-api.md): listę z filtrem statusu, szczegóły, kolejne etapy PAID → PROCESSING → SHIPPED → DELIVERED i numer przesyłki wymagany przy wysyłce. Korekta numeru jest możliwa przed doręczeniem; klient i gość widzą aktualny numer. Operacje wymagają roli EMPLOYEE oraz operationId zapewniającego bezpieczne ponowienia. Migracja Flyway nie wymaga nowych ustawień środowiska.

V8 dodaje [linki śledzenia gościa i kolejkę poczty](docs/tracking-api.md). Link jest ważny 90 dni, udostępnia wyłącznie status i numer przesyłki. Można go odnowić na e-mail zapisany w zamówieniu; odpowiedź API nie ujawnia tokenu ani istnienia zamówienia. Awaria SMTP nie cofa zapłaty; kolejka ponawia wysyłkę do 5 prób. Ustaw nowy, trwały TRACKING_SECRET (Base64 dokładnie 32 losowych bajtów) również w konfiguracji IntelliJ. Wygeneruj go raz i zachowaj poza repozytorium — nie generuj nowego przy każdym starcie, gdy istnieją już zaszyfrowane linki/wiadomości. TRACKING_LINK_URL domyślnie wskazuje przyszły Angular: http://localhost:4200/track-order. API można przetestować w Swaggerze bez frontendu.

V7 dodaje [API symulatora płatności](docs/payment-api.md): próby dla klienta i gościa, wybór sukcesu/odrzucenia/anulowania oraz bezpieczne ponowienia. Sukces ustawia PAID i atomowo wydaje towar z rezerwacji, zapisując SALE w historii magazynu. Kwota pochodzi z zamówienia; próby nie przedłużają 15 minut. Nie ma prawdziwych płatności ani formularza kart. Migracja uruchamia się standardowo przez Flyway; nie wymaga nowych zmiennych środowiska.

V5 dodaje koszyki i ich pozycje. [API koszyka](docs/cart-api.md) opisuje token gościa w `X-Cart-Token` (dla przyszłego Angulara: sessionStorage, bez cookie), podsumowanie cen i dostawy oraz scalanie po zalogowaniu. Konflikt dostępności zachowuje oba koszyki. Testy obejmują także prywatność koszyków, dostawę dla 3/4 sztuk, aktualizację cen, wygasanie tokenu i równoczesne ponowienia scalania.

V6 dodaje podsumowania i zamówienia. [API zamówień](docs/order-api.md) opisuje potwierdzanie aktualnej ceny, token zakupu gościa (`X-Purchase-Token`), ponawianie „Zapłać”, dostęp klienta i anulowanie nieopłaconych. Utworzenie zamówienia zapisuje dane historyczne, atomowo rezerwuje wszystkie sztuki i czyści koszyk. Zadanie co 15 sekund zwalnia wygasłe rezerwacje. Nie oznacza jeszcze zapłaty — status nowego zamówienia to PENDING_PAYMENT. Testy obejmują rywalizację o ostatnie sztuki, rollback, niezmienność cen/adresu oraz granicę wygaśnięcia i wyścig z anulowaniem.

Przełożenie materiałów z laboratoriów na Springa: [zasady laboratoryjne](docs/lab-guidelines.md).

Kontrakt i przykłady wywołań: [API kont i logowania](docs/backend-api.md), [API katalogu i magazynu](docs/catalog-api.md). W drugim dokumencie jest instrukcja przygotowania pierwszej oferty przez Swagger. Publiczna lista: http://localhost:8080/api/tires — początkowo pusta. Wymagania: [AGENTS.md](AGENTS.md). Projekt bazy: [docs/backend-design.md](docs/backend-design.md).

Springdoc używa poprawki 3.1.1 ze względu na [błąd generowania schematów parametrów z walidacją w 3.1.0](https://github.com/springdoc/springdoc-openapi/issues/3314), ujawniony przez filtry katalogu. Java i Spring Boot pozostają w dotychczasowych wersjach.

W IntelliJ ustaw `JWT_SECRET` w **Run → Edit Configurations → Environment variables** (wygenerowana wartość Base64 co najmniej 32 losowych bajtów; nie hasło wymyślone ręcznie). Bez poprawnego sekretu start zakończy się czytelnym błędem. Używaj tej samej wartości przy restartach, jeśli wcześniej wydane tokeny mają nadal działać; zmiana klucza je unieważnia. Nie zapisuj sekretu w współdzielonej konfiguracji IDE/Git. Lista ustawień: [backend/.env.example](backend/.env.example); plik jest wzorem, nie konfiguracją automatycznie wczytywaną przez Spring.

W IntelliJ uruchom klasę `com.shop.tire.Application` i wybierz JDK 25. Katalog roboczy może wskazywać główny katalog `TireShop` lub `backend`. Główny `compose.yaml` dołącza konfigurację z `backend/compose.yaml` (wymaga Docker Compose 2.20+); oba korzystają z tej samej bazy i wolumenu. Dla innego katalogu roboczego ustaw zmienną `COMPOSE_FILE` na pełną ścieżkę do `backend/compose.yaml`.

## Baza danych

Lokalne parametry: host `localhost`, port `15432`, baza i użytkownik `tireshop`, hasło `tireshop_local`. Są przeznaczone wyłącznie do pracy lokalnej. Baza jest dostępna tylko na interfejsie lokalnym, a dane pozostają w wolumenie Dockera po restarcie.

Zatrzymanie backendu: `Ctrl+C`. Baza pozostaje uruchomiona. Aby ją zatrzymać, wykonaj z katalogu `backend`:

```powershell
docker compose stop
```

Dla istniejącej bazy ustaw `SPRING_PROFILES_ACTIVE=external` oraz `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Profil ten nie uruchamia Dockera. `SERVER_PORT` zmienia port HTTP. Nie zapisuj rzeczywistych haseł w repozytorium. Pliki `.env` nie są automatycznie wczytywane przez Spring — ustaw zmienne w terminalu lub konfiguracji uruchomienia IDE.

Flyway wykonuje migracje z `backend/src/main/resources/db/migration`. V1 tworzy konta, adresy i jednorazowe tokeny. Historyczna V2 tworzyła sesje; V3 usuwa ich dwie techniczne tabele po przejściu na JWT. V4 dodaje producentów, opony, magazyn i historię zmian stanu. Dotychczasowe zalogowania sesyjne przestają działać, konta i hasła pozostają. Nie zmieniaj wykonanych migracji ani nie czyść bazy. Hibernate sprawdza zgodność schematu (`ddl-auto=validate`).

## Pierwszy administrator

W konfiguracji uruchomienia IntelliJ dodaj zmienne środowiskowe:

```text
ADMIN_BOOTSTRAP_ENABLED=true
ADMIN_EMAIL=twój-adres-administratora
ADMIN_PASSWORD=twoje-silne-hasło-od-12-do-128-znaków
```

Wprowadź własny poprawny e-mail i hasło. Po pierwszym starcie usuń te zmienne. Inicjalizacja tworzy administratora tylko wtedy, gdy żaden jeszcze nie istnieje; nie zmienia hasła istniejącego konta ani nie nadaje roli administratora istniejącemu klientowi. Bez tych zmiennych aplikacja działa, ale nie tworzy domyślnego administratora.

## JWT i poczta

Logowanie zwraca JSON z `accessToken`, `tokenType: Bearer` i `expiresIn: 900`. Kolejne chronione żądania wysyłają `Authorization: Bearer <accessToken>`. Nie ma cookie sesji ani pobierania CSRF. W Swaggerze wykonaj logowanie, a następnie wklej sam token w **Authorize**.

Token wygasa po 15 minutach; nie ma refresh tokenów. Po wygaśnięciu zaloguj się ponownie. Publiczna rejestracja tworzy klienta, ale nie loguje go automatycznie. `POST /api/auth/logout` unieważnia wszystkie dotychczasowe tokeny konta; klient usuwa też token z pamięci. Reset hasła lub wyłączenie konta również odbiera dostęp wcześniejszym tokenom.

Weryfikacja obejmuje HS256, podpis, czas, issuer, audience oraz aktualną rolę/status i wersję uwierzytelnienia konta. Backend nie przechowuje sesji HTTP, ale odczytuje konto, aby móc natychmiast odebrać dostęp. Nie umieszczaj tokenu w URL ani logach. Przyszły Angular powinien domyślnie przechowywać go w pamięci aplikacji.

`JWT_ISSUER` domyślnie wynosi `tireshop`, `JWT_AUDIENCE` — `tireshop-api`, a `JWT_ACCESS_TOKEN_TTL` — `15m` (dopuszczalne 1s–15m). `CORS_ALLOWED_ORIGINS` to lista originów oddzielonych przecinkiem; profil lokalny dopuszcza `http://localhost:4200`, pozostałe profile nie dopuszczają domyślnie obcych originów. Nie wysyłamy credentials/cookies; Angular może też użyć proxy `/api`.

Zaproszenie pracownika jest ważne 24 godziny, reset hasła 30 minut. Wiadomości lokalne trafiają do Mailpit, bez wysyłki do rzeczywistych odbiorców. Link prowadzi domyślnie do przyszłego frontendu `http://localhost:4200/set-password`; do czasu zbudowania Angulara token można odczytać w Mailpit i przekazać do API opisanego w dokumentacji.

W profilu `external` skonfiguruj także `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM`, opcjonalnie `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_AUTH`, `MAIL_STARTTLS` oraz `PASSWORD_LINK_URL` (stały adres zaufanego frontendu). Poza lokalnym developmentem przesyłaj JWT wyłącznie przez HTTPS.

Wysyłka zaproszeń/resetów jest synchroniczna: brak dostępu do poczty zwraca 503 i wycofuje zmianę tokenu/konta, umożliwiając ponowienie. Te wiadomości nie korzystają z kolejki; trwała kolejka i automatyczne ponowienia dotyczą linków śledzenia zamówień gościa.

## Testy i budowanie

Z katalogu `backend`:

```powershell
.\mvnw.cmd verify
```

Testy HTTP używają osobnego PostgreSQL 17 uruchamianego przez Testcontainers. Wymagają działającego Dockera i nie dotykają lokalnej bazy sklepu. Sprawdzają migracje, JWT (w tym błędne i wygasłe tokeny), brak sesji, uprawnienia, CORS, konta, katalog, magazyn, koszyki, zamówienia, płatności i realizację. Obejmują także ponowienia, rollback i równoległe operacje na stanie. Sekrety są generowane wyłącznie w testach; nie trzeba ustawiać własnego `JWT_SECRET` ani `TRACKING_SECRET`.

Większość testów używa profilu `test` i kontrolowanego nadajnika poczty, m.in. do wymuszania awarii SMTP. `MvpJourneyIntegrationTests` uruchamia pełną aplikację w profilu `external` z izolowanymi PostgreSQL i Mailpit, wykonuje zakupy klienta/gościa przez HTTP i sprawdza prawdziwą wysyłkę SMTP. Uruchamia aplikację ponownie na tej samej bazie, aby sprawdzić migracje, trwałość danych, odzyskanie linku i wykonanie zaległych zadań. [Wynik końcowej weryfikacji MVP](docs/mvp-verification.md).

Wynik budowania: `backend/target/demo-0.0.1-SNAPSHOT.jar`. Przy uruchamianiu JAR zapewnij działającą bazę i użyj profilu `external` z właściwymi zmiennymi połączenia.

## IntelliJ — błędy importu Maven i JVM target 5

Wrapper używa Maven 3.9.9, aby ominąć problem importu Maven 3.10 (`ArtifactHandlerManager`) w IntelliJ. W `pom.xml` jawnie ustawiono kompilację dla Java 25.

Po zmianie konfiguracji:

1. W oknie Maven wybierz **Reload All Maven Projects**.
2. W **Project Structure → Project** wybierz SDK 25 i poziom języka 25.
3. W **Settings → Build Tools → Maven** wybierz **Use Maven wrapper**; w **Importing** i **Runner** wybierz JDK projektu (25).
4. W konfiguracji uruchomienia `Application` pozostaw katalog roboczy projektu (`TireShop`) lub wybierz `backend`.

Jeżeli JDK 25 nie ma na liście IDE, dodaj istniejący katalog `C:\Program Files\Java\jdk-25` przez **Add SDK → JDK**. Nie ustawiaj targetu 5 ani JDK 8 — projekt wymaga JDK 25.
