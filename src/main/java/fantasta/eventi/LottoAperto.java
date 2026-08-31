package fantasta.eventi;

public class LottoAperto extends Evento {

    private int idLotto;
    private int idCalciatore;

    public LottoAperto() {
    }

    public LottoAperto(long sequenza, int idLotto, int idCalciatore) {
        super("LOTTO_APERTO", sequenza);
        this.idLotto = idLotto;
        this.idCalciatore = idCalciatore;
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
}
