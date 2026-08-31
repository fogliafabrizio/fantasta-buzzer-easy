package fantasta.eventi;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "tipo", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = AstaCreata.class, name = "ASTA_CREATA"),
        @JsonSubTypes.Type(value = AssegnazioneIniziale.class, name = "ASSEGNAZIONE_INIZIALE")
})
public abstract class Evento {

    private String tipo;
    private Instant istante;
    private long sequenza;

    protected Evento() {
    }

    protected Evento(String tipo, long sequenza) {
        this.tipo = tipo;
        this.istante = Instant.now();
        this.sequenza = sequenza;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Instant getIstante() {
        return istante;
    }

    public void setIstante(Instant istante) {
        this.istante = istante;
    }

    public long getSequenza() {
        return sequenza;
    }

    public void setSequenza(long sequenza) {
        this.sequenza = sequenza;
    }
}
