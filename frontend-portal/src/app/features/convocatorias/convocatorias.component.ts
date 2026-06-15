import { Component, OnInit, signal, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { PortalApiService } from '../../core/services/portal-api.service';

const CATEGORIA_LABEL: Record<string, string> = {
  OFERTA_EDUCATIVA: 'Oferta Educativa',
  RECURSOS_HUMANOS: 'Recursos Humanos',
};
const CATEGORIA_COLOR: Record<string, string> = {
  OFERTA_EDUCATIVA: '#198754',
  RECURSOS_HUMANOS: '#8B0000',
};
const CATEGORIA_ICON: Record<string, string> = {
  OFERTA_EDUCATIVA: 'pi-graduation-cap',
  RECURSOS_HUMANOS: 'pi-briefcase',
};

const TIPO_LABEL: Record<string, string> = {
  INSCRIPCION: 'Inscripción', REINSCRIPCION: 'Reinscripción',
  VACANTE_DOCENTE: 'Vacante Docente', VACANTE_ADMINISTRATIVA: 'Vacante Administrativa',
  BECA: 'Beca', INTERCAMBIO: 'Intercambio', EXTRACURRICULAR: 'Extracurricular',
};
const TIPO_COLOR: Record<string, string> = {
  INSCRIPCION: '#198754', REINSCRIPCION: '#0d6efd', VACANTE_DOCENTE: '#8B0000',
  VACANTE_ADMINISTRATIVA: '#6f42c1', BECA: '#d4a017', INTERCAMBIO: '#0dcaf0',
  EXTRACURRICULAR: '#fd7e14',
};
const TIPO_POR_CATEGORIA: Record<string, string[]> = {
  OFERTA_EDUCATIVA: ['INSCRIPCION', 'REINSCRIPCION', 'BECA', 'INTERCAMBIO', 'EXTRACURRICULAR'],
  RECURSOS_HUMANOS: ['VACANTE_DOCENTE', 'VACANTE_ADMINISTRATIVA'],
};

const PLANTELES = ['Metepec', 'Tenancingo', 'Ixtapan de la Sal'];
const NIVELES   = ['PRIMARIA', 'SECUNDARIA', 'PREPARATORIA'];
const NIVEL_LABEL: Record<string, string> = {
  PRIMARIA: 'Primaria', SECUNDARIA: 'Secundaria', PREPARATORIA: 'Preparatoria',
};

@Component({
  selector: 'app-convocatorias',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, SelectModule, InputTextModule, ProgressSpinnerModule],
  template: `
    <!-- ── Banner ─────────────────────────────────────────────── -->
    <section class="banner">
      <div class="container banner-inner">
        <div class="banner-text">
          <h1>Convocatorias</h1>
          <p>Inscripciones, becas e intercambios · Vacantes docentes y administrativas del Instituto Nevadi.</p>
        </div>
        <div class="banner-logo-pill">
          <img src="nevadi-logo.jpg" alt="Instituto Nevadi" class="banner-logo" />
        </div>
      </div>
    </section>

    <!-- ── Pestañas de categoría ─────────────────────────────── -->
    <div class="categoria-tabs-bar">
      <div class="container tabs-inner">
        <button class="cat-tab" [class.active]="!categoriaFiltro"
          (click)="seleccionarCategoria(null)">
          <i class="pi pi-list"></i> Todas
        </button>
        @for (cat of categoriasOpt; track cat.value) {
          <button class="cat-tab" [class.active]="categoriaFiltro === cat.value"
            [class.rrhh]="cat.value === 'RECURSOS_HUMANOS'"
            [class.educativa]="cat.value === 'OFERTA_EDUCATIVA'"
            (click)="seleccionarCategoria(cat.value)">
            <i class="pi" [class]="CATEGORIA_ICON[cat.value]"></i>
            {{ cat.label }}
          </button>
        }
      </div>
    </div>

    <!-- ── Filtros ─────────────────────────────────────────────── -->
    <div class="filtros-bar">
      <div class="container filtros-inner">
        <!-- Búsqueda -->
        <input pInputText [(ngModel)]="busqueda" placeholder="Buscar convocatoria…"
          (input)="filtrar()" class="filtro-input" />

        <!-- Plantel -->
        <p-select [options]="plantelesOpt" [(ngModel)]="plantelFiltro"
          (onChange)="cargar()" optionLabel="label" optionValue="value"
          placeholder="Todos los planteles" [showClear]="true" class="filtro-sel" />

        <!-- Nivel (solo para oferta educativa) -->
        @if (!categoriaFiltro || categoriaFiltro === 'OFERTA_EDUCATIVA') {
          <p-select [options]="nivelesOpt" [(ngModel)]="nivelFiltro"
            (onChange)="cargar()" optionLabel="label" optionValue="value"
            placeholder="Todos los niveles" [showClear]="true" class="filtro-sel" />
        }

        <!-- Tipo (filtrado por categoria si aplica) -->
        <p-select [options]="tiposOptFiltrados()" [(ngModel)]="tipoFiltro"
          (onChange)="cargar()" optionLabel="label" optionValue="value"
          placeholder="Todos los tipos" [showClear]="true" class="filtro-sel" />

        <!-- Contador -->
        @if (!loading()) {
          <span class="filtro-count">{{ convocatoriasFiltradas().length }} resultado(s)</span>
        }
      </div>
    </div>

    <!-- ── Chips de filtros activos ──────────────────────────── -->
    @if (plantelFiltro || nivelFiltro || tipoFiltro || busqueda) {
      <div class="container chips-row">
        @if (plantelFiltro) {
          <span class="chip">Plantel: {{ plantelFiltro }} <button (click)="limpiar('plantel')">×</button></span>
        }
        @if (nivelFiltro) {
          <span class="chip">Nivel: {{ NIVEL_LABEL[nivelFiltro] }} <button (click)="limpiar('nivel')">×</button></span>
        }
        @if (tipoFiltro) {
          <span class="chip">Tipo: {{ tipoLabel(tipoFiltro) }} <button (click)="limpiar('tipo')">×</button></span>
        }
        @if (busqueda) {
          <span class="chip">Búsqueda: "{{ busqueda }}" <button (click)="limpiar('busqueda')">×</button></span>
        }
        <button class="chip-clear" (click)="limpiarTodo()">Limpiar filtros</button>
      </div>
    }

    <!-- ── Listado ─────────────────────────────────────────────── -->
    <div class="container convs-container">
      @if (loading()) {
        <div style="text-align:center;padding:4rem"><p-progressSpinner strokeWidth="3" /></div>
      } @else if (convocatoriasFiltradas().length === 0) {
        <div class="empty-state">
          <i class="pi pi-search" style="font-size:3rem;color:#dee2e6"></i>
          <h3>Sin resultados</h3>
          <p>No hay convocatorias vigentes con los filtros seleccionados.</p>
          <button class="btn-limpiar" (click)="limpiarTodo()">Ver todas las convocatorias</button>
        </div>
      } @else {
        <!-- Agrupado por categoría cuando no hay filtro de categoría -->
        @if (!categoriaFiltro) {
          @for (cat of ['OFERTA_EDUCATIVA', 'RECURSOS_HUMANOS']; track cat) {
            @if (porCategoria(cat).length) {
              <div class="grupo categoria-grupo" [class.rrhh-grupo]="cat === 'RECURSOS_HUMANOS'">
                <div class="grupo-header categoria-header" [class.rrhh-header]="cat === 'RECURSOS_HUMANOS'">
                  <i class="pi" [class]="CATEGORIA_ICON[cat]"></i>
                  <h2>{{ CATEGORIA_LABEL[cat] }}</h2>
                  <span class="grupo-count" [class.rrhh-count]="cat === 'RECURSOS_HUMANOS'">
                    {{ porCategoria(cat).length }}
                  </span>
                </div>
                <!-- Sub-agrupado por plantel cuando no hay filtro de plantel -->
                @if (!plantelFiltro) {
                  @if (generalesPorCategoria(cat).length) {
                    <div class="subgrupo">
                      <div class="subgrupo-header"><i class="pi pi-globe"></i> Todos los Planteles</div>
                      <div class="grid-convocatorias">
                        @for (c of generalesPorCategoria(cat); track c['id']) {
                          <ng-container *ngTemplateOutlet="convCard; context:{c}" />
                        }
                      </div>
                    </div>
                  }
                  @for (plantel of PLANTELES; track plantel) {
                    @if (porPlantelCategoria(plantel, cat).length) {
                      <div class="subgrupo">
                        <div class="subgrupo-header"><i class="pi pi-building"></i> Plantel {{ plantel }}</div>
                        <div class="grid-convocatorias">
                          @for (c of porPlantelCategoria(plantel, cat); track c['id']) {
                            <ng-container *ngTemplateOutlet="convCard; context:{c}" />
                          }
                        </div>
                      </div>
                    }
                  }
                } @else {
                  <div class="grid-convocatorias">
                    @for (c of porCategoria(cat); track c['id']) {
                      <ng-container *ngTemplateOutlet="convCard; context:{c}" />
                    }
                  </div>
                }
              </div>
            }
          }
        } @else {
          <!-- Vista sin categoría agrupada (ya hay filtro de categoría) -->
          @if (!plantelFiltro) {
            @if (generales().length) {
              <div class="subgrupo">
                <div class="subgrupo-header"><i class="pi pi-globe"></i> Todos los Planteles</div>
                <div class="grid-convocatorias">
                  @for (c of generales(); track c['id']) { <ng-container *ngTemplateOutlet="convCard; context:{c}" /> }
                </div>
              </div>
            }
            @for (plantel of PLANTELES; track plantel) {
              @if (porPlantel(plantel).length) {
                <div class="subgrupo">
                  <div class="subgrupo-header"><i class="pi pi-building"></i> Plantel {{ plantel }}</div>
                  <div class="grid-convocatorias">
                    @for (c of porPlantel(plantel); track c['id']) { <ng-container *ngTemplateOutlet="convCard; context:{c}" /> }
                  </div>
                </div>
              }
            }
          } @else {
            <div class="grid-convocatorias" style="margin-top:1.5rem">
              @for (c of convocatoriasFiltradas(); track c['id']) { <ng-container *ngTemplateOutlet="convCard; context:{c}" /> }
            </div>
          }
        }
      }
    </div>

    <!-- Template de tarjeta -->
    <ng-template #convCard let-c="c">
      <a [routerLink]="['/convocatorias', c['id']]" class="conv-card"
         [class.rrhh-card]="c['categoria'] === 'RECURSOS_HUMANOS'">
        <div class="conv-tipo-bar" [style.background]="tipoColor(c['tipo'])"></div>
        <div class="conv-body">
          <div class="conv-tags">
            <span class="tag-categoria"
              [class.tag-rrhh]="c['categoria'] === 'RECURSOS_HUMANOS'"
              [class.tag-educativa]="c['categoria'] === 'OFERTA_EDUCATIVA'">
              <i class="pi" [class]="CATEGORIA_ICON[c['categoria']]" style="font-size:.65rem"></i>
              {{ categoriaLabel(c['categoria']) }}
            </span>
            <span class="tag-tipo" [style.background]="tipoColor(c['tipo']) + '20'" [style.color]="tipoColor(c['tipo'])">
              {{ tipoLabel(c['tipo']) }}
            </span>
            @if (c['nombre_nivel']) {
              <span class="tag-nivel">{{ c['nombre_nivel'] }}</span>
            }
          </div>
          <h3 class="conv-titulo">{{ c['titulo'] }}</h3>
          @if (c['descripcion']) {
            <p class="conv-desc">{{ c['descripcion'] | slice:0:90 }}{{ c['descripcion']?.length > 90 ? '…' : '' }}</p>
          }
          <div class="conv-footer-info">
            <span class="conv-cierre"><i class="pi pi-calendar-times"></i> Cierra {{ c['fecha_cierre_postulacion'] | date:'d MMM yyyy' }}</span>
            @if (c['cupo_maximo']) {
              <span class="conv-cupo"><i class="pi pi-users"></i> {{ c['cupo_actual'] }}/{{ c['cupo_maximo'] }}</span>
            }
          </div>
        </div>
        <div class="conv-action">Ver detalle <i class="pi pi-arrow-right"></i></div>
      </a>
    </ng-template>
  `,
  styles: [`
    .banner { background: linear-gradient(135deg, #8B0000 0%, #5a0000 100%); color: #fff; padding: 2.5rem 0; }
    .banner-inner { display: flex; justify-content: space-between; align-items: center; gap: 1.5rem; }
    .banner-text h1 { font-family: Jost, sans-serif; font-size: 2rem; font-weight: 800; margin-bottom: .35rem; }
    .banner-text p  { opacity: .8; font-size: .95rem; max-width: 500px; }
    .banner-logo-pill { background: rgba(255,255,255,.92); border-radius: 8px; padding: 6px 14px; display: inline-flex; align-items: center; flex-shrink: 0; box-shadow: 0 2px 8px rgba(0,0,0,.2); }
    .banner-logo { height: 44px; width: auto; display: block; }

    /* Pestañas de categoría */
    .categoria-tabs-bar { background: #fff; border-bottom: 2px solid #dee2e6; position: sticky; top: 56px; z-index: 101; }
    .tabs-inner { display: flex; gap: 0; }
    .cat-tab {
      display: flex; align-items: center; gap: .45rem; padding: .85rem 1.5rem;
      background: none; border: none; border-bottom: 3px solid transparent;
      font-size: .88rem; font-weight: 600; color: #6c757d; cursor: pointer;
      transition: color .15s, border-color .15s; margin-bottom: -2px;
    }
    .cat-tab:hover { color: #1a1a2e; }
    .cat-tab.active { color: #1a1a2e; border-bottom-color: #8B0000; }
    .cat-tab.educativa.active { border-bottom-color: #198754; color: #198754; }
    .cat-tab.rrhh.active     { border-bottom-color: #8B0000; color: #8B0000; }

    .filtros-bar { background: #f8f9fa; border-bottom: 1px solid #dee2e6; position: sticky; top: calc(56px + 49px); z-index: 100; box-shadow: 0 2px 8px rgba(0,0,0,.04); }
    .filtros-inner { display: flex; gap: .75rem; padding: .75rem 0; align-items: center; flex-wrap: wrap; }
    .filtro-input { min-width: 220px; flex: 1; }
    .filtro-sel { min-width: 160px; }
    .filtro-count { font-size: .82rem; color: #6c757d; white-space: nowrap; margin-left: auto; }

    .chips-row { display: flex; gap: .5rem; flex-wrap: wrap; align-items: center; padding: .75rem 0 0; }
    .chip { display: inline-flex; align-items: center; gap: .35rem; background: #f0e8d5; color: #7d4e00; font-size: .78rem; padding: .2rem .6rem; border-radius: 20px; font-weight: 600; }
    .chip button { background: none; border: none; cursor: pointer; font-size: .9rem; color: #7d4e00; line-height: 1; }
    .chip-clear { background: none; border: 1px solid #dee2e6; color: #6c757d; font-size: .78rem; padding: .2rem .7rem; border-radius: 20px; cursor: pointer; }

    /* Grupos */
    .convs-container { padding-top: 1.5rem; padding-bottom: 3rem; }
    .grupo { margin-bottom: 2.5rem; }
    .categoria-grupo { border-radius: 12px; border: 1px solid #dee2e6; overflow: hidden; margin-bottom: 2rem; }
    .categoria-header {
      display: flex; align-items: center; gap: .6rem; padding: 1rem 1.5rem;
      background: linear-gradient(90deg, #e8f5e9 0%, #f8f9fa 100%);
      border-bottom: 2px solid #c3e6cb;
    }
    .categoria-header i { color: #198754; font-size: 1.1rem; }
    .categoria-header h2 { font-family: Jost, sans-serif; font-size: 1.1rem; font-weight: 700; color: #155724; margin: 0; }
    .grupo-count { background: #198754; color: #fff; font-size: .72rem; font-weight: 700; padding: .15rem .5rem; border-radius: 10px; }
    .rrhh-grupo { }
    .rrhh-header {
      background: linear-gradient(90deg, #fde8e8 0%, #f8f9fa 100%);
      border-bottom-color: #f5c6c6;
    }
    .rrhh-header i, .rrhh-header h2 { color: #8B0000 !important; }
    .rrhh-count { background: #8B0000 !important; }

    .subgrupo { padding: 1rem 1.5rem 1.25rem; border-bottom: 1px solid #f0f0f0; }
    .subgrupo:last-child { border-bottom: none; }
    .subgrupo-header { display: flex; align-items: center; gap: .4rem; font-size: .82rem; font-weight: 600; color: #6c757d; margin-bottom: .85rem; }
    .subgrupo-header i { font-size: .8rem; }

    .grupo-header { display: flex; align-items: center; gap: .6rem; margin-bottom: 1.25rem; padding-bottom: .75rem; border-bottom: 2px solid #dee2e6; }
    .grupo-header i { color: #8B0000; font-size: 1.1rem; }
    .grupo-header h2 { font-family: Jost, sans-serif; font-size: 1.15rem; font-weight: 700; color: #1a1a2e; margin: 0; }

    /* Grid tarjetas */
    .grid-convocatorias { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 1.1rem; }
    .conv-card { display: flex; flex-direction: column; background: #fff; border-radius: 10px; border: 1px solid #dee2e6; overflow: hidden; text-decoration: none; color: inherit; transition: box-shadow .2s, transform .2s; }
    .conv-card:hover { box-shadow: 0 8px 24px rgba(0,0,0,.12); transform: translateY(-3px); }
    .rrhh-card { border-color: #f5c6c6; }
    .conv-tipo-bar { height: 4px; }
    .conv-body { padding: 1rem 1rem .6rem; flex: 1; }
    .conv-tags { display: flex; gap: .35rem; flex-wrap: wrap; margin-bottom: .6rem; }
    .tag-categoria {
      display: inline-flex; align-items: center; gap: .25rem;
      font-size: .68rem; font-weight: 700; padding: .15rem .5rem; border-radius: 4px;
    }
    .tag-educativa { background: #d1e7dd; color: #0a3622; }
    .tag-rrhh      { background: #f8d7da; color: #6b0000; }
    .tag-tipo { font-size: .68rem; font-weight: 700; padding: .15rem .5rem; border-radius: 4px; }
    .tag-nivel { font-size: .68rem; background: #f0f0f0; color: #495057; padding: .15rem .5rem; border-radius: 4px; }
    .conv-titulo { font-size: .92rem; font-weight: 700; color: #1a1a2e; margin-bottom: .4rem; line-height: 1.35; }
    .conv-desc { font-size: .8rem; color: #6c757d; line-height: 1.4; margin-bottom: .5rem; }
    .conv-footer-info { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: .5rem; font-size: .78rem; }
    .conv-cierre { color: #8B0000; font-weight: 600; display: flex; align-items: center; gap: .3rem; }
    .conv-cupo { color: #6c757d; display: flex; align-items: center; gap: .3rem; }
    .conv-action { padding: .6rem 1rem; background: #f8f9fa; font-size: .8rem; color: #8B0000; font-weight: 600; display: flex; align-items: center; gap: .35rem; }

    .empty-state { text-align: center; padding: 4rem 1rem; display: flex; flex-direction: column; align-items: center; gap: .75rem; }
    .empty-state h3 { color: #495057; }
    .empty-state p  { color: #6c757d; font-size: .9rem; }
    .btn-limpiar { background: #8B0000; color: #fff; border: none; padding: .5rem 1.25rem; border-radius: 8px; cursor: pointer; font-size: .88rem; }

    @media (max-width: 640px) {
      .banner-logo-pill { display: none; }
      .filtros-inner { gap: .5rem; }
      .filtro-input, .filtro-sel { min-width: 0; flex: 1 1 calc(50% - .25rem); }
      .cat-tab { padding: .7rem .85rem; font-size: .82rem; }
      .subgrupo { padding: .85rem; }
    }
  `],
})
export class ConvocatoriasComponent implements OnInit {
  private readonly api    = inject(PortalApiService);
  private readonly route  = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly PLANTELES       = PLANTELES;
  readonly NIVEL_LABEL     = NIVEL_LABEL;
  readonly CATEGORIA_ICON  = CATEGORIA_ICON;
  readonly CATEGORIA_LABEL = CATEGORIA_LABEL;

  convocatorias          = signal<any[]>([]);
  convocatoriasFiltradas = signal<any[]>([]);
  loading                = signal(true);
  busqueda               = '';
  plantelFiltro:   string | null = null;
  nivelFiltro:     string | null = null;
  tipoFiltro:      string | null = null;
  categoriaFiltro: string | null = null;

  plantelesOpt   = PLANTELES.map(p => ({ label: `Plantel ${p}`, value: p }));
  nivelesOpt     = NIVELES.map(n => ({ label: NIVEL_LABEL[n], value: n }));
  categoriasOpt  = Object.entries(CATEGORIA_LABEL).map(([value, label]) => ({ value, label }));
  tiposOptTodos  = Object.entries(TIPO_LABEL).map(([value, label]) => ({ value, label }));

  tiposOptFiltrados = computed(() => {
    if (!this.categoriaFiltro) return this.tiposOptTodos;
    const permitidos = TIPO_POR_CATEGORIA[this.categoriaFiltro] ?? [];
    return this.tiposOptTodos.filter(t => permitidos.includes(t.value));
  });

  ngOnInit() {
    this.route.queryParamMap.subscribe(qp => {
      this.plantelFiltro   = qp.get('plantel')   ?? null;
      this.nivelFiltro     = qp.get('nivel')     ?? null;
      this.categoriaFiltro = qp.get('categoria') ?? null;
      this.cargar();
    });
  }

  seleccionarCategoria(cat: string | null) {
    this.categoriaFiltro = cat;
    this.tipoFiltro = null; // reset tipo al cambiar categoría
    this.cargar();
  }

  cargar() {
    this.loading.set(true);
    const params: any = {};
    if (this.categoriaFiltro) params['categoria'] = this.categoriaFiltro;
    if (this.tipoFiltro)      params['tipo']      = this.tipoFiltro;
    if (this.plantelFiltro)   params['plantel']   = this.plantelFiltro;
    if (this.nivelFiltro)     params['nivel']     = this.nivelFiltro;

    this.api.get<any>('/convocatorias', params).subscribe({
      next: r => {
        const lista = Array.isArray(r) ? r : (r['data'] ?? []);
        this.convocatorias.set(lista);
        this.filtrar();
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  filtrar() {
    const q = this.busqueda.toLowerCase().trim();
    const lista = this.convocatorias();
    this.convocatoriasFiltradas.set(
      q ? lista.filter(c => c['titulo']?.toLowerCase().includes(q) || c['descripcion']?.toLowerCase().includes(q)) : lista
    );
  }

  // Agrupaciones por categoría (para vista sin filtro de categoría)
  porCategoria(cat: string)  { return this.convocatoriasFiltradas().filter(c => c['categoria'] === cat); }
  generalesPorCategoria(cat: string) {
    return this.convocatoriasFiltradas().filter(c => c['categoria'] === cat && !c['nombre_plantel']);
  }
  porPlantelCategoria(nombre: string, cat: string) {
    return this.convocatoriasFiltradas().filter(c => c['categoria'] === cat && c['nombre_plantel'] === nombre);
  }

  // Agrupaciones sin categoría (cuando ya hay filtro de categoría)
  generales() { return this.convocatoriasFiltradas().filter(c => !c['plantel_id'] && !c['nombre_plantel']); }
  porPlantel(nombre: string) { return this.convocatoriasFiltradas().filter(c => c['nombre_plantel'] === nombre); }

  limpiar(campo: string) {
    if (campo === 'plantel')  this.plantelFiltro = null;
    if (campo === 'nivel')    this.nivelFiltro   = null;
    if (campo === 'tipo')     this.tipoFiltro    = null;
    if (campo === 'busqueda') this.busqueda      = '';
    this.cargar();
  }

  limpiarTodo() {
    this.plantelFiltro   = null;
    this.nivelFiltro     = null;
    this.tipoFiltro      = null;
    this.categoriaFiltro = null;
    this.busqueda        = '';
    this.router.navigate(['/convocatorias']);
    this.cargar();
  }

  tipoLabel(t: string)      { return TIPO_LABEL[t] ?? t; }
  tipoColor(t: string)      { return TIPO_COLOR[t] ?? '#6c757d'; }
  categoriaLabel(c: string) { return CATEGORIA_LABEL[c] ?? c; }
}
