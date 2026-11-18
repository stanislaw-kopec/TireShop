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

Hasło nie jest wyświetlane ani zapisane w kodzie. `DEMO_PASSWORD` jest potrzebne podczas pierwszego wczytania oraz aktualizacji seeda dodającej nowe konta. Po zakończeniu procesu możesz usunąć je z terminala: `Remove-Item Env:DEMO_PASSWORD`. Aby wrócić do zwykłego uruchamiania, ustaw `SPRING_PROFILES_ACTIVE=local`; dane pozostają w bazie.

W IntelliJ, w konfiguracji klasy `Application`, ustaw `SPRING_PROFILES_ACTIVE=demo`, `DEMO_PASSWORD` oraz oba sekrety. Nie zapisuj haseł w współdzielonej konfiguracji projektu. Po pierwszym udanym starcie możesz usunąć `DEMO_PASSWORD`; seed zostanie pominięty.

Profil korzysta z tej samej lokalnej bazy co zwykły profil `local`; nie czyści jej. Jeżeli chcesz osobną bazę prezentacyjną, przygotuj ją i ustaw `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` oraz `SPRING_DOCKER_COMPOSE_ENABLED=false`. Wtedy sam zapewnij PostgreSQL i lokalny Mailpit. Nie kieruj demo do bazy produkcyjnej ani rzeczywistego serwera poczty.

## Konta

| Rola | E-mail | Hasło przy pierwszym utworzeniu |
| --- | --- | --- |
| ADMIN | `admin@demo.test` | `DEMO_PASSWORD` |
| EMPLOYEE | `employee@demo.test` | `DEMO_PASSWORD` |
| CUSTOMER | `customer@demo.test` | `DEMO_PASSWORD` |
| CUSTOMER — historia | `history@demo.test` | `DEMO_PASSWORD` przy aktualizacji do v2 |

Jeśli w bazie już jest administrator, seed wykorzystuje go do przygotowania cen i **nie tworzy kolejnego admina ani nie zmienia jego hasła**. Loguj się wtedy dotychczasowymi danymi administratora. Dotyczy to również administratora utworzonego wcześniej w tym samym starcie przez `ADMIN_BOOTSTRAP_ENABLED`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` — zwykły bootstrap działa przed seedem.

Pracownik i klient demo są tworzeni jako aktywni, bez procesu zaproszenia. Jest to wyłącznie przygotowanie danych w profilu demo, nie zmiana publicznej rejestracji ani uprawnień. Jeśli ich adresy są już zajęte, pierwsze wczytanie zostanie przerwane i wycofane; seed nie przejmuje istniejących kont.

## Zawartość

- 24 produkty, 4 producentów, 3 rozmiary i wszystkie sezony. Nazwy `Demo 1`–`Demo 24` oraz ceny są fikcyjnymi przykładami, nie rzeczywistą ofertą producentów. Zdjęcia pozostają puste (pole opcjonalne), bez zależności od zewnętrznego hostingu; można ustawić własny adres HTTPS w edycji produktu.
- 21 produktów dostępnych publicznie — katalog ma więcej niż jedną stronę. `Demo 13` nie ma ceny, `Demo 14` ma cenę, ale jest poza sprzedażą, `Demo 15` ma cenę i zgodę na sprzedaż, lecz zerowy stan.
- Początkowe przyjęcie po 40 sztuk dla 23 produktów, z rejestrem magazynowym. Sprzedaż przykładowych zamówień zmniejsza ten stan przez zwykły symulator płatności. `Demo 16` ma także 20 korekt (naprzemiennie +1 i −1, łącznie bez zmiany stanu) i dwa wydania po zapłacie: 23 wpisy do pokazania paginacji.
- Adres klienta i koszyk z dwiema pozycjami (łącznie 3 sztuki). Dodanie czwartej pozwala pokazać zmianę dostawy z 20 PLN na darmową.
- 6 zamówień klienta: `PENDING_PAYMENT`, `PAID`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED`. Wysłane i doręczone mają demonstracyjne numery przesyłek z prefiksem `DEMO-`.
- Konto `history@demo.test` bez adresu w profilu, z 21 zamówieniami. Zamówienie `PAID` zawiera 4 sztuki dwóch produktów (darmowa dostawa) i 21 prób płatności: 10 odrzuceń, 10 anulowań i sukces. Zamówienie `SHIPPED` zawiera 3 sztuki (20 PLN dostawy) oraz poprawiony numer przesyłki `DEMO-NEW-…`. Pozostałe to 18 anulowanych i jedno oczekujące z rozpoczętą płatnością. Dane dostawy podane podczas zakupów nie zmieniają profilu tego konta.
- 3 zamówienia gości: opłacone `guest@demo.test` i `history-guest@demo.test` (drugie po odrzuceniu i anulowaniu prób) oraz anulowane `cancelled-guest@demo.test`, którego rozpoczęta płatność również została anulowana. Dwa linki śledzenia trafiają do Mailpit przez normalną kolejkę po zatwierdzeniu seeda.
- 30 zamówień łącznie i 50 wpisów audytu na świeżej bazie: 30 utworzeń i 20 anulowań. Audyt powstaje przez zwykłe zdarzenia po commit. Późniejsze operacje i wygaśnięcie rezerwacji dodają następne wpisy.

Obie rezerwacje `PENDING_PAYMENT` wygasają po normalnych 15 minutach. Restart ich nie przedłuża ani nie tworzy nowych zamówień. Po wygaśnięciu pokaż kolejną płatność, składając nowe zamówienie. Wszystkie płatności są symulowane; kwoty i daty powstają przez zwykłe reguły aplikacji. Nie zmieniamy zegara ani historycznych statusów bez przejść domenowych.

## Pokaz nowych widoków i pozostałych funkcji

| Funkcja | Dane i sposób prezentacji |
| --- | --- |
| Historia prób płatności | Zaloguj `history@demo.test`, w historii zamówień wybierz opłacone (`PAID`), otwórz szczegóły i historię płatności. Pokaż wyniki oraz drugą stronę. |
| Historia magazynu | Pracownik → opony → edytuj `Demo 16` → historia zmian magazynowych. Widać przyjęcie, korekty dodatnie/ujemne i wydania; są dwie strony. |
| Audyt | Administrator → Dziennik zdarzeń. Przełącz strony oraz filtry utworzenia/anulowania zamówienia. |
| Paginacja zamówień | `history@demo.test` ma 21 zamówień; lista pracownika ma 30. |
| Brak zapisanego adresu | `history@demo.test` → dodaj oponę do koszyka → przejdź do zakupu. Wprowadź adres w checkout; profil nadal pozostanie bez adresu. Osobno pokaż jego zapis i edycję w panelu konta. |
| Koszyk i scalanie | `customer@demo.test` ma 3 sztuki. Dodaj sztukę jako gość i zaloguj się — po scaleniu pokaż 4 sztuki i darmową dostawę. |
| Cena i dopuszczenie do sprzedaży | Administrator ustawia cenę `Demo 13`, potem osobno włącza sprzedaż; `Demo 14` można dopuścić lub wycofać. |
| Stan i parametry | Pracownik dodaje dostawę `Demo 15`, edytuje parametry/zdjęcie i tworzy nowy model; cenę i sprzedaż ustala administrator. |
| Statusy i przesyłka | Podstawowy klient ma wszystkie etapy; pracownik może obsłużyć PAID → PROCESSING → SHIPPED → DELIVERED. W SHIPPED pokaż korektę numeru. |
| Śledzenie gościa | Otwórz jedną z dwóch wiadomości w Mailpit; pokaż status i formularz ponownego wydania linku. |
| Rejestracja i konta pracowników | Zarejestruj nowego klienta. Administrator tworzy pracownika z nowym adresem; otwórz wiadomość w Mailpit i ustaw hasło. |
| Reset hasła i wylogowanie | Administrator inicjuje reset wybranemu klientowi/pracownikowi. W Mailpit otwórz link i ustaw hasło; pokaż ponowne logowanie oraz wylogowanie. |

Seed przygotowuje dane, a operacje takie jak rejestracja, zaproszenie pracownika, reset hasła, scalanie koszyków i edycja zdjęcia wykonuje się podczas pokazu. Nie resetuje haseł ani nie tworzy gotowych tokenów zaproszeń przy starcie. Historia płatności gościa wymaga kontekstu jego zakupu: pokaż ją po nowym zakupie w tej samej karcie. Link z wiadomości daje wyłącznie dostęp do śledzenia, nie do płatności.

## Pokaz sklepu w przeglądarce

1. Po `docker compose up` otwórz [sklep](http://localhost:4200). W katalogu wybierz szerokość opony, otwórz szczegóły modelu i dodaj jedną sztukę. W koszyku zobaczysz 20 zł za dostawę.
2. Przejdź dalej jako gość. Podaj adres i testowy e-mail, sprawdź podsumowanie i kliknij „Zapłać”. W symulatorze możesz odrzucić pierwszą próbę, ponowić ją i zatwierdzić. Zachowaj numer zamówienia i otwórz link do śledzenia. Kopię linku znajdziesz w [lokalnej skrzynce Mailpit](http://localhost:8026).
3. W osobnej karcie dodaj jedną oponę jako gość i zaloguj się jako `customer@demo.test`. Na świeżej bazie konto ma już trzy sztuki. Po połączeniu koszyków zobaczysz cztery sztuki i darmową dostawę. Kup opony z użyciem adresu z konta, a po zapłacie odszukaj numer zamówienia w historii.
4. Zaloguj się jako `employee@demo.test` w osobnej karcie. W panelu pracownika znajdź oba zamówienia po ich numerach. Dla każdego rozpocznij realizację, podaj numer przesyłki, oznacz jako wysłane, a następnie jako doręczone.
5. Odśwież status na stronie śledzenia gościa i otwórz ponownie historię klienta. W obu miejscach sprawdź status „Doręczone” oraz przypisany numer przesyłki.

## Przykładowy pokaz w Swaggerze

1. Otwórz Swagger UI na porcie 8081 (8080 przy starcie z IDE). Publicznie pobierz `/api/tires`, pokaż filtry sezonu/rozmiaru i sortowanie ceny.
2. Zaloguj `customer@demo.test` przez `/api/auth/login`, wklej `accessToken` do Authorize (`bearerAuth`). Pokaż adres, koszyk i historię zamówień. Zmień koszyk z 3 na 4 sztuki, pobierz podsumowanie i złóż nowe zamówienie.
3. Utwórz próbę płatności i potwierdź `SUCCEEDED` zgodnie z [API płatności](payment-api.md).
4. Zaloguj pracownika (zastąp token w Authorize). Pokaż zamówienie `PAID`, przejdź do `PROCESSING`, następnie `SHIPPED` z numerem przesyłki i `DELIVERED`. Każda nowa operacja potrzebuje nowego UUID `operationId`.
5. Zaloguj administratora. Pokaż zmianę ceny i dopuszczenie produktu do sprzedaży; administrator nie dziedziczy operacji pracownika.
6. W Mailpit na porcie 8026 (8025 przy starcie z IDE) otwórz wiadomość do `guest@demo.test`. Otwórz link w nowej karcie, aby zobaczyć status. Na stronie „Śledzenie zamówienia gościa” możesz podać numer zamówienia z wiadomości i e-mail użyty przy zakupie, aby otrzymać nowy link w Mailpit.

## Bezpieczne ponowienie i błędy

V10 dodaje jedynie strukturę `demo_seed_history`; dane nie są migracją Flyway. Seed używa znaczników `presentation-v1` i `presentation-v2`. Na nowej bazie obie wersje powstają w jednej transakcji. Starsza baza v1 otrzymuje tylko rozszerzenie: dziewięć nowych produktów, osobne konto historii i nowe zamówienia. Nie modyfikuje wcześniejszych produktów, koszyków, adresów ani zamówień. Blokada PostgreSQL zapobiega dwóm równoczesnym wykonaniom. Błąd wycofuje zmiany bieżącego uruchomienia; wcześniejsze dane v1 pozostają.

W Dockerze aktualizacja wymaga tylko `docker compose up --build`, bez usuwania wolumenu ani plików `.demo`. Przy starcie z IDE/Mavena ponownie ustaw `DEMO_PASSWORD` dla nowego konta historii. Aktualizacja wymaga aktywnego administratora i oryginalnych aktywnych kont pracownika oraz klienta we właściwych rolach. Zajęty adres `history@demo.test` powoduje przerwanie aktualizacji; seed nie przejmuje konta ani nie zmienia jego hasła.

Po sukcesie kolejne starty pomijają seed, także gdy zmienisz `DEMO_PASSWORD`. Zmiany cen, haseł, magazynu i statusów wykonane w czasie pokazu pozostają zachowane. Nie ma automatycznego czyszczenia ani resetu prezentacji. Nową prezentację od zera można przygotować w nowej, pustej bazie; nie usuwaj znacznika wykonania w używanej bazie.

## Testy

Standardowe `./mvnw.cmd verify` obejmuje także testy seeda na PostgreSQL. Sprawdzają dane i role przez HTTP, paginację i historie, brak seeda poza profilem demo, aktualizację bazy v1 bez zmiany istniejących kont/ceny/koszyka, zachowanie zmienionej ceny/hasła po restarcie, ponowienia równoległe, rollback kolizji konta oraz współpracę z istniejącym bootstrapem administratora. Test konfiguracji sprawdza wymaganie hasła i brak jego wartości w komunikatach błędów.
