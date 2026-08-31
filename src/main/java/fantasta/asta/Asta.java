package fantasta.asta;

import fantasta.eventi.*;
import fantasta.listone.Calciatore;
import fantasta.listone.ImportListone;
import fantasta.web.Snapshot;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class Asta {

    private static final Logger log = LoggerFactory.getLogger(Asta.class);
    private static final String CARATTERI_CODICE = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int LUNGHEZZA_CODICE = 4;

    @Value("${fantasta.dati.dir}")
    private String directoryDati;

    private final ApplicationEventPublisher eventiSpring;

    // Timer del countdown: un solo thread, un solo task in volo alla volta. Il callback
    // rientra nel lock synchronized(this) usato dalle offerte, cosi' scadenza e rilancio
    // simultanei si serializzano e la scrittura sul log avviene sotto quell'unico lock.
    private final ScheduledExecutorService timerScheduler;
    private ScheduledFuture<?> timerScadenza;

    private String nomeAsta;
    private TipoAsta tipoAsta;
    private int durataCountdown;
    private boolean banditorePartecipa;
    private Map<String, Partecipante> partecipanti;
    private Set<Integer> calciatoriAssegnati;
    private Lotto lottoCorrente;
    private int prossimoIdLotto;
    private long sequenza;
    private List<Calciatore> calciatori;
    private LogEventi logEventi;
    private String fileListone;
    private boolean attiva;

    private ImportListone ultimaAnalisi;
    private String nomeFileListoneSalvato;

    public Asta(ApplicationEventPublisher eventiSpring) {
        this.eventiSpring = eventiSpring;
        this.timerScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "asta-countdown");
            t.setDaemon(true);
            return t;
        });
    }

    @PostConstruct
    public void inizializza() {
        Path dir = Path.of(directoryDati);
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new RuntimeException("Impossibile creare la directory dati " + dir + ": " + e.getMessage(), e);
            }
        }

        Path fileLog = cercaFileLog(dir);
        if (fileLog != null) {
            ricostruisciDaLog(fileLog);
        } else {
            log.info("Nessun file JSONL trovato in {}. Il server parte senza asta", dir);
        }
    }

    private Path cercaFileLog(Path dir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.jsonl")) {
            for (Path p : stream) {
                return p;
            }
        } catch (IOException e) {
            log.warn("Errore nella ricerca di file JSONL in {}: {}", dir, e.getMessage());
        }
        return null;
    }

    private void ricostruisciDaLog(Path fileLog) {
        log.info("Ricostruzione stato dal file {}", fileLog);
        this.logEventi = new LogEventi(fileLog);

        List<Evento> eventi = logEventi.leggiTutti();
        if (eventi.isEmpty()) {
            log.warn("Il file {} e' vuoto, il server parte senza asta", fileLog);
            return;
        }

        Evento primo = eventi.get(0);
        if (!(primo instanceof AstaCreata astaCreata)) {
            throw new RuntimeException("Il primo evento nel file " + fileLog
                    + " non e' ASTA_CREATA ma " + primo.getTipo());
        }

        String nomeXlsx = astaCreata.getFileListone();
        Path fileXlsx = fileLog.getParent().resolve(nomeXlsx);
        if (!Files.exists(fileXlsx) || !Files.isReadable(fileXlsx)) {
            throw new RuntimeException("File listone non trovato: " + nomeXlsx
                    + " (cercato in " + fileXlsx.getParent() + "). "
                    + "Il server non puo' avviarsi senza il file xlsx indicato nell'evento ASTA_CREATA.");
        }

        ImportListone catalogo = ImportListone.leggiCatalogo(fileXlsx);
        this.calciatori = catalogo.getCalciatori();
        log.info("Catalogo caricato: {} calciatori dal file {}", calciatori.size(), nomeXlsx);

        this.partecipanti = new LinkedHashMap<>();
        this.calciatoriAssegnati = new LinkedHashSet<>();
        this.lottoCorrente = null;
        this.prossimoIdLotto = 1;
        this.sequenza = 0;

        for (Evento evento : eventi) {
            applicaEvento(evento);
        }

        // Ricostruzione stato all'avvio (data-model, passi 5-6): il tempo trascorso a
        // server spento non e' recuperabile dal log. Se il lotto e' rimasto APERTO lo si
        // rimette in pausa a tempo pieno scrivendo un LOTTO_IN_PAUSA con
        // secondiResidui = durataCountdown; decide poi il banditore se riprendere o
        // rifare. Nessun countdown viene riavviato e nessun LOTTO_SCADUTO viene scritto
        // in automatico. Se e' gia' IN_PAUSA resta com'e', senza eventi aggiuntivi.
        if (lottoCorrente != null && lottoCorrente.getStato() == StatoLotto.APERTO) {
            long seqPausa = ++this.sequenza;
            LottoInPausa pausaRipristino = new LottoInPausa(seqPausa, lottoCorrente.getIdLotto(), durataCountdown);
            logEventi.append(pausaRipristino);
            applicaEvento(pausaRipristino);
            log.info("Lotto {} rimasto aperto al riavvio: rimesso in pausa a tempo pieno ({} s), "
                    + "il banditore decide se riprendere o annullare",
                    lottoCorrente.getIdLotto(), durataCountdown);
        }

        this.attiva = true;
        this.fileListone = nomeXlsx;
        log.info("Stato ricostruito: asta '{}', {} partecipanti, {} calciatori assegnati, sequenza={}",
                nomeAsta, partecipanti.size(), calciatoriAssegnati.size(), sequenza);
    }

    private void applicaEvento(Evento evento) {
        this.sequenza = evento.getSequenza();

        if (evento instanceof AstaCreata ac) {
            this.nomeAsta = ac.getNomeAsta();
            this.tipoAsta = TipoAsta.valueOf(ac.getTipoAsta());
            this.durataCountdown = ac.getDurataCountdown();
            this.banditorePartecipa = ac.isBanditorePartecipa();

            for (AstaCreata.PartecipanteEvento pe : ac.getPartecipanti()) {
                partecipanti.put(pe.getCodice(),
                        new Partecipante(pe.getNome(), pe.getCodice(), pe.getCrediti()));
            }
        } else if (evento instanceof AssegnazioneIniziale ai) {
            Partecipante p = partecipanti.get(ai.getCodicePartecipante());
            if (p != null) {
                Calciatore c = trovaCalciatore(ai.getIdCalciatore());
                if (c != null) {
                    p.aggiungiCalciatore(c.ruolo(), ai.getIdCalciatore());
                    p.scalaCrediti(ai.getCosto());
                    calciatoriAssegnati.add(ai.getIdCalciatore());
                }
            }
        } else if (evento instanceof LottoAperto la) {
            this.lottoCorrente = new Lotto(la.getIdLotto(), la.getIdCalciatore(), durataCountdown);
            this.lottoCorrente.setIstanteScadenza(la.getIstante().plusSeconds(durataCountdown));
            this.prossimoIdLotto = la.getIdLotto() + 1;
        } else if (evento instanceof OffertaAccettata oa) {
            if (lottoCorrente != null && lottoCorrente.getIdLotto() == oa.getIdLotto()) {
                lottoCorrente.setOffertaCorrente(oa.getImporto());
                lottoCorrente.setOfferenteCorrente(oa.getCodicePartecipante());
                // Un'offerta accettata resetta il countdown: il tempo pieno riparte
                // dall'istante dell'evento (now dal vivo, storico in ricostruzione).
                lottoCorrente.setIstanteScadenza(oa.getIstante().plusSeconds(durataCountdown));
            }
        } else if (evento instanceof LottoScaduto ls) {
            if (lottoCorrente != null && lottoCorrente.getIdLotto() == ls.getIdLotto()) {
                lottoCorrente.setStato(StatoLotto.SCADUTO);
            }
        } else if (evento instanceof LottoInPausa lip) {
            if (lottoCorrente != null && lottoCorrente.getIdLotto() == lip.getIdLotto()) {
                // I secondi residui congelati sono salvati nell'evento: la ricostruzione
                // dal log ottiene lo stesso tempo, indipendente da quando si rilegge.
                lottoCorrente.setSecondiCongelati(lip.getSecondiResidui());
                lottoCorrente.setStato(StatoLotto.IN_PAUSA);
            }
        } else if (evento instanceof LottoRipreso lr) {
            if (lottoCorrente != null && lottoCorrente.getIdLotto() == lr.getIdLotto()) {
                lottoCorrente.setIstanteScadenza(
                        lr.getIstante().plusSeconds(lottoCorrente.getSecondiCongelati()));
                lottoCorrente.setStato(StatoLotto.APERTO);
            }
        } else if (evento instanceof LottoRiaperto lria) {
            if (lottoCorrente != null && lottoCorrente.getIdLotto() == lria.getIdLotto()) {
                if (ModalitaRiapertura.DA_CAPO.name().equals(lria.getModalita())) {
                    lottoCorrente.setOffertaCorrente(null);
                    lottoCorrente.setOfferenteCorrente(null);
                }
                lottoCorrente.setIstanteScadenza(lria.getIstante().plusSeconds(durataCountdown));
                lottoCorrente.setStato(StatoLotto.APERTO);
            }
        } else if (evento instanceof LottoAggiudicato lag) {
            if (lottoCorrente != null && lottoCorrente.getIdLotto() == lag.getIdLotto()) {
                Partecipante vincitore = partecipanti.get(lag.getCodiceVincitore());
                Calciatore c = trovaCalciatore(lag.getIdCalciatore());
                if (vincitore != null && c != null) {
                    vincitore.aggiungiCalciatore(c.ruolo(), lag.getIdCalciatore());
                    vincitore.scalaCrediti(lag.getImporto());
                    calciatoriAssegnati.add(lag.getIdCalciatore());
                }
                // Il lotto non sparisce: resta in lottoCorrente come AGGIUDICATO cosi' la
                // scheda con calciatore, vincitore e importo continua a mostrarsi finche'
                // il banditore non apre il lotto successivo o annulla la scheda.
                lottoCorrente.setStato(StatoLotto.AGGIUDICATO);
            }
        } else if (evento instanceof LottoAnnullato lan) {
            if (lottoCorrente != null && lottoCorrente.getIdLotto() == lan.getIdLotto()) {
                this.lottoCorrente = null;
            }
        }
    }

    public synchronized void creaAsta(String nomeAsta, TipoAsta tipoAsta, int durataCountdown,
                                       boolean banditorePartecipa, String fileListone,
                                       List<Calciatore> calciatori,
                                       List<AstaCreata.PartecipanteEvento> partecipantiEvento,
                                       List<AssegnazioneIniziale> assegnazioniIniziali) {

        String nomeFile = "asta-" + LocalDate.now() + "-"
                + nomeAsta.toLowerCase().replaceAll("[^a-z0-9]+", "-") + ".jsonl";
        Path fileLog = Path.of(directoryDati).resolve(nomeFile);
        this.logEventi = new LogEventi(fileLog);

        this.nomeAsta = nomeAsta;
        this.tipoAsta = tipoAsta;
        this.durataCountdown = durataCountdown;
        this.banditorePartecipa = banditorePartecipa;
        this.fileListone = fileListone;
        this.calciatori = calciatori;
        this.partecipanti = new LinkedHashMap<>();
        this.calciatoriAssegnati = new LinkedHashSet<>();
        this.lottoCorrente = null;
        this.prossimoIdLotto = 1;
        this.sequenza = 0;

        long seq = ++this.sequenza;
        AstaCreata eventoCreazione = new AstaCreata(seq, nomeAsta, tipoAsta.name(),
                durataCountdown, banditorePartecipa, fileListone, partecipantiEvento);
        logEventi.append(eventoCreazione);

        for (AstaCreata.PartecipanteEvento pe : partecipantiEvento) {
            partecipanti.put(pe.getCodice(),
                    new Partecipante(pe.getNome(), pe.getCodice(), pe.getCrediti()));
        }

        for (AssegnazioneIniziale ai : assegnazioniIniziali) {
            ai.setSequenza(++this.sequenza);
            logEventi.append(ai);
            applicaEvento(ai);
        }

        this.attiva = true;
        log.info("Asta '{}' creata con {} partecipanti (tipo={})",
                nomeAsta, partecipanti.size(), tipoAsta);
    }

    public synchronized Snapshot generaSnapshot() {
        if (!attiva) return null;

        List<Snapshot.SnapshotPartecipante> sp = partecipanti.values().stream()
                .map(p -> new Snapshot.SnapshotPartecipante(
                        p.getNome(), p.getCodice(), p.getCrediti(),
                        p.getRosa().entrySet().stream()
                                .collect(Collectors.toMap(Map.Entry::getKey,
                                        e -> List.copyOf(e.getValue())))))
                .toList();

        Snapshot.SnapshotLotto sl = null;
        if (lottoCorrente != null) {
            sl = new Snapshot.SnapshotLotto(
                    lottoCorrente.getIdLotto(),
                    lottoCorrente.getIdCalciatore(),
                    lottoCorrente.getStato(),
                    lottoCorrente.getOffertaCorrente(),
                    lottoCorrente.getOfferenteCorrente(),
                    lottoCorrente.getSecondiResidui());
        }

        return new Snapshot(sequenza, sl, sp, List.copyOf(calciatoriAssegnati));
    }

    /**
     * Apre un lotto per il calciatore indicato. Il banditore puo' aprire un solo
     * lotto alla volta e solo su calciatori liberi (non assegnati, non fuori lista).
     * L'idLotto e' generato dal server in modo progressivo.
     */
    public synchronized Esito apriLotto(int idCalciatore) {
        if (!attiva) {
            return Esito.rifiuto409("Nessuna asta attiva");
        }
        // Un lotto AGGIUDICATO resta a video come scheda dell'esito: aprirne uno nuovo
        // e' proprio il modo con cui lo si sostituisce. Ogni altro stato (APERTO,
        // IN_PAUSA, SCADUTO) e' invece un lotto ancora in corso e blocca l'apertura.
        if (lottoCorrente != null && lottoCorrente.getStato() != StatoLotto.AGGIUDICATO) {
            return Esito.rifiuto409("C'e' gia' un lotto in corso");
        }
        Calciatore c = trovaCalciatore(idCalciatore);
        if (c == null) {
            return Esito.rifiuto400("Calciatore " + idCalciatore + " non presente nel listone");
        }
        if (c.fuoriLista()) {
            return Esito.rifiuto409("Il calciatore " + c.nome() + " e' fuori lista");
        }
        if (calciatoriAssegnati.contains(idCalciatore)) {
            return Esito.rifiuto409("Il calciatore " + c.nome() + " e' gia' stato assegnato");
        }

        long seq = ++this.sequenza;
        int idLotto = this.prossimoIdLotto;
        LottoAperto evento = new LottoAperto(seq, idLotto, idCalciatore);
        logEventi.append(evento);
        applicaEvento(evento);
        programmaScadenza();

        log.info("Lotto {} aperto per il calciatore {} ({}, {}), countdown di {} s avviato",
                idLotto, idCalciatore, c.nome(), c.ruolo(), durataCountdown);
        return Esito.ok();
    }

    /**
     * Valida e registra un'offerta. Validazione, append+fsync sul log e
     * aggiornamento della proiezione avvengono qui, dentro l'unico punto di
     * serializzazione synchronized: due rilanci concorrenti non possono essere
     * accettati entrambi. L'importo arriva grezzo (Number) proprio per poter
     * distinguere un intero valido da un decimale o da un valore assente.
     */
    public synchronized Esito registraOfferta(int idLotto, String codicePartecipante, Number importoGrezzo) {
        if (!attiva) {
            return Esito.rifiuto409("Nessuna asta attiva");
        }
        Partecipante p = partecipanti.get(codicePartecipante);
        if (p == null) {
            return Esito.rifiuto400("Codice partecipante sconosciuto");
        }
        if (lottoCorrente == null) {
            return Esito.rifiuto409("nessun lotto in corso");
        }
        if (lottoCorrente.getIdLotto() != idLotto) {
            return Esito.rifiuto409("lotto non corrispondente");
        }
        switch (lottoCorrente.getStato()) {
            case IN_PAUSA:
                return Esito.rifiuto409("asta in pausa: offerte sospese");
            case SCADUTO:
                return Esito.rifiuto409("tempo scaduto: in attesa del banditore");
            case AGGIUDICATO:
                return Esito.rifiuto409("lotto gia' aggiudicato");
            case APERTO:
            default:
                break;
        }
        if (importoGrezzo == null) {
            return Esito.rifiuto400("importo non valido");
        }
        double importoReale = importoGrezzo.doubleValue();
        if (importoReale < 1 || importoReale != Math.rint(importoReale)) {
            return Esito.rifiuto400("importo non valido");
        }
        int offertaBase = lottoCorrente.getOffertaCorrente() != null ? lottoCorrente.getOffertaCorrente() : 0;
        if (importoReale <= offertaBase) {
            return Esito.rifiuto409("offerta non superiore all'offerta corrente");
        }
        if (importoReale > p.getCrediti()) {
            return Esito.rifiuto409("crediti insufficienti");
        }

        int importo = (int) importoReale;
        long seq = ++this.sequenza;
        OffertaAccettata evento = new OffertaAccettata(seq, idLotto, codicePartecipante, importo);
        logEventi.append(evento);
        applicaEvento(evento);
        // L'offerta accettata ha resettato l'istante di scadenza: si riprogramma il
        // timer sul nuovo tempo pieno.
        programmaScadenza();

        log.info("Offerta accettata: lotto {}, partecipante {} ({}), importo {}, countdown resettato a {} s",
                idLotto, codicePartecipante, p.getNome(), importo, durataCountdown);
        return Esito.ok();
    }

    /**
     * Annulla il lotto in corso rimuovendolo. In APERTO, IN_PAUSA e SCADUTO il
     * calciatore torna libero (nessuna assegnazione era avvenuta): e' anche la via con
     * cui il banditore chiude un lotto scaduto senza offerte. In AGGIUDICATO l'annullo
     * chiude soltanto la scheda dell'esito: l'assegnazione e i crediti gia' scalati
     * restano, perche' registrati dall'evento LOTTO_AGGIUDICATO. L'eventuale timer in
     * volo viene fermato. Opera sul lotto corrente: e' il banditore, uno solo, a
     * comandare, quindi non serve indicare quale lotto.
     */
    public synchronized Esito annullaLotto() {
        if (!attiva) {
            return Esito.rifiuto409("Nessuna asta attiva");
        }
        if (lottoCorrente == null) {
            return Esito.rifiuto409("Nessun lotto in corso");
        }

        int idLotto = lottoCorrente.getIdLotto();
        boolean eraAggiudicato = lottoCorrente.getStato() == StatoLotto.AGGIUDICATO;
        long seq = ++this.sequenza;
        LottoAnnullato evento = new LottoAnnullato(seq, idLotto);
        logEventi.append(evento);
        applicaEvento(evento);
        annullaTimerScadenza();

        if (eraAggiudicato) {
            log.info("Scheda del lotto {} chiusa: l'aggiudicazione resta valida, calciatore assegnato", idLotto);
        } else {
            log.info("Lotto {} annullato, il calciatore torna libero", idLotto);
        }
        return Esito.ok();
    }

    /**
     * Mette in pausa il lotto aperto: il countdown si congela sui secondi residui e le
     * offerte vengono rifiutate finche' il banditore non riprende. Il timer in volo
     * viene fermato cosi' il tempo non scorre.
     */
    public synchronized Esito pausaLotto() {
        if (!attiva) {
            return Esito.rifiuto409("Nessuna asta attiva");
        }
        if (lottoCorrente == null) {
            return Esito.rifiuto409("Nessun lotto in corso");
        }
        if (lottoCorrente.getStato() != StatoLotto.APERTO) {
            return Esito.rifiuto409("il lotto non e' aperto");
        }

        int idLotto = lottoCorrente.getIdLotto();
        // Il tempo autorevole si legge in tempo reale prima di congelarlo nell'evento.
        int residui = lottoCorrente.getSecondiResidui();
        long seq = ++this.sequenza;
        LottoInPausa evento = new LottoInPausa(seq, idLotto, residui);
        logEventi.append(evento);
        applicaEvento(evento);
        annullaTimerScadenza();

        log.info("Lotto {} in pausa, countdown congelato a {} s",
                idLotto, lottoCorrente.getSecondiCongelati());
        return Esito.ok();
    }

    /**
     * Riprende il lotto in pausa: il countdown riparte dai secondi congelati e il timer
     * viene riprogrammato.
     */
    public synchronized Esito riprendiLotto() {
        if (!attiva) {
            return Esito.rifiuto409("Nessuna asta attiva");
        }
        if (lottoCorrente == null) {
            return Esito.rifiuto409("Nessun lotto in corso");
        }
        if (lottoCorrente.getStato() != StatoLotto.IN_PAUSA) {
            return Esito.rifiuto409("il lotto non e' in pausa");
        }

        int idLotto = lottoCorrente.getIdLotto();
        long seq = ++this.sequenza;
        LottoRipreso evento = new LottoRipreso(seq, idLotto);
        logEventi.append(evento);
        applicaEvento(evento);
        programmaScadenza();

        log.info("Lotto {} ripreso, countdown riparte da {} s",
                idLotto, lottoCorrente.getSecondiResidui());
        return Esito.ok();
    }

    /**
     * Conferma l'aggiudicazione del lotto scaduto: assegna il calciatore al vincitore,
     * scala i crediti e porta il lotto in AGGIUDICATO. Ammesso solo su lotto SCADUTO con
     * almeno un'offerta: senza offerte non c'e' nulla da aggiudicare e il banditore usa
     * l'annullamento.
     */
    public synchronized Esito confermaLotto() {
        if (!attiva) {
            return Esito.rifiuto409("Nessuna asta attiva");
        }
        if (lottoCorrente == null) {
            return Esito.rifiuto409("Nessun lotto in corso");
        }
        if (lottoCorrente.getStato() != StatoLotto.SCADUTO) {
            return Esito.rifiuto409("il lotto non e' scaduto");
        }
        if (lottoCorrente.getOffertaCorrente() == null) {
            return Esito.rifiuto409("nessuna offerta da aggiudicare");
        }

        int idLotto = lottoCorrente.getIdLotto();
        int idCalciatore = lottoCorrente.getIdCalciatore();
        String vincitore = lottoCorrente.getOfferenteCorrente();
        int importo = lottoCorrente.getOffertaCorrente();

        long seq = ++this.sequenza;
        LottoAggiudicato evento = new LottoAggiudicato(seq, idLotto, idCalciatore, vincitore, importo);
        logEventi.append(evento);
        applicaEvento(evento);
        annullaTimerScadenza();

        log.info("Lotto {} aggiudicato al partecipante {} per {} (calciatore {})",
                idLotto, vincitore, importo, idCalciatore);
        return Esito.ok();
    }

    /**
     * Riapre il lotto scaduto. DA_CAPO azzera le offerte e riparte a tempo pieno;
     * MANTENENDO tiene l'offerta corrente in piedi e fa ripartire solo il tempo. In
     * entrambi i casi il countdown si riavvia per intero e il timer viene riprogrammato.
     */
    public synchronized Esito riapriLotto(ModalitaRiapertura modalita) {
        if (!attiva) {
            return Esito.rifiuto409("Nessuna asta attiva");
        }
        if (lottoCorrente == null) {
            return Esito.rifiuto409("Nessun lotto in corso");
        }
        if (lottoCorrente.getStato() != StatoLotto.SCADUTO) {
            return Esito.rifiuto409("il lotto non e' scaduto");
        }

        int idLotto = lottoCorrente.getIdLotto();
        long seq = ++this.sequenza;
        LottoRiaperto evento = new LottoRiaperto(seq, idLotto, modalita.name());
        logEventi.append(evento);
        applicaEvento(evento);
        programmaScadenza();

        log.info("Lotto {} riaperto ({}), countdown di {} s riavviato",
                idLotto, modalita, durataCountdown);
        return Esito.ok();
    }

    // --- Timer del countdown ---

    /**
     * (Ri)programma il task di scadenza sull'istante di scadenza del lotto corrente.
     * Chiamato sempre dentro il lock synchronized: cancella l'eventuale task precedente
     * e ne pianifica uno nuovo solo se il lotto e' APERTO. Se il tempo e' gia' scaduto
     * il ritardo e' zero e il task scatta appena possibile.
     */
    private void programmaScadenza() {
        annullaTimerScadenza();
        if (lottoCorrente == null || lottoCorrente.getStato() != StatoLotto.APERTO) {
            return;
        }
        int idLotto = lottoCorrente.getIdLotto();
        long ritardoMs = Duration.between(Instant.now(), lottoCorrente.getIstanteScadenza()).toMillis();
        if (ritardoMs < 0) {
            ritardoMs = 0;
        }
        timerScadenza = timerScheduler.schedule(() -> onScadenza(idLotto), ritardoMs, TimeUnit.MILLISECONDS);
    }

    private void annullaTimerScadenza() {
        if (timerScadenza != null) {
            timerScadenza.cancel(false);
            timerScadenza = null;
        }
    }

    /**
     * Callback del timer. Entra nello stesso lock delle offerte per registrare la
     * scadenza: cosi' scadenza e rilancio simultanei si serializzano e la scrittura sul
     * log avviene sotto l'unico lock. Il broadcast SSE viene lasciato fuori dal lock,
     * pubblicando un evento applicativo Spring che il controller SSE ascolta (evita la
     * dipendenza circolare Asta -> SseController).
     */
    private void onScadenza(int idLotto) {
        if (scadiSeApplicabile(idLotto)) {
            eventiSpring.publishEvent(new LottoScadutoEvent(idLotto));
        }
    }

    private synchronized boolean scadiSeApplicabile(int idLotto) {
        if (lottoCorrente == null
                || lottoCorrente.getIdLotto() != idLotto
                || lottoCorrente.getStato() != StatoLotto.APERTO) {
            return false;
        }
        long seq = ++this.sequenza;
        LottoScaduto evento = new LottoScaduto(seq, idLotto);
        logEventi.append(evento);
        applicaEvento(evento);
        annullaTimerScadenza();

        log.info("Lotto {} scaduto: countdown esaurito, in attesa del banditore", idLotto);
        return true;
    }

    @PreDestroy
    public void spegni() {
        timerScheduler.shutdownNow();
    }

    public synchronized boolean isAttiva() {
        return attiva;
    }

    public synchronized List<Calciatore> getCalciatori() {
        return calciatori;
    }

    public synchronized Partecipante trovaPartecipante(String codice) {
        return partecipanti != null ? partecipanti.get(codice) : null;
    }

    private Calciatore trovaCalciatore(int id) {
        if (calciatori == null) return null;
        for (Calciatore c : calciatori) {
            if (c.id() == id) return c;
        }
        return null;
    }

    public String generaCodice() {
        SecureRandom random = new SecureRandom();
        Set<String> codiciEsistenti = partecipanti != null ? partecipanti.keySet() : Set.of();
        for (int tentativo = 0; tentativo < 1000; tentativo++) {
            StringBuilder sb = new StringBuilder(LUNGHEZZA_CODICE);
            for (int i = 0; i < LUNGHEZZA_CODICE; i++) {
                sb.append(CARATTERI_CODICE.charAt(random.nextInt(CARATTERI_CODICE.length())));
            }
            String codice = sb.toString();
            if (!codiciEsistenti.contains(codice)) {
                return codice;
            }
        }
        throw new RuntimeException("Impossibile generare un codice univoco dopo 1000 tentativi");
    }

    public synchronized void setUltimaAnalisi(ImportListone analisi) {
        this.ultimaAnalisi = analisi;
    }

    public synchronized ImportListone getUltimaAnalisi() {
        return ultimaAnalisi;
    }

    public synchronized void setNomeFileListoneSalvato(String nome) {
        this.nomeFileListoneSalvato = nome;
    }

    public synchronized String getNomeFileListoneSalvato() {
        return nomeFileListoneSalvato;
    }

    public String getDirectoryDati() {
        return directoryDati;
    }
}
