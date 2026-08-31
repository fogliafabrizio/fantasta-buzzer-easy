package fantasta.asta;

/**
 * Esito di un'operazione validata dentro il punto di serializzazione dell'Asta
 * (apertura lotto, offerta, annullamento). Trasporta il motivo del rifiuto in
 * italiano e l'informazione minima per la mappatura HTTP: {@code badRequest}
 * distingue un input malformato (400) da un conflitto con lo stato corrente (409).
 */
public record Esito(boolean accettata, String motivo, boolean badRequest) {

    public static Esito ok() {
        return new Esito(true, null, false);
    }

    /** Rifiuto per input non valido nel merito (importo non intero, ecc.): 400. */
    public static Esito rifiuto400(String motivo) {
        return new Esito(false, motivo, true);
    }

    /** Rifiuto per conflitto con lo stato dell'asta (lotto non aperto, ecc.): 409. */
    public static Esito rifiuto409(String motivo) {
        return new Esito(false, motivo, false);
    }

    public int statusHttp() {
        return badRequest ? 400 : 409;
    }
}
