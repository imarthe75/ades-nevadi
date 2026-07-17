import { Component, OnInit, OnDestroy, ChangeDetectionStrategy, ChangeDetectorRef, inject, signal, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { ApiService } from '../../../core/services/api.service';
import { AdesFormatDirective } from '../../directives/ades-format.directive';

/**
 * Componente compartido de selección de domicilio con cascada SEPOMEX.
 * Implementa la cascada Estado→Municipio→Colonia usando datos del catálogo
 * postal almacenado en `public.ades_*` (no el schema sepomex legacy).
 * Emite el objeto dirección completo al componente padre vía Output.
 */
@Component({
  selector: 'app-selector-geo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,CommonModule, FormsModule, SelectModule, InputTextModule, ButtonModule, TooltipModule],
  template: `
    <div class="geo-grid">
      <!-- Búsqueda rápida por CP -->
      <div class="geo-cp">
        <label for="sg-cp">Código Postal</label>
        <div style="display:flex;gap:.5rem">
          <input pInputText id="sg-cp" [(ngModel)]="cpBusqueda" placeholder="Ej: 50100" maxlength="5"
                 style="width:100px" (keyup.enter)="buscarPorCP()"/>
          <p-button icon="pi pi-search" ariaLabel="Autocompletar por CP" [text]="true" (onClick)="buscarPorCP()"
                    pTooltip="Autocompletar por CP" />
        </div>
      </div>

      <!-- Estado -->
      <div>
        <label>Estado *</label>
        <p-select
          [options]="estados()"
          [(ngModel)]="estadoSeleccionado"
          optionLabel="nombre" optionValue="id"
          placeholder="Seleccionar estado"
          (onChange)="onEstadoChange()"
          [filter]="true" filterBy="nombre"
          styleClass="w-full" ariaLabel="Estado"/>
      </div>

      <!-- Municipio -->
      <div>
        <label>Municipio *</label>
        <p-select
          [options]="municipios()"
          [(ngModel)]="municipioSeleccionado"
          optionLabel="nombre" optionValue="id"
          placeholder="Seleccionar municipio"
          [disabled]="!estadoSeleccionado"
          (onChange)="onMunicipioChange()"
          [filter]="true" filterBy="nombre"
          styleClass="w-full" ariaLabel="Municipio"/>
      </div>

      <!-- Colonia -->
      <div>
        <label>Colonia</label>
        <p-select
          [options]="colonias()"
          [(ngModel)]="coloniaSeleccionada"
          optionLabel="colonia" optionValue="id"
          placeholder="Seleccionar colonia"
          [disabled]="!municipioSeleccionado"
          (onChange)="onColoniaChange()"
          [filter]="true" filterBy="colonia"
          styleClass="w-full" ariaLabel="Colonia"/>
      </div>
    </div>
  `,
  styles: [`
    .geo-grid { display: grid; grid-template-columns: auto 1fr 1fr 1fr; gap: .75rem; align-items: end; }
    .geo-cp { display: flex; flex-direction: column; gap: .25rem; }
    label { font-size: .82rem; color: var(--text-color-secondary); display: block; margin-bottom: .2rem; }
  `]
})
export class SelectorGeoComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroy$ = new Subject<void>();
  private cpTimeoutId?: ReturnType<typeof setTimeout>;

  @Input() estado: number | null = null;
  @Input() municipio: number | null = null;
  @Input() colonia: string | null = null;
  @Input() cp: string | null = null;

  @Output() estadoChange   = new EventEmitter<number | null>();
  @Output() municipioChange = new EventEmitter<number | null>();
  @Output() coloniaChange  = new EventEmitter<string | null>();
  @Output() cpChange       = new EventEmitter<string | null>();

  estados    = signal<any[]>([]);
  municipios = signal<any[]>([]);
  colonias   = signal<any[]>([]);

  estadoSeleccionado: number | null = null;
  municipioSeleccionado: number | null = null;
  coloniaSeleccionada: string | null = null;
  cpBusqueda: string = '';

  ngOnInit(): void {
    this.cargarEstados();
    if (this.estado) {
      this.estadoSeleccionado = this.estado;
      this.cargarMunicipios(this.estado);
    }
    if (this.municipio) {
      this.municipioSeleccionado = this.municipio;
      this.cargarColonias(this.municipio);
    }
    if (this.cp) this.cpBusqueda = this.cp;
  }

  cargarEstados(): void {
    this.api.get<any[]>('/geo/estados')
      .pipe(takeUntil(this.destroy$))
      .subscribe(e => this.estados.set(e));
  }

  onEstadoChange(): void {
    this.municipioSeleccionado = null;
    this.coloniaSeleccionada = null;
    this.municipios.set([]);
    this.colonias.set([]);
    this.estadoChange.emit(this.estadoSeleccionado);
    if (this.estadoSeleccionado) this.cargarMunicipios(this.estadoSeleccionado);
  }

  cargarMunicipios(estadoId: number): void {
    this.api.get<any[]>('/geo/municipios', { estado_id: estadoId })
      .pipe(takeUntil(this.destroy$))
      .subscribe(m => this.municipios.set(m));
  }

  onMunicipioChange(): void {
    this.coloniaSeleccionada = null;
    this.colonias.set([]);
    this.municipioChange.emit(this.municipioSeleccionado);
    if (this.municipioSeleccionado) this.cargarColonias(this.municipioSeleccionado);
  }

  cargarColonias(municipioId: number): void {
    this.api.get<any[]>('/geo/colonias', { municipio_id: municipioId })
      .pipe(takeUntil(this.destroy$))
      .subscribe(c => this.colonias.set(c));
  }

  onColoniaChange(): void {
    this.coloniaChange.emit(this.coloniaSeleccionada);
  }

  buscarPorCP(): void {
    if (!this.cpBusqueda || this.cpBusqueda.length !== 5) return;
    this.api.get<any>(`/geo/buscar-cp/${this.cpBusqueda}`)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: data => {
          if (!data) return;
          this.cpChange.emit(this.cpBusqueda);
          // Autocompletar estado
          this.estadoSeleccionado = data.estado_id;
          this.estadoChange.emit(data.estado_id);
          // Cargar municipios y autocompletar
          this.cargarMunicipios(data.estado_id);
          this.cdr.markForCheck();
          if (this.cpTimeoutId !== undefined) clearTimeout(this.cpTimeoutId);
          this.cpTimeoutId = setTimeout(() => {
            this.municipioSeleccionado = data.municipio_id;
            this.municipioChange.emit(data.municipio_id);
            // Colonias del CP
            this.colonias.set(data.colonias || []);
            this.cdr.markForCheck();
          }, 200);
        }
      });
  }

  ngOnDestroy(): void {
    if (this.cpTimeoutId !== undefined) clearTimeout(this.cpTimeoutId);
    this.destroy$.next();
    this.destroy$.complete();
  }
}
