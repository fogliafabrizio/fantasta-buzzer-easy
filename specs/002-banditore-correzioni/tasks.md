---

description: "Task list per Correzioni del Banditore e Rifiniture"
---

# Tasks: Correzioni del Banditore e Rifiniture

**Input**: Design documents da `/specs/002-banditore-correzioni/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/endpoints.md](contracts/endpoints.md), [quickstart.md](quickstart.md)

**Tests**: **nessun task di test automatico.** La costituzione li vieta (VII.6). La verifica
è manuale e vive in [quickstart.md](quickstart.md): ogni fase si chiude eseguendo la sua
sezione del quickstart.

**Organization**: i task sono raggruppati per user story. Le fasi 3-9 corrispondono uno a
uno ai cinque gruppi di [plan.md](plan.md#ordine-di-implementazione) — il gruppo 3 del
piano è l'insieme delle fasi 5, 6 e 7.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallelizzabile (file diversi, nessuna dipendenza da task incompleti)
- **[Story]**: la user story di appartenenza (US1…US7)
- Ogni task porta il percorso esatto del file

## Path Conventions

Modulo Spring Boot unico, come da [plan.md](plan.md#source-code-repository-root):
backend in `src/main/java/fantasta/`, console Angular in `console/src/app/`, telefono
statico in `src/main/resources/static/telefono/`. Nessuna directory `tests/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: nessuna inizializzazione da fare. Il progetto esiste, la feature 1 è
completa, non ci sono nuove dipendenze da aggiungere né configurazione da toccare.

- [ ] T001 Verificare la linea di partenza: `mvn -q package`, avviare `java -jar target/fantasta-*.jar` su un log JSONL della feature 1 e confermare che lo stato si ricostruisce senza errori. È il riferimento contro cui confrontare ogni riavvio successivo

**Checkpoint**: baseline nota e riproducibile.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: le primitive di proiezione condivise da tutte le correzioni. Nessun evento
nuovo, nessun endpoint: dopo questa fase il comportamento osservabile è identico a prima.

**⚠️ Blocca US2, US3, US4, US5.** **Non blocca US1**, che non tocca la proiezione: per
l'ordine imposto dalla costituzione (appendice A) e dal piano, US1 si fa comunque per prima.

- [ ] T002 [P] Creare il record `Aggiudicazione(int idLotto, int idCalciatore)` in `src/main/java/fantasta/asta/Aggiudicazione.java`, con javadoc che spiega che è una voce della pila degli annullabili
- [ ] T003 [P] In `src/main/java/fantasta/asta/Partecipante.java`: aggiungere il campo `rettificaCrediti` (int, default 0), cambiare `getCrediti()` in `creditiTotali - getCreditiSpesi() + rettificaCrediti`, aggiungere `applicaRettificaCrediti(int delta)` e `Integer rimuovi(int idCalciatore)` che cerca la `VoceRosa` nei quattro ruoli, la toglie e ne restituisce il prezzo (`null` se assente). Aggiornare il javadoc di classe: la formula dei crediti non è più solo `totali − spesi`
- [ ] T004 In `src/main/java/fantasta/asta/Asta.java`: aggiungere il campo `Deque<Aggiudicazione> annullabili`, inizializzarlo a vuoto in `creaAsta` e in `ricostruisciDaLog` accanto a `calciatoriAssegnati`, e impilare `(idLotto, idCalciatore)` nel ramo `LottoAggiudicato` di `applicaEvento`. Il push va **dentro** la guardia già esistente `if (lottoCorrente != null && lottoCorrente.getIdLotto() == lag.getIdLotto())`, accanto ad `acquista` e `calciatoriAssegnati.add`: fuori da quella guardia si creerebbe una voce in pila senza la corrispondente voce di rosa, e l'annullamento cercherebbe poi di rimuovere qualcosa che non c'è. Live e rilettura sbaglierebbero allo stesso modo, quindi la verifica di riavvio **non** lo scoprirebbe
- [ ] T005 In `src/main/java/fantasta/asta/Asta.java`: aggiungere il metodo privato `String correzioniAmmesse()` che restituisce `null` se le correzioni sono ammesse, altrimenti il motivo del rifiuto. Blocca `APERTO`, `SCADUTO`, `IN_PAUSA`; **non** blocca `AGGIUDICATO` né l'assenza di lotto. Motivo: `"le correzioni sono possibili solo quando non c'e' un lotto in corso"`. Cinque chiamanti reali in arrivo, uno per correzione

**Checkpoint**: `mvn -q package` compila, il server riparte, lo stato ricostruito è identico
a T001 e `creditiInCircolazione` non si è mosso. Nulla è cambiato per l'utente.

---

## Phase 3: User Story 1 - Guardia sui pulsanti rapidi (Priority: P1) 🎯 MVP

**Goal**: un rilancio da pulsante rapido dichiara su quale offerta era basato e viene
rifiutato se nel frattempo l'offerta è cambiata; un doppio tap non produce due offerte.

**Independent Test**: quickstart §Gruppo 1 (scenari 1.1-1.7). Con due telefoni sullo stesso
lotto, far arrivare un rilancio da uno mentre l'altro sta per toccare un pulsante rapido:
la seconda offerta è rifiutata con il motivo corretto e i suoi crediti non si muovono.

**Dipendenze**: nessuna. Non richiede la Phase 2.

### Implementation for User Story 1

- [ ] T006 [US1] In `src/main/java/fantasta/asta/Asta.java`, aggiungere il parametro `Integer offertaBase` alla firma di `registraOfferta` e inserire la guardia come **passo 6** dei controlli: dopo lo `switch` sullo stato del lotto e prima del controllo `importoGrezzo == null`. Se `offertaBase != null` e diverso da `offertaCorrente != null ? offertaCorrente : 0` → `Esito.rifiuto409("l'offerta e' cambiata mentre rilanciavi")`. Riusare l'espressione già presente per la regola del rilancio, non introdurne una seconda
- [ ] T007 [US1] In `src/main/java/fantasta/asta/Asta.java`, aggiungere alla riga di log del rifiuto il valore dichiarato e quello corrente, in italiano, così dal log si capisce perché la guardia è scattata (principio VI)
- [ ] T008 [US1] In `src/main/java/fantasta/web/OffertaController.java`, leggere `offertaBase` dal body lasciandolo grezzo come già si fa con `importo` (`null` se assente o non numerico) e passarlo a `registraOfferta`. Il controller non interpreta: decide il dominio
- [ ] T009 [US1] In `src/main/resources/static/telefono/app.js`, far inviare ai pulsanti rapidi `offertaBase = lotto.offertaCorrente != null ? lotto.offertaCorrente : 0`, letto dallo snapshot su cui il pulsante è stato disegnato. L'input a importo libero **non** deve mai inviare il campo
- [ ] T010 [US1] In `src/main/resources/static/telefono/app.js`, disabilitare tutti i pulsanti rapidi subito dopo un tap e riabilitarli solo all'arrivo del successivo snapshot, riconnessione compresa. Non toccare l'input a importo libero
- [ ] T011 [US1] In `src/main/resources/static/telefono/app.js`, verificare che il motivo `"l'offerta e' cambiata mentre rilanciavi"` arrivi all'utente attraverso il percorso di rifiuto già esistente della feature 1, senza aggiungerne un secondo
- [ ] T012 [US1] Eseguire quickstart §Gruppo 1 e la §"verifica che conta più di tutte" (riavvio)

**Checkpoint**: la guardia funziona con due dispositivi reali; le offerte a importo digitato
si comportano esattamente come prima.

---

## Phase 4: User Story 2 - Annullamento dell'ultima aggiudicazione (Priority: P1)

**Goal**: il banditore annulla dalla console l'ultima aggiudicazione annullabile,
ripetutamente all'indietro; il calciatore torna libero e i crediti tornano a chi li ha versati.

**Independent Test**: quickstart §Gruppo 2 (scenari 2.1-2.9). Aggiudicare due calciatori,
annullare due volte, verificare che entrambi siano liberi, i crediti tornati e i telefoni
allineati senza ricaricare.

**Dipendenze**: Phase 2 (T003, T004, T005).

### Implementation for User Story 2

- [ ] T013 [P] [US2] Creare `src/main/java/fantasta/eventi/AggiudicazioneAnnullata.java` con i campi `idLotto`, `idCalciatore`, `codicePartecipante`, `importoRestituito`, costruttore vuoto per Jackson e costruttore completo con `super("AGGIUDICAZIONE_ANNULLATA", sequenza)`, sullo stampo di `LottoAggiudicato`
- [ ] T014 [US2] Registrare `AggiudicazioneAnnullata` in `@JsonSubTypes` su `src/main/java/fantasta/eventi/Evento.java` con nome `"AGGIUDICAZIONE_ANNULLATA"`
- [ ] T015 [US2] In `src/main/java/fantasta/asta/Asta.java`, aggiungere il ramo `AggiudicazioneAnnullata` in `applicaEvento` con i cinque effetti di [data-model.md](data-model.md#aggiudicazione_annullata): `rimuovi` la voce di rosa, `calciatoriAssegnati.remove`, togliere la voce dalla pila, e azzerare `lottoCorrente` **se e solo se** `lottoCorrente.getIdLotto() == idLotto`. Nessuna modifica a `rettificaCrediti`. Guardie difensive come nei rami della feature 1 (`if (p != null)`, esito di `rimuovi` non nullo): in rilettura non c'è un `Esito` a proteggere, e un log inatteso deve produrre una riga di log in italiano, non un NPE all'avvio
- [ ] T016 [US2] In `src/main/java/fantasta/asta/Asta.java`, implementare `public synchronized Esito annullaAggiudicazione(int idLotto)`: asta attiva → `correzioniAmmesse()` → pila non vuota (`409 "non c'e' nessuna aggiudicazione da annullare"`) → `idLotto` uguale alla cima (`409 "l'ultima aggiudicazione annullabile e' cambiata"`) → leggere dalla proiezione **corrente** chi ha il calciatore e a che prezzo → append + `applicaEvento` → riga di log in italiano
- [ ] T017 [US2] In `src/main/java/fantasta/web/Snapshot.java`, aggiungere il record annidato `SnapshotAnnullabile(int idLotto, int idCalciatore, String codicePartecipante, int importo)` e il campo `annullabile` (nullable) al record `Snapshot`
- [ ] T018 [US2] In `src/main/java/fantasta/asta/Asta.java`, popolare `annullabile` in `generaSnapshot` dalla cima della pila incrociata con la proiezione corrente (proprietario e prezzo di adesso, non quelli di `LOTTO_AGGIUDICATO`); `null` se la pila è vuota
- [ ] T018b [US2] In `console/src/app/services/sse.service.ts`, aggiungere l'interfaccia `SnapshotAnnullabile { idLotto: number; idCalciatore: number; codicePartecipante: string; importo: number }` e il campo `annullabile: SnapshotAnnullabile | null` all'interfaccia `Snapshot`. Il tipo è nullable per davvero: è `null` ogni volta che non c'è nulla da annullare, ed è da lì che la console decide se disegnare il pulsante
- [ ] T019 [US2] In `src/main/java/fantasta/web/ConsoleController.java`, aggiungere `POST /api/console/annulla-aggiudicazione` con body `{idLotto}`, validazione del campo, mappatura dell'`Esito` sui codici di [contracts/endpoints.md](contracts/endpoints.md#post-apiconsoleannulla-aggiudicazione) e `sseController.broadcast()` dopo il successo
- [ ] T020 [US2] Creare `console/src/app/console/correzioni/correzioni.component.ts` con la sola sezione annullamento: pulsante disponibile solo quando `annullabile` non è null, dialog PrimeNG di conferma che dice quale calciatore torna libero (nome risolto dal listone locale), a chi tornano quanti crediti e quali saranno i suoi crediti residui dopo
- [ ] T021 [US2] Montare `<app-correzioni>` in `console/src/app/app.component.ts` fra `app-lotto-corrente` e `app-tabella-partecipanti`
- [ ] T022 [US2] Eseguire quickstart §Gruppo 2, la §"verifica che conta più di tutte" controllando che dopo il riavvio la prossima annullabile sia la stessa, e la sequenza combinata **R1** di §"Le tre sequenze combinate"

**Checkpoint**: annullamento ripetibile all'indietro, non disponibile senza aggiudicazioni
né con un lotto in corso, disponibile con la scheda `AGGIUDICATO` a video.

---

## Phase 5: User Story 3 - Rettifica di un'aggiudicazione (Priority: P2)

**Goal**: il banditore corregge prezzo, assegnatario o entrambi di un'assegnazione
esistente, in una sola operazione e una sola riga di log.

**Independent Test**: quickstart §Gruppo 3 (scenari 3.1-3.5, 3.9a-3.9c, 3.14a-3.14b).
Aggiudicare un calciatore, rettificarne prezzo e assegnatario, verificare rose e crediti
di entrambi i partecipanti.

**Dipendenze**: Phase 2. Porta anche la segnalazione dei crediti negativi (FR-016b), che
US5 riusa.

### Implementation for User Story 3

- [ ] T023 [P] [US3] Creare `src/main/java/fantasta/eventi/AssegnazioneRettificata.java` con i campi `idCalciatore`, `codicePartecipanteVecchio`, `prezzoVecchio`, `codicePartecipanteNuovo`, `prezzoNuovo`
- [ ] T024 [US3] Registrare `AssegnazioneRettificata` in `@JsonSubTypes` su `src/main/java/fantasta/eventi/Evento.java` con nome `"ASSEGNAZIONE_RETTIFICATA"`
- [ ] T025 [US3] In `src/main/java/fantasta/asta/Asta.java`, aggiungere il ramo `AssegnazioneRettificata` in `applicaEvento`: `rimuovi` dalla rosa del vecchio, **poi** `acquista(ruolo, idCalciatore, prezzoNuovo)` sulla rosa del nuovo con il ruolo preso dal listone. **L'ordine è vincolante**: quando vecchio e nuovo sono lo stesso partecipante (rettifica del solo prezzo) le due operazioni agiscono sulla stessa lista con lo stesso `idCalciatore`, e invertirle farebbe rimuovere la voce appena inserita. Live e rilettura sbaglierebbero allo stesso modo, quindi la verifica di riavvio **non** lo scoprirebbe. `calciatoriAssegnati` e la pila **non** si toccano. Usare solo `idCalciatore` per trovare la voce: i campi `…Vecchio` sono denormalizzati per il log. Guardie difensive come nei rami della feature 1
- [ ] T026 [US3] Nello stesso ramo, aggiornare la scheda a video: se `lottoCorrente != null && stato == AGGIUDICATO && lottoCorrente.getIdCalciatore() == idCalciatore`, impostare `offerenteCorrente = codicePartecipanteNuovo` e `offertaCorrente = prezzoNuovo`. Deve valere anche in rilettura, quindi sta qui e non nel metodo di comando ([research.md A4](research.md#a4))
- [ ] T027 [US3] In `src/main/java/fantasta/asta/Asta.java`, implementare `public synchronized Esito rettificaAssegnazione(int idCalciatore, String codicePartecipante, Number prezzoGrezzo)`: asta attiva → `correzioniAmmesse()` → partecipante noto → calciatore assegnato a qualcuno (`409`) → prezzo intero `>= 0` → rifiuto `400 "la rettifica non cambierebbe nulla"` se assegnatario e prezzo coincidono con quelli correnti → append + `applicaEvento` → riga di log in italiano con prima e dopo. **Nessun rifiuto per crediti risultanti negativi**
- [ ] T028 [US3] In `src/main/java/fantasta/web/ConsoleController.java`, aggiungere `POST /api/console/rettifica-assegnazione` con body `{idCalciatore, codicePartecipante, prezzo}`, i codici di [contracts/endpoints.md](contracts/endpoints.md#post-apiconsolerettifica-assegnazione) e `sseController.broadcast()` dopo il successo (FR-017)
- [ ] T029 [US3] In `console/src/app/console/correzioni/correzioni.component.ts`, aggiungere la sezione rettifica: scelta fra i calciatori attualmente assegnati, campi assegnatario e prezzo precompilati con i valori correnti, e conferma che dice calciatore, assegnatario prima/dopo, prezzo prima/dopo e i crediti residui risultanti di entrambi. Non proporre la conferma se nulla cambierebbe
- [ ] T030 [US3] In `console/src/app/console/correzioni/correzioni.component.ts`, evidenziare nella conferma i crediti risultanti negativi (FR-016)
- [ ] T031 [P] [US3] In `console/src/app/console/tabella-partecipanti/tabella-partecipanti.component.ts`, marcare in modo vistoso la **riga intera** del partecipante con crediti negativi, non solo il numero: si deve notare scorrendo la tabella senza cercarla (FR-016b)
- [ ] T032 [P] [US3] In `src/main/resources/static/telefono/app.js` e `src/main/resources/static/telefono/style.css`, mostrare i propri crediti negativi in modo vistoso, con l'etichetta **"crediti negativi"** e l'importo. La frase enuncia il **fatto**, mai la regola: niente "non puoi rilanciare", niente spiegazioni di cosa il server permetta o vieti. Il telefono non ripete regole di dominio (principio III) e non deve ritrovarsi a mentire se un giorno la regola cambia. È comunque l'unico segnale che il partecipante riceve prima di provare a offrire ([research.md A8](research.md#a8))
- [ ] T033 [US3] Eseguire quickstart §Gruppo 3 scenari 3.1-3.5, 3.14a-3.14b, 3.16 (rettifica del solo prezzo: il calciatore non deve sparire dalla rosa), 3.9a-3.9c, e la sequenza combinata **R2** di §"Le tre sequenze combinate"

**Checkpoint**: rettifica combinata in una riga di log, scheda a video aggiornata, rosso
visibile su console e telefono prima che qualcuno provi a rilanciare.

---

## Phase 6: User Story 4 - Rimozione di un calciatore da una rosa (Priority: P2)

**Goal**: un calciatore esce da una rosa con un importo restituito deciso dal banditore,
anche diverso dal prezzo pagato, anche zero.

**Independent Test**: quickstart §Gruppo 3 (scenari 3.6, 3.7, 3.15). Rimuovere un calciatore
restituendo un importo diverso dal prezzo pagato e verificare rosa, crediti, disponibilità
del calciatore e totale in circolazione.

**Dipendenze**: Phase 2 (in particolare `applicaRettificaCrediti` da T003).

### Implementation for User Story 4

- [ ] T034 [P] [US4] Creare `src/main/java/fantasta/eventi/CalciatoreRimosso.java` con i campi `idCalciatore`, `codicePartecipante`, `prezzoPagato`, `importoRestituito`
- [ ] T035 [US4] Registrare `CalciatoreRimosso` in `@JsonSubTypes` su `src/main/java/fantasta/eventi/Evento.java` con nome `"CALCIATORE_RIMOSSO"`
- [ ] T036 [US4] In `src/main/java/fantasta/asta/Asta.java`, aggiungere il ramo `CalciatoreRimosso` in `applicaEvento`: `int prezzoInRosa = rimuovi(idCalciatore)`, poi `applicaRettificaCrediti(importoRestituito - prezzoInRosa)`, `calciatoriAssegnati.remove`, e togliere dalla pila la voce con quell'`idCalciatore` **ovunque si trovi** (non solo in cima): senza quel calciatore non c'è più un'aggiudicazione da disfare. Il delta si calcola dal valore **restituito da `rimuovi`**, mai dal campo `prezzoPagato` dell'evento: il rimborso viene dalla rosa e il delta deve venire dalla stessa fonte, altrimenti due numeri che devono coincidere hanno due origini diverse. `prezzoPagato` resta nell'evento **solo** come campo denormalizzato per la leggibilità del log e la proiezione non lo legge mai — non riusarlo per nessun calcolo. Guardie difensive come nei rami della feature 1
- [ ] T036b [US4] Nello stesso ramo, azzerare `lottoCorrente` se la rimozione riguarda il calciatore della scheda ancora a video: `lottoCorrente != null && stato == AGGIUDICATO && lottoCorrente.getIdCalciatore() == idCalciatore`. Una scheda che dice "vinto da Marco per 36" mentre quel calciatore è tornato libero è la stessa bugia dell'annullamento, con un comando diverso. Deve valere anche in rilettura, quindi sta qui e non nel metodo di comando ([research.md A4](research.md#a4))
- [ ] T037 [US4] In `src/main/java/fantasta/asta/Asta.java`, implementare `public synchronized Esito rimuoviDaRosa(int idCalciatore, Number importoGrezzo)`: asta attiva → `correzioniAmmesse()` → calciatore assegnato a qualcuno (`409`) → importo intero `>= 0` → leggere `prezzoPagato` dalla rosa → append + `applicaEvento` → riga di log in italiano che dice esplicitamente di quanto si muove il totale in circolazione
- [ ] T038 [US4] In `src/main/java/fantasta/web/ConsoleController.java`, aggiungere `POST /api/console/rimuovi-da-rosa` con body `{idCalciatore, importoRestituito}`, i codici di [contracts/endpoints.md](contracts/endpoints.md#post-apiconsolerimuovi-da-rosa) e `sseController.broadcast()` dopo il successo (FR-017)
- [ ] T039 [US4] In `console/src/app/console/correzioni/correzioni.component.ts`, aggiungere la sezione rimozione: scelta fra i calciatori assegnati, campo importo restituito, e conferma che dice quale calciatore esce da quale rosa, quanto viene restituito, i crediti residui risultanti **e il totale in circolazione prima e dopo** — è l'unica correzione che lo muove
- [ ] T040 [US4] Eseguire quickstart §Gruppo 3 scenari 3.6, 3.7, 3.15, 3.15a (scheda a video che sparisce), e la §"verifica che conta più di tutte" controllando `creditiInCircolazione` prima e dopo il riavvio

**Checkpoint**: rimozione con restituzione parziale, totale in circolazione mosso della
differenza esatta e coerente dopo il riavvio.

---

## Phase 7: User Story 5 - Aggiunta manuale di un calciatore a una rosa (Priority: P2)

**Goal**: un calciatore libero entra nella rosa di un partecipante a un prezzo deciso dal
banditore, senza passare da un lotto.

**Independent Test**: quickstart §Gruppo 3 (scenari 3.8, 3.9, 3.11). Aggiungere un
calciatore libero a un prezzo scelto e verificare rosa, crediti e indisponibilità del
calciatore per i lotti successivi.

**Dipendenze**: Phase 2. Per lo scenario 3.9 riusa la segnalazione dei negativi di T031-T032.

### Implementation for User Story 5

- [ ] T041 [P] [US5] Creare `src/main/java/fantasta/eventi/CalciatoreAggiunto.java` con i campi `idCalciatore`, `codicePartecipante`, `prezzo`
- [ ] T042 [US5] Registrare `CalciatoreAggiunto` in `@JsonSubTypes` su `src/main/java/fantasta/eventi/Evento.java` con nome `"CALCIATORE_AGGIUNTO"`
- [ ] T043 [US5] In `src/main/java/fantasta/asta/Asta.java`, aggiungere il ramo `CalciatoreAggiunto` in `applicaEvento`: `acquista(ruolo, idCalciatore, prezzo)` con il ruolo dal listone e `calciatoriAssegnati.add`. La pila degli annullabili **non** si tocca: un'aggiunta manuale non è un'aggiudicazione ([research.md A1](research.md#a1)). Guardie difensive come nei rami della feature 1
- [ ] T044 [US5] In `src/main/java/fantasta/asta/Asta.java`, implementare `public synchronized Esito aggiungiARosa(int idCalciatore, String codicePartecipante, Number prezzoGrezzo)`: asta attiva → `correzioniAmmesse()` → calciatore nel listone (`400`) → calciatore libero (`409`) → partecipante noto → prezzo intero `>= 0` → append + `applicaEvento` → riga di log in italiano. Un calciatore `fuoriLista` **è** aggiungibile a mano, a differenza di `apriLotto`
- [ ] T045 [US5] In `src/main/java/fantasta/web/ConsoleController.java`, aggiungere `POST /api/console/aggiungi-a-rosa` con body `{idCalciatore, codicePartecipante, prezzo}`, i codici di [contracts/endpoints.md](contracts/endpoints.md#post-apiconsoleaggiungi-a-rosa) e `sseController.broadcast()` dopo il successo (FR-017)
- [ ] T046 [US5] In `console/src/app/console/correzioni/correzioni.component.ts`, aggiungere la sezione aggiunta manuale: scelta ristretta ai calciatori **liberi** (listone meno `calciatoriAssegnati`), destinatario e prezzo, conferma che dice chi entra in quale rosa a che prezzo e i crediti residui risultanti, con i negativi evidenziati
- [ ] T047 [US5] Eseguire quickstart §Gruppo 3 scenari 3.8, 3.9, 3.10, 3.10a, 3.11, 3.12, 3.13, la §"verifica che conta più di tutte" e la sequenza combinata **R3** di §"Le tre sequenze combinate"

**Checkpoint**: gruppo 3 del piano completo. Le tre correzioni non-annullamento funzionano
e i crediti negativi sono visibili ovunque devono esserlo.

---

## Phase 8: User Story 6 - Impostazioni asta modificabili (Priority: P3)

**Goal**: nome dell'asta e durata del countdown modificabili tra un lotto e l'altro, con la
nuova durata in vigore dal lotto successivo e conservata attraverso il riavvio.

**Independent Test**: quickstart §Gruppo 4 (scenari 4.1-4.6). Modificare nome e durata tra
due lotti e verificare che il nome appaia aggiornato ovunque e che il lotto successivo usi
la nuova durata.

**Dipendenze**: Phase 2 (solo per `correzioniAmmesse()`).

### Implementation for User Story 6

- [ ] T048 [P] [US6] Creare `src/main/java/fantasta/eventi/ImpostazioniModificate.java` con i campi `nomeAsta` e `durataCountdown`
- [ ] T049 [US6] Registrare `ImpostazioniModificate` in `@JsonSubTypes` su `src/main/java/fantasta/eventi/Evento.java` con nome `"IMPOSTAZIONI_MODIFICATE"`
- [ ] T050 [US6] In `src/main/java/fantasta/asta/Asta.java`, aggiungere il ramo `ImpostazioniModificate` in `applicaEvento`: sostituisce `nomeAsta` e `durataCountdown`, nient'altro. Verificare che i rami esistenti `LottoAperto`, `OffertaAccettata` e `LottoRiaperto` continuino a leggere `durataCountdown` al momento della loro applicazione — è ciò che rende la rilettura fedele ([data-model.md](data-model.md#rilettura-del-log-e-ricostruzione-allavvio)) e **non** va cambiato
- [ ] T051 [US6] In `src/main/java/fantasta/asta/Asta.java`, implementare `public synchronized Esito modificaImpostazioni(String nomeAsta, Number durataGrezza)`: asta attiva → `correzioniAmmesse()` → nome non vuoto → durata intera `>= 1` → append + `applicaEvento` → riga di log in italiano. Il nome del file JSONL **non** cambia
- [ ] T051b [US6] In `src/main/java/fantasta/web/ConsoleController.java`, aggiungere `POST /api/console/impostazioni` con body `{nomeAsta, durataCountdown}`, i codici di [contracts/endpoints.md](contracts/endpoints.md#post-apiconsoleimpostazioni) e `sseController.broadcast()` dopo il successo (FR-017). Senza questo task il form di T053 non ha nulla da chiamare
- [ ] T052 [US6] In `src/main/java/fantasta/web/Snapshot.java` e in `generaSnapshot` di `src/main/java/fantasta/asta/Asta.java`, aggiungere i campi `nomeAsta` e `durataCountdown`
- [ ] T052b [US6] In `console/src/app/services/sse.service.ts`, aggiungere `nomeAsta: string` e `durataCountdown: number` all'interfaccia `Snapshot`. Sono i due valori con cui T053 precompila il form e T055 disegna l'intestazione: nessuno dei due va tenuto in un campo locale del componente, o dopo una modifica resterebbe indietro rispetto allo snapshot
- [ ] T053 [US6] Creare `console/src/app/console/impostazioni/impostazioni.component.ts`: form con nome asta e durata countdown precompilati dallo snapshot, disponibile solo quando non c'è un lotto in corso, con conferma. Nessun partecipante, codice o credito in questa schermata (FR-022)
- [ ] T054 [US6] In `console/src/app/console/lotto-corrente/lotto-corrente.component.ts` (o nell'intestazione di `console/src/app/app.component.ts`), mostrare `nomeAsta` dallo snapshot invece che da dove viene oggi
- [ ] T055 [P] [US6] In `src/main/resources/static/telefono/app.js`, mostrare `nomeAsta` dallo snapshot e aggiornarlo quando cambia
- [ ] T056 [US6] Eseguire quickstart §Gruppo 4 e la §"verifica che conta più di tutte", controllando in particolare lo scenario 4.6 (`ASTA_CREATA` immutata, nome del file invariato), più la §"Un caso in cui lo stato dopo il riavvio è diverso, ed è corretto": un lotto aperto che riparte in pausa al **nuovo** tempo pieno. Va confermato di persona, così in serata non viene scambiato per un difetto

**Checkpoint**: impostazioni modificabili e persistenti attraverso il riavvio, senza che
`ASTA_CREATA` sia stato toccato.

---

## Phase 9: User Story 7 - Vista avversari sul telefono (Priority: P3)

**Goal**: una sezione collassabile, chiusa di default, che mostra per ogni altro
partecipante i crediti residui e la rosa raggruppata per ruolo.

**Independent Test**: quickstart §Gruppo 5 (scenari 5.1-5.5). Da un telefono, aprire la
sezione dopo alcune aggiudicazioni e verificare che crediti e rose coincidano con la console.

**Dipendenze**: nessuna sul backend. **Zero modifiche al server**: niente endpoint, niente
campi nello snapshot (FR-025).

### Implementation for User Story 7

- [ ] T057 [P] [US7] In `src/main/resources/static/telefono/index.html`, aggiungere il contenitore della sezione avversari sotto il buzzer, chiuso all'apertura della pagina
- [ ] T058 [US7] In `src/main/resources/static/telefono/app.js`, disegnare la sezione da `snapshot.partecipanti` escludendo il proprio codice: per ciascun avversario crediti residui e rosa raggruppata per ruolo P/D/C/A, con i nomi risolti dal listone già in locale. Ridisegnarla a ogni snapshot mentre è aperta, senza che l'utente debba chiudere e riaprire, e senza che aprirla o chiuderla perda lo stato del buzzer
- [ ] T059 [US7] In `src/main/resources/static/telefono/app.js`, applicare agli avversari con crediti negativi lo stesso stile vistoso introdotto da T032, riusandolo e senza duplicarlo
- [ ] T060 [P] [US7] In `src/main/resources/static/telefono/style.css`, stile della sezione collassabile: chiusa non deve ridurre lo spazio del buzzer rispetto a oggi (SC-008)
- [ ] T061 [US7] Eseguire quickstart §Gruppo 5

**Checkpoint**: tutte e sette le user story funzionano.

---

## Phase 10: Polish & Cross-Cutting Concerns

- [ ] T062 Rileggere le righe di log delle cinque correzioni in `src/main/java/fantasta/asta/Asta.java`: ognuna deve dire cosa stava succedendo, su quale calciatore o partecipante, e con quali valori prima e dopo (principio VI). Nessuno stack trace nudo, nessun messaggio in inglese
- [ ] T063 [P] Aggiornare `specs/001-asta-funzionante/data-model.md`, sezione "Estensibilità per feature future": i nomi effettivi degli eventi divergono da quelli ipotizzati lì (un solo `ASSEGNAZIONE_RETTIFICATA` invece di `PREZZO_RETTIFICATO` + `ASSEGNATARIO_RETTIFICATO`). Sostituire la sezione con un rimando a `specs/002-banditore-correzioni/data-model.md`, così non restano due elenchi discordanti
- [ ] T064 Verifica di compatibilità: prendere un JSONL scritto **prima** di questa feature con il suo xlsx, avviare il server e confermare che ricostruisce lo stato di allora senza errori di parsing né righe ignorate (quickstart §"Compatibilità con i log della feature 1")
- [ ] T065 Verifica finale d'insieme: eseguire per intero la §"verifica che conta più di tutte" del quickstart con la sequenza completa (due aggiudicazioni, un annullamento, una rettifica di prezzo e assegnatario, una rimozione con restituzione parziale, un'aggiunta manuale, una modifica delle impostazioni), confrontando tutti e sei i valori annotati prima e dopo il riavvio, **più le tre sequenze combinate R1, R2 e R3** rieseguite di fila sullo stesso log: è il caso peggiore realistico e l'unico in cui le correzioni si incrociano davvero
- [ ] T066 Prova di prontezza serata (costituzione, appendice B): due dispositivi su hotspot da telefono, QR code generato a runtime, porta aperta nel firewall — con le correzioni applicate dal vivo mentre i telefoni sono collegati

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: nessuna dipendenza
- **Foundational (Phase 2)**: dipende da Phase 1. **Blocca US2, US3, US4, US5. Non blocca US1 né US7**
- **US1 (Phase 3)**: indipendente da tutto. Va per prima per ordine costituzionale, non per dipendenza tecnica
- **US2 (Phase 4)**: richiede Phase 2
- **US3, US4, US5 (Phase 5-7)**: richiedono Phase 2. Indipendenti fra loro sul backend; sul frontend condividono `correzioni.component.ts`, quindi vanno in serie o coordinate
- **US6 (Phase 8)**: richiede solo T005 di Phase 2. T053 (form) richiede T051b (endpoint) e T052b (tipo client)
- **US7 (Phase 9)**: nessuna dipendenza di backend. T059 richiede T032 (US3)
- **Polish (Phase 10)**: dopo le story che si intendono consegnare

### Ordine consegnato (dal piano e dall'appendice A della costituzione)

`Phase 1 → Phase 2 → US1 → US2 → US3 → US4 → US5 → US6 → US7 → Polish`

Se il tempo finisce, finisce dal fondo: meglio la serata senza US6 e US7 che con tutto
abbozzato.

### Punti di contesa sui file

Tre file sono toccati da quasi tutte le fasi e **non** vanno lavorati in parallelo:

- `src/main/java/fantasta/asta/Asta.java` — rami di `applicaEvento` e metodi di comando
- `src/main/java/fantasta/eventi/Evento.java` — registrazione `@JsonSubTypes`
- `src/main/java/fantasta/web/ConsoleController.java` — i cinque endpoint di correzione
- `src/main/java/fantasta/web/Snapshot.java` — `annullabile` (T017, fase 4) e `nomeAsta`/`durataCountdown` (T052, fase 8)
- `console/src/app/services/sse.service.ts` — l'interfaccia `Snapshot` lato client, che segue gli stessi due task: T018b (fase 4) e T052b (fase 8). Va tenuta allineata a `Snapshot.java` nella stessa fase in cui il campo nasce, non dopo
- `console/src/app/console/correzioni/correzioni.component.ts` — sezioni di US2, US3, US4, US5

### Parallel Opportunities

- **Phase 2**: T002 e T003 in parallelo (file diversi); T004 e T005 in serie sullo stesso file
- **Per ogni story**: la classe evento (T013, T023, T034, T041, T048) è sempre un file nuovo e parallelizzabile rispetto al resto della sua fase
- **US3**: T031 (console) e T032 (telefono) in parallelo fra loro e con i task di backend
- **US6**: T055 (telefono) in parallelo con T053-T054 (console)
- **US7**: T057 e T060 in parallelo
- **Polish**: T063 in parallelo con tutto il resto

---

## Parallel Example: Phase 2 e User Story 3

```bash
# Phase 2 — due file nuovi/indipendenti insieme:
Task: "Creare il record Aggiudicazione in src/main/java/fantasta/asta/Aggiudicazione.java"
Task: "rettificaCrediti, rimuovi e applicaRettificaCrediti in src/main/java/fantasta/asta/Partecipante.java"

# User Story 3 — la classe evento e le due segnalazioni FR-016b insieme:
Task: "Creare AssegnazioneRettificata in src/main/java/fantasta/eventi/AssegnazioneRettificata.java"
Task: "Riga intera marcata sui crediti negativi in console/src/app/console/tabella-partecipanti/tabella-partecipanti.component.ts"
Task: "Crediti negativi vistosi in src/main/resources/static/telefono/app.js e style.css"
```

---

## Implementation Strategy

### MVP (US1 da sola)

1. Phase 1 (T001)
2. Phase 3 — US1 (T006-T012)
3. **FERMARSI E VERIFICARE**: quickstart §Gruppo 1 con due dispositivi reali

US1 è consegnabile da sola e ha valore da sola: previene l'errore economico sul buzzer,
che è il punto in cui la serata può andare storta davvero. Non richiede la Phase 2.

### Consegna incrementale

1. Phase 1 + US1 → guardia sul buzzer (MVP)
2. Phase 2 → primitive di proiezione, nessun cambiamento visibile
3. US2 → annullamento: la correzione che il banditore cerca per prima
4. US3 + US4 + US5 → gruppo 3 del piano: rettifica, rimozione, aggiunta
5. US6 → impostazioni
6. US7 → vista avversari
7. Polish → T064 e T065 sono le verifiche che dicono se il log regge davvero

Dopo ogni fase il server deve ripartire e ricostruire uno stato identico: è la verifica
che si ripete di più e l'unica che scopre gli errori di proiezione.

---

## Notes

- **Nessun test automatico**: la verifica è il quickstart, eseguito a mano, e chiude ogni fase
- Gli eventi della feature 1 non cambiano di un campo: nessuna migrazione, i log esistenti
  restano rileggibili (T064 lo verifica)
- Ogni correzione è un evento appeso in fondo: nessun evento viene mai riscritto o rimosso
- Tutte le correzioni riusano il `synchronized` sull'istanza `Asta` già esistente. Se un
  task porta a scrivere un nuovo lock o un nuovo oggetto di sincronizzazione, è il segnale
  che si sta sbagliando strada
- Commit dopo ogni task o gruppo logico; il main resta funzionante alla fine di ogni fase
- **Tre task portano un vincolo che la verifica di riavvio non intercetterebbe**, perché
  live e rilettura sbaglierebbero allo stesso modo e quindi convergerebbero su uno stato
  sbagliato: T004 (push dentro la guardia), T025 (`rimuovi` prima di `acquista`) e T036
  (delta dal valore restituito, non dal campo dell'evento). Vanno letti prima di scrivere
  il codice, non verificati dopo
- Gli id `T018b`, `T036b`, `T051b` e `T052b` sono inserimenti successivi alla prima
  stesura: mantengono validi i riferimenti esistenti invece di rinumerare la coda
