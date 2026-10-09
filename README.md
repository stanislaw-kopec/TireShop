# TireShop

## Backend — uruchomienie lokalne

Wymagania: JDK 25 (`JAVA_HOME` wskazujący katalog JDK) oraz uruchomiony Docker Desktop z kontenerami Linux. Maven pobiera się automatycznie przez wrapper.

Z katalogu projektu w PowerShell:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Domyślny profil `local` uruchamia PostgreSQL 17 przez Docker Compose. Pierwszy start wymaga internetu do pobrania zależności i obrazu bazy. Porty 8080 i 15432 muszą być wolne.

- Backend: http://localhost:8080
- Powitanie (`GET`, tekst `Hello World!`): http://localhost:8080/api/hello
- Stan aplikacji i połączenia z bazą: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI: http://localhost:8080/v3/api-docs

Nie ma jeszcze kontrolerów biznesowych, więc główny adres `/` zwraca 404.

W IntelliJ uruchom klasę `com.shop.tire.Application` i wybierz JDK 25. Katalog roboczy może wskazywać główny katalog `TireShop` lub `backend`. Główny `compose.yaml` dołącza konfigurację z `backend/compose.yaml` (wymaga Docker Compose 2.20+); oba korzystają z tej samej bazy i wolumenu. Dla innego katalogu roboczego ustaw zmienną `COMPOSE_FILE` na pełną ścieżkę do `backend/compose.yaml`.

## Baza danych

Lokalne parametry: host `localhost`, port `15432`, baza i użytkownik `tireshop`, hasło `tireshop_local`. Są przeznaczone wyłącznie do pracy lokalnej. Baza jest dostępna tylko na interfejsie lokalnym, a dane pozostają w wolumenie Dockera po restarcie.

Zatrzymanie backendu: `Ctrl+C`. Baza pozostaje uruchomiona. Aby ją zatrzymać, wykonaj z katalogu `backend`:

```powershell
docker compose stop
```

Dla istniejącej bazy ustaw `SPRING_PROFILES_ACTIVE=external` oraz `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Profil ten nie uruchamia Dockera. `SERVER_PORT` zmienia port HTTP. Nie zapisuj rzeczywistych haseł w repozytorium. Pliki `.env` nie są automatycznie wczytywane przez Spring — ustaw zmienne w terminalu lub konfiguracji uruchomienia IDE.

Hibernate sprawdza schemat (`ddl-auto=validate`), ale nie modyfikuje go automatycznie. Przed dodaniem pierwszych encji należy przygotować migracje bazy.

## Testy i budowanie

Z katalogu `backend`:

```powershell
.\mvnw.cmd verify
```

Testy HTTP stanu aplikacji i dokumentacji API korzystają z profilu `test` i osobnej bazy H2 w pamięci, bez Dockera. Nie zastępuje testów zgodności zapytań z PostgreSQL.

Wynik budowania: `backend/target/demo-0.0.1-SNAPSHOT.jar`. Przy uruchamianiu JAR zapewnij działającą bazę i użyj profilu `external` z właściwymi zmiennymi połączenia.

## IntelliJ — błędy importu Maven i JVM target 5

Wrapper używa Maven 3.9.9, aby ominąć problem importu Maven 3.10 (`ArtifactHandlerManager`) w IntelliJ. W `pom.xml` jawnie ustawiono kompilację dla Java 25.

Po zmianie konfiguracji:

1. W oknie Maven wybierz **Reload All Maven Projects**.
2. W **Project Structure → Project** wybierz SDK 25 i poziom języka 25.
3. W **Settings → Build Tools → Maven** wybierz **Use Maven wrapper**; w **Importing** i **Runner** wybierz JDK projektu (25).
4. W konfiguracji uruchomienia `Application` pozostaw katalog roboczy projektu (`TireShop`) lub wybierz `backend`.

Jeżeli JDK 25 nie ma na liście IDE, dodaj istniejący katalog `C:\Program Files\Java\jdk-25` przez **Add SDK → JDK**. Nie ustawiaj targetu 5 ani JDK 8 — projekt wymaga JDK 25.
