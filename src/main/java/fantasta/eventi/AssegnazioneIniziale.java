package fantasta.eventi;

public class AssegnazioneIniziale extends Evento {

    private int idCalciatore;
    private String codicePartecipante;
    private int costo;

    public AssegnazioneIniziale() {
    }

    public AssegnazioneIniziale(long sequenza, int idCalciatore, String codicePartecipante, int costo) {
        super("ASSEGNAZIONE_INIZIALE", sequenza);
        this.idCalciatore = idCalciatore;
        this.codicePartecipante = codicePartecipante;
        this.costo = costo;
    }

    public int getIdCalciatore() {
        return idCalciatore;
    }

    public void setIdCalciatore(int idCalciatore) {
        this.idCalciatore = idCalciatore;
    }

    public String getCodicePartecipante() {
        return codicePartecipante;
    }

    public void setCodicePartecipante(String codicePartecipante) {
        this.codicePartecipante = codicePartecipante;
    }

    public int getCosto() {
        return costo;
    }

    public void setCosto(int costo) {
        this.costo = costo;
    }
}
