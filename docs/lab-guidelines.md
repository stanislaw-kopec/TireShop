# Materiały laboratoryjne a TireShop w Spring Boot

Aktualizacja: 4 listopada 2026. Dokument opisuje przełożenie zasad z czterech dostarczonych PDF na istniejący backend. Nie zastępuje wymagań sklepu z `AGENTS.md` i nie zmienia projektu w ExpressJS. Przykłady poleceń, seedów i domeny Task Manager w PDF są materiałem dydaktycznym, nie poleceniem wykonania tych operacji na TireShop.

## Źródła i zakres zastosowania

Uzupełnienie z 18 listopada 2026 obejmuje pierwszy etap dostosowania do materiału 05, czyli CI. Workflow `.github/workflows/ci.yml` sprawdza backend, frontend i budowanie obu obrazów Docker po pushu do `main` oraz przy pull requeście. Maven wrapper i JUnit zastępują polecenia testowe Expressa. PostgreSQL 17 jest uruchamiany przez istniejące Testcontainers w zadaniu backendu. Taki wariant różni się od wymaganego w zadaniu 05 wpisu `services: postgres` i wymaga uzgodnienia przy dosłownym ocenianiu konfiguracji. Pokaz czerwonego i zielonego uruchomienia w jednym PR oraz zrzuty do sprawozdania wymagają osobnego wykonania na GitHub.

Drugi etap z 18 listopada 2026 uzupełnia testy jednostkowe. `CartServiceTests` dodaje 22 przypadki, a `OrderCancellationTests` kolejne 10. Każdy test tworzy własne repozytorium w pamięci. `Clock.fixed` kontroluje czas, parametryzacja JUnit sprawdza granice ilości i terminów oraz niedozwolone statusy, a błędy są porównywane po `code`. Mocki służą do zastąpienia pozostałych domen i sprawdzenia publikacji zdarzenia oraz zwolnienia magazynu. Testy anulowania potwierdzają brak zdarzenia po odmowie operacji i jednokrotną publikację przy ponowieniu. Granice transakcji, role i współbieżność sprawdzają istniejące testy integracyjne.

Profile Maven `unit` i `integration` pozwalają uruchamiać zestawy osobno. Z katalogu `backend` użyj `./mvnw.cmd -Punit test` albo `./mvnw.cmd -Pintegration verify`. Bez profilu `verify` wykonuje oba zestawy. Testy jednostkowe nie uruchamiają Springa, serwera HTTP ani połączeń do bazy.

Próbę jakości testów wykonano przez tymczasową zmianę warunku dostawy w `CartTotals` z `count < 4` na `count <= 4`. Polecenie `./mvnw.cmd -Punit '-Dtest=CartServiceTests#deliveryDependsOnTotalQuantity' test` wykonało cztery przypadki i wykryło jeden błąd asercji dla czterech opon — oczekiwano `0.00`, otrzymano `20.00`. Log tej lokalnej próby znajduje się w ignorowanym `backend/target/stage2-mutation.log`. Poprawny kod przywrócono po próbie.

Weryfikacja etapu 2 zakończyła się wynikiem 47 testów jednostkowych, 0 błędów i 0 pominięć. Po przywróceniu kodu powtórzono cały zestaw z `-Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random` i `-Djunit.jupiter.execution.order.random.seed=18112026`, uzyskując ponownie 47 poprawnych przypadków. W PowerShell oba argumenty należy objąć pojedynczymi cudzysłowami. Osobne uruchomienie profilu `integration` wybrało cztery istniejące klasy integracyjne, ale zatrzymało się na niedostępnym silniku Docker Desktop. Pełna weryfikacja integracyjna wymaga ponowienia po naprawie Dockera.

| Materiał | Wykorzystane części | Zastosowanie w TireShop |
|---|---|---|
| 01 — ExpressJS i pierwsze REST API | s. 3–4, 10–18, 19–21: HTTP, statusy, params/query/body, CRUD i reguły biznesowe | Rzeczownikowe ścieżki, właściwe metody/statusy, walidacja backendu, osobne operacje biznesowe. |
| 02 — Architektura warstwowa, walidacja i obsługa błędów | s. 4–7, 10–16, 17–24: request ID, warstwy, DI, czas, przejścia statusów, DTO i błędy | Pakiety domenowe, cienkie kontrolery, serwisy bez obiektów HTTP, rekordy DTO, Bean Validation, ProblemDetail. |
| 03 — PostgreSQL, migracje, relacje i transakcje | s. 2–11, 12–18, 19–28: osobna baza, constraints, migracje, paginacja, N+1 i współbieżność | PostgreSQL w Dockerze, Flyway, JPA, pula połączeń, stabilne sortowanie, transakcje i blokady potwierdzone testami PostgreSQL. |
| 04 — Authentication, authorization i konfiguracja | s. 2–10, 11–18, 18–24: hasła, JWT, role/ownership, sekrety, OpenAPI i CORS | JWT Bearer na 15 minut bez refresh tokenów, Spring Security, walidacja konfiguracji i Swagger Authorize. |

Materiały źródłowe dostarczył użytkownik; nie kopiujemy ich do repozytorium. Numery stron odnoszą się do stron PDF, a nie numerów sekcji.

## Odpowiedniki mechanizmów

| Express / laboratorium | Spring / TireShop |
|---|---|
| Router i controller | `@RestController`, `@RequestMapping`, `@GetMapping` itd. |
| `req.params`, `req.query`, `req.body` | `@PathVariable`, `@RequestParam` / DTO filtrów, `@RequestBody` |
| Zod, strict schema | Rekordy DTO, Jakarta Bean Validation, `@Valid`, odrzucanie nieznanych pól JSON |
| Middleware authenticate/authorize | Spring Security Resource Server, `SecurityFilterChain`, reguły ról i `@PreAuthorize` |
| `req.user` | Zweryfikowany principal przekazany z `@AuthenticationPrincipal` |
| Service i fabryka zależności | `@Service` i wstrzykiwanie przez konstruktor; `Clock` dla czasu |
| Knex repository, pool | Spring Data JPA / repozytorium, `DataSource` i HikariCP |
| Knex migrations | Wersjonowane migracje Flyway, bez edycji już wykonanych plików |
| Unit of Work / `trx` | Transakcja serwisu `@Transactional` obejmująca współpracujące repozytoria |
| `SELECT … FOR UPDATE` | Zapytanie z blokadą / `@Lock(PESSIMISTIC_WRITE)` w transakcji |
| Error middleware | `@RestControllerAdvice` oraz osobne handlery błędów filtrów Security |
| `jsonwebtoken` | `JwtEncoder`, `JwtDecoder` i standardowy filtr Bearer Spring Security |
| Konfiguracja Zod / fail fast | Walidowane `@ConfigurationProperties`, zmienne środowiskowe |
| Swagger UI | Istniejący springdoc-openapi z opisem `bearerAuth` |

## Świadome różnice względem przykładów

- Zachowujemy Java 25, Spring Boot i PostgreSQL 17. Nie instalujemy bibliotek Node.js i nie cofamy wersji bazy do przykładu z PDF.
- Zachowujemy domeny sklepu oraz role CUSTOMER, EMPLOYEE, ADMIN. Uprawnienia admina z Task Managera nie nadają mu automatycznie uprawnień pracownika w sklepie.
- Format błędu pozostaje `ProblemDetail`, rozszerzany o stabilny `code` i błędy pól. Nie wprowadzamy drugiej otoczki `{error: ...}`.
- Hasła nadal używają PBKDF2 przez Spring Security, z losową solą i kosztem obliczeń. Nie ma potrzeby zmieniać istniejących haseł na bcrypt tylko dla zgodności z przykładem. Limit bcrypt dotyczy bajtów, więc kopiowanie limitu znaków z przykładu nie byłoby równoważne.
- JWT podpisuje HS256; sekret to Base64 co najmniej 32 losowych bajtów, a nie dowolny ciąg o długości 32 znaków. Użytkownik zatwierdził 15 minut ważności i brak odświeżania.
- `auth_version` oraz stan/rola konta są sprawdzane w bazie. Dzięki temu reset hasła i wyłączenie konta działają od razu. Wylogowanie unieważnia wszystkie poprzednie tokeny konta. JWT bez takiej dodatkowej kontroli pozostawałby ważny do `exp`, jak wyjaśnia materiał 04.
- Nie ma sesji HTTP, cookie uwierzytelniającego ani endpointu pobierania CSRF. Token jest przyjmowany tylko w Authorization. Jeśli przyszły koszyk gościa będzie oparty o cookie, jego operacje wymagają osobnego projektu CSRF.
- Administrator powstaje przez opcjonalny bootstrap, nie przez seed z publicznym hasłem i czyszczeniem tabel. Konfiguracja przykładowa nie zawiera sekretów.
- Flyway zachowuje V1/V2, a V3 usuwa tylko techniczne tabele sesji. Nie dodajemy automatycznego `down` usuwającego dane, wzorowanego na Knex; kolejne zmiany schematu dostają nowe wersje.
- Nie kopiujemy logowania pełnego URL z przykładu middleware, ponieważ linki resetu i śledzenia zawierają sekrety. Diagnostyka musi je pomijać lub redagować.

## Co zmienia ten etap

Etap 3 dostosowania do materiału 05 dodaje dziennik `ORDER_CREATED` / `ORDER_CANCELLED` i endpoint administratora. Spring dostarcza publikację zdarzeń oraz obsługę audytu po commit. Osobna transakcja zapisu i przechwycenie błędu zapewniają niezależność odpowiedzi API od awarii audytu. Jednostkowe testy publikacji i testy HTTP/PostgreSQL weryfikują ponowienia, rollback, dostęp i błąd listenera. Kontrakt oraz ograniczenia trwałości opisano w [API audytu](audit-api.md). Przykład anulowania cudzej rezerwacji przez ADMIN z ćwiczenia dotyczy domeny sal; w TireShop anulowanie nadal przysługuje właścicielowi nieopłaconego zakupu lub wynika z wygaśnięcia rezerwacji.

Kod otrzymuje JWT, sprawdzanie claims i aktualnego stanu konta, Bearer w Swaggerze, konfigurację CORS, nową migrację oraz testy. Instrukcja uruchomienia i kontrakt API są aktualizowane razem z kodem.

Powyższy opis dotyczy etapu przejścia na JWT. W kolejnych etapach wdrożono katalog, koszyk, zamówienia, płatności, śledzenie i realizację pracowniczą, a także ścisłą kontrolę typów JSON oraz wspólny format błędów parametrów. Aktualny zakres i ograniczenia weryfikacji opisuje [raport MVP](mvp-verification.md). Request ID i odseparowanie istniejącego `ApiException` od HTTP pozostają osobnymi zadaniami technicznymi; nie blokują uzgodnionej ścieżki zakupu.

Przy integracji korzystamy ze standardowego mechanizmu [Spring Security JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html). Zasady dotyczące ciasteczek i CSRF trzeba rozpatrywać zgodnie z [dokumentacją CSRF Spring Security](https://docs.spring.io/spring-security/reference/features/exploits/csrf.html), niezależnie od formatu tokenu.
