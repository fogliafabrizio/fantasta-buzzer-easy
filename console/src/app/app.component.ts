import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CreaAstaComponent } from './console/crea-asta/crea-asta.component';
import { QrDisplayComponent } from './console/qr-display/qr-display.component';
import { LottoCorrenteComponent } from './console/lotto-corrente/lotto-corrente.component';
import { RicercaCalciatoreComponent } from './console/ricerca-calciatore/ricerca-calciatore.component';
import { TabellaPartecipantiComponent } from './console/tabella-partecipanti/tabella-partecipanti.component';
import { CorrezioniComponent } from './console/correzioni/correzioni.component';
import { SseService } from './services/sse.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule, CreaAstaComponent, QrDisplayComponent,
    LottoCorrenteComponent, RicercaCalciatoreComponent, TabellaPartecipantiComponent,
    CorrezioniComponent
  ],
  template: `
    <div class="container">
      <h1>Fantasta — Console Banditore</h1>

      @if (!sseService.astaAttiva()) {
        <app-crea-asta></app-crea-asta>
      } @else {
        @if (sseService.isConnessionePersa()) {
          <div class="errore-banner">Connessione al server persa</div>
        }
        <app-lotto-corrente></app-lotto-corrente>
        <app-ricerca-calciatore></app-ricerca-calciatore>
        <app-correzioni></app-correzioni>
        <app-tabella-partecipanti></app-tabella-partecipanti>
        <app-qr-display></app-qr-display>
      }
    </div>
  `
})
export class AppComponent {
  constructor(public sseService: SseService) {}
}
