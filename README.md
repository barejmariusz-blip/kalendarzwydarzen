# Kalendarz Wydarzeń

Agregator terminów sportowych na Androida. Zaznaczasz, co cię interesuje (federacja, liga, drużyna, zawodnik, dowolny kalendarz online), a apka:

1. pobiera nadchodzące wydarzenia,
2. wpisuje je do kalendarza w apce (lista + widok miesiąca),
3. przypomina powiadomieniem (np. dzień przed, godzinę przed, na start),
4. na życzenie dodaje wydarzenie do kalendarza Google w telefonie.

Wyników nie pokazuje. Od tego są inne apki, tu chodzi tylko o to, żeby nic cię nie ominęło.

APK: **Releases → latest → kalendarz-wydarzen.apk** (budowany automatycznie po każdym pushu na `main`, ten sam klucz co Dziennik FBW i Post Przerywany).

## Źródła danych

| Źródło | Klucz | Co daje | Uwagi |
|---|---|---|---|
| **ESPN** | brak | UFC, F1, IndyCar, Ligue 1, Eredivisie, Liga Konferencji, Liga Narodów, mecze towarzyskie, ATP, WTA, Bundesliga, PL, La Liga, Serie A, LM, LE, NBA, NHL, NFL | Nieoficjalne API. Działa bez rejestracji, ale może się zmienić bez ostrzeżenia. |
| **FIS** | brak | Skoki: Puchar Świata mężczyzn i kobiet | Oficjalny kalendarz ICS. W trybie „Polacy” apka czyta listę startową lub wyniki konkursu (do 10 dni przed) i oznacza tylko potwierdzonych Polaków. |
| **TheSportsDB (kolejki)** | darmowy `123` | Ekstraklasa, PlusLiga, Liga Mistrzów EHF, MotoGP | Darmowy klucz oddaje pełną kolejkę. Apka szuka bieżącej kolejki i bierze 6 kolejnych. ESPN nie ma Ekstraklasy. |
| **TheSportsDB (sezon)** | darmowy `123` | KSW | Darmowo do ~15 wydarzeń na sezon, a KSW ma ok. 12 gal rocznie, więc wystarcza. |
| **Kalendarz ICS** | brak | Wszystko, co ma kalendarz online (federacje, kluby, publiczne kalendarze Google) | Najbardziej uniwersalne. Tędy KSW, boks, freak fighty itp. |
| **TheSportsDB** | darmowy `123` / premium | Ligi i drużyny po ID | Darmowy klucz zwraca tylko **1** najbliższe wydarzenie i 10 lig. Sensowne tylko z premium (~9$/mies.). |
| **Ręcznie** | — | Pojedyncze wydarzenie | Na szybko, gdy nie ma źródła. |

ESPN: dla lig drużynowych można obserwować **całą ligę** (pobieranie dzień po dniu, do 45 dni) albo **konkretną drużynę** (pełny terminarz klubu jednym zapytaniem).

Do każdego źródła można dodać **filtr** (słowa po przecinku), np. ESPN Bundesliga + `Bayern` daje tylko mecze Bayernu, a UFC + `Prochazka` tylko jego walki.

## Tryb „Polacy” 🇵🇱

Przycisk w nagłówku. Zostają tylko wydarzenia z udziałem Polaków, w dwóch poziomach:

- **🇵🇱 pewne:** polska liga lub federacja (Ekstraklasa, PlusLiga, KSW), reprezentacja, Polak na karcie walk (flaga z ESPN), Polak w drabince turnieju, polski klub.
- **🇵🇱? standby:** Polak w kadrze klubu, ale może nie zagrać. Apka czyta skład klubu z ESPN (obywatelstwo, kontuzje, występy w sezonie) i podaje powód: gra regularnie, głównie z ławki, rzadko gra, nie zagrał w sezonie, kontuzja. Nic nie jest zakładane z góry: tenis pojawia się dopiero, gdy Polak jest w opublikowanej drabince, a wydarzenia bez danych o uczestnikach (np. skoki z kalendarza ICS) tylko wtedy, gdy w opisie jest nazwisko z listy „moi zawodnicy”.

Do tego w ustawieniach: lista „moi zawodnicy” (łapani po nazwisku zawsze, np. Sochan, którego ESPN nie oznacza jako Polaka), słowa kluczowe polskich klubów i osobny tryb dla każdego źródła (auto / zawsze / pomijaj).

### „Potwierdzone” ✔

Drugi przycisk obok „Polacy”. Zostaje tylko to, co jest już przesądzone: Polak na karcie walk, w opublikowanej drabince, na liście startowej FIS albo **w wyjściowym składzie** meczu. Składy meczowe ESPN publikuje ok. godzinę przed meczem. Apka sprawdza je przy otwarciu i w tle (60 i 25 minut przed meczem, a potem co 10 minut do startu), a gdy Polak jest w pierwszej jedenastce, wysyła powiadomienie „🇵🇱 ✔ … w wyjściowym składzie”.

## Gdzie oglądać 📺

Przy każdym wydarzeniu, od razu na liście, najpierw stoją **polscy nadawcy** (pogrubieni), a po kropce **pozostali** z danych źródła. Mapa praw obowiązuje na sezon 2026/27, stan na listopad 2026. Mecze reprezentacji Polski idą na TVP, skoki w Wiśle i Zakopanem dodatkowo na TVP, a WTA do końca 2026 w Canal+, od 2027 w Eleven. W ustawieniach każdego źródła możesz wpisać własnych nadawców, np. STS TV, i wtedy mają oni pierwszeństwo.

## Widżet

„Kalendarz Wydarzeń” w widżetach ekranu głównego (rozmiar 4×3) pokazuje 4 najbliższe wydarzenia według bieżącego trybu (wszystkie / Polacy / Potwierdzone), z godziną, nazwą, nadawcą i nazwiskami Polaków. Minione wydarzenia znikają same, bez otwierania apki. Kliknięcie otwiera aplikację.

## Architektura

```
assets/index.html     UI + logika (adaptery źródeł, parser ICS, synchronizacja, plan przypomnień), dane w localStorage
MainActivity.java     WebView + most window.Android:
                        http(id,url)       → pobieranie bez CORS (ESPN, ICS, TSDB)
                        schedule(json)     → przypomnienia (AlarmManager, do 48 najbliższych)
                        toCalendar(...)    → wpis w kalendarzu systemowym (Intent, bez uprawnień)
                        saveFile / notify* → kopia danych, uprawnienia powiadomień
Alarms.java           planowanie i wyświetlanie powiadomień
BootReceiver.java     odtworzenie przypomnień po restarcie / aktualizacji
```

Nowe źródło = nowa funkcja w `Sources` zwracająca `[{id, title, start, end, allDay, loc, info, url}]`.

## Stan: szkic v0.1

Działa:
- obserwowane: ESPN (12 lig), ICS, TheSportsDB, ręczne; filtr słów, kolor, osobne przypomnienia per źródło, pauza
- synchronizacja przy otwarciu (gdy minęły 3h) i przyciskiem
- lista nadchodzących + kalendarz miesięczny
- szczegóły wydarzenia: do kalendarza Google, wycisz, ukryj
- przypomnienia z dokładnym czasem, przetrwają restart telefonu
- eksport/import kopii

## Do zrobienia (propozycja kolejności)

1. **Test źródeł na telefonie:** potwierdzić, że ESPN odpowiada z apki, i dobrać ICS dla KSW, boksu i skoków.
2. **Synchronizacja w tle** (WorkManager co ~6h), żeby przesunięte terminy łapały się bez otwierania apki.
3. **Powiadomienie o zmianie terminu:** „UFC 325 przesunięte na …”, „walka X wypadła z karty”.
4. **Karta walk** dla gal (UFC/ESPN ma listę walk w `competitions`): obserwowanie konkretnego zawodnika bez filtra tekstowego.
5. **Więcej źródeł:** skoki (FIS), tenis, siatkówka, żużel; ewentualnie scraper dla federacji bez API.
6. **Gdzie oglądać po polsku:** mapowanie stacji (Polsat Sport, Canal+, Viaplay…).
7. Widget na ekran główny z najbliższymi wydarzeniami.
