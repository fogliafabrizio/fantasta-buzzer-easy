package fantasta.eventi;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Trasforma un LogCorrottoException nel blocco che Spring stampa al posto dello stack
 * trace quando l'avvio fallisce. Chi legge deve capire dal terminale cosa e' successo e
 * cosa fare, senza aprire il codice: alle nove di sera, con la gente intorno, uno stack
 * trace non e' un messaggio.
 * <p>
 * Registrato in META-INF/spring.factories: senza quella riga Spring non lo trova e
 * torna a stampare la traccia.
 */
public class LogCorrottoAnalyzer extends AbstractFailureAnalyzer<LogCorrottoException> {

    @Override
    protected FailureAnalysis analyze(Throwable failure, LogCorrottoException causa) {
        String descrizione =
                "Il log dell'asta contiene una riga che non e' un evento leggibile, "
                        + "quindi lo stato non puo' essere ricostruito.\n\n"
                        + "  File:     " + causa.getFile() + "\n"
                        + "  Riga:     " + causa.getNumeroRiga() + "\n"
                        + "  Contenuto: " + causa.getContenuto() + "\n"
                        + "  Problema: " + causa.getDettaglio() + "\n\n"
                        + "Le righe precedenti sono state lette senza errori: il problema comincia "
                        + "da questa. Una riga tagliata a meta' e' il segno di un arresto del server "
                        + "durante la scrittura, ed e' quasi sempre l'ultima del file.";

        String azione =
                "Apri il file alla riga " + causa.getNumeroRiga() + " e guarda cosa c'e' scritto.\n"
                        + "Se la riga e' tagliata a meta' ed e' l'ultima del file, l'evento non era stato "
                        + "scritto per intero: togliendola si riparte dallo stato precedente, che e' "
                        + "completo.\n"
                        + "Fai una copia del file prima di modificarlo. Il server non salta le righe "
                        + "rotte da solo: uno stato ricostruito a meta' sarebbe uno stato inventato.";

        return new FailureAnalysis(descrizione, azione, causa);
    }
}
