package fantasta.asta;

/**
 * Evento applicativo Spring pubblicato quando il countdown scade lato server (fuori
 * da una richiesta HTTP). Serve a far ripartire il broadcast dello snapshot senza
 * introdurre una dipendenza circolare tra Asta e SseController: l'Asta pubblica,
 * il controller SSE ascolta. Le transizioni innescate da HTTP restano gestite dai
 * controller, che chiamano gia' broadcast direttamente.
 */
public class LottoScadutoEvent {

    private final int idLotto;

    public LottoScadutoEvent(int idLotto) {
        this.idLotto = idLotto;
    }

    public int getIdLotto() {
        return idLotto;
    }
}
