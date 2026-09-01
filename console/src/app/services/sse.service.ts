import { Injectable, signal, computed, NgZone } from '@angular/core';

export interface SnapshotLotto {
  idLotto: number;
  idCalciatore: number;
  stato: string;
  offertaCorrente: number | null;
  offerenteCorrente: string | null;
  secondiResidui: number;
}

/** Un calciatore in rosa con il prezzo pagato. Il nome lo risolve il client dal listone. */
export interface VoceRosa {
  idCalciatore: number;
  prezzo: number;
}

export type Rosa = Record<'P' | 'D' | 'C' | 'A', VoceRosa[]>;

export interface SnapshotPartecipante {
  nome: string;
  codice: string;
  /** Dotazione iniziale. I crediti residui sono sempre totali - somma dei prezzi in rosa. */
  creditiTotali: number;
  crediti: number;
  rosa: Rosa;
}

export interface Snapshot {
  sequenza: number;
  lotto: SnapshotLotto | null;
  partecipanti: SnapshotPartecipante[];
  calciatoriAssegnati: number[];
  /**
   * Invariante della lega calcolato dal server: residui piu' impegnato in rosa, su tutti
   * i partecipanti. Non si muove alle aggiudicazioni. Si mostra, non si ricalcola.
   */
  creditiInCircolazione: number;
}

@Injectable({ providedIn: 'root' })
export class SseService {
  private snapshot = signal<Snapshot | null>(null);
  private connesso = signal(false);
  private connessionePersa = signal(false);
  private eventSource: EventSource | null = null;
  private heartbeatTimer: ReturnType<typeof setTimeout> | null = null;

  readonly snapshotCorrente = this.snapshot.asReadonly();
  readonly astaAttiva = computed(() => this.snapshot() !== null);
  readonly isConnessionePersa = this.connessionePersa.asReadonly();

  constructor(private zone: NgZone) {
    this.connetti();
  }

  private connetti(): void {
    if (this.eventSource) {
      this.eventSource.close();
    }

    this.eventSource = new EventSource('/api/sse');

    this.eventSource.addEventListener('snapshot', (e: MessageEvent) => {
      this.zone.run(() => {
        const data = JSON.parse(e.data) as Snapshot;
        this.snapshot.set(data);
        this.connesso.set(true);
        this.connessionePersa.set(false);
        this.resetHeartbeatTimer();
      });
    });

    this.eventSource.addEventListener('attesa', () => {
      this.zone.run(() => {
        this.snapshot.set(null);
        this.connesso.set(true);
        this.connessionePersa.set(false);
        this.resetHeartbeatTimer();
      });
    });

    this.eventSource.addEventListener('heartbeat', () => {
      this.zone.run(() => {
        this.resetHeartbeatTimer();
      });
    });

    this.eventSource.onerror = () => {
      this.zone.run(() => {
        this.connessionePersa.set(true);
      });
    };
  }

  private resetHeartbeatTimer(): void {
    if (this.heartbeatTimer) {
      clearTimeout(this.heartbeatTimer);
    }
    this.heartbeatTimer = setTimeout(() => {
      this.connessionePersa.set(true);
    }, 20_000);
  }
}
