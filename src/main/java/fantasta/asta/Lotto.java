package fantasta.asta;

import java.time.Duration;
import java.time.Instant;

public class Lotto {

    private final int idLotto;
    private final int idCalciatore;
    private final int durataCountdown;
    private StatoLotto stato;
    private Integer offertaCorrente;
    private String offerenteCorrente;

    // Quando APERTO: istante in cui il countdown arrivera' a zero. I secondi residui
    // sono calcolati in tempo reale da questo istante, mai letti da un evento, cosi'
    // un client che si connette a meta' lotto vede il tempo giusto.
    private Instant istanteScadenza;

    // Quando IN_PAUSA: secondi residui congelati al momento della pausa. Il countdown
    // resta fermo su questo valore finche' il lotto non viene ripreso.
    private int secondiCongelati;

    public Lotto(int idLotto, int idCalciatore, int durataCountdown) {
        this.idLotto = idLotto;
        this.idCalciatore = idCalciatore;
        this.durataCountdown = durataCountdown;
        this.stato = StatoLotto.APERTO;
        this.offertaCorrente = null;
        this.offerenteCorrente = null;
    }

    public int getIdLotto() {
        return idLotto;
    }

    public int getIdCalciatore() {
        return idCalciatore;
    }

    public int getDurataCountdown() {
        return durataCountdown;
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

    public Instant getIstanteScadenza() {
        return istanteScadenza;
    }

    public void setIstanteScadenza(Instant istanteScadenza) {
        this.istanteScadenza = istanteScadenza;
    }

    public int getSecondiCongelati() {
        return secondiCongelati;
    }

    public void setSecondiCongelati(int secondiCongelati) {
        this.secondiCongelati = secondiCongelati;
    }

    /**
     * Secondi residui calcolati in tempo reale al momento della chiamata: quando il
     * lotto e' APERTO si misura quanto manca all'istante di scadenza (arrotondato per
     * eccesso al secondo); in pausa si restituisce il valore congelato; scaduto o
     * aggiudicato il countdown e' a zero. Il valore non e' mai preso da un evento.
     */
    public int getSecondiResidui() {
        switch (stato) {
            case APERTO:
                if (istanteScadenza == null) {
                    return durataCountdown;
                }
                long ms = Duration.between(Instant.now(), istanteScadenza).toMillis();
                if (ms <= 0) {
                    return 0;
                }
                return (int) ((ms + 999) / 1000);
            case IN_PAUSA:
                return secondiCongelati;
            default:
                return 0;
        }
    }
}
