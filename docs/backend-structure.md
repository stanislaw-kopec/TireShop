# Struktura kodu backendu

Kod w `backend/src/main/java/com/shop/tire` jest podzielony najpierw według domen sklepu. W domenach `account`, `catalog`, `cart`, `inventory`, `order` i `payment` pliki grupujemy według odpowiedzialności:

| Podpakiet | Zawartość |
|---|---|
| `controller` | Endpointy HTTP i pomocnicze mapowanie odpowiedzi HTTP. |
| `service` | Przypadki użycia, autoryzacja operacji, transakcje i współpraca z innymi domenami. |
| `repository` | Repozytoria JPA/JDBC i zapis/odczyt danych. |
| `dto` | Rekordy żądań i odpowiedzi API. |
| `entity` | Encje JPA z prywatnymi polami i metodami zmiany stanu. |
| `model` | Enumy, migawki danych i reguły domenowe, np. sumy koszyka. |
| `event` | Zdarzenia zamówienia: utworzenie, anulowanie i opłacenie. |
| `config` | Konfiguracja domeny oraz jej dokumentacja OpenAPI. |
| `job` | Zadania uruchamiane cyklicznie, delegujące pracę do serwisów. |

Przykładowo obsługa zamówienia prowadzi przez `order.controller.CustomerOrderController`, `order.service.OrderService` i `order.repository.OrderRepository`. Publiczne kontrakty odpowiedzi są w `order.dto`, a migawka zamówienia i status w `order.model`.

Nie każda domena potrzebuje wszystkich podpakietów. Koszyk i płatności korzystają z JDBC, więc nie mają podpakietu encji JPA. Mały pakiet `audit`, konfiguracja `security`, seed `demo` i istniejące podpakiety `shared` zachowują swój prosty podział.

Podpakiet w Javie nie dziedziczy dostępu do elementów pakietu nadrzędnego. Typy i metody używane między podpakietami są publiczne, ale pola encji pozostają prywatne. Kontrolery korzystają z serwisów; sam publiczny modyfikator repozytorium nie uprawnia innych domen do omijania reguł biznesowych.

Testy jednostkowe serwisów znajdują się w odpowiadających im pakietach w `backend/src/test/java`. Testy integracyjne HTTP obejmujące wiele domen pozostają w `com.shop.tire`.

Po przenoszeniu klas uruchom `mvnw.cmd clean verify` z katalogu `backend` (na Linux/macOS: `./mvnw clean verify`). Czysta kompilacja usuwa stare pliki `.class`, które mogłyby zostać wykryte przez Springa obok nowych pakietów.
