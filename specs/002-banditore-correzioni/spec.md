# Feature Specification: Correzioni del Banditore e Rifiniture

**Feature Branch**: `002-banditore-correzioni`

**Created**: 2026-09-01

**Status**: Draft

**Input**: Correzioni del banditore dalla sola console (annullamento dell'ultima aggiudicazione, rettifica di prezzo e assegnatario, rimozione da rosa con restituzione decisa, aggiunta manuale in rosa), più tre rifiniture: guardia sui pulsanti rapidi del telefono, impostazioni asta modificabili dalla console, vista avversari collassabile sul telefono.

## Contesto

La feature 1 (asta funzionante) è completa e verificata: import del listone, creazione
asta, apertura lotti, buzzer, countdown, conferma e riapertura, proiezioni di crediti e
rose, ricerca calciatori dal telefono. Nulla di tutto ciò viene rispecificato qui.

Questa feature aggiunge la capacità del banditore di **correggere in avanti** gli errori
della serata — un prezzo battuto male, un nome sbagliato, un'aggiudicazione data alla
persona sbagliata — senza mai riscrivere o cancellare il passato. Ogni correzione è un
nuovo evento appeso in fondo al log; rose e crediti restano proiezioni ricalcolate.

## User Scenarios & Testing *(mandatory)*

Le storie sono ordinate secondo l'ordine di implementazione obbligatorio richiesto: si
implementano dall'alto verso il basso e, se il tempo finisce, finisce dal fondo.

### User Story 1 - Guardia sui pulsanti rapidi (Priority: P1)

Durante un lotto molto contestato lo snapshot cambia continuamente. Un partecipante vede
sul telefono l'offerta corrente a 20 e tocca il pulsante rapido `+2` intendendo offrire
22; nel frattempo un altro partecipante ha portato l'offerta a 40 e lo snapshot arriva un
istante prima del tap. Senza guardia il partecipante offrirebbe 42 senza volerlo.

Con la guardia, il telefono che rilancia con un pulsante rapido dichiara anche su quale
offerta corrente stava basando il calcolo (nessuna, se non c'era offerta). Il server
verifica che quella base coincida ancora con l'offerta corrente al momento in cui elabora
la richiesta e, se non coincide, rifiuta con il motivo "l'offerta è cambiata mentre
rilanciavi". Il partecipante vede il nuovo stato e decide di nuovo.

L'importo digitato liberamente non ha guardia: chi scrive 42 intende 42, qualunque sia
l'offerta corrente, e resta valido com'è.

In più, dopo un tap i pulsanti rapidi si disabilitano fino all'arrivo del prossimo
snapshot, così un doppio tap involontario non produce due offerte.

**Why this priority**: È l'unica parte della feature che tocca il buzzer, cioè il momento
in cui la serata può andare storta con conseguenze economiche reali per un partecipante.
Va prima di tutto il resto perché previene gli errori invece di ripararli.

**Independent Test**: Con due telefoni sullo stesso lotto, far arrivare un rilancio da uno
mentre l'altro sta per toccare un pulsante rapido: l'offerta del secondo viene rifiutata
con il motivo corretto e i suoi crediti non si muovono. Testabile e utile da sola.

**Acceptance Scenarios**:

1. **Given** un lotto aperto con offerta corrente 20 di Marco, **When** Luca tocca `+2`
   avendo a schermo l'offerta 20 e nessun'altra offerta arriva prima, **Then** l'offerta
   22 di Luca viene accettata e diventa l'offerta corrente su tutti i dispositivi.
2. **Given** un lotto aperto con offerta corrente 20 a schermo su Luca, **When** l'offerta
   corrente diventa 40 e solo dopo arriva al server il tap `+2` di Luca, **Then** l'offerta
   viene rifiutata e Luca vede il motivo "l'offerta è cambiata mentre rilanciavi";
   l'offerta corrente resta 40.
3. **Given** un lotto aperto senza alcuna offerta, **When** Marco tocca `+5` (base:
   nessuna offerta) e nessuno ha offerto nel frattempo, **Then** l'offerta viene accettata.
4. **Given** un lotto aperto senza alcuna offerta a schermo su Marco, **When** un altro
   partecipante offre 3 e solo dopo arriva il tap di Marco basato su "nessuna offerta",
   **Then** l'offerta di Marco viene rifiutata con lo stesso motivo.
5. **Given** un lotto aperto con offerta corrente 20, **When** Luca digita liberamente 25
   e invia mentre l'offerta corrente è nel frattempo diventata 22, **Then** l'offerta 25
   viene valutata con le sole regole esistenti e accettata: nessuna guardia si applica.
6. **Given** un lotto aperto, **When** il partecipante tocca due volte in rapida
   successione lo stesso pulsante rapido, **Then** parte una sola offerta: i pulsanti
   rapidi restano disabilitati fino al successivo snapshot.

---

### User Story 2 - Annullamento dell'ultima aggiudicazione (Priority: P1)

Il banditore ha confermato un'aggiudicazione per errore — il calciatore sbagliato, o
troppo presto. Dalla console annulla l'ultima aggiudicazione: il calciatore torna libero e
tornabile all'asta, i crediti tornano al partecipante che li aveva pagati. L'operazione è
ripetibile all'indietro più volte di seguito: annullando due volte si tolgono le ultime
due aggiudicazioni, nell'ordine inverso a quello in cui sono avvenute.

**Why this priority**: È la correzione più frequente della serata e quella che il
banditore cerca per prima quando si accorge di un errore.

**Independent Test**: Aggiudicare due calciatori, annullare due volte, e verificare che
entrambi i calciatori siano di nuovo liberi, che i crediti dei partecipanti siano tornati
al valore precedente e che i telefoni mostrino lo stato aggiornato.

**Acceptance Scenarios**:

1. **Given** Marco con 500 crediti che si è aggiudicato Malen a 36 (crediti residui 464),
   **When** il banditore annulla l'ultima aggiudicazione e conferma, **Then** Malen è di
   nuovo libero e cercabile, Marco ha 500 crediti, la rosa di Marco non contiene più Malen
   e tutti i telefoni riflettono il nuovo stato.
2. **Given** tre aggiudicazioni consecutive, **When** il banditore annulla tre volte di
   seguito, **Then** i tre calciatori tornano liberi nell'ordine inverso e i crediti di
   tutti i partecipanti coinvolti tornano ai valori precedenti.
3. **Given** un'asta in cui non è stata ancora confermata nessuna aggiudicazione, **When**
   il banditore cerca di annullare, **Then** l'azione non è disponibile e la console dice
   che non c'è nessuna aggiudicazione da annullare.
4. **Given** un lotto attualmente aperto, scaduto o in pausa, **When** il banditore prova
   ad annullare l'ultima aggiudicazione, **Then** l'azione non è disponibile e la console
   dice che le correzioni sono possibili solo quando non c'è un lotto in corso.
5. **Given** l'annullamento richiesto, **When** appare la richiesta di conferma, **Then**
   dice esattamente cosa cambierà: quale calciatore torna libero, a quale partecipante
   tornano quanti crediti, e quali saranno i suoi crediti residui dopo l'operazione.
6. **Given** un'aggiudicazione annullata, **When** il calciatore viene rimesso all'asta e
   aggiudicato di nuovo, **Then** la nuova aggiudicazione è a tutti gli effetti l'ultima
   ed è a sua volta annullabile.

---

### User Story 3 - Rettifica di un'aggiudicazione (Priority: P2)

Un'aggiudicazione è andata a buon fine ma è registrata male: il prezzo battuto era 36 e
sul foglio ne risultano 63, oppure il calciatore è finito sul partecipante sbagliato, o
entrambe le cose. Il banditore sceglie dalla console il calciatore assegnato e ne
rettifica prezzo, assegnatario, o entrambi in un'unica operazione. I crediti dei
partecipanti coinvolti si ricalcolano di conseguenza: chi cede il calciatore riceve
indietro l'importo che aveva pagato, chi lo riceve paga il nuovo prezzo.

**Why this priority**: Meno frequente dell'annullamento, ma indispensabile quando
l'errore è vecchio di diversi lotti e annullare all'indietro costerebbe troppo.

**Independent Test**: Aggiudicare un calciatore, poi rettificarne prezzo e assegnatario, e
verificare rose e crediti di entrambi i partecipanti coinvolti.

**Acceptance Scenarios**:

1. **Given** Malen assegnato a Marco a 63 per errore (prezzo reale 36), **When** il
   banditore rettifica il prezzo a 36 e conferma, **Then** Malen resta a Marco e Marco
   riceve indietro 27 crediti.
2. **Given** Malen assegnato a Marco a 36, **When** il banditore rettifica l'assegnatario
   a Luca lasciando il prezzo, **Then** Malen è nella rosa di Luca, non più in quella di
   Marco, Marco riceve indietro 36 crediti e Luca ne paga 36.
3. **Given** Malen assegnato a Marco a 63, **When** il banditore rettifica in un'unica
   operazione assegnatario a Luca e prezzo a 36, **Then** Marco riceve indietro 63 crediti, Luca
   ne paga 36 e Malen è nella rosa di Luca.
4. **Given** una rettifica richiesta, **When** appare la richiesta di conferma, **Then**
   dice esattamente cosa cambierà: calciatore, assegnatario prima e dopo, prezzo prima e
   dopo, e i crediti residui risultanti dei partecipanti coinvolti.
5. **Given** un lotto in corso, **When** il banditore prova a rettificare, **Then**
   l'azione non è disponibile con il motivo esplicito.
6. **Given** una rettifica appena applicata, **When** il banditore la ritiene ancora
   sbagliata, **Then** può rettificare di nuovo la stessa assegnazione partendo dai valori
   correnti.

---

### User Story 4 - Rimozione di un calciatore da una rosa (Priority: P2)

Un calciatore deve uscire dalla rosa di un partecipante e l'importo da restituire non è
necessariamente il prezzo pagato: può essere concordato in stanza, può essere zero.
Il banditore sceglie il calciatore assegnato, indica l'importo da restituire e conferma.
Il calciatore torna libero, i crediti indicati tornano al partecipante.

**Why this priority**: Copre gli accordi presi a voce nella stanza, che il software non
conosce e non deve conoscere. Meno frequente della rettifica.

**Independent Test**: Rimuovere un calciatore restituendo un importo diverso dal prezzo
pagato e verificare che rosa, crediti del partecipante e disponibilità del calciatore
siano coerenti.

**Acceptance Scenarios**:

1. **Given** Malen nella rosa di Marco pagato 36, **When** il banditore lo rimuove
   restituendo 20 e conferma, **Then** Malen è libero, Marco ha 20 crediti in più e il
   totale dei crediti in circolazione è diminuito di 16.
2. **Given** Malen nella rosa di Marco pagato 36, **When** il banditore lo rimuove
   restituendo 0, **Then** Malen è libero e i crediti di Marco non cambiano.
3. **Given** una rimozione richiesta, **When** appare la richiesta di conferma, **Then**
   dice quale calciatore esce da quale rosa, quanto viene restituito e quali saranno i
   crediti residui del partecipante dopo l'operazione.
4. **Given** un lotto in corso, **When** il banditore prova a rimuovere, **Then** l'azione
   non è disponibile con il motivo esplicito.

---

### User Story 5 - Aggiunta manuale di un calciatore a una rosa (Priority: P2)

Un calciatore va messo nella rosa di un partecipante senza passare da un lotto: uno
scambio deciso in stanza, un recupero dopo un pasticcio. Il banditore sceglie un
calciatore libero, il partecipante destinatario e il prezzo, e conferma. Il calciatore
entra nella rosa e i crediti si scalano dell'importo indicato.

**Why this priority**: Completa la coppia rimozione/aggiunta, che insieme permettono di
ricostruire qualunque situazione a mano quando le altre correzioni non bastano.

**Independent Test**: Aggiungere manualmente un calciatore libero alla rosa di un
partecipante a un prezzo scelto e verificare rosa, crediti e indisponibilità del
calciatore per i lotti successivi.

**Acceptance Scenarios**:

1. **Given** Malen libero e Marco con 500 crediti, **When** il banditore aggiunge Malen
   alla rosa di Marco a 36 e conferma, **Then** Malen è nella rosa di Marco sotto il ruolo
   A, Marco ha 464 crediti e Malen non è più proponibile come lotto.
2. **Given** un'aggiunta richiesta, **When** appare la richiesta di conferma, **Then** dice
   quale calciatore entra in quale rosa, a quale prezzo, e quali saranno i crediti residui
   del partecipante dopo l'operazione.
3. **Given** Marco con 10 crediti residui, **When** il banditore aggiunge un calciatore a
   36, **Then** la conferma segnala in modo evidente che i crediti risultanti sarebbero
   negativi; se il banditore conferma comunque, l'operazione viene applicata e il valore
   negativo resta visibile in console.
4. **Given** un calciatore già assegnato a qualcuno, **When** il banditore apre l'aggiunta
   manuale, **Then** quel calciatore non è selezionabile.
5. **Given** un lotto in corso, **When** il banditore prova ad aggiungere, **Then**
   l'azione non è disponibile con il motivo esplicito.

---

### User Story 6 - Impostazioni asta modificabili dalla console (Priority: P3)

Il nome dell'asta è stato scritto male, oppure il countdown di 10 secondi si rivela troppo
corto per la stanza. Il banditore modifica nome dell'asta e durata del countdown dalla
console, tra un lotto e l'altro. La nuova durata vale dal lotto successivo: un lotto già
aperto non cambia ritmo a metà.

**Why this priority**: Migliora la serata ma non è necessaria a condurla: il countdown
scelto alla creazione funziona.

**Independent Test**: Modificare nome e durata tra due lotti e verificare che il nome
appaia aggiornato ovunque e che il lotto successivo usi la nuova durata.

**Acceptance Scenarios**:

1. **Given** un'asta senza lotto in corso con countdown 10 secondi, **When** il banditore
   porta la durata a 15 e conferma, **Then** il lotto successivo parte con 15 secondi e i
   telefoni lo mostrano.
2. **Given** un'asta chiamata "Asta 2026", **When** il banditore la rinomina in
   "Asta Lega Bar Sport", **Then** il nuovo nome appare su console e telefoni.
3. **Given** un lotto aperto, scaduto o in pausa, **When** il banditore prova a modificare
   le impostazioni, **Then** l'azione non è disponibile con il motivo esplicito.
4. **Given** una modifica alle impostazioni applicata, **When** il server viene riavviato,
   **Then** lo stato ricostruito usa le impostazioni modificate, non quelle iniziali.
5. **Given** la schermata delle impostazioni, **When** il banditore la apre, **Then** non
   trova partecipanti, codici o crediti: quelli si correggono con le rettifiche.

---

### User Story 7 - Vista avversari sul telefono (Priority: P3)

Prima di rilanciare, un partecipante vuole sapere quanto può ancora spendere chi è in gara
con lui e quanti portieri gli mancano. Sul telefono trova una sezione degli avversari,
chiusa di default, che aprendola mostra per ogni altro partecipante i crediti residui e la
rosa raggruppata per ruolo. Chiusa di default perché lo spazio sullo schermo appartiene al
buzzer.

**Why this priority**: È comodità pura: gli stessi dati sono già visibili sulla console del
banditore ed è l'ultima cosa a cui rinunciare se il tempo finisce.

**Independent Test**: Da un telefono, aprire la sezione avversari dopo alcune
aggiudicazioni e verificare che crediti e rose mostrati coincidano con quelli in console.

**Acceptance Scenarios**:

1. **Given** un partecipante collegato, **When** apre la pagina, **Then** la sezione
   avversari è presente ma chiusa e non riduce lo spazio del buzzer.
2. **Given** la sezione avversari aperta, **When** ci sono 8 partecipanti, **Then** vede i
   7 avversari con crediti residui e rosa raggruppata per ruolo (P, D, C, A), con i nomi
   dei calciatori, e non vede sé stesso duplicato nell'elenco.
3. **Given** la sezione avversari aperta, **When** viene confermata un'aggiudicazione o
   applicata una correzione, **Then** i dati mostrati si aggiornano con il normale
   snapshot, senza che il partecipante debba chiudere e riaprire.

---

### Edge Cases

- **Correzione durante un lotto**: qualunque correzione (annullamento, rettifica,
  rimozione, aggiunta, modifica impostazioni) è rifiutata se esiste un lotto in stato
  APERTO, SCADUTO o IN_PAUSA; il motivo è esplicito in console.
- **Annullamento senza aggiudicazioni**: azione non disponibile, con il motivo.
- **Annullamento oltre il fondo**: dopo aver annullato tutte le aggiudicazioni disponibili,
  un ulteriore annullamento non è possibile. Le assegnazioni iniziali di un'asta di
  riparazione non sono aggiudicazioni e non si annullano: si correggono con rimozione o
  rettifica.
- **Rettifica con lo stesso assegnatario e lo stesso prezzo**: la conferma segnala che
  nulla cambierebbe e l'operazione non viene applicata.
- **Rettifica verso un partecipante con crediti insufficienti**: consentita, con la stessa
  segnalazione evidente prevista per l'aggiunta manuale; decide il banditore.
- **Importi non validi** (negativi, non interi, campo vuoto) in rettifica, rimozione o
  aggiunta: rifiutati prima della conferma, con il motivo.
- **Durata countdown non valida** (zero, negativa, non intera): rifiutata con il motivo.
- **Guardia e regole d'offerta insieme**: se la base dichiarata non corrisponde più,
  l'offerta è rifiutata per quel motivo, anche se sarebbe stata invalida anche per altre
  ragioni; il partecipante riceve il motivo che spiega davvero cosa è successo.
- **Offerta libera senza base dichiarata**: nessuna guardia, comportamento identico a oggi.
- **Telefono che non riceve lo snapshot successivo a un tap**: i pulsanti rapidi restano
  disabilitati; alla riconnessione il primo snapshot li riabilita.
- **Vista avversari con rose vuote**: la sezione mostra comunque tutti gli avversari con
  crediti residui e rosa vuota.

## Requirements *(mandatory)*

### Functional Requirements

**Guardia sui pulsanti rapidi**

- **FR-001**: Il telefono, quando rilancia tramite un pulsante rapido (+1/+2/+5/+10), DEVE
  dichiarare insieme all'offerta l'offerta corrente che aveva a schermo al momento del tap,
  o l'assenza di offerta se non ce n'era.
- **FR-002**: Il server DEVE rifiutare l'offerta se la base dichiarata non coincide con
  l'offerta corrente al momento dell'elaborazione, valutando questa condizione all'interno
  dello stesso punto di serializzazione già usato per le regole d'offerta esistenti.
- **FR-003**: Il motivo del rifiuto DEVE essere "l'offerta è cambiata mentre rilanciavi" ed
  essere mostrato al partecipante che ha rilanciato.
- **FR-004**: Le offerte con importo digitato liberamente NON DEVONO portare alcuna base e
  DEVONO restare valutate esattamente con le regole esistenti.
- **FR-005**: Dopo un tap su un pulsante rapido, il telefono DEVE disabilitare i pulsanti
  rapidi fino all'arrivo del successivo snapshot.

**Correzioni del banditore**

- **FR-006**: Ogni correzione DEVE essere disponibile solo dalla console del banditore.
- **FR-007**: Ogni correzione DEVE essere rifiutata quando esiste un lotto in stato APERTO,
  SCADUTO o IN_PAUSA, con motivo esplicito.
- **FR-008**: Ogni correzione DEVE richiedere una conferma esplicita che descrive cosa
  cambierà: calciatore coinvolto, partecipanti coinvolti, importi prima e dopo, e crediti
  residui risultanti.
- **FR-009**: Ogni correzione DEVE essere registrata come nuovo evento appeso in fondo al
  log, senza mai riscrivere o rimuovere eventi precedenti.
- **FR-010**: Rose, crediti residui e disponibilità dei calciatori DEVONO restare proiezioni
  ricalcolate dal log, comprensive degli eventi di correzione.
- **FR-011**: Il banditore DEVE poter annullare l'ultima aggiudicazione confermata non
  ancora annullata: il calciatore torna libero e proponibile come lotto, i crediti pagati
  tornano al partecipante che li aveva versati.
- **FR-012**: L'annullamento DEVE essere ripetibile all'indietro più volte di seguito,
  procedendo dall'aggiudicazione più recente verso le precedenti.
- **FR-013**: Il banditore DEVE poter rettificare un'assegnazione esistente cambiando
  prezzo, assegnatario, o entrambi in un'unica operazione, con ricalcolo dei crediti di
  tutti i partecipanti coinvolti.
- **FR-014**: Il banditore DEVE poter rimuovere un calciatore da una rosa indicando
  l'importo da restituire, che può essere diverso dal prezzo pagato, incluso zero.
- **FR-015**: Il banditore DEVE poter aggiungere manualmente un calciatore libero alla rosa
  di un partecipante a un prezzo da lui deciso; il calciatore diventa non più proponibile
  come lotto.
- **FR-016**: Le correzioni che porterebbero i crediti residui di un partecipante sotto zero
  DEVONO essere segnalate in modo evidente nella conferma e restare consentite se il
  banditore conferma.
- **FR-016b**: Un partecipante con crediti residui negativi DEVE essere segnalato in modo
  vistoso sia nella tabella dei partecipanti della console sia sul telefono del
  partecipante stesso, senza che nessuno debba provare a rilanciare per accorgersene.
  Il valore negativo è mostrato com'è, mai troncato a zero.
- **FR-017**: Dopo ogni correzione tutti i telefoni DEVONO ricevere il normale snapshot
  completo e riflettere il nuovo stato senza azioni manuali.
- **FR-018**: La console DEVE continuare a mostrare il totale dei crediti in circolazione,
  aggiornato dopo ogni correzione.

**Impostazioni asta modificabili**

- **FR-019**: Il banditore DEVE poter modificare dalla console il nome dell'asta e la durata
  del countdown.
- **FR-020**: La modifica DEVE essere registrata come nuovo evento nel log, senza mai
  riscrivere l'evento di creazione dell'asta.
- **FR-021**: La modifica delle impostazioni DEVE essere consentita solo quando non c'è un
  lotto in corso e la nuova durata DEVE avere effetto a partire dal lotto successivo.
- **FR-022**: Le impostazioni modificabili NON DEVONO includere partecipanti, codici o
  crediti.
- **FR-023**: Dopo un riavvio, lo stato ricostruito DEVE riflettere le impostazioni
  modificate.

**Vista avversari sul telefono**

- **FR-024**: Il telefono DEVE offrire una sezione collassabile, chiusa all'apertura della
  pagina, che mostra per ogni altro partecipante i crediti residui e la rosa raggruppata per
  ruolo (P, D, C, A).
- **FR-025**: La vista avversari DEVE usare esclusivamente i dati già presenti nello
  snapshot, risolvendo i nomi dei calciatori dal listone già scaricato localmente: nessun
  nuovo endpoint e nessun campo aggiuntivo nello snapshot.
- **FR-026**: La vista avversari DEVE aggiornarsi con il normale snapshot mentre è aperta.

### Key Entities

- **Correzione**: un intervento del banditore su assegnazioni e crediti già registrati.
  Esiste in quattro forme — annullamento dell'ultima aggiudicazione, rettifica di
  prezzo e/o assegnatario, rimozione da rosa con importo restituito, aggiunta manuale a
  rosa con prezzo. Ogni forma è un nuovo evento nel log e non modifica gli eventi passati.
- **Impostazioni asta**: nome dell'asta e durata del countdown, modificabili tra un lotto e
  l'altro tramite un evento dedicato.
- **Base dell'offerta**: l'offerta corrente che il telefono aveva a schermo quando è stato
  toccato un pulsante rapido, oppure l'assenza di offerta. Accompagna solo le offerte da
  pulsante rapido e serve unicamente a verificare che il partecipante stia rilanciando su
  ciò che vedeva.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Nessun partecipante può offrire un importo diverso da quello che intendeva a
  causa di uno snapshot cambiato tra ciò che vedeva e il suo tap su un pulsante rapido: in
  quel caso l'offerta è sempre rifiutata con un motivo comprensibile.
- **SC-002**: Un doppio tap involontario su un pulsante rapido produce al massimo una
  offerta.
- **SC-003**: Il banditore corregge un'aggiudicazione sbagliata (annullamento o rettifica)
  in meno di 30 secondi e senza consultare nessuno fuori dalla console.
- **SC-004**: Dopo qualunque sequenza di correzioni, il totale dei crediti in circolazione
  mostrato in console coincide con la somma, su tutti i partecipanti, dei crediti residui
  **più** i prezzi già impegnati nelle rose — non con la somma dei soli crediti residui — e
  rose, crediti e totale coincidono con quelli ricostruiti riavviando il server. La
  rimozione di un calciatore con importo restituito diverso dal prezzo pagato è l'unica
  operazione che sposta questo totale rispetto al valore iniziale, e lo sposta esattamente
  della differenza.
- **SC-005**: Nessuna correzione viene applicata senza una conferma che dichiara in anticipo
  l'effetto: il 100% delle operazioni passa da una schermata di conferma.
- **SC-006**: Dopo ogni correzione, tutti i telefoni collegati mostrano il nuovo stato entro
  il tempo del normale aggiornamento della serata, senza ricaricare la pagina.
- **SC-007**: Il file del log non perde né altera alcuna riga: dopo una serata con
  correzioni, tutti gli eventi originali sono ancora leggibili nello stesso ordine.
- **SC-008**: Con la sezione avversari chiusa, lo spazio dedicato al buzzer sul telefono non
  è inferiore a quello attuale.

## Assumptions

- "Ultima aggiudicazione" significa l'aggiudicazione confermata più recente non ancora
  annullata. Le assegnazioni iniziali di un'asta di riparazione non sono aggiudicazioni e
  restano fuori dall'annullamento: si correggono con rimozione o rettifica.
- Rettifica, rimozione e aggiunta manuale operano su una singola assegnazione scelta dal
  banditore tra i calciatori attualmente assegnati (o liberi, per l'aggiunta) e non hanno
  vincoli di ordine: si può correggere anche un'assegnazione vecchia di molti lotti.
- Il banditore è l'autorità: le correzioni non impongono limiti di regolamento (slot per
  ruolo, capienza della rosa, budget). L'unica difesa è la conferma che mostra l'effetto,
  più la segnalazione evidente quando i crediti risultanti sarebbero negativi.
- Un'aggiudicazione annullata o rettificata resta nel log come evento passato; la console
  non offre uno storico navigabile né un annulla-annulla: per tornare indietro si applica
  una nuova correzione in avanti.
- Gli importi (prezzi e restituzioni) sono interi non negativi, coerentemente con i crediti
  della feature 1.
- Il totale dei crediti in circolazione è già mostrato in console dalla feature 1: qui si
  richiede solo che resti corretto dopo ogni correzione.
- La guardia si applica ai soli pulsanti rapidi. Un client che non dichiara la base viene
  trattato come offerta libera, quindi senza guardia.
- Nessun nuovo canale di trasporto: le correzioni viaggiano sugli stessi meccanismi già in
  uso (invio dal client al server, snapshot completo dal server ai client).
- La vista avversari mostra tutti i partecipanti tranne quello che sta guardando.

## Out of Scope

- Uno storico navigabile delle correzioni.
- Un annulla-annulla (ripristino di una correzione appena applicata).
- Permessi, ruoli o autenticazione per accedere alle correzioni.
- Modifica di partecipanti, codici e crediti dalla schermata delle impostazioni.
- Qualunque nuovo endpoint o campo aggiuntivo nello snapshot per la vista avversari.
