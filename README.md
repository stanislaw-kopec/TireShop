# TireShop

## Backend — uruchomienie lokalne

Wymagania: JDK 25 (`JAVA_HOME` wskazujący katalog JDK) oraz uruchomiony Docker Desktop z kontenerami Linux. Maven pobiera się automatycznie przez wrapper.

Z katalogu projektu w PowerShell:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Domyślny profil `local` uruchamia PostgreSQL 17 oraz Mailpit (lokalny podgląd poczty) przez Docker Compose. Pierwszy start wymaga internetu do pobrania zależności i obrazów. Porty 8080, 15432, 1025 i 8025 muszą być wolne.

- Backend: http://localhost:8080
- Powitanie (`GET`, tekst `Hello World!`): http://localhost:8080/api/hello
- Stan aplikacji i połączenia z bazą: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI: http://localhost:8080/v3/api-docs
- Podgląd lokalnych zaproszeń i resetów haseł: http://localhost:8025

Pierwszy etap obejmuje konta, logowanie sesyjne, adresy, zaproszenia pracowników i resetowanie haseł przez administratora. Katalog, magazyn, koszyk, zamówienia i płatności są opisane w projekcie, ale nie są jeszcze zaimplementowane. Backend nie udostępnia strony pod `/`.

Kontrakt i przykłady wywołań: [API kont i logowania](docs/backend-api.md). Wymagania: [AGENTS.md](AGENTS.md). Projekt bazy: [docs/backend-design.md](docs/backend-design.md).

W IntelliJ uruchom klasę `com.shop.tire.Application` i wybierz JDK 25. Katalog roboczy może wskazywać główny katalog `TireShop` lub `backend`. Główny `compose.yaml` dołącza konfigurację z `backend/compose.yaml` (wymaga Docker Compose 2.20+); oba korzystają z tej samej bazy i wolumenu. Dla innego katalogu roboczego ustaw zmienną `COMPOSE_FILE` na pełną ścieżkę do `backend/compose.yaml`.

## Baza danych

Lokalne parametry: host `localhost`, port `15432`, baza i użytkownik `tireshop`, hasło `tireshop_local`. Są przeznaczone wyłącznie do pracy lokalnej. Baza jest dostępna tylko na interfejsie lokalnym, a dane pozostają w wolumenie Dockera po restarcie.

Zatrzymanie backendu: `Ctrl+C`. Baza pozostaje uruchomiona. Aby ją zatrzymać, wykonaj z katalogu `backend`:

```powershell
docker compose stop
```

Dla istniejącej bazy ustaw `SPRING_PROFILES_ACTIVE=external` oraz `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Profil ten nie uruchamia Dockera. `SERVER_PORT` zmienia port HTTP. Nie zapisuj rzeczywistych haseł w repozytorium. Pliki `.env` nie są automatycznie wczytywane przez Spring — ustaw zmienne w terminalu lub konfiguracji uruchomienia IDE.

Flyway wykonuje migracje z `backend/src/main/resources/db/migration`: konta, adresy, jednorazowe tokeny oraz techniczne tabele sesji. Hibernate sprawdza zgodność schematu (`ddl-auto=validate`). Sesje są przechowywane przez Spring Session JDBC w PostgreSQL.

## Pierwszy administrator

W konfiguracji uruchomienia IntelliJ dodaj zmienne środowiskowe:

```text
ADMIN_BOOTSTRAP_ENABLED=true
ADMIN_EMAIL=twój-adres-administratora
ADMIN_PASSWORD=twoje-silne-hasło-od-12-do-128-znaków
```

Wprowadź własny poprawny e-mail i hasło. Po pierwszym starcie usuń te zmienne. Inicjalizacja tworzy administratora tylko wtedy, gdy żaden jeszcze nie istnieje; nie zmienia hasła istniejącego konta ani nie nadaje roli administratora istniejącemu klientowi. Bez tych zmiennych aplikacja działa, ale nie tworzy domyślnego administratora.

## Sesje i poczta

Logowanie używa JSON, sesji w cookie `SESSION` i ochrony CSRF. Przed pierwszą operacją POST/PUT i po logowaniu/wylogowaniu pobierz `GET /api/auth/csrf`, a następnie wysyłaj wartość cookie `XSRF-TOKEN` w nagłówku `X-XSRF-TOKEN`. Cookie sesji jest HttpOnly. Swagger wymaga również pobrania CSRF przed operacjami zapisu.

Sesja wygasa po 30 minutach bezczynności lub po 8 godzinach od logowania. Po zmianie hasła wszystkie wcześniejsze sesje przestają dawać dostęp. Publiczna rejestracja zawsze tworzy klienta i nie loguje go automatycznie.

Zaproszenie pracownika jest ważne 24 godziny, reset hasła 30 minut. Wiadomości lokalne trafiają do Mailpit, bez wysyłki do rzeczywistych odbiorców. Link prowadzi domyślnie do przyszłego frontendu `http://localhost:4200/set-password`; do czasu zbudowania Angulara token można odczytać w Mailpit i przekazać do API opisanego w dokumentacji.

W profilu `external` skonfiguruj także `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM`, opcjonalnie `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_AUTH`, `MAIL_STARTTLS` oraz `PASSWORD_LINK_URL` (stały adres zaufanego frontendu). Poza profilem lokalnym cookie wymaga HTTPS; tylko podczas świadomego lokalnego testu HTTP ustaw `SESSION_COOKIE_SECURE=false`.

W tym etapie wysyłka zaproszeń/resetów jest synchroniczna: brak dostępu do poczty zwraca 503 i wycofuje zmianę tokenu/konta, umożliwiając ponowienie. Nie ma jeszcze trwałej kolejki ani automatycznego ponawiania wysyłki.

## Testy i budowanie

Z katalogu `backend`:

```powershell
.\mvnw.cmd verify
```

Testy HTTP używają profilu `test` oraz osobnego PostgreSQL 17 uruchamianego przez Testcontainers. Wymagają działającego Dockera i nie dotykają lokalnej bazy sklepu. Sprawdzają migracje, sesje, CSRF, uprawnienia, adresy, zaproszenia i resetowanie haseł. Poczta w testach jest zastąpiona kontrolowanym nadajnikiem.

Wynik budowania: `backend/target/demo-0.0.1-SNAPSHOT.jar`. Przy uruchamianiu JAR zapewnij działającą bazę i użyj profilu `external` z właściwymi zmiennymi połączenia.

## IntelliJ — błędy importu Maven i JVM target 5

Wrapper używa Maven 3.9.9, aby ominąć problem importu Maven 3.10 (`ArtifactHandlerManager`) w IntelliJ. W `pom.xml` jawnie ustawiono kompilację dla Java 25.

Po zmianie konfiguracji:

1. W oknie Maven wybierz **Reload All Maven Projects**.
2. W **Project Structure → Project** wybierz SDK 25 i poziom języka 25.
3. W **Settings → Build Tools → Maven** wybierz **Use Maven wrapper**; w **Importing** i **Runner** wybierz JDK projektu (25).
4. W konfiguracji uruchomienia `Application` pozostaw katalog roboczy projektu (`TireShop`) lub wybierz `backend`.

Jeżeli JDK 25 nie ma na liście IDE, dodaj istniejący katalog `C:\Program Files\Java\jdk-25` przez **Add SDK → JDK**. Nie ustawiaj targetu 5 ani JDK 8 — projekt wymaga JDK 25.
