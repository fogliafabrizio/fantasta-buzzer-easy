package fantasta.eventi;

import java.util.List;

public class AstaCreata extends Evento {

    private String nomeAsta;
    private String tipoAsta;
    private int durataCountdown;
    private boolean banditorePartecipa;
    private String fileListone;
    private List<PartecipanteEvento> partecipanti;

    public AstaCreata() {
    }

    public AstaCreata(long sequenza, String nomeAsta, String tipoAsta, int durataCountdown,
                      boolean banditorePartecipa, String fileListone,
                      List<PartecipanteEvento> partecipanti) {
        super("ASTA_CREATA", sequenza);
        this.nomeAsta = nomeAsta;
        this.tipoAsta = tipoAsta;
        this.durataCountdown = durataCountdown;
        this.banditorePartecipa = banditorePartecipa;
        this.fileListone = fileListone;
        this.partecipanti = partecipanti;
    }

    public String getNomeAsta() {
        return nomeAsta;
    }

    public void setNomeAsta(String nomeAsta) {
        this.nomeAsta = nomeAsta;
    }

    public String getTipoAsta() {
        return tipoAsta;
    }

    public void setTipoAsta(String tipoAsta) {
        this.tipoAsta = tipoAsta;
    }

    public int getDurataCountdown() {
        return durataCountdown;
    }

    public void setDurataCountdown(int durataCountdown) {
        this.durataCountdown = durataCountdown;
    }

    public boolean isBanditorePartecipa() {
        return banditorePartecipa;
    }

    public void setBanditorePartecipa(boolean banditorePartecipa) {
        this.banditorePartecipa = banditorePartecipa;
    }

    public String getFileListone() {
        return fileListone;
    }

    public void setFileListone(String fileListone) {
        this.fileListone = fileListone;
    }

    public List<PartecipanteEvento> getPartecipanti() {
        return partecipanti;
    }

    public void setPartecipanti(List<PartecipanteEvento> partecipanti) {
        this.partecipanti = partecipanti;
    }

    public static class PartecipanteEvento {
        private String nome;
        private String codice;
        private int crediti;

        public PartecipanteEvento() {
        }

        public PartecipanteEvento(String nome, String codice, int crediti) {
            this.nome = nome;
            this.codice = codice;
            this.crediti = crediti;
        }

        public String getNome() {
            return nome;
        }

        public void setNome(String nome) {
            this.nome = nome;
        }

        public String getCodice() {
            return codice;
        }

        public void setCodice(String codice) {
            this.codice = codice;
        }

        public int getCrediti() {
            return crediti;
        }

        public void setCrediti(int crediti) {
            this.crediti = crediti;
        }
    }
}
