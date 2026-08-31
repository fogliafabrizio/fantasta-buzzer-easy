package fantasta.eventi;

/**
 * Scadenza del countdown di un lotto: il tempo si e' esaurito e il lotto passa in
 * SCADUTO. Nessuna aggiudicazione automatica: i buzzer si disabilitano e si attende
 * la decisione del banditore (aggiudicare, riaprire o annullare). L'evento e' scritto
 * dal callback del timer del server, dentro lo stesso lock usato dalle offerte.
 */
public class LottoScaduto extends Evento {

    private int idLotto;

    public LottoScaduto() {
    }

    public LottoScaduto(long sequenza, int idLotto) {
        super("LOTTO_SCADUTO", sequenza);
        this.idLotto = idLotto;
    }

    public int getIdLotto() {
        return idLotto;
    }

    public void setIdLotto(int idLotto) {
        this.idLotto = idLotto;
    }
}
