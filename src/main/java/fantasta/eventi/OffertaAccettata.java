package fantasta.eventi;

public class OffertaAccettata extends Evento {

    private int idLotto;
    private String codicePartecipante;
    private int importo;

    public OffertaAccettata() {
    }

    public OffertaAccettata(long sequenza, int idLotto, String codicePartecipante, int importo) {
        super("OFFERTA_ACCETTATA", sequenza);
        this.idLotto = idLotto;
        this.codicePartecipante = codicePartecipante;
        this.importo = importo;
    }

    public int getIdLotto() {
        return idLotto;
    }

    public void setIdLotto(int idLotto) {
        this.idLotto = idLotto;
    }

    public String getCodicePartecipante() {
        return codicePartecipante;
    }

    public void setCodicePartecipante(String codicePartecipante) {
        this.codicePartecipante = codicePartecipante;
    }

    public int getImporto() {
        return importo;
    }

    public void setImporto(int importo) {
        this.importo = importo;
    }
}
