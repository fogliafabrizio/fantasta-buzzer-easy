package fantasta.eventi;

/**
 * Annullamento dell'ultima aggiudicazione annullabile: il calciatore torna libero e i
 * crediti tornano a chi li ha versati. L'evento LOTTO_AGGIUDICATO originale resta dov'e',
 * immutato: si corregge in avanti, appendendo.
 * <p>
 * codicePartecipante e importoRestituito sono letti dalla proiezione <em>corrente</em> al
 * momento del comando, non dal LOTTO_AGGIUDICATO di allora: se nel frattempo
 * un'assegnazione e' stata rettificata, il calciatore sta in un'altra rosa e a un altro
 * prezzo, ed e' quello il credito da restituire. Portarli qui dentro rende l'effetto della
 * riga indipendente da come lo stato e' stato costruito prima di essa: in rilettura la
 * proiezione li applica alla lettera.
 */
public class AggiudicazioneAnnullata extends Evento {

    private int idLotto;
    private int idCalciatore;
    private String codicePartecipante;
    private int importoRestituito;

    public AggiudicazioneAnnullata() {
    }

    public AggiudicazioneAnnullata(long sequenza, int idLotto, int idCalciatore,
                                   String codicePartecipante, int importoRestituito) {
        super("AGGIUDICAZIONE_ANNULLATA", sequenza);
        this.idLotto = idLotto;
        this.idCalciatore = idCalciatore;
        this.codicePartecipante = codicePartecipante;
        this.importoRestituito = importoRestituito;
    }

    public int getIdLotto() {
        return idLotto;
    }

    public void setIdLotto(int idLotto) {
        this.idLotto = idLotto;
    }

    public int getIdCalciatore() {
        return idCalciatore;
    }

    public void setIdCalciatore(int idCalciatore) {
        this.idCalciatore = idCalciatore;
    }

    public String getCodicePartecipante() {
        return codicePartecipante;
    }

    public void setCodicePartecipante(String codicePartecipante) {
        this.codicePartecipante = codicePartecipante;
    }

    public int getImportoRestituito() {
        return importoRestituito;
    }

    public void setImportoRestituito(int importoRestituito) {
        this.importoRestituito = importoRestituito;
    }
}
