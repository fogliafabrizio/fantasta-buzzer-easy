# Research — Asta Funzionante

**Date**: 2026-08-31 | **Spec**: [spec.md](spec.md)

## Dipendenze

### Apache POI (poi-ooxml)

**Decisione**: Apache POI `poi-ooxml` per la lettura del file xlsx.

**Razionale**: è lo standard de facto per leggere/scrivere file Excel in Java. Supporta
il formato `.xlsx` (OOXML) senza conversioni. L'alternativa principale (FastExcel/EasyExcel)
è più leggera ma ha meno garanzie di compatibilità con file prodotti da software terzi.
Il listone viene letto una sola volta all'import, quindi le prestazioni di POI non sono
un problema.

**Alternative considerate**:
- FastExcel: più leggero, ma meno testato su file xlsx prodotti da terze parti.
- JExcelApi: supporta solo `.xls` (BIFF), non `.xlsx`.

### ZXing (core + javase)

**Decisione**: ZXing `core` + `javase` per la generazione dei QR code.

**Razionale**: libreria matura, nessuna dipendenza nativa, genera immagini QR in formato
PNG in memoria. L'output viene servito come risorsa via endpoint GET. Non servono funzioni
di lettura QR (solo generazione).

**Alternative considerate**:
- QRGen: wrapper su ZXing, aggiunge una dipendenza senza valore reale.

### Spring Boot Web

**Decisione**: Spring Boot `spring-boot-starter-web` (include Jackson, Tomcat embedded).

**Razionale**: vincolato dalla costituzione. Nessuna altra dipendenza Spring (no
spring-boot-starter-data, no spring-boot-starter-security, etc.).

SSE è supportato nativamente da Spring MVC tramite `SseEmitter`. Nessuna dipendenza
aggiuntiva necessaria.

## Punti ambigui nella spec (segnalazioni)

### 1. Offerta minima sul primo rilancio

La spec dice che un'offerta viene rifiutata se "non supera l'offerta corrente". Quando il
lotto è appena aperto e non c'è offerta corrente, la spec non indica un importo minimo.

**Possibilità**:
- (A) Qualsiasi importo ≥ 1 è valido.
- (B) La prima offerta deve essere ≥ quotazione del calciatore.

In molte leghe la base d'asta è la quotazione. Ma la spec non lo dice, e la costituzione
vieta di imporre regolamento ("Tutto il resto del regolamento vive in stanza, non nel
software").

**Scelta proposta**: qualsiasi importo ≥ 1 è accettato. Coerente con la spec e con la
costituzione. **Da confermare con l'utente.**

### 2. Formato del codice partecipante

La spec dice "ogni partecipante riceve un codice" e "l'URL resta comunque leggibile e
comunicabile a voce." Non specifica la lunghezza o il formato.

**Scelta proposta**: codice alfanumerico di 4 caratteri maiuscoli (es. `A3K7`),
generato dal server, univoco nell'asta. Abbastanza corto da comunicare a voce,
abbastanza lungo da evitare collisioni su 8-10 partecipanti. **Da confermare.**

### 3. Snapshot iniziale alla connessione SSE

La spec dice che il telefono "si riallinea da solo" alla riconnessione (FR-018). Questo
implica che alla connessione SSE il server invii immediatamente lo snapshot corrente.
Non è esplicitato nella spec ma è l'unico comportamento che soddisfa il requisito.

**Scelta**: alla connessione SSE, il server invia subito lo snapshot corrente. Non
servono conferme, è l'unica implementazione coerente.

### 4. Conteggio crediti e offerte in corso

Quando un partecipante ha un'offerta in corso su un lotto aperto, i suoi crediti residui
nel snapshot devono riflettere l'impegno? Esempio: Marco ha 500 crediti e ha offerto 30
sul lotto corrente. I crediti mostrati sono 500 (effettivi) o 470 (al netto dell'impegno)?

La spec dice che i crediti si scalano alla conferma (FR-013: "scalare i crediti"). Ma la
regola di validazione dice "eccede i crediti residui" — se i crediti mostrati sono 500
e Marco offre 500, l'offerta dovrebbe essere accettata. Ma se poi Marco offre 10 su un
secondo lotto prima della conferma del primo? In questa feature un solo lotto è aperto
alla volta, quindi il problema non si pone: i crediti si scalano solo alla conferma.

**Scelta**: i crediti residui nello snapshot sono quelli effettivi (già scalati per le
aggiudicazioni confermate). Le offerte in corso non li intaccano. La validazione
confronta l'offerta con i crediti effettivi. Un solo lotto alla volta rende questo sicuro.
