# Data Model — Asta Funzionante

**Date**: 2026-08-31 | **Spec**: [spec.md](spec.md) | **Research**: [research.md](research.md)

## Entità di dominio

### Calciatore (record immutabile, dal listone)

| Campo        | Tipo    | Origine xlsx | Note                                     |
|--------------|---------|--------------|------------------------------------------|
| id           | int     | `#`          | Identità univoca del calciatore          |
| nome         | String  | `Nome`       |                                          |
| ruolo        | Ruolo   | `R.`         | Enum: P, D, C, A                         |
| squadra      | String  | `Sq.`       |                                          |
| quotazione   | int     | `QUOT.`      |                                          |
| fuoriLista   | boolean | `Fuori lista`| true se il valore è `*`                  |

Colonne xlsx ignorate: `Under`, `R.MANTRA`, `PGv`, `MV`, `FM`, `FVM/1000`.
Colonne xlsx usate solo all'import per riparazione: `FantaSquadra`, `Costo`.

### Partecipante (stato mutevole, proiettato dal log)

| Campo    | Tipo              | Note                                       |
|----------|-------------------|--------------------------------------------|
| nome     | String            | Inserito dal banditore o da FantaSquadra    |
| codice   | String            | 4 caratteri alfanumerici, univoco nell'asta |
| crediti  | int               | Proiettato: iniziali − somma aggiudicazioni |
| rosa     | Map<Ruolo, List<Integer>> | Proiettato: id calciatori per ruolo  |

### Lotto (stato mutevole, al più uno attivo)

| Campo              | Tipo       | Note                                      |
|--------------------|------------|-------------------------------------------|
| idLotto            | int        | Sequenziale, generato dal server          |
| idCalciatore       | int        | Riferimento a Calciatore.id               |
| stato              | StatoLotto | APERTO, IN_PAUSA, SCADUTO, AGGIUDICATO   |
| offertaCorrente    | Integer    | null se nessuna offerta                   |
| offerenteCorrente  | String     | codice del partecipante, null se nessuna  |
| secondiResidui     | int        | Calcolato in tempo reale dal timer del server a ogni generazione di snapshot |

### Asta (stato globale in memoria)

| Campo              | Tipo                     | Note                                  |
|--------------------|--------------------------|---------------------------------------|
| nomeAsta           | String                   | Nome della serata                     |
| tipoAsta           | TipoAsta                 | INIZIALE, RIPARAZIONE                 |
| durataCountdown    | int                      | Secondi, configurato alla creazione   |
| banditorePartecipa | boolean                  | Informativo: registrato nel log, non influenza il comportamento del server. Il banditore che partecipa usa il proprio telefono come gli altri |
| partecipanti       | Map<String, Partecipante>| Chiave = codice                       |
| calciatoriAssegnati| Set<Integer>             | id dei calciatori già aggiudicati     |
| lottoCorrente      | Lotto                    | null se nessun lotto attivo           |
| prossimoIdLotto    | int                      | Contatore per generare idLotto        |

### Enum

- **Ruolo**: `P`, `D`, `C`, `A`
- **StatoLotto**: `APERTO`, `IN_PAUSA`, `SCADUTO`, `AGGIUDICATO`
- **TipoAsta**: `INIZIALE`, `RIPARAZIONE`

## Macchina a stati del Lotto

```
                    ┌──────────────────────┐
                    │                      │
         ┌─────────▼──────────┐            │
         │      APERTO        │◄───────────┤ LOTTO_RIAPERTO
         │                    │            │ (da SCADUTO)
         └──┬─────────┬───────┘            │
            │         │                    │
   LOTTO_   │         │ LOTTO_             │
   IN_PAUSA │         │ SCADUTO            │
            │         │                    │
   ┌────────▼───┐  ┌──▼───────────┐        │
   │  IN_PAUSA  │  │   SCADUTO    ├────────┘
   │            │  │              │
   └────────┬───┘  └──┬──────┬───┘
            │         │      │
   LOTTO_   │         │      │ LOTTO_ANNULLATO
   RIPRESO  │         │      │ (da SCADUTO)
            │         │      │
            │    ┌────▼──┐   │
            │    │AGGIUD.│   │
            │    └───────┘   │
            │                │
            └───► (torna ad APERTO)
                             │
             LOTTO_ANNULLATO ▼
          (lotto rimosso, calciatore libero)
```

Transizioni con LOTTO_ANNULLATO (non tutte nel diagramma):
- Da APERTO: LOTTO_ANNULLATO → lotto rimosso, calciatore torna libero.
- Da IN_PAUSA: LOTTO_ANNULLATO → lotto rimosso, calciatore torna libero.
- Da SCADUTO: LOTTO_ANNULLATO → lotto rimosso, calciatore torna libero. Il banditore
  può annullare un lotto scaduto senza doverlo prima riaprire.

## Tipi di evento (log JSONL)

Tutti gli eventi condividono tre campi:

| Campo    | Tipo   | Note                                       |
|----------|--------|--------------------------------------------|
| tipo     | String | Discriminante del tipo di evento           |
| istante  | String | ISO 8601 con millisecondi (server clock)   |
| sequenza | long   | Monotonica crescente, 1-based, per asta    |

### ASTA_CREATA

Primo evento del log. Registra la creazione dell'asta con partecipanti e configurazione.

| Campo              | Tipo                                      |
|--------------------|-------------------------------------------|
| tipo               | `"ASTA_CREATA"`                           |
| nomeAsta           | String                                    |
| tipoAsta           | `"INIZIALE"` \| `"RIPARAZIONE"`          |
| durataCountdown    | int (secondi)                             |
| banditorePartecipa | boolean                                   |
| fileListone        | String                                    |
| partecipanti       | `[{nome, codice, crediti}]`               |

`fileListone` è il nome del file xlsx salvato nella directory dati dall'endpoint
analizza-listone. Il server lo registra nell'evento per sapere quale file ricaricare
all'avvio (vedi "Ricostruzione stato all'avvio").

`banditorePartecipa` è informativo: registrato nel log per leggibilità, non influenza
il comportamento del server. Il banditore che partecipa usa il proprio telefono con il
proprio codice, come qualsiasi altro partecipante.

Per l'asta di riparazione, `crediti` in ASTA_CREATA contiene i crediti **totali** di
ciascun partecipante: crediti residui inseriti dal banditore + somma dei costi dei
calciatori pre-assegnati (FantaSquadra/Costo). Il server calcola questo totale
all'import. La proiezione ricalcola i crediti residui in modo uniforme:
crediti totali − somma di tutte le deduzioni (ASSEGNAZIONE_INIZIALE + LOTTO_AGGIUDICATO).

### ASSEGNAZIONE_INIZIALE

Solo per asta di riparazione. Un evento per ogni calciatore pre-assegnato da FantaSquadra/Costo.

| Campo              | Tipo                                      |
|--------------------|-------------------------------------------|
| tipo               | `"ASSEGNAZIONE_INIZIALE"`                 |
| idCalciatore       | int                                       |
| codicePartecipante | String                                    |
| costo              | int                                       |

### LOTTO_APERTO

Il banditore apre un lotto per un calciatore libero.

| Campo        | Tipo                                           |
|--------------|------------------------------------------------|
| tipo         | `"LOTTO_APERTO"`                               |
| idLotto      | int                                            |
| idCalciatore | int                                            |

### OFFERTA_ACCETTATA

Un'offerta valida è stata accettata dal server. Il countdown riparte.

| Campo              | Tipo                                      |
|--------------------|-------------------------------------------|
| tipo               | `"OFFERTA_ACCETTATA"`                     |
| idLotto            | int                                       |
| codicePartecipante | String                                    |
| importo            | int                                       |

### LOTTO_IN_PAUSA

Il banditore mette in pausa il lotto. Registra i secondi residui per ripristino.

| Campo          | Tipo                                          |
|----------------|-----------------------------------------------|
| tipo           | `"LOTTO_IN_PAUSA"`                            |
| idLotto        | int                                           |
| secondiResidui | int                                           |

### LOTTO_RIPRESO

Il banditore riprende il lotto dalla pausa. Il countdown riparte dai secondi salvati.

| Campo   | Tipo                                              |
|---------|---------------------------------------------------|
| tipo    | `"LOTTO_RIPRESO"`                                 |
| idLotto | int                                               |

### LOTTO_SCADUTO

Il countdown del server è arrivato a zero.

| Campo   | Tipo                                              |
|---------|---------------------------------------------------|
| tipo    | `"LOTTO_SCADUTO"`                                 |
| idLotto | int                                               |

### LOTTO_AGGIUDICATO

Il banditore conferma l'aggiudicazione. Campi denormalizzati per leggibilità del log.

| Campo              | Tipo                                      |
|--------------------|-------------------------------------------|
| tipo               | `"LOTTO_AGGIUDICATO"`                     |
| idLotto            | int                                       |
| idCalciatore       | int                                       |
| codicePartecipante | String                                    |
| importo            | int                                       |

### LOTTO_RIAPERTO

Il banditore riapre il lotto dalla scadenza.

| Campo    | Tipo                                             |
|----------|--------------------------------------------------|
| tipo     | `"LOTTO_RIAPERTO"`                               |
| idLotto  | int                                              |
| modalita | `"DA_CAPO"` \| `"MANTENENDO"`                   |

### LOTTO_ANNULLATO

Il banditore annulla il lotto. Il calciatore torna libero.

| Campo   | Tipo                                              |
|---------|---------------------------------------------------|
| tipo    | `"LOTTO_ANNULLATO"`                               |
| idLotto | int                                               |

## Estensibilità per feature future

Gli eventi sopra non cambiano. Le feature future (out of scope per ora) saranno nuovi
tipi di evento appendibili:

- **Annullamento aggiudicazione**: `AGGIUDICAZIONE_ANNULLATA` con `idLotto` — la
  proiezione rimuove il calciatore dalla rosa e restituisce i crediti.
- **Rettifica prezzo**: `PREZZO_RETTIFICATO` con `idLotto`, `nuovoImporto` — la
  proiezione ricalcola i crediti.
- **Rettifica assegnatario**: `ASSEGNATARIO_RETTIFICATO` con `idLotto`,
  `nuovoCodicePartecipante` — la proiezione sposta il calciatore.
- **Rimozione manuale da rosa**: `CALCIATORE_RIMOSSO` con `idCalciatore`,
  `codicePartecipante`.
- **Aggiunta manuale a rosa**: `CALCIATORE_AGGIUNTO` con `idCalciatore`,
  `codicePartecipante`, `costo`.

Nessuno di questi eventi richiede modifiche agli eventi già definiti. La proiezione
aggiunge un ramo per ogni nuovo tipo.

## Ricostruzione stato all'avvio

1. Cercare nella directory dati un file JSONL. Se non esiste, il server parte senza
   asta (stato "attesa creazione").
2. Leggere la prima riga del JSONL (evento ASTA_CREATA) e ottenere il campo
   `fileListone`. Caricare l'xlsx corrispondente dalla stessa directory dati con
   ImportListone, estraendo **solo il catalogo calciatori** (id, nome, ruolo, squadra,
   quotazione, fuoriLista). Le colonne FantaSquadra e Costo vengono **ignorate** nella
   rilettura: servono solo alla creazione dell'asta per generare gli eventi
   ASSEGNAZIONE_INIZIALE. Senza questa regola, dopo un crash in riparazione ogni
   calciatore verrebbe assegnato due volte e i crediti scalati due volte.
3. Se il file xlsx indicato da `fileListone` non esiste o non è leggibile, il server
   **fallisce l'avvio** con un messaggio in italiano che indica quale file manca
   (es. "File listone non trovato: listone-2026-09-01.xlsx"). Non parte mai con un
   listone vuoto o parziale.
4. Leggere il JSONL riga per riga, parsare JSON, applicare in ordine:

| Evento                 | Effetto sulla proiezione                                    |
|------------------------|-------------------------------------------------------------|
| ASTA_CREATA            | Inizializza partecipanti, config asta                       |
| ASSEGNAZIONE_INIZIALE  | Aggiunge calciatore a rosa, scala crediti, marca assegnato  |
| LOTTO_APERTO           | Crea lotto corrente in stato APERTO                         |
| OFFERTA_ACCETTATA      | Aggiorna offerta/offerente sul lotto corrente               |
| LOTTO_IN_PAUSA         | Stato → IN_PAUSA, salva secondiResidui                      |
| LOTTO_RIPRESO          | Stato → APERTO                                              |
| LOTTO_SCADUTO          | Stato → SCADUTO                                             |
| LOTTO_AGGIUDICATO      | Assegna calciatore, scala crediti, chiude lotto             |
| LOTTO_RIAPERTO         | Stato → APERTO; se DA_CAPO: azzera offerta                  |
| LOTTO_ANNULLATO        | Rimuove lotto corrente                                      |

5. Dopo il replay, se il lotto corrente è in stato APERTO:
   - Scrivere un evento LOTTO_IN_PAUSA con `secondiResidui = durataCountdown`.
   - **Scelta esplicita**: il tempo trascorso prima del crash non è recuperabile dal
     log (il log registra solo i secondiResidui al momento di una pausa). Si riparte
     dal tempo pieno; il banditore decide se riprendere o annullare.
6. Se il lotto corrente è in stato IN_PAUSA: resta in pausa, nessun evento aggiuntivo.
7. Avviare il server, iniziare ad accettare connessioni SSE.
