import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { FileUploadModule } from 'primeng/fileupload';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { CheckboxModule } from 'primeng/checkbox';
import { TableModule } from 'primeng/table';
import { MessageModule } from 'primeng/message';

interface RispostaAnalisi {
  tipoAsta: string;
  calciatori: number;
  fuoriLista: number;
  partecipanti?: string[];
}

interface PartecipanteForm {
  nome: string;
  crediti: number;
}

interface CreditiResiduiForm {
  nome: string;
  creditiResidui: number;
}

@Component({
  selector: 'app-crea-asta',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ButtonModule, FileUploadModule,
    InputTextModule, InputNumberModule, CheckboxModule, TableModule, MessageModule
  ],
  template: `
    <!-- PASSO 1: Upload xlsx -->
    @if (!analisi()) {
      <div class="passo">
        <h2>Passo 1 — Carica il listone</h2>
        <p-fileUpload
          mode="basic"
          accept=".xlsx"
          [auto]="true"
          chooseLabel="Scegli file xlsx"
          url="/api/console/analizza-listone"
          name="file"
          (onUpload)="onUploadRiuscito($event)"
          (onError)="onUploadErrore($event)">
        </p-fileUpload>
        @if (errore()) {
          <p-message severity="error" [text]="errore()!"></p-message>
        }
      </div>
    }

    <!-- PASSO 2: Configurazione asta -->
    @if (analisi()) {
      <div class="passo">
        <h2>Passo 2 — Configura l'asta</h2>

        <div class="info-analisi">
          <p><strong>Tipo asta:</strong> {{ analisi()!.tipoAsta }}</p>
          <p><strong>Calciatori:</strong> {{ analisi()!.calciatori }} ({{ analisi()!.fuoriLista }} fuori lista)</p>
        </div>

        <div class="campo">
          <label for="nomeAsta">Nome asta</label>
          <input pInputText id="nomeAsta" [(ngModel)]="nomeAsta" placeholder="Es. Campionato Ronchetto 2026-27">
        </div>

        <div class="campo">
          <label for="durataCountdown">Durata countdown (secondi)</label>
          <p-inputNumber id="durataCountdown" [(ngModel)]="durataCountdown" [min]="1" [max]="300"></p-inputNumber>
        </div>

        <div class="campo">
          <p-checkbox [(ngModel)]="banditorePartecipa" [binary]="true" label="Il banditore partecipa"></p-checkbox>
        </div>

        <!-- Asta iniziale: tabella partecipanti -->
        @if (analisi()!.tipoAsta === 'INIZIALE') {
          <h3>Partecipanti</h3>
          <p-table [value]="partecipanti()" [tableStyle]="{'min-width': '30rem'}">
            <ng-template pTemplate="header">
              <tr>
                <th>Nome</th>
                <th>Crediti</th>
                <th></th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-p let-i="rowIndex">
              <tr>
                <td><input pInputText [(ngModel)]="p.nome" placeholder="Nome partecipante"></td>
                <td><p-inputNumber [(ngModel)]="p.crediti" [min]="1"></p-inputNumber></td>
                <td><button pButton icon="pi pi-trash" class="p-button-danger p-button-text" (click)="rimuoviPartecipante(i)"></button></td>
              </tr>
            </ng-template>
          </p-table>
          <button pButton label="Aggiungi partecipante" icon="pi pi-plus" class="p-button-outlined" (click)="aggiungiPartecipante()" style="margin-top: 8px"></button>
        }

        <!-- Asta riparazione: tabella crediti residui -->
        @if (analisi()!.tipoAsta === 'RIPARAZIONE') {
          <h3>Crediti residui per partecipante</h3>
          <p-table [value]="creditiResidui()" [tableStyle]="{'min-width': '30rem'}">
            <ng-template pTemplate="header">
              <tr>
                <th>Nome (da file)</th>
                <th>Crediti residui</th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-cr>
              <tr>
                <td>{{ cr.nome }}</td>
                <td><p-inputNumber [(ngModel)]="cr.creditiResidui" [min]="0"></p-inputNumber></td>
              </tr>
            </ng-template>
          </p-table>
        }

        <div style="margin-top: 16px">
          <button pButton label="Crea asta" icon="pi pi-check" (click)="creaAsta()" [disabled]="creando()"></button>
          <button pButton label="Annulla" icon="pi pi-times" class="p-button-secondary" (click)="annulla()" style="margin-left: 8px"></button>
        </div>

        @if (errore()) {
          <div style="margin-top: 12px"><p-message severity="error" [text]="errore()!"></p-message></div>
        }
      </div>
    }
  `,
  styles: [`
    .passo { margin-top: 24px; }
    .info-analisi { background: #262640; padding: 12px 16px; border-radius: 6px; margin-bottom: 16px; }
    .info-analisi p { margin: 4px 0; }
    .campo { margin-bottom: 12px; }
    .campo label { display: block; margin-bottom: 4px; color: #aaa; }
    .campo input, .campo p-inputNumber { width: 100%; }
  `]
})
export class CreaAstaComponent {
  analisi = signal<RispostaAnalisi | null>(null);
  errore = signal<string | null>(null);
  creando = signal(false);

  nomeAsta = '';
  durataCountdown = 30;
  banditorePartecipa = false;

  partecipanti = signal<PartecipanteForm[]>([
    { nome: '', crediti: 500 },
    { nome: '', crediti: 500 }
  ]);

  creditiResidui = signal<CreditiResiduiForm[]>([]);

  constructor(private http: HttpClient) {}

  onUploadRiuscito(event: any): void {
    const risposta = JSON.parse(event.xhr.responseText) as RispostaAnalisi;
    this.analisi.set(risposta);
    this.errore.set(null);

    if (risposta.tipoAsta === 'RIPARAZIONE' && risposta.partecipanti) {
      this.creditiResidui.set(
        risposta.partecipanti.map(nome => ({ nome, creditiResidui: 0 }))
      );
    }
  }

  onUploadErrore(event: any): void {
    let msg = 'Errore nel caricamento del file';
    if (event.xhr && event.xhr.responseText) {
      try {
        const body = JSON.parse(event.xhr.responseText);
        msg = body.errore || msg;
      } catch { /* ignora */ }
    }
    this.errore.set(msg);
  }

  aggiungiPartecipante(): void {
    this.partecipanti.update(list => [...list, { nome: '', crediti: 500 }]);
  }

  rimuoviPartecipante(index: number): void {
    this.partecipanti.update(list => list.filter((_, i) => i !== index));
  }

  annulla(): void {
    this.analisi.set(null);
    this.errore.set(null);
  }

  creaAsta(): void {
    this.errore.set(null);
    this.creando.set(true);

    let body: Record<string, unknown>;

    if (this.analisi()!.tipoAsta === 'INIZIALE') {
      const validi = this.partecipanti().filter(p => p.nome.trim());
      if (validi.length === 0) {
        this.errore.set('Inserire almeno un partecipante');
        this.creando.set(false);
        return;
      }
      body = {
        nomeAsta: this.nomeAsta,
        durataCountdown: this.durataCountdown,
        banditorePartecipa: this.banditorePartecipa,
        partecipanti: validi.map(p => ({ nome: p.nome.trim(), crediti: p.crediti }))
      };
    } else {
      const cr: Record<string, number> = {};
      for (const entry of this.creditiResidui()) {
        cr[entry.nome] = entry.creditiResidui;
      }
      body = {
        nomeAsta: this.nomeAsta,
        durataCountdown: this.durataCountdown,
        banditorePartecipa: this.banditorePartecipa,
        creditiResidui: cr
      };
    }

    this.http.post<{ partecipanti: { nome: string; codice: string }[] }>(
      '/api/console/crea-asta', body
    ).subscribe({
      next: () => {
        this.creando.set(false);
      },
      error: (err) => {
        let msg = 'Errore nella creazione dell\'asta';
        if (err.error?.errore) {
          msg = err.error.errore;
        }
        this.errore.set(msg);
        this.creando.set(false);
      }
    });
  }
}
