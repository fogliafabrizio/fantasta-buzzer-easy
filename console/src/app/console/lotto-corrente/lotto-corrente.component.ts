import { Component, computed, effect, signal } from '@angular/core';
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
        <div class="card-lotto stato-{{ l.stato }}">
          <div class="testa">
            <span class="nome">{{ nomeCalciatore(l.idCalciatore) }}</span>
            <span class="ruolo ruolo-{{ ruoloCalciatore(l.idCalciatore) }}">{{ ruoloCalciatore(l.idCalciatore) }}</span>
            <span class="countdown"
                  [class.pausa]="l.stato === 'IN_PAUSA'"
                  [class.scaduto]="l.stato === 'SCADUTO'"
                  [class.aggiudicato]="l.stato === 'AGGIUDICATO'">
              @if (l.stato === 'SCADUTO') {
                Scaduto
              } @else if (l.stato === 'AGGIUDICATO') {
                Aggiudicato
              } @else if (l.stato === 'IN_PAUSA') {
                In pausa · {{ secondiVisualizzati() }}s
              } @else {
                {{ secondiVisualizzati() }}s
              }
            </span>
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

          @if (l.stato === 'SCADUTO') {
            @if (l.offertaCorrente != null) {
              <div class="avviso">
                Tempo scaduto. Offerta vincente: {{ l.offertaCorrente }} di {{ nomeOfferente(l.offerenteCorrente) }}.
                Conferma l'aggiudicazione oppure riapri il lotto.
              </div>
              <div class="azioni">
                <button class="btn-primario" (click)="conferma()" [disabled]="operazione()">
                  Aggiudica
                </button>
                <button class="btn-secondario" (click)="riapri('DA_CAPO')" [disabled]="operazione()">
                  Riapri da capo
                </button>
                <button class="btn-secondario" (click)="riapri('MANTENENDO')" [disabled]="operazione()">
                  Riapri mantenendo l'offerta
                </button>
                <button class="btn-annulla" (click)="annulla()" [disabled]="operazione()">
                  Annulla lotto
                </button>
              </div>
            } @else {
              <div class="avviso">
                Tempo scaduto senza offerte. Chiudi il lotto: il calciatore torna libero e richiamabile.
              </div>
              <div class="azioni">
                <button class="btn-annulla" (click)="annulla()" [disabled]="operazione()">
                  Chiudi lotto
                </button>
              </div>
            }
          } @else if (l.stato === 'AGGIUDICATO') {
            <div class="avviso esito">
              Aggiudicato a {{ nomeOfferente(l.offerenteCorrente) }} per {{ l.offertaCorrente }}.
              Apri il prossimo lotto per continuare.
            </div>
            <div class="azioni">
              <button class="btn-annulla" (click)="annulla()" [disabled]="operazione()">
                Chiudi scheda
              </button>
            </div>
          } @else if (l.stato === 'IN_PAUSA') {
            <div class="azioni">
              <button class="btn-primario" (click)="riprendi()" [disabled]="operazione()">
                Riprendi
              </button>
              <button class="btn-annulla" (click)="annulla()" [disabled]="operazione()">
                Annulla lotto
              </button>
            </div>
          } @else {
            <div class="azioni">
              <button class="btn-secondario" (click)="pausa()" [disabled]="operazione()">
                Pausa
              </button>
              <button class="btn-annulla" (click)="annulla()" [disabled]="operazione()">
                Annulla lotto
              </button>
            </div>
          }

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
    .card-lotto.stato-SCADUTO { border-color: #eab308; }
    .card-lotto.stato-IN_PAUSA { border-color: #6b7280; }
    .card-lotto.stato-AGGIUDICATO { border-color: #22c55e; }
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
    .countdown {
      margin-left: auto; font-size: 1.3rem; font-weight: bold;
      padding: 4px 12px; border-radius: 6px;
      background: #1f2937; color: #4ade80; font-variant-numeric: tabular-nums;
    }
    .countdown.pausa { color: #d1d5db; }
    .countdown.scaduto { color: #eab308; }
    .countdown.aggiudicato { color: #22c55e; }
    .dettagli { display: flex; gap: 16px; color: #aaa; margin-top: 4px; }
    .offerta { margin: 16px 0; }
    .importo { font-size: 2.4rem; font-weight: bold; color: #4ade80; }
    .offerente { color: #ccc; }
    .nessuna-offerta { color: #888; font-size: 1.1rem; }
    .avviso {
      background: #1f2937; border: 1px solid #3a3a5a; border-radius: 6px;
      padding: 10px 12px; color: #e5e7eb; margin-bottom: 12px;
    }
    .avviso.esito { border-color: #22c55e; color: #a7f3c4; }
    .azioni { display: flex; flex-wrap: wrap; gap: 8px; }
    .azioni button {
      border: none; border-radius: 6px; padding: 10px 16px;
      font-size: 1rem; cursor: pointer; color: #fff;
    }
    .azioni button:disabled { opacity: 0.5; cursor: default; }
    .btn-primario { background: #22c55e; }
    .btn-secondario { background: #3b82f6; }
    .btn-annulla { background: #c53030; }
    .errore-inline { color: #ff6b6b; margin-top: 8px; }
    .nessun-lotto { color: #888; padding: 16px 0; }
  `]
})
export class LottoCorrenteComponent {
  operazione = signal(false);
  errore = signal<string | null>(null);

  // Countdown scalato localmente solo per l'animazione: il tempo autorevole e' quello
  // del server. A ogni snapshot ci si riallinea fissando l'istante di scadenza locale
  // (adesso + secondiResidui); tra uno snapshot e l'altro si mostra ceil((scadenza -
  // adesso)/1000), stessa semantica del server. Il refresh e' piu' fitto di un secondo
  // cosi' il numero cambia a ridosso del secondo reale e la coda 2 -> 1 -> scaduto non
  // ha beat di troppo. L'espirazione resta autorevole sul server (lo snapshot SCADUTO
  // interrompe comunque il conteggio locale).
  private static readonly CADENZA_COUNTDOWN = 250;
  secondiVisualizzati = signal(0);
  private istanteScadenzaLocale = 0;

  constructor(
    public sseService: SseService,
    private listoneService: ListoneService,
    private http: HttpClient
  ) {
    this.listoneService.carica();

    effect(() => {
      const l = this.lotto();
      // Riallineamento a ogni snapshot: si riparte dal tempo autorevole del server.
      const secondi = l ? l.secondiResidui : 0;
      this.istanteScadenzaLocale = Date.now() + secondi * 1000;
      this.secondiVisualizzati.set(secondi);
    }, { allowSignalWrites: true });

    setInterval(() => this.tick(), LottoCorrenteComponent.CADENZA_COUNTDOWN);
  }

  lotto = computed(() => this.sseService.snapshotCorrente()?.lotto ?? null);

  private tick(): void {
    const l = this.lotto();
    // Il countdown scorre solo a lotto aperto; in pausa o scaduto resta fermo sul
    // valore riallineato dall'ultimo snapshot.
    if (!l || l.stato !== 'APERTO') return;
    this.secondiVisualizzati.set(Math.max(0, Math.ceil((this.istanteScadenzaLocale - Date.now()) / 1000)));
  }

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

  // I comandi console operano sul lotto corrente lato server: nessun idLotto nel body
  // (solo il banditore comanda). Solo riapri porta la modalita.
  private comando(url: string, corpo: Record<string, unknown> | null, erroreDefault: string): void {
    this.errore.set(null);
    this.operazione.set(true);
    this.http.post(url, corpo).subscribe({
      next: () => this.operazione.set(false),
      error: (err) => {
        this.errore.set(err.error?.errore ?? erroreDefault);
        this.operazione.set(false);
      }
    });
  }

  annulla(): void {
    this.comando('/api/console/annulla-lotto', null, 'Errore nell\'annullamento del lotto');
  }

  pausa(): void {
    this.comando('/api/console/pausa', null, 'Errore nella messa in pausa del lotto');
  }

  riprendi(): void {
    this.comando('/api/console/riprendi', null, 'Errore nella ripresa del lotto');
  }

  conferma(): void {
    this.comando('/api/console/conferma', null, 'Errore nell\'aggiudicazione del lotto');
  }

  riapri(modalita: 'DA_CAPO' | 'MANTENENDO'): void {
    this.comando('/api/console/riapri', { modalita }, 'Errore nella riapertura del lotto');
  }
}
