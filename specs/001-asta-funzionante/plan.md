# Implementation Plan: Asta Funzionante

**Branch**: `001-asta-funzionante` | **Date**: 2026-08-31 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-asta-funzionante/spec.md`

## Summary

Applicazione Spring Boot singola (un jar) che gestisce una serata d'asta del fantacalcio
in LAN. Il banditore conduce dal PC (console Angular + PrimeNG), i partecipanti
rilanciano dal telefono (HTML/JS statico). Lo stato è un log append-only di eventi su
file JSONL; rose, crediti e conteggi sono proiezioni ricalcolate. Il trasporto è SSE
(server → client) + POST REST (client → server).

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.x

**Primary Dependencies**:
- `spring-boot-starter-web` (Tomcat embedded, Jackson, SSE via SseEmitter)
- `poi-ooxml` (Apache POI per lettura xlsx)
- `zxing core` + `zxing javase` (generazione QR code PNG)

**Storage**: File JSONL append-only, un file per asta. Nessun database.

**Testing**: Nessuno (divieto da costituzione VII.6).

**Target Platform**: Windows (portatile del banditore), JRE 21, rete LAN/hotspot.

**Project Type**: Web application (server + due frontend in un unico jar).

**Performance Goals**: ≤ 1s per propagare un'offerta a tutti i dispositivi (SC-003). Import xlsx < 5s per 600 calciatori (SC-005).

**Constraints**: Nessun accesso a internet. Max 10 dispositivi (1 PC + 9 telefoni). Un solo lotto alla volta.

**Scale/Scope**: 8 partecipanti, ~560 calciatori, ~50-100 lotti per serata.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principio                                              | Conforme | Note                                                      |
|---|--------------------------------------------------------|----------|-----------------------------------------------------------|
| I | Lessico in italiano, 4 termini obbligatori             | ✓        | Classi, campi, eventi, endpoint tutti in italiano          |
| I | Macchina a stati esplicita del lotto                   | ✓        | APERTO → SCADUTO → AGGIUDICATO + IN_PAUSA, in data-model  |
| II| Log append-only JSONL, un file per asta                | ✓        | 10 tipi di evento, proiezioni ricalcolate                  |
| II| Ogni evento forzato su disco prima della risposta      | ✓        | fsync prima di rispondere al client                        |
| II| Correzioni in avanti, mai riscrivere il passato        | ✓        | Eventi futuri appendibili senza modificare i presenti      |
| III| Server unica autorità, nessuna logica nel client      | ✓        | Offerte validate solo dal server, countdown autorevole     |
| III| Offerte importi assoluti con id lotto                 | ✓        | POST /api/offerta con idLotto e importo assoluto           |
| III| Tre regole: supera offerta, non eccede crediti, lotto APERTO | ✓ | Validazione in asta/ package                          |
| IV | SSE server→client, POST client→server, nessun altro  | ✓        | Nessun WebSocket, nessun polling                           |
| IV | Snapshot completo mai diff                            | ✓        | Ogni evento produce snapshot completo via SSE               |
| IV | Listone risorsa statica, GET una volta                | ✓        | GET /api/listone, non nello snapshot                        |
| IV | Codice identifica persona non sessione                | ✓        | Più dispositivi ammessi con stesso codice                   |
| IV | Nessuna password, ruolo, permesso                     | ✓        | Nessuna autenticazione                                      |
| V  | Un solo modulo Spring Boot, un jar                    | ✓        | Nessun microservizio, nessun modulo aggiuntivo              |
| V  | Package per contesto di dominio                       | ✓        | asta/, listone/, eventi/, web/                              |
| V  | Nessuna astrazione senza due usi reali                | ✓        | Nessuna interfaccia a implementazione unica                 |
| V  | Console Angular+PrimeNG, telefono HTML/JS statico     | ✓        | Due frontend, nessun framework aggiuntivo                   |
| V  | JSONL + xlsx uniche dipendenze da disco               | ✓        | Nessun database                                             |
| VI | Log in italiano con contesto                          | ✓        | Ogni riga dice cosa, su quale lotto/partecipante, perché    |
| VI | Rifiuto visibile con motivo                           | ✓        | Body 400 con campo motivo                                   |
| VI | Import fallisce rumorosamente                         | ✓        | Errore con riga/colonna, nessun import parziale             |
| VI | Riavvio: stato dal log, lotto in IN_PAUSA             | ✓        | Ricostruzione + evento LOTTO_IN_PAUSA sintetico             |
| VI | Disconnessione: riallineamento via snapshot           | ✓        | Snapshot immediato alla connessione SSE                     |
| VII| Nessuna chiamata internet                             | ✓        | Tutto in LAN                                                |
| VII| Nessuna autenticazione                                | ✓        |                                                              |
| VII| Stato solo nel log                                    | ✓        | In memoria è proiezione, persistito è JSONL                  |
| VII| Nessun WebSocket o polling                            | ✓        | Solo SSE + POST                                              |
| VII| Nessuna logica di dominio nel client                  | ✓        |                                                              |
| VII| Nessun test, Docker, CI, profili, ambienti            | ✓        |                                                              |
| VII| Nessuna schermata/entità non necessaria               | ✓        |                                                              |

**Risultato gate**: tutti i principi conformi. Nessuna violazione da giustificare.

## Project Structure

### Documentation (this feature)

```text
specs/001-asta-funzionante/
├── plan.md              # Questo file
├── spec.md              # Specifica della feature
├── research.md          # Ricerca dipendenze e ambiguità
├── data-model.md        # Entità, eventi, macchina a stati, ricostruzione
├── quickstart.md        # Guida di validazione end-to-end
├── contracts/
│   └── endpoints.md     # Endpoint REST, SSE, snapshot
└── checklists/
    └── requirements.md  # Checklist qualità spec
```

### Source Code (repository root)

```text
pom.xml

src/main/java/fantasta/
├── asta/
│   ├── Asta.java                    # Stato in memoria, logica di proiezione
│   ├── Partecipante.java            # Record: nome, codice, crediti, rosa
│   ├── Lotto.java                   # Stato del lotto corrente
│   ├── StatoLotto.java              # Enum: APERTO, IN_PAUSA, SCADUTO, AGGIUDICATO
│   ├── TipoAsta.java                # Enum: INIZIALE, RIPARAZIONE
│   └── Ruolo.java                   # Enum: P, D, C, A
├── listone/
│   ├── Calciatore.java              # Record: id, nome, ruolo, squadra, quotazione, fuoriLista
│   └── ImportListone.java           # Lettura xlsx con Apache POI, validazione
├── eventi/
│   ├── Evento.java                  # Classe base: tipo, istante, sequenza
│   ├── AstaCreata.java
│   ├── AssegnazioneIniziale.java
│   ├── LottoAperto.java
│   ├── OffertaAccettata.java
│   ├── LottoInPausa.java
│   ├── LottoRipreso.java
│   ├── LottoScaduto.java
│   ├── LottoAggiudicato.java
│   ├── LottoRiaperto.java
│   ├── LottoAnnullato.java
│   └── LogEventi.java               # Append JSONL su file, lettura all'avvio, fsync
└── web/
    ├── ConsoleController.java        # POST /api/console/*
    ├── OffertaController.java        # POST /api/offerta
    ├── SseController.java            # GET /api/sse, gestione SseEmitter
    ├── ListoneController.java        # GET /api/listone
    ├── QrCodeController.java         # GET /api/qrcode/{codice}
    └── Snapshot.java                 # Record per serializzazione Jackson dello snapshot SSE

src/main/resources/
├── application.properties
└── static/
    └── telefono/
        ├── index.html
        ├── app.js
        └── style.css

console/                              # Progetto Angular (console banditore)
├── angular.json
├── package.json
└── src/
    └── app/
        ├── app.component.ts
        ├── console/                  # Componenti console
        └── services/
            └── sse.service.ts        # Connessione SSE
```

**Structure Decision**: un solo modulo Maven alla root con il backend Java. L'Angular
project `console/` è separato; la build copia l'output in
`src/main/resources/static/console/` prima del packaging Maven. Il telefono è HTML/JS
statico direttamente in `src/main/resources/static/telefono/`.

## 1. Tipi di evento del log (schema di fatto)

Vedi [data-model.md](data-model.md) sezione "Tipi di evento" per i campi completi.

Riepilogo:

| Tipo                     | Campi specifici                                     | Quando                              |
|--------------------------|-----------------------------------------------------|--------------------------------------|
| `ASTA_CREATA`            | nomeAsta, tipoAsta, durataCountdown, banditorePartecipa, fileListone, partecipanti[] | Creazione asta |
| `ASSEGNAZIONE_INIZIALE`  | idCalciatore, codicePartecipante, costo             | Solo riparazione, un evento per calciatore pre-assegnato |
| `LOTTO_APERTO`           | idLotto, idCalciatore                               | Banditore apre lotto                 |
| `OFFERTA_ACCETTATA`      | idLotto, codicePartecipante, importo                | Offerta valida accettata             |
| `LOTTO_IN_PAUSA`         | idLotto, secondiResidui                             | Banditore mette in pausa             |
| `LOTTO_RIPRESO`          | idLotto                                             | Banditore riprende                   |
| `LOTTO_SCADUTO`          | idLotto                                             | Countdown a zero                     |
| `LOTTO_AGGIUDICATO`      | idLotto, idCalciatore, codicePartecipante, importo  | Banditore conferma                   |
| `LOTTO_RIAPERTO`         | idLotto, modalita (DA_CAPO \| MANTENENDO)           | Banditore riapre da scaduto          |
| `LOTTO_ANNULLATO`        | idLotto                                             | Banditore annulla                    |

Tutti gli eventi portano anche `tipo`, `istante` (ISO 8601), `sequenza` (long, monotonica).

Progettati per estensibilità: annullamento aggiudicazione, rettifica prezzo/assegnatario,
rimozione/aggiunta manuale saranno nuovi tipi di evento appendibili senza modificare
quelli esistenti (dettagli in data-model.md sezione "Estensibilità").

## 2. Struttura dei package

```
fantasta/
├── asta/       # Stato dell'asta, Partecipante, Lotto, StatoLotto, Ruolo, TipoAsta
│               # Logica di dominio: validazione offerte, transizioni lotto, proiezione
├── listone/    # Calciatore (record), ImportListone (lettura xlsx, validazione formato)
├── eventi/     # Tipi di evento (10 classi), LogEventi (I/O file JSONL), Evento (base)
└── web/        # Controller REST/SSE, Snapshot (record per serializzazione)
```

Nessun package `config`, `util`, `mapper`, `repository`, `service`. La logica sta nelle
classi di dominio. I controller chiamano direttamente `Asta` e `LogEventi`.

## 3. Elenco endpoint

Vedi [contracts/endpoints.md](contracts/endpoints.md) per i dettagli completi (body, risposte, codici HTTP).

| Metodo | Path                         | Scopo                                    |
|--------|------------------------------|------------------------------------------|
| POST   | `/api/console/analizza-listone` | Upload e analisi xlsx, salvataggio su disco |
| POST   | `/api/console/crea-asta`     | Creazione asta (usa xlsx già salvato)    |
| POST   | `/api/console/apri-lotto`    | Apre lotto per un calciatore             |
| POST   | `/api/console/conferma`      | Conferma aggiudicazione                  |
| POST   | `/api/console/riapri`        | Riapre lotto (DA_CAPO o MANTENENDO)      |
| POST   | `/api/console/pausa`         | Mette in pausa                           |
| POST   | `/api/console/riprendi`      | Riprende dalla pausa                     |
| POST   | `/api/console/annulla-lotto` | Annulla lotto                            |
| POST   | `/api/offerta`               | Offerta dal partecipante                 |
| GET    | `/api/listone`               | Listone completo (statico)               |
| GET    | `/api/sse`                   | Stream SSE (snapshot completi)           |
| GET    | `/api/qrcode/{codice}`       | Immagine PNG del QR code                 |

## 4. Forma dello snapshot SSE

Vedi [contracts/endpoints.md](contracts/endpoints.md) sezione "Forma dello snapshot"
per la tabella campo per campo.

Riepilogo struttura:

```json
{
  "sequenza": 42,
  "lotto": {
    "idLotto": 3,
    "idCalciatore": 5585,
    "stato": "APERTO",
    "offertaCorrente": 15,
    "offerenteCorrente": "A3K7",
    "secondiResidui": 22
  },
  "partecipanti": [
    {
      "nome": "Marco",
      "codice": "A3K7",
      "crediti": 485,
      "rosa": {"P": [], "D": [254], "C": [], "A": [5585]}
    }
  ],
  "calciatoriAssegnati": [5585, 254]
}
```

- `lotto` è null se nessun lotto attivo.
- `secondiResidui` è calcolato in tempo reale dal timer del server al momento della generazione dello snapshot; il client lo usa come punto di partenza per un'animazione locale cosmetica.
- `calciatoriAssegnati` è la union di tutte le rose (ridondante, per filtro veloce sul telefono).

## 5. Ricostruzione stato dal log all'avvio

Vedi [data-model.md](data-model.md) sezione "Ricostruzione stato all'avvio".

Procedura:
1. Cercare nella directory dati un file JSONL. Se non esiste, partire senza asta.
2. Leggere la prima riga del JSONL (evento ASTA_CREATA), ottenere il campo
   `fileListone`. Caricare l'xlsx corrispondente dalla stessa directory con
   ImportListone, estraendo **solo il catalogo calciatori** (id, nome, ruolo, squadra,
   quotazione, fuoriLista). Le colonne FantaSquadra e Costo vengono ignorate nella
   rilettura (servono solo alla creazione per generare ASSEGNAZIONE_INIZIALE).
3. Se il file xlsx indicato non esiste, il server fallisce l'avvio con messaggio
   in italiano che indica quale file manca.
4. Leggere il JSONL riga per riga, parsare con Jackson, applicare in ordine alla
   proiezione. Ogni tipo di evento ha un effetto specifico (tabella in data-model.md).
5. Se il lotto corrente risulta APERTO dopo il replay (crash durante un lotto):
   - Scrivere un evento `LOTTO_IN_PAUSA` sintetico con `secondiResidui = durataCountdown`.
   - Scelta esplicita: il tempo trascorso prima del crash non è recuperabile dal
     log, si riparte dal tempo pieno. Il banditore decide se riprendere o annullare.
6. Se il lotto corrente è IN_PAUSA: resta in pausa senza eventi aggiuntivi.
7. Avviare il server e iniziare ad accettare connessioni SSE.

Nessuno dei due file viene mai riscritto. La ricostruzione è una lettura sequenziale
pura. L'xlsx viene salvato nella directory dati dall'endpoint analizza-listone; il suo
nome è registrato nel campo `fileListone` dell'evento ASTA_CREATA.

## Ambiguità segnalate

### 1. Offerta minima sul primo rilancio

La spec non dice se la prima offerta deve essere ≥ 1 o ≥ quotazione. La costituzione
vieta di imporre regolamento nel software. **Scelta proposta**: qualsiasi importo ≥ 1.
Vedi [research.md](research.md) per il ragionamento completo.

### 2. Formato del codice partecipante

Non specificato nella spec. **Scelta proposta**: 4 caratteri alfanumerici maiuscoli
generati dal server, comunicabili a voce. Vedi [research.md](research.md).

## Gestione del countdown (dettaglio implementativo)

Il countdown vive interamente sul server:

1. All'apertura del lotto, il server avvia un timer di `durataCountdown` secondi.
2. A ogni offerta accettata, il timer si resetta a `durataCountdown`.
3. In pausa, il timer si ferma; alla ripresa, riparte dal valore salvato.
4. Alla scadenza, il server scrive `LOTTO_SCADUTO` e invia lo snapshot.
5. Nello snapshot, `secondiResidui` è calcolato **in tempo reale** dal timer del
   server al momento della generazione dello snapshot, non dal valore dell'evento.
   Questo garantisce che un client appena connesso riceva i secondi residui correnti,
   non un valore stantio. Il client usa il valore come punto di partenza per
   un'animazione locale cosmetica.

Implementazione: `ScheduledExecutorService` con un singolo task schedulato per asta.
Ad ogni reset si cancella il task precedente e se ne schedula uno nuovo. Il metodo
`generaSnapshot()` di Asta interroga il timer per calcolare i secondi residui.

## Serializzazione dei comandi

Lettura dello stato, validazione dell'offerta, append+fsync sul log e aggiornamento
della proiezione devono avvenire dentro un **unico punto di serializzazione** per asta.
Senza serializzazione, due rilanci concorrenti possono essere entrambi validati contro
la stessa offertaCorrente e accettati entrambi.

Implementazione: tutti i metodi pubblici di `Asta` che mutano lo stato (accettazione
offerta, apertura/chiusura lotto, pausa, conferma) sono `synchronized`. Il callback
del timer di scadenza (`LOTTO_SCADUTO`) acquisisce lo stesso lock. I controller HTTP
chiamano `Asta` che serializza internamente; nessun lock nei controller.

La generazione dello snapshot (lettura pura) acquisisce lo stesso lock per garantire
una vista consistente. Con max 10 dispositivi il collo di bottiglia è irrilevante.

## Heartbeat SSE

Il server invia un **evento SSE con nome `heartbeat`** e payload vuoto
(`event: heartbeat\ndata: \n\n`) ogni 15 secondi a tutti i client connessi. È un
evento SSE reale, non un commento: arriva a `EventSource.addEventListener('heartbeat', ...)`
sui client. L'heartbeat non trasporta mai stato; lo snapshot resta l'unico messaggio
che porta dati. Senza heartbeat, i browser mobili a schermo spento chiudono la
connessione dopo 30-60 secondi di inattività.

Sia il telefono che la console monitorano la ricezione: resettano un timer di 20
secondi a ogni heartbeat e a ogni snapshot ricevuto. Se il timer scade, mostrano un
indicatore visibile di connessione persa. `EventSource.onerror` resta gestito ma non
è il meccanismo principale: su un telefono in standby o con wifi caduta in modo
sporco può non scattare per minuti. Il timer sull'assenza di messaggi è il
rilevamento primario. Alla riconnessione (automatica via EventSource), il server
invia lo snapshot iniziale e l'indicatore scompare.

Implementazione: un `ScheduledExecutorService` separato da quello del countdown invia
l'evento heartbeat a tutti gli `SseEmitter` registrati. Emitter che lanciano eccezione
vengono rimossi dalla lista.

## Dipendenze Maven

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.apache.poi</groupId>
        <artifactId>poi-ooxml</artifactId>
        <version>5.3.0</version>
    </dependency>
    <dependency>
        <groupId>com.google.zxing</groupId>
        <artifactId>core</artifactId>
        <version>3.5.3</version>
    </dependency>
    <dependency>
        <groupId>com.google.zxing</groupId>
        <artifactId>javase</artifactId>
        <version>3.5.3</version>
    </dependency>
</dependencies>
```

Nessun'altra dipendenza. Jackson è incluso in spring-boot-starter-web.

## Complexity Tracking

Nessuna violazione da giustificare.
