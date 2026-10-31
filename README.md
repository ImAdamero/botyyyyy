# EasyBot (Fabric 1.21.11)

Menedżer botów z osobną konsolą dla każdego bota i ogólną konsolą błędów.

## Sterowanie (w grze, bez otwartego okna)

| Klawisz | Co robi |
|---|---|
| **X** | menu EasyBot: adres, ilość botów, odstęp, hasło, komenda po zalogowaniu, nicki, Start/Stop |
| **B** | ogólna konsola błędów wszystkich botów (czemu nie wchodzą itd.) |
| **dwuklik w wiersz bota** (w menu X) | osobna konsola tego jednego bota: czat graczy, komunikaty serwera, wysyłanie czatu/komend jako ten bot |

Klawisze można zmienić w *Opcje → Sterowanie → EasyBot*.

## Zachowanie botów

- Pierwszy bot wchodzi od razu, każdy następny po **stałym odstępie** (domyślnie 18 s, pole „Odstęp s").
- Automatyczne `/register <hasło>` i `/login <hasło>` (domyślnie hasło `eloelo`), gdy serwer o to prosi.
  Jeśli opis użycia ma dwa argumenty, wysyła hasło dwa razy.
- Opcjonalna komenda po zalogowaniu (np. `/warp afk`).
- Kod z mapy (captcha) trzeba wpisać samemu: wejdź w konsolę bota (dwuklik) i wyślij kod jako czat.

## Budowanie na GitHubie (bez instalowania czegokolwiek)

1. Załóż puste repozytorium na github.com.
2. Wgraj do niego **całą zawartość tego folderu** razem z ukrytym folderem `.github`
   (Add file → Upload files → przeciągnij wszystkie pliki i foldery).
3. Wejdź w zakładkę **Actions** → workflow **build** → poczekaj na zielony znaczek.
4. Wejdź w ukończony przebieg → na dole **Artifacts** → pobierz **EasyBot-1.21.11** → rozpakuj → plik `.jar`.
5. Wrzuć `.jar` do folderu `mods` razem z **Fabric Loader** i **Fabric API** dla 1.21.11.

Jeśli build się nie uda: otwórz przebieg → krok **Build** → skopiuj czerwony błąd i wyślij go (zwykle chodzi
o nazwę metody albo wersję biblioteki, to szybka poprawka).

## Ustawienia

`config/easybot.json` (tworzy się sam): adres, ilość, odstęp, hasło, komenda po zalogowaniu, nicki.

## Wersje użyte w projekcie (`gradle.properties`)

Minecraft 1.21.11, Yarn 1.21.11+build.4, Fabric Loader 0.18.4, Fabric API 0.141.3+1.21.11,
MCProtocolLib 1.21.11-1, Loom 1.15-SNAPSHOT, Gradle 9.2.1 (instalowany przez GitHub Actions).
