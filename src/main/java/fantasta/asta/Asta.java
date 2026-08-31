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

        return new Snapshot(sequenza, null, sp, List.copyOf(calciatoriAssegnati));
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
