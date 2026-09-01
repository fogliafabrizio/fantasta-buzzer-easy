# Research — Correzioni del Banditore e Rifiniture

**Date**: 2026-09-01 | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Nessun NEEDS CLARIFICATION tecnico: linguaggio, dipendenze, trasporto e formato del log
sono fissati dalla feature 1 e dalla costituzione. Quello che va deciso è di dominio, e
sta qui. Le ambiguità della spec sono nella sezione finale, con la decisione presa e il
motivo — nessuna è stata risolta in silenzio.

## Decisioni

### D1 — Identità di un'assegnazione: `idCalciatore`

Un calciatore sta in al più una rosa alla volta (`calciatoriAssegnati` è un `Set`, e
`apriLotto` rifiuta i calciatori già assegnati). Quindi `idCalciatore` **è già** la
chiave univoca di un'assegnazione corrente: rettifica, rimozione e aggiunta manuale
operano su di esso e non serve inventare un id di assegnazione.

**Alternative scartate**: un `idAssegnazione` progressivo (astrazione nuova senza secondo
uso reale, principio V); la coppia `(codicePartecipante, idCalciatore)` (ridondante,
perché il partecipante è derivabile e passarlo aprirebbe il caso "non combaciano").

L'annullamento fa eccezione e usa `idLotto`, perché ciò che annulla è un'aggiudicazione —
un evento — non un'assegnazione corrente. Vedi [D3](#d3--ultima-aggiudicazione-annullabile).

### D2 — Gli eventi di correzione portano i valori che applicano

`LOTTO_AGGIUDICATO` porta già `idCalciatore`, `codiceVincitore` e `importo`
denormalizzati, e la proiezione li applica alla lettera. I nuovi eventi seguono la
stessa regola: il server calcola i valori sotto il lock, li scrive nell'evento, e
`applicaEvento` li applica senza ricalcolare nulla.

**Perché**: rende la rilettura del log banalmente deterministica (l'effetto di una riga
non dipende da come è stato costruito lo stato prima di essa, solo da dove sta) e rende
il log leggibile a mano, che è metà del suo valore in una serata.

**Alternativa scartata**: eventi "magri" (solo `idLotto`, il resto ricalcolato dalla
proiezione). Funzionerebbe, ma sposta la logica dell'effetto dentro `applicaEvento`, cioè
in un metodo che gira sia dal vivo sia in rilettura, moltiplicando i modi in cui i due
percorsi possono divergere.

### D3 — "Ultima aggiudicazione annullabile"

La spec dice "l'aggiudicazione confermata più recente non ancora annullata". Non basta:
non dice cosa succede a un'aggiudicazione il cui calciatore è stato nel frattempo
rettificato o rimosso. Definizione adottata, precisa:

La proiezione tiene una **pila** `annullabili` di record `Aggiudicazione(idLotto,
idCalciatore)`, in ordine di apparizione degli eventi. Quattro regole, tutte applicate in
`applicaEvento` e quindi identiche dal vivo e in rilettura:

1. `LOTTO_AGGIUDICATO` → **impila** `(idLotto, idCalciatore)` in cima.
2. `AGGIUDICAZIONE_ANNULLATA(idLotto)` → **toglie** la voce con quell'`idLotto`.
3. `CALCIATORE_RIMOSSO(idCalciatore)` → **toglie** la voce con quell'`idCalciatore`,
   ovunque si trovi nella pila. Il calciatore è tornato libero: non c'è più
   un'aggiudicazione da disfare, e annullarla creerebbe crediti dal nulla.
4. `ASSEGNAZIONE_RETTIFICATA`, `CALCIATORE_AGGIUNTO`, `ASSEGNAZIONE_INIZIALE`,
   `LOTTO_ANNULLATO` → **non toccano la pila**.

**La prossima annullabile è la cima della pila**, e solo quella. Se la pila è vuota
l'azione non è disponibile (US2 scenario 3 e "annullamento oltre il fondo").

Conseguenze volute, tutte coerenti con la spec:

- Annullare *n* volte toglie le ultime *n* aggiudicazioni in ordine inverso (US2 sc. 2).
- Un calciatore annullato, rimesso all'asta e riaggiudicato produce una nuova voce in
  cima: è di nuovo l'ultima annullabile (US2 sc. 6).
- `ASSEGNAZIONE_INIZIALE` non impila mai: le assegnazioni della riparazione non sono
  aggiudicazioni e non si annullano (Assumptions della spec).
- Un'aggiudicazione rettificata **resta** annullabile, ma il suo annullamento agisce sui
  **valori correnti** (chi ha oggi il calciatore, a che prezzo lo ha in rosa), non su
  quelli scritti in `LOTTO_AGGIUDICATO`. Vedi [A2](#a2).

### D4 — Rettifica: un evento solo, non due

`ASSEGNAZIONE_RETTIFICATA` porta insieme nuovo assegnatario e nuovo prezzo, anche quando
uno dei due non cambia. US3 chiede esplicitamente "un'unica operazione" e US3 scenario 3
la esercita su entrambi i campi insieme.

**Alternativa scartata**: `PREZZO_RETTIFICATO` + `ASSEGNATARIO_RETTIFICATO`, come
ipotizzava la sezione "Estensibilità" di [data-model 001](../001-asta-funzionante/data-model.md).
Quella era una nota non vincolante, e due eventi separati renderebbero una rettifica
combinata due righe di log non atomiche: un riavvio in mezzo lascerebbe uno stato che il
banditore non ha mai chiesto.

### D5 — `offertaBase`: `0` significa "nessuna offerta", assente significa "nessuna guardia"

Il POST dell'offerta guadagna un campo opzionale `offertaBase`.

| Valore nel body | Significato |
|-----------------|-------------|
| assente o `null` | Offerta a importo digitato: **nessuna guardia**, regole identiche a oggi (FR-004) |
| intero `0` | Pulsante rapido premuto su un lotto **senza offerta corrente** |
| intero `≥ 1` | Pulsante rapido premuto sull'offerta corrente dichiarata |

`0` è disponibile senza ambiguità perché le offerte valide sono `≥ 1`. Il server confronta
`offertaBase` con `offertaCorrente != null ? offertaCorrente : 0`, che è **già** la
variabile `offertaBase` calcolata in `Asta.java:416` per la regola del rilancio: la
guardia riusa lo stesso valore, non ne introduce un secondo.

**Alternativa scartata**: un booleano `conGuardia` a fianco. Due campi per un'informazione
sola, e uno stato in più da sbagliare (guardia attiva con base assente).

### D6 — Punto esatto della verifica della guardia

Dentro `Asta.registraOfferta`, cioè **dentro l'unico punto di serializzazione già
esistente** (`synchronized` sull'istanza `Asta`, lo stesso lock in cui rientra il callback
di scadenza del timer). Nessun nuovo lock, nessun nuovo metodo sincronizzato.

Posizione precisa nell'ordine dei controlli, oggi in [Asta.java:384-436](../../src/main/java/fantasta/asta/Asta.java#L384-L436):

1. asta attiva → 2. partecipante noto → 3. lotto in corso → 4. `idLotto` corrispondente →
5. stato del lotto (`switch`) → **6. GUARDIA `offertaBase`** ← qui, subito prima della
riga 409 → 7. importo valido → 8. importo superiore all'offerta corrente → 9. crediti
sufficienti → append + fsync + proiezione + riprogrammazione del timer.

Sta **dopo** i controlli 3-5 e **prima** dei controlli 7-9:

- dopo, perché se il lotto non è più quello o non è più aperto, "lotto non
  corrispondente" e "tempo scaduto" spiegano meglio cos'è successo di quanto farebbe la
  guardia;
- prima, perché l'edge case della spec ("Guardia e regole d'offerta insieme") impone che
  quando la base non combacia il partecipante riceva *quel* motivo, anche se l'offerta
  sarebbe stata invalida anche per altre ragioni.

Firma: `registraOfferta(int idLotto, String codicePartecipante, Number importoGrezzo,
Integer offertaBase)`. `offertaBase` arriva grezzo dal controller come già fa
`importoGrezzo`: `null` se assente o non numerico, così il dominio (non il controller)
decide cosa significa.

### D7 — Guardia contro correzione durante un lotto: un solo controllo, non cinque

FR-007 vale per tutte le correzioni. Un metodo privato `correzioniAmmesse()` in `Asta`
restituisce il motivo del rifiuto o `null`, e i cinque metodi di correzione lo chiamano
per primo. Cinque usi reali: non è un'astrazione preventiva.

Blocca `APERTO`, `SCADUTO`, `IN_PAUSA`. **Non** blocca `AGGIUDICATO`: vedi [A3](#a3).

### D8 — L'anteprima di conferma è calcolata in console, senza endpoint di anteprima

FR-008 chiede che la conferma dichiari in anticipo calciatore, partecipanti, importi prima
e dopo, e crediti residui risultanti. Tutti questi dati sono già nello snapshot (rose con
prezzo, crediti, `annullabile`) e nel listone già scaricato: la console li compone da sola.

**Perché non viola il principio III**: la console *mostra* aritmetica su dati che il
server le ha già dato; non decide nulla. Il server resta l'unico che accetta o rifiuta, e
rivalida ogni cosa sotto il lock. È la stessa natura del calcolo `+N` che il telefono già
fa nella feature 1 ("i pulsanti rapidi sono zucchero: il client invia la somma già
calcolata").

**Alternativa scartata**: un endpoint `anteprima-correzione` per forma di correzione.
Quattro endpoint nuovi che non cambiano nulla, un secondo percorso di calcolo da tenere
allineato al primo, e la tentazione di un livello "servizio correzioni" — esattamente ciò
che il principio V vieta.

### D9 — Rettifica e aggiunta manuale restano due endpoint distinti

Hanno lo stesso body (`idCalciatore`, `codicePartecipante`, `prezzo`) ma precondizioni
opposte: la rettifica esige un calciatore **assegnato**, l'aggiunta un calciatore
**libero**. Unirli significherebbe un endpoint che si comporta in due modi a seconda dello
stato, con un motivo di rifiuto ("non è né assegnato né libero") che non esiste.
Restano due, e producono due eventi diversi con effetti diversi sulla proiezione.

### D10 — Vista avversari: nessun campo, nessun endpoint

Lo snapshot porta già, per ogni partecipante, `nome`, `codice`, `crediti` e la rosa
raggruppata per ruolo con `{idCalciatore, prezzo}`. Il telefono ha già il listone in
locale e già risolve i nomi dei calciatori nella propria rosa
([app.js](../../src/main/resources/static/telefono/app.js), funzione `proprietari`). La
sezione avversari è un filtro su `snapshot.partecipanti` che esclude il proprio codice,
ridisegnato al ricevimento di ogni snapshot come le altre viste. FR-025 è soddisfatto
senza scriverci nulla di nuovo.

## Ambiguità della spec, e come sono state risolte

Nessuna di queste è stata decisa in silenzio: sono i punti in cui la spec non dice, e la
decisione presa cambia il comportamento osservabile.

### A1

**Un calciatore aggiunto a mano è annullabile?** La spec elenca esplicitamente le
assegnazioni iniziali della riparazione fra ciò che *non* si annulla, ma tace
sull'aggiunta manuale (US5).

**Decisione**: **no**, `CALCIATORE_AGGIUNTO` non impila un annullabile. L'annullamento
disfa un'aggiudicazione — l'esito di un lotto battuto — e un'aggiunta manuale non lo è,
esattamente come non lo è un'assegnazione iniziale. Si disfa con una rimozione,
indicando l'importo da restituire. Se il banditore si aspettasse il contrario, questa è
la riga da cambiare.

### A2

**Un'aggiudicazione rettificata è ancora annullabile? E annullandola, si restituisce il
prezzo originale o quello rettificato?** La spec non incrocia mai US2 e US3.

**Decisione**: resta annullabile, e l'annullamento agisce sui **valori correnti**: toglie
il calciatore dalla rosa di chi lo ha *adesso* e restituisce il prezzo con cui è *adesso*
in rosa. L'evento `AGGIUDICAZIONE_ANNULLATA` scrive quei valori correnti, non quelli di
`LOTTO_AGGIUDICATO`.

**Perché**: restituire il prezzo originale a chi non lo ha mai pagato creerebbe crediti
dal nulla e ne distruggerebbe altrove — l'unica cosa che questa feature non deve poter
fare per errore. Con i valori correnti l'invariante è preservato per costruzione.

Il caso simmetrico (calciatore **rimosso** e poi annullamento della sua aggiudicazione) è
invece impossibile per la regola 3 di [D3](#d3--ultima-aggiudicazione-annullabile): la
voce esce dalla pila.

### A3

**Le correzioni sono possibili con la scheda di un lotto AGGIUDICATO ancora a video?** La
spec (US2 sc. 4, US3 sc. 5, US4 sc. 4, US5 sc. 5, edge case "Correzione durante un lotto")
elenca sempre e solo `APERTO`, `SCADUTO`, `IN_PAUSA`. `AGGIUDICATO` non compare.

**Decisione**: **sì, consentite**. È il momento in cui il banditore si accorge
dell'errore: bloccarlo lì lo costringerebbe ad aprire un altro lotto per poter correggere
il precedente. Coerente con la feature 1, dove un lotto `AGGIUDICATO` non è "un lotto in
corso" ma solo la scheda dell'esito, e infatti non blocca nemmeno `apriLotto`
([Asta.java:351](../../src/main/java/fantasta/asta/Asta.java#L351)).

### A4

**Che ne è della scheda a video se si annulla proprio quel lotto?** Nessuna riga della
spec lo dice.

**Decisione**, in due parti:

- `AGGIUDICAZIONE_ANNULLATA` **azzera** `lottoCorrente` se e solo se è il lotto mostrato
  (`lottoCorrente.idLotto == idLotto`). Lasciare a video una scheda "vinto da Marco per
  36" appena annullata sarebbe la peggiore delle bugie sullo schermo del banditore.
- `ASSEGNAZIONE_RETTIFICATA` **aggiorna** la scheda se riguarda il calciatore mostrato
  (`lottoCorrente.stato == AGGIUDICATO && lottoCorrente.idCalciatore == idCalciatore`):
  `offerenteCorrente = codicePartecipanteNuovo` e `offertaCorrente = prezzoNuovo`. La
  scheda continua a dire chi ha vinto e a quanto, e dopo la rettifica quei due valori sono
  cambiati: mostrarli vecchi sarebbe la stessa bugia dell'annullamento, in forma più
  subdola perché plausibile.

- `CALCIATORE_RIMOSSO` **azzera** la scheda se riguarda il calciatore mostrato
  (`lottoCorrente.stato == AGGIUDICATO && lottoCorrente.idCalciatore == idCalciatore`).
  Una scheda che dice "vinto da Marco per 36" mentre quel calciatore è di nuovo libero è
  la stessa bugia dell'annullamento, con un comando diverso.

`CALCIATORE_AGGIUNTO` non può riguardare la scheda: agisce su calciatori liberi, e quello
della scheda è assegnato. Nessun caso resta scoperto.

La regola che ne esce è unica e vale la pena enunciarla così: **la scheda a video deve
dire la verità corrente sul suo calciatore, o sparire.** Se dopo una correzione i suoi due
valori sono ancora veri, resta; se sono cambiati, si aggiorna; se il calciatore non è più
assegnato, sparisce.

### A5

**Il nome dell'asta non è oggi nello snapshot.** US6 scenario 2 pretende che il nuovo nome
"appaia su console e telefoni", ma `Snapshot` non lo porta
([Snapshot.java:16-22](../../src/main/java/fantasta/web/Snapshot.java#L16-L22)) e non
esiste un endpoint che lo esponga. Out of Scope vieta campi nuovi nello snapshot **per la
vista avversari**, non in generale.

**Decisione**: lo snapshot guadagna `nomeAsta` e `durataCountdown`. Da questa feature sono
stato *mutevole*, e il principio IV dice che lo snapshot porta lo stato mutevole completo.
`durataCountdown` serve anche a precompilare la schermata impostazioni senza inventare un
GET.

### A6

**"La nuova durata vale dal lotto successivo" va imposta?** No: è già garantita, perché le
impostazioni si possono cambiare solo senza lotto in corso (FR-021). Nessun controllo
aggiuntivo. Va però scritto — ed è in [data-model.md](data-model.md) — che in rilettura
`LOTTO_APERTO`, `OFFERTA_ACCETTATA` e `LOTTO_RIAPERTO` usano la `durataCountdown` **in
vigore a quel punto del log**, che è esattamente ciò che accadeva dal vivo.

### A7

**Rettifica che non cambia nulla**: la spec dice che "la conferma segnala che nulla
cambierebbe e l'operazione non viene applicata" — un requisito scritto come se fosse solo
di interfaccia.

**Decisione**: controllo in **entrambi** i posti. La console non propone la conferma; il
server rifiuta comunque con `400` e motivo "la rettifica non cambierebbe nulla". Il server
è l'autorità e non si fida della console (principio III).

### A8

**"Segnalazione evidente" dei crediti negativi (FR-016)**: la spec non dice se il server
debba marcarli.

**Decisione**: **nessun campo nuovo e nessun controllo nel server**. Il server applica e
basta; `crediti` nello snapshot diventa semplicemente negativo, mai troncato a zero. Sono
i client a confrontare con zero — aritmetica di visualizzazione, come [D8](#d8) — e a
segnalare in tre posti (FR-016, FR-016b):

1. **Conferma in console**, prima di applicare: i crediti risultanti negativi sono
   evidenziati nel testo della conferma.
2. **Tabella partecipanti in console**, dopo: la riga del partecipante in rosso è
   vistosa — non solo il numero colorato, ma la riga intera marcata, così si vede
   scorrendo la tabella senza cercarla.
3. **Telefono del partecipante interessato**: la propria scheda crediti mostra il valore
   negativo in modo vistoso, con la conseguenza scritta a parole ("non puoi rilanciare
   finché il banditore non sistema i crediti").

Il punto 3 non è cosmetico ed è la ragione per cui FR-016b esiste: senza,
l'unico segnale che arriva al partecipante è "crediti insufficienti" in risposta a
un'offerta da 1, un motivo corretto ma incomprensibile a chi non sa di essere sotto zero.
La causa deve essere visibile **prima** che qualcuno provi a rilanciare, non dopo. La
vista avversari mostra già i negativi degli altri con lo stesso stile, senza codice
dedicato.

## Crediti sotto zero e totale in circolazione — risposta esplicita

Domanda posta al piano: una correzione può portare i crediti di un partecipante sotto zero
o il totale in circolazione fuori dal valore iniziale?

### Crediti di un partecipante sotto zero: **sì, ed è voluto**

| Correzione | Può andare sotto zero? | Perché |
|------------|------------------------|--------|
| Annullamento | **No** | Toglie una voce di rosa e restituisce esattamente il prezzo che era in rosa: i crediti residui possono solo salire. |
| Rimozione | **No** | Toglie la voce (+prezzo) e applica `restituito − prezzo`: netto `+restituito`, con `restituito ≥ 0`. I crediti possono solo salire o restare uguali. |
| **Rettifica** | **Sì** | Il nuovo assegnatario paga il nuovo prezzo: se non ha abbastanza residuo va negativo. Esplicitamente ammesso dall'edge case "Rettifica verso un partecipante con crediti insufficienti". Vale anche a parità di assegnatario con prezzo al rialzo. |
| **Aggiunta manuale** | **Sì** | US5 scenario 3 lo richiede alla lettera: Marco con 10 crediti, calciatore aggiunto a 36. |

**Cosa succede quando accade**: l'operazione viene applicata (il banditore è l'autorità,
FR-016), l'evento è scritto, `crediti` nello snapshot è un intero negativo, e la console
lo mostra in evidenza sia nella conferma sia nella tabella dei partecipanti. Nessun
troncamento a zero da nessuna parte: un valore pinzato a zero mentirebbe al banditore
proprio quando ha più bisogno di vedere il rosso.

**Conseguenza a valle che nessuno aveva scritto nella spec**: la regola d'offerta
esistente `importo > crediti residui → "crediti insufficienti"`
([Asta.java:420](../../src/main/java/fantasta/asta/Asta.java#L420)) non cambia. Un
partecipante con crediti negativi **non può più fare alcuna offerta**, nemmeno da 1,
finché il banditore non lo rimette in positivo con un'altra correzione.

Il piano **non** aggiunge un motivo di rifiuto dedicato: sarebbe un caso in più senza un
secondo uso, e arriverebbe comunque troppo tardi. Copre invece la causa a monte con
FR-016b — il rosso è vistoso sulla tabella della console e sul telefono
dell'interessato, prima che qualcuno provi a rilanciare. Vedi [A8](#a8) per i tre punti
in cui è segnalato.

### Totale in circolazione fuori dal valore iniziale: **sì, da una sola correzione**

Il totale è `Σ (crediti residui + prezzi impegnati in rosa)`
([Asta.java:319-325](../../src/main/java/fantasta/asta/Asta.java#L319-L325)), che con
l'accumulatore diventa `Σ (creditiTotali + rettificaCrediti)`.

| Correzione | Effetto sul totale |
|------------|--------------------|
| Annullamento | **Invariato**: i crediti si spostano dalla rosa al residuo dello stesso partecipante. |
| Rettifica (prezzo, assegnatario o entrambi) | **Invariato**: quanto esce dalla rosa di uno rientra nel suo residuo, quanto entra nella rosa dell'altro esce dal suo residuo. |
| Aggiunta manuale | **Invariato**: dal residuo alla rosa, stesso partecipante. Vale anche quando il residuo va negativo. |
| **Rimozione** | **Cambia di `importoRestituito − prezzoPagato`.** Restituendo meno del pagato il totale **scende**; restituendo di più **sale**. |

È l'unico modo di creare o distruggere crediti, ed è **per costruzione**, non per svista:
US4 scenario 1 lo pretende ("il totale dei crediti in circolazione è diminuito di 16") e i
contratti della feature 1 lo avevano già previsto in parole
([endpoints.md](../001-asta-funzionante/contracts/endpoints.md), nota su
`creditiInCircolazione`: "per esempio da un importo restituito a piacere rimuovendo un
calciatore").

**Cosa succede**: nulla viene rifiutato e nulla viene compensato. Il totale in console
diverge dal valore iniziale e resta divergente finché il banditore non lo riporta a posto
con un'altra rimozione o aggiunta. È esattamente il ruolo di spia che quel numero ha: se è
cambiato, qualcuno ha restituito un importo diverso dal prezzo, ed è visibile.

**SC-004 è stato corretto nella spec, non nel comportamento.** Diceva che il totale
coincide con la somma dei crediti residui: non è vero, ed era già falso nella feature 1 —
il totale è residui **più** impegnato in rosa. Il criterio ora dice la proprietà giusta e
verificabile: totale = Σ (residui + impegnato), coincidente con quello ricalcolato al
riavvio, e mosso dal valore iniziale solo dalla rimozione con restituzione diversa dal
prezzo, esattamente della differenza. Nessuna riga di codice cambia per questo.
