# Śledzenie zamówień gościa i kolejka poczty — V8

Po zatwierdzeniu symulowanej płatności zamówienia gościa backend tworzy pierwszy link oraz zadanie wysyłki na e-mail zapisany w zamówieniu. Zapisy należą do tej samej transakcji co płatność i magazyn. Ponowienie sukcesu nie tworzy następnego linku ani zadania. Dla zamówienia konta nadal służy panel klienta i JWT; nie tworzymy publicznego linku.

## API

| Operacja | Uwierzytelnienie | Wynik |
|---|---|---|
| GET /api/guest/orders/{id}/tracking-link | X-Purchase-Token | 200: trackingUrl, expiresAt |
| GET /api/order-tracking | X-Tracking-Token | 200: orderNumber, status, trackingNumber |
| POST /api/order-tracking/link-requests | publiczne | 202: ten sam ogólny komunikat |

Pierwszy endpoint służy ekranowi potwierdzenia: wymaga nadal ważnego kontekstu zakupu (24 h od podsumowania). Nie przyjmuje tokenu koszyka ani samych identyfikatorów. Przed zapłatą zwraca 409 ORDER_NOT_PAID; obcy lub wygasły kontekst to 404 ORDER_NOT_FOUND. Odczyt nie generuje nowego tokenu i nie przedłuża jego ważności. Zalogowanie po zakupie gościa nie zmienia tego mechanizmu.

Pierwszy link jest ważny 90 dni od paid_at. Śledzenie działa niezależnie od wygaśnięcia tokenu zakupu i JWT. Zwraca wyłącznie numer zamówienia, bieżący status i numer przesyłki (null, gdy brak). Nie udostępnia kontaktu, adresu, pozycji, cen ani operacji edycji, anulowania czy płatności. Zły, brakujący lub wygasły token zawsze daje 404 TRACKING_NOT_FOUND. Dokładnie w chwili expiresAt link już nie działa.

Odpowiedzi tych endpointów mają Cache-Control: no-store. Format błędów to ProblemDetail. Swagger ma osobne schematy purchaseToken oraz trackingToken.

## Link w Angularze

Domyślny adres to `http://localhost:4200/track-order#token=...`. Token jest we fragmencie, który przeglądarka nie wysyła do serwera przy otwieraniu strony. Angular ma odczytać go, usunąć fragment z paska adresu przez history.replaceState i przekazać token w nagłówku X-Tracking-Token do API. Nie umieszczaj go w query, logach ani systemach analitycznych. Backend nie przyjmuje tokenu śledzenia z query, body ani cookie. CORS dopuszcza ten nagłówek dla skonfigurowanych originów.

Link na ekranie potwierdzenia lub w wiadomości z Mailpit prowadzi do widoku śledzenia. Formularz na tej samej stronie pozwala poprosić o odnowienie linku po podaniu numeru zamówienia i adresu e-mail z zakupu. Nie ma integracji z kurierem. V8 dodaje pole tracking_number do zamówienia; V9 udostępnia jego uzupełnianie przy wysyłce i korektę przed doręczeniem w [API pracownika](fulfillment-api.md).

## Odnowienie

Żądanie:

```json
{
  "orderNumber": "TS-00000000-0000-0000-0000-000000000000",
  "email": "adres-podany-przy-zakupie@example.com"
}
```

E-mail jest wyłącznie sprawdzeniem danych zamówienia. Odbiorca wiadomości zawsze pochodzi z jego zapisanej kopii. API nie pozwala wybrać innego odbiorcy i nie zwraca nowego tokenu ani informacji o istnieniu zamówienia.

Poprawnie zbudowane żądanie zawsze daje 202 z tym samym komunikatem — także dla nieznanego numeru, złego e-maila, zamówienia nieopłaconego, konta klienta lub przekroczenia limitu. Niepoprawny format numeru/e-maila daje 400 VALIDATION_FAILED; nieznane pola JSON są odrzucane.

Limity egzekwuje PostgreSQL atomowo, także między instancjami backendu: 10 żądań na 15 minut dla adresu sieciowego oraz 1 na 15 minut dla numeru zamówienia. Liczą się także nieudane próby. Nie ufamy klientowskiemu X-Forwarded-For; za reverse proxy limit będzie wspólny dla adresu proxy, dopóki nie zostanie osobno skonfigurowana zaufana infrastruktura. Przekroczenie limitu nie powoduje wysyłki.

Odnowiony link ma nowe losowe 32 bajty i jest ważny 90 dni od wydania. Stare linki pozostają ważne do własnego terminu, więc anonimowe żądanie nie odbiera dostępu dotychczasowemu odbiorcy. Po utracie kontekstu zakupu nowy link otrzymuje się wyłącznie e-mailem. Dla opłaconych zamówień gościa sprzed V8, bez pierwszego linku, również użyj odnowienia.

## Poczta i awarie

Decyzja użytkownika z 5 listopada 2026: kolejka w tej samej bazie, bez brokera. Zadanie co 15 sekund obsługuje maksymalnie 20 wiadomości, każdą w osobnej transakcji. FOR UPDATE SKIP LOCKED chroni przed równoczesną obsługą tego samego zadania przez dwa procesy. SMTP jest wywoływane dopiero dla zatwierdzonego zapisu kolejki, bez blokowania zamówienia.

Po błędzie SMTP następują ponowienia po 1, 2, 4 i 8 minutach — łącznie maksymalnie 5 prób. Stan PENDING przechodzi do SENT albo FAILED. Błąd odszyfrowania lub wygasły link kończą zadanie jako FAILED bez wysyłki. Awaria poczty nie cofa płatności ani wydania magazynowego. Po wyczerpaniu prób odbiorca może zażądać nowego linku.

SMTP nie gwarantuje dokładnie jednej wiadomości: awaria po przyjęciu e-maila, lecz przed zatwierdzeniem SENT w bazie może spowodować ponowną wysyłkę tej samej wiadomości. Link pozostaje ten sam. Po SENT/FAILED zaszyfrowana treść jest usuwana; pozostają metadane zadania.

Lokalnie wiadomości trafiają do Mailpit: http://localhost:8025. Testy awarii i ponowień zastępują JavaMailSender atrapą. Pełny scenariusz `MvpJourneyIntegrationTests` wysyła rzeczywiste SMTP do osobnego kontenera Mailpit i sprawdza treść otrzymanej wiadomości; nie wysyłamy poczty do rzeczywistych odbiorców.

## Konfiguracja i przechowywanie sekretów

- TRACKING_SECRET: wymagany, Base64 dokładnie 32 losowych bajtów, niezależny od JWT_SECRET. Zachowaj tę samą wartość między restartami i na wszystkich instancjach. Brak lub zły format zatrzymuje start.
- TRACKING_LINK_URL: zaufany adres strony śledzenia, domyślnie http://localhost:4200/track-order. HTTPS, poza lokalnym localhost/127.0.0.1; bez query, fragmentu i danych logowania.
- ORDER_MAIL_JOB_ENABLED: domyślnie true. Wyłączenie zatrzymuje wysyłkę oraz okresowe usuwanie wygasłych kopii ekranowych i starych liczników; nie wyłącza walidacji terminów dostępu w API.
- Istniejące MAIL_HOST, MAIL_PORT, MAIL_FROM i pozostałe ustawienia SMTP pozostają wspólne z pocztą kont.

W bazie znajduje się hash tokenu do sprawdzania dostępu. Pierwszy token ma dodatkową zaszyfrowaną kopię wyłącznie na potrzeby odzyskania linku na uprawnionym ekranie potwierdzenia, do terminu kontekstu zakupu. Zadanie okresowe usuwa ją po tym terminie. Odnowione tokeny nie mają kopii ekranowej. Cała treść kolejki, w tym e-mail i link, jest zaszyfrowana.

Szyfrowanie realizuje Spring Security AesGcmBytesEncryptor, bez własnego algorytmu: [dokumentacja modułu kryptograficznego](https://docs.spring.io/spring-security/reference/features/integrations/cryptography.html). Utrata lub zmiana TRACKING_SECRET uniemożliwi odczyt istniejących zaszyfrowanych kopii i oczekujących wiadomości. Już wydane linki nadal weryfikowane są po hashu; planowana rotacja klucza wymaga osobnej migracji zaszyfrowanych danych.

Migracja V8 dodaje order_access_token, order_email_outbox, tracking_request_limit oraz customer_order.tracking_number. Nie zmienia historycznych migracji ani danych zamówień. Nie przechowuj sekretu w Git, .env.example ani współdzielonej konfiguracji IntelliJ.
