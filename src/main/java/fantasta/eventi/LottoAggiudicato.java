package fantasta.eventi;

/**
 * Aggiudicazione di un lotto scaduto: il banditore conferma l'offerta vincente. Il
 * calciatore viene assegnato al vincitore, i crediti gli sono scalati e il lotto si
 * chiude. L'evento porta con se' idCalciatore, vincitore e importo cosi' la proiezione
 * si ricostruisce dal log senza dipendere dallo stato in memoria al momento.
 */
public class LottoAggiudicato extends Evento {

    private int idLotto;
    private int idCalciatore;
    private String codiceVincitore;
    private int importo;

    public LottoAggiudicato() {
    }

    public LottoAggiudicato(long sequenza, int idLotto, int idCalciatore,
                            String codiceVincitore, int importo) {
        super("LOTTO_AGGIUDICATO", sequenza);
        this.idLotto = idLotto;
        this.idCalciatore = idCalciatore;
        this.codiceVincitore = codiceVincitore;
        this.importo = importo;
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

    public String getCodiceVincitore() {
        return codiceVincitore;
    }

    public void setCodiceVincitore(String codiceVincitore) {
        this.codiceVincitore = codiceVincitore;
    }

    public int getImporto() {
        return importo;
    }

    public void setImporto(int importo) {
        this.importo = importo;
    }
}
