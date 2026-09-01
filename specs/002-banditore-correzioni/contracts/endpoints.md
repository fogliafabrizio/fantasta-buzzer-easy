# Contratti endpoint — Correzioni del Banditore

**Date**: 2026-09-01 | **Data model**: [data-model.md](../data-model.md)

Delta sui [contratti della feature 1](../../001-asta-funzionante/contracts/endpoints.md).
Tutti gli endpoint esistenti restano **identici**, tranne `POST /api/offerta` che
guadagna un campo **opzionale**: un client vecchio che non lo invia continua a funzionare
esattamente come prima.

## Modifiche a endpoint esistenti

### POST /api/offerta — campo `offertaBase`

**Body** (il campo `offertaBase` è l'unica novità):

```json
{
  "idLotto": 3,
  "codicePartecipante": "A3K7",
  "importo": 22,
  "offertaBase": 20
}
```

| Valore di `offertaBase` | Significato |
|-------------------------|-------------|
| assente o `null` | Importo digitato liberamente: **nessuna guardia**, valutazione identica alla feature 1 (FR-004) |
| `0` | Pulsante rapido premuto su un lotto **senza offerta corrente** |
| intero `≥ 1` | Pulsante rapido premuto sull'offerta corrente dichiarata |

`0` è libero da ambiguità perché le offerte valide sono `≥ 1`. Il telefono invia sempre
`offertaBase` sui pulsanti rapidi e **mai** sull'importo digitato.

**Nuovo motivo di rifiuto**:

| Codice | Body |
|--------|------|
| `409` | `{"motivo": "l'offerta è cambiata mentre rilanciavi"}` |

**Dove viene verificato**: dentro `Asta.registraOfferta`, cioè nel punto di
serializzazione `synchronized` sull'istanza `Asta` già esistente — lo stesso lock in cui
rientra il callback di scadenza del timer. Non nasce un secondo punto di serializzazione.

Ordine dei controlli nel metodo, con la guardia al passo 6:

1. asta attiva
2. partecipante noto
3. lotto in corso
4. `idLotto` corrispondente
5. stato del lotto (`IN_PAUSA` / `SCADUTO` / `AGGIUDICATO` → rifiuto)
6. **guardia**: se `offertaBase != null` e `offertaBase != (offertaCorrente != null ? offertaCorrente : 0)` → `409 "l'offerta è cambiata mentre rilanciavi"`
7. importo valido (intero `≥ 1`)
8. importo superiore all'offerta corrente
9. crediti sufficienti
10. append + fsync + proiezione + riprogrammazione del timer

Sta **dopo** 3-5 perché "lotto non corrispondente" e "tempo scaduto" spiegano meglio cosa
è successo; sta **prima** di 7-9 perché l'edge case della spec impone che il motivo della
guardia prevalga su ogni altro. Il confronto riusa la stessa espressione
`offertaCorrente != null ? offertaCorrente : 0` già presente per la regola del rilancio.

### Forma dello snapshot — tre campi nuovi

```json
{
  "sequenza": 42,
  "nomeAsta": "Asta Lega Bar Sport",
  "durataCountdown": 15,
  "lotto": { "...invariato..." },
  "partecipanti": [ "...invariato, ma crediti può ora essere negativo..." ],
  "calciatoriAssegnati": [5585, 254],
  "creditiInCircolazione": 1000,
  "annullabile": {
    "idLotto": 7,
    "idCalciatore": 5585,
    "codicePartecipante": "A3K7",
    "importo": 36
  }
}
```

| Campo | Tipo | Note |
|-------|------|------|
| `nomeAsta` | String | **Nuovo**. Da questa feature è stato mutevole, quindi appartiene allo snapshot (principio IV). Console e telefoni lo mostrano da qui. |
| `durataCountdown` | int | **Nuovo**. Serve a precompilare la schermata impostazioni senza inventare un GET. |
| `annullabile` | object \| null | **Nuovo**. La prossima aggiudicazione annullabile (cima della pila), o `null` se non ce n'è. Il client **disegna** la disponibilità dell'azione, non la deduce (principio I). |
| `annullabile.idLotto` | int | Da rimandare indietro nel POST come guardia contro il doppio click |
| `annullabile.idCalciatore` | int | Il nome lo risolve il client dal listone locale |
| `annullabile.codicePartecipante` | String | Chi ha **adesso** il calciatore in rosa |
| `annullabile.importo` | int | Il prezzo con cui è **adesso** in rosa, cioè quanto verrà restituito |
| `partecipanti[].crediti` | int | **Semantica estesa**: la formula diventa `creditiTotali − Σ prezzi in rosa + rettificaCrediti` e il valore **può essere negativo** dopo una rettifica o un'aggiunta manuale. Nessun troncamento a zero: sono i client a segnalarlo (vedi sotto). |
| `creditiInCircolazione` | int | Invariato come formula, ma ora **può divergere dal valore iniziale**: solo `CALCIATORE_RIMOSSO` con `importoRestituito != prezzoPagato` lo muove. Resta la spia prevista dalla feature 1. |

Nessun campo nuovo per la vista avversari: usa `partecipanti[]` così com'è (FR-025).

**Obbligo dei client su `crediti` negativi** (FR-016b). Il server non aggiunge alcun flag:
il confronto con zero lo fanno i client, che devono renderlo **vistoso** in tre punti.

| Dove | Cosa |
|------|------|
| Conferma in console, prima di applicare | I crediti risultanti negativi evidenziati nel testo della conferma (FR-016) |
| Tabella partecipanti in console, dopo | **Riga intera** marcata, non solo il numero colorato: si deve vedere scorrendo la tabella senza cercarla |
| Telefono del partecipante interessato | Valore negativo vistoso sulla propria scheda crediti, con la conseguenza scritta a parole ("non puoi rilanciare finché il banditore non sistema i crediti") |

L'ultimo punto è il motivo per cui FR-016b esiste: la regola d'offerta non cambia, quindi
chi è in rosso riceve "crediti insufficienti" anche offrendo 1 — corretto ma
incomprensibile a chi non sa di essere sotto zero. **La causa deve essere visibile prima
che qualcuno provi a rilanciare.** La vista avversari mostra i negativi altrui con lo
stesso stile, senza codice dedicato.

## Nuovi endpoint della console

Valgono per tutti e cinque:

- **Precondizione comune** (FR-007): rifiutati con `409` e
  `{"errore": "le correzioni sono possibili solo quando non c'e' un lotto in corso"}` se
  `lottoCorrente` è in stato `APERTO`, `SCADUTO` o `IN_PAUSA`. Uno stato `AGGIUDICATO` —
  la sola scheda dell'esito a video — **non blocca**: è il momento in cui il banditore si
  accorge dell'errore ([research.md A3](../research.md#a3)).
- **Nessuna asta attiva** → `409 {"errore": "Nessuna asta attiva"}`.
- **Conferma**: il body inviato **è** la conferma. La console mostra la schermata di
  conferma prima di chiamare e compone il testo dallo snapshot e dal listone locale,
  senza endpoint di anteprima ([research.md D8](../research.md#d8--lanteprima-di-conferma-è-calcolata-in-console-senza-endpoint-di-anteprima)).
- **Importi**: interi `≥ 0` (i prezzi delle offerte sono `≥ 1`, ma un prezzo deciso dal
  banditore e una restituzione possono essere `0`). Non interi, negativi o assenti →
  `400` con il motivo.
- In caso di successo: evento appeso e forzato su disco **prima** della risposta, poi
  `sseController.broadcast()`, poi `200`.

---

### POST /api/console/annulla-aggiudicazione

Annulla l'ultima aggiudicazione annullabile: il calciatore torna libero, i crediti tornano
a chi li ha versati.

**Body della conferma**:

```json
{
  "idLotto": 7
}
```

`idLotto` è l'`annullabile.idLotto` letto dallo snapshot. Il server verifica che sia
ancora la cima della pila: è la guardia contro il doppio click e contro una console
rimasta indietro — stessa idea della guardia `offertaBase`, applicata al banditore.

**Risposte**:

| Codice | Significato |
|--------|-------------|
| `200` | `{"stato": "annullata"}` |
| `400` | `{"errore": "Il campo idLotto e' obbligatorio e deve essere numerico"}` |
| `409` | `{"errore": "non c'e' nessuna aggiudicazione da annullare"}` — pila vuota |
| `409` | `{"errore": "l'ultima aggiudicazione annullabile e' cambiata"}` — `idLotto` non è la cima |
| `409` | Precondizione comune (lotto in corso) |

**Evento scritto**: `AGGIUDICAZIONE_ANNULLATA` con `idLotto`, `idCalciatore`,
`codicePartecipante` e `importoRestituito` letti dalla proiezione **corrente** sotto il
lock, non da `LOTTO_AGGIUDICATO` ([research.md A2](../research.md#a2)).

---

### POST /api/console/rettifica-assegnazione

Cambia prezzo, assegnatario o entrambi di un'assegnazione esistente.

**Body della conferma**:

```json
{
  "idCalciatore": 5585,
  "codicePartecipante": "B4M2",
  "prezzo": 36
}
```

Sempre entrambi i valori nuovi, anche quello che non cambia: il body descrive lo stato
dopo, non il delta.

**Risposte**:

| Codice | Significato |
|--------|-------------|
| `200` | `{"stato": "rettificata"}` |
| `400` | `{"errore": "Il campo idCalciatore e' obbligatorio e deve essere numerico"}` |
| `400` | `{"errore": "Il campo prezzo deve essere un intero >= 0"}` |
| `400` | `{"errore": "la rettifica non cambierebbe nulla"}` — stesso assegnatario e stesso prezzo ([research.md A7](../research.md#a7)) |
| `404` | `{"errore": "Codice partecipante sconosciuto"}` |
| `409` | `{"errore": "il calciatore <nome> non e' assegnato a nessuno"}` |
| `409` | Precondizione comune |

Crediti risultanti negativi: **consentiti**, nessun rifiuto (FR-016), ma da segnalare in
modo vistoso su console e telefono dell'interessato (FR-016b).

**Evento scritto**: `ASSEGNAZIONE_RETTIFICATA`.

Se la rettifica riguarda il calciatore della scheda `AGGIUDICATO` ancora a video, lo
snapshot successivo porta `lotto.offerenteCorrente` e `lotto.offertaCorrente` **aggiornati
ai valori nuovi**: la scheda non resta con quelli vecchi.

---

### POST /api/console/rimuovi-da-rosa

Toglie un calciatore da una rosa restituendo un importo deciso dal banditore.

**Body della conferma**:

```json
{
  "idCalciatore": 5585,
  "importoRestituito": 20
}
```

Il partecipante non si indica: il calciatore sta in al più una rosa e il server sa quale.

**Risposte**:

| Codice | Significato |
|--------|-------------|
| `200` | `{"stato": "rimosso"}` |
| `400` | `{"errore": "Il campo idCalciatore e' obbligatorio e deve essere numerico"}` |
| `400` | `{"errore": "Il campo importoRestituito deve essere un intero >= 0"}` |
| `409` | `{"errore": "il calciatore <nome> non e' assegnato a nessuno"}` |
| `409` | Precondizione comune |

**Evento scritto**: `CALCIATORE_RIMOSSO` con `prezzoPagato` letto dalla rosa e presente
**solo** per la leggibilità del log: la proiezione non lo usa mai.

Se la rimozione riguarda il calciatore della scheda `AGGIUDICATO` ancora a video, lo
snapshot successivo porta `lotto: null`: la scheda sparisce, come per l'annullamento.

**Attenzione**: è l'unico endpoint che può spostare `creditiInCircolazione` fuori dal
valore iniziale, di `importoRestituito − prezzoPagato`. Nessun rifiuto e nessuna
compensazione: la conferma in console mostra il totale prima e dopo.

---

### POST /api/console/aggiungi-a-rosa

Mette un calciatore libero nella rosa di un partecipante, senza passare da un lotto.

**Body della conferma**:

```json
{
  "idCalciatore": 5585,
  "codicePartecipante": "A3K7",
  "prezzo": 36
}
```

**Risposte**:

| Codice | Significato |
|--------|-------------|
| `200` | `{"stato": "aggiunto"}` |
| `400` | `{"errore": "Il campo idCalciatore e' obbligatorio e deve essere numerico"}` |
| `400` | `{"errore": "Il campo prezzo deve essere un intero >= 0"}` |
| `400` | `{"errore": "Calciatore <id> non presente nel listone"}` |
| `404` | `{"errore": "Codice partecipante sconosciuto"}` |
| `409` | `{"errore": "il calciatore <nome> e' gia' assegnato"}` — US5 scenario 4 |
| `409` | Precondizione comune |

Un calciatore `fuoriLista` **è** aggiungibile a mano: `apriLotto` lo rifiuta perché non si
batte all'asta, ma una rosa può contenerlo per accordo in stanza. Se in serata si rivela
sbagliato, è un `if` da togliere.

Crediti risultanti negativi: **consentiti** (US5 scenario 3), con la stessa segnalazione
vistosa su console e telefono dell'interessato (FR-016b).

**Evento scritto**: `CALCIATORE_AGGIUNTO`.

---

### POST /api/console/impostazioni

Cambia nome dell'asta e durata del countdown.

**Body della conferma**:

```json
{
  "nomeAsta": "Asta Lega Bar Sport",
  "durataCountdown": 15
}
```

Entrambi obbligatori, anche quando uno solo cambia: la console li precompila dallo
snapshot.

**Risposte**:

| Codice | Significato |
|--------|-------------|
| `200` | `{"stato": "aggiornate"}` |
| `400` | `{"errore": "Il campo nomeAsta e' obbligatorio"}` |
| `400` | `{"errore": "Il campo durataCountdown deve essere un intero >= 1"}` |
| `409` | Precondizione comune |

**Evento scritto**: `IMPOSTAZIONI_MODIFICATE`. `ASTA_CREATA` non viene toccato e il nome
del file JSONL non cambia. La nuova durata vale dal lotto successivo per costruzione: non
c'è un lotto in corso da cui potrebbe cambiare ritmo a metà.

Partecipanti, codici e crediti **non** sono in questo endpoint (FR-022): si correggono con
rettifica, rimozione e aggiunta.
