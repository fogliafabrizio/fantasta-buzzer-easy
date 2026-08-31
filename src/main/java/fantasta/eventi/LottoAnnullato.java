package fantasta.eventi;

/**
 * Annullamento di un lotto: il calciatore torna libero, nessuna assegnazione avviene.
 *
 * Nel Gruppo 2 (senza countdown, scadenza e conferma) un lotto aperto non ha altro
 * modo di chiudersi: l'annullamento e' quindi l'unica via per liberare il calciatore
 * e poterne aprire un altro. La proiezione qui gestisce solo lo stato APERTO; il
 * Gruppo 3 la estendera' a IN_PAUSA e SCADUTO.
 */
public class LottoAnnullato extends Evento {

    private int idLotto;

    public LottoAnnullato() {
    }

    public LottoAnnullato(long sequenza, int idLotto) {
        super("LOTTO_ANNULLATO", sequenza);
        this.idLotto = idLotto;
    }

    public int getIdLotto() {
        return idLotto;
    }

    public void setIdLotto(int idLotto) {
        this.idLotto = idLotto;
    }
}
