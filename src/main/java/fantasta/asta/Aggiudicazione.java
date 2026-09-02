package fantasta.asta;

/**
 * Una voce della pila delle aggiudicazioni ancora annullabili. Non e' un evento e non
 * viene mai scritta sul log: e' proiezione pura, ricostruita dal replay come la rosa e i
 * crediti. Porta l'idLotto, perche' e' quello che la console rimanda indietro come guardia
 * contro il doppio click, e l'idCalciatore, perche' e' con quello che si ritrova in quale
 * rosa il calciatore si trovi <em>adesso</em> e a che prezzo.
 * <p>
 * Il prezzo e il proprietario non stanno qui apposta: fra l'aggiudicazione e il suo
 * annullamento una rettifica puo' averli cambiati entrambi, e una copia congelata qui
 * dentro sarebbe la fonte sbagliata da cui restituire i crediti.
 */
public record Aggiudicazione(int idLotto, int idCalciatore) {
}
