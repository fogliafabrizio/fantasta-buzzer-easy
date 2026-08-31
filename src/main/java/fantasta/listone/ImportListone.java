package fantasta.listone;

import fantasta.asta.Ruolo;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ImportListone {

    private static final Logger log = LoggerFactory.getLogger(ImportListone.class);

    private final List<Calciatore> calciatori;
    private final Map<String, List<Assegnazione>> assegnazioniPerPartecipante;
    private final boolean riparazione;

    private ImportListone(List<Calciatore> calciatori,
                          Map<String, List<Assegnazione>> assegnazioniPerPartecipante,
                          boolean riparazione) {
        this.calciatori = calciatori;
        this.assegnazioniPerPartecipante = assegnazioniPerPartecipante;
        this.riparazione = riparazione;
    }

    public static ImportListone leggiCompleto(Path file) {
        return leggi(file, true);
    }

    public static ImportListone leggiCatalogo(Path file) {
        return leggi(file, false);
    }

    private static ImportListone leggi(Path file, boolean includiAssegnazioni) {
        log.info("Lettura xlsx {} (modalita={})", file.getFileName(),
                includiAssegnazioni ? "completa" : "solo catalogo");

        try (InputStream is = Files.newInputStream(file);
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet foglio = workbook.getSheetAt(0);
            Row intestazione = foglio.getRow(0);
            if (intestazione == null) {
                throw new FormatoListoneException("Il foglio non ha riga di intestazione");
            }

            Map<String, Integer> colonne = mappaColonne(intestazione);
            verificaColonnaObbligatoria(colonne, "#");
            verificaColonnaObbligatoria(colonne, "Nome");
            verificaColonnaObbligatoria(colonne, "R.");
            verificaColonnaObbligatoria(colonne, "Sq.");
            verificaColonnaObbligatoria(colonne, "QUOT.");
            verificaColonnaObbligatoria(colonne, "Fuori lista");

            boolean haFantaSquadra = colonne.containsKey("FantaSquadra");
            boolean riparazione = false;
            Map<String, List<Assegnazione>> assegnazioni = new LinkedHashMap<>();

            if (includiAssegnazioni && haFantaSquadra) {
                verificaColonnaObbligatoria(colonne, "Costo");
            }

            List<Calciatore> calciatori = new ArrayList<>();

            for (int i = 1; i <= foglio.getLastRowNum(); i++) {
                Row riga = foglio.getRow(i);
                if (riga == null) continue;

                Cell cellaId = riga.getCell(colonne.get("#"));
                if (cellaId == null || cellaId.getCellType() == CellType.BLANK) continue;

                int id = leggiIntero(cellaId, i, "#");
                String nome = leggiStringa(riga, colonne.get("Nome"), i, "Nome");
                Ruolo ruolo = parseRuolo(leggiStringa(riga, colonne.get("R."), i, "R."), i);
                String squadra = leggiStringa(riga, colonne.get("Sq."), i, "Sq.");
                int quotazione = leggiIntero(riga.getCell(colonne.get("QUOT.")), i, "QUOT.");

                Cell cellaFuori = riga.getCell(colonne.get("Fuori lista"));
                boolean fuoriLista = cellaFuori != null
                        && cellaFuori.getCellType() == CellType.STRING
                        && "*".equals(cellaFuori.getStringCellValue().trim());

                calciatori.add(new Calciatore(id, nome, ruolo, squadra, quotazione, fuoriLista));

                if (includiAssegnazioni && haFantaSquadra) {
                    Cell cellaFS = riga.getCell(colonne.get("FantaSquadra"));
                    String fantaSquadra = cellaFS != null && cellaFS.getCellType() == CellType.STRING
                            ? cellaFS.getStringCellValue().trim() : "";

                    if (!fantaSquadra.isEmpty()) {
                        riparazione = true;
                        Cell cellaCosto = riga.getCell(colonne.get("Costo"));
                        int costo = cellaCosto != null && cellaCosto.getCellType() == CellType.NUMERIC
                                ? (int) cellaCosto.getNumericCellValue() : 0;

                        assegnazioni
                                .computeIfAbsent(fantaSquadra, k -> new ArrayList<>())
                                .add(new Assegnazione(id, costo));
                    }
                }
            }

            if (calciatori.isEmpty()) {
                throw new FormatoListoneException("Il file non contiene calciatori");
            }

            log.info("Letti {} calciatori dal file {} (riparazione={})", calciatori.size(), file.getFileName(), riparazione);
            return new ImportListone(calciatori, assegnazioni, riparazione);

        } catch (FormatoListoneException e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException("Impossibile leggere il file " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    private static Map<String, Integer> mappaColonne(Row intestazione) {
        Map<String, Integer> mappa = new HashMap<>();
        for (int i = 0; i < intestazione.getLastCellNum(); i++) {
            Cell cella = intestazione.getCell(i);
            if (cella != null && cella.getCellType() == CellType.STRING) {
                mappa.put(cella.getStringCellValue().trim(), i);
            }
        }
        return mappa;
    }

    private static void verificaColonnaObbligatoria(Map<String, Integer> colonne, String nome) {
        if (!colonne.containsKey(nome)) {
            throw new FormatoListoneException("Colonna '" + nome + "' mancante nell'intestazione");
        }
    }

    private static Ruolo parseRuolo(String valore, int riga) {
        return switch (valore) {
            case "P" -> Ruolo.P;
            case "D" -> Ruolo.D;
            case "C" -> Ruolo.C;
            case "A" -> Ruolo.A;
            default -> throw new FormatoListoneException(
                    "Ruolo non riconosciuto '" + valore + "' alla riga " + (riga + 1));
        };
    }

    private static String leggiStringa(Row riga, int colonna, int rigaIdx, String nomeColonna) {
        Cell cella = riga.getCell(colonna);
        if (cella == null || cella.getCellType() == CellType.BLANK) {
            throw new FormatoListoneException(
                    "Valore mancante nella colonna '" + nomeColonna + "' alla riga " + (rigaIdx + 1));
        }
        if (cella.getCellType() == CellType.NUMERIC) {
            return String.valueOf((int) cella.getNumericCellValue());
        }
        return cella.getStringCellValue().trim();
    }

    private static int leggiIntero(Cell cella, int rigaIdx, String nomeColonna) {
        if (cella == null || cella.getCellType() == CellType.BLANK) {
            throw new FormatoListoneException(
                    "Valore mancante nella colonna '" + nomeColonna + "' alla riga " + (rigaIdx + 1));
        }
        if (cella.getCellType() == CellType.NUMERIC) {
            return (int) cella.getNumericCellValue();
        }
        try {
            return Integer.parseInt(cella.getStringCellValue().trim());
        } catch (NumberFormatException e) {
            throw new FormatoListoneException(
                    "Valore non numerico nella colonna '" + nomeColonna + "' alla riga " + (rigaIdx + 1)
                            + ": '" + cella.getStringCellValue() + "'");
        }
    }

    public List<Calciatore> getCalciatori() {
        return calciatori;
    }

    public Map<String, List<Assegnazione>> getAssegnazioniPerPartecipante() {
        return assegnazioniPerPartecipante;
    }

    public boolean isRiparazione() {
        return riparazione;
    }

    public Set<String> getNomiPartecipanti() {
        return assegnazioniPerPartecipante.keySet();
    }

    public int calcolaSommaCosti(String nomePartecipante) {
        List<Assegnazione> lista = assegnazioniPerPartecipante.get(nomePartecipante);
        if (lista == null) return 0;
        return lista.stream().mapToInt(Assegnazione::costo).sum();
    }

    public long contaFuoriLista() {
        return calciatori.stream().filter(Calciatore::fuoriLista).count();
    }

    public record Assegnazione(int idCalciatore, int costo) {
    }

    public static class FormatoListoneException extends RuntimeException {
        public FormatoListoneException(String messaggio) {
            super(messaggio);
        }
    }
}
