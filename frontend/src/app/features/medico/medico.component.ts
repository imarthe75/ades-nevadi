/**
 * MedicoComponent — Expediente médico del alumno + historial de incidentes.
 *
 * Flujo: buscar alumno → ver/editar expediente base → ver incidentes → registrar nuevo incidente.
 */
import { Component, OnInit, inject, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { DividerModule } from 'primeng/divider';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TabsModule } from 'primeng/tabs';
import { SelectModule } from 'primeng/select';
import { TooltipModule } from 'primeng/tooltip';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import type { Estudiante } from '../../core/models';
import { grupoLabel } from '../../core/models';
import { ApexNotificationService, ApexTimelineComponent } from 'apex-component-library';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';

interface ExpedienteMedico {
  id: string;
  estudiante_id: string;
  tipo_sangre: string | null;
  alergias: string | null;
  medicamentos_autorizados: string | null;
  condiciones_cronicas: string | null;
  observaciones_generales: string | null;
}

interface IncidenteMedico {
  id: string;
  fecha_incidente: string;
  descripcion: string;
  tratamiento_aplicado: string | null;
  requirio_traslado: boolean;
  notificado_tutor: boolean;
}

interface Medicamento {
  id: string;
  nombre_medicamento: string;
  dosis: string;
  frecuencia: string;
  horario: string | null;
  via_administracion: string;
  prescrito_por: string | null;
  fecha_inicio: string | null;
  fecha_fin: string | null;
  observaciones: string | null;
  is_active: boolean;
}

@Component({
  selector: 'app-medico',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    ButtonModule, InputTextModule, TextareaModule, CardModule,
    TagModule, DialogModule, DividerModule, ToggleSwitchModule, TabsModule,
    SelectModule, TooltipModule, InteractiveGridComponent, ApexTimelineComponent
  ],
  template: `

    <div class="page-header">
      <div>
        <h2>Expediente Médico</h2>
        <p class="subtitle">Ficha médica y registro de incidentes por alumno</p>
      </div>
    </div>

    <!-- Filtros de Contexto Cascading (Plantel -> Nivel -> Grado -> Grupo -> Alumno) -->
    <div class="filter-bar">
      <p-select
        [options]="plantelesOpts()"
        [(ngModel)]="selectedPlantelId"
        optionLabel="nombre_plantel"
        optionValue="id"
        placeholder="Plantel"
        (onChange)="onPlantelChange()"
        [showClear]="!isPlantelDisabled()"
        [disabled]="isPlantelDisabled()"
        [filter]="true" filterPlaceholder="Buscar..."
        styleClass="filter-select" />

      <p-select
        [options]="nivelesOpts()"
        [(ngModel)]="selectedNivelId"
        optionLabel="nombre_nivel"
        optionValue="id"
        placeholder="Nivel"
        (onChange)="onNivelChange()"
        [showClear]="!isNivelDisabled()"
        [disabled]="isNivelDisabled() || !selectedPlantelId"
        [filter]="true" filterPlaceholder="Buscar..."
        styleClass="filter-select" />

      <p-select
        [options]="gradosOpts()"
        [(ngModel)]="selectedGradoId"
        optionLabel="nombre_grado"
        optionValue="id"
        placeholder="Grado"
        (onChange)="onGradoChange()"
        [showClear]="true"
        [disabled]="!selectedNivelId"
        [filter]="true" filterPlaceholder="Buscar..."
        styleClass="filter-select" />

      <p-select
        [options]="gruposOpts()"
        [(ngModel)]="selectedGrupoId"
        optionLabel="_label"
        optionValue="id"
        placeholder="Grupo"
        (onChange)="onGrupoChange()"
        [showClear]="true"
        [disabled]="!selectedGradoId"
        [filter]="true" filterPlaceholder="Buscar..."
        styleClass="filter-select" />

      <p-select
        [options]="alumnosOpts()"
        [(ngModel)]="selectedAlumnoId"
        optionLabel="_label"
        optionValue="id"
        placeholder="Alumno"
        (onChange)="onAlumnoChange()"
        [showClear]="true"
        [disabled]="!selectedGrupoId"
        [filter]="true" filterPlaceholder="Buscar..."
        styleClass="filter-select" />
    </div>

    <!-- Expediente del alumno seleccionado -->
    @if (alumnoSeleccionado()) {
      <div class="expediente-layout">

        <!-- Ficha base -->
        <p-card>
          <ng-template pTemplate="title">
            <div class="card-title-row">
              <span>{{ alumnoSeleccionado()!.persona?.apellido_paterno }} {{ alumnoSeleccionado()!.persona?.nombre }}</span>
              <span class="matricula-badge">{{ alumnoSeleccionado()!.matricula }}</span>
            </div>
          </ng-template>

          @if (expediente()) {
            <div class="expediente-form">
              <div class="field-row">
                <div class="field">
                  <label>Tipo de sangre</label>
                  <input pInputText [(ngModel)]="expediente()!.tipo_sangre" style="width:100px" />
                </div>
              </div>
              <div class="field">
                <label>Alergias</label>
                <textarea pTextarea [(ngModel)]="expediente()!.alergias" rows="2" style="width:100%"></textarea>
              </div>
              <div class="field">
                <label>Medicamentos autorizados</label>
                <textarea pTextarea [(ngModel)]="expediente()!.medicamentos_autorizados" rows="2" style="width:100%"></textarea>
              </div>
              <div class="field">
                <label>Condiciones crónicas</label>
                <textarea pTextarea [(ngModel)]="expediente()!.condiciones_cronicas" rows="2" style="width:100%"></textarea>
              </div>
              <div class="field">
                <label>Observaciones generales</label>
                <textarea pTextarea [(ngModel)]="expediente()!.observaciones_generales" rows="2" style="width:100%"></textarea>
              </div>
              <div class="flex-buttons">
                <p-button label="Guardar expediente" icon="pi pi-save" [loading]="savingExp()" (onClick)="guardarExpediente()" />
                <p-button label="Certificado Deportivo" icon="pi pi-file-pdf" severity="warn" (onClick)="descargarCertificado()" />
              </div>
            </div>
          } @else {
            <p style="color:var(--text-secondary)">Sin expediente médico registrado.</p>
            <p-button label="Crear expediente" icon="pi pi-plus" styleClass="mt-2" (onClick)="crearExpediente()" />
          }
        </p-card>

        <!-- Pestañas de Historial e Medicamentos -->
        <div>
          <p-tabs value="0">
            <p-tablist>
              <p-tab value="0"><i class="pi pi-exclamation-triangle"></i> Incidentes</p-tab>
              <p-tab value="1"><i class="pi pi-prescription"></i> Medicamentos Controlados</p-tab>
            </p-tablist>
            
            <p-tabpanels>
              <p-tabpanel value="0">
                <div class="tab-header-row">
                  <h3>Historial de Incidentes</h3>
                  <p-button label="Registrar Incidente" icon="pi pi-plus" [size]="'small'" (onClick)="abrirIncidente()" />
                </div>

                <apex-timeline
                  [value]="incidentesTimeline()"
                  title="Historial Médico"
                  [showRelativeTime]="true"
                  [showActor]="false"
                />
              </p-tabpanel>

              <p-tabpanel value="1">
                <div class="tab-header-row">
                  <h3>Medicamentos Administrados</h3>
                  <p-button label="Registrar Medicamento" icon="pi pi-plus" [size]="'small'" (onClick)="abrirMedicamento()" />
                </div>

                <app-interactive-grid
                  [data]="medicamentos()"
                  [columns]="medicamentosColumns"
                  [showDelete]="true"
                  (rowDeleted)="suspenderMedicamento($event.id)"
                />
              </p-tabpanel>
            </p-tabpanels>
          </p-tabs>
        </div>

      </div>
    }

    <!-- Dialog incidente -->
    <p-dialog [(visible)]="showIncidenteDialog" header="Registrar Incidente Médico" [modal]="true" [style]="{width:'460px'}">
      <div class="form-fields">
        <div class="field">
          <label>Descripción del incidente</label>
          <textarea pTextarea [(ngModel)]="incForm.descripcion" rows="3" style="width:100%"></textarea>
        </div>
        <div class="field">
          <label>Tratamiento aplicado / Medidas tomadas</label>
          <textarea pTextarea [(ngModel)]="incForm.tratamiento_aplicado" rows="2" style="width:100%"></textarea>
        </div>
        <div class="field-inline">
          <p-toggleSwitch [(ngModel)]="incForm.requirio_traslado" />
          <label>Requirió traslado de emergencia</label>
        </div>
        <div class="field-inline">
          <p-toggleSwitch [(ngModel)]="incForm.notificado_tutor" />
          <label>Tutor/Familia notificado inmediatamente</label>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="showIncidenteDialog = false" />
        <p-button label="Guardar y Crear Acta" icon="pi pi-save" [loading]="savingInc()" (onClick)="guardarIncidente()" />
      </ng-template>
    </p-dialog>

    <!-- Dialog medicamento -->
    <p-dialog [(visible)]="showMedicamentoDialog" header="Registrar Medicamento Controlado" [modal]="true" [style]="{width:'460px'}">
      <div class="form-fields">
        <div class="field">
          <label>Nombre del Medicamento *</label>
          <input pInputText [(ngModel)]="medForm.nombre_medicamento" />
        </div>
        <div class="field-row">
          <div class="field" style="flex:1">
            <label>Dosis *</label>
            <input pInputText [(ngModel)]="medForm.dosis" placeholder="Ej: 500mg, 1 tableta" />
          </div>
          <div class="field" style="flex:1">
            <label>Vía *</label>
            <p-select [options]="vias" [(ngModel)]="medForm.via_administracion" />
          </div>
        </div>
        <div class="field">
          <label>Frecuencia *</label>
          <input pInputText [(ngModel)]="medForm.frecuencia" placeholder="Ej: Cada 8 horas" />
        </div>
        <div class="field">
          <label>Horario sugerido</label>
          <input pInputText [(ngModel)]="medForm.horario" placeholder="Ej: 08:00, 16:00, 24:00" />
        </div>
        <div class="field">
          <label>Prescrito por</label>
          <input pInputText [(ngModel)]="medForm.prescrito_por" placeholder="Nombre del médico" />
        </div>
        <div class="field">
          <label>Observaciones o indicaciones adicionales</label>
          <textarea pTextarea [(ngModel)]="medForm.observaciones" rows="2" style="width:100%"></textarea>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="showMedicamentoDialog = false" />
        <p-button label="Registrar" icon="pi pi-save" [loading]="savingMed()" (onClick)="guardarMedicamento()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .filter-bar { display: flex; gap: 0.75rem; align-items: center; margin-bottom: 1.25rem; flex-wrap: wrap; }
    .filter-select { min-width: 220px; }
    .expediente-layout { display: grid; grid-template-columns: 1fr 1fr; gap: 1.25rem; margin-top: 1rem; }
    @media (max-width: 900px) { .expediente-layout { grid-template-columns: 1fr; } }
    .card-title-row { display: flex; justify-content: space-between; align-items: center; }
    .matricula-badge { background: var(--nevadi-red-lighter); color: var(--nevadi-red-dark); border-radius: 12px; padding: 0.15rem 0.6rem; font-size: 0.78rem; font-weight: 700; }
    .expediente-form { display: flex; flex-direction: column; gap: 0.75rem; }
    .field { display: flex; flex-direction: column; gap: 0.2rem; }
    .field label { font-size: 0.8rem; font-weight: 600; color: var(--text-secondary); }
    .field-row { display: flex; gap: 1rem; }
    .form-fields { display: flex; flex-direction: column; gap: 0.75rem; }
    .field-inline { display: flex; align-items: center; gap: 0.5rem; }
    .field-inline label { font-size: 0.85rem; }
    .flex-buttons { display: flex; gap: 0.75rem; margin-top: 0.75rem; }
    .tab-header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem; }
    .tab-header-row h4 { margin: 0; font-family: 'Jost', sans-serif; }
  `],
})
export class MedicoComponent implements OnInit {
  private readonly api = inject(ApiService);
  readonly ctx = inject(ContextService);
  private readonly notify = inject(ApexNotificationService);

  plantelesOpts = signal<any[]>([]);
  nivelesOpts = signal<any[]>([]);
  gradosOpts = signal<any[]>([]);
  gruposOpts = signal<any[]>([]);
  alumnosOpts = signal<Estudiante[]>([]);

  selectedPlantelId: string | null = null;
  selectedNivelId: string | null = null;
  selectedGradoId: string | null = null;
  selectedGrupoId: string | null = null;
  selectedAlumnoId: string | null = null;

  readonly isPlantelDisabled = computed(() => this.ctx.nivelAcceso() > 2);
  readonly isNivelDisabled = computed(() => this.ctx.nivelAcceso() > 3);

  alumnoSeleccionado = signal<Estudiante | null>(null);
  expediente = signal<ExpedienteMedico | null>(null);
  incidentes = signal<IncidenteMedico[]>([]);
  medicamentos = signal<Medicamento[]>([]);
  
  savingExp = signal(false);
  savingInc = signal(false);
  savingMed = signal(false);
  
  showIncidenteDialog = false;
  showMedicamentoDialog = false;
  
  incForm = { descripcion: '', tratamiento_aplicado: '', requirio_traslado: false, notificado_tutor: false };
  medForm = { nombre_medicamento: '', dosis: '', frecuencia: '', horario: '', via_administracion: 'ORAL', prescrito_por: '', observaciones: '' };
  
  readonly vias = ['ORAL', 'TOPICA', 'INHALADA', 'INYECTABLE', 'OFTALMICA', 'OTICA'];

  readonly incidentesTimeline = computed(() =>
    this.incidentes().map(inc => ({
      title: inc.descripcion,
      date: inc.fecha_incidente
        ? new Date(inc.fecha_incidente).toLocaleDateString('es-MX')
        : '—',
      description: [
        inc.tratamiento_aplicado ? `Tratamiento: ${inc.tratamiento_aplicado}` : null,
        inc.requirio_traslado ? 'Requirió traslado de emergencia' : null,
        inc.notificado_tutor  ? 'Tutor notificado' : null,
      ].filter(Boolean).join(' · ') || undefined,
      severity: inc.requirio_traslado ? 'error' : 'warning',
      icon: inc.requirio_traslado ? 'pi pi-exclamation-triangle' : 'pi pi-heart',
    }))
  );

  readonly medicamentosColumns: ColumnConfig[] = [
    { field: 'nombre_medicamento', header: 'Medicamento' },
    { field: 'dosis',              header: 'Dosis',       width: '100px' },
    { field: 'frecuencia',         header: 'Frecuencia',  width: '110px' },
    { field: 'via_administracion', header: 'Vía',         width: '110px' },
  ];

  constructor() {
    effect(() => {
      const p = this.ctx.plantel();
      if (p?.id && !this.selectedPlantelId) {
        this.selectedPlantelId = p.id;
        this.onPlantelChange();
      }
    });
  }

  ngOnInit(): void {
    this.api.get<any[]>('/planteles').subscribe({
      next: p => {
        this.plantelesOpts.set(p);
        const currentPlantel = this.ctx.plantel();
        if (currentPlantel?.id) {
          this.selectedPlantelId = currentPlantel.id;
          this.onPlantelChange();
        }
      },
      error: () => {}
    });
  }

  onPlantelChange(): void {
    this.selectedNivelId = null;
    this.selectedGradoId = null;
    this.selectedGrupoId = null;
    this.selectedAlumnoId = null;
    this.nivelesOpts.set([]);
    this.gradosOpts.set([]);
    this.gruposOpts.set([]);
    this.alumnosOpts.set([]);
    this.alumnoSeleccionado.set(null);

    if (!this.selectedPlantelId) return;

    this.api.get<any[]>(`/planteles/${this.selectedPlantelId}/niveles`).subscribe({
      next: ns => {
        const mapped = ns.map(x => ({ id: x.id ?? x.nivel_id, nombre_nivel: x.nombre_nivel }));
        this.nivelesOpts.set(mapped);
        
        const ctxNivel = this.ctx.nivel();
        if (ctxNivel && mapped.some(n => n.id === ctxNivel.id)) {
          this.selectedNivelId = ctxNivel.id;
          this.onNivelChange();
        }
      },
      error: () => {}
    });
  }

  onNivelChange(): void {
    this.selectedGradoId = null;
    this.selectedGrupoId = null;
    this.selectedAlumnoId = null;
    this.gradosOpts.set([]);
    this.gruposOpts.set([]);
    this.alumnosOpts.set([]);
    this.alumnoSeleccionado.set(null);

    if (!this.selectedNivelId) return;

    this.api.get<any[]>(`/catalogs/grados`, { nivel_id: this.selectedNivelId }).subscribe({
      next: gs => {
        this.gradosOpts.set(gs);
      },
      error: () => {}
    });
  }

  onGradoChange(): void {
    this.selectedGrupoId = null;
    this.selectedAlumnoId = null;
    this.gruposOpts.set([]);
    this.alumnosOpts.set([]);
    this.alumnoSeleccionado.set(null);

    if (!this.selectedGradoId) return;

    const params: Record<string, any> = { solo_activos: true, ciclo_vigente: true };
    if (this.selectedPlantelId) params['plantel_id'] = this.selectedPlantelId;
    if (this.selectedGradoId) params['grado_id'] = this.selectedGradoId;

    this.api.get<any[]>('/grupos', params).subscribe({
      next: gps => {
        this.gruposOpts.set(gps.map(x => ({ ...x, _label: grupoLabel(x) })));
      },
      error: () => this.gruposOpts.set([])
    });
  }

  onGrupoChange(): void {
    this.selectedAlumnoId = null;
    this.alumnosOpts.set([]);
    this.alumnoSeleccionado.set(null);

    if (!this.selectedGrupoId) return;

    const params: Record<string, any> = { grupo_id: this.selectedGrupoId };
    if (this.selectedPlantelId) params['plantel_id'] = this.selectedPlantelId;

    this.api.get<any>('/alumnos', params).subscribe({
      next: resp => {
        const list = resp.data ?? resp;
        this.alumnosOpts.set(list.map((a: any) => ({
          ...a,
          _label: `${a.persona?.apellido_paterno ?? ''} ${a.persona?.nombre ?? ''}`.trim() || a.matricula
        })));
      },
      error: () => this.alumnosOpts.set([])
    });
  }

  onAlumnoChange(): void {
    this.alumnoSeleccionado.set(null);
    if (!this.selectedAlumnoId) return;
    const selected = this.alumnosOpts().find(a => a.id === this.selectedAlumnoId);
    if (selected) {
      this.seleccionar(selected);
    }
  }

  seleccionar(a: Estudiante): void {
    this.alumnoSeleccionado.set(a);
    this.cargarExpediente(a.id);
    this.cargarIncidentes(a.id);
    this.cargarMedicamentos(a.id);
  }

  private cargarExpediente(id: string): void {
    this.api.get<ExpedienteMedico>(`/expedientes-medicos/alumno/${id}`)
      .subscribe({ next: e => this.expediente.set(e), error: () => this.expediente.set(null) });
  }

  private cargarIncidentes(id: string): void {
    this.api.get<IncidenteMedico[]>(`/incidentes-medicos/alumno/${id}`)
      .subscribe(r => this.incidentes.set(r));
  }

  private cargarMedicamentos(id: string): void {
    this.api.get<Medicamento[]>(`/salud-avanzada/medicamentos/${id}`)
      .subscribe(r => this.medicamentos.set(r));
  }

  crearExpediente(): void {
    const alumno = this.alumnoSeleccionado();
    if (!alumno) return;
    this.api.post<ExpedienteMedico>('/expedientes-medicos', { estudiante_id: alumno.id }).subscribe({
      next: e => { this.expediente.set(e); this.notify.success('Expediente creado'); },
      error: (e) => this.notify.error('Error', e.error?.detail),
    });
  }

  guardarExpediente(): void {
    const exp = this.expediente();
    if (!exp) return;
    this.savingExp.set(true);
    const { estudiante_id, ...datos } = exp as any;
    this.api.put(`/expedientes-medicos/${exp.id}`, datos).subscribe({
      next: () => { this.savingExp.set(false); this.notify.success('Expediente actualizado'); },
      error: () => this.savingExp.set(false),
    });
  }

  abrirIncidente(): void {
    this.incForm = { descripcion: '', tratamiento_aplicado: '', requirio_traslado: false, notificado_tutor: false };
    this.showIncidenteDialog = true;
  }

  guardarIncidente(): void {
    const alumno = this.alumnoSeleccionado();
    if (!alumno || !this.incForm.descripcion) return;
    this.savingInc.set(true);
    
    // Primero registrar el incidente médico básico
    const payload = { ...this.incForm, estudiante_id: alumno.id };
    this.api.post<IncidenteMedico>('/incidentes-medicos', payload).subscribe({
      next: (r) => {
        // Generar acta formal en el backend (SB-005)
        const actaPayload = {
          descripcion_detallada: this.incForm.descripcion,
          medidas_tomadas: this.incForm.tratamiento_aplicado || 'Primeros auxilios',
          requirio_traslado: this.incForm.requirio_traslado,
          notificado_familia: this.incForm.notificado_tutor
        };
        
        this.api.post(`/salud-avanzada/actas-incidente/${r.id}`, actaPayload).subscribe({
          next: () => {
            this.incidentes.update(list => [r, ...list]);
            this.showIncidenteDialog = false;
            this.savingInc.set(false);
            this.notify.success('Incidente registrado y Acta formal generada');
          },
          error: () => {
            this.incidentes.update(list => [r, ...list]);
            this.showIncidenteDialog = false;
            this.savingInc.set(false);
            this.notify.warning('Incidente registrado pero falló la generación de acta formal.');
          }
        });
      },
      error: () => this.savingInc.set(false),
    });
  }

  abrirMedicamento(): void {
    this.medForm = { nombre_medicamento: '', dosis: '', frecuencia: '', horario: '', via_administracion: 'ORAL', prescrito_por: '', observaciones: '' };
    this.showMedicamentoDialog = true;
  }

  guardarMedicamento(): void {
    const alumno = this.alumnoSeleccionado();
    if (!alumno || !this.medForm.nombre_medicamento || !this.medForm.dosis || !this.medForm.frecuencia) {
      this.notify.warning('Completa todos los campos obligatorios');
      return;
    }
    this.savingMed.set(true);
    this.api.post<any>(`/salud-avanzada/medicamentos/${alumno.id}`, this.medForm).subscribe({
      next: () => {
        this.cargarMedicamentos(alumno.id);
        this.showMedicamentoDialog = false;
        this.savingMed.set(false);
        this.notify.success('Medicamento registrado');
      },
      error: () => this.savingMed.set(false),
    });
  }

  suspenderMedicamento(medId: string): void {
    const alumno = this.alumnoSeleccionado();
    if (!alumno) return;
    this.api.delete(`/salud-avanzada/medicamentos/${medId}`).subscribe({
      next: () => {
        this.cargarMedicamentos(alumno.id);
        this.notify.success('Medicamento suspendido');
      },
      error: () => this.notify.error('Error al suspender medicamento'),
    });
  }

  descargarCertificado(): void {
    const alumno = this.alumnoSeleccionado();
    if (!alumno) return;
    window.open(`/api/v1/salud-avanzada/certificado-deportivo/${alumno.id}`, '_blank');
  }

  descargarActa(incidenteId: string): void {
    window.open(`/api/v1/salud-avanzada/incidentes/${incidenteId}/acta-pdf`, '_blank');
  }
}
