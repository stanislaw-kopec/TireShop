# TireShop — wymagania i zasady pracy nad backendem

## Cel i zakres dokumentu

Backend prostej aplikacji sklepu internetowego z oponami. Ten dokument obowiązuje przy pracy nad backendem w `backend/`. Opisuje docelowe wymagania, a nie listę już zaimplementowanych funkcji. Nie implementuj całego zakresu przy okazji pojedynczego zadania.

Sekcje „Ustalone wymagania” i „Zasady techniczne” są wiążące. Sekcja „Propozycje do omówienia” zawiera pomysły, które nie rozszerzają automatycznie zakresu MVP. Przy kolejnych decyzjach użytkownika aktualizuj dokument.

Projekt jest realizowany w Spring Boot na zajęcia, których materiały pokazują ExpressJS. Przenoś zasady projektowania, a nie składnię JavaScriptu ani listę bibliotek. Podstawą dostosowania są materiały 01–04: REST API; architektura, walidacja i błędy; PostgreSQL, migracje i transakcje; uwierzytelnianie, autoryzacja i konfiguracja. Ich omówienie i różnice względem Springa zapisano w `docs/lab-guidelines.md`.

Decyzja z 4 listopada 2026: JWT zastępuje wcześniejsze logowanie sesyjne. Access token jest ważny 15 minut; po wygaśnięciu wymagane jest ponowne logowanie. Nie dodawaj refresh tokenów w tym etapie.

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

- Decyzja z 5 listopada 2026 (etap zamówień): zmiana ceny lub zawartości po podsumowaniu wymaga pobrania i potwierdzenia nowego podsumowania. Klient może anulować wyłącznie nieopłacone zamówienie; zwroty po zapłacie pozostają poza zakresem.
- Backend wydaje podsumowanie ważne 15 minut, niezależne od rezerwacji. Utworzenie zamówienia potwierdza jego ID i unikalny checkoutKey; podsumowanie ma najwyżej jedno zamówienie. Identyczne ponowienie zwraca to samo zamówienie, bez przedłużenia terminu lub kolejnej rezerwacji.
- Gość otrzymuje w podsumowaniu osobny token zakupu, ważny 24 godziny i przesyłany w X-Purchase-Token. Token koszyka nie daje dostępu do zamówienia. Przyszły link śledzenia po zapłacie jest oddzielnym mechanizmem. Adres podany przy zakupie nie zmienia profilu.

- Decyzja z 5 listopada 2026: gość używa osobnego losowego tokenu koszyka w nagłówku `X-Cart-Token`, bez cookie. Przyszły Angular przechowuje go w `sessionStorage` do zamknięcia karty. Backend przechowuje skrót tokenu i ogranicza jego ważność do 24 godzin od utworzenia; zamknięcie karty nie jest zdarzeniem wiarygodnie wykrywalnym przez backend.
- Jeśli podczas scalania brakuje towaru lub produkt jest niedostępny, zwróć konflikt i zachowaj oba koszyki bez zmian. Nie zmniejszaj samoczynnie ilości. Po sukcesie token gościa nie daje dostępu do koszyka klienta; pozwala jedynie bezpiecznie ponowić scalanie z tym samym zalogowanym kontem do pierwotnego terminu ważności.

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
- Status `CANCELLED` obejmuje wygaśnięcie nieopłaconego zamówienia oraz anulowanie go przez uprawnionego klienta/gościa. Anulowanie po zapłacie wymaga procesu zwrotów, poza zakresem MVP.
- Pracownik przypisuje numer przesyłki. Śledzenie w MVP oznacza aktualny status i numer przesyłki, bez integracji z API kuriera.
- Decyzja z 5 listopada 2026: numer przesyłki jest wymagany przy przejściu do SHIPPED. Pracownik może go skorygować wyłącznie w SHIPPED, przed doręczeniem. Korekta sprawdza poprzednio odczytany numer, aby nie nadpisywać równoczesnej edycji. Nie pomijaj i nie cofaj etapów realizacji.

### Dostęp gościa

- Po potwierdzeniu zapłaty gość otrzymuje specjalny link do śledzenia statusu swojego zamówienia bez zakładania konta.
- Link zawiera nieprzewidywalny token umożliwiający wyłącznie odczyt wskazanego zamówienia. Sam numer zamówienia nie daje dostępu.
- Widok zawiera status i numer przesyłki, gdy zostanie uzupełniony. Nie ujawnia zbędnych danych osobowych ani nie umożliwia zmiany zamówienia.
- Link jest wyświetlany na ekranie potwierdzenia oraz wysyłany e-mailem po potwierdzeniu płatności. Lokalnie używaj podglądu poczty zamiast rzeczywistej wysyłki.
- Dostęp do linku na ekranie potwierdzenia wymaga uprawnienia do danego zakupu; sam identyfikator zamówienia nie wystarcza.
- Pierwszy link jest ważny 90 dni od potwierdzenia zapłaty. Gość może otrzymać nowy link wyłącznie na adres e-mail zapisany w zamówieniu; odnowiony link jest ważny 90 dni od wydania.
- Ponowne wydanie nie ujawnia tokenu w publicznej odpowiedzi API ani nie pozwala wybrać innego adresu odbiorcy. Ogranicz częstotliwość żądań i nie ujawniaj, czy podane zamówienie istnieje. Nie loguj tokenu ani pełnego linku.
- Decyzja z 5 listopada 2026: wiadomości z linkami używają małej kolejki w PostgreSQL z ponawianiem, bez brokera. Zapis kolejki należy do transakcji płatności, SMTP działa po commit. Awaria poczty nie cofa zapłaty. Chronimy zaszyfrowaną treść kolejki osobnym TRACKING_SECRET i usuwamy ją po zakończeniu zadania. Nie obiecujemy dokładnie jednej wysyłki SMTP.

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
- Przepływ: mapowanie endpointu i walidacja → kontroler → serwis → repozytorium → PostgreSQL. Kontroler nie wywołuje bezpośrednio repozytorium ani SQL. Repozytorium nie decyduje o rolach, kosztach dostawy czy dopuszczalnych zmianach statusu.
- Serwis przyjmuje zwykłe dane, identyfikator/tożsamość wykonawcy i zwraca wynik albo błąd domenowy. Nie przekazuj do niego `HttpServletRequest`, `HttpServletResponse` ani `ResponseEntity`. Status HTTP ustala kontroler lub globalny handler; nowe błędy domenowe nie powinny wymagać od serwisu wyboru kodu HTTP. Istniejący `ApiException(HttpStatus, ...)` jest uproszczeniem do uporządkowania przy pracy nad obsługą błędów, a nie wzorcem dla nowych domen.
- Dozwolone zmiany statusów opisuj jawnie, np. enumem i mapą przejść. Nie pozwalaj zmieniać statusu przez ogólną aktualizację DTO. Ponowienie zakończonej operacji nie może ponownie wykonać jej skutków; sprzeczne przejście oznacza konflikt.
- Stosuj wstrzykiwanie zależności przez konstruktor.
- Klasa startowa uruchamia aplikację, konfiguracja składa zależności, a domeny nie otwierają serwera ani połączeń do bazy. Do prostych testów reguł nie wymagaj uruchomienia HTTP; `Clock` pozwala sterować czasem w testach.

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
- URL zasobów nazywaj rzeczownikami, operację wyrażaj metodą HTTP. `GET` nie zmienia stanu; `POST` tworzy/uruchamia operację, `PUT` zastępuje udokumentowany zestaw danych, `PATCH` zmienia wskazaną część. Zmiana statusu może mieć osobny endpoint z własnymi regułami. Nie implementuj kasowania historii tylko po to, aby mieć pełny CRUD.
- Używaj 200 dla odczytu/odpowiedzi, 201 dla utworzenia i 204 bez body. Przy 201 dodawaj `Location`, gdy istnieje adres odczytu nowego zasobu dostępny danemu odbiorcy; nie wskazuj nieistniejącego endpointu.
- Waliduj dane wejściowe przy użyciu Jakarta Bean Validation oraz reguł domenowych.
- Waliduj osobno body, parametry ścieżki i query: typy, zakresy, enumy, rozmiary, formaty i limity długości. Kształt danych sprawdzaj na granicy API, a stan konta, dostępność i uprawnienia do konkretnego zasobu w serwisie.
- DTO żądania jawnie określa dozwolone pola; odrzucaj nieznane pola JSON, zwłaszcza `role`, `accountId`, ceny i statusy zarządzane przez backend. Nie kopiuj całego wejścia na encję. W JSON nie polegaj na niejawnej konwersji tekstu na liczbę/boolean; konwersja poprawnych wartości query/path jest osobną kwestią.
- Normalizuj dane świadomie (np. e-mail do małych liter, obcięcie brzegowych spacji w nazwie), a następnie weryfikuj wartości używane w logice. Nigdy nie przycinaj ani nie zmieniaj hasła. DTO odpowiedzi jawnie wybiera pola; daty zdarzeń mają format ISO 8601 z UTC, a opcjonalne pola spójną reprezentację.
- Stosuj globalny `@RestControllerAdvice` ze spójnym formatem błędów opartym na `ProblemDetail`, w tym błędami walidacji pól.
- Zachowaj `ProblemDetail` zamiast kopiować strukturę `{error: ...}` z Expressa. Błąd powinien zawierać stały `code` dla frontendu, opis oraz dla walidacji listę pól/komunikatów bez ujawnienia ich poufnych wartości. Frontend nie powinien podejmować decyzji na podstawie porównania tekstu komunikatu.
- Rozróżniaj nieprawidłowe dane (400), brak uwierzytelnienia (401), brak uprawnień (403), brak zasobu (404) i konflikt stanu biznesowego (409).
- Niepoprawny JSON i niepoprawne UUID/enum/query to błąd klienta, nie 500. Nieznane ścieżki powinny mieć JSON zamiast HTML, z zachowaniem pierwszeństwa kontroli dostępu. Przy chronionych zasobach obcego właściciela preferuj 404, aby nie ujawniać ich istnienia; brak roli do operacji oznacza 403.
- Błędy z filtrów bezpieczeństwa również powinny mieć spójny format; samo controller advice nie wystarczy do ich obsługi.
- Nie ujawniaj stosów wyjątków, SQL, haseł ani szczegółów infrastruktury w odpowiedziach API.
- Przy rozbudowie diagnostyki dodaj generowany lub bezpiecznie zwalidowany `X-Request-Id`, zwracany także jako `requestId` błędu i obecny w logach. Nie ufaj dowolnemu tekstowi z nagłówka. Loguj metodę, szablon ścieżki, status i czas obsługi; nie loguj Authorization, body z hasłem ani pełnych URL z tokenem. Obsługa 500 powinna ułatwiać znalezienie miejsca błędu po ID bez ujawniania sekretów w logach.
- Utrzymuj dokumentację OpenAPI zgodną z implementacją: DTO, parametry, odpowiedzi sukcesu i błędów oraz `bearerAuth` (HTTP Bearer, JWT). Chronione operacje muszą mieć oznaczenie bezpieczeństwa; publiczne logowanie/rejestracja go nie wymagają. Swagger powinien umożliwiać użycie przycisku Authorize.

### Dane, spójność i bezpieczeństwo

- PostgreSQL działa jako osobna usługa; obecnie używamy PostgreSQL 17, JPA/Hibernate i Flyway. Nie cofaj wersji do przykładu z laboratoriów. Kontener bazy ma wolumen oraz jawnie wskazaną wersję obrazu, bez `latest`.
- Schemat zmieniaj wyłącznie nowymi, wersjonowanymi migracjami Flyway. Nie edytuj wykonanych migracji ani ich sum kontrolnych. Usuwając funkcję sesji, zachowaj historyczne V1/V2 i dodaj kolejną migrację; nie naprawiaj wdrożenia usuwaniem całej bazy.
- Oddziel migracje struktury od opcjonalnych danych demonstracyjnych. Seedy tylko na jawnym profilu lokalnym/demo, nigdy automatyczne `TRUNCATE` przy starcie. Pierwszy administrator nadal powstaje przez opcjonalny bootstrap ze zmiennych środowiska, bez domyślnego hasła z PDF.
- Nie używaj `ddl-auto=update` jako mechanizmu zarządzania schematem. Poza izolowanymi testami zachowaj walidację schematu.
- Modeluj relacje przez PK/FK, a M:N przez tabelę łączącą, gdy domena faktycznie jej wymaga. Dobieraj `NOT NULL`, `UNIQUE`, `CHECK` i zachowanie przy usuwaniu do reguł sklepu. Nie dodawaj `CASCADE` usuwającego zamówienia wraz z kontem lub produktem. Identyfikatory generuj jako UUID, nigdy jako liczba elementów + 1.
- Używaj wspólnego `DataSource` i puli połączeń Springa, bez nowej puli dla każdego żądania/repozytorium. Parametryzuj zapytania; nie doklejaj danych klienta do SQL/JPQL. Indeksy projektuj pod rzeczywiste filtry, połączenia i sortowanie.
- Listy mają walidowaną paginację z limitem maksymalnie 100 pozycji i metadanymi. Przy implementacji ustal i opisz numerację stron. Zawsze stosuj jawne sortowanie oraz stabilny drugi klucz (np. ID). Pola sortowania wybieraj z dozwolonej listy, nie przekazuj klientowskiego tekstu do SQL.
- Unikaj N+1 przy listach produktów/zamówień: dobierz projekcję DTO, JOIN lub `EntityGraph`; nie ustawiaj wszystkich relacji na EAGER. Nie łącz paginacji kolekcji z fetch joinem powodującym stronicowanie w pamięci. Weryfikuj liczbę zapytań, gdy endpoint pobiera powiązane dane.
- Operacje zamówień, płatności i magazynu muszą zachowywać spójność przy równoczesnych żądaniach. Samo sprawdzenie dostępności przed zapisem nie wystarcza do ochrony przed sprzedażą ponad stan.
- Wyznacz granice transakcji i egzekwuj istotne ograniczenia również w bazie danych.
- Jedna operacja biznesowa obejmująca kilka zapisów ma jedną transakcję serwisu (`@Transactional`); uczestniczą w niej wszystkie repozytoria. Nie przechwytuj błędu tylko po to, aby kontynuować częściowy zapis; uwzględniaj reguły rollbacku i fakt, że wywołanie metody w tej samej instancji omija proxy Springa.
- Sama transakcja przy READ COMMITTED nie rozwiązuje check-then-act. Dla współdzielonego limitu/rezerwacji stosuj odpowiednią blokadę w bazie, atomowy update lub constraint. Blokuj zawsze w ustalonej kolejności i ponownie odczytuj stan po uzyskaniu blokady; nie zastępuj tego `synchronized`, które nie chroni wielu instancji backendu.
- Konflikty UNIQUE, FK i CHECK obsługuj według znaczenia operacji, bez szczegółów PostgreSQL w odpowiedzi. Duplikat/konflikt współbieżnej edycji zwykle daje 409, niepoprawne dane 400; nie zamieniaj dowolnej awarii bazy w 409. Test z PostgreSQL ma potwierdzać rollback i zachowanie przy równoległych żądaniach.
- Autoryzuj operacje po stronie backendu, zarówno według roli, jak i właściciela zasobu. Sam identyfikator zamówienia nie daje dostępu do niego.
- Hasła przechowuj wyłącznie jako bezpieczne skróty za pośrednictwem mechanizmów Spring Security. Nie implementuj własnej kryptografii.
- Nie zapisuj w logach haseł, tokenów, sekretów ani pełnych danych osobowych. Sekrety przekazuj konfiguracją środowiska.

### Uwierzytelnianie JWT i konfiguracja

- Wybrane uwierzytelnianie kont: podpisany JWT w `Authorization: Bearer <token>`, Spring Security Resource Server, bez Spring Session i `HttpSession` jako magazynu logowania (`STATELESS`). Nie twórz własnego parsera JWT ani własnej kryptografii.
- Logowanie zwraca `accessToken`, `tokenType: Bearer` i `expiresIn` jako liczbę sekund (domyślnie 900). Bez refresh tokenów; po wygaśnięciu ponowne logowanie. Nie przenoś starego limitu sesji 30 min/8 h do JWT.
- JWT zawiera minimum potrzebnych claims: `sub` jako UUID w tekście, `iat`, `exp`, `iss`, `aud`, `jti`, rolę i wersję uwierzytelnienia. Nie zawiera hasła, hasha, adresu dostawy ani innych zbędnych danych osobowych. Podpis nie szyfruje payloadu.
- Weryfikuj podpis, jawnie dozwolony algorytm HS256, czas ważności, issuer, audience i poprawność wymaganych claims. Odrzucaj tokeny bez terminu, z `alg=none`, zmienionym payloadem i obcym podpisem. Samo odkodowanie base64 nie jest weryfikacją.
- `JWT_SECRET` to Base64 co najmniej 32 losowych bajtów z bezpiecznego generatora, dostarczane przez środowisko. Nie ustawiaj stałego domyślnego sekretu w kodzie i nie generuj go po cichu przy każdym starcie. Brak/zły sekret ma zatrzymać start z czytelnym błędem, bez wydrukowania jego wartości.
- Stan konta, rola i `auth_version` są dodatkowo sprawdzane w bazie przy uwierzytelnianiu tokenu. Reset hasła, wyłączenie konta i wylogowanie odbierają dostęp także przed `exp`. Przyjęte proste wylogowanie zwiększa wersję konta i unieważnia wszystkie jego dotychczasowe JWT; opisz ten efekt w API. Nie oznacza to sesji HTTP ani obietnicy, że JWT będzie weryfikowany bez odczytu bazy.
- Tożsamość pochodzi z poprawnie zweryfikowanego tokenu, nigdy z body/query. Role sprawdzaj w Spring Security, a dostęp do konkretnego zasobu dodatkowo w serwisie na podstawie właściciela. Rejestracja zawsze nadaje CUSTOMER; ADMIN nie dziedziczy automatycznie EMPLOYEE.
- Logowanie dla złego hasła, nieistniejącego i niedostępnego konta zwraca ten sam komunikat 401. 401 dotyczy także brakującego, wygasłego lub nieprawidłowego JWT; 403 oznacza brak uprawnień uwierzytelnionego wykonawcy.
- Bearer jest akceptowany wyłącznie z nagłówka, nie z query, body ani cookie. CSRF jest wyłączone tylko dla obecnego API, które nie używa automatycznie wysyłanych cookies ani Basic auth do uwierzytelniania. Jeśli w przyszłości cookie da dostęp do koszyka gościa lub pojawi się refresh cookie, zaprojektuj ochronę tych operacji przed CSRF. Samo użycie JWT nie usuwa zagrożenia CSRF.
- Dla Angulara preferuj token w pamięci aplikacji, nigdy w URL ani logach; nie wybieraj localStorage jako domyślnego magazynu sekretów. Frontend nie jest teraz implementowany. Przesyłanie tokenów poza lokalnym developmentem wymaga HTTPS.
- CORS dopuszcza jawnie skonfigurowane originy (lokalnie `http://localhost:4200`), nagłówek Authorization oraz potrzebne metody/preflight. CORS nie jest autoryzacją i nie blokuje klienta typu curl; nie zastępuje ról ani kontroli właściciela.
- Konfigurację grupuj tematycznie (np. walidowane `@ConfigurationProperties`), waliduj wymagane parametry przy starcie i dokumentuj jednostki/zakresy. Sekrety tylko ze środowiska; `.env.example` zawiera nazwy i bezpieczne placeholdery, `.env` pozostaje ignorowany. Spring nie wczytuje `.env` automatycznie. Ujawniony sekret należy wymienić, a nie tylko usunąć z pliku.
- Zachowaj istniejący `PasswordEncoder` PBKDF2 z solą i kosztem obliczeń; bcrypt z materiału jest przykładem, nie wymogiem zmiany istniejących haseł. Nie stosuj zwykłego SHA-256 do haseł. Skrót losowego tokenu resetu to inne zastosowanie niż hashowanie hasła i pozostaje osobnym mechanizmem.

### Weryfikacja zmian

- Testuj reguły biznesowe, uprawnienia, niedozwolone przejścia stanów i istotne błędy, a nie gettery ani kod frameworka.
- Dla zamówień i płatności uwzględnij powtórne żądania oraz rywalizację o ostatnie sztuki towaru.
- Testy zależne od zachowania PostgreSQL wykonuj na PostgreSQL, np. przez Testcontainers. H2 może służyć do prostych testów startu i HTTP, ale nie potwierdza zgodności z PostgreSQL.
- Po zmianach uruchom adekwatne testy; podstawowe polecenie to `./mvnw.cmd verify` z `backend` na Windows lub `./mvnw verify` na Linux/macOS.
- Aktualizuj README, gdy zmienia się sposób uruchomienia lub konfiguracji. Zachowaj możliwość startu z IntelliJ i przez Maven wrapper.
- Opisuj ograniczenia weryfikacji, jeśli czegoś nie udało się sprawdzić.
- Testuj JWT przez rzeczywiste żądania HTTP: brak/wygaśnięcie/podrobienie tokenu, nieprawidłowe claims i algorytm, 401/403, cudze zasoby, reset/wylogowanie, brak tworzenia sesji, kontrakt Bearer w OpenAPI i dozwolone/zabronione CORS. Nie zastępuj całej weryfikacji tokenu atrapą zalogowanego użytkownika.

## Propozycje do omówienia — nie są jeszcze zatwierdzonym zakresem

1. **Koszyk gościa:** mechanizm tokenu został ustalony 5 listopada 2026 i opisany w wymaganiach. Dłuższe przechowywanie między sesjami przeglądarki pozostaje poza bieżącym zakresem.
2. **Adres wpisany przy zakupie:** dodatkowa opcja świadomego zapisania go także na koncie może powstać w przyszłym frontendzie. Obecny checkout nie zmienia profilu.

Wstępny projekt relacji i uzasadnienia znajdują się w `docs/backend-design.md`. Pozostałe propozycje tego dokumentu nie stają się wymaganiami bez ich uzgodnienia.

## Stan implementacji

Pierwszy etap: konta, profil i adres, zaproszenia pracowników oraz reset haseł przez administratora. Uwierzytelnianie zostało zmienione na JWT Bearer ważny 15 minut, bez refresh tokenów; V3 usuwa nieużywane tabele sesji. Kontrakt: `docs/backend-api.md`.

Drugi etap: publiczny katalog z filtrami, paginacją i sortowaniem po cenie; odczyt wszystkich produktów dla personelu; tworzenie i edycja parametrów przez pracownika; cena i dopuszczenie do sprzedaży przez admina; przyjęcia, korekty i historia magazynu. V4 tworzy `manufacturer`, `tire_product`, `inventory` i `inventory_change`. Zdjęcie jest opcjonalnym adresem HTTPS, bez uploadu plików. Nowy produkt i zerowy stan powstają atomowo. Ruch magazynowy wymaga UUID `operationId`; ponowienie tej samej operacji nie zmienia stanu ponownie. Szczegóły walidacji i kontrakt: `docs/catalog-api.md`.

Trzeci etap: koszyk CUSTOMER i gościa, aktualne ceny, podsumowanie dostawy oraz atomowe, odporne na ponowienia scalanie. V5 dodaje `shopping_cart` i `cart_item`; domena cart korzysta z repozytorium JDBC. Maksymalnie 100 różnych produktów, PUT ustawia ilość docelową. Kontrakt: `docs/cart-api.md`. Frontend ma wywołać scalanie po logowaniu; samo logowanie nie konsumuje koszyka gościa.

Czwarty etap: podsumowanie zakupu, zamówienie z kopią danych, rezerwacja na 15 minut, historia/szczegóły klienta, dostęp gościa przez token zakupu, anulowanie nieopłaconych i zadanie zwalniające wygasłe rezerwacje. V6 dodaje `checkout_quote`, `customer_order`, `order_item`. Zamówienie i rezerwacja wszystkich pozycji powstają atomowo; koszyk jest czyszczony dopiero w tej samej udanej transakcji. Kontrakt: `docs/order-api.md`.

Piąty etap: backend symulatora płatności klienta/gościa, historia prób, sukces/odrzucenie/anulowanie, ponowienia oraz atomowe wydanie zarezerwowanego towaru. V7 dodaje payment_attempt, paid_at i systemowe wpisy SALE powiązane z pozycją zamówienia. Płatność blokuje ten sam wiersz zamówienia co anulowanie/wygaśnięcie i sprawdza termin ponownie po uzyskaniu blokad magazynu. Jedna próba PENDING na zamówienie; requestKey zapewnia bezpieczne ponowienie utworzenia. Anulowanie zamówienia zamyka oczekującą próbę w tej samej transakcji. Kontrakt: `docs/payment-api.md`.

Szósty etap: link śledzenia gościa po zapłacie, odczyt wyłącznie statusu/numeru przesyłki, odnawianie na zapisany e-mail i kolejka wiadomości z maksymalnie 5 próbami. V8 dodaje order_access_token, order_email_outbox, tracking_request_limit i tracking_number w zamówieniu. Pierwszy link jest ważny 90 dni od zapłaty; odnowiony 90 dni od wydania, bez unieważniania starych. Publiczne odnowienie zawsze odpowiada 202 dla poprawnego formatu, z limitami 10 żądań/15 min na adres sieciowy i 1/15 min na numer zamówienia. TRACKING_SECRET szyfruje payload poczty i czasową kopię pierwszego tokenu dla uprawnionego ekranu potwierdzenia; dostęp weryfikowany jest po hashu. Kontrakt: `docs/tracking-api.md`.

Siódmy etap: lista zamówień z filtrem statusu i paginacją oraz szczegóły dla EMPLOYEE, przejścia PAID → PROCESSING → SHIPPED → DELIVERED i korekta numeru przed doręczeniem. ADMIN nie dziedziczy uprawnień. V9 dodaje order_fulfillment_operation do bezpiecznych ponowień po operationId oraz ograniczenia przesyłki. Zapis operacji i zmiana zamówienia są atomowe, z blokadą wiersza zamówienia. trackingNumber jest widoczny także w historii/szczegółach klienta i kontekście gościa. Kontrakt: `docs/fulfillment-api.md`.

Ekran Angulara pozostaje do implementacji. Nowe domeny używają błędów niezależnych od HTTP; porządkowanie istniejącego `ApiException` oraz request ID są nadal osobnymi zadaniami.

## Poza zakresem MVP

Kupony, opinie, faktury PDF, wiele magazynów, wiele walut, automatyczne zwroty pieniędzy, integracje kurierskie i rzeczywiste płatności. Nie dodawaj ich bez decyzji użytkownika.

## Proponowana kolejność prac

Po uzgodnieniu decyzji potrzebnych dla danego etapu: katalog i migracje → konta i uprawnienia → koszyk → zamówienia i magazyn → symulator płatności → obsługa pracownicza i administracyjna. Dostarczaj małe, kompletne fragmenty z testami; nie buduj całej architektury z wyprzedzeniem.
