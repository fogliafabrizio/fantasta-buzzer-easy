import { Component, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { SseService } from '../../services/sse.service';
import { ListoneService } from '../../services/listone.service';

@Component({
  selector: 'app-lotto-corrente',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="lotto">
      @if (lotto(); as l) {
        <h2>Lotto in corso</h2>
        <div class="card-lotto">
          <div class="testa">
            <span class="nome">{{ nomeCalciatore(l.idCalciatore) }}</span>
            <span class="ruolo ruolo-{{ ruoloCalciatore(l.idCalciatore) }}">{{ ruoloCalciatore(l.idCalciatore) }}</span>
          </div>
          <div class="dettagli">
            <span>{{ squadraCalciatore(l.idCalciatore) }}</span>
            <span>Quotazione {{ quotazioneCalciatore(l.idCalciatore) }}</span>
          </div>

          <div class="offerta">
            @if (l.offertaCorrente != null) {
              <div class="importo">{{ l.offertaCorrente }}</div>
              <div class="offerente">di {{ nomeOfferente(l.offerenteCorrente) }}</div>
            } @else {
              <div class="nessuna-offerta">Nessuna offerta</div>
            }
          </div>

          <button class="btn-annulla" (click)="annulla(l.idLotto)" [disabled]="operazione()">
            Annulla lotto
          </button>
          @if (errore()) {
            <div class="errore-inline">{{ errore() }}</div>
          }
        </div>
      } @else {
        <div class="nessun-lotto">Nessun lotto in corso. Cerca un calciatore per aprirne uno.</div>
      }
    </div>
  `,
  styles: [`
    .lotto { margin-top: 8px; }
    .card-lotto {
      background: #2a2a4a;
      border: 1px solid #3a3a5a;
      border-radius: 8px;
      padding: 16px;
    }
    .testa { display: flex; align-items: center; gap: 12px; }
    .nome { font-size: 1.6rem; font-weight: bold; color: #fff; }
    .ruolo {
      font-size: 0.9rem; font-weight: bold; padding: 2px 8px;
      border-radius: 4px; background: #444; color: #fff;
    }
    .ruolo-P { background: #d9822b; }
    .ruolo-D { background: #2f855a; }
    .ruolo-C { background: #2b6cb0; }
    .ruolo-A { background: #c53030; }
    .dettagli { display: flex; gap: 16px; color: #aaa; margin-top: 4px; }
    .offerta { margin: 16px 0; }
    .importo { font-size: 2.4rem; font-weight: bold; color: #4ade80; }
    .offerente { color: #ccc; }
    .nessuna-offerta { color: #888; font-size: 1.1rem; }
    .btn-annulla {
      background: #c53030; color: #fff; border: none; border-radius: 6px;
      padding: 10px 16px; font-size: 1rem; cursor: pointer;
    }
    .btn-annulla:disabled { opacity: 0.5; cursor: default; }
    .errore-inline { color: #ff6b6b; margin-top: 8px; }
    .nessun-lotto { color: #888; padding: 16px 0; }
  `]
})
export class LottoCorrenteComponent {
  operazione = signal(false);
  errore = signal<string | null>(null);

  constructor(
    public sseService: SseService,
    private listoneService: ListoneService,
    private http: HttpClient
  ) {
    this.listoneService.carica();
  }

  lotto = computed(() => this.sseService.snapshotCorrente()?.lotto ?? null);

  private calc(id: number) {
    return this.listoneService.trovaCalciatore(id);
  }

  nomeCalciatore(id: number): string {
    return this.calc(id)?.nome ?? ('#' + id);
  }

  ruoloCalciatore(id: number): string {
    return this.calc(id)?.ruolo ?? '';
  }

  squadraCalciatore(id: number): string {
    return this.calc(id)?.squadra ?? '';
  }

  quotazioneCalciatore(id: number): number | string {
    return this.calc(id)?.quotazione ?? '';
  }

  nomeOfferente(codice: string | null): string {
    if (!codice) return '';
    const p = this.sseService.snapshotCorrente()?.partecipanti.find(x => x.codice === codice);
    return p?.nome ?? codice;
  }

  annulla(idLotto: number): void {
    this.errore.set(null);
    this.operazione.set(true);
    this.http.post('/api/console/annulla-lotto', { idLotto }).subscribe({
      next: () => this.operazione.set(false),
      error: (err) => {
        this.errore.set(err.error?.errore ?? 'Errore nell\'annullamento del lotto');
        this.operazione.set(false);
      }
    });
  }
}
