package fantasta.eventi;

/**
 * Messa in pausa di un lotto aperto: il countdown viene congelato e le offerte sono
 * rifiutate finche' il banditore non riprende. L'evento salva i {@code secondiResidui}
 * al momento della pausa, cosi' la ripresa da un log storico riparte esattamente da
 * quel tempo. E' anche l'evento con cui, al riavvio dopo un crash, un lotto rimasto
 * APERTO viene rimesso in pausa a tempo pieno ({@code secondiResidui = durataCountdown}):
 * il tempo trascorso a server spento non e' recuperabile.
 */
public class LottoInPausa extends Evento {

    private int idLotto;
    private int secondiResidui;

    public LottoInPausa() {
    }

    public LottoInPausa(long sequenza, int idLotto, int secondiResidui) {
        super("LOTTO_IN_PAUSA", sequenza);
        this.idLotto = idLotto;
        this.secondiResidui = secondiResidui;
    }

    public int getIdLotto() {
        return idLotto;
    }

    public void setIdLotto(int idLotto) {
        this.idLotto = idLotto;
    }

    public int getSecondiResidui() {
        return secondiResidui;
    }

    public void setSecondiResidui(int secondiResidui) {
        this.secondiResidui = secondiResidui;
    }
}
