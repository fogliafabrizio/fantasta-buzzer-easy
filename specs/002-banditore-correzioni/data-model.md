# Data Model — Correzioni del Banditore e Rifiniture

**Date**: 2026-09-01 | **Spec**: [spec.md](spec.md) | **Research**: [research.md](research.md)

Documento incrementale sul [data model della feature 1](../001-asta-funzionante/data-model.md).
Gli eventi già definiti lì **non cambiano di un campo**: un log scritto ieri si rilegge
oggi identico. Qui ci sono solo le aggiunte.

## Modifiche alla proiezione

### Partecipante

| Campo | Tipo | Stato |
|-------|------|-------|
| nome, codice, creditiTotali, rosa | — | invariati |
| **rettificaCrediti** | int, default 0 | **nuovo** |

La formula dei crediti residui passa da

```
crediti = creditiTotali − Σ prezzi in rosa
```

a

```
crediti = creditiTotali − Σ prezzi in rosa + rettificaCrediti
```

`rettificaCrediti` è la somma di `(importoRestituito − prezzoPagato)` su tutti gli eventi
`CALCIATORE_RIMOSSO` che riguardano quel partecipante. Resta una proiezione ricalcolata
dal log: nessun evento la persiste, è il replay a farla crescere. È l'unico modo di
esprimere una restituzione diversa dal prezzo pagato — giustificazione completa in
[plan.md](plan.md#complexity-tracking).

Due nuovi metodi, entrambi con più di un chiamante reale:

- `rimuovi(int idCalciatore)` → cerca la `VoceRosa` nei quattro ruoli, la toglie e
  restituisce il prezzo che portava; `null` se non c'è. Usato da annullamento, rettifica
  e rimozione.
- `applicaRettificaCrediti(int delta)` → somma a `rettificaCrediti`. Usato dalla rimozione
  (dal vivo e in rilettura).

Un calciatore non può comparire due volte in una rosa né in due rose: `calciatoriAssegnati`
e i controlli di `apriLotto`, `aggiungiARosa` e `rettificaAssegnazione` lo garantiscono.
`rimuovi` toglie quindi sempre al più una voce.

### Asta

| Campo | Tipo | Stato |
|-------|------|-------|
| nomeAsta, durataCountdown | String, int | **ora mutevoli** (erano fissati alla creazione) |
| **annullabili** | `Deque<Aggiudicazione>` | **nuovo** |

```java
public record Aggiudicazione(int idLotto, int idCalciatore) {}
```

La pila delle aggiudicazioni ancora annullabili, dalla meno recente (fondo) alla più
recente (cima). **La prossima annullabile è la cima, e solo quella.** Regole di
manutenzione, applicate dentro `applicaEvento` e quindi identiche dal vivo e in rilettura:

| Evento | Effetto sulla pila |
|--------|--------------------|
| `LOTTO_AGGIUDICATO` | impila `(idLotto, idCalciatore)` in cima |
| `AGGIUDICAZIONE_ANNULLATA` | toglie la voce con quell'`idLotto` (è sempre la cima) |
| `CALCIATORE_RIMOSSO` | toglie la voce con quell'`idCalciatore`, ovunque si trovi |
| `ASSEGNAZIONE_RETTIFICATA` | nessuno: resta annullabile, sui valori correnti |
| `CALCIATORE_AGGIUNTO` | nessuno: un'aggiunta manuale non è un'aggiudicazione |
| `ASSEGNAZIONE_INIZIALE` | nessuno: le assegnazioni della riparazione non si annullano |
| `LOTTO_ANNULLATO` | nessuno: chiude la scheda a video, non disfa l'assegnazione |

Motivazione e alternative in [research.md D3](research.md#d3--ultima-aggiudicazione-annullabile)
e [A1](research.md#a1)/[A2](research.md#a2).

## Nuovi tipi di evento

Tutti condividono i tre campi comuni già definiti (`tipo`, `istante`, `sequenza`) e vanno
registrati in `@JsonSubTypes` su `Evento`. Tutti portano i valori che applicano: la
proiezione li applica alla lettera, senza ricalcolare ([research.md D2](research.md#d2--gli-eventi-di-correzione-portano-i-valori-che-applicano)).

---

### AGGIUDICAZIONE_ANNULLATA

Il banditore annulla l'ultima aggiudicazione annullabile. Il calciatore torna libero, i
crediti tornano a chi li aveva versati.

| Campo | Tipo | Note |
|-------|------|------|
| tipo | `"AGGIUDICAZIONE_ANNULLATA"` | |
| idLotto | int | Il lotto la cui aggiudicazione viene disfatta |
| idCalciatore | int | Denormalizzato dalla voce in pila |
| codicePartecipante | String | Chi ha il calciatore in rosa **al momento dell'annullamento** (può differire dal vincitore originale, se c'è stata una rettifica) |
| importoRestituito | int | Il prezzo con cui il calciatore è **attualmente** in rosa |

**Effetto sulla proiezione**:

| Rose | `codicePartecipante.rimuovi(idCalciatore)` — la voce esce dalla rosa |
|------|------|
| **Crediti** | `+importoRestituito` per `codicePartecipante`, per sola conseguenza della formula (la voce non pesa più). `rettificaCrediti` **non** viene toccato: `importoRestituito` è per costruzione uguale al prezzo che era in rosa. |
| **Calciatori assegnati** | `calciatoriAssegnati.remove(idCalciatore)` — il calciatore torna libero e riapribile come lotto |
| **Pila annullabili** | toglie la voce con quell'`idLotto` |
| **Lotto corrente** | se `lottoCorrente != null && lottoCorrente.idLotto == idLotto` → `lottoCorrente = null` (la scheda a video sparisce). Altrimenti intatto. Vedi [research.md A4](research.md#a4). |
| **Crediti in circolazione** | **invariato** |

---

### ASSEGNAZIONE_RETTIFICATA

Il banditore corregge prezzo, assegnatario o entrambi, in una sola operazione e in una
sola riga di log.

| Campo | Tipo | Note |
|-------|------|------|
| tipo | `"ASSEGNAZIONE_RETTIFICATA"` | |
| idCalciatore | int | Identifica l'assegnazione ([research.md D1](research.md#d1--identità-di-unassegnazione-idcalciatore)) |
| codicePartecipanteVecchio | String | Chi lo aveva prima |
| prezzoVecchio | int | A quanto lo aveva |
| codicePartecipanteNuovo | String | Chi lo ha dopo; uguale al vecchio se si cambia solo il prezzo |
| prezzoNuovo | int | Quanto paga dopo; uguale al vecchio se si cambia solo l'assegnatario |

`prezzoVecchio` e `codicePartecipanteVecchio` sono denormalizzati per leggibilità del log e
non vengono riletti dalla proiezione: l'effetto usa solo `idCalciatore` per trovare la voce.

**Effetto sulla proiezione**:

| Rose | `vecchio.rimuovi(idCalciatore)`, poi `nuovo.acquista(ruolo, idCalciatore, prezzoNuovo)`. Il `ruolo` viene dal listone (`trovaCalciatore`), come già fa `LOTTO_AGGIUDICATO`. Se vecchio e nuovo coincidono, la voce esce e rientra nella stessa rosa con il nuovo prezzo. |
|------|------|
| **Crediti** | `vecchio: +prezzoVecchio`, `nuovo: −prezzoNuovo`, per sola conseguenza della formula. `rettificaCrediti` non toccato. I crediti del nuovo **possono diventare negativi** ed è ammesso (FR-016). |
| **Calciatori assegnati** | **invariato**: il calciatore resta assegnato, cambia solo a chi |
| **Pila annullabili** | **invariata** |
| **Lotto corrente** | se `lottoCorrente != null && lottoCorrente.stato == AGGIUDICATO && lottoCorrente.idCalciatore == idCalciatore` → la scheda a video si **aggiorna**: `offerenteCorrente = codicePartecipanteNuovo`, `offertaCorrente = prezzoNuovo`. Altrimenti intatto. La scheda dice chi ha vinto e a quanto: dopo la rettifica quei due valori sono cambiati, e mostrarli vecchi sarebbe una bugia plausibile, quindi peggiore. Vedi [research.md A4](research.md#a4). |
| **Crediti in circolazione** | **invariato** |

---

### CALCIATORE_RIMOSSO

Il calciatore esce da una rosa e l'importo restituito lo decide il banditore.

| Campo | Tipo | Note |
|-------|------|------|
| tipo | `"CALCIATORE_RIMOSSO"` | |
| idCalciatore | int | |
| codicePartecipante | String | Chi lo aveva |
| prezzoPagato | int | Il prezzo con cui era in rosa |
| importoRestituito | int | Deciso dal banditore, `≥ 0`, può essere `0` e può differire da `prezzoPagato` |

`prezzoPagato` è **solo** denormalizzazione per la leggibilità del log: la proiezione non
lo legge mai. Il rimborso viene dalla voce di rosa (il valore restituito da `rimuovi`) e
il delta di `rettificaCrediti` si calcola dalla stessa fonte, così due numeri che devono
coincidere non hanno due origini diverse. È l'unica eccezione a [D2](research.md#d2--gli-eventi-di-correzione-portano-i-valori-che-applicano),
e vale per un motivo preciso: qui l'evento porta due grandezze legate da una sottrazione,
e fidarsi di entrambe significa poter sbagliare i crediti se non concordano.

**Effetto sulla proiezione**:

| Rose | `codicePartecipante.rimuovi(idCalciatore)` |
|------|------|
| **Crediti** | `+prezzoPagato` dalla formula, più `applicaRettificaCrediti(importoRestituito − prezzoPagato)`. Netto: `+importoRestituito`. |
| **Calciatori assegnati** | `calciatoriAssegnati.remove(idCalciatore)` — torna libero |
| **Pila annullabili** | toglie la voce con quell'`idCalciatore`, ovunque si trovi: non c'è più un'aggiudicazione da disfare |
| **Lotto corrente** | se `lottoCorrente != null && lottoCorrente.stato == AGGIUDICATO && lottoCorrente.idCalciatore == idCalciatore` → `lottoCorrente = null`: la scheda a video **sparisce**. Altrimenti intatto. Una scheda che dice "vinto da Marco per 36" mentre quel calciatore è tornato libero è la stessa bugia dell'annullamento, con un comando diverso. Vedi [research.md A4](research.md#a4). |
| **Crediti in circolazione** | **cambia di `importoRestituito − prezzoPagato`**. È l'unico evento che sposta il totale, ed è voluto ([research.md](research.md#totale-in-circolazione-fuori-dal-valore-iniziale-sì-da-una-sola-correzione)). |

---

### CALCIATORE_AGGIUNTO

Un calciatore libero entra in una rosa senza passare da un lotto.

| Campo | Tipo | Note |
|-------|------|------|
| tipo | `"CALCIATORE_AGGIUNTO"` | |
| idCalciatore | int | Deve essere libero al momento del comando |
| codicePartecipante | String | Destinatario |
| prezzo | int | `≥ 0`, deciso dal banditore |

**Effetto sulla proiezione**:

| Rose | `codicePartecipante.acquista(ruolo, idCalciatore, prezzo)`, con `ruolo` dal listone |
|------|------|
| **Crediti** | `−prezzo`, dalla formula. **Possono diventare negativi** ed è ammesso (US5 scenario 3). |
| **Calciatori assegnati** | `calciatoriAssegnati.add(idCalciatore)` — non più proponibile come lotto |
| **Pila annullabili** | **invariata**: non è un'aggiudicazione ([research.md A1](research.md#a1)) |
| **Lotto corrente** | **invariato** |
| **Crediti in circolazione** | **invariato** |

---

### IMPOSTAZIONI_MODIFICATE

Nome dell'asta e durata del countdown, cambiati tra un lotto e l'altro.

| Campo | Tipo | Note |
|-------|------|------|
| tipo | `"IMPOSTAZIONI_MODIFICATE"` | |
| nomeAsta | String | Non vuoto |
| durataCountdown | int | Intero `≥ 1` |

Entrambi i campi sono sempre presenti, anche quando uno solo dei due cambia: l'evento
descrive le impostazioni dopo la modifica, non il delta. L'evento `ASTA_CREATA` **non**
viene mai riscritto.

**Effetto sulla proiezione**:

| Rose, crediti, calciatori assegnati, pila annullabili, lotto corrente | **nessuno** |
|---|---|
| **Asta** | `nomeAsta = nomeAsta`, `durataCountdown = durataCountdown` |
| **Crediti in circolazione** | **invariato** |

Il nome del file JSONL **non** cambia: è stato scelto alla creazione dal nome di allora e
rinominarlo significherebbe toccare il passato. Solo il nome mostrato cambia.

---

## Rilettura del log e ricostruzione all'avvio

La procedura della feature 1 (passi 1-7) resta identica. Cambia solo la tabella del passo 4,
che guadagna cinque righe. Nessun evento nuovo richiede un secondo passaggio sul file,
uno stato temporaneo o una lettura all'indietro: `applicaEvento` resta una funzione
dell'evento e dello stato corrente.

| Evento | Effetto sulla proiezione, in rilettura |
|--------|-----------------------------------------|
| *(dieci righe della feature 1)* | invariate |
| **AGGIUDICAZIONE_ANNULLATA** | toglie la voce di rosa a `codicePartecipante`, libera `idCalciatore`, toglie l'`idLotto` dalla pila; azzera `lottoCorrente` se è quello |
| **ASSEGNAZIONE_RETTIFICATA** | sposta la voce di rosa da `codicePartecipanteVecchio` a `codicePartecipanteNuovo` con `prezzoNuovo`; `calciatoriAssegnati` invariato; aggiorna offerente e importo della scheda `AGGIUDICATO` a video se riguarda il suo calciatore |
| **CALCIATORE_RIMOSSO** | toglie la voce di rosa e somma a `rettificaCrediti` la differenza fra `importoRestituito` e il prezzo **restituito da `rimuovi`**, libera `idCalciatore`, toglie la voce dalla pila, azzera la scheda `AGGIUDICATO` a video se riguarda il suo calciatore |
| **CALCIATORE_AGGIUNTO** | aggiunge la voce di rosa a `prezzo`, marca `idCalciatore` assegnato |
| **IMPOSTAZIONI_MODIFICATE** | sostituisce `nomeAsta` e `durataCountdown` |

### Perché lo stato ricostruito è identico a quello di prima del riavvio

Quattro proprietà, verificabili leggendo `applicaEvento`:

1. **Ogni evento di correzione porta i valori che applica.** L'effetto di una riga non
   dipende da come è stato costruito lo stato prima di essa, ma solo dalla posizione della
   riga nel file — che è la stessa dal vivo e in rilettura.
2. **Dal vivo si passa dagli stessi rami.** I metodi di correzione fanno append e poi
   chiamano `applicaEvento` sullo stesso oggetto evento, esattamente come `confermaLotto`
   e `apriLotto` nella feature 1. Non esiste un percorso "dal vivo" che modifichi la
   proiezione senza passare da lì.
3. **La pila degli annullabili è deterministica.** Nasce vuota, cresce e cala solo per i
   quattro eventi della tabella, nell'ordine del file. Non dipende dall'orologio né da
   quanti riavvii ci sono stati in mezzo. Dopo il replay la cima è la stessa che era prima
   dello spegnimento.
4. **`rettificaCrediti` è una somma di interi commutativa e senza saturazione.** Rileggere
   gli stessi eventi nello stesso ordine dà lo stesso totale, anche negativo.

**Interazione con `durataCountdown` mutevole** — punto delicato, perché tre eventi della
feature 1 leggono `durataCountdown` durante il replay:

- `LOTTO_APERTO` → `istanteScadenza = istante + durataCountdown`
- `OFFERTA_ACCETTATA` → resetta `istanteScadenza` a `istante + durataCountdown`
- `LOTTO_RIAPERTO` → `istanteScadenza = istante + durataCountdown`

In rilettura questi usano il valore **in vigore a quel punto del log**, cioè quello
dell'ultimo `IMPOSTAZIONI_MODIFICATE` precedente (o di `ASTA_CREATA` se non ce ne sono).
È esattamente il valore che era in memoria dal vivo, perché le impostazioni si possono
cambiare solo quando non c'è un lotto in corso (FR-021): nessun `IMPOSTAZIONI_MODIFICATE`
può mai cadere in mezzo agli eventi di un lotto. Nessuna modifica ai tre rami esistenti.

**Interazione con il passo 5** (lotto rimasto `APERTO` al riavvio → si riscrive
`LOTTO_IN_PAUSA` con `secondiResidui = durataCountdown`): usa la durata corrente
post-replay, cioè quella modificata. FR-023 soddisfatto.

**Log della feature 1 senza eventi nuovi**: si rileggono senza differenze. La pila resta
popolata dai soli `LOTTO_AGGIUDICATO`, `rettificaCrediti` resta 0 per tutti e la formula
dei crediti dà lo stesso risultato di prima. Nessuna migrazione, nessuna versione di
schema, nessun campo obbligatorio aggiunto agli eventi esistenti.
