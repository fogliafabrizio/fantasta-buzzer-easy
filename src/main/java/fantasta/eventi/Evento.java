package fantasta.eventi;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "tipo", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = AstaCreata.class, name = "ASTA_CREATA"),
        @JsonSubTypes.Type(value = AssegnazioneIniziale.class, name = "ASSEGNAZIONE_INIZIALE"),
        @JsonSubTypes.Type(value = LottoAperto.class, name = "LOTTO_APERTO"),
        @JsonSubTypes.Type(value = OffertaAccettata.class, name = "OFFERTA_ACCETTATA"),
        @JsonSubTypes.Type(value = LottoAnnullato.class, name = "LOTTO_ANNULLATO"),
        @JsonSubTypes.Type(value = LottoScaduto.class, name = "LOTTO_SCADUTO"),
        @JsonSubTypes.Type(value = LottoInPausa.class, name = "LOTTO_IN_PAUSA"),
        @JsonSubTypes.Type(value = LottoRipreso.class, name = "LOTTO_RIPRESO"),
        @JsonSubTypes.Type(value = LottoAggiudicato.class, name = "LOTTO_AGGIUDICATO"),
        @JsonSubTypes.Type(value = LottoRiaperto.class, name = "LOTTO_RIAPERTO")
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
