import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CreaAstaComponent } from './console/crea-asta/crea-asta.component';
import { QrDisplayComponent } from './console/qr-display/qr-display.component';
import { SseService } from './services/sse.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, CreaAstaComponent, QrDisplayComponent],
  template: `
    <div class="container">
      <h1>Fantasta — Console Banditore</h1>

      @if (!sseService.astaAttiva()) {
        <app-crea-asta></app-crea-asta>
      } @else {
        <app-qr-display></app-qr-display>
      }
    </div>
  `
})
export class AppComponent {
  constructor(public sseService: SseService) {}
}
