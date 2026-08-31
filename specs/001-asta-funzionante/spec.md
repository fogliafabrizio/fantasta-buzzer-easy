# Feature Specification: Asta Funzionante

**Feature Branch**: `001-asta-funzionante`

**Created**: 2026-08-31

**Status**: Draft

**Input**: Serata d'asta del fantacalcio in LAN: il banditore conduce dal PC, i partecipanti rilanciano dal telefono. Import del listone xlsx, apertura lotti, buzzer, countdown, conferma o riapertura, gestione crediti e rose.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Creazione asta da listone xlsx (Priority: P1)

Il banditore avvia l'applicazione, seleziona il file xlsx "lista calciatori" esportato da Leghe Fantacalcio e crea una nuova asta. Il sistema legge il primo foglio del file, indipendentemente dal suo nome. La riga 1 contiene le intestazioni; le righe successive sono i calciatori.

Le colonne attese sono, nell'ordine: `#`, `Nome`, `Fuori lista`, `Sq.`, `Under`, `R.`, `R.MANTRA`, `PGv`, `MV`, `FM`, `FVM/1000`, `QUOT.`, `FantaSquadra`, `Costo`. L'identificazione avviene per nome di intestazione, non per posizione.

- **Identita del calciatore**: colonna `#` (numerico, univoco).
- **Ruolo**: colonna `R.` con valori ammessi `P`, `D`, `C`, `A`. La colonna `R.MANTRA` viene ignorata.
- **Fuori lista**: i calciatori con valore `*` nella colonna `Fuori lista` vengono importati ma marcati come non disponibili per l'asta.
- **Quotazione**: colonna `QUOT.`.
- **Squadra di serie A**: colonna `Sq.`.

Il tipo di serata si determina automaticamente:

- Se la colonna `FantaSquadra` e vuota su tutte le righe: **asta iniziale**. Il banditore inserisce i nomi dei partecipanti e i crediti iniziali (uguali per tutti).
- Se la colonna `FantaSquadra` e valorizzata su almeno una riga: **asta di riparazione**. I partecipanti sono i valori distinti di `FantaSquadra`; ogni calciatore con `FantaSquadra` compilata entra gia assegnato a quel partecipante al prezzo indicato in `Costo`. I crediti residui di ciascun partecipante li inserisce il banditore manualmente, perche il file non li contiene.

**Why this priority**: Senza l'import non esiste l'asta. Tutto il resto dipende da questo passo.

**Independent Test**: Importare un file xlsx valido e verificare che il listone sia caricato correttamente, che il tipo di serata sia riconosciuto, e che i partecipanti e i crediti siano configurati.

**Acceptance Scenarios**:

1. **Given** un file xlsx con 14 colonne nell'intestazione e FantaSquadra vuota su tutte le righe, **When** il banditore lo importa e inserisce 8 partecipanti con 500 crediti ciascuno, **Then** l'asta iniziale viene creata con 561 calciatori (di cui 37 marcati fuori lista), 8 partecipanti ciascuno con 500 crediti e rosa vuota.
2. **Given** un file xlsx con FantaSquadra valorizzata su alcune righe, **When** il banditore lo importa e inserisce i crediti residui, **Then** l'asta di riparazione viene creata con i calciatori gia assegnati ai rispettivi partecipanti ai prezzi indicati.
3. **Given** un file xlsx a cui manca una colonna obbligatoria (es. `R.`), **When** il banditore tenta l'import, **Then** il sistema mostra un errore leggibile indicando la colonna mancante e non importa nulla.
4. **Given** un file xlsx con una riga che ha un valore non valido nella colonna `R.` (es. `X`), **When** il banditore tenta l'import, **Then** il sistema mostra un errore indicando la riga problematica e non importa nulla.

---

### User Story 2 - Connessione dei partecipanti via QR code (Priority: P1)

Dopo la creazione dell'asta, la console del banditore mostra un QR code per ciascun partecipante. Il QR code contiene l'URL del telefono e il codice del partecipante, cosi basta inquadrarlo per collegarsi. L'URL e generato a runtime dall'indirizzo IP reale dell'interfaccia di rete. L'URL e il codice sono anche leggibili e comunicabili a voce.

**Why this priority**: Senza collegamento, i partecipanti non possono partecipare.

**Independent Test**: Creare un'asta, inquadrare il QR code con un telefono e verificare che la pagina si apra gia collegata al partecipante corretto.

**Acceptance Scenarios**:

1. **Given** un'asta creata con 8 partecipanti, **When** la console mostra i QR code, **Then** ciascun QR code contiene un URL con l'IP reale del server e il codice del partecipante, e l'URL e leggibile anche come testo.
2. **Given** un partecipante che inquadra il QR code dal telefono, **When** la pagina si apre, **Then** il partecipante vede il proprio nome, i propri crediti e la propria rosa (vuota nell'asta iniziale).

---

### User Story 3 - Apertura lotto e buzzer dal telefono (Priority: P1)

Il banditore cerca un calciatore ancora libero dalla console e apre un lotto. I partecipanti vedono sul telefono il calciatore in asta con nome, ruolo, squadra, quotazione. Il buzzer si attiva: un pulsante per rilanciare, pulsanti rapidi (+1, +2, +5, +10 calcolati sull'offerta corrente) e un campo per digitare un importo qualsiasi. Il partecipante invia un'offerta assoluta.

**Why this priority**: Il lotto e il buzzer sono il cuore della serata.

**Independent Test**: Aprire un lotto dalla console, fare un rilancio dal telefono, e verificare che l'offerta appaia su tutti i dispositivi.

**Acceptance Scenarios**:

1. **Given** un'asta in corso senza lotto aperto, **When** il banditore cerca "Malen" e apre il lotto, **Then** tutti i telefoni vedono: "Malen", ruolo "A", squadra "Roma", quotazione 36, nessuna offerta corrente, countdown in corso.
2. **Given** un lotto aperto su "Malen" senza offerte, **When** il partecipante "Marco" preme il buzzer con importo 5, **Then** l'offerta 5 di Marco appare su tutti i dispositivi come offerta corrente.
3. **Given** un lotto con offerta corrente 5 di Marco, **When** il partecipante "Luca" preme +2, **Then** il sistema riceve l'offerta 7 da Luca, e tutti vedono offerta corrente 7 di Luca.
4. **Given** un lotto con offerta corrente 7, **When** Marco prova a offrire 6, **Then** l'offerta viene rifiutata e Marco vede il motivo: "offerta non superiore all'offerta corrente".

---

### User Story 4 - Countdown, scadenza e conferma del banditore (Priority: P1)

Quando un lotto e aperto, parte un countdown visibile su tutti i dispositivi. Ogni nuova offerta accettata fa ripartire il countdown. Alla scadenza, il buzzer si disattiva, i telefoni mostrano "in attesa del banditore" con l'offerta vincente. Il banditore puo:
- **Confermare** l'aggiudicazione: il calciatore viene assegnato all'offerente al prezzo offerto, i crediti si scalano.
- **Riaprire da capo**: offerte azzerate, countdown riparte.
- **Riaprire mantenendo**: l'offerta corrente resta, il countdown riparte.

Se il countdown scade senza nessuna offerta, il calciatore torna libero e richiamabile.

**Why this priority**: La conferma chiude il ciclo del lotto; senza di essa nessun calciatore viene aggiudicato.

**Independent Test**: Aprire un lotto, fare un'offerta, attendere la scadenza, e testare le tre azioni del banditore separatamente.

**Acceptance Scenarios**:

1. **Given** un lotto scaduto con offerta 10 di Marco, **When** il banditore conferma, **Then** il calciatore viene aggiudicato a Marco per 10 crediti, i crediti di Marco diminuiscono di 10, il calciatore appare nella rosa di Marco.
2. **Given** un lotto scaduto con offerta 10 di Marco, **When** il banditore riapre da capo, **Then** le offerte si azzerano, il countdown riparte, il buzzer si riattiva per tutti.
3. **Given** un lotto scaduto con offerta 10 di Marco, **When** il banditore riapre mantenendo, **Then** l'offerta 10 di Marco resta, il countdown riparte, il buzzer si riattiva.
4. **Given** un lotto aperto senza offerte, **When** il countdown scade, **Then** il calciatore torna libero e il banditore puo cercarlo e rimetterlo all'asta in futuro.

---

### User Story 5 - Pausa e annullamento del lotto (Priority: P2)

Il banditore puo mettere in pausa un lotto aperto: il countdown si ferma, il buzzer si disattiva sui telefoni ("lotto in pausa"), le offerte inviate durante la pausa vengono rifiutate con motivo "lotto in pausa". Il banditore riprende quando vuole. Puo anche annullare il lotto: il calciatore torna libero, nessuna aggiudicazione avviene.

**Why this priority**: La pausa e utile per gestire imprevisti durante la serata. Non blocca il flusso principale ma migliora il controllo.

**Independent Test**: Aprire un lotto, metterlo in pausa, tentare un'offerta (rifiutata), riprendere, e verificare che il lotto continui normalmente.

**Acceptance Scenarios**:

1. **Given** un lotto aperto con countdown a 15 secondi, **When** il banditore mette in pausa, **Then** il countdown si ferma, i telefoni mostrano "lotto in pausa", le offerte vengono rifiutate.
2. **Given** un lotto in pausa, **When** il banditore riprende, **Then** il countdown riparte dal valore a cui era stato fermato, il buzzer si riattiva.
3. **Given** un lotto aperto con offerte, **When** il banditore annulla il lotto, **Then** il calciatore torna libero, nessun credito viene scalato, nessuna assegnazione avviene.

---

### User Story 6 - Visualizzazione rose e crediti (Priority: P2)

La console mostra una tabella di tutti i partecipanti con: crediti residui e rosa raggruppata per ruolo (P, D, C, A). Mostra anche il totale dei crediti ancora in circolazione nella lega. Sul telefono, il partecipante vede i propri crediti residui e la propria rosa raggruppata per ruolo.

**Why this priority**: La visibilita dello stato finanziario guida le decisioni di rilancio. Non e necessaria per far funzionare il lotto, ma e essenziale per una serata informata.

**Independent Test**: Aggiudicare un calciatore e verificare che crediti e rosa si aggiornino sia sulla console che sul telefono del partecipante coinvolto.

**Acceptance Scenarios**:

1. **Given** un'asta in corso con Marco che ha 490 crediti e 1 attaccante in rosa, **When** Marco apre il suo telefono, **Then** vede "Crediti: 490" e sotto "Attaccanti: [nome calciatore]".
2. **Given** la stessa situazione, **When** il banditore guarda la console, **Then** vede nella tabella la riga di Marco con 490 crediti e la composizione della rosa per ruolo.
3. **Given** 8 partecipanti con 500 crediti ciascuno e nessuna aggiudicazione, **When** il banditore guarda la console, **Then** il totale crediti in circolazione mostra 4000.

---

### User Story 7 - Ricerca calciatori liberi dal telefono (Priority: P2)

Il partecipante puo cercare tra i calciatori ancora liberi, filtrando per nome e per ruolo. I calciatori gia assegnati non compaiono nei risultati. I calciatori marcati "fuori lista" non compaiono tra i liberi.

**Why this priority**: Permette ai partecipanti di pianificare le proprie offerte. Non blocca il flusso principale.

**Independent Test**: Con un'asta in corso e alcuni calciatori gia assegnati, cercare un nome e verificare che appaiano solo quelli ancora liberi.

**Acceptance Scenarios**:

1. **Given** un'asta con "Malen" gia aggiudicato, **When** un partecipante cerca "Malen", **Then** non appare nei risultati.
2. **Given** un'asta in corso, **When** un partecipante filtra per ruolo "A", **Then** vede solo gli attaccanti ancora liberi.
3. **Given** un calciatore con "Fuori lista" = *, **When** un partecipante lo cerca, **Then** non appare tra i calciatori liberi.

---

### User Story 8 - Ricerca e apertura calciatore dalla console (Priority: P1)

Il banditore cerca un calciatore tra quelli ancora liberi dalla console e lo apre come lotto. La ricerca funziona per nome e per ruolo. Solo i calciatori liberi (non assegnati e non fuori lista) possono essere aperti.

**Why this priority**: Il banditore deve poter trovare e selezionare il prossimo calciatore rapidamente per mantenere il ritmo della serata.

**Independent Test**: Dalla console, cercare un calciatore per nome, aprirlo come lotto, e verificare che il lotto sia visibile su tutti i telefoni.

**Acceptance Scenarios**:

1. **Given** un'asta in corso, **When** il banditore cerca "Thuram" e lo seleziona, **Then** si apre un nuovo lotto per Thuram, visibile su tutti i telefoni.
2. **Given** un calciatore gia aggiudicato, **When** il banditore lo cerca, **Then** non appare tra i risultati liberi.

---

### Edge Cases

- **Offerta che eccede i crediti residui**: il partecipante tenta di offrire piu di quanto possiede. L'offerta viene rifiutata con motivo "crediti insufficienti".
- **Offerta su lotto gia chiuso**: un partecipante invia un'offerta che si riferisce a un lotto ormai chiuso mentre ne e gia aperto un altro. L'offerta viene rifiutata e non interferisce con il lotto in corso.
- **Disconnessione e riconnessione del telefono**: un partecipante perde la connessione a meta serata. Quando si riconnette, il telefono si riallinea automaticamente con lo stato corrente dell'asta senza intervento manuale.
- **Due dispositivi con lo stesso codice partecipante**: un partecipante si collega da due telefoni con lo stesso codice. Entrambi funzionano come telecomandi indipendenti: vedono lo stesso stato, entrambi possono fare offerte.
- **Riavvio del server a meta asta**: il server si spegne e viene riavviato. Lo stato dell'asta si ricostruisce e, se un lotto era in corso, riparte in pausa. Il banditore decide se riprendere o annullare.
- **Import xlsx con formato inatteso**: il file non ha le colonne attese, o ha intestazioni diverse. Il sistema mostra un errore leggibile che indica cosa non corrisponde (colonna mancante, valore inatteso) e non importa nulla. Nessun import parziale.
- **Offerta durante la pausa**: l'offerta viene rifiutata con motivo "lotto in pausa".
- **Banditore che partecipa all'asta**: alla creazione, si puo configurare se il banditore e anche partecipante. Se si, partecipa dal proprio telefono con il proprio codice, come qualsiasi altro partecipante. La console resta dedicata alla conduzione.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Il sistema DEVE importare il primo foglio di un file xlsx con intestazioni `#`, `Nome`, `Fuori lista`, `Sq.`, `Under`, `R.`, `R.MANTRA`, `PGv`, `MV`, `FM`, `FVM/1000`, `QUOT.`, `FantaSquadra`, `Costo` sulla riga 1. L'identificazione delle colonne avviene per nome di intestazione, non per posizione.
- **FR-002**: Il sistema DEVE determinare automaticamente il tipo di asta (iniziale o riparazione) in base al contenuto della colonna `FantaSquadra`.
- **FR-003**: Il sistema DEVE rifiutare l'import intero se il file ha formato inatteso, mostrando un errore leggibile con indicazione della colonna o riga problematica. Nessun import parziale.
- **FR-004**: Il sistema DEVE permettere al banditore di inserire i nomi dei partecipanti e i crediti iniziali (uguali per tutti) per l'asta iniziale.
- **FR-005**: Il sistema DEVE permettere al banditore di inserire i crediti residui di ciascun partecipante per l'asta di riparazione.
- **FR-006**: Il sistema DEVE generare un codice univoco per ciascun partecipante e mostrare un QR code sulla console contenente URL (con IP reale della rete) e codice.
- **FR-007**: Il sistema DEVE permettere al banditore di cercare calciatori liberi per nome e ruolo, e di aprire un lotto per un calciatore selezionato.
- **FR-008**: Il sistema DEVE mostrare su tutti i dispositivi: il calciatore in asta, il suo ruolo, la sua squadra, la sua quotazione, l'offerta corrente, chi la detiene, e i secondi residui del countdown.
- **FR-009**: Il sistema DEVE permettere ai partecipanti di inviare offerte tramite: buzzer (rilancio), pulsanti rapidi (+1, +2, +5, +10 sull'offerta corrente) e campo importo libero. Il client invia sempre un importo assoluto.
- **FR-010**: Il sistema DEVE rifiutare un'offerta se: l'importo non e un intero maggiore o uguale a 1, non supera l'offerta corrente, eccede i crediti residui, il lotto non e aperto, il lotto e in pausa, o l'offerta si riferisce a un lotto diverso da quello corrente. Il motivo del rifiuto DEVE essere visibile al partecipante.
- **FR-011**: Il sistema DEVE gestire un countdown per ogni lotto aperto. Ogni offerta accettata fa ripartire il countdown.
- **FR-012**: Alla scadenza del countdown, il sistema DEVE disattivare il buzzer e attendere l'azione del banditore: conferma, riapertura da capo (offerte azzerate), o riapertura mantenendo l'offerta corrente.
- **FR-013**: Alla conferma, il sistema DEVE assegnare il calciatore all'offerente al prezzo offerto, scalare i crediti, e aggiornare la rosa.
- **FR-014**: Il sistema DEVE permettere al banditore di mettere in pausa e riprendere un lotto aperto, e di annullare un lotto (il calciatore torna libero).
- **FR-015**: La console DEVE mostrare una tabella di tutti i partecipanti con crediti residui, rosa raggruppata per ruolo, e il totale dei crediti in circolazione.
- **FR-016**: Il telefono DEVE mostrare il nome del partecipante, i crediti residui, e la rosa raggruppata per ruolo.
- **FR-017**: Il telefono DEVE permettere la ricerca dei calciatori ancora liberi per nome e ruolo. I calciatori assegnati e quelli fuori lista non compaiono tra i risultati.
- **FR-018**: Il sistema DEVE gestire la disconnessione e riconnessione del telefono: al rientro, il dispositivo si riallinea automaticamente con lo stato corrente.
- **FR-019**: Il sistema DEVE supportare piu dispositivi collegati con lo stesso codice partecipante, funzionanti come telecomandi indipendenti.
- **FR-020**: Dopo un riavvio del server, il sistema DEVE ricostruire lo stato dell'asta e far ripartire un eventuale lotto in corso in stato di pausa.
- **FR-021**: Se il countdown scade senza offerte, il calciatore DEVE tornare libero e richiamabile.
- **FR-022**: Alla creazione dell'asta, il sistema DEVE permettere di configurare se il banditore partecipa anche come partecipante.
- **FR-023**: Il telefono DEVE mostrare stati distinti: attesa (nessun lotto aperto), lotto aperto (buzzer attivo), lotto scaduto (buzzer disattivato, "in attesa del banditore"), lotto in pausa (buzzer disattivato), aggiudicato (chi l'ha preso e a quanto).

### Key Entities

- **Asta**: una serata, composta da un listone di calciatori, N partecipanti e una sequenza di lotti. Puo essere "iniziale" o "di riparazione".
- **Calciatore**: presente nel listone. Attributi: identificativo numerico (#), nome, ruolo (P/D/C/A), squadra, quotazione, stato (libero, assegnato, fuori lista).
- **Partecipante**: persona che partecipa all'asta. Attributi: nome, codice univoco, crediti residui, rosa (elenco calciatori assegnati, raggruppati per ruolo).
- **Lotto**: la messa all'asta di un singolo calciatore. Stati: APERTO, IN_PAUSA, SCADUTO, AGGIUDICATO. Attributi: calciatore, offerta corrente, offerente corrente, secondi residui.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una serata con 8 partecipanti e 561 calciatori si svolge dall'import alla fine senza necessita di riavviare o intervenire manualmente sullo stato.
- **SC-002**: Un partecipante riesce a collegarsi inquadrando il QR code in meno di 10 secondi.
- **SC-003**: Un'offerta inviata dal telefono viene visualizzata su tutti i dispositivi entro 1 secondo.
- **SC-004**: Dopo un riavvio del server, l'asta e operativa (in pausa) entro 5 secondi.
- **SC-005**: L'import di un file xlsx con formato corretto completa in meno di 5 secondi per un listone di 600 calciatori.
- **SC-006**: Ogni offerta rifiutata mostra il motivo al partecipante entro 1 secondo.
- **SC-007**: Un telefono disconnesso e riconnesso si riallinea entro 2 secondi senza intervento dell'utente.
- **SC-008**: Il 100% delle offerte rifiutate contiene un messaggio di motivo comprensibile.

## Assumptions

- La rete LAN supporta almeno 10 dispositivi connessi contemporaneamente (1 PC + fino a 9 telefoni).
- I partecipanti usano un browser moderno sui telefoni (supporto per SSE e QR code scanner integrato o da fotocamera).
- Il file xlsx segue il formato di export di Leghe Fantacalcio con le 14 colonne nell'intestazione. Variazioni nel nome del foglio sono tollerate (si legge il primo foglio), ma non variazioni nei nomi delle colonne.
- La durata del countdown e configurabile dal banditore alla creazione dell'asta; il valore predefinito e 30 secondi.
- I crediti e le offerte sono numeri interi positivi.
- Un solo lotto alla volta puo essere aperto.
- Il banditore-partecipante, se configurato, offre dal proprio telefono con il proprio codice, come qualsiasi altro partecipante.

## Out of Scope

- Annullamento di un'aggiudicazione gia confermata.
- Rettifica di prezzo o assegnatario dopo la conferma.
- Rimozione e aggiunta manuale di un calciatore in rosa.
- Svincoli, scambi, vincoli di reparto.
- Export verso Leghe Fantacalcio.
- Gestione del budget di riparazione.
