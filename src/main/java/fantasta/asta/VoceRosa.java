package fantasta.asta;

/**
 * Un calciatore nella rosa di un partecipante, con il prezzo effettivamente pagato.
 * E' l'unita' su cui si regge la proiezione dei crediti: ogni acquisto, che venga da
 * ASSEGNAZIONE_INIZIALE (riparazione) o da LOTTO_AGGIUDICATO (asta), e' una voce di
 * rosa con il suo prezzo, e i crediti residui sono i totali meno la somma dei prezzi.
 */
public record VoceRosa(int idCalciatore, int prezzo) {
}
