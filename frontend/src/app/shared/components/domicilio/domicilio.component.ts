/**
 * DomicilioComponent — Gestión de direcciones y medios de contacto de una persona.
 * Reusable: se embebe en alumno-perfil, profesor-perfil, y cualquier formulario de actor.
 * Integra catálogo SEPOMEX (ades_codigos_postales / ades_localidades) para cascada CP→Asentamiento→Municipio→Estado.
 * Geocodificación vía Nominatim (OpenStreetMap, sin clave API, uso razonable).
 */
import {
  Component, Input, OnChanges, OnInit, OnDestroy, SimpleChanges, ChangeDetectionStrategy, ChangeDetectorRef, inject, signal, computed
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Subject, takeUntil } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { TagModule } from 'primeng/tag';
import { DividerModule } from 'primeng/divider';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService } from 'primeng/api';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ApexNotificationService } from 'apex-component-library';
import { ApiService } from '../../../core/services/api.service';
import { AdesFormatDirective } from '../../directives/ades-format.directive';

// ─── Catálogos locales ────────────────────────────────────────────────────────
const TIPOS_DIR   = ['PRINCIPAL','TRABAJO','TEMPORAL','ANTERIOR','CORRESPONDENCIA'];
const TIPOS_VIA   = ['CALLE','AVENIDA','BOULEVARD','CALZADA','PRIVADA','CERRADA','ANDADOR','CARRETERA','CAMINO','OTRO'];
const MEDIOS      = ['CELULAR','FIJO','WHATSAPP','EMAIL','TELEGRAM','FAX','OTRO'];
const TIPOS_CONT  = ['PERSONAL','TRABAJO','FAMILIAR','INSTITUCIONAL','EMERGENCIA'];

interface SepomexRow {
  cp_id: string;
  localidad_id: string;
  codigo_postal: string;
  nombre_localidad: string;
  tipo_asentamiento: string;
  nombre_municipio: string;
  nombre_estado: string;
  nombre_pais: string;
}

interface Direccion {
  id: string;
  tipo_direccion: string;
  es_principal: boolean;
  tipo_via: string | null;
  calle: string | null;
  numero_exterior: string | null;
  numero_interior: string | null;
  entre_calle_1: string | null;
  entre_calle_2: string | null;
  referencia: string | null;
  codigo_postal: string | null;
  nombre_localidad: string | null;
  tipo_asentamiento: string | null;
  nombre_municipio: string | null;
  nombre_estado: string | null;
  nombre_pais: string | null;
  latitud: number | null;
  longitud: number | null;
  row_version: number;
}

interface PersonaContacto {
  id: string;
  medio: string;
  tipo: string;
  valor: string;
  etiqueta: string | null;
  es_principal: boolean;
  orden: number;
  notas: string | null;
  row_version: number;
}

@Component({
  selector: 'app-domicilio',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule,
    ButtonModule, InputTextModule, SelectModule, TextareaModule,
    TagModule, DividerModule, TooltipModule,
    ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  template: `
    <p-confirmDialog />

    @if (!personaId) {
      <div class="empty-state">
        <i class="pi pi-info-circle"></i>
        Guarda el perfil principal antes de gestionar domicilios y contactos.
      </div>
    } @else {

      <!-- ═══ SECCIÓN 1: Medios de Contacto ═══════════════════════════════ -->
      <div class="section-header">
        <h4 class="sec-title">
          <i class="pi pi-phone"></i> Medios de Contacto
        </h4>
        <p-button label="Agregar" icon="pi pi-plus" size="small" [text]="true"
          (onClick)="abrirNuevoContacto()" />
      </div>

      <!-- Lista de contactos agrupados por medio -->
      @if (contactos().length === 0 && !editandoContacto()) {
        <p class="empty-msg">Sin medios de contacto registrados.</p>
      }

      <div class="contactos-list">
        @for (c of contactos(); track c.id) {
          <div class="contacto-item" [class.principal]="c.es_principal">
            <div class="contacto-icon">
              <i [class]="iconMedio(c.medio)"></i>
            </div>
            <div class="contacto-info">
              <span class="contacto-valor">{{ c.valor }}</span>
              <span class="contacto-meta">
                {{ c.medio }} · {{ c.tipo }}
                @if (c.etiqueta) { <em>— {{ c.etiqueta }}</em> }
              </span>
            </div>
            <div class="contacto-actions">
              @if (c.es_principal) { <p-tag value="Principal" severity="success" /> }
              <p-button icon="pi pi-pencil" ariaLabel="Editar contacto" [text]="true" [rounded]="true" size="small"
                (onClick)="editarContacto(c)" />
              <p-button icon="pi pi-trash" ariaLabel="Eliminar contacto" [text]="true" [rounded]="true" size="small"
                [loading]="eliminandoContactoId() === c.id"
                severity="danger" (onClick)="eliminarContacto(c.id)" />
            </div>
          </div>
        }
      </div>

      <!-- Formulario inline contacto -->
      @if (editandoContacto()) {
        <div class="inline-form">
          <h5>{{ contactoEdit.id ? 'Editar' : 'Nuevo' }} medio de contacto</h5>
          <div class="form-grid">
            <div class="form-row">
              <label>Medio *</label>
              <p-select [options]="mediosOpts" [(ngModel)]="contactoEdit.medio"
                optionLabel="label" optionValue="value" ariaLabel="Medio"/>
            </div>
            <div class="form-row">
              <label>Tipo</label>
              <p-select [options]="tiposContOpts" [(ngModel)]="contactoEdit.tipo"
                optionLabel="label" optionValue="value" ariaLabel="Tipo"/>
            </div>
            <div class="form-row full">
              <label for="dom-contacto-valor">{{ contactoEdit.medio === 'EMAIL' ? 'Correo electrónico *' : 'Número *' }}</label>
              <input pInputText id="dom-contacto-valor" [(ngModel)]="contactoEdit.valor"
                [adesFormat]="contactoEdit.medio === 'EMAIL' ? 'email' : (contactoEdit.medio === 'CELULAR' || contactoEdit.medio === 'FIJO' || contactoEdit.medio === 'WHATSAPP' ? 'telefono' : 'safe')"
                [type]="contactoEdit.medio === 'EMAIL' ? 'email' : 'tel'"
                [placeholder]="contactoEdit.medio === 'EMAIL' ? 'usuario@correo.com' : '10 dígitos'"/>
            </div>
            <div class="form-row full">
              <label for="dom-contacto-etiqueta">Etiqueta</label>
              <input pInputText id="dom-contacto-etiqueta" [(ngModel)]="contactoEdit.etiqueta"
                placeholder="Ej: Celular mamá, WhatsApp trabajo" maxlength="50"/>
            </div>
            <div class="form-row">
              <label for="dom-contacto-notas">Notas</label>
              <input pInputText id="dom-contacto-notas" [(ngModel)]="contactoEdit.notas" maxlength="200"/>
            </div>
            <div class="form-row">
              <label for="dom-contacto-principal">¿Principal?</label>
              <input type="checkbox" id="dom-contacto-principal" [(ngModel)]="contactoEdit.es_principal"/>
            </div>
          </div>
          <div class="form-btns">
            <p-button label="Cancelar" severity="secondary" [text]="true" size="small"
              (onClick)="cancelarContacto()" />
            <p-button label="Guardar" icon="pi pi-save" size="small"
              [loading]="savingContacto()" (onClick)="guardarContacto()" />
          </div>
        </div>
      }

      <p-divider />

      <!-- ═══ SECCIÓN 2: Domicilios ════════════════════════════════════════ -->
      <div class="section-header">
        <h4 class="sec-title">
          <i class="pi pi-map-marker"></i> Domicilios
        </h4>
        <p-button label="Agregar" icon="pi pi-plus" size="small" [text]="true"
          (onClick)="abrirNuevaDireccion()" />
      </div>

      @if (direcciones().length === 0 && !editandoDireccion()) {
        <p class="empty-msg">Sin domicilios registrados.</p>
      }

      <div class="dirs-list">
        @for (d of direcciones(); track d.id) {
          <div class="dir-card" [class.principal]="d.es_principal">
            <div class="dir-header">
              <div>
                <strong>{{ d.tipo_direccion }}</strong>
                @if (d.es_principal) { <p-tag value="Principal" severity="success" /> }
              </div>
              <div class="dir-actions">
                @if (!d.es_principal) {
                  <p-button label="Marcar principal" [text]="true" size="small"
                    [loading]="estableciendoPrincipalId() === d.id"
                    (onClick)="setPrincipal(d.id)" />
                }
                <p-button icon="pi pi-pencil" ariaLabel="Editar dirección" [text]="true" [rounded]="true" size="small"
                  (onClick)="editarDireccion(d)" />
                <p-button icon="pi pi-trash" ariaLabel="Eliminar dirección" [text]="true" [rounded]="true" size="small"
                  [loading]="eliminandoDireccionId() === d.id"
                  severity="danger" (onClick)="eliminarDireccion(d.id)" />
              </div>
            </div>
            <div class="dir-detalle">
              {{ formatDireccion(d) }}
            </div>
            @if (d.nombre_localidad) {
              <div class="dir-ubic">
                <i class="pi pi-map-marker"></i>
                {{ d.nombre_localidad }}
                @if (d.tipo_asentamiento) { ({{ d.tipo_asentamiento }}) }
                @if (d.nombre_municipio) { , {{ d.nombre_municipio }} }
                @if (d.nombre_estado) { , {{ d.nombre_estado }} }
                @if (d.codigo_postal) { CP {{ d.codigo_postal }} }
              </div>
            }
            @if (d.latitud && d.longitud) {
              <div class="dir-mapa">
                <a [href]="urlMapa(d.latitud, d.longitud)" target="_blank" class="mapa-link">
                  <i class="pi pi-external-link"></i> Ver en mapa
                </a>
                <span class="coords">{{ d.latitud | number:'1.6-6' }}, {{ d.longitud | number:'1.6-6' }}</span>
              </div>
            }
          </div>
        }
      </div>

      <!-- Formulario inline dirección -->
      @if (editandoDireccion()) {
        <div class="inline-form dir-form">
          <h5>{{ dirEdit.id ? 'Editar' : 'Nueva' }} dirección</h5>

          <!-- SEPOMEX -->
          <div class="sepomex-block">
            <!-- CP input -->
            <div class="form-row">
              <label for="dom-cp">Código Postal *</label>
              <div class="cp-input-wrap">
                <input pInputText id="dom-cp" [(ngModel)]="cpBusqueda"
                  adesFormat="cp"
                  (ngModelChange)="onCpChange($event)"
                  maxlength="5" placeholder="5 dígitos"
                  style="font-family:monospace;width:100px"/>
                @if (buscandoCp()) {
                  <i class="pi pi-spin pi-spinner" style="margin-left:.5rem"></i>
                }
                @if (cpSelId()) {
                  <p-button icon="pi pi-times-circle" ariaLabel="Limpiar para captura manual" [text]="true" size="small"
                    severity="secondary" pTooltip="Limpiar para captura manual"
                    (onClick)="limpiarSeleccionCp()" styleClass="ml-1" />
                }
              </div>
            </div>

            <!-- LOV de asentamientos -->
            @if (asentamientos().length > 0) {
              <div class="form-row">
                <label>Colonia / Asentamiento *</label>
                <p-select
                  [options]="asentamientoOpts()"
                  [ngModel]="cpSelId()"
                  (ngModelChange)="cpSelId.set($event)"
                  optionLabel="label" optionValue="value"
                  [filter]="true" filterPlaceholder="Buscar colonia…"
                  placeholder="Seleccionar asentamiento…"
                  (onChange)="onAsentamientoSelect($event)"
                  style="width:100%" ariaLabel="Colonia / Asentamiento"/>
              </div>
            } @else if (cpBusqueda.length === 5 && !buscandoCp()) {
              <p class="sepomex-nota">
                <i class="pi pi-info-circle"></i>
                CP no encontrado — captura el asentamiento manualmente.
              </p>
            }

            <!-- Geolocalización: bloqueada si viene de SEPOMEX, editable si es manual -->
            @if (cpSelId()) {
              <!-- Modo bloqueado: auto-llenado desde SEPOMEX -->
              <div class="sep-locked">
                <div class="sep-locked-row">
                  <span class="sep-locked-lbl">Colonia</span>
                  <span class="sep-locked-val">
                    <i class="pi pi-lock"></i> {{ dirEdit.nombre_localidad }}
                  </span>
                </div>
                <div class="sep-locked-row">
                  <span class="sep-locked-lbl">Municipio</span>
                  <span class="sep-locked-val">
                    <i class="pi pi-lock"></i> {{ dirEdit.nombre_municipio }}
                  </span>
                </div>
                <div class="sep-locked-row">
                  <span class="sep-locked-lbl">Estado</span>
                  <span class="sep-locked-val">
                    <i class="pi pi-lock"></i> {{ dirEdit.nombre_estado }}
                  </span>
                </div>
                <div class="sep-locked-row">
                  <span class="sep-locked-lbl">País</span>
                  <span class="sep-locked-val">
                    <i class="pi pi-lock"></i> {{ dirEdit.nombre_pais }}
                  </span>
                </div>
              </div>
            } @else {
              <!-- Modo manual: editable cuando no hay CP seleccionado -->
              <div class="form-row">
                <label for="dom-colonia">Colonia</label>
                <input pInputText id="dom-colonia" [(ngModel)]="dirEdit.nombre_localidad"
                  placeholder="Colonia, Fraccionamiento, Ejido…"/>
              </div>
              <div class="form-row">
                <label for="dom-municipio">Municipio</label>
                <input pInputText id="dom-municipio" [(ngModel)]="dirEdit.nombre_municipio"
                  placeholder="Municipio o Alcaldía"/>
              </div>
              <div class="form-row">
                <label for="dom-estado">Estado</label>
                <input pInputText id="dom-estado" [(ngModel)]="dirEdit.nombre_estado"
                  placeholder="Estado o Entidad federativa"/>
              </div>
              <div class="form-row">
                <label for="dom-pais">País</label>
                <input pInputText id="dom-pais" [(ngModel)]="dirEdit.nombre_pais"/>
              </div>
            }
          </div>

          <p-divider align="left"><small>Vialidad</small></p-divider>

          <div class="form-grid">
            <div class="form-row">
              <label>Tipo de vía</label>
              <p-select [options]="tiposViaOpts" [(ngModel)]="dirEdit.tipo_via"
                optionLabel="label" optionValue="value" [showClear]="true" ariaLabel="Tipo de vía"/>
            </div>
            <div class="form-row">
              <label for="dom-calle">Nombre de calle</label>
              <input pInputText id="dom-calle" [(ngModel)]="dirEdit.calle" placeholder="Ej: Morelos"/>
            </div>
            <div class="form-row">
              <label for="dom-num-ext">Núm. exterior</label>
              <input pInputText id="dom-num-ext" [(ngModel)]="dirEdit.numero_exterior" maxlength="20"
                placeholder="Ej: 42-A"/>
            </div>
            <div class="form-row">
              <label for="dom-num-int">Núm. interior</label>
              <input pInputText id="dom-num-int" [(ngModel)]="dirEdit.numero_interior" maxlength="20"
                placeholder="Ej: 3-B (opcional)"/>
            </div>
            <div class="form-row full">
              <label for="dom-entre-1">Entre calles</label>
              <div style="display:grid;grid-template-columns:1fr auto 1fr;gap:.4rem;align-items:center">
                <input pInputText id="dom-entre-1" [(ngModel)]="dirEdit.entre_calle_1" placeholder="Primera calle"/>
                <span style="font-size:.75rem;color:var(--text-muted)">y</span>
                <input pInputText [(ngModel)]="dirEdit.entre_calle_2" placeholder="Segunda calle" aria-label="Segunda calle entre la que se ubica"/>
              </div>
            </div>
            <div class="form-row full">
              <label for="dom-referencia">Referencia</label>
              <input pInputText id="dom-referencia" [(ngModel)]="dirEdit.referencia"
                maxlength="255" placeholder="Ej: Frente al parque, casa azul"/>
            </div>
            <div class="form-row">
              <label>Tipo dirección</label>
              <p-select [options]="tiposDirOpts" [(ngModel)]="dirEdit.tipo_direccion"
                optionLabel="label" optionValue="value" ariaLabel="Tipo dirección"/>
            </div>
            <div class="form-row">
              <label for="dom-dir-principal">¿Principal?</label>
              <input type="checkbox" id="dom-dir-principal" [(ngModel)]="dirEdit.es_principal"/>
            </div>
          </div>

          <p-divider align="left"><small>Geolocalización (opcional)</small></p-divider>

          <div class="form-grid">
            <div class="form-row">
              <label for="dom-lat">Latitud</label>
              <input pInputText id="dom-lat" [(ngModel)]="dirEdit.latitud" type="number"
                step="0.000001" placeholder="Ej: 19.432608"/>
            </div>
            <div class="form-row">
              <label for="dom-lng">Longitud</label>
              <input pInputText id="dom-lng" [(ngModel)]="dirEdit.longitud" type="number"
                step="0.000001" placeholder="Ej: -99.133209"/>
            </div>
            <div class="form-row full" style="justify-content:flex-end;gap:.5rem;display:flex">
              <p-button label="Geocodificar" icon="pi pi-map" size="small" [text]="true"
                [loading]="geocodificando()" (onClick)="geocodificar()" />
              @if (dirEdit.latitud && dirEdit.longitud) {
                <a [href]="urlMapa(dirEdit.latitud, dirEdit.longitud)" target="_blank" class="mapa-link-btn">
                  <i class="pi pi-external-link"></i> Ver en mapa
                </a>
              }
            </div>
          </div>

          <div class="form-btns">
            <p-button label="Cancelar" severity="secondary" [text]="true" size="small"
              (onClick)="cancelarDireccion()" />
            <p-button label="Guardar dirección" icon="pi pi-save" size="small"
              [loading]="savingDir()" (onClick)="guardarDireccion()" />
          </div>
        </div>
      }
    }
  `,
  styles: [`
    .empty-state { color: var(--text-muted); font-size: .85rem; padding: .5rem 0; }
    .empty-msg   { color: var(--text-muted); font-size: .82rem; margin: .25rem 0 .5rem; }

    .section-header {
      display: flex; justify-content: space-between; align-items: center;
      margin-bottom: .5rem;
    }
    .sec-title {
      font-size: .82rem; font-weight: 700; text-transform: uppercase;
      letter-spacing: .05em; color: var(--primary-color); margin: 0;
      display: flex; align-items: center; gap: .4rem;
    }

    /* Contactos */
    .contactos-list { display: flex; flex-direction: column; gap: .4rem; margin-bottom: .75rem; }
    .contacto-item {
      display: flex; align-items: center; gap: .75rem;
      padding: .5rem .75rem; border: 1px solid var(--surface-200); border-radius: 6px;
    }
    .contacto-item.principal { border-color: var(--green-300); background: var(--green-50); }
    .contacto-icon { font-size: 1.1rem; color: var(--primary-color); width: 24px; text-align: center; }
    .contacto-info { flex: 1; min-width: 0; }
    .contacto-valor { font-weight: 600; font-size: .88rem; display: block; }
    .contacto-meta  { font-size: .75rem; color: var(--text-secondary); }
    .contacto-actions { display: flex; gap: .25rem; align-items: center; }

    /* Direcciones */
    .dirs-list { display: flex; flex-direction: column; gap: .6rem; margin-bottom: .75rem; }
    .dir-card {
      border: 1px solid var(--surface-200); border-radius: 8px; padding: .75rem;
    }
    .dir-card.principal { border-color: var(--primary-color); background: var(--primary-50, var(--nevadi-red-lighter)); }
    .dir-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: .3rem; }
    .dir-actions { display: flex; gap: .2rem; align-items: center; }
    .dir-detalle { font-size: .88rem; font-weight: 500; margin-bottom: .2rem; }
    .dir-ubic { font-size: .78rem; color: var(--text-secondary); margin-bottom: .2rem; }
    .dir-mapa { display: flex; align-items: center; gap: .75rem; font-size: .78rem; margin-top: .25rem; }
    .mapa-link { color: var(--primary-color); text-decoration: none; }
    .mapa-link:hover { text-decoration: underline; }
    .coords { color: var(--text-muted); font-family: monospace; font-size: .72rem; }

    /* Formularios inline */
    .inline-form {
      background: var(--surface-50); border: 1px solid var(--surface-200);
      border-radius: 8px; padding: 1rem; margin-bottom: .75rem;
    }
    .inline-form h5 { margin: 0 0 .75rem; font-size: .9rem; }
    .form-grid { display: flex; flex-direction: column; gap: .4rem; }
    .form-row {
      display: grid; grid-template-columns: 130px 1fr; gap: .5rem;
      align-items: center;
    }
    .form-row.full { grid-column: 1 / -1; }
    .form-row label { font-size: .8rem; color: var(--text-color-secondary); }
    .form-btns { display: flex; justify-content: flex-end; gap: .5rem; margin-top: .75rem; }

    /* SEPOMEX */
    .sepomex-block { display: flex; flex-direction: column; gap: .4rem; margin-bottom: .5rem; }
    .cp-input-wrap { display: flex; align-items: center; }
    .sepomex-nota { font-size: .78rem; color: #f59e0b; margin: .25rem 0; }

    /* Campos bloqueados por SEPOMEX */
    .sep-locked {
      background: var(--surface-100); border: 1px solid var(--surface-300);
      border-radius: 6px; padding: .5rem .75rem;
      display: flex; flex-direction: column; gap: .3rem; margin-top: .25rem;
    }
    .sep-locked-row { display: flex; align-items: center; gap: .5rem; }
    .sep-locked-lbl {
      font-size: .75rem; color: var(--text-color-secondary);
      width: 70px; flex-shrink: 0; text-align: right;
    }
    .sep-locked-val {
      font-size: .85rem; font-weight: 500; color: var(--text-color);
      display: flex; align-items: center; gap: .3rem;
    }
    .sep-locked-val .pi-lock { color: var(--surface-400); font-size: .65rem; }

    /* Mapa link button */
    .mapa-link-btn {
      font-size: .8rem; color: var(--primary-color); text-decoration: none;
      display: flex; align-items: center; gap: .25rem;
      padding: .3rem .6rem; border: 1px solid var(--primary-color);
      border-radius: 4px;
    }
  `],
})
export class DomicilioComponent implements OnChanges, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly http = inject(HttpClient);
  private readonly notify = inject(ApexNotificationService);
  private readonly confirm = inject(ConfirmationService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroy$ = new Subject<void>();

  @Input() personaId: string | null = null;
  @Input() entidadTipo: string = 'PERSONA';

  direcciones  = signal<Direccion[]>([]);
  contactos    = signal<PersonaContacto[]>([]);

  editandoDireccion = signal(false);
  editandoContacto  = signal(false);
  savingDir         = signal(false);
  eliminandoDireccionId = signal<string | null>(null);
  estableciendoPrincipalId = signal<string | null>(null);
  eliminandoContactoId = signal<string | null>(null);
  savingContacto    = signal(false);
  buscandoCp        = signal(false);
  geocodificando    = signal(false);

  asentamientos = signal<SepomexRow[]>([]);
  cpSelId       = signal('');   // signal → garantiza reactividad en @if del template
  cpBusqueda    = '';
  dirEdit: any  = {};
  contactoEdit: any = {};

  asentamientoOpts = computed(() =>
    this.asentamientos().map(a => ({
      label: `${a.nombre_localidad} (${a.tipo_asentamiento || 'Colonia'})`,
      value: a.cp_id,
      row: a,
    }))
  );

  readonly tiposDirOpts  = TIPOS_DIR.map(v => ({ label: v, value: v }));
  readonly tiposViaOpts  = TIPOS_VIA.map(v => ({ label: v, value: v }));
  readonly mediosOpts    = MEDIOS.map(v => ({ label: v, value: v }));
  readonly tiposContOpts = TIPOS_CONT.map(v => ({ label: v, value: v }));

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['personaId'] && this.personaId) {
      this.cargar();
    }
  }

  private cargar(): void {
    this.cargarDirecciones();
    this.cargarContactos();
  }

  private cargarDirecciones(): void {
    if (!this.personaId) return;
    this.api.get<Direccion[]>('/direcciones', {
      entidad_tipo: this.entidadTipo,
      entidad_id: this.personaId,
    }).pipe(takeUntil(this.destroy$)).subscribe({ next: d => this.direcciones.set(d), error: () => {} });
  }

  private cargarContactos(): void {
    if (!this.personaId) return;
    this.api.get<PersonaContacto[]>('/persona-contactos', { persona_id: this.personaId })
      .pipe(takeUntil(this.destroy$))
      .subscribe({ next: c => this.contactos.set(c), error: () => {} });
  }

  // ── Direcciones ──────────────────────────────────────────────────────────

  abrirNuevaDireccion(): void {
    this.cpBusqueda = '';
    this.cpSelId.set('');
    this.asentamientos.set([]);
    this.dirEdit = {
      tipo_direccion: 'PRINCIPAL',
      es_principal: this.direcciones().length === 0,
      tipo_via: null, calle: '', numero_exterior: '', numero_interior: '',
      entre_calle_1: '', entre_calle_2: '', referencia: '',
      nombre_localidad: '', nombre_municipio: '', nombre_estado: '', nombre_pais: 'México',
      latitud: null, longitud: null,
    };
    this.editandoDireccion.set(true);
  }

  editarDireccion(d: Direccion): void {
    this.cpBusqueda = d.codigo_postal ?? '';
    this.cpSelId.set('');
    this.asentamientos.set([]);
    this.dirEdit = { ...d };
    if (this.cpBusqueda.length === 5) {
      // Carga asentamientos — usuario puede re-seleccionar si necesita cambiar colonia
      this.buscarPorCp(this.cpBusqueda);
    }
    this.editandoDireccion.set(true);
  }

  cancelarDireccion(): void {
    this.editandoDireccion.set(false);
    this.dirEdit = {};
    this.cpBusqueda = '';
    this.cpSelId.set('');
    this.asentamientos.set([]);
  }

  onCpChange(cp: string): void {
    this.cpSelId.set('');
    if (cp.length === 5) {
      this.buscarPorCp(cp);
    } else {
      this.asentamientos.set([]);
    }
  }

  private buscarPorCp(cp: string): void {
    this.buscandoCp.set(true);
    this.api.get<SepomexRow[]>('/catalogs/sepomex/por-cp', { cp })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: rows => {
          this.asentamientos.set(rows);
          this.buscandoCp.set(false);
        },
        error: () => this.buscandoCp.set(false),
      });
  }

  onAsentamientoSelect(event: any): void {
    const selectedValue = event?.value ?? event;
    const opt = this.asentamientoOpts().find(o => o.value === selectedValue);
    if (!opt) return;
    const row = opt.row as SepomexRow;
    this.dirEdit.nombre_localidad  = row.nombre_localidad;
    this.dirEdit.tipo_asentamiento = row.tipo_asentamiento;
    this.dirEdit.nombre_municipio  = row.nombre_municipio;
    this.dirEdit.nombre_estado     = row.nombre_estado;
    this.dirEdit.nombre_pais       = row.nombre_pais || 'México';
    this.dirEdit.codigo_postal     = row.codigo_postal;
    this.dirEdit._cp_id            = row.cp_id;
    this.dirEdit._localidad_id     = row.localidad_id;
  }

  limpiarSeleccionCp(): void {
    this.cpSelId.set('');
    this.dirEdit._cp_id = null;
    this.dirEdit._localidad_id = null;
  }

  guardarDireccion(): void {
    if (!this.personaId) return;
    this.savingDir.set(true);

    const payload: any = {
      entidad_tipo: this.entidadTipo,
      entidad_id: this.personaId,
      tipo_direccion: this.dirEdit.tipo_direccion || 'PRINCIPAL',
      es_principal: this.dirEdit.es_principal,
      tipo_via: this.dirEdit.tipo_via || null,
      calle: this.dirEdit.calle || null,
      numero_exterior: this.dirEdit.numero_exterior || null,
      numero_interior: this.dirEdit.numero_interior || null,
      // NOTA: el campo Java es `entreCalles1`/`entreCalles2` (plural) aunque la
      // columna de BD y el modelo local usan singular "calle" — con snake_case
      // JSON debe ser entre_calles_1/entre_calles_2, no entre_calle_1/2.
      entre_calles_1: this.dirEdit.entre_calle_1 || null,
      entre_calles_2: this.dirEdit.entre_calle_2 || null,
      referencia: this.dirEdit.referencia || null,
      latitud: this.dirEdit.latitud || null,
      longitud: this.dirEdit.longitud || null,
      precision_gps: this.dirEdit.precision_gps || null,
    };

    if (this.dirEdit._cp_id) {
      payload.codigo_postal_id = this.dirEdit._cp_id;
      payload.localidad_id     = this.dirEdit._localidad_id;
    }
    if (this.dirEdit.row_version !== undefined) {
      payload.row_version = this.dirEdit.row_version;
    }

    const req = this.dirEdit.id
      ? this.api.patch(`/direcciones/${this.dirEdit.id}`, payload)
      : this.api.post('/direcciones', payload);

    req.pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.notify.success('Guardado', 'Dirección actualizada');
        this.cancelarDireccion();
        this.cargarDirecciones();
      },
      error: e => {
        this.notify.error('Error', e.error?.detail ?? 'Error al guardar');
        this.savingDir.set(false);
      },
    });
  }

  eliminarDireccion(id: string): void {
    const dir = this.direcciones().find(d => d.id === id);
    const desc = dir ? `${dir.calle ?? ''} ${dir.numero_exterior ?? ''}`.trim() || dir.tipo_direccion : 'esta dirección';
    this.confirm.confirm({
      message: `¿Eliminar la dirección "${desc}"?`,
      header: 'Confirmar eliminación',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.eliminandoDireccionId.set(id);
        this.api.delete(`/direcciones/${id}`)
          .pipe(takeUntil(this.destroy$))
          .subscribe({
            next: () => { this.eliminandoDireccionId.set(null); this.cargarDirecciones(); },
            error: () => this.eliminandoDireccionId.set(null),
          });
      },
    });
  }

  setPrincipal(id: string): void {
    this.estableciendoPrincipalId.set(id);
    this.api.patch(`/direcciones/${id}/principal`, {})
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => { this.estableciendoPrincipalId.set(null); this.cargarDirecciones(); },
        error: () => this.estableciendoPrincipalId.set(null),
      });
  }

  geocodificar(): void {
    const parts = [
      this.dirEdit.calle, this.dirEdit.numero_exterior,
      this.dirEdit.nombre_localidad, this.dirEdit.nombre_municipio,
      this.dirEdit.nombre_estado, this.dirEdit.nombre_pais || 'México',
    ].filter(Boolean);
    if (parts.length < 2) {
      this.notify.warning('Datos insuficientes', 'Ingresa al menos calle y municipio para geocodificar');
      return;
    }
    this.geocodificando.set(true);
    const q = encodeURIComponent(parts.join(', '));
    this.http.get<any[]>(
      `https://nominatim.openstreetmap.org/search?q=${q}&format=json&limit=1`,
      { headers: { 'Accept-Language': 'es' } }
    ).pipe(takeUntil(this.destroy$)).subscribe({
      next: results => {
        if (results.length > 0) {
          this.dirEdit.latitud  = parseFloat(results[0].lat);
          this.dirEdit.longitud = parseFloat(results[0].lon);
          this.dirEdit.precision_gps = 'APROXIMADA';
          this.notify.success('Geocodificado', `${results[0].display_name}`);
        } else {
          this.notify.warning('Sin resultados', 'No se encontraron coordenadas para esta dirección');
        }
        this.geocodificando.set(false);
        this.cdr.markForCheck();
      },
      error: () => {
        this.notify.error('Error', 'No se pudo conectar a Nominatim');
        this.geocodificando.set(false);
      },
    });
  }

  // ── Contactos ────────────────────────────────────────────────────────────

  abrirNuevoContacto(): void {
    this.contactoEdit = {
      medio: 'CELULAR', tipo: 'PERSONAL',
      valor: '', etiqueta: '',
      es_principal: this.contactos().length === 0, // auto-principal si es el primero
      notas: '',
    };
    this.editandoContacto.set(true);
  }

  editarContacto(c: PersonaContacto): void {
    this.contactoEdit = { ...c };
    this.editandoContacto.set(true);
  }

  cancelarContacto(): void {
    this.editandoContacto.set(false);
    this.contactoEdit = {};
  }

  guardarContacto(): void {
    if (!this.personaId) return;

    const valor = (this.contactoEdit.valor ?? '').trim();
    const medio = this.contactoEdit.medio ?? '';

    if (!valor) {
      this.notify.warning('Campo requerido', 'Ingresa el valor del medio de contacto');
      return;
    }

    if (medio === 'EMAIL') {
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
      if (!emailRegex.test(valor)) {
        this.notify.warning('Email inválido', 'Ingresa un correo electrónico válido (ej: nombre@dominio.com)');
        return;
      }
    } else if (['CELULAR', 'FIJO', 'WHATSAPP'].includes(medio)) {
      const digitosRegex = /^\d{10}$/;
      const soloDigitos = valor.replace(/[\s\-\(\)]/g, '');
      if (!digitosRegex.test(soloDigitos)) {
        this.notify.warning('Teléfono inválido', 'El número debe tener exactamente 10 dígitos');
        return;
      }
      this.contactoEdit.valor = soloDigitos; // normalizar
    }

    // Auto-principal si es el único registro
    if (this.contactos().length === 0) {
      this.contactoEdit.es_principal = true;
    }

    this.savingContacto.set(true);
    const isNew = !this.contactoEdit.id;
    const payload = { ...this.contactoEdit, persona_id: this.personaId };
    const req = isNew
      ? this.api.post('/persona-contactos', payload)
      : this.api.patch(`/persona-contactos/${this.contactoEdit.id}`, payload);
    req.pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.notify.success('Guardado', 'Contacto actualizado');
        this.cancelarContacto();
        this.cargarContactos();
      },
      error: e => {
        this.notify.error('Error', e.error?.detail ?? 'Error');
        this.savingContacto.set(false);
      },
    });
  }

  eliminarContacto(id: string): void {
    const c = this.contactos().find(x => x.id === id);
    const desc = c?.valor ?? c?.etiqueta ?? 'este medio de contacto';
    this.confirm.confirm({
      message: `¿Eliminar el medio de contacto "${desc}"?`,
      header: 'Confirmar eliminación',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.eliminandoContactoId.set(id);
        this.api.delete(`/persona-contactos/${id}`)
          .pipe(takeUntil(this.destroy$))
          .subscribe({
            next: () => { this.eliminandoContactoId.set(null); this.cargarContactos(); },
            error: () => this.eliminandoContactoId.set(null),
          });
      },
    });
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  iconMedio(medio: string): string {
    const map: Record<string, string> = {
      CELULAR: 'pi pi-mobile', FIJO: 'pi pi-phone',
      WHATSAPP: 'pi pi-whatsapp', EMAIL: 'pi pi-envelope',
      TELEGRAM: 'pi pi-send', FAX: 'pi pi-print', OTRO: 'pi pi-ellipsis-h',
    };
    return map[medio] ?? 'pi pi-ellipsis-h';
  }

  formatDireccion(d: Direccion): string {
    const partes: string[] = [];
    if (d.tipo_via) partes.push(d.tipo_via);
    if (d.calle) partes.push(d.calle);
    if (d.numero_exterior) partes.push(`#${d.numero_exterior}`);
    if (d.numero_interior) partes.push(`Int. ${d.numero_interior}`);
    if (d.entre_calle_1 && d.entre_calle_2) {
      partes.push(`(entre ${d.entre_calle_1} y ${d.entre_calle_2})`);
    }
    if (d.referencia) partes.push(`— ${d.referencia}`);
    return partes.join(' ') || 'Sin detalle de calle';
  }

  urlMapa(lat: number | null, lng: number | null): string {
    if (!lat || !lng) return '#';
    return `https://www.openstreetmap.org/?mlat=${lat}&mlon=${lng}&zoom=17`;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
