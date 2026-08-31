import { Component, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { SseService } from '../../services/sse.service';
import { ListoneService, Calciatore } from '../../services/listone.service';

const MAX_RISULTATI = 60;

@Component({
  selector: 'app-ricerca-calciatore',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="ricerca">
      <h2>Cerca un calciatore libero</h2>

      <div class="filtri">
        <input
          type="text"
          [ngModel]="query()"
          (ngModelChange)="query.set($event)"
          placeholder="Nome calciatore">
        <select [ngModel]="ruolo()" (ngModelChange)="ruolo.set($event)">
          <option value="">Tutti i ruoli</option>
          <option value="P">Portieri</option>
          <option value="D">Difensori</option>
          <option value="C">Centrocampisti</option>
          <option value="A">Attaccanti</option>
        </select>
      </div>

      @if (lottoAperto()) {
        <div class="avviso">C'e' gia' un lotto in corso: annullalo per aprirne un altro.</div>
      }

      @if (errore()) {
        <div class="errore-inline">{{ errore() }}</div>
      }

      @if (query() || ruolo()) {
        <div class="risultati">
          @for (c of risultati(); track c.id) {
            <div class="riga">
              <span class="nome">{{ c.nome }}</span>
              <span class="ruolo ruolo-{{ c.ruolo }}">{{ c.ruolo }}</span>
              <span class="squadra">{{ c.squadra }}</span>
              <span class="quot">Q {{ c.quotazione }}</span>
              <button
                (click)="apri(c.id)"
                [disabled]="lottoAperto() || operazione()">
                Apri lotto
              </button>
            </div>
          } @empty {
            <div class="vuoto">Nessun calciatore libero corrisponde alla ricerca.</div>
          }
          @if (troncato()) {
            <div class="vuoto">Mostrati i primi {{ maxRisultati }} risultati: affina la ricerca.</div>
          }
        </div>
      } @else {
        <div class="vuoto">Digita un nome o scegli un ruolo per cercare.</div>
      }
    </div>
  `,
  styles: [`
    .ricerca { margin-top: 24px; }
    .filtri { display: flex; gap: 8px; margin-bottom: 12px; }
    .filtri input { flex: 1; padding: 8px; border-radius: 6px; border: 1px solid #444; background: #1f1f38; color: #e0e0e0; }
    .filtri select { padding: 8px; border-radius: 6px; border: 1px solid #444; background: #1f1f38; color: #e0e0e0; }
    .avviso { color: #d9a441; margin-bottom: 8px; }
    .errore-inline { color: #ff6b6b; margin-bottom: 8px; }
    .risultati { display: flex; flex-direction: column; gap: 4px; }
    .riga {
      display: grid;
      grid-template-columns: 1fr auto auto auto auto;
      gap: 12px; align-items: center;
      background: #262640; padding: 8px 12px; border-radius: 6px;
    }
    .riga .nome { color: #fff; font-weight: 600; }
    .ruolo { font-size: 0.8rem; font-weight: bold; padding: 1px 6px; border-radius: 4px; color: #fff; }
    .ruolo-P { background: #d9822b; }
    .ruolo-D { background: #2f855a; }
    .ruolo-C { background: #2b6cb0; }
    .ruolo-A { background: #c53030; }
    .squadra { color: #aaa; }
    .quot { color: #888; }
    .riga button {
      background: #2b6cb0; color: #fff; border: none; border-radius: 6px;
      padding: 6px 12px; cursor: pointer;
    }
    .riga button:disabled { opacity: 0.4; cursor: default; }
    .vuoto { color: #888; padding: 8px 0; }
  `]
})
export class RicercaCalciatoreComponent {
  readonly maxRisultati = MAX_RISULTATI;

  query = signal('');
  ruolo = signal('');
  errore = signal<string | null>(null);
  operazione = signal(false);

  constructor(
    private sseService: SseService,
    private listoneService: ListoneService,
    private http: HttpClient
  ) {
    this.listoneService.carica();
  }

  lottoAperto = computed(() => this.sseService.snapshotCorrente()?.lotto != null);

  private liberi = computed<Calciatore[]>(() => {
    const assegnati = new Set(this.sseService.snapshotCorrente()?.calciatoriAssegnati ?? []);
    return this.listoneService.listone()
      .filter(c => !c.fuoriLista && !assegnati.has(c.id));
  });

  private filtrati = computed<Calciatore[]>(() => {
    const q = this.query().trim().toLowerCase();
    const r = this.ruolo();
    return this.liberi().filter(c =>
      (r === '' || c.ruolo === r) &&
      (q === '' || c.nome.toLowerCase().includes(q))
    );
  });

  risultati = computed(() => this.filtrati().slice(0, MAX_RISULTATI));

  troncato = computed(() => this.filtrati().length > MAX_RISULTATI);

  apri(idCalciatore: number): void {
    this.errore.set(null);
    this.operazione.set(true);
    this.http.post('/api/console/apri-lotto', { idCalciatore }).subscribe({
      next: () => this.operazione.set(false),
      error: (err) => {
        this.errore.set(err.error?.errore ?? 'Errore nell\'apertura del lotto');
        this.operazione.set(false);
      }
    });
  }
}
