package fantasta.eventi;

import java.nio.file.Path;

/**
 * Una riga del log non e' leggibile come evento: JSON non valido, riga troncata da un
 * crash a meta' scrittura, oppure un tipo di evento che questa versione non conosce.
 * <p>
 * Il replay non prosegue e il server non parte: uno stato ricostruito saltando una riga
 * sarebbe uno stato inventato, e nessuno se ne accorgerebbe fino a quando i crediti non
 * tornano. Un log rotto e' un problema da guardare a mano.
 * <p>
 * Porta con se' file, numero di riga e contenuto perche' chi legge l'errore possa
 * aprire il file a quella riga senza cercare altro: e' LogCorrottoAnalyzer a
 * trasformarli nel blocco leggibile stampato all'avvio.
 */
public class LogCorrottoException extends RuntimeException {

    /** Oltre questa lunghezza il contenuto della riga viene troncato nel messaggio. */
    private static final int MAX_CONTENUTO = 200;

    private final Path file;
    private final int numeroRiga;
    private final String contenuto;
    private final String dettaglio;

    public LogCorrottoException(Path file, int numeroRiga, String contenuto, String dettaglio, Throwable causa) {
        super("Riga " + numeroRiga + " del file " + file + " non leggibile come evento: "
                + primaRiga(dettaglio), causa);
        this.file = file;
        this.numeroRiga = numeroRiga;
        this.contenuto = tronca(contenuto);
        this.dettaglio = primaRiga(dettaglio);
    }

    /**
     * Il dettaglio arriva dalla libreria di parsing e su piu' righe: la prima dice cosa
     * non andava, le successive sono coordinate interne al parser. Nel blocco d'errore
     * si tiene solo la prima, il resto e' rumore per chi sta guardando il file.
     */
    private static String primaRiga(String dettaglio) {
        if (dettaglio == null) {
            return "";
        }
        int aCapo = dettaglio.indexOf('\n');
        return (aCapo < 0 ? dettaglio : dettaglio.substring(0, aCapo)).trim();
    }

    private static String tronca(String riga) {
        if (riga == null) {
            return "";
        }
        if (riga.length() <= MAX_CONTENUTO) {
            return riga;
        }
        return riga.substring(0, MAX_CONTENUTO) + "... (riga troncata qui nel messaggio, nel file continua)";
    }

    public Path getFile() {
        return file;
    }

    public int getNumeroRiga() {
        return numeroRiga;
    }

    public String getContenuto() {
        return contenuto;
    }

    public String getDettaglio() {
        return dettaglio;
    }
}
