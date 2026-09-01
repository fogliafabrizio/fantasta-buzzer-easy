# Quickstart — Verifica manuale delle correzioni

**Date**: 2026-09-01 | **Plan**: [plan.md](plan.md) | **Contratti**: [contracts/endpoints.md](contracts/endpoints.md)

Nessun test automatico (costituzione VII.6): la verifica è manuale e si fa come si fa la
serata. Questi scenari sono ciò che va provato prima di considerare finito un gruppo.

## Prerequisiti

- `mvn -q package` e `java -jar target/fantasta-*.jar`, come per la feature 1.
- Console su `http://localhost:8080/console/`, telefoni su `http://<ip>:8080/telefono/`.
- Un'asta creata con almeno 2 partecipanti da 500 crediti e 2 telefoni collegati
  (o due schede del browser con codici diversi).
- Il file JSONL sotto `fantasta.dati.dir`: va tenuto aperto in un editor, perché **metà di
  queste verifiche si fanno leggendolo**.

## Gruppo 1 — Guardia sui pulsanti rapidi (US1)

| # | Cosa fare | Cosa deve succedere |
|---|-----------|---------------------|
| 1.1 | Lotto aperto, offerta corrente 20 di Marco. Luca tocca `+2` | Offerta 22 accettata, visibile su tutti i dispositivi |
| 1.2 | Con Luca fermo sull'offerta 20 a schermo, far portare l'offerta a 40 da Marco, **poi** far toccare `+2` a Luca | `409` con "l'offerta è cambiata mentre rilanciavi", visibile su Luca. Offerta corrente ancora 40, crediti di Luca fermi |
| 1.3 | Lotto aperto senza offerte, Marco tocca `+5` | Accettata a 5 |
| 1.4 | Lotto senza offerte a schermo su Marco, un altro offre 3, poi arriva il tap di Marco | Rifiutata con lo stesso motivo |
| 1.5 | Offerta corrente 22, Luca digita 25 e invia | Accettata: nessuna guardia sull'importo digitato. Verificare nel log che il POST non portasse `offertaBase` |
| 1.6 | Doppio tap rapido sullo stesso pulsante | Una sola `OFFERTA_ACCETTATA` nel JSONL. I pulsanti restano spenti fino al prossimo snapshot |
| 1.7 | Staccare il wifi del telefono dopo un tap | Pulsanti rapidi spenti; alla riconnessione il primo snapshot li riaccende |

Modo pratico per 1.2 e 1.4: mettere il lotto in pausa non serve — basta un secondo
dispositivo e un po' di coordinazione, oppure un `curl` diretto con `offertaBase`
sbagliato.

## Gruppo 2 — Annullamento (US2)

| # | Cosa fare | Cosa deve succedere |
|---|-----------|---------------------|
| 2.1 | Marco (500) si aggiudica Malen a 36, poi il banditore annulla e conferma | Malen libero e cercabile dal telefono, Marco a 500, Malen fuori dalla sua rosa, telefoni aggiornati senza ricaricare |
| 2.2 | Tre aggiudicazioni di fila, poi tre annullamenti | I tre calciatori tornano liberi in ordine inverso, tutti i crediti tornano ai valori precedenti |
| 2.3 | Asta senza nessuna aggiudicazione confermata | Il pulsante di annullamento non è disponibile; la console dice che non c'è nulla da annullare (`annullabile` è `null` nello snapshot) |
| 2.4 | Con un lotto `APERTO`, poi `SCADUTO`, poi `IN_PAUSA` | L'azione non è disponibile, con il motivo esplicito |
| 2.5 | Subito dopo una conferma, con la scheda `AGGIUDICATO` ancora a video | L'azione **è** disponibile, e annullando la scheda sparisce ([research.md A4](research.md#a4)) |
| 2.6 | Aprire la conferma | Dice quale calciatore torna libero, a chi tornano quanti crediti, e i suoi crediti residui dopo |
| 2.7 | Annullare, rimettere il calciatore all'asta, riaggiudicarlo | La nuova aggiudicazione è l'ultima annullabile |
| 2.8 | Annullare tutto fino a svuotare la pila, poi provare ancora | Non disponibile. Su asta di riparazione: le assegnazioni iniziali **non** compaiono mai come annullabili |
| 2.9 | Leggere il JSONL | Le `LOTTO_AGGIUDICATO` originali sono ancora lì, immutate, con le `AGGIUDICAZIONE_ANNULLATA` in fondo (SC-007) |

## Gruppo 3 — Rettifica, rimozione, aggiunta (US3, US4, US5)

| # | Cosa fare | Cosa deve succedere |
|---|-----------|---------------------|
| 3.1 | Malen a Marco per 63, rettificare il prezzo a 36 | Malen resta a Marco, Marco riceve 27 |
| 3.2 | Malen a Marco per 36, rettificare l'assegnatario a Luca | Malen nella rosa di Luca, Marco +36, Luca −36 |
| 3.3 | Malen a Marco per 63, rettificare in una volta a Luca e 36 | Marco +63, Luca −36, Malen da Luca. **Una sola riga** nel JSONL |
| 3.4 | Rettificare con lo stesso assegnatario e lo stesso prezzo | La console non propone la conferma; forzando la chiamata, `400 "la rettifica non cambierebbe nulla"` |
| 3.5 | Rettificare di nuovo la stessa assegnazione | Parte dai valori correnti e funziona |
| 3.6 | Malen a Marco pagato 36, rimuoverlo restituendo 20 | Malen libero, Marco +20, **`creditiInCircolazione` sceso di 16** |
| 3.7 | Rimuovere restituendo 0 | Calciatore libero, crediti del partecipante invariati, totale sceso del prezzo pagato |
| 3.8 | Aggiungere Malen (libero) a Marco a 36 | Malen in rosa sotto il ruolo A, Marco −36, Malen non più proponibile come lotto |
| 3.9 | Marco con 10 crediti, aggiungere un calciatore a 36 | La conferma segnala in modo evidente il risultato negativo; confermando, l'operazione passa e `−26` resta visibile in console |
| 3.9a | Guardare la tabella partecipanti della console **senza cercare Marco** | La riga di Marco è marcata in modo vistoso, non solo il numero colorato: si nota scorrendo (FR-016b) |
| 3.9b | Guardare il telefono di Marco **prima** che provi a rilanciare | Il rosso è vistoso sulla sua scheda crediti, con scritto a parole che non può rilanciare finché il banditore non sistema |
| 3.9c | Guardare la sezione avversari dal telefono di Luca | Marco appare in rosso con lo stesso stile |
| 3.10 | Dal telefono di Marco in rosso, provare a offrire 1 | Rifiutata con "crediti insufficienti" — motivo corretto ma poco esplicativo, ed è per questo che 3.9a-3.9c devono già averlo detto. **Comportamento previsto**, vedi [research.md](research.md#crediti-di-un-partecipante-sotto-zero-sì-ed-è-voluto) |
| 3.10a | Rimettere Marco in positivo con una rettifica | La segnalazione sparisce ovunque al primo snapshot, e Marco può rilanciare di nuovo |
| 3.11 | Aprire l'aggiunta manuale con un calciatore già assegnato | Non selezionabile; forzando la chiamata, `409` |
| 3.12 | Provare le tre correzioni con un lotto in corso | Non disponibili, con motivo esplicito |
| 3.13 | Importi negativi, decimali o campo vuoto | Rifiutati prima della conferma, con il motivo |
| 3.14 | Dopo una sequenza di annullamento + rettifica del calciatore rettificato | Il calciatore esce dalla rosa di chi lo ha **adesso**, al prezzo che ha **adesso** ([research.md A2](research.md#a2)) |
| 3.14a | Subito dopo una conferma, con la scheda `AGGIUDICATO` a video, rettificare prezzo e assegnatario **di quel** calciatore | La scheda si aggiorna con i valori nuovi, non resta con i vecchi. Aprire un altro lotto e tornare non deve servire a niente perché è già giusta |
| 3.14b | Riavviare il server con la scheda rettificata a video | Dopo il replay la scheda mostra ancora i valori rettificati |
| 3.15 | Rimuovere un calciatore che era stato aggiudicato, poi provare ad annullare | Quell'aggiudicazione **non** è più fra le annullabili |
| 3.15a | Subito dopo una conferma, con la scheda `AGGIUDICATO` a video, rimuovere **quel** calciatore dalla rosa | La scheda **sparisce**, come per l'annullamento: non può restare a dire "vinto da Marco per 36" mentre il calciatore è libero |
| 3.16 | Rettificare il **solo prezzo** lasciando lo stesso assegnatario | Il calciatore resta una volta sola nella rosa, al prezzo nuovo. Se sparisce, l'ordine `rimuovi`/`acquista` è invertito (T025) |

## Gruppo 4 — Impostazioni (US6)

| # | Cosa fare | Cosa deve succedere |
|---|-----------|---------------------|
| 4.1 | Senza lotto in corso, portare il countdown da 10 a 15 | Il lotto successivo parte con 15 secondi, i telefoni lo mostrano |
| 4.2 | Rinominare l'asta | Il nuovo nome appare su console e telefoni |
| 4.3 | Con un lotto `APERTO`, `SCADUTO` o `IN_PAUSA` | L'azione non è disponibile, con motivo esplicito |
| 4.4 | Aprire la schermata impostazioni | Nessun partecipante, nessun codice, nessun credito |
| 4.5 | Durata `0`, negativa o decimale | Rifiutata con il motivo |
| 4.6 | Leggere il JSONL | `ASTA_CREATA` è immutata, con `IMPOSTAZIONI_MODIFICATE` in fondo. Il nome del file non è cambiato |

## Gruppo 5 — Vista avversari (US7)

| # | Cosa fare | Cosa deve succedere |
|---|-----------|---------------------|
| 5.1 | Aprire la pagina del telefono | La sezione avversari è presente ma chiusa, e lo spazio del buzzer non è diminuito (SC-008) |
| 5.2 | Aprirla con 8 partecipanti | 7 avversari, crediti residui e rosa per ruolo P/D/C/A con i nomi dei calciatori, sé stessi esclusi |
| 5.3 | Con la sezione aperta, confermare un'aggiudicazione o applicare una correzione | I dati si aggiornano da soli, senza chiudere e riaprire |
| 5.4 | Aprirla a inizio serata | Tutti gli avversari, con rose vuote e crediti pieni |
| 5.5 | Confrontare con la tabella della console | Numeri identici |

## La verifica che conta più di tutte: il riavvio

Da rifare alla fine di **ogni** gruppo, non solo alla fine.

1. Fare una serie di operazioni che includa almeno: due aggiudicazioni, un annullamento,
   una rettifica di assegnatario e prezzo, una rimozione con restituzione parziale,
   un'aggiunta manuale, e una modifica delle impostazioni.
2. Annotare dalla console: crediti residui di ogni partecipante, rose complete,
   `creditiInCircolazione`, nome asta, durata countdown, e quale aggiudicazione risulta
   annullabile.
3. `Ctrl-C` sul server e riavviarlo.
4. Confrontare, uno per uno, i sei valori annotati. **Devono coincidere tutti**, compreso
   quale aggiudicazione è la prossima annullabile.
5. Verificare che il JSONL abbia lo stesso numero di righe di prima del riavvio, più al
   massimo una `LOTTO_IN_PAUSA` di ripristino se era rimasto un lotto aperto.
6. Verificare che nessuna riga preesistente sia cambiata: `git diff` non serve, basta il
   confronto a occhio delle prime righe e il conteggio (SC-007).

Se il punto 4 fallisce su `creditiInCircolazione` ma non sulle rose, il sospetto è
`rettificaCrediti`: è l'unico stato che non si legge dalle rose.

### Le tre sequenze combinate

Le operazioni singole non bastano: gli errori di proiezione si annidano dove due
correzioni si incrociano sullo stesso calciatore. Ognuna di queste va eseguita, annotata,
e riverificata dopo il riavvio come al punto 4.

| # | Sequenza | Stato atteso, prima e dopo il riavvio |
|---|----------|----------------------------------------|
| R1 | Aggiudicare un calciatore, **annullare**, riaprirlo come lotto, riaggiudicarlo a un altro partecipante | Il calciatore è nella rosa del secondo vincitore al nuovo prezzo; il primo ha i crediti pieni; la prossima annullabile è la **nuova** aggiudicazione, non la vecchia; `creditiInCircolazione` invariato |
| R2 | Aggiudicare a Marco per 36, **rettificare** ad altro assegnatario e prezzo 50, poi **annullare** quell'aggiudicazione | Tutti i crediti tornati ai valori pre-aggiudicazione, calciatore libero, pila vuota, scheda sparita. Il rimborso deve essere **50 a chi lo aveva dopo la rettifica**, non 36 al vincitore originale: se torna 36, l'annullamento sta leggendo il vecchio evento invece della proiezione corrente |
| R3 | Aggiudicare a 36, **rimuovere** restituendo 20, poi **aggiungere a mano** lo stesso calciatore a 30 | Il calciatore è in rosa a 30, `creditiInCircolazione` è sceso di 16 e di 16 soltanto, e la pila resta **vuota**: l'aggiunta manuale non rende annullabile nulla |

Se una di queste, riletta da log freddo, non converge allo stesso stato, il difetto è
quasi certamente in uno dei tre punti che la sola verifica di riavvio non intercetta: il
push sulla pila fuori dalla guardia (T004), l'ordine `rimuovi`/`acquista` (T025), o il
delta calcolato dal campo dell'evento invece che dalla rosa (T036).

### Un caso in cui lo stato dopo il riavvio è diverso, ed è corretto

Non è un difetto ed è l'unico punto in cui "stesso stato prima e dopo" è falso per scelta.
Va conosciuto prima della serata, non scoperto durante.

1. Portare il countdown da 10 a 15 con `IMPOSTAZIONI_MODIFICATE`.
2. Aprire un lotto e lasciarlo scorrere fino a circa 4 secondi residui.
3. Uccidere il server e riavviarlo.

Il lotto riparte `IN_PAUSA` a **15 secondi**, non a 4 e non a 10. I secondi trascorsi non
sono nel log (feature 1, passo 5 della ricostruzione: il tempo si riparte pieno), e il
tempo pieno è ora quello **modificato** — che è appunto ciò che FR-023 chiede. Decide il
banditore se riprendere o rifare il lotto.

## Compatibilità con i log della feature 1

Prendere un JSONL scritto prima di questa feature, metterlo nella directory dati con il
suo xlsx, e avviare il server: deve ricostruire esattamente lo stato di allora, senza
errori di parsing e senza righe ignorate. Nessuna migrazione, nessuna conversione.
