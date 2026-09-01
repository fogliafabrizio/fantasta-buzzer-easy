import { ChangeDetectionStrategy, Component, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SseService } from '../../services/sse.service';
import { ListoneService } from '../../services/listone.service';

const RUOLI = ['P', 'D', 'C', 'A'] as const;
type Ruolo = typeof RUOLI[number];

interface CalciatoreInRosa {
  idCalciatore: number;
  nome: string;
  prezzo: number;
}

interface RepartoRosa {
  ruolo: Ruolo;
  quanti: number;
  calciatori: CalciatoreInRosa[];
}

interface RigaPartecipante {
  codice: string;
  nome: string;
  crediti: number;
  reparti: RepartoRosa[];
}

/**
 * Tabella di tutti i partecipanti con crediti residui e rosa per ruolo.
 *
 * Tutto qui e' derivato: i crediti arrivano gia' proiettati dal log (totali meno la
 * somma dei prezzi in rosa) e il totale in circolazione e' la loro somma, calcolata al
 * volo. Nessun dato viene tenuto in un campo locale.
 *
 * Nessuna regola imposta: la tabella mostra e basta. Non segnala slot pieni, non
 * calcola capienze ne' budget massimi.
 *
 * OnPush + signal: il componente si ridisegna solo quando cambiano snapshot o listone.
 * Il countdown del lotto corrente, che scorre ogni 250 ms su un signal di un altro
 * componente, non tocca questa tabella. E a snapshot nuovo il `track` sulle chiavi
 * stabili (codice, ruolo, id calciatore) fa aggiornare le celle in posto, senza
 * ricostruire righe e chip.
 */
@Component({
  selector: 'app-tabella-partecipanti',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (righe().length) {
      <div class="partecipanti">
        <div class="testa-sezione">
          <h2>Partecipanti</h2>
          <span class="circolazione">
            Crediti in circolazione: <strong>{{ creditiInCircolazione() }}</strong>
          </span>
        </div>

        <table>
          <thead>
            <tr>
              <th class="col-nome">Partecipante</th>
              <th class="col-crediti">Crediti</th>
              @for (r of ruoli; track r) {
                <th class="col-ruolo">{{ r }}</th>
              }
            </tr>
          </thead>
          <tbody>
            @for (riga of righe(); track riga.codice) {
              <tr>
                <td class="col-nome">{{ riga.nome }}</td>
                <td class="col-crediti">{{ riga.crediti }}</td>
                @for (reparto of riga.reparti; track reparto.ruolo) {
                  <td class="col-ruolo">
                    <span class="conteggio ruolo-{{ reparto.ruolo }}">{{ reparto.quanti }}</span>
                    @for (c of reparto.calciatori; track c.idCalciatore) {
                      <span class="chip">{{ c.nome }} <em>{{ c.prezzo }}</em></span>
                    }
                  </td>
                }
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
  styles: [`
    .partecipanti { margin-top: 24px; }
    .testa-sezione { display: flex; align-items: baseline; gap: 16px; flex-wrap: wrap; }
    .testa-sezione h2 { margin: 0; }
    .circolazione { color: #aaa; }
    .circolazione strong { color: #4ade80; font-variant-numeric: tabular-nums; }
    table { width: 100%; border-collapse: collapse; margin-top: 12px; }
    th, td {
      text-align: left; padding: 8px 10px; vertical-align: top;
      border-bottom: 1px solid #3a3a5a;
    }
    th { color: #aaa; font-weight: normal; font-size: 0.85rem; }
    .col-nome { color: #fff; font-weight: bold; white-space: nowrap; }
    .col-crediti {
      color: #4ade80; font-weight: bold; font-variant-numeric: tabular-nums;
      white-space: nowrap; width: 1%;
    }
    .col-ruolo { min-width: 140px; }
    .conteggio {
      display: inline-block; min-width: 20px; text-align: center;
      font-weight: bold; font-size: 0.8rem; color: #fff;
      border-radius: 4px; padding: 1px 6px; margin: 0 6px 4px 0; background: #444;
    }
    .ruolo-P { background: #d9822b; }
    .ruolo-D { background: #2f855a; }
    .ruolo-C { background: #2b6cb0; }
    .ruolo-A { background: #c53030; }
    .chip {
      display: inline-block; background: #1f2937; color: #e5e7eb;
      border-radius: 4px; padding: 1px 6px; margin: 0 4px 4px 0; font-size: 0.85rem;
    }
    .chip em { color: #4ade80; font-style: normal; font-weight: bold; }
  `]
})
export class TabellaPartecipantiComponent {
  readonly ruoli = RUOLI;

  constructor(
    private sseService: SseService,
    private listoneService: ListoneService
  ) {
    this.listoneService.carica();
  }

  righe = computed<RigaPartecipante[]>(() => {
    const snapshot = this.sseService.snapshotCorrente();
    if (!snapshot) return [];

    // Il listone e' letto dentro il computed: quando arriva, i nomi dei calciatori
    // compaiono da soli senza altro lavoro.
    const listone = this.listoneService.listone();
    const nomi = new Map(listone.map(c => [c.id, c.nome]));

    return snapshot.partecipanti.map(p => ({
      codice: p.codice,
      nome: p.nome,
      crediti: p.crediti,
      reparti: RUOLI.map(ruolo => {
        const voci = p.rosa[ruolo] ?? [];
        return {
          ruolo,
          quanti: voci.length,
          calciatori: voci.map(v => ({
            idCalciatore: v.idCalciatore,
            nome: nomi.get(v.idCalciatore) ?? ('#' + v.idCalciatore),
            prezzo: v.prezzo
          }))
        };
      })
    }));
  });

  creditiInCircolazione = computed(() =>
    this.righe().reduce((somma, riga) => somma + riga.crediti, 0)
  );
}
