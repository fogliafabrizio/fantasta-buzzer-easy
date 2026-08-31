package fantasta.eventi;

/**
 * Ripresa di un lotto in pausa: il countdown riparte dai secondi congelati al momento
 * della pausa. Il nuovo istante di scadenza si ricava dall'istante di questo evento
 * piu' i secondi residui congelati.
 */
public class LottoRipreso extends Evento {

    private int idLotto;

    public LottoRipreso() {
    }

    public LottoRipreso(long sequenza, int idLotto) {
        super("LOTTO_RIPRESO", sequenza);
        this.idLotto = idLotto;
    }

    public int getIdLotto() {
        return idLotto;
    }

    public void setIdLotto(int idLotto) {
        this.idLotto = idLotto;
    }
}
