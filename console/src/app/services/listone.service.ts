import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface Calciatore {
  id: number;
  nome: string;
  ruolo: string;
  squadra: string;
  quotazione: number;
  fuoriLista: boolean;
}

@Injectable({ providedIn: 'root' })
export class ListoneService {
  private calciatori = signal<Calciatore[]>([]);
  private caricato = false;

  readonly listone = this.calciatori.asReadonly();

  constructor(private http: HttpClient) {}

  carica(): void {
    if (this.caricato) return;
    this.caricato = true;

    this.http.get<Calciatore[]>('/api/listone').subscribe({
      next: (dati) => this.calciatori.set(dati),
      error: () => {
        this.caricato = false;
      }
    });
  }

  trovaCalciatore(id: number): Calciatore | undefined {
    return this.listone().find(c => c.id === id);
  }
}
