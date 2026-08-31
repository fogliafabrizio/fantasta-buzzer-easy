package fantasta.asta;

/**
 * Modalita' con cui il banditore riapre un lotto scaduto.
 * DA_CAPO azzera le offerte e riparte a tempo pieno; MANTENENDO tiene l'offerta
 * corrente in piedi e fa ripartire solo il tempo.
 */
public enum ModalitaRiapertura {
    DA_CAPO, MANTENENDO
}
