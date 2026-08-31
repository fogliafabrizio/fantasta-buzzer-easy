# Tasks: Asta Funzionante

**Input**: Design documents from `/specs/001-asta-funzionante/`

**Prerequisites**: plan.md, spec.md, data-model.md, contracts/endpoints.md, research.md, quickstart.md

**Ordine**: segue la sezione A della costituzione. Ogni fase e una fetta verticale
end-to-end (backend + telefono + console). Nessun task di test automatico, build,
containerizzazione o deploy.

**Convenzione**: i task marcati con `XLSX` dipendono dal formato reale del file xlsx.
Affrontali con il file vero sotto mano.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Parallelizzabile (file diversi, nessuna dipendenza da task incompleti)
- **[Story]**: User story di riferimento (US1..US8 da spec.md)

---

## Phase 1: Scaffold progetto

**Scopo**: struttura Maven, applicazione Spring Boot avviabile, scheletro dei due frontend.

- [X] T001 Creare pom.xml alla root con parent spring-boot-starter-parent 3.x, dipendenze spring-boot-starter-web, poi-ooxml 5.3.0, zxing core 3.5.3, zxing javase 3.5.3, Java 21, finalName fantasta in pom.xml
- [X] T002 [P] Creare la classe main Spring Boot FantastaApplication in src/main/java/fantasta/FantastaApplication.java
- [X] T003 [P] Creare application.properties con server.port=8080 e proprieta fantasta.dati.dir per la directory del file JSONL in src/main/resources/application.properties
- [X] T004 [P] Inizializzare il progetto Angular standalone con PrimeNG in console/ (angular.json, package.json, tsconfig.json, app.component.ts con routing base). Configurare proxy verso http://localhost:8080 per /api durante lo sviluppo
- [X] T005 [P] Creare i file scheletro del telefono: pagina HTML con meta viewport, foglio di stile vuoto, script JS vuoto in src/main/resources/static/telefono/index.html, src/main/resources/static/telefono/style.css, src/main/resources/static/telefono/app.js

**Checkpoint**: `mvn package -DskipTests` compila senza errori, `java -jar target/fantasta.jar` parte e risponde 404 su http://localhost:8080/api/listone.

---

## Phase 2: Gruppo 1 — Import xlsx e creazione asta (US1, US2)

**Goal**: il banditore importa il listone xlsx, crea l'asta con i partecipanti, ogni
partecipante inquadra il QR code dal telefono e vede il proprio nome. Il file JSONL
viene creato con l'evento ASTA_CREATA.

### Backend

- [X] T006 [P] [US1] Creare gli enum Ruolo (P, D, C, A), TipoAsta (INIZIALE, RIPARAZIONE), StatoLotto (APERTO, IN_PAUSA, SCADUTO, AGGIUDICATO) in src/main/java/fantasta/asta/Ruolo.java, src/main/java/fantasta/asta/TipoAsta.java, src/main/java/fantasta/asta/StatoLotto.java
- [X] T007 [P] [US1] Creare il record Calciatore (id, nome, ruolo, squadra, quotazione, fuoriLista) in src/main/java/fantasta/listone/Calciatore.java
- [X] T008 [US1] Creare la classe base Evento (tipo, istante, sequenza) con deserializzazione polimorfica Jackson sul campo tipo, e le sottoclassi AstaCreata (con campo fileListone) e AssegnazioneIniziale in src/main/java/fantasta/eventi/Evento.java, src/main/java/fantasta/eventi/AstaCreata.java, src/main/java/fantasta/eventi/AssegnazioneIniziale.java
- [X] T009 [US1] Creare LogEventi: append di un evento come riga JSON su file JSONL con fsync prima del return, lettura sequenziale del file all'avvio con deserializzazione polimorfica, creazione file se non esiste, nome file basato su data e nome asta in src/main/java/fantasta/eventi/LogEventi.java
- [X] T010 [US1] `XLSX` Creare ImportListone: lettura del primo foglio del file xlsx con Apache POI, identificazione colonne per nome di intestazione (riga 1), estrazione id/nome/ruolo/squadra/quotazione/fuoriLista, rilevamento automatico tipo asta da colonna FantaSquadra, estrazione assegnazioni pre-esistenti (FantaSquadra + Costo) per riparazione, calcolo somma costi per partecipante (per il calcolo dei crediti totali). Supportare due modalita di lettura: (1) completa alla creazione (catalogo + FantaSquadra/Costo per riparazione), (2) solo catalogo al riavvio (ignora FantaSquadra e Costo per evitare doppia assegnazione). Errore con riga e colonna se formato inatteso, nessun import parziale in src/main/java/fantasta/listone/ImportListone.java
- [X] T011 [P] [US1] Creare Partecipante (nome, codice, crediti, rosa come Map di Ruolo a List di Integer) e Lotto (idLotto, idCalciatore, stato, offertaCorrente, offerenteCorrente, secondiResidui) in src/main/java/fantasta/asta/Partecipante.java, src/main/java/fantasta/asta/Lotto.java
- [X] T012 [US1] Creare Asta: stato globale in memoria (nomeAsta, tipoAsta, durataCountdown, partecipanti, calciatoriAssegnati, lottoCorrente, prossimoIdLotto). banditorePartecipa e informativo (registrato nel log, non influenza il comportamento del server). Tutti i metodi pubblici che mutano lo stato sono synchronized (punto di serializzazione unico per asta). Metodo di proiezione che applica ASTA_CREATA (inizializza partecipanti con codici generati di 4 caratteri alfanumerici maiuscoli; per riparazione crediti = totali calcolati dal server; memorizza fileListone per il recovery) e ASSEGNAZIONE_INIZIALE (aggiunge calciatore a rosa, scala crediti, marca assegnato). Metodo generaSnapshot() synchronized che calcola secondiResidui in tempo reale dal timer del server (non dal valore dell'ultimo evento). Replay del log all'avvio in src/main/java/fantasta/asta/Asta.java
- [X] T013 [US1] Creare il record Snapshot (sequenza, lotto, partecipanti, calciatoriAssegnati) con la struttura esatta da contracts/endpoints.md, serializzabile con Jackson in src/main/java/fantasta/web/Snapshot.java
- [X] T014 [US1] `XLSX` Creare ConsoleController con due endpoint: (1) POST /api/console/analizza-listone — riceve multipart con file xlsx, chiama ImportListone, salva il file xlsx nella directory dati con nome basato su data e nome, risponde 200 con tipo asta, conteggio calciatori e nomi partecipanti per riparazione. Errori: 400 se formato invalido, 409 se asta gia in corso. (2) POST /api/console/crea-asta — riceve JSON (non piu multipart), usa il file xlsx gia salvato, crea eventi ASTA_CREATA (con fileListone = nome del file xlsx salvato; per riparazione: crediti totali = residui inseriti + somma costi pre-assegnati) e ASSEGNAZIONE_INIZIALE, li persiste via LogEventi, inizializza Asta, risponde 201 con nomi e codici. Errori: 400, 409, 412 se nessun file analizzato in src/main/java/fantasta/web/ConsoleController.java
- [X] T015 [P] [US1] Creare ListoneController con GET /api/listone che restituisce la lista completa dei calciatori come array JSON, 404 se l'asta non esiste in src/main/java/fantasta/web/ListoneController.java
- [X] T016 [US2] Creare SseController con GET /api/sse: crea SseEmitter, invia snapshot iniziale alla connessione (evento SSE di tipo snapshot), registra l'emitter per broadcast futuri, gestisce disconnessione (rimozione emitter). Se asta non creata, invia evento attesa. Metodo broadcast chiamato dopo ogni cambiamento di stato. Heartbeat: ScheduledExecutorService separato invia evento SSE con nome heartbeat e payload vuoto (`event: heartbeat\ndata: \n\n`) ogni 15 secondi a tutti gli emitter registrati; emitter che lanciano eccezione vengono rimossi in src/main/java/fantasta/web/SseController.java
- [X] T017 [US2] Creare QrCodeController con GET /api/qrcode/{codice}: genera immagine PNG del QR code con ZXing contenente URL http://{ip}:{porta}/telefono/?codice={codice} dove ip e rilevato a runtime dall'interfaccia di rete non-loopback. 404 se codice non esiste in src/main/java/fantasta/web/QrCodeController.java

### Console banditore

- [X] T018 [US1] Creare il servizio SSE in Angular: connessione a /api/sse, parsing eventi snapshot e attesa, registrazione su addEventListener('heartbeat', ...) per ricevere l'heartbeat, esposizione dello snapshot come Observable, riconnessione automatica. Monitoraggio connessione: timer di 20 secondi resettato a ogni heartbeat e a ogni snapshot; se scade, esporre un Observable connessionePersa=true; onerror come fallback. Alla riconnessione (snapshot iniziale ricevuto), connessionePersa=false. Aggiungere servizio listone: fetch GET /api/listone una volta dopo la creazione dell'asta, esposizione come Observable per lookup calciatori da id in console/src/app/services/sse.service.ts e console/src/app/services/listone.service.ts
- [X] T019 [US1] `XLSX` Creare la vista creazione asta in due passi: (1) upload file xlsx (PrimeNG FileUpload) via POST /api/console/analizza-listone, visualizzazione risultato (tipo asta, numero calciatori, nomi partecipanti se riparazione). (2) form con nome asta, durata countdown, checkbox banditore partecipa; per iniziale: tabella partecipanti (nome + crediti); per riparazione: tabella con nomi partecipanti dal passo 1 e campo crediti residui per ciascuno. Pulsante crea che invia JSON a POST /api/console/crea-asta. Visualizzazione errori dal server in console/src/app/console/crea-asta/
- [X] T020 [US2] Creare la sezione QR code nella console: dopo la creazione, mostrare per ogni partecipante il QR code (immagine da GET /api/qrcode/{codice}), il nome, il codice e l'URL leggibile in chiaro in console/src/app/console/qr-display/

### Telefono

- [X] T021 [US2] Implementare la pagina telefono: leggere il parametro codice dall'URL, connettersi a GET /api/sse tramite EventSource, scaricare il listone da GET /api/listone una volta, mostrare il nome del partecipante e lo stato "nessun lotto in corso" in src/main/resources/static/telefono/index.html e src/main/resources/static/telefono/app.js

---

**Verifica manuale Gruppo 1**:

1. `mvn package -DskipTests && java -jar target/fantasta.jar`
2. Aprire la console Angular (`ng serve` da console/) su http://localhost:4200
3. Caricare il file xlsx reale via analizza-listone: verificare che la console mostri tipo asta e conteggio calciatori
4. Compilare il form partecipanti, creare l'asta: verificare risposta 201 con nomi e codici
5. Verificare che nella directory dati esistano sia il file xlsx salvato che il file JSONL con l'evento ASTA_CREATA
6. Verificare che GET http://localhost:8080/api/listone restituisca i calciatori
7. Verificare che i QR code siano visibili in console con URL e codice leggibili
8. Inquadrare un QR code dal telefono: la pagina si apre e mostra il nome del partecipante e "nessun lotto in corso"
9. Aprire un secondo terminale con `curl -N http://localhost:8080/api/sse` e verificare che arrivi lo snapshot con lotto null e i partecipanti, e che ogni 15 secondi arrivi un evento heartbeat
10. Verificare che nel JSONL l'evento ASTA_CREATA contenga il campo fileListone con il nome del file xlsx salvato
11. Terminare il server, riavviare: verificare che il listone e lo stato dell'asta si ricostruiscano dal fileListone indicato in ASTA_CREATA

---

## Phase 3: Gruppo 2 — Apertura lotto e buzzer (US3, US8)

**Goal**: il banditore cerca un calciatore libero dalla console e apre un lotto. I
telefoni mostrano il calciatore con nome, ruolo, squadra, quotazione. I partecipanti
fanno offerte dal buzzer. Le offerte invalide vengono rifiutate con motivo.

### Backend

- [ ] T022 [P] [US3] Creare i tipi evento LottoAperto (idLotto, idCalciatore) e OffertaAccettata (idLotto, codicePartecipante, importo), registrarli nel deserializzatore polimorfico di Evento in src/main/java/fantasta/eventi/LottoAperto.java, src/main/java/fantasta/eventi/OffertaAccettata.java
- [ ] T023 [US3] Aggiungere ad Asta la proiezione per LOTTO_APERTO (crea lotto corrente in stato APERTO, genera idLotto) e OFFERTA_ACCETTATA (aggiorna offertaCorrente e offerenteCorrente). Aggiungere validazione offerta: rifiuto se importo non e un intero >= 1 (motivo "importo non valido"), se importo non superiore a offertaCorrente, se crediti insufficienti, se lotto non APERTO, se idLotto non corrisponde, con motivo testuale in italiano in src/main/java/fantasta/asta/Asta.java
- [ ] T024 [US8] Aggiungere a ConsoleController l'endpoint POST /api/console/apri-lotto (body: idCalciatore). Validazione: calciatore esiste, libero, non fuoriLista, nessun altro lotto in corso. Scrive LOTTO_APERTO, aggiorna Asta, broadcast snapshot in src/main/java/fantasta/web/ConsoleController.java
- [ ] T025 [US3] Creare OffertaController con POST /api/offerta (body: idLotto, codicePartecipante, importo). Chiama validazione su Asta; se valida scrive OFFERTA_ACCETTATA e broadcast; se rifiutata risponde 400 o 409 con campo motivo. Motivi: "importo non valido", "offerta non superiore all'offerta corrente", "crediti insufficienti", "lotto non aperto", "lotto in pausa", "lotto non corrispondente" in src/main/java/fantasta/web/OffertaController.java

### Console banditore

- [ ] T026 [US8] Aggiungere alla console la ricerca calciatori liberi: campo di testo per nome, filtro per ruolo, lista risultati dal listone locale (esclusi assegnati e fuoriLista via calciatoriAssegnati dello snapshot), pulsante per aprire lotto in console/src/app/console/ricerca-calciatore/
- [ ] T027 [US3] Aggiungere alla console la visualizzazione del lotto corrente: nome calciatore (da listone), ruolo, squadra, quotazione, offerta corrente, nome offerente (da partecipanti dello snapshot) in console/src/app/console/lotto-corrente/

### Telefono

- [ ] T028 [US3] Aggiungere al telefono la sezione lotto attivo: quando lo snapshot ha lotto non null e stato APERTO, mostrare nome/ruolo/squadra/quotazione del calciatore (lookup dal listone locale), offerta corrente e nome offerente in src/main/resources/static/telefono/app.js
- [ ] T029 [US3] Implementare il buzzer sul telefono: pulsanti rapidi +1, +2, +5, +10 (calcolati su offertaCorrente; se offertaCorrente e null la base e 0, quindi i pulsanti offrono 1, 2, 5, 10), campo importo libero, pulsante invia. POST /api/offerta con importo assoluto, idLotto dallo snapshot, codice dall'URL. Mostrare il motivo di rifiuto dal server se 400/409 (incluso "importo non valido") in src/main/resources/static/telefono/app.js

---

**Verifica manuale Gruppo 2**:

1. Con l'asta del Gruppo 1 ancora attiva (o ricreata), aprire la console
2. Cercare "Malen" nella ricerca calciatori, verificare che appaia con ruolo A
3. Premere "apri lotto" su Malen
4. Sul telefono: verificare che appaia Malen con nome, ruolo, squadra, quotazione
5. Dal telefono: premere il buzzer con importo 5. Verificare che l'offerta 5 appaia su tutti i dispositivi
6. Da un secondo telefono (o curl): offrire 3, verificare rifiuto con motivo "offerta non superiore all'offerta corrente"
7. Dal secondo telefono: premere +2 (= 7), verificare che l'offerta 7 appaia ovunque
8. Offrire 9999 (piu dei crediti), verificare rifiuto "crediti insufficienti"
9. Offrire 0 o un valore decimale: verificare rifiuto "importo non valido"
10. Controllare il file JSONL: devono esserci eventi LOTTO_APERTO e OFFERTA_ACCETTATA

---

## Phase 4: Gruppo 3 — Countdown, scadenza, conferma, riapertura, pausa (US4, US5)

**Goal**: il countdown parte all'apertura del lotto e si resetta a ogni offerta. Alla
scadenza il buzzer si disattiva. Il banditore puo confermare, riaprire (da capo o
mantenendo) o annullare. Il lotto puo essere messo in pausa. Dopo un riavvio il server
ricostruisce lo stato.

### Backend

- [ ] T030 [P] [US4] Creare i tipi evento LottoScaduto (idLotto), LottoAggiudicato (idLotto, idCalciatore, codicePartecipante, importo), LottoRiaperto (idLotto, modalita DA_CAPO o MANTENENDO), registrarli nel deserializzatore polimorfico in src/main/java/fantasta/eventi/LottoScaduto.java, src/main/java/fantasta/eventi/LottoAggiudicato.java, src/main/java/fantasta/eventi/LottoRiaperto.java
- [ ] T031 [P] [US5] Creare i tipi evento LottoInPausa (idLotto, secondiResidui), LottoRipreso (idLotto), LottoAnnullato (idLotto), registrarli nel deserializzatore polimorfico in src/main/java/fantasta/eventi/LottoInPausa.java, src/main/java/fantasta/eventi/LottoRipreso.java, src/main/java/fantasta/eventi/LottoAnnullato.java
- [ ] T032 [US4] Aggiungere ad Asta la proiezione per LOTTO_SCADUTO (stato a SCADUTO), LOTTO_AGGIUDICATO (assegna calciatore a rosa, scala crediti, chiude lotto), LOTTO_RIAPERTO (stato a APERTO; se DA_CAPO azzera offerta e offerente) in src/main/java/fantasta/asta/Asta.java
- [ ] T033 [US5] Aggiungere ad Asta la proiezione per LOTTO_IN_PAUSA (stato a IN_PAUSA, salva secondiResidui), LOTTO_RIPRESO (stato a APERTO), LOTTO_ANNULLATO (rimuove lotto corrente, calciatore torna libero; valido da APERTO, IN_PAUSA e SCADUTO) in src/main/java/fantasta/asta/Asta.java
- [ ] T034 [US4] Implementare la gestione countdown in Asta: ScheduledExecutorService con singolo task, avvio timer alla creazione del lotto (LOTTO_APERTO), reset timer a ogni OFFERTA_ACCETTATA, stop timer su IN_PAUSA, ripresa da secondiResidui su LOTTO_RIPRESO, alla scadenza scrive LOTTO_SCADUTO e broadcast. Il callback del timer acquisisce lo stesso lock synchronized di Asta (invoca un metodo synchronized). Il timer NON si avvia durante il replay del log all'avvio in src/main/java/fantasta/asta/Asta.java
- [ ] T035 [US4] Aggiungere a ConsoleController gli endpoint POST /api/console/conferma (nessun body, scrive LOTTO_AGGIUDICATO, 409 se lotto non SCADUTO o nessuna offerta) e POST /api/console/riapri (body: modalita, scrive LOTTO_RIAPERTO, 409 se non SCADUTO) in src/main/java/fantasta/web/ConsoleController.java
- [ ] T036 [US5] Aggiungere a ConsoleController gli endpoint POST /api/console/pausa (scrive LOTTO_IN_PAUSA con secondiResidui correnti, 409 se non APERTO), POST /api/console/riprendi (scrive LOTTO_RIPRESO, 409 se non IN_PAUSA), POST /api/console/annulla-lotto (scrive LOTTO_ANNULLATO, valido da APERTO, IN_PAUSA e SCADUTO; 409 se nessun lotto in corso) in src/main/java/fantasta/web/ConsoleController.java
- [ ] T037 [US4] Implementare la ricostruzione stato all'avvio: cercare un file JSONL nella directory dati. Se non esiste, partire senza asta. Leggere la prima riga (ASTA_CREATA), ottenere fileListone, caricare l'xlsx corrispondente con ImportListone estraendo solo il catalogo calciatori (ignorare FantaSquadra e Costo nella rilettura). Se il file xlsx indicato non esiste, fallire l'avvio con messaggio in italiano che indica quale file manca. Eseguire il replay del log JSONL. Dopo il replay, se il lotto corrente e in stato APERTO scrivere un evento LOTTO_IN_PAUSA sintetico con secondiResidui uguale a durataCountdown (scelta esplicita: il tempo trascorso prima del crash non e recuperabile, si riparte dal tempo pieno); se e in stato IN_PAUSA non fare nulla. Avviare il server solo dopo la ricostruzione in src/main/java/fantasta/asta/Asta.java

### Console banditore

- [ ] T038 [US4] Aggiungere alla console la visualizzazione countdown (secondi residui, animazione locale dal valore nello snapshot) e lo stato del lotto (APERTO, SCADUTO, IN_PAUSA, AGGIUDICATO) in console/src/app/console/lotto-corrente/
- [ ] T039 [US4] Aggiungere alla console i controlli post-scadenza: pulsante conferma aggiudicazione, pulsante riapri da capo, pulsante riapri mantenendo, pulsante annulla lotto. Visibili solo quando lotto e SCADUTO in console/src/app/console/lotto-corrente/
- [ ] T040 [US5] Aggiungere alla console i controlli pausa: pulsante pausa (visibile se APERTO), pulsante riprendi (visibile se IN_PAUSA), pulsante annulla lotto (visibile se APERTO o IN_PAUSA). Il pulsante annulla e presente anche in SCADUTO (vedi T039) in console/src/app/console/lotto-corrente/

### Telefono

- [ ] T041 [US4] Aggiungere al telefono l'animazione countdown: timer locale che parte da secondiResidui e si aggiorna ogni secondo, reset a ogni nuovo snapshot con offerta diversa. Mostrare i secondi residui in modo visibile in src/main/resources/static/telefono/app.js
- [ ] T042 [US4] [US5] Implementare gli stati visivi del telefono: stato attesa (nessun lotto, mostra "nessun lotto in corso"), stato APERTO (buzzer attivo, countdown visibile), stato SCADUTO (buzzer disattivato, mostra "in attesa del banditore" con offerta vincente e nome), stato IN_PAUSA (buzzer disattivato, mostra "lotto in pausa"), stato AGGIUDICATO (mostra chi ha vinto e a quanto, poi torna ad attesa) in src/main/resources/static/telefono/app.js

---

**Verifica manuale Gruppo 3**:

1. Aprire un lotto, fare un'offerta, attendere la scadenza del countdown
2. Verificare che i telefoni mostrino "in attesa del banditore" con buzzer disattivato
3. Dalla console: confermare. Verificare che il calciatore appaia nella rosa del vincitore nel file JSONL
4. Aprire un nuovo lotto, fare un'offerta, attendere scadenza, riapri da capo: le offerte si azzerano, il countdown riparte, il buzzer si riattiva
5. Fare un'offerta, attendere scadenza, riapri mantenendo: l'offerta resta, il countdown riparte
6. Aprire un lotto, metterlo in pausa: il countdown si ferma, il telefono mostra "lotto in pausa", un'offerta dal telefono viene rifiutata con "lotto in pausa"
7. Riprendere: il countdown riparte, il buzzer si riattiva
8. Aprire un lotto, annullare: il calciatore torna libero (cercabile di nuovo)
9. Aprire un lotto, fare un'offerta, attendere la scadenza, annullare da SCADUTO: il calciatore torna libero senza dover riaprire prima
10. Aprire un lotto, fare un'offerta, terminare il server (Ctrl+C), riavviare: lo stato viene ricostruito, il lotto riparte in pausa con il tempo pieno (non il tempo residuo al crash)
11. Il countdown a zero senza offerte: il calciatore torna libero

---

## Phase 5: Gruppo 4 — Proiezioni crediti e rose (US6)

**Goal**: la console mostra la tabella di tutti i partecipanti con crediti e rose per
ruolo. Il telefono mostra i crediti e la rosa del partecipante con i nomi dei calciatori.

### Console banditore

- [ ] T043 [US6] Creare nella console la tabella partecipanti: per ogni partecipante mostrare nome, crediti residui, rosa raggruppata per ruolo (P, D, C, A) con nomi dei calciatori (da listone). Mostrare il totale crediti in circolazione (somma crediti di tutti i partecipanti). Aggiornamento in tempo reale dallo snapshot SSE in console/src/app/console/tabella-partecipanti/

### Telefono

- [ ] T044 [US6] Aggiungere al telefono la sezione crediti e rosa: mostrare i crediti residui del partecipante corrente (da snapshot, filtrato per codice), la rosa raggruppata per ruolo con i nomi dei calciatori (lookup dal listone locale). Aggiornamento automatico a ogni snapshot in src/main/resources/static/telefono/app.js

---

**Verifica manuale Gruppo 4**:

1. Con almeno un calciatore aggiudicato, verificare sulla console la tabella: il vincitore ha crediti diminuiti e il calciatore nella rosa sotto il ruolo corretto
2. Sul telefono del vincitore: crediti aggiornati e calciatore nella rosa con nome
3. Sulla console: il totale crediti in circolazione corrisponde alla somma dei crediti di tutti i partecipanti
4. Aggiudicare un secondo calciatore e verificare che tutto si aggiorni in tempo reale

---

## Phase 6: Gruppo 5 — Ricerca calciatori liberi dal telefono (US7)

**Goal**: il partecipante cerca tra i calciatori ancora liberi dal telefono, filtrando
per nome e ruolo. Assegnati e fuori lista sono esclusi.

### Telefono

- [ ] T045 [US7] Aggiungere al telefono la funzione ricerca calciatori liberi: campo di testo per nome (ricerca parziale case-insensitive), filtro per ruolo (P/D/C/A/tutti), lista risultati dal listone locale. Escludere i calciatori presenti in calciatoriAssegnati dello snapshot e quelli con fuoriLista=true. Mostrare id, nome, ruolo, squadra, quotazione in src/main/resources/static/telefono/app.js e src/main/resources/static/telefono/index.html

---

**Verifica manuale Gruppo 5**:

1. Dal telefono, cercare un calciatore per nome: appare solo se libero
2. Filtrare per ruolo A: appaiono solo attaccanti liberi
3. Cercare un calciatore gia aggiudicato: non appare nei risultati
4. Cercare un calciatore fuori lista: non appare nei risultati

---

## Phase 7: Rifiniture

**Scopo**: gestione edge case e robustezza della connessione.

- [ ] T046 Implementare auto-reconnect SSE sul telefono: se la connessione SSE si chiude, EventSource riconnette automaticamente (comportamento nativo), ma al riconnect il server deve inviare lo snapshot iniziale (gia implementato in T016). Verificare che il telefono gestisca la chiusura e riapertura senza intervento in src/main/resources/static/telefono/app.js
- [ ] T047 Gestire sul telefono l'offerta su lotto cambiato: se il partecipante ha preparato un'offerta ma nel frattempo il lotto e cambiato (idLotto diverso nello snapshot), il buzzer si resetta. L'offerta inviata con idLotto vecchio viene comunque rifiutata dal server (409, "lotto non corrispondente") in src/main/resources/static/telefono/app.js
- [ ] T048 Implementare sul telefono il monitoraggio heartbeat: registrarsi su addEventListener('heartbeat', ...) e su onmessage (snapshot). Avviare un timer di 20 secondi che si resetta a ogni heartbeat e a ogni snapshot ricevuto. Se il timer scade, mostrare un indicatore visibile di connessione persa (es. barra rossa "connessione persa"). Gestire anche onerror come fallback (mostra indicatore immediatamente). Alla ricezione del primo snapshot dopo la riconnessione (onopen + snapshot iniziale), nascondere l'indicatore. Il timer sull'assenza di messaggi e il rilevamento primario; onerror e il fallback per disconnessioni improvvise in src/main/resources/static/telefono/app.js
- [ ] T049 Aggiungere alla console l'indicatore di connessione persa: il servizio SSE (T018) espone gia connessionePersa come Observable. Mostrare un banner visibile (es. barra rossa "connessione al server persa") quando connessionePersa e true. Nasconderlo alla riconnessione. Stesso meccanismo del telefono: timer 20s su heartbeat+snapshot, onerror come fallback in console/src/app/console/

---

**Verifica manuale Rifiniture**:

1. Chiudere la connessione di rete del telefono per 5 secondi, riaprirla: il telefono si riallinea da solo con lo stato corrente
2. Aprire un lotto, preparare un'offerta dal telefono, nel frattempo annullare il lotto dalla console: l'offerta viene rifiutata o il buzzer si resetta
3. Spegnere lo schermo del telefono per 30 secondi: al riaccendersi l'indicatore di connessione persa deve essere visibile. Attendere la riconnessione: l'indicatore scompare e lo stato si riallinea
4. Con il server in funzione e un telefono connesso, fermare il server: entro 20 secondi il telefono mostra l'indicatore di connessione persa
5. Con il server in funzione e la console aperta, fermare il server: entro 20 secondi la console mostra l'indicatore di connessione persa. Riavviare il server: l'indicatore scompare alla riconnessione

---

## Dipendenze e ordine di esecuzione

### Dipendenze tra fasi

- **Phase 1 (Scaffold)**: nessuna dipendenza, si inizia subito
- **Phase 2 (Gruppo 1)**: dipende dal completamento di Phase 1
- **Phase 3 (Gruppo 2)**: dipende dal completamento di Phase 2
- **Phase 4 (Gruppo 3)**: dipende dal completamento di Phase 3
- **Phase 5 (Gruppo 4)**: dipende dal completamento di Phase 4
- **Phase 6 (Gruppo 5)**: dipende dal completamento di Phase 5
- **Phase 7 (Rifiniture)**: dipende dal completamento di Phase 6

**Ordine strettamente sequenziale** per sezione A della costituzione: ogni gruppo si
inizia solo quando il precedente funziona end-to-end.

### Parallelismo all'interno di ogni fase

- **Phase 1**: T002, T003, T004, T005 sono tutti [P] dopo T001
- **Phase 2**: T006, T007, T011 sono [P] tra loro; T015 e [P] rispetto ad altri task backend
- **Phase 3**: T022 e [P] rispetto ad altri task nello stesso gruppo
- **Phase 4**: T030 e T031 sono [P] tra loro

### Task che dipendono dal formato xlsx

| Task | File | Cosa serve sapere dal xlsx |
|------|------|---------------------------|
| T010 | ImportListone.java | Nomi esatti delle 14 colonne, formato valori (# numerico, R. come P/D/C/A, Fuori lista come *, QUOT. numerico, FantaSquadra e Costo per riparazione) |
| T014 | ConsoleController.java | analizza-listone riceve il file xlsx via multipart e lo salva; crea-asta usa il file salvato. Serve sapere cosa ImportListone estrae per costruire la risposta di analisi e calcolare i crediti totali |
| T019 | crea-asta/ (Angular) | UX del form: sapere che il tipo asta si determina automaticamente dal file, e che per riparazione i partecipanti vengono dal file ma i crediti li inserisce il banditore |

---

## Esempio di parallelismo: Phase 2

```
T006 (enum)  ──┐
T007 (Calciatore)──┤──► T008 (Evento) ──► T009 (LogEventi)
T011 (Partecipante)┘                           │
                                               ▼
T010 (ImportListone) ──► T012 (Asta) ──► T013 (Snapshot)
                                               │
                                               ▼
                     T014 (ConsoleController) ──► T016 (SseController)
                     T015 (ListoneController) [P]     │
                                                      ▼
                                               T017 (QrCode)
                                                      │
                                      ┌───────────────┼───────────────┐
                                      ▼               ▼               ▼
                               T018 (SSE svc)  T020 (QR view)  T021 (telefono)
                                      │
                                      ▼
                               T019 (crea-asta view)
```

---

## Strategia di implementazione

### MVP (Gruppo 1 + Gruppo 2)

1. Completare Phase 1: Scaffold
2. Completare Phase 2: Import e creazione asta
3. Completare Phase 3: Apertura lotto e buzzer
4. **STOP e VERIFICA**: l'asta funziona end-to-end per import, apertura, offerte

### Serata operativa (Gruppo 1-3)

5. Completare Phase 4: Countdown, conferma, pausa
6. **STOP e VERIFICA**: una serata completa e possibile (senza visualizzazione rose)

### Serata completa (Gruppi 1-5)

7. Completare Phase 5: Proiezioni crediti e rose
8. Completare Phase 6: Ricerca dal telefono
9. Completare Phase 7: Rifiniture

Come da costituzione: "Se il tempo finisce, finisce dal fondo."

---

## Note

- [P] = file diversi, nessuna dipendenza da task incompleti
- [Story] mappa il task alla user story di spec.md
- `XLSX` = serve il file xlsx reale per implementare e verificare
- Ogni fase lascia il progetto compilabile e avviabile
- Commit dopo ogni task o gruppo logico di task
