# TireShop — frontend

Aplikacja startowa w Angular 21 ma komponent standalone, routing, HttpClient oraz TypeScript w trybie strict. Widok startowy jest po polsku.

## Demo

Uruchom `docker compose up` w głównym katalogu TireShop i otwórz [frontend](http://localhost:4200). Żądania do `/api/` na tym samym porcie trafiają do backendu. Po zmianach w kodzie kontenera użyj `docker compose up --build`.

## Praca lokalna

Z Node.js 24.11.1 i npm wykonaj te polecenia w katalogu `frontend`:

```sh
npm ci
npm start
```

Serwer deweloperski odświeża stronę pod adresem [localhost:4200](http://localhost:4200). Plik `proxy.conf.json` kieruje `/api/**` do backendu demo na porcie 8081. Dla backendu z IntelliJ ustaw w nim port 8080. Jeśli demo zajmuje port 4200, zatrzymaj jego frontend poleceniem `docker compose stop frontend` z głównego katalogu.

Testy uruchomisz przez `npm test -- --watch=false`, a wersję produkcyjną zbudujesz przez `npm run build`.
