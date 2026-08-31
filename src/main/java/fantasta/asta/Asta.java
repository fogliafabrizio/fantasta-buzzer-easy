package fantasta.asta;

import fantasta.eventi.*;
import fantasta.listone.Calciatore;
import fantasta.listone.ImportListone;
import fantasta.web.Snapshot;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class Asta {

    private static final Logger log = LoggerFactory.getLogger(Asta.class);
    private static final String CARATTERI_CODICE = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int LUNGHEZZA_CODICE = 4;

    @Value("${fantasta.dati.dir}")
    private String directoryDati;

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
            this.prossimoIdLotto = la.getIdLotto() + 1;
        } else if (evento instanceof OffertaAccettata oa) {
            if (lottoCorrente != null && lottoCorrente.getIdLotto() == oa.getIdLotto()) {
                lottoCorrente.setOffertaCorrente(oa.getImporto());
                lottoCorrente.setOfferenteCorrente(oa.getCodicePartecipante());
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
        if (lottoCorrente != null) {
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

        log.info("Lotto {} aperto per il calciatore {} ({}, {})",
                idLotto, idCalciatore, c.nome(), c.ruolo());
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
        if (lottoCorrente == null || lottoCorrente.getStato() != StatoLotto.APERTO) {
            return Esito.rifiuto409("lotto non aperto");
        }
        if (lottoCorrente.getIdLotto() != idLotto) {
            return Esito.rifiuto409("lotto non corrispondente");
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

        log.info("Offerta accettata: lotto {}, partecipante {} ({}), importo {}",
                idLotto, codicePartecipante, p.getNome(), importo);
        return Esito.ok();
    }

    /**
     * Annulla il lotto in corso: il calciatore torna libero, nessuna assegnazione
     * avviene. Nel Gruppo 2 il lotto e' sempre APERTO.
     */
    public synchronized Esito annullaLotto(int idLotto) {
        if (!attiva) {
            return Esito.rifiuto409("Nessuna asta attiva");
        }
        if (lottoCorrente == null) {
            return Esito.rifiuto409("Nessun lotto in corso");
        }
        if (lottoCorrente.getIdLotto() != idLotto) {
            return Esito.rifiuto409("lotto non corrispondente");
        }

        long seq = ++this.sequenza;
        LottoAnnullato evento = new LottoAnnullato(seq, idLotto);
        logEventi.append(evento);
        applicaEvento(evento);

        log.info("Lotto {} annullato, il calciatore torna libero", idLotto);
        return Esito.ok();
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
