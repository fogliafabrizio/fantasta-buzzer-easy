# Quickstart — Asta Funzionante

**Date**: 2026-08-31 | **Contracts**: [contracts/endpoints.md](contracts/endpoints.md)

## Prerequisiti

- JDK 21+
- Node.js 18+ (solo per la build della console Angular, non a runtime)
- Maven 3.9+

## Avvio

```bash
mvn package -DskipTests
java -jar target/fantasta.jar
```

Il server parte su `http://localhost:8080`. La porta è configurabile in
`application.properties` (`server.port`).

## Scenario di validazione end-to-end

### 1. Creazione asta

```bash
curl -X POST http://localhost:8080/api/console/crea-asta \
  -F "file=@lista_calciatori_svincolati_classic_ronchetto-championship.xlsx" \
  -F 'config={"nomeAsta":"Test","durataCountdown":30,"banditorePartecipa":false,"partecipanti":[{"nome":"Marco","crediti":500},{"nome":"Luca","crediti":500}]}'
```

**Risultato atteso**: `201` con body `{"partecipanti":[{"nome":"Marco","codice":"XXXX"},{"nome":"Luca","codice":"YYYY"}]}`.

Un file JSONL viene creato nella directory dati con l'evento ASTA_CREATA.

### 2. Connessione SSE

In un terminale separato:

```bash
curl -N http://localhost:8080/api/sse
```

**Risultato atteso**: riceve immediatamente uno snapshot con lotto null, due partecipanti
con 500 crediti e rose vuote.

### 3. Scarico listone

```bash
curl http://localhost:8080/api/listone | head -c 500
```

**Risultato atteso**: array JSON con i calciatori, ciascuno con id, nome, ruolo, squadra,
quotazione, fuoriLista.

### 4. Apertura lotto

```bash
curl -X POST http://localhost:8080/api/console/apri-lotto \
  -H "Content-Type: application/json" \
  -d '{"idCalciatore":5585}'
```

**Risultato atteso**: `200`. Il terminale SSE riceve uno snapshot con lotto APERTO su
calciatore 5585 (Malen), secondiResidui = 30.

### 5. Offerta

```bash
curl -X POST http://localhost:8080/api/offerta \
  -H "Content-Type: application/json" \
  -d '{"idLotto":1,"codicePartecipante":"XXXX","importo":10}'
```

(Sostituire `XXXX` con il codice di Marco ricevuto al passo 1.)

**Risultato atteso**: `200`. Lo snapshot SSE mostra offertaCorrente=10,
offerenteCorrente=codice di Marco, secondiResidui resettati a 30.

### 6. Offerta rifiutata

```bash
curl -X POST http://localhost:8080/api/offerta \
  -H "Content-Type: application/json" \
  -d '{"idLotto":1,"codicePartecipante":"YYYY","importo":5}'
```

**Risultato atteso**: `400` con `{"motivo":"offerta non superiore all'offerta corrente"}`.

### 7. Scadenza e conferma

Attendere 30 secondi. Lo snapshot SSE mostra stato SCADUTO.

```bash
curl -X POST http://localhost:8080/api/console/conferma
```

**Risultato atteso**: `200`. Lo snapshot mostra lotto null, Marco con crediti 490 e
rosa `{"A":[5585]}`, calciatoriAssegnati=[5585].

### 8. Riavvio e recovery

Terminare il server (Ctrl+C). Riavviare con `java -jar target/fantasta.jar`.

**Risultato atteso**: il server rilegge il file JSONL, ricostruisce lo stato. Una nuova
connessione SSE mostra lo stesso stato di prima della chiusura.

### 9. QR code

Aprire nel browser: `http://localhost:8080/api/qrcode/XXXX`.

**Risultato atteso**: immagine PNG di un QR code che, scansionato, apre
`http://{ip}:8080/telefono/?codice=XXXX`.

### 10. Pagina telefono

Aprire nel browser: `http://localhost:8080/telefono/?codice=XXXX`.

**Risultato atteso**: pagina con nome "Marco", crediti 490, rosa con Malen sotto
Attaccanti, nessun lotto in corso.
