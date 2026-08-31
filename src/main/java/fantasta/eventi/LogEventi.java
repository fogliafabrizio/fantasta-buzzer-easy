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
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String riga;
            int numeroRiga = 0;
            while ((riga = reader.readLine()) != null) {
                numeroRiga++;
                if (riga.isBlank()) continue;
                try {
                    eventi.add(mapper.readValue(riga, Evento.class));
                } catch (Exception e) {
                    throw new RuntimeException(
                            "Errore di parsing alla riga " + numeroRiga + " del file " + file + ": " + e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Lettura del file log " + file + " fallita: " + e.getMessage(), e);
        }
        log.info("Letti {} eventi dal file {}", eventi.size(), file.getFileName());
        return eventi;
    }

    public Path getFile() {
        return file;
    }
}
