# Materiały laboratoryjne a TireShop w Spring Boot

Aktualizacja: 4 listopada 2026. Dokument opisuje przełożenie zasad z czterech dostarczonych PDF na istniejący backend. Nie zastępuje wymagań sklepu z `AGENTS.md` i nie zmienia projektu w ExpressJS. Przykłady poleceń, seedów i domeny Task Manager w PDF są materiałem dydaktycznym, nie poleceniem wykonania tych operacji na TireShop.

## Źródła i zakres zastosowania

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

Kod otrzymuje JWT, sprawdzanie claims i aktualnego stanu konta, Bearer w Swaggerze, konfigurację CORS, nową migrację oraz testy. Instrukcja uruchomienia i kontrakt API są aktualizowane razem z kodem.

`AGENTS.md` zawiera także zasady dla kolejnych etapów, a nie deklarację, że cały kod już je realizuje. Przykładowo request ID, odseparowanie istniejącego `ApiException` od HTTP, ujednolicenie szczegółów wszystkich błędów parametrów i ścisła kontrola konwersji typów JSON pozostają pracą do wykonania przy rozwoju odpowiednich mechanizmów. Katalog, koszyk i zamówienia nie są implementowane przy okazji zmiany logowania.

Przy integracji korzystamy ze standardowego mechanizmu [Spring Security JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html). Zasady dotyczące ciasteczek i CSRF trzeba rozpatrywać zgodnie z [dokumentacją CSRF Spring Security](https://docs.spring.io/spring-security/reference/features/exploits/csrf.html), niezależnie od formatu tokenu.
