# TireShop — wymagania i zasady pracy nad backendem

## Cel i zakres dokumentu

Backend prostej aplikacji sklepu internetowego z oponami. Ten dokument obowiązuje przy pracy nad backendem w `backend/`. Opisuje docelowe wymagania, a nie listę już zaimplementowanych funkcji. Nie implementuj całego zakresu przy okazji pojedynczego zadania.

Sekcje „Ustalone wymagania” i „Zasady techniczne” są wiążące. Sekcja „Propozycje do omówienia” zawiera pomysły, które nie rozszerzają automatycznie zakresu MVP. Przy kolejnych decyzjach użytkownika aktualizuj dokument.

## Ustalone wymagania funkcjonalne

### Katalog opon

- Publiczna lista i szczegóły opony: nazwa/model, producent, zdjęcie, szerokość, profil, średnica, sezon (letnia, zimowa, całoroczna), cena za sztukę i dostępność.
- Główna lista sklepu pokazuje opony dopuszczone przez administratora do sprzedaży, z ustaloną ceną i dodatnią dostępną ilością. Dostępność sprawdzaj ponownie przy zakupie.
- Filtrowanie po rozmiarze, sezonie i producencie, paginacja oraz sortowanie po cenie.
- Indeksy nośności i prędkości oraz oznaczenia XL/run-flat pozostają poza MVP; na razie rozróżniaj produkty według uzgodnionych podstawowych parametrów.
- Wybór dodatniej, całkowitej liczby sztuk i dodawanie do koszyka.
- Cenę ustala i zmienia wyłącznie administrator. Produkt bez ustalonej ceny nie jest dostępny do zakupu; brak ceny nie oznacza ceny zerowej.
- Pracownik może edytować parametry i zdjęcie opony. Wycofanie produktu ze sprzedaży należy wyłącznie do administratora i nie usuwa historycznych zamówień.
- Nowy produkt nie jest automatycznie w sprzedaży. Administrator ustala cenę i zarządza dopuszczeniem do sprzedaży; cena i dostępność w ofercie są osobnymi ustawieniami.

### Ścieżka zakupu

1. Użytkownik przegląda listę dostępnych opon, korzysta z filtrów i dodaje wybrane ilości do koszyka.
2. Z koszyka wybiera „Przejdź dalej”. Jeśli nie jest zalogowany, wybiera zakup jako gość albo logowanie/rejestrację. Zalogowany klient kontynuuje zakupy ze swoim kontem bez ponownego logowania.
3. Gość oraz klient bez zapisanego adresu uzupełniają krok „Adres i dostawa”. Dla klienta z kompletnym adresem użyj danych z konta; przed zapłatą pokaż adres i koszt dostawy do sprawdzenia oraz umożliw poprawienie danych.
4. Przed „Zapłać” pokaż podsumowanie pozycji, adresu, dostawy i całej kwoty brutto. Samo przeglądanie kroków nie tworzy zamówienia i nie rezerwuje towaru.
5. Kliknięcie „Zapłać” zleca backendowi utworzenie zamówienia po sprawdzeniu danych, cen i dostępności. W tym momencie zaczyna się 15-minutowa rezerwacja; następnie klient przechodzi do symulowanej płatności.
6. Po potwierdzonej zapłacie gość otrzymuje link do śledzenia, a klient konta numer zamówienia i informację, że może je śledzić w swoim panelu. Potwierdzenie opiera się na stanie zamówienia w backendzie.

Logowanie jest dostępne podczas przeglądania katalogu, w koszyku i przed złożeniem zamówienia. Nie może powodować utraty koszyka; obowiązuje uzgodnione scalanie. Po scaleniu trzeba ponownie pokazać aktualną zawartość i koszt dostawy przed zapłatą. Logowanie po utworzeniu zamówienia nie zmienia automatycznie jego właściciela ani trybu zakupu; już złożone zamówienie gościa pozostaje dostępne przez jego kontekst zakupu lub link do śledzenia po zapłacie.

### Koszyk i zakup

- Zakupy jako gość lub zalogowany klient. Odczyt koszyka, zmiana ilości i usuwanie pozycji.
- Po zalogowaniu połącz koszyk gościa z koszykiem konta, sumując ilości tych samych produktów i sprawdzając dostępność. Operacja nie rezerwuje towaru i nie może powielać ilości przy ponowieniu tego samego scalenia.
- Backend sprawdza dostępność i oblicza ceny oraz sumy. Nie ufa kwotom ani identyfikatorowi właściciela przesłanym przez klienta API.
- Jedna waluta: PLN. Ceny produktów ustalane i prezentowane jako brutto (z VAT); w MVP nie jest wymagany osobny cennik netto. Jeden adres dostawy na koncie i jedna metoda dostawy.
- Dostawa kosztuje 20,00 PLN brutto za całe zamówienie zawierające od 1 do 3 sztuk opon. Od 4 sztuk wzwyż dostawa całego zamówienia jest darmowa.
- Backend sumuje sztuki ze wszystkich pozycji zamówienia, niezależnie od marki, modelu i wymiarów. Nie wymagaj kompletów ani wielokrotności liczby 4.
- Pustego koszyka nie można zamówić. Przy implementacji przetestuj granicę: 3 sztuki → 20,00 PLN, 4 sztuki → 0,00 PLN, również dla różnych produktów w jednym zamówieniu.
- Złożenie zamówienia wymaga danych kontaktowych i adresu dostawy, także dla gościa.
- Zamówienie zachowuje kopię danych produktów, cen, kosztu dostawy i adresu. Zmiany katalogu lub konta nie zmieniają historycznych zamówień ani kwot ich płatności.

### Rezerwacja magazynowa

- Koszyk nie rezerwuje towaru. Rezerwacja powstaje przy złożeniu zamówienia i trwa 15 minut od jego utworzenia.
- Rezerwacja wszystkich pozycji jest atomowa. Brak dostępności jednej pozycji nie może pozostawić częściowej rezerwacji zamówienia.
- Backend zapisuje i egzekwuje termin wygaśnięcia. Udana płatność zatwierdza sprzedaż z zarezerwowanych sztuk.
- Anulowanie zamówienia lub wygaśnięcie rezerwacji zwalnia sztuki dokładnie raz. Nieopłacone zamówienie po wygaśnięciu przechodzi do `CANCELLED`.
- Sprawdzaj termin również przy obsłudze płatności, niezależnie od zadania zwalniającego wygasłe rezerwacje. Płatność i wygaśnięcie muszą być odporne na równoczesne wykonanie.
- Rozróżniaj stan fizyczny, zarezerwowany i dostępny. Stan nie może być ujemny, a korekta magazynowa nie może naruszać aktywnych rezerwacji.
- Prowadź prosty rejestr zmian stanu fizycznego (`inventory_change`): produkt, autor lub system, data, zmiana ilości i powód. Obejmuje przyjęcia, ręczne korekty i wydania po zapłacie; zapis powstaje w tej samej transakcji co zmiana stanu.
- Rejestr jest dopisywany, bez edycji wcześniejszych wpisów; pomyłkę poprawia kolejna korekta. Rezerwacja nie jest wydaniem towaru. Nie rozbudowuj tego mechanizmu do osobnego systemu audytu ani event sourcingu.

### Symulacja płatności

- Utworzenie próby płatności, przejście do osobnego ekranu symulatora i wybór sukcesu, odrzucenia lub anulowania płatności.
- Stan próby płatności jest oddzielny od realizacji zamówienia: `PENDING`, `SUCCEEDED`, `FAILED`, `CANCELLED`.
- Powrót przeglądarki nie potwierdza zapłaty. Backend weryfikuje wynik względem zapisanej próby i kwoty zamówienia.
- Powtórny wynik tej samej płatności nie może ponownie pobrać towaru ani wielokrotnie zmienić zamówienia. Odrzucaj sprzeczne i niedozwolone przejścia.
- Symulator nie pobiera pieniędzy ani prawdziwych danych kart. Oddziel go od logiki zamówień i wyraźnie oznacz jako symulację.
- Pracownik ani administrator nie zastępują wyniku płatności ręcznym oznaczeniem zamówienia jako opłaconego.
- Po odrzuceniu lub anulowaniu próby można ponowić płatność do pierwotnego terminu 15 minut. Kolejna próba nie przedłuża rezerwacji. Anulowanie próby nie anuluje automatycznie zamówienia.
- Dla opłaconego zamówienia nie można utworzyć kolejnej płatności. Ponowienia nie mogą prowadzić do wielokrotnego zatwierdzenia sprzedaży.
- Po upływie terminu (także dokładnie w chwili wygaśnięcia) symulator odrzuca sukces płatności. Klient musi złożyć nowe zamówienie ze sprawdzeniem bieżących cen i dostępności.

### Realizacja i śledzenie zamówienia

- Przebieg: `PENDING_PAYMENT → PAID → PROCESSING → SHIPPED → DELIVERED`.
- Status `PAID` wynika z potwierdzonej płatności. Pracownik obsługuje kolejne etapy i ręcznie aktualizuje status doręczenia.
- Status `CANCELLED` obejmuje wygaśnięcie nieopłaconego zamówienia. Pozostałe reguły anulowania pozostają do ustalenia.
- Pracownik przypisuje numer przesyłki. Śledzenie w MVP oznacza aktualny status i numer przesyłki, bez integracji z API kuriera.

### Dostęp gościa

- Po potwierdzeniu zapłaty gość otrzymuje specjalny link do śledzenia statusu swojego zamówienia bez zakładania konta.
- Link zawiera nieprzewidywalny token umożliwiający wyłącznie odczyt wskazanego zamówienia. Sam numer zamówienia nie daje dostępu.
- Widok zawiera status i numer przesyłki, gdy zostanie uzupełniony. Nie ujawnia zbędnych danych osobowych ani nie umożliwia zmiany zamówienia.
- Link jest wyświetlany na ekranie potwierdzenia oraz wysyłany e-mailem po potwierdzeniu płatności. Lokalnie używaj podglądu poczty zamiast rzeczywistej wysyłki.
- Dostęp do linku na ekranie potwierdzenia wymaga uprawnienia do danego zakupu; sam identyfikator zamówienia nie wystarcza.
- Pierwszy link jest ważny 90 dni od potwierdzenia zapłaty. Gość może otrzymać nowy link wyłącznie na adres e-mail zapisany w zamówieniu; odnowiony link jest ważny 90 dni od wydania.
- Ponowne wydanie nie ujawnia tokenu w publicznej odpowiedzi API ani nie pozwala wybrać innego adresu odbiorcy. Ogranicz częstotliwość żądań i nie ujawniaj, czy podane zamówienie istnieje. Nie loguj tokenu ani pełnego linku.

### Konto klienta

- Rejestracja i logowanie, odczyt i edycja własnych danych oraz jednego adresu dostawy.
- Panel klienta udostępnia edycję adresu oraz historię i śledzenie zamówień. Zmiana adresu na koncie nie zmienia adresów już złożonych zamówień.
- Historia i szczegóły własnych zamówień, aktualny status oraz numer przesyłki.
- Klient nie może odczytywać ani modyfikować cudzych danych i zamówień.

### Pracownik

- Logowanie i rola `EMPLOYEE`.
- Dodawanie opon do magazynu, przyjmowanie dostaw i korekty stanu zgodne z zasadami rezerwacji. Pracownik nie ustala i nie zmienia cen.
- Pracownik tworzy nowe modele opon z parametrami i dodaje ich sztuki do magazynu. Administrator ustala cenę, zanim produkt stanie się dostępny do zakupu.
- Pracownik edytuje parametry i zdjęcia produktów, ale nie może wycofywać ich ze sprzedaży.
- Dostęp do zamówień i danych potrzebnych do ich realizacji, dozwolone zmiany statusów oraz uzupełnianie numeru przesyłki.
- Brak uprawnień do tworzenia kont pracowników i resetowania haseł innych osób.

### Administrator

- Logowanie i rola `ADMIN`.
- Ustalanie i zmiana cen opon; bez zmiany historycznych zamówień.
- Dopuszczanie opon do sprzedaży i wycofywanie ich przez zmianę oznaczenia dostępności w ofercie, bez usuwania danych potrzebnych do historii zamówień.
- Tworzenie kont pracowników oraz inicjowanie resetu haseł klientów i pracowników.
- Reset nie ujawnia obecnego hasła. Właściciel konta ustala nowe przez jednorazowy, wygasający mechanizm resetu.
- Publiczna rejestracja tworzy wyłącznie konto `CUSTOMER`; roli nie przyjmuj z żądania rejestracji.
- Nie zakładaj automatycznego dziedziczenia uprawnień pracownika przez administratora. Dodatkowe uprawnienia wymagają ustalenia.

## Zasady techniczne

### Prostota i architektura

- Przestrzegaj SOLID, YAGNI i DRY. Stosuj je proporcjonalnie do prostego sklepu.
- Utrzymuj jeden backend jako modularny monolit. Nie dodawaj mikroserwisów, brokera wiadomości, CQRS ani event sourcingu bez konkretnej potrzeby.
- Nie twórz interfejsu dla każdej klasy, bazowych serwisów CRUD ani ogólnych frameworków na zapas. Abstrakcja powinna rozwiązywać aktualny problem.
- Współdziel rzeczywiste reguły biznesowe, ale nie łącz niezależnych domen tylko dlatego, że ich kod wygląda podobnie.
- Kontrolery obsługują HTTP, serwisy realizują przypadki użycia i transakcje, repozytoria odpowiadają za zapis i odczyt. Logiki biznesowej nie umieszczaj w kontrolerach.
- Stosuj wstrzykiwanie zależności przez konstruktor.

### Pakiety domenowe

- Organizuj kod według domen i funkcjonalności, pod `com.shop.tire`.
- Przewidywane pakiety: `catalog`, `cart`, `order`, `payment`, `account`, `inventory`.
- Twórz pakiety dopiero wraz z implementacją ich funkcji. Podział może być doprecyzowany, jeżeli wynika to z rzeczywistych zależności.
- Kontroler, serwis, repozytorium, encje i DTO danej domeny trzymaj w jej pakiecie. Podpakiety dodawaj, gdy poprawiają czytelność.
- Nie twórz globalnych pakietów `controllers`, `services`, `repositories`, `entities` i `dtos` skupiających wszystkie domeny.
- Wspólne mechanizmy techniczne, takie jak obsługa błędów i konfiguracja bezpieczeństwa, mogą mieć małe, jasno nazwane pakiety, np. `shared.error` i `security`. Nie twórz worka `utils` na logikę domenową.
- Unikaj cyklicznych zależności. Operacje zmieniające cudzą domenę wykonuj przez jej jasno określone operacje, zamiast omijać reguły przez bezpośrednie zapisy do jej repozytorium.

### Nowoczesna Java

- Projekt używa Java 25 i Spring Boot. Nie zmieniaj wersji stosu przy okazji niezwiązanego zadania.
- DTO żądań i odpowiedzi domyślnie definiuj jako rekordy (`record`), bez Lomboka.
- Encje JPA pozostają klasami. Nie używaj rekordów jako encji ani `@Data` na encjach.
- Preferuj niemutowalne dane, enumy dla zamkniętych zbiorów stanów oraz czytelne konstrukcje współczesnej Javy. Nie używaj funkcji preview bez uzgodnienia.
- Lombok stosuj oszczędnie i tylko tam, gdzie daje realną korzyść; jego obecność w zależnościach nie wymaga używania go.
- Dla pieniędzy stosuj `BigDecimal` oraz jawnie ustaloną walutę i reguły zaokrąglania. Nie stosuj `double` ani `float`.
- Zapisuj czas zdarzeń w spójnej postaci, preferując `Instant`. Gdy logika zależy od bieżącego czasu, wstrzykuj `Clock`.

### API i błędy

- Udostępniaj REST API pod `/api`. Nie wystawiaj encji JPA bezpośrednio; używaj DTO.
- Waliduj dane wejściowe przy użyciu Jakarta Bean Validation oraz reguł domenowych.
- Stosuj globalny `@RestControllerAdvice` ze spójnym formatem błędów opartym na `ProblemDetail`, w tym błędami walidacji pól.
- Rozróżniaj nieprawidłowe dane (400), brak uwierzytelnienia (401), brak uprawnień (403), brak zasobu (404) i konflikt stanu biznesowego (409).
- Błędy z filtrów bezpieczeństwa również powinny mieć spójny format; samo controller advice nie wystarczy do ich obsługi.
- Nie ujawniaj stosów wyjątków, SQL, haseł ani szczegółów infrastruktury w odpowiedziach API.
- Utrzymuj dokumentację OpenAPI zgodną z implementacją.

### Dane, spójność i bezpieczeństwo

- PostgreSQL jest docelową bazą. Schemat zmieniaj wersjonowanymi migracjami; przed dodaniem pierwszych encji wybierz i skonfiguruj jedno narzędzie migracji.
- Nie używaj `ddl-auto=update` jako mechanizmu zarządzania schematem. Poza izolowanymi testami zachowaj walidację schematu.
- Operacje zamówień, płatności i magazynu muszą zachowywać spójność przy równoczesnych żądaniach. Samo sprawdzenie dostępności przed zapisem nie wystarcza do ochrony przed sprzedażą ponad stan.
- Wyznacz granice transakcji i egzekwuj istotne ograniczenia również w bazie danych.
- Autoryzuj operacje po stronie backendu, zarówno według roli, jak i właściciela zasobu. Sam identyfikator zamówienia nie daje dostępu do niego.
- Hasła przechowuj wyłącznie jako bezpieczne skróty za pośrednictwem mechanizmów Spring Security. Nie implementuj własnej kryptografii.
- Nie zapisuj w logach haseł, tokenów, sekretów ani pełnych danych osobowych. Sekrety przekazuj konfiguracją środowiska.
- Wybrane uwierzytelnianie: sesja po stronie backendu i bezpieczne cookie dla przyszłego frontendu Angular. Nie wprowadzaj JWT. Stosuj ochronę CSRF, HttpOnly cookie sesji oraz Secure na HTTPS. Logowanie, role i kontrolę właściciela wdrażaj przed prywatnymi operacjami koszyka i zamówień.

### Weryfikacja zmian

- Testuj reguły biznesowe, uprawnienia, niedozwolone przejścia stanów i istotne błędy, a nie gettery ani kod frameworka.
- Dla zamówień i płatności uwzględnij powtórne żądania oraz rywalizację o ostatnie sztuki towaru.
- Testy zależne od zachowania PostgreSQL wykonuj na PostgreSQL, np. przez Testcontainers. H2 może służyć do prostych testów startu i HTTP, ale nie potwierdza zgodności z PostgreSQL.
- Po zmianach uruchom adekwatne testy; podstawowe polecenie to `./mvnw.cmd verify` z `backend` na Windows lub `./mvnw verify` na Linux/macOS.
- Aktualizuj README, gdy zmienia się sposób uruchomienia lub konfiguracji. Zachowaj możliwość startu z IntelliJ i przez Maven wrapper.
- Opisuj ograniczenia weryfikacji, jeśli czegoś nie udało się sprawdzić.

## Propozycje do omówienia — nie są jeszcze zatwierdzonym zakresem

1. **Zmiana ceny w koszyku:** proponowane jest potwierdzenie aktualnej kwoty przez klienta przed utworzeniem zamówienia, jeżeli cena uległa zmianie.
2. **Anulowanie zamówienia:** proponowane MVP pozwala klientowi anulować tylko nieopłacone zamówienie. Anulowanie opłaconych zamówień wymaga procesu zwrotu i pozostaje poza obecnym zakresem.
3. **Koszyk gościa:** do ustalenia pozostaje dłuższe przechowywanie koszyka po wygaśnięciu anonimowej sesji. Pierwszy etap kont używa sesji JDBC, 30 minut bezczynności i maksymalnie 8 godzin logowania. Bootstrap administratora jest opcjonalny, przez zmienne środowiska; zaproszenia (24 h) i reset (30 min) trafiają lokalnie do Mailpit. Ustawienia są konfigurowalne.
4. **Adres wpisany przy zakupie:** proponowane jest osobne, świadome zapisanie go na koncie. Zmiana adresu dla pojedynczego zamówienia nie powinna po cichu nadpisywać adresu profilu.

Wstępny projekt relacji i uzasadnienia znajdują się w `docs/backend-design.md`. Pozostałe propozycje tego dokumentu nie stają się wymaganiami bez ich uzgodnienia.

## Stan implementacji

Pierwszy etap: migracje Flyway dla kont, adresów, tokenów jednorazowych i sesji; rejestracja klienta, logowanie/wylogowanie z CSRF, własny profil i adres, zaproszenia pracowników oraz reset haseł przez administratora. Kontrakt: `docs/backend-api.md`. Pozostałe funkcje sklepu są nadal wymaganiami do implementacji.

## Poza zakresem MVP

Kupony, opinie, faktury PDF, wiele magazynów, wiele walut, automatyczne zwroty pieniędzy, integracje kurierskie i rzeczywiste płatności. Nie dodawaj ich bez decyzji użytkownika.

## Proponowana kolejność prac

Po uzgodnieniu decyzji potrzebnych dla danego etapu: katalog i migracje → konta i uprawnienia → koszyk → zamówienia i magazyn → symulator płatności → obsługa pracownicza i administracyjna. Dostarczaj małe, kompletne fragmenty z testami; nie buduj całej architektury z wyprzedzeniem.
