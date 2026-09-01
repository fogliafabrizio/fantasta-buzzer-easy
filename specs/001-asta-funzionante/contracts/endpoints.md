# Contratti endpoint — Asta Funzionante

**Date**: 2026-08-31 | **Data model**: [data-model.md](../data-model.md)

Tutti gli endpoint sono sotto `/api`. Il dominio è in italiano. I body sono JSON
(`application/json`) salvo dove indicato.

## Risorse statiche

### GET /api/listone

Restituisce il listone completo dei calciatori importati (inclusi fuori lista).
Risorsa statica: il contenuto non cambia dopo l'import. Il client la scarica una volta
e filtra localmente.

**Risposta** `200 OK`:

```json
[
  {
    "id": 5585,
    "nome": "Malen",
    "ruolo": "A",
    "squadra": "Roma",
    "quotazione": 36,
    "fuoriLista": false
  },
  {
    "id": 254,
    "nome": "Dimarco",
    "ruolo": "D",
    "squadra": "Inter",
    "quotazione": 31,
    "fuoriLista": false
  }
]
```

Se l'asta non è ancora creata: `404`.

### GET /api/qrcode/{codice}

Restituisce l'immagine PNG del QR code per il partecipante con il codice dato.
Il QR code contiene l'URL completo del telefono: `http://{ip}:{porta}/telefono/?codice={codice}`.

**Risposta** `200 OK` con `Content-Type: image/png`.

Se il codice non esiste: `404`.

## SSE

### GET /api/sse

Stream Server-Sent Events. Aperto dai telefoni e dalla console. Nessun parametro
obbligatorio — lo snapshot è lo stesso per tutti.

**Comportamento**:
1. Alla connessione, il server invia immediatamente lo snapshot corrente (evento `snapshot`).
2. A ogni cambiamento di stato (offerta accettata, lotto aperto, aggiudicazione, ecc.)
   il server invia un nuovo evento `snapshot` a tutti i client connessi.
3. Se l'asta non è ancora creata, il server invia un evento `attesa` e resta in ascolto
   fino alla creazione.
4. Il server invia un evento SSE con nome `heartbeat` e payload vuoto
   (`event: heartbeat\ndata: \n\n`) ogni 15 secondi a tutti i client connessi. Lo
   scopo è duplice: mantenere viva la connessione TCP e permettere ai client di
   rilevare la disconnessione. L'heartbeat non trasporta mai stato; lo snapshot resta
   l'unico messaggio che porta dati. I client si registrano su
   `addEventListener('heartbeat', ...)` e resettano un timer di 20 secondi a ogni
   heartbeat e a ogni snapshot ricevuto. Se il timer scade, mostrano un indicatore
   di connessione persa.

**Formato evento SSE**:

```
event: snapshot
data: { ... JSON snapshot ... }
```

### Forma dello snapshot

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
      "creditiTotali": 500,
      "crediti": 485,
      "rosa": {
        "P": [],
        "D": [{ "idCalciatore": 254, "prezzo": 3 }],
        "C": [],
        "A": [{ "idCalciatore": 5585, "prezzo": 12 }]
      }
    }
  ],
  "calciatoriAssegnati": [5585, 254],
  "creditiInCircolazione": 1000
}
```

**Campo per campo**:

| Campo                        | Tipo                 | Note                                                      |
|------------------------------|----------------------|-----------------------------------------------------------|
| `sequenza`                   | long                 | Numero dell'ultimo evento applicato                       |
| `lotto`                      | object \| null       | null se nessun lotto attivo                               |
| `lotto.idLotto`              | int                  | Identificativo del lotto                                  |
| `lotto.idCalciatore`         | int                  | Riferimento a Calciatore.id                               |
| `lotto.stato`                | String               | `APERTO` \| `IN_PAUSA` \| `SCADUTO` \| `AGGIUDICATO`    |
| `lotto.offertaCorrente`      | Integer \| null      | null se nessuna offerta                                   |
| `lotto.offerenteCorrente`    | String \| null       | codice partecipante, null se nessuna offerta              |
| `lotto.secondiResidui`       | int                  | Calcolato in tempo reale dal timer del server; 0 se SCADUTO |
| `partecipanti`               | array                | Tutti i partecipanti, sempre presenti                     |
| `partecipanti[].nome`        | String               |                                                           |
| `partecipanti[].codice`      | String               |                                                           |
| `partecipanti[].creditiTotali` | int                | Dotazione iniziale del partecipante                       |
| `partecipanti[].crediti`     | int                  | Crediti residui, proiettati: `creditiTotali` meno la somma dei `prezzo` in rosa |
| `partecipanti[].rosa`        | object               | Chiavi: `P`, `D`, `C`, `A`, sempre tutte e quattro; valori: array di `{ idCalciatore, prezzo }` |

**Nota su crediti e rosa**: sono proiezioni ricalcolate dal log a ogni snapshot, mai
campi persistiti. Ogni acquisto — `ASSEGNAZIONE_INIZIALE` per la riparazione,
`LOTTO_AGGIUDICATO` per l'asta — aggiunge una voce alla rosa con il prezzo pagato, e i
crediti residui seguono da un'unica formula valida per entrambi i tipi di asta. La rosa
porta gli id, non i nomi: quelli li risolvono i client dal listone che hanno gia' in
locale via `GET /api/listone`.

**Nota su `creditiInCircolazione`**: non e' la somma dei crediti residui ma dei crediti
residui piu' i prezzi gia' impegnati nelle rose. E' un invariante della lega: durante
un'asta normale non si muove mai, perche' un'aggiudicazione sposta crediti dal residuo
del vincitore alla sua rosa senza farne sparire. Serve come spia di un errore: se il
totale cambia, dei crediti sono stati creati o distrutti (per esempio da un importo
restituito a piacere rimuovendo un calciatore). Per questo lo calcola il server dalla
proiezione del log e i client si limitano a mostrarlo: una formula sola, in un posto solo.
| `calciatoriAssegnati`        | array of int         | Tutti gli id dei calciatori già aggiudicati o pre-assegnati |
| `creditiInCircolazione`      | int                  | Somma su tutti i partecipanti di (crediti residui + prezzi impegnati in rosa) |

Il campo `calciatoriAssegnati` è la union di tutte le rose. È ridondante ma permette al
telefono di filtrare i calciatori liberi senza iterare le rose di tutti i partecipanti.

**Nota su `secondiResidui`**: il server calcola il valore in tempo reale dal timer al
momento della generazione dello snapshot, non dal valore dell'ultimo evento. Un client
appena connesso riceve i secondi residui correnti. Il client lo usa come punto di
partenza per un timer locale di animazione cosmetico: lo stato del lotto cambia solo
quando arriva un nuovo snapshot dal server.

## Comandi del partecipante (telefono)

### POST /api/offerta

Invia un'offerta sul lotto corrente.

**Body**:

```json
{
  "idLotto": 3,
  "codicePartecipante": "A3K7",
  "importo": 15
}
```

**Risposte**:

| Codice | Significato                                                    |
|--------|----------------------------------------------------------------|
| `200`  | Offerta accettata (lo snapshot aggiornato arriva via SSE)      |
| `400`  | Offerta rifiutata, body: `{"motivo": "offerta non superiore all'offerta corrente"}` |
| `409`  | Lotto diverso da quello corrente o lotto non aperto, body: `{"motivo": "..."}` |

Motivi di rifiuto possibili:
- `"importo non valido"` (non è un intero ≥ 1)
- `"offerta non superiore all'offerta corrente"`
- `"crediti insufficienti"`
- `"lotto non aperto"`
- `"lotto in pausa"`
- `"lotto non corrispondente"` (l'idLotto non è quello corrente)

## Comandi della console (banditore)

### POST /api/console/analizza-listone

Riceve il file xlsx, lo salva nella directory dati, lo analizza e restituisce il tipo
di asta rilevato e i nomi dei partecipanti. Prerequisito obbligatorio prima di
crea-asta. Il nome del file salvato viene registrato nel campo `fileListone`
dell'evento ASTA_CREATA, così il server sa quale file ricaricare all'avvio. Alla
rilettura all'avvio, l'xlsx viene letto in modalità catalogo (solo id, nome, ruolo,
squadra, quotazione, fuoriLista); le colonne FantaSquadra e Costo vengono ignorate
per evitare doppia assegnazione dei calciatori pre-assegnati.

**Content-Type**: `multipart/form-data`

| Part   | Tipo        | Note                        |
|--------|-------------|-----------------------------|
| `file` | file (xlsx) | Il file del listone         |

**Risposta** `200 OK` — asta iniziale (FantaSquadra vuota su tutte le righe):

```json
{
  "tipoAsta": "INIZIALE",
  "calciatori": 561,
  "fuoriLista": 37
}
```

**Risposta** `200 OK` — asta di riparazione (FantaSquadra valorizzata):

```json
{
  "tipoAsta": "RIPARAZIONE",
  "calciatori": 561,
  "fuoriLista": 37,
  "partecipanti": ["Squadra Alpha", "Squadra Beta"]
}
```

**Errori**:

| Codice | Significato                                                    |
|--------|----------------------------------------------------------------|
| `400`  | Formato inatteso, body: `{"errore": "colonna 'R.' mancante"}` |
| `409`  | Asta già in corso                                              |

### POST /api/console/crea-asta

Crea l'asta usando il file xlsx già salvato da analizza-listone.

**Content-Type**: `application/json`

**Body** — asta iniziale:

```json
{
  "nomeAsta": "Campionato Ronchetto 2026-27",
  "durataCountdown": 30,
  "banditorePartecipa": true,
  "partecipanti": [
    {"nome": "Marco", "crediti": 500},
    {"nome": "Luca", "crediti": 500}
  ]
}
```

Per l'asta iniziale, `partecipanti` contiene nomi e crediti (uguali per tutti).

**Body** — asta di riparazione:

```json
{
  "nomeAsta": "Riparazione Ronchetto 2026-27",
  "durataCountdown": 30,
  "banditorePartecipa": false,
  "creditiResidui": {
    "Squadra Alpha": 320,
    "Squadra Beta": 415
  }
}
```

Per l'asta di riparazione, i nomi partecipanti vengono dall'analisi del file
(FantaSquadra). Il banditore inserisce solo i crediti residui. Il server calcola
internamente i crediti totali (residui + somma dei costi pre-assegnati da
FantaSquadra/Costo) e li memorizza nell'evento ASTA_CREATA. L'evento include anche
il campo `fileListone` con il nome del file xlsx salvato da analizza-listone.

**Risposte**:

| Codice | Significato                                                    |
|--------|----------------------------------------------------------------|
| `201`  | Asta creata, body: `{"partecipanti": [{nome, codice}]}`       |
| `400`  | Errore nella configurazione, body: `{"errore": "..."}` |
| `409`  | Asta già in corso                                              |
| `412`  | Nessun file analizzato (chiamare prima analizza-listone)       |

### POST /api/console/apri-lotto

Apre un lotto per un calciatore libero.

**Body**:

```json
{
  "idCalciatore": 5585
}
```

**Risposte**:

| Codice | Significato                                                    |
|--------|----------------------------------------------------------------|
| `200`  | Lotto aperto                                                   |
| `400`  | Calciatore non libero, fuori lista, o non esistente            |
| `409`  | Altro lotto già in corso                                       |

### POST /api/console/conferma

Conferma l'aggiudicazione del lotto scaduto. Nessun body.

**Risposte**:

| Codice | Significato                                                    |
|--------|----------------------------------------------------------------|
| `200`  | Aggiudicazione confermata                                      |
| `409`  | Lotto non in stato SCADUTO, o nessuna offerta                  |

### POST /api/console/riapri

Riapre il lotto dalla scadenza.

**Body**:

```json
{
  "modalita": "DA_CAPO"
}
```

Valori: `DA_CAPO` (offerte azzerate) o `MANTENENDO` (offerta corrente resta).

**Risposte**:

| Codice | Significato                                                    |
|--------|----------------------------------------------------------------|
| `200`  | Lotto riaperto                                                 |
| `409`  | Lotto non in stato SCADUTO                                     |

### POST /api/console/pausa

Mette in pausa il lotto aperto. Nessun body.

**Risposte**:

| Codice | Significato                                                    |
|--------|----------------------------------------------------------------|
| `200`  | Lotto in pausa                                                 |
| `409`  | Lotto non in stato APERTO                                      |

### POST /api/console/riprendi

Riprende il lotto dalla pausa. Nessun body.

**Risposte**:

| Codice | Significato                                                    |
|--------|----------------------------------------------------------------|
| `200`  | Lotto ripreso                                                  |
| `409`  | Lotto non in stato IN_PAUSA                                    |

### POST /api/console/annulla-lotto

Annulla il lotto corrente. Il calciatore torna libero. Nessun body.

**Risposte**:

| Codice | Significato                                                    |
|--------|----------------------------------------------------------------|
| `200`  | Lotto annullato                                                |
| `409`  | Nessun lotto in corso                                          |

## Pagine statiche

| URL              | Servito da                          | Note                              |
|------------------|-------------------------------------|-----------------------------------|
| `/console/`      | Angular build (static resources)    | Console banditore (PC)            |
| `/telefono/`     | HTML/JS/CSS statico                 | Pagina partecipante (telefono)    |
| `/telefono/?codice=A3K7` | Stessa pagina, parametro query | Collegamento diretto via QR code  |
