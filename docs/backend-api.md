# API kont — pierwszy etap implementacji

Backend lokalnie: `http://localhost:8080`. Format żądań i odpowiedzi: JSON. Endpointy zmieniające stan wymagają CSRF, także rejestracja, logowanie, wylogowanie i ustawienie hasła. Nieznane pola JSON (np. rola podczas rejestracji) są odrzucane.

## Endpointy

| Metoda i adres | Dostęp | Wynik |
|---|---|---|
| GET `/api/auth/csrf` | Publiczny | 200, cookie XSRF-TOKEN i nazwa nagłówka |
| POST `/api/auth/register` | Publiczny + CSRF | 201, konto CUSTOMER; bez automatycznego logowania |
| POST `/api/auth/login` | Publiczny + CSRF | 200, dane konta i HttpOnly cookie SESSION |
| POST `/api/auth/logout` | CSRF | 204, unieważnienie bieżącej sesji |
| GET `/api/account/me` | Zalogowany | 200, własne dane; nigdy hash hasła |
| PUT `/api/account/me` | Zalogowany + CSRF | 200, aktualizacja imienia, nazwiska i telefonu |
| GET `/api/account/me/address` | Zalogowany | 200 lub 404 ADDRESS_NOT_FOUND |
| PUT `/api/account/me/address` | Zalogowany + CSRF | 200, zapis jednego własnego adresu |
| GET `/api/admin/accounts?email=…` | ADMIN | 200, konto wyszukane po pełnym e-mailu lub 404; ID służy do zlecenia resetu |
| POST `/api/admin/employees` | ADMIN + CSRF | 201, konto INVITED i wiadomość z linkiem ustawienia hasła |
| POST `/api/admin/accounts/{accountId}/password-reset` | ADMIN + CSRF | 204, wiadomość klientowi/pracownikowi; dla INVITED ponowienie zaproszenia |
| POST `/api/auth/password-actions/complete` | Publiczny + CSRF + jednorazowy token | 204, ustawienie hasła; zużyty/wygasły token → 400 |

Reset nie obejmuje administratorów ani kont DISABLED. ADMIN i EMPLOYEE są niezależnymi rolami. Operacje na profilu nie przyjmują ID właściciela z żądania — wynikają z sesji.

## Rejestracja i logowanie

Rejestracja:

```json
{
  "email": "klient@example.test",
  "password": "Przykladowe haslo 123!",
  "firstName": "Jan",
  "lastName": "Kowalski"
}
```

Logowanie przyjmuje wyłącznie `email` i `password`. Hasła mają od 12 do 128 znaków, są haszowane przez Spring Security (PBKDF2 z solą). E-maile przechowujemy w postaci znormalizowanej do małych liter. Klient nie może zmieniać e-maila ani roli przez aktualizację profilu.

Cookie XSRF-TOKEN służy tylko ochronie przed CSRF. Nie jest sesją ani tokenem logowania. Przeglądarka wysyła SESSION automatycznie; aplikacja Angular będzie kopiowała XSRF-TOKEN do nagłówka X-XSRF-TOKEN przy wywołaniach pod tym samym originem. Backend domyślnie nie otwiera CORS dla innych originów; lokalny Angular będzie mógł użyć proxy `/api`.

Przykład PowerShell, który zachowuje cookie i pobiera CSRF (użyj wybranego hasła testowego):

```powershell
$api = 'http://localhost:8080'
$session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
Invoke-RestMethod "$api/api/auth/csrf" -WebSession $session
$csrf = $session.Cookies.GetCookies([uri]$api)['XSRF-TOKEN'].Value
$registration = @{
    email = 'klient@example.test'
    password = 'Przykladowe haslo 123!'
    firstName = 'Jan'
    lastName = 'Kowalski'
} | ConvertTo-Json
Invoke-RestMethod "$api/api/auth/register" -Method Post -WebSession $session `
    -Headers @{'X-XSRF-TOKEN' = $csrf} -ContentType 'application/json' -Body $registration

$login = @{email = 'klient@example.test'; password = 'Przykladowe haslo 123!'} | ConvertTo-Json
Invoke-RestMethod "$api/api/auth/login" -Method Post -WebSession $session `
    -Headers @{'X-XSRF-TOKEN' = $csrf} -ContentType 'application/json' -Body $login
Invoke-RestMethod "$api/api/auth/csrf" -WebSession $session
$csrf = $session.Cookies.GetCookies([uri]$api)['XSRF-TOKEN'].Value
Invoke-RestMethod "$api/api/account/me" -WebSession $session
```

Po logowaniu i wylogowaniu odśwież CSRF. Nie zapisuj prawdziwych haseł w skryptach ani repozytorium.

## Adres

```json
{
  "recipientName": "Jan Kowalski",
  "street": "Przykładowa",
  "buildingNumber": "10",
  "apartmentNumber": "2",
  "postalCode": "00-001",
  "city": "Warszawa",
  "countryCode": "PL"
}
```

`apartmentNumber` jest opcjonalne. Zapis zastępuje adres własnego konta. Zmiana profilu przyjmuje `firstName`, `lastName`, opcjonalnie `phone`.

## Zaproszenie i reset hasła

Zaproszenie pracownika: `email`, `firstName`, `lastName`. Administrator nie ustala jego hasła. W lokalnym podglądzie `http://localhost:8025` pojawi się jednorazowy link; po ustawieniu hasła konto przechodzi z INVITED do ACTIVE.

Ustawienie hasła po zaproszeniu lub resecie:

```json
{
  "token": "token-odczytany-z-linku-w-wiadomosci",
  "password": "Nowe przykladowe haslo 456!"
}
```

Token w rzeczywistej wiadomości ma 43 znaki. Jest ważny 24 godziny dla zaproszenia lub 30 minut dla resetu. Nowe zaproszenie/reset unieważnia wcześniejsze niewykorzystane linki tego konta. Po ustawieniu hasła trzeba się zalogować. Wcześniejsze sesje zostają odrzucone na kolejnym żądaniu, dzięki kontroli wersji uwierzytelnienia w bazie.

Brak usługi pocztowej powoduje 503 i wycofanie operacji, bez ujawniania tokenu w odpowiedzi. Wysyłka synchroniczna nie daje gwarancji dokładnie jednej wiadomości — trwała kolejka pozostaje osobnym etapem.

## Błędy

Błędy API i filtrów bezpieczeństwa mają format `application/problem+json` z `status`, `title`, `detail`, `instance` i opcjonalnym `code`. Walidacja dodatkowo zawiera `errors` z nazwą pola i komunikatem, bez wartości hasła.

- 400: błędne pola, JSON, nieobsługiwane pola lub token ustawienia hasła.
- 401: brak logowania, błędne hasło, wygasła/unieważniona sesja.
- 403: brak uprawnień lub poprawnego CSRF.
- 404: brak adresu/zasobu.
- 409: konflikt danych, zajęty e-mail, niedozwolony reset.
- 503: niedostępna poczta.

Na tym etapie nie ma endpointów katalogu, koszyka, zamówień ani płatności. Nie ma również publicznego „zapomniałem hasła” — zgodnie z zakresem reset inicjuje administrator.
