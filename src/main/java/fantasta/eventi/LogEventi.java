package fantasta.eventi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LogEventi {

    private static final Logger log = LoggerFactory.getLogger(LogEventi.class);
    private final ObjectMapper mapper;
    private final Path file;

    public LogEventi(Path file) {
        this.file = file;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    public synchronized void append(Evento evento) {
        try {
            String riga = mapper.writeValueAsString(evento);
            try (FileOutputStream fos = new FileOutputStream(file.toFile(), true)) {
                fos.write(riga.getBytes(StandardCharsets.UTF_8));
                fos.write('\n');
                fos.getFD().sync();
            }
            log.info("Evento {} (seq={}) scritto su {}", evento.getTipo(), evento.getSequenza(), file.getFileName());
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Scrittura evento " + evento.getTipo() + " fallita sul file " + file + ": " + e.getMessage(), e);
        }
    }

    public List<Evento> leggiTutti() {
        if (!Files.exists(file)) {
            return List.of();
        }
        List<Evento> eventi = new ArrayList<>();
        int righeVuote = 0;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String riga;
            int numeroRiga = 0;
            while ((riga = reader.readLine()) != null) {
                numeroRiga++;
                if (riga.isBlank()) {
                    righeVuote++;
                    continue;
                }
                try {
                    eventi.add(mapper.readValue(riga, Evento.class));
                } catch (Exception e) {
                    // Nessun salto: la riga rotta ferma il replay e l'avvio.
                    throw new LogCorrottoException(file, numeroRiga, riga, e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Lettura del file log " + file + " fallita: " + e.getMessage(), e);
        }
        log.info("Letti {} eventi dal file {}", eventi.size(), file.getFileName());

        if (righeVuote > 0) {
            log.warn("Ignorate {} righe vuote nel file {}: un log scritto dal server non ne contiene, "
                    + "quindi il file e' stato toccato a mano. Il server parte lo stesso",
                    righeVuote, file.getFileName());
        }
        verificaCoerenzaSequenza(eventi);

        return eventi;
    }

    /**
     * Avviso, non blocco. Ogni evento viene appeso subito dopo aver preso il proprio
     * numero, quindi in un log scritto solo dal server le sequenze vanno da 1 a N senza
     * buchi e l'ultima coincide con il numero di eventi. Uno scarto dice che una riga si
     * e' persa o e' stata duplicata, ma la causa e' ignota: anche una modifica manuale
     * legittima lo produce. Segnalare e ripartire e' meglio che non ripartire in serata
     * per un sospetto.
     * <p>
     * Il conteggio guarda i soli eventi letti dal file: l'eventuale LOTTO_IN_PAUSA che
     * la ricostruzione scrive all'avvio per un lotto rimasto aperto arriva dopo, e
     * confrontarlo qui darebbe un falso allarme a ogni riavvio con un lotto aperto.
     */
    private void verificaCoerenzaSequenza(List<Evento> eventi) {
        if (eventi.isEmpty()) {
            return;
        }
        long ultimaSequenza = eventi.get(eventi.size() - 1).getSequenza();
        if (ultimaSequenza != eventi.size()) {
            log.warn("Sequenza incoerente nel file {}: {} eventi letti ma l'ultimo porta sequenza {}. "
                    + "Manca o si ripete qualche riga. Il server parte lo stesso: vale la pena "
                    + "guardare il file prima di andare avanti",
                    file.getFileName(), eventi.size(), ultimaSequenza);
        }
    }

    public Path getFile() {
        return file;
    }
}
