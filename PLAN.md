# FRECKLES TV – Piano di lavoro

App Android per tracciare film e serie TV: episodi visti, tempo totale speso, ricerca online, notifiche nuove stagioni, import JSON (export TV Time).

Legenda: `[ ]` da fare · `[x]` fatto. Lavorare in ordine, una fase per volta, un commit per fase.

## Decisioni tecniche (già prese)

- **Linguaggio/UI**: Kotlin + Jetpack Compose + Material 3 (Material You, tema dark/light, animazioni).
- **Architettura**: MVVM, single-activity, Navigation Compose, DI manuale (`FreckleApp`), niente Hilt.
- **Standalone**: nessun login né server. Al primo avvio si chiede solo il nome utente (DataStore), poi l'app parte da sola. Unica rete: TMDB.
- **Database locale**: Room (fonte di verità, funziona offline).
- **Rete**: Retrofit + kotlinx.serialization (o Moshi), Coil per le immagini.
- **Notifiche**: WorkManager (controllo periodico) + NotificationManager.
- **Min SDK** 26, target ultimo SDK stabile.
- **Sorgente dati: TMDB, non IMDb.** IMDb non ha un'API pubblica gratuita (quella ufficiale è a pagamento via AWS). TMDB è gratuita, ha poster, trama, durata episodi, date di uscita, e restituisce l'`imdb_id` (`external_ids`) di ogni titolo. Nell'app mostro/collego la pagina IMDb tramite quell'id.
  - API key TMDB già fornita, salvata in `local.properties` (`TMDB_API_KEY`, gitignored, NON committare). Se manca, l'app mostra un messaggio chiaro.
  - Linguaggio risultati: `it-IT` con fallback `en-US`.

## Analisi del JSON di import (`tvtime-series-2026-07-07.json`)

- Array di serie: `uuid`, `id.tvdb` (sempre presente), `id.imdb` (sempre `null`), `title`, `status` (`up_to_date` | `continuing` | `stopped`), `is_favorite`, `created_at`, `seasons[]`.
- `seasons[]`: `number`, `is_specials`, `episodes[]` (alcune stagioni hanno `episodes: []`).
- `episodes[]`: `id.tvdb`, `number`, `name`, `special`, `is_watched`, `watched_at` (nullable), `rewatch_count`, `watched_count`.
- Stagione 0 / `special: true` = extra (making of, podcast): **esclusi dal conteggio e dal tempo**, ma importati/mostrabili a parte. Alcune serie hanno episodi `special` dentro stagioni normali (es. Euphoria, Black Mirror "White Christmas" che è visto): gestirli per flag `special`, non per numero stagione.
- Solo serie, nessun film nel file: il parser deve però accettare in futuro un array `movies` (vedi Fase 6).
- Nomi di serie con suffissi tipo "(IT)", "(2018)", "(US)": per il match usare il tvdb id, non il titolo.
- Il file ha migliaia di episodi: import in background con barra di progresso, in transazione Room a batch.

## Fase 0 – Setup progetto
- [x] Creare progetto Android Gradle (Kotlin DSL, version catalog) in questa cartella, package `com.freckles.tv`.
- [x] Dipendenze: Compose BOM, Material3, Navigation, Hilt, Room (KSP), Retrofit, serialization, Coil, WorkManager, DataStore.
- [x] `.gitignore` (include `local.properties`, `build/`, `.gradle/`).
- [x] `BuildConfig` field `TMDB_API_KEY` letto da `local.properties`.
- [ ] Verificare che `./gradlew assembleDebug` compili.

## Fase 1 – Dati e database
- [ ] Entità Room: `Show`, `Season`, `Episode`, `Movie`, con `tmdbId`, `tvdbId`, `imdbId`, titolo, poster, backdrop, trama, `status`, `isFavorite`, `notifyEnabled` (campanella), `runtimeMinutes`.
- [ ] `Episode`: `watched`, `watchedAt`, `watchCount`, `runtimeMinutes`, `isSpecial`.
- [ ] `Movie`: `watched`, `watchedAt`, `runtimeMinutes`.
- [ ] DAO con Flow; query di statistiche: episodi visti/totali per serie, minuti totali visti (serie + film, rewatch contati con `watchCount`), esclusi gli special.
- [ ] Repository che unisce DB e API.

## Fase 2 – Client TMDB
- [ ] Servizi: `search/multi`, `tv/{id}` (+ `external_ids`), `tv/{id}/season/{n}`, `movie/{id}`, `find/{id}?external_source=tvdb_id`, `trending`.
- [ ] Durata episodio: `runtime` del singolo episodio, fallback `episode_run_time` della serie, fallback 40 min.
- [ ] Gestione errori/offline, cache locale.

## Fase 3 – Schermate principali
- [ ] Navigation bar inferiore: **Home · Cerca · Libreria · Statistiche**.
- [ ] **Home**: "Continua a guardare" (prossimo episodio da vedere con tasto rapido "segna visto"), in arrivo, tendenze.
- [ ] **Cerca**: ricerca live (debounce) su TMDB, risultati serie/film con poster, tasto "Aggiungi".
- [ ] **Dettaglio serie**: backdrop grande, trama, stagioni espandibili, check per episodio, "segna stagione come vista", progress ring, **campanella** per le notifiche, link IMDb, preferito.
- [ ] **Dettaglio film**: come sopra, con tasto "Visto".
- [ ] **Libreria**: tab Serie/Film, filtri (in corso, completate, preferite, da iniziare), ordinamento.
- [ ] **Statistiche**: episodi visti, film visti, tempo totale (formato "X mesi Y giorni Z ore"), grafico visione per mese, serie più viste.

## Fase 4 – Design moderno
- [ ] Tema Material 3 con palette custom (accento vivace, dark mode di default), colori dinamici su Android 12+.
- [ ] Poster a card con angoli arrotondati, gradienti sui backdrop, transizioni condivise tra lista e dettaglio.
- [ ] Animazioni: progress ring, check episodio, campanella (scuote quando attivata), shimmer di caricamento.
- [ ] Icona app adattiva e splash screen.
- [ ] Stati vuoti illustrati, haptic feedback sui check.

## Fase 5 – Notifiche nuove stagioni
- [ ] Campanella nel dettaglio serie → salva `notifyEnabled`.
- [ ] `PeriodicWorkRequest` (ogni ~12 ore, vincolo rete): per ogni serie con campanella interroga TMDB; notifica se compaiono una nuova stagione o un `next_episode_to_air`/`air_date` nuovi rispetto all'ultimo stato salvato.
- [ ] Canale notifiche, permesso `POST_NOTIFICATIONS` (Android 13+), tap sulla notifica apre il dettaglio.
- [ ] Impostazioni: attiva/disattiva globale, frequenza controllo.
- [ ] Nota: niente server push, quindi il controllo è periodico (ritardo possibile di qualche ora); dichiararlo nelle impostazioni.

## Fase 6 – Import JSON
- [ ] Selezione file con Storage Access Framework (`OpenDocument`).
- [ ] Parser in streaming per il formato sopra (`kotlinx.serialization` con `ignoreUnknownKeys`).
- [ ] Per ogni serie: risolvere TMDB tramite `find` col `tvdb` id; se fallisce, ricerca per titolo (pulendo suffissi tipo "(IT)", anno) e segnarla come "da verificare".
- [ ] Mappare episodi per numero stagione/episodio (TMDB li scarica per stagione) e applicare `is_watched`, `watched_at`, `watched_count`.
- [ ] Mantenere `is_favorite`, `created_at`.
- [ ] Schermata di progresso con conteggio importati/saltati/errori e report finale.
- [ ] Import idempotente: rieseguirlo non duplica (chiave `tmdbId`).
- [ ] Predisporre supporto a un array `movies` opzionale.
- [ ] Test unitario del parser sul file reale (copiare un estratto in `app/src/test/resources`; NON committare l'intero file se contiene dati personali, valutare con l'utente).

## Fase 7 – Rifinitura
- [ ] Backup/esportazione JSON nello stesso formato.
- [ ] Test unitari (calcolo tempo/statistiche, parser) e UI test base.
- [ ] Gestione rotazione/tablet, accessibilità (content description, contrasto).
- [ ] Build release firmata (istruzioni in README).
- [ ] README con setup (chiave TMDB, build, installazione APK).

## Definizione di "fatto"
Installo l'APK, importo il JSON, vedo le mie serie con poster e progresso, cerco un titolo, attivo la campanella, e le statistiche mostrano episodi visti e tempo totale corretti.

## Stato
- Compila (`./gradlew assembleDebug` con il JBR di Android Studio come JAVA_HOME). Mai provata su emulatore/telefono.
- Fasi 0-3: fatte (DB, TMDB, Home/Cerca/Libreria/Statistiche/Dettaglio serie e film, campanella, preferiti).
- Fase 5: `NewSeasonWorker` ogni 12h (nuova stagione o premiere annunciata). Da provare.
- Fase 6: import TV Time in Impostazioni (icona ingranaggio in Home). Parser verificato sul file reale: 89 serie, 2889 episodi visti. Speciali ed extra ignorati.
- Export/import backup JSON (serie + film) fatto, campanella animata, haptic sui check. Import verificato dall utente sul file reale (tempo plausibile).
- Da fare: rifinitura grafica (icona vera, shimmer, transizioni), test notifiche su dispositivo.

## Note
- Il JSON personale è gitignored (`tvtime-series-*.json`).
