# Dane do prezentacji — profil demo

W głównym katalogu projektu uruchom `docker compose up`. Compose buduje aplikację i włącza profil `demo`, który dodaje dane raz do osobnej bazy prezentacyjnej. Pierwszy start wymaga dostępu do internetu, aby pobrać obrazy i zależności.

Otwórz [Swagger UI](http://localhost:8081/swagger-ui/index.html) i [Mailpit](http://localhost:8026). Początkowe hasło kont odczytaj z `.demo/password.txt`. Przy pierwszym uruchomieniu kontener generuje tam hasło i w osobnych plikach zapisuje dwa niezależne klucze dla JWT i śledzenia. Git pomija te pliki. Zachowaj katalog `.demo` razem z wolumenem bazy przy przenoszeniu prezentacji. Po zmianach w kodzie przebuduj obraz przez `docker compose up --build`.

## Alternatywne uruchomienie z IDE lub Maven

Wymagania są takie jak w README: Java 25, działający Docker oraz ustawione własne `JWT_SECRET` i `TRACKING_SECRET`. Zachowuj te same sekrety przy restartach.

Z katalogu `backend` w PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = "demo"
$demoCredential = Read-Host "Hasło nowych kont demo (12-128 znaków)" -AsSecureString
$env:DEMO_PASSWORD = [System.Net.NetworkCredential]::new("", $demoCredential).Password
.\mvnw.cmd spring-boot:run
```

Hasło nie jest wyświetlane ani zapisane w kodzie. `DEMO_PASSWORD` jest potrzebne tylko podczas pierwszego wczytania. Po zakończeniu procesu możesz usunąć je z terminala: `Remove-Item Env:DEMO_PASSWORD`. Aby wrócić do zwykłego uruchamiania, ustaw `SPRING_PROFILES_ACTIVE=local`; dane pozostają w bazie.

W IntelliJ, w konfiguracji klasy `Application`, ustaw `SPRING_PROFILES_ACTIVE=demo`, `DEMO_PASSWORD` oraz oba sekrety. Nie zapisuj haseł w współdzielonej konfiguracji projektu. Po pierwszym udanym starcie możesz usunąć `DEMO_PASSWORD`; seed zostanie pominięty.

Profil korzysta z tej samej lokalnej bazy co zwykły profil `local`; nie czyści jej. Jeżeli chcesz osobną bazę prezentacyjną, przygotuj ją i ustaw `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` oraz `SPRING_DOCKER_COMPOSE_ENABLED=false`. Wtedy sam zapewnij PostgreSQL i lokalny Mailpit. Nie kieruj demo do bazy produkcyjnej ani rzeczywistego serwera poczty.

## Konta

| Rola | E-mail | Hasło przy pierwszym utworzeniu |
| --- | --- | --- |
| ADMIN | `admin@demo.test` | `DEMO_PASSWORD` |
| EMPLOYEE | `employee@demo.test` | `DEMO_PASSWORD` |
| CUSTOMER | `customer@demo.test` | `DEMO_PASSWORD` |

Jeśli w bazie już jest administrator, seed wykorzystuje go do przygotowania cen i **nie tworzy kolejnego admina ani nie zmienia jego hasła**. Loguj się wtedy dotychczasowymi danymi administratora. Dotyczy to również administratora utworzonego wcześniej w tym samym starcie przez `ADMIN_BOOTSTRAP_ENABLED`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` — zwykły bootstrap działa przed seedem.

Pracownik i klient demo są tworzeni jako aktywni, bez procesu zaproszenia. Jest to wyłącznie przygotowanie danych w profilu demo, nie zmiana publicznej rejestracji ani uprawnień. Jeśli ich adresy są już zajęte, pierwsze wczytanie zostanie przerwane i wycofane; seed nie przejmuje istniejących kont.

## Zawartość

- 15 produktów, 4 producentów, 3 rozmiary i wszystkie sezony. Nazwy `Demo 1`–`Demo 15` oraz ceny są fikcyjnymi przykładami, nie rzeczywistą ofertą producentów. Zdjęcia pozostają puste (pole opcjonalne), bez zależności od zewnętrznego hostingu.
- 12 produktów dostępnych publicznie. `Demo 13` nie ma ceny, `Demo 14` ma cenę, ale jest poza sprzedażą, `Demo 15` ma cenę i zgodę na sprzedaż, lecz zerowy stan.
- Początkowe przyjęcie po 40 sztuk dla 14 produktów, z rejestrem magazynowym. Sprzedaż przykładowych zamówień zmniejsza ten stan przez zwykły symulator płatności.
- Adres klienta i koszyk z dwiema pozycjami (łącznie 3 sztuki). Dodanie czwartej pozwala pokazać zmianę dostawy z 20 PLN na darmową.
- 6 zamówień klienta: `PENDING_PAYMENT`, `PAID`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED`. Wysłane i doręczone mają demonstracyjne numery przesyłek z prefiksem `DEMO-`.
- 1 opłacone zamówienie gościa `guest@demo.test`, z linkiem śledzenia wysyłanym do Mailpit przez normalną kolejkę po zatwierdzeniu seeda.

Rezerwacja `PENDING_PAYMENT` wygasa po normalnych 15 minutach. Restart jej nie przedłuża ani nie tworzy nowego zamówienia. Wszystkie płatności są symulowane; kwoty i daty powstają przez zwykłe reguły aplikacji. Nie zmieniamy zegara ani historycznych statusów bez przejść domenowych.

## Przykładowy pokaz w Swaggerze

1. Otwórz Swagger UI na porcie 8081 (8080 przy starcie z IDE). Publicznie pobierz `/api/tires`, pokaż filtry sezonu/rozmiaru i sortowanie ceny.
2. Zaloguj `customer@demo.test` przez `/api/auth/login`, wklej `accessToken` do Authorize (`bearerAuth`). Pokaż adres, koszyk i historię zamówień. Zmień koszyk z 3 na 4 sztuki, pobierz podsumowanie i złóż nowe zamówienie.
3. Utwórz próbę płatności i potwierdź `SUCCEEDED` zgodnie z [API płatności](payment-api.md).
4. Zaloguj pracownika (zastąp token w Authorize). Pokaż zamówienie `PAID`, przejdź do `PROCESSING`, następnie `SHIPPED` z numerem przesyłki i `DELIVERED`. Każda nowa operacja potrzebuje nowego UUID `operationId`.
5. Zaloguj administratora. Pokaż zmianę ceny i dopuszczenie produktu do sprzedaży; administrator nie dziedziczy operacji pracownika.
6. W Mailpit na porcie 8026 (8025 przy starcie z IDE) otwórz wiadomość do `guest@demo.test`. Otwórz link w nowej karcie, aby zobaczyć status. Na stronie „Śledzenie zamówienia gościa” możesz podać numer zamówienia z wiadomości i e-mail użyty przy zakupie, aby otrzymać nowy link w Mailpit.

## Bezpieczne ponowienie i błędy

V10 dodaje jedynie strukturę `demo_seed_history`; dane nie są migracją Flyway. Seed oraz znacznik `presentation-v1` powstają w jednej transakcji. Blokada PostgreSQL zapobiega dwóm równoczesnym wykonaniom. Błąd wycofuje wszystkie zmiany seeda; po usunięciu przyczyny można uruchomić aplikację ponownie.

Po sukcesie kolejne starty pomijają seed, także gdy zmienisz `DEMO_PASSWORD`. Zmiany cen, haseł, magazynu i statusów wykonane w czasie pokazu pozostają zachowane. Nie ma automatycznego czyszczenia ani resetu prezentacji. Nową prezentację od zera można przygotować w nowej, pustej bazie; nie usuwaj znacznika wykonania w używanej bazie.

## Testy

Standardowe `./mvnw.cmd verify` obejmuje także testy seeda na PostgreSQL. Sprawdzają dane i role przez HTTP, brak seeda poza profilem demo, zachowanie zmienionej ceny/hasła po restarcie, ponowienia równoległe, rollback kolizji konta oraz współpracę z istniejącym bootstrapem administratora. Test konfiguracji sprawdza wymaganie hasła i brak jego wartości w komunikatach błędów.
