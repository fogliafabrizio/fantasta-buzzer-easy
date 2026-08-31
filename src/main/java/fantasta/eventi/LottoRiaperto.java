package fantasta.eventi;

/**
 * Riapertura di un lotto scaduto decisa dal banditore, in due modalita' distinte:
 * DA_CAPO azzera le offerte e riparte a tempo pieno; MANTENENDO tiene in piedi
 * l'offerta corrente e fa ripartire solo il tempo. In entrambi i casi il countdown
 * riparte per intero dall'istante di questo evento.
 */
public class LottoRiaperto extends Evento {

    private int idLotto;
    private String modalita;

    public LottoRiaperto() {
    }

    public LottoRiaperto(long sequenza, int idLotto, String modalita) {
        super("LOTTO_RIAPERTO", sequenza);
        this.idLotto = idLotto;
        this.modalita = modalita;
    }

    public int getIdLotto() {
        return idLotto;
    }

    public void setIdLotto(int idLotto) {
        this.idLotto = idLotto;
    }

    public String getModalita() {
        return modalita;
    }

    public void setModalita(String modalita) {
        this.modalita = modalita;
    }
}
