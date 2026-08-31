package fantasta.asta;

public class Lotto {

    private final int idLotto;
    private final int idCalciatore;
    private StatoLotto stato;
    private Integer offertaCorrente;
    private String offerenteCorrente;
    private int secondiResidui;

    public Lotto(int idLotto, int idCalciatore, int durataCountdown) {
        this.idLotto = idLotto;
        this.idCalciatore = idCalciatore;
        this.stato = StatoLotto.APERTO;
        this.offertaCorrente = null;
        this.offerenteCorrente = null;
        this.secondiResidui = durataCountdown;
    }

    public int getIdLotto() {
        return idLotto;
    }

    public int getIdCalciatore() {
        return idCalciatore;
    }

    public StatoLotto getStato() {
        return stato;
    }

    public void setStato(StatoLotto stato) {
        this.stato = stato;
    }

    public Integer getOffertaCorrente() {
        return offertaCorrente;
    }

    public void setOffertaCorrente(Integer offertaCorrente) {
        this.offertaCorrente = offertaCorrente;
    }

    public String getOfferenteCorrente() {
        return offerenteCorrente;
    }

    public void setOfferenteCorrente(String offerenteCorrente) {
        this.offerenteCorrente = offerenteCorrente;
    }

    public int getSecondiResidui() {
        return secondiResidui;
    }

    public void setSecondiResidui(int secondiResidui) {
        this.secondiResidui = secondiResidui;
    }
}
