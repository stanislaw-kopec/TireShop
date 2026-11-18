# TireShop — praca lokalna

Do prezentacji całej aplikacji wystarczy `docker compose up` z głównego katalogu. Ten dokument opisuje uruchamianie backendu z IDE/Mavena oraz pracę nad frontendem. [Powrót do README](../README.md).

## Wymagania i środowiska

- JDK **25**, z `JAVA_HOME` wskazującym katalog JDK.
- Docker z działającymi kontenerami Linux i Compose **2.20+**.
- Node.js **24** i npm, jeśli uruchamiasz Angulara poza Dockerem.
- Maven Wrapper dołączony do backendu; nie trzeba osobnej instalacji Mavena.

| Element | Całe demo z głównego Compose | Backend lokalny + Angular deweloperski |
|---|---|---|
| Frontend | `http://localhost:4200` | `http://localhost:4200` |
| Backend | `http://localhost:8081` | `http://localhost:8080` |
| Mailpit | `http://localhost:8026` | `http://localhost:8025` |
| PostgreSQL | Dostępny w sieci kontenerów | `localhost:15432` |
| Projekt Compose | `tireshop-demo` | `tireshop` |

Oba warianty używają osobnych wolumenów bazy. Nie uruchamiaj dwóch frontendów jednocześnie na porcie 4200.

## Sekrety backendu

Backend wymaga dwóch niezależnych wartości:

- `JWT_SECRET` — Base64 co najmniej 32 losowych bajtów, do podpisywania JWT.
- `TRACKING_SECRET` — Base64 dokładnie 32 losowych bajtów, do szyfrowania treści kolejki i czasowej kopii linku śledzenia.

Wygeneruj je raz dla swojej lokalnej bazy i zachowaj poza repozytorium. Zmiana `JWT_SECRET` unieważnia wcześniejsze tokeny, a zmiana `TRACKING_SECRET` uniemożliwia odczyt wcześniej zaszyfrowanych treści. W demo kontenerowym wartości są przygotowywane automatycznie i zachowywane w `.demo/`.

Przykład pierwszego przygotowania w PowerShell, bez wypisywania wartości:

```powershell
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtBytes = New-Object byte[] 32
$trackingBytes = New-Object byte[] 32
$rng.GetBytes($jwtBytes)
$rng.GetBytes($trackingBytes)
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
$env:TRACKING_SECRET = [Convert]::ToBase64String($trackingBytes)
$rng.Dispose()
```

Zmienne z tego przykładu obowiązują w bieżącym terminalu i uruchomionych z niego procesach. Przed zamknięciem terminala zachowaj wartości w swoim lokalnym magazynie sekretów; przy kolejnych uruchomieniach wczytaj je ponownie zamiast generować nowe. Przykład nie konfiguruje już otwartego IntelliJ.

Plik [backend/.env.example](../backend/.env.example) jest wzorem ustawień. **Spring Boot nie wczytuje `.env` automatycznie** — użyj zmiennych środowiskowych terminala lub konfiguracji uruchomienia IDE. Nie zapisuj sekretów we współdzielonej konfiguracji Git.

## Backend przez Maven

Po ustawieniu sekretów, z głównego katalogu projektu:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Na Linux/macOS ustaw analogiczne zmienne środowiskowe i wykonaj `sh ./mvnw spring-boot:run` w katalogu `backend`.

Domyślny profil `local` uruchamia przez Compose PostgreSQL i Mailpit. Backend wystartuje na porcie 8080. Zwykły start nie dodaje danych demo; publiczny katalog nowej bazy jest pusty. Aby uruchomić seed, skorzystaj z [instrukcji profilu demo](demo.md#alternatywne-uruchomienie-z-ide-lub-maven).

- [Swagger UI](http://localhost:8080/swagger-ui/index.html)
- [Stan aplikacji](http://localhost:8080/actuator/health)
- [Publiczny katalog API](http://localhost:8080/api/tires)
- [Mailpit](http://localhost:8025)

Backend nie serwuje strony sklepu pod `/`; interfejs jest w Angularze.

`Ctrl+C` zatrzymuje backend. Usługi lokalne pozostają uruchomione, ponieważ Spring używa `lifecycle-management=start-only`. Zatrzymasz je z głównego katalogu:

```sh
docker compose -f compose.local.yaml stop
```

## Backend z IntelliJ

1. Zaimportuj `backend/pom.xml` jako projekt Maven.
2. W **Project Structure → Project** wybierz JDK 25 i poziom języka 25.
3. W **Settings → Build Tools → Maven** wybierz Maven Wrapper oraz JDK 25 dla importowania i uruchamiania Mavena.
4. W konfiguracji uruchomienia klasy `com.shop.tire.Application` dodaj `JWT_SECRET` i `TRACKING_SECRET` w **Environment variables**.
5. Ustaw katalog roboczy na katalog główny `TireShop` albo `backend` i uruchom aplikację.

Jeżeli JDK 25 nie ma na liście, dodaj jego lokalny katalog przez **Add SDK → JDK**. Dla innego katalogu roboczego ustaw `COMPOSE_FILE` na pełną ścieżkę do `backend/compose.yaml`.

## Pierwszy administrator bez seeda

W zwykłym profilu nie powstaje domyślne konto administratora. Na pierwszy start dodaj:

```text
ADMIN_BOOTSTRAP_ENABLED=true
ADMIN_EMAIL=<własny-poprawny-adres-email>
ADMIN_PASSWORD=<własne-hasło-12-do-128-znaków>
```

Po utworzeniu konta usuń te ustawienia. Bootstrap działa tylko przy braku administratora, nie zmienia hasła istniejącego konta i nie awansuje klienta do roli ADMIN. Przygotowanie oferty opisuje [API katalogu i magazynu](catalog-api.md).

## Frontend lokalny

W osobnym terminalu, z głównego katalogu projektu:

```sh
cd frontend
npm ci
npm start
```

Otwórz [http://localhost:4200](http://localhost:4200). `frontend/proxy.conf.json` przekazuje `/api/**` na port **8081**, czyli backend demo. Przy backendzie z IDE/Mavena ustaw w tym pliku `target` na `http://127.0.0.1:8080` i uruchom ponownie serwer Angulara.

Jeżeli port 4200 zajmuje kontener demo, zatrzymaj tylko jego frontend przez `docker compose stop frontend` z głównego katalogu. Backend demo może wtedy nadal obsługiwać lokalnego Angulara.

## Konfiguracja bazy, poczty i dostępu

Lokalna baza z `backend/compose.yaml`: host `localhost`, port `15432`, baza i użytkownik `tireshop`, hasło `tireshop_local`. Są to ustawienia wyłącznie lokalne. Flyway wykonuje nowe migracje przy starcie, a Hibernate sprawdza schemat przez `ddl-auto=validate`. Nie edytuj wykonanych migracji ani nie usuwaj bazy jako sposobu naprawienia ich historii.

| Zmienne | Zastosowanie |
|---|---|
| `SPRING_PROFILES_ACTIVE=external` | Start bez automatycznego uruchamiania usług Compose. |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Połączenie z przygotowaną bazą PostgreSQL. |
| `SERVER_PORT` | Port HTTP, domyślnie 8080. |
| `CORS_ALLOWED_ORIGINS` | Dozwolone originy oddzielone przecinkiem; lokalnie domyślnie `http://localhost:4200`. |
| `JWT_ISSUER`, `JWT_AUDIENCE` | Domyślnie `tireshop` i `tireshop-api`. |
| `JWT_ACCESS_TOKEN_TTL` | Domyślnie `15m`, dopuszczalny zakres od `1s` do `15m`. |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM` | Serwer SMTP i adres nadawcy. Lokalnie SMTP Mailpit używa portu 1025. |
| `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_AUTH`, `MAIL_STARTTLS` | Opcjonalne uwierzytelnianie i szyfrowanie połączenia SMTP. |
| `PASSWORD_LINK_URL` | Domyślnie `http://localhost:4200/set-password`. |
| `TRACKING_LINK_URL` | Domyślnie `http://localhost:4200/track-order`. |
| `ORDER_MAIL_JOB_ENABLED` | Włączenie obsługi kolejki poczty, domyślnie `true`. |

Profil `external` wymaga działających usług bazy i poczty oraz poprawnych sekretów. Wynik budowania Maven znajduje się w `backend/target/demo-0.0.1-SNAPSHOT.jar`; można uruchomić go przez `java -jar` z profilem `external` i odpowiednią konfiguracją środowiska.

W Swaggerze zaloguj się przez `POST /api/auth/login`, skopiuj `accessToken` i wklej sam token w **Authorize**. JWT wygasa po 15 minutach, bez automatycznego odświeżania. W Angularze token pozostaje w pamięci, więc odświeżenie strony także wymaga ponownego logowania. Zaproszenia pracowników są ważne 24 godziny, a linki resetu hasła 30 minut; ich wiadomości znajdziesz w Mailpit.

## Typowe problemy

| Objaw | Co sprawdzić |
|---|---|
| Błąd `JVM target 5` lub `ArtifactHandlerManager` w IntelliJ | JDK 25, Maven Wrapper 3.9.9 oraz **Reload All Maven Projects**. Nie zmieniaj docelowej Javy na 5 lub 8. |
| Start odrzucony z powodu sekretu | Ustaw poprawne Base64 dla obu niezależnych sekretów w procesie, z którego uruchamiasz aplikację. |
| Testcontainers nie widzi Dockera albo Docker zwraca 500 | Sprawdź `docker version` i stan Docker Desktop. Ewentualny restart Dockera przerwie również inne kontenery. |
| Port jest zajęty | Sprawdź, czy nie działa drugi frontend albo drugi egzemplarz backendu. Demo i start lokalny mają różne porty API. |
| Angular nie pobiera danych | Sprawdź backend oraz port docelowy w `frontend/proxy.conf.json`. |
| Po przeniesieniu klas Spring zgłasza zduplikowane komponenty | Wykonaj `mvnw.cmd clean verify`, aby usunąć stare pliki `.class`. |
| Po zmianach kodu demo nadal wygląda tak samo | Przebuduj obrazy przez `docker compose up --build`; sam restart nie buduje ich ponownie. |

Polecenia testów i opis CI znajdują się w [głównym README](../README.md#testy-i-ciągła-integracja).
