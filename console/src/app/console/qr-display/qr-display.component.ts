import { Component, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SseService } from '../../services/sse.service';
import { ListoneService } from '../../services/listone.service';

@Component({
  selector: 'app-qr-display',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (sseService.isConnessionePersa()) {
      <div class="errore-banner">Connessione al server persa</div>
    }

    <h2>Asta attiva — QR Code partecipanti</h2>

    <div class="griglia-qr">
      @for (p of partecipanti(); track p.codice) {
        <div class="card-qr">
          <img [src]="'/api/qrcode/' + p.codice" [alt]="'QR ' + p.nome" width="200" height="200">
          <div class="info">
            <div class="nome">{{ p.nome }}</div>
            <div class="codice">Codice: {{ p.codice }}</div>
            <div class="url">{{ urlBase() }}/telefono/?codice={{ p.codice }}</div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .griglia-qr {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
      gap: 16px;
      margin-top: 16px;
    }
    .card-qr {
      background: #262640;
      border-radius: 8px;
      padding: 16px;
      text-align: center;
    }
    .card-qr img {
      border-radius: 4px;
      background: #fff;
      padding: 8px;
    }
    .info { margin-top: 8px; }
    .nome { font-size: 1.2rem; font-weight: bold; color: #fff; }
    .codice { font-size: 1rem; color: #aaa; margin-top: 4px; }
    .url { font-size: 0.75rem; color: #666; margin-top: 4px; word-break: break-all; }
  `]
})
export class QrDisplayComponent {
  constructor(
    public sseService: SseService,
    private listoneService: ListoneService
  ) {
    this.listoneService.carica();
  }

  partecipanti = computed(() => {
    const snap = this.sseService.snapshotCorrente();
    return snap?.partecipanti ?? [];
  });

  urlBase = computed(() => {
    return window.location.protocol + '//' + window.location.hostname + ':8080';
  });
}
