# API kont — pierwszy etap implementacji

Backend lokalnie: `http://localhost:8080`. Format żądań i odpowiedzi: JSON. Chronione endpointy wymagają `Authorization: Bearer <accessToken>`. Nie używamy sesji ani cookie uwierzytelniającego; nie trzeba pobierać CSRF. Nieznane pola JSON (np. rola podczas rejestracji) są odrzucane.

## Endpointy

| Metoda i adres | Dostęp | Wynik |
|---|---|---|
| POST `/api/auth/register` | Publiczny | 201, konto CUSTOMER; bez automatycznego logowania |
| POST `/api/auth/login` | Publiczny | 200, accessToken, tokenType i expiresIn |
| POST `/api/auth/logout` | Zalogowany | 204, unieważnienie wszystkich dotychczasowych JWT konta |
| GET `/api/account/me` | Zalogowany | 200, własne dane; nigdy hash hasła |
| PUT `/api/account/me` | Zalogowany | 200, aktualizacja imienia, nazwiska i telefonu |
| GET `/api/account/me/address` | Zalogowany | 200 lub 404 ADDRESS_NOT_FOUND |
| PUT `/api/account/me/address` | Zalogowany | 200, zapis jednego własnego adresu |
| GET `/api/admin/accounts?email=…` | ADMIN | 200, konto wyszukane po pełnym e-mailu lub 404; ID służy do zlecenia resetu |
| POST `/api/admin/employees` | ADMIN | 201, konto INVITED i wiadomość z linkiem ustawienia hasła |
| POST `/api/admin/accounts/{accountId}/password-reset` | ADMIN | 204, wiadomość klientowi/pracownikowi; dla INVITED ponowienie zaproszenia |
| POST `/api/auth/password-actions/complete` | Publiczny + jednorazowy token | 204, ustawienie hasła; zużyty/wygasły token → 400 |

Reset nie obejmuje administratorów ani kont DISABLED. ADMIN i EMPLOYEE są niezależnymi rolami. Operacje na profilu nie przyjmują ID właściciela z żądania — wynikają ze zweryfikowanego tokenu.

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

Odpowiedź logowania:

```json
{
  "accessToken": "podpisany-JWT",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

`expiresIn` to liczba sekund, nie tekst `15m`. Token domyślnie jest ważny 15 minut. Po wygaśnięciu ponów logowanie; nie istnieje endpoint refresh. Dane konta odczytaj przez `GET /api/account/me`. JWT nie zawiera hasła ani adresu — jego payload nie jest zaszyfrowany.

Przykład PowerShell (ustaw własne testowe dane):

```powershell
$api = 'http://localhost:8080'
$registration = @{
    email = 'klient@example.test'
    password = 'Przykladowe haslo 123!'
    firstName = 'Jan'
    lastName = 'Kowalski'
} | ConvertTo-Json
Invoke-RestMethod "$api/api/auth/register" -Method Post -ContentType 'application/json' -Body $registration

$loginBody = @{email = 'klient@example.test'; password = 'Przykladowe haslo 123!'} | ConvertTo-Json
$login = Invoke-RestMethod "$api/api/auth/login" -Method Post -ContentType 'application/json' -Body $loginBody
$headers = @{Authorization = "Bearer $($login.accessToken)"}
Invoke-RestMethod "$api/api/account/me" -Headers $headers
Invoke-RestMethod "$api/api/auth/logout" -Method Post -Headers $headers
Remove-Variable login, headers
```

Wylogowanie zwiększa `auth_version` konta i odbiera dostęp wszystkim wcześniejszym JWT, także na innych urządzeniach. Samo usunięcie kopii tokenu w przeglądarce nie unieważnia go na backendzie. Nie zapisuj tokenów ani rzeczywistych haseł w skryptach/logach. Po wylogowaniu albo odrzuceniu wygasłego JWT klient usuwa token z pamięci. Wywołując publiczne logowanie ponownie, nie dołączaj starego tokenu — błędny Bearer jest odrzucany również na publicznej ścieżce.

Swagger ma **Authorize**: wklej sam JWT, bez prefiksu Bearer. Origin Angulara w profilu lokalnym: `http://localhost:4200`; nagłówki Authorization i Content-Type są dozwolone. Bez cookies i `withCredentials`. Poza profilem lokalnym originy ustaw przez `CORS_ALLOWED_ORIGINS`; CORS nie zastępuje autoryzacji.

Stare `GET /api/auth/csrf`, cookies SESSION/XSRF-TOKEN i limit sesji 30 min/8 h nie należą już do kontraktu. Reset hasła nadal korzysta z osobnego jednorazowego losowego tokenu z wiadomości, a nie z JWT logowania.

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

Token w rzeczywistej wiadomości ma 43 znaki. Jest ważny 24 godziny dla zaproszenia lub 30 minut dla resetu. Nowe zaproszenie/reset unieważnia wcześniejsze niewykorzystane linki tego konta. Po ustawieniu hasła trzeba się zalogować. Wcześniejsze JWT zostają odrzucone na kolejnym żądaniu, dzięki kontroli wersji uwierzytelnienia w bazie.

Brak usługi pocztowej powoduje 503 i wycofanie operacji, bez ujawniania tokenu w odpowiedzi. Wysyłka synchroniczna nie daje gwarancji dokładnie jednej wiadomości — trwała kolejka pozostaje osobnym etapem.

## Błędy

Błędy API i filtrów bezpieczeństwa mają format `application/problem+json` z `status`, `title`, `detail`, `instance` i opcjonalnym `code`. Walidacja dodatkowo zawiera `errors` z nazwą pola i komunikatem, bez wartości hasła.

- 400: błędne pola, JSON, nieobsługiwane pola lub token ustawienia hasła.
- 401: brak logowania, błędne hasło, brakujący, wygasły, podrobiony lub unieważniony JWT.
- 403: brak uprawnień.
- 404: brak adresu/zasobu.
- 409: konflikt danych, zajęty e-mail, niedozwolony reset.
- 503: niedostępna poczta.

Katalog i magazyn opisuje [osobny kontrakt](catalog-api.md). Koszyk, zamówienia i płatności pozostają kolejnymi etapami. Nie ma publicznego „zapomniałem hasła” — zgodnie z zakresem reset inicjuje administrator.
