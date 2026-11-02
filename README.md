# Freckles TV

App Android personale per tenere traccia di **serie TV, film e videogiochi**: episodi visti, tempo totale speso, statistiche, notifiche per le nuove stagioni e import dall'export di TV Time.

Funziona offline: tutti i dati stanno sul telefono (database Room). La rete serve solo per cercare titoli e scaricarne le informazioni.

## Funzioni

- **Serie e film** da [TMDB](https://www.themoviedb.org/): ricerca, anteprima, "da vedere" o "già visto", episodi spuntabili, stagioni intere in un tocco, link a IMDb.
- **Videogiochi** da [RAWG](https://rawg.io/): stato (da giocare, in corso, finito, abbandonato), ore giocate inserite a mano, voto.
- **Statistiche**: tempo totale, serie più viste, generi preferiti, ore per anno e per mese.
- **Campanella**: controllo ogni ~12 ore delle serie seguite e notifica quando esce o viene annunciata una nuova stagione.
- **Profilo**: nome, avatar (emoji o foto), 5 film/serie e 5 videogiochi preferiti, tema chiaro/scuro e 7 colori (+ Material You).
- **Backup**: export/import JSON manuale e backup automatico giornaliero in una cartella a scelta (ultimi 7 file).
- **Import da TV Time**: legge il file `tvtime-series-*.json` (le serie vengono abbinate a TMDB tramite l'id tvdb).

## Compilare

Requisiti: **Android Studio** (recente, con il suo JDK incluso) e un telefono o emulatore con Android 8.0+.

1. Apri la cartella del progetto in Android Studio e lascia completare il Gradle sync.
2. Crea/completa il file `local.properties` nella radice (non va mai committato, è già in `.gitignore`):

   ```properties
   sdk.dir=C\:/Users/<utente>/AppData/Local/Android/Sdk
   TMDB_API_KEY=<chiave TMDB v3>
   RAWG_API_KEY=<chiave RAWG>
   ```

   - Chiave TMDB gratuita: account su themoviedb.org → Impostazioni → API.
   - Chiave RAWG gratuita: rawg.io/apidocs.
3. Premi **Run**, oppure da terminale:

   ```bash
   ./gradlew assembleDebug        # APK di debug in app/build/outputs/apk/debug/
   ./gradlew testDebugUnitTest    # test
   ```

   Da riga di comando serve `JAVA_HOME` che punti al JDK di Android Studio, ad es. `C:\Program Files\Android\Android Studio\jbr`.

### APK firmato (release)

La build di release viene firmata solo se in `local.properties` ci sono i dati del keystore:

```properties
RELEASE_STORE_FILE=C\:/percorso/freckles-release.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=freckles
RELEASE_KEY_PASSWORD=...
```

```bash
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk
```

Conserva keystore e password in un posto sicuro: senza, non potrai più pubblicare aggiornamenti installabili sopra la versione firmata.

Nota: il controllo lint della release è disattivato perché il lint di AGP 8.7 va in crash con il JDK 25 di Android Studio.

## Struttura

```
app/src/main/java/com/freckles/tv/
├── data/
│   ├── db/          Room: entità, DAO, database (schemi in app/schemas, migrazioni automatiche)
│   ├── remote/      client TMDB e RAWG (Retrofit + kotlinx.serialization)
│   ├── importer/    import TV Time ed export/import del backup
│   ├── prefs/       profilo e preferenze (DataStore)
│   └── Repository.kt
├── notify/          worker delle nuove stagioni
├── backup/          worker del backup automatico
├── ui/              schermate Compose (home, cerca, libreria, statistiche, dettagli, profilo…)
└── util/            funzioni di formato (testate)
```

## Crediti

Dati di film e serie: TMDB. Dati dei videogiochi: RAWG. Questo prodotto usa le API di TMDB ma non è approvato né certificato da TMDB.
