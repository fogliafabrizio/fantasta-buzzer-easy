# Costituzione — Fanta Asta

**Versione:** 1.0.0 · **Ratificata:** 2026-08-31 · **Ultima modifica:** 2026-08-31

Applicazione locale per gestire la serata d'asta del fantacalcio (modalità Classic).
Gira sul portatile del banditore, in LAN, senza internet. I partecipanti si collegano
dal cellulare. Ogni specifica, piano e implementazione successiva è vincolata da questo
documento. In caso di conflitto tra una spec e la costituzione, vince la costituzione.

---

## I. Lessico e dominio

Il dominio è scritto **in italiano**: classi, campi, eventi, endpoint, etichette UI.

Quattro termini sono obbligatori e non hanno sinonimi:

- **Calciatore** — chi sta nel listone (nome, ruolo P/D/C/A, squadra, quotazione).
- **Partecipante** — la persona con il telefono (nome, codice, crediti, rosa).
- **Lotto** — la messa all'asta di un singolo calciatore.
- **Asta** — la serata: un listone, N partecipanti, una sequenza di lotti.

Mai `Player`, mai `Giocatore`: l'ambiguità calciatore/partecipante è vietata ovunque.

Il lotto vive in una macchina a stati esplicita, pubblicata dal server in ogni snapshot:

`APERTO → SCADUTO → AGGIUDICATO`, più `IN_PAUSA` (raggiungibile da `APERTO`).

Da `SCADUTO` il banditore può confermare (`AGGIUDICATO`) o riaprire (`APERTO`), da capo
o mantenendo l'offerta corrente. Nessun altro percorso esiste. Il client **disegna** lo
stato, non lo deduce.

## II. Il log è lo stato

Lo stato autorevole dell'asta è una **sequenza append-only di eventi** su file JSONL,
un evento per riga, **un file per asta**, nominato con data e nome dell'asta.

- Rose, crediti residui e conteggi per ruolo sono **proiezioni ricalcolate** dal log.
  Non sono mai campi persistiti: se un dato può essere derivato, è derivato.
- Ogni evento è scritto **e forzato su disco prima** che il server risponda al client.
- Si corregge **in avanti**, mai riscrivendo il passato: annullamenti, rettifiche di
  prezzo o assegnatario, rimozioni e aggiunte manuali di calciatori a una rosa sono
  essi stessi eventi appesi in fondo.
- All'avvio lo stato si ricostruisce rileggendo il file dall'inizio.

*Razionale: unica fonte di verità, recovery gratuito, e nessuna possibilità che rosa,
crediti e storia divergano dopo un annullamento.*

## III. Il server è l'unica autorità

Nessuna decisione di dominio nel client. Il telefono non calcola crediti, non valuta
la validità di un'offerta, non chiude lotti, non misura il tempo.

- Le offerte sono **importi assoluti** e portano **l'id del lotto** a cui si riferiscono.
  I pulsanti rapidi (+1/+2/+5/+10) sono zucchero: il client invia la somma già calcolata.
- Il server **rifiuta** un'offerta invalida. Non la corregge, non la arrotonda, non la
  rialza mai per conto del partecipante.
- Il **countdown è autorevole solo sul server**; lo snapshot porta i secondi residui e
  il client li scala localmente solo per l'animazione. Nessuna sincronia di orologi.
- Le uniche regole imposte sono tre: l'offerta supera l'offerta corrente; non eccede i
  crediti residui del partecipante; appartiene al lotto attualmente `APERTO`.

Tutto il resto del regolamento (slot per ruolo, capienza, budget di riparazione) vive
**in stanza**, non nel software. Il banditore può forzare qualunque stato.

## IV. Trasporto e stato condiviso

- **SSE** dal server al client, **POST REST** dal client al server. Nessun altro canale.
- Ogni cambiamento produce uno **snapshot completo dello stato mutevole**, mai un diff:
  lotto corrente, offerta e offerente, stato dei partecipanti, id dei calciatori assegnati.
- Il **listone è una risorsa statica**, scaricata una volta via GET all'apertura del
  client. Non viaggia mai dentro lo snapshot. Ricerca e filtri sono locali al telefono.
- Il **codice partecipante identifica la persona, non una sessione**: più dispositivi
  con lo stesso codice sono ammessi e si comportano da telecomandi indipendenti.
- Nessuna password, nessun ruolo, nessun permesso. La console del banditore è
  raggiungibile solo dal PC.

## V. Struttura e semplicità

- **Un solo modulo Spring Boot**, un solo jar che serve anche i due frontend. Avvio con
  `java -jar`. Nessuna configurazione oltre un `application.properties`.
- Package **per contesto di dominio** (`asta`, `listone`, `eventi`, `web`), mai per
  strato tecnico.
- **Nessuna astrazione senza due usi reali**: niente interfacce a implementazione unica,
  niente factory, niente pattern preventivi, niente livelli di indirezione "per dopo".
- Console banditore in **Angular + PrimeNG**, nessun'altra libreria e nessuno state
  management. Telefono in **HTML/JS statico**, nessun framework, nessuna build.
- Il file JSONL e il file xlsx sono le uniche dipendenze da disco. Nessun database.

## VI. Errori, log e recovery

- I log sono **in italiano** e ogni riga dice: cosa stava succedendo, su quale lotto o
  partecipante, e perché è fallito. Uno stack trace nudo non è un log accettabile.
- **Ogni rifiuto è visibile all'utente con il motivo** ("offerta superata", "crediti
  insufficienti", "lotto già chiuso"). Mai un fallimento silenzioso.
- L'import xlsx **fallisce in modo rumoroso** su formato inatteso, indicando riga e
  colonna. Non tenta correzioni, non indovina, non importa a metà.
- **Riavvio dopo crash**: lo stato si ricostruisce dal log e il lotto in corso riparte
  in `IN_PAUSA`; decide il banditore se riprendere o rifare.
- **Disconnessione del telefono**: il client riconnette da solo e si riallinea con il
  primo snapshot successivo. Nessuno stato locale da riconciliare, nessuna coda di
  offerte in sospeso: un'offerta non arrivata è un'offerta non fatta.

## VII. Divieti

Il progetto non deve **mai**:

1. Fare chiamate a internet a runtime, né dipendere da un servizio esterno.
2. Introdurre autenticazione, password, ruoli o permessi.
3. Mantenere stato mutabile fuori dal log degli eventi.
4. Aggiungere un secondo formato di trasporto oltre SSE + POST (niente WebSocket,
   niente polling).
5. Spostare logica di dominio nel client.
6. Introdurre test automatici, Docker, CI/CD, profili o ambienti multipli.
7. Aggiungere schermate, entità o campi che non servano alla serata dell'asta.

---

## A. Ordine di implementazione obbligatorio

Nessun passo si inizia prima che il precedente funzioni end-to-end:

1. Import xlsx del listone e creazione asta (partecipanti, codici, crediti, countdown).
2. Apertura lotto, buzzer dal telefono, offerte accettate e rifiutate.
3. Countdown, scadenza, conferma o riapertura del banditore, pausa.
4. Proiezioni: crediti residui e rose per ruolo, su console e telefono.
5. Ricerca calciatori ancora liberi dal telefono.
6. Annullamento dell'ultima aggiudicazione, ripetibile all'indietro.
7. Rettifica di prezzo e assegnatario, rimozione e aggiunta manuale in rosa.

Se il tempo finisce, finisce dal fondo: meglio la serata senza i passi 6-7 che tutto
abbozzato.

## B. Checklist di prontezza serata

Non è codice, ma è vincolante quanto il resto:

- Il **QR code** in console contiene l'URL generato **a runtime** dall'indirizzo reale
  dell'interfaccia di rete, mai da configurazione: sotto hotspot l'IP cambia.
- La porta del server è **aperta nel firewall** anche su rete classificata "pubblica".
- Prova completa **su hotspot da telefono** con almeno due dispositivi collegati, prima
  della serata.

---

## Governance

Questa costituzione prevale su ogni altra pratica. Una modifica richiede: la ragione
scritta, l'aggiornamento della versione (MAJOR: rimozione o ridefinizione di un
principio; MINOR: nuovo principio o sezione; PATCH: chiarimento), e la verifica che le
spec già scritte restino conformi. Ogni piano che viola un principio va corretto, non
giustificato.