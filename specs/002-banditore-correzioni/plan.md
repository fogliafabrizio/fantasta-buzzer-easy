# Implementation Plan: Correzioni del Banditore e Rifiniture

**Branch**: `002-banditore-correzioni` | **Date**: 2026-09-01 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-banditore-correzioni/spec.md`

## Summary

Cinque nuovi tipi di evento appesi al log esistente danno al banditore la capacità di
correggere in avanti (annullamento, rettifica, rimozione, aggiunta manuale) e di
modificare nome e countdown dell'asta. Nessun evento della feature 1 cambia forma:
i log già scritti restano rileggibili riga per riga, e la ricostruzione all'avvio
guadagna cinque rami nel `switch` di `applicaEvento`, non un secondo motore.

La guardia sui pulsanti rapidi è un campo opzionale nel POST dell'offerta, verificato
dentro `Asta.registraOfferta` — lo stesso metodo `synchronized` che già serializza
offerte e scadenza. Non nasce un secondo punto di serializzazione: tutte le correzioni
sono metodi `synchronized` sulla stessa istanza `Asta`, come `confermaLotto` e
`apriLotto`.

Un solo cambiamento strutturale nella proiezione: `Partecipante` acquista un
accumulatore `rettificaCrediti`, senza il quale la restituzione a piacere (FR-014) non
è esprimibile. Dettagli e giustificazione in [Complexity Tracking](#complexity-tracking).

**Artefatti**: [research.md](research.md) (decisioni e ambiguità), [data-model.md](data-model.md)
(eventi, proiezione, rilettura), [contracts/endpoints.md](contracts/endpoints.md) (endpoint e
snapshot), [quickstart.md](quickstart.md) (scenari di verifica manuale).

## Technical Context

**Language/Version**: Java 21, Spring Boot 3 (modulo unico, invariato dalla feature 1)

**Primary Dependencies**: Spring Web (REST + SSE), Jackson (polimorfismo eventi via
`@JsonSubTypes` su `Evento`), Apache POI (import xlsx). Console in Angular + PrimeNG.
Telefono in HTML/JS statico senza build. **Nessuna nuova dipendenza.**

**Storage**: file JSONL append-only in `fantasta.dati.dir`, un file per asta, più l'xlsx
del listone. Nessun database, nessuna migrazione.

**Testing**: nessuno. La costituzione (VII.6) vieta i test automatici; la verifica è
manuale ed è descritta in [quickstart.md](quickstart.md).

**Target Platform**: portatile del banditore in LAN, telefoni dei partecipanti via
browser. Nessuna chiamata a internet.

**Project Type**: applicazione web a modulo singolo con due frontend serviti dallo
stesso jar.

**Performance Goals**: irrilevanti alla scala della serata (≤ 12 partecipanti, qualche
centinaio di eventi). Il vincolo reale resta la latenza percepita del buzzer, non
toccata da questa feature.

**Constraints**: ogni evento è forzato su disco prima della risposta HTTP (già garantito
da `LogEventi.append`); nessun evento viene mai riscritto o rimosso; lo stato ricostruito
al riavvio deve coincidere con quello precedente al riavvio.

**Scale/Scope**: 5 nuovi tipi di evento, 5 nuovi endpoint console, 1 campo opzionale nel
POST offerta, 3 nuovi campi nello snapshot, 2 nuovi componenti Angular, 2 sezioni nuove
nel telefono.

## Constitution Check

*GATE: superato prima di Phase 0 e ri-verificato dopo Phase 1.*

| Principio | Verifica sul piano | Esito |
|-----------|--------------------|-------|
| **I. Lessico e dominio** | Eventi ed endpoint in italiano; `Calciatore`/`Partecipante`/`Lotto`/`Asta` usati nel senso obbligatorio. La macchina a stati del lotto **non cambia**: nessuna correzione introduce una transizione nuova (l'annullamento agisce sulla proiezione delle rose, non sul lotto). | ✅ |
| **II. Il log è lo stato** | Ogni correzione è un evento appeso in fondo. Rose, crediti e `calciatoriAssegnati` restano proiezioni ricalcolate; `rettificaCrediti` è a sua volta una proiezione (somma dei delta degli eventi di rimozione), non un dato persistito a parte. | ✅ |
| **III. Il server è l'unica autorità** | La guardia `offertaBase` è valutata dal server dentro `registraOfferta`. La console non decide: propone e il server accetta o rifiuta. L'anteprima di conferma è aritmetica di visualizzazione su dati già nello snapshot — stessa natura del calcolo `+N` già fatto dal telefono nella feature 1. Vedi [research.md](research.md#d8). | ✅ |
| **IV. Trasporto e stato condiviso** | Solo SSE + POST. Snapshot sempre completo. Nessun endpoint per la vista avversari (FR-025). Tre campi nuovi nello snapshot, tutti stato mutevole che prima non lo era. | ✅ |
| **V. Struttura e semplicità** | Nessun modulo, nessuna interfaccia, nessuna factory. Le correzioni sono metodi `synchronized` su `Asta`, accanto a quelli esistenti. Nessuna nuova dipendenza. | ✅ |
| **VI. Errori, log e recovery** | Ogni rifiuto ha motivo in italiano restituito al chiamante e riga di log che dice cosa si stava facendo e su chi. La rilettura dei nuovi eventi è specificata evento per evento in [data-model.md](data-model.md#rilettura-del-log-e-ricostruzione-allavvio). | ✅ |
| **VII. Divieti** | Niente internet, niente auth, niente stato fuori dal log, niente nuovo trasporto, niente logica di dominio nel client, niente test/Docker/CI, nessuno schermo che non serva alla serata. | ✅ |
| **A. Ordine obbligatorio** | Questa feature copre i passi 6 e 7 (annullamento, rettifiche) più tre rifiniture. I passi 1-5 sono completi e funzionanti. L'ordine interno delle user story (P1 → P3) rispetta "se il tempo finisce, finisce dal fondo". | ✅ |

**Nessuna violazione da giustificare** oltre alla voce in Complexity Tracking, che è un
campo dati richiesto da un requisito, non un'astrazione preventiva.

## Project Structure

### Documentation (this feature)

```text
specs/002-banditore-correzioni/
├── plan.md              # Questo file
├── research.md          # Decisioni, ambiguità della spec, invarianti sui crediti
├── data-model.md        # Nuovi eventi, effetto sulla proiezione, rilettura
├── quickstart.md        # Scenari di verifica manuale
├── contracts/
│   └── endpoints.md     # Delta agli endpoint e allo snapshot
└── checklists/
```

### Source Code (repository root)

Solo file esistenti modificati, più cinque classi evento e due componenti Angular.

```text
src/main/java/fantasta/
├── asta/
│   ├── Asta.java                    # MODIFICATO: 5 metodi synchronized nuovi,
│   │                                #   5 rami in applicaEvento, campo annullabili,
│   │                                #   parametro offertaBase in registraOfferta
│   ├── Partecipante.java            # MODIFICATO: rimuovi(), rettificaCrediti
│   └── Aggiudicazione.java          # NUOVO: record (idLotto, idCalciatore) per lo
│                                    #   stack degli annullabili
├── eventi/
│   ├── Evento.java                  # MODIFICATO: 5 voci in @JsonSubTypes
│   ├── AggiudicazioneAnnullata.java # NUOVO
│   ├── AssegnazioneRettificata.java # NUOVO
│   ├── CalciatoreRimosso.java       # NUOVO
│   ├── CalciatoreAggiunto.java      # NUOVO
│   └── ImpostazioniModificate.java  # NUOVO
└── web/
    ├── Snapshot.java                # MODIFICATO: nomeAsta, durataCountdown, annullabile
    ├── ConsoleController.java       # MODIFICATO: 5 endpoint nuovi
    └── OffertaController.java       # MODIFICATO: legge offertaBase e lo passa grezzo

console/src/app/console/
├── correzioni/correzioni.component.ts   # NUOVO: annullamento, rettifica, rimozione,
│                                        #   aggiunta, con dialog di conferma PrimeNG
├── impostazioni/impostazioni.component.ts # NUOVO: nome asta e durata countdown
├── tabella-partecipanti/…               # MODIFICATO: riga intera marcata quando i
│                                        #   crediti sono negativi (FR-016b)
└── lotto-corrente/…                     # MODIFICATO: mostra nomeAsta dallo snapshot

src/main/resources/static/telefono/
├── app.js       # MODIFICATO: offertaBase sui pulsanti rapidi, disabilitazione fino
│                #   al prossimo snapshot, sezione avversari collassabile,
│                #   segnalazione vistosa dei propri crediti negativi (FR-016b)
├── index.html   # MODIFICATO: contenitore della sezione avversari
└── style.css    # MODIFICATO: stile della sezione collassabile e del rosso crediti
```

**Structure Decision**: nessuna nuova struttura. Si estendono i quattro package di
dominio già esistenti (`asta`, `eventi`, `listone`, `web`), coerentemente con il
principio V ("package per contesto di dominio, mai per strato tecnico"). Il package
`listone` non viene toccato: nessuna correzione riguarda il catalogo.

## Ordine di implementazione

Segue le priorità della spec, che a loro volta seguono l'appendice A della costituzione.
Ogni gruppo si chiude funzionante prima di iniziare il successivo.

| Gruppo | Contenuto | User story |
|--------|-----------|------------|
| 1 | Guardia `offertaBase` + disabilitazione dei pulsanti rapidi | US1 |
| 2 | `AGGIUDICAZIONE_ANNULLATA`, stack degli annullabili, campo `annullabile` nello snapshot, endpoint e conferma in console | US2 |
| 3 | `ASSEGNAZIONE_RETTIFICATA`, `CALCIATORE_RIMOSSO`, `CALCIATORE_AGGIUNTO`, `rettificaCrediti`, endpoint e conferme, segnalazione vistosa dei crediti negativi su console e telefono | US3, US4, US5, FR-016b |
| 4 | `IMPOSTAZIONI_MODIFICATE`, `nomeAsta` e `durataCountdown` nello snapshot, schermata impostazioni | US6 |
| 5 | Sezione avversari collassabile sul telefono | US7 |

Il gruppo 3 tiene insieme le tre correzioni non-annullamento perché condividono
`Partecipante.rimuovi`, l'accumulatore `rettificaCrediti` e la stessa guardia
"nessun lotto in corso": separarle produrrebbe tre mezze implementazioni dello stesso
meccanismo.

## Complexity Tracking

| Violazione | Perché serve | Alternativa più semplice, e perché scartata |
|------------|--------------|---------------------------------------------|
| `Partecipante.rettificaCrediti`: un campo accumulato in più nella proiezione, oltre alla rosa | FR-014 impone che la rimozione restituisca un importo **deciso dal banditore**, diverso dal prezzo pagato e possibilmente zero. La formula attuale `crediti = creditiTotali − Σ prezzi in rosa` restituisce sempre e solo il prezzo pagato: la differenza non ha dove stare. `rettificaCrediti` è la somma di `(importoRestituito − prezzoPagato)` sugli eventi `CALCIATORE_RIMOSSO`, quindi resta una proiezione ricalcolata dal log (principio II), non un dato persistito. | **Cambiare `creditiTotali`**: scartata perché `creditiTotali` è la dotazione iniziale, viaggia nello snapshot ed è la base della verifica "i conti tornano". Alterarla renderebbe indistinguibile una dotazione da una correzione. **Registrare la rimozione come rosa con prezzo negativo**: scartata perché sporcherebbe la rosa con voci fantasma e romperebbe `calciatoriAssegnati` e la vista avversari. Ha un solo uso reale (la rimozione) e questo è consapevole: non è un livello di indirezione "per dopo" ma il campo minimo che rende esprimibile un requisito. |
