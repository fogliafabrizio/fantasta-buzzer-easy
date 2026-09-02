import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { SseService } from '../../services/sse.service';
import { ListoneService } from '../../services/listone.service';

/**
 * Le correzioni del banditore. Per ora una sola: l'annullamento dell'ultima aggiudicazione.
 *
 * La disponibilita' dell'azione non viene dedotta qui: il pulsante c'e' quando lo snapshot
 * porta `annullabile`, e sparisce quando quel campo e' null. Quale aggiudicazione sia
 * annullabile, e se un lotto in corso lo impedisca, lo decide il server: la console
 * propone e il server accetta o rifiuta.
 *
 * La conferma dice quale calciatore torna libero, a chi tornano quanti crediti e quali
 * saranno i suoi crediti residui dopo. Quella somma e' aritmetica di visualizzazione su
 * numeri gia' presenti nello snapshot, non una regola di dominio ricalcolata.
 */
@Component({
  selector: 'app-correzioni',
  standalone: true,
  imports: [CommonModule, DialogModule, ButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (annullabile(); as a) {
      <div class="correzioni">
        <h2>Correzioni</h2>
        <div class="riga">
          <span class="descrizione">
            Ultima aggiudicazione annullabile:
            <strong>{{ nomeCalciatore(a.idCalciatore) }}</strong>
            a {{ nomePartecipante(a.codicePartecipante) }} per {{ a.importo }}
          </span>
          <button class="btn-annulla" (click)="apriConferma()" [disabled]="operazione()">
            Annulla aggiudicazione
          </button>
        </div>

        @if (errore()) {
          <div class="errore-inline">{{ errore() }}</div>
        }

        <p-dialog header="Annullare l'aggiudicazione?"
                  [(visible)]="confermaAperta"
                  [modal]="true"
                  [draggable]="false"
                  [resizable]="false"
                  [style]="{ width: '32rem' }">
          <p>
            <strong>{{ nomeCalciatore(a.idCalciatore) }}</strong> torna libero e potra'
            essere rimesso all'asta.
          </p>
          <p>
            {{ a.importo }} crediti tornano a
            <strong>{{ nomePartecipante(a.codicePartecipante) }}</strong>, che passera' da
            {{ creditiAttuali(a.codicePartecipante) }} a
            {{ creditiAttuali(a.codicePartecipante) + a.importo }} crediti residui.
          </p>
          <ng-template pTemplate="footer">
            <p-button label="Non annullare" severity="secondary"
                      (onClick)="chiudiConferma()" [disabled]="operazione()"></p-button>
            <p-button label="Annulla l'aggiudicazione" severity="danger"
                      (onClick)="conferma(a.idLotto)" [disabled]="operazione()"></p-button>
          </ng-template>
        </p-dialog>
      </div>
    }
  `,
  styles: [`
    .correzioni { margin-top: 24px; }
    .correzioni h2 { margin: 0 0 12px; }
    .riga {
      display: flex; align-items: center; gap: 16px; flex-wrap: wrap;
      background: #2a2a4a; border: 1px solid #3a3a5a; border-radius: 8px;
      padding: 12px 16px;
    }
    .descrizione { color: #e5e7eb; }
    .descrizione strong { color: #fff; }
    .btn-annulla {
      margin-left: auto; border: none; border-radius: 6px; padding: 10px 16px;
      font-size: 1rem; cursor: pointer; color: #fff; background: #c53030;
    }
    .btn-annulla:disabled { opacity: 0.5; cursor: default; }
    .errore-inline { color: #ff6b6b; margin-top: 8px; }
  `]
})
export class CorrezioniComponent {
  confermaAperta = false;
  operazione = signal(false);
  errore = signal<string | null>(null);

  constructor(
    private sseService: SseService,
    private listoneService: ListoneService,
    private http: HttpClient
  ) {
    this.listoneService.carica();
  }

  annullabile = computed(() => this.sseService.snapshotCorrente()?.annullabile ?? null);

  nomeCalciatore(id: number): string {
    return this.listoneService.trovaCalciatore(id)?.nome ?? ('#' + id);
  }

  nomePartecipante(codice: string): string {
    const p = this.sseService.snapshotCorrente()?.partecipanti.find(x => x.codice === codice);
    return p?.nome ?? codice;
  }

  creditiAttuali(codice: string): number {
    const p = this.sseService.snapshotCorrente()?.partecipanti.find(x => x.codice === codice);
    return p?.crediti ?? 0;
  }

  apriConferma(): void {
    this.errore.set(null);
    this.confermaAperta = true;
  }

  chiudiConferma(): void {
    this.confermaAperta = false;
  }

  // L'idLotto rimandato indietro e' quello su cui la conferma e' stata disegnata: se nel
  // frattempo la cima della pila e' cambiata, il server rifiuta e il motivo si vede qui.
  conferma(idLotto: number): void {
    this.errore.set(null);
    this.operazione.set(true);
    this.http.post('/api/console/annulla-aggiudicazione', { idLotto }).subscribe({
      next: () => {
        this.operazione.set(false);
        this.confermaAperta = false;
      },
      error: (err) => {
        this.errore.set(err.error?.errore ?? 'Errore nell\'annullamento dell\'aggiudicazione');
        this.operazione.set(false);
        this.confermaAperta = false;
      }
    });
  }
}
