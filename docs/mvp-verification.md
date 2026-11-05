# Weryfikacja backendu MVP — 6 listopada 2026

Wykonano `./mvnw.cmd -B verify` z katalogu `backend`: **88 testów, 0 błędów, 0 niepowodzeń, 0 pominiętych; BUILD SUCCESS**. Powstał wykonywalny JAR `backend/target/demo-0.0.1-SNAPSHOT.jar`. Testy korzystały z izolowanych kontenerów PostgreSQL 17 i Mailpit; nie zmieniały lokalnej bazy sklepu.

## Sprawdzony zakres

| Obszar | Weryfikacja |
| --- | --- |
| Konta i bezpieczeństwo | Rejestracja, profil/adres, JWT i jego odrzucanie, wylogowanie/reset, rozdzielenie ról i właścicieli, CORS, brak sesji, kontrakt OpenAPI. |
| Katalog i magazyn | Filtry, paginacja, sortowanie, cena i dopuszczenie przez admina, parametry i stan przez pracownika, historia zmian i ponowienia. |
| Koszyk | Klient i gość, scalanie bez duplikowania, konflikt bez utraty zawartości, aktualne ceny, dostawa 20 PLN dla 3 sztuk i darmowa od 4, także dla mieszanych produktów. |
| Zamówienia | Podsumowanie, kopie cen/adresu, atomowa rezerwacja, idempotencja, wygaśnięcie i anulowanie, konkurencja o ostatnie sztuki oraz rollback. |
| Płatności | Sukces/odrzucenie/anulowanie, ponowienia, wydanie dokładnie raz, ścisła granica wygaśnięcia i wyścigi z anulowaniem/rezerwacją. |
| Śledzenie i poczta | Dostęp gościa, terminy i limity odnowień, prywatność odpowiedzi, kolejka, awarie i ponawianie SMTP bez cofania zapłaty. |
| Realizacja | Dozwolone przejścia do doręczenia, wymagany numer przesyłki, korekta przed doręczeniem, ponowienia i równoczesne zmiany. |

Nowy `MvpJourneyIntegrationTests` przechodzi przez API od zaproszenia pracownika i przygotowania oferty, przez scalanie koszyków oraz zakupy klienta i gościa, do zapłaty i doręczenia. Odczytuje rzeczywiste wiadomości SMTP w Mailpit: zaproszenie, link śledzenia i reset hasła. Po resecie potwierdza odrzucenie starego JWT.

Test zamyka i ponownie uruchamia kontekst aplikacji z serwerem HTTP na tej samej izolowanej bazie i z tymi samymi sekretami. Sprawdza 9 migracji, zachowanie zamówień i linku, wysyłkę oczekującej wiadomości oraz zwolnienie wygasłej rezerwacji przez zadanie w tle. Wygaśnięcie przygotowuje przez zmianę czasu wyłącznie w danych testowych, bez oczekiwania 15 minut.

## Poprawka wykryta podczas sprawdzania

Część odpowiedzi generowanych przez Spring (np. 404 dla nieznanej ścieżki) nie zawierała wymaganego `code`. Globalny handler uzupełnia teraz stabilne kody i bezpieczne opisy takich błędów. Walidacja parametrów zawiera listę `errors` bez odsyłania ich wartości. Test regresji sprawdza 400/404/405/415 oraz pierwszeństwo 401 dla nieuwierzytelnionego żądania do chronionej ścieżki.

## Wniosek i granice sprawdzenia

Nie znaleziono brakującej funkcji blokującej uzgodniony backend MVP. Można przejść do integracji Angulara. Nie dodawano nowych funkcji biznesowych ani migracji.

Nie testowano interfejsu Angulara (jeszcze nie istnieje), przycisku uruchamiania w konkretnej instalacji IntelliJ ani wdrożenia produkcyjnego. Restart sprawdzono w procesie testowym, a nie przez zabicie procesu JVM lub odcięcie zasilania. Testy nie są gwarancją braku wszystkich błędów ani testem obciążenia. Request ID i uporządkowanie starszego `ApiException` pozostają poza tym sprawdzeniem. Zaproszenia/reset nadal wysyłają pocztę synchronicznie; trwała kolejka dotyczy linków śledzenia.
