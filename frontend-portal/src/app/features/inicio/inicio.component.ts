import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { PortalApiService } from '../../core/services/portal-api.service';

const PLANTELES = [
  {
    nombre: 'Metepec',
    municipio: 'Metepec, Estado de México',
    direccion: 'Prol. Heriberto Enríquez 1001',
    cp: '52140',
    telefonos: ['722-325-3683', '722-297-1441'],
    email: 'nevadimetepec@institutonevadi.edu.mx',
    icono: 'pi-building',
    niveles: ['Primaria SEP', 'Secundaria SEP', 'Preparatoria UAEMEX'],
    color: '#8B0000',
  },
  {
    nombre: 'Tenancingo',
    municipio: 'Tenancingo, Estado de México',
    direccion: 'Carr. Tenancingo-Tenería S/N',
    cp: '52400',
    telefonos: ['714-142-4323'],
    email: 'nevaditenancingo@institutonevadi.edu.mx',
    icono: 'pi-building',
    niveles: ['Primaria SEP', 'Secundaria SEP', 'Preparatoria UAEMEX'],
    color: '#6b0000',
  },
  {
    nombre: 'Ixtapan de la Sal',
    municipio: 'Ixtapan de la Sal, Estado de México',
    direccion: 'Independencia Pte. 5',
    cp: '51900',
    telefonos: ['721-143-3015'],
    email: 'nevadiixtapan@institutonevadi.edu.mx',
    icono: 'pi-building',
    niveles: ['Primaria SEP', 'Secundaria SEP', 'Preparatoria UAEMEX'],
    color: '#4a0000',
  },
];

const NIVELES = [
  { nombre: 'Primaria', org: 'SEP', icono: 'pi-star', desc: 'Educación básica de 1° a 6° grado' },
  { nombre: 'Secundaria', org: 'SEP', icono: 'pi-book', desc: 'Educación básica de 1° a 3° grado' },
  { nombre: 'Preparatoria', org: 'UAEMEX', desc: 'Bachillerato CBU — Plan 2024', icono: 'pi-graduation-cap' },
];

@Component({
  selector: 'app-inicio',
  standalone: true,
  imports: [CommonModule, RouterLink, ButtonModule],
  template: `
    <!-- ══ HERO ══════════════════════════════════════════════════ -->
    <section class="hero">
      <div class="hero-bg"></div>
      <div class="container hero-content">
        <div class="hero-logo-pill">
          <img src="nevadi-logo.jpg" alt="Instituto Nevadi" class="hero-logo" />
        </div>
        <div class="hero-badge">Portal Institucional Oficial</div>
        <h1 class="hero-title">
          Bienvenido al<br><span class="gold">Portal Institucional</span>
        </h1>
        <p class="hero-lema">
          "El único camino para salir adelante es la educación"
        </p>
        <p class="hero-desc">
          3 planteles · 3 niveles educativos · Estado de México
        </p>
        <div class="hero-ctas">
          <a routerLink="/convocatorias">
            <p-button label="Ver convocatorias" icon="pi pi-search"
              [style]="{'background':'#d4a017','border-color':'#d4a017','color':'#1a1a2e','font-weight':'700'}"
              size="large" />
          </a>
          <a routerLink="/seguimiento">
            <p-button label="Consultar mi postulación" icon="pi pi-file-check" severity="secondary"
              [outlined]="true" size="large"
              [style]="{'color':'#fff','border-color':'rgba(255,255,255,.6)'}" />
          </a>
        </div>
      </div>
    </section>

    <!-- ══ CONVOCATORIAS — OFERTA EDUCATIVA ═════════════════════ -->
    <section class="section">
      <div class="container">
        <div class="section-header cat-header educativa">
          <div class="cat-header-left">
            <i class="pi pi-graduation-cap cat-icon educativa-icon"></i>
            <div>
              <h2>Oferta Educativa</h2>
              <p class="cat-desc">Inscripciones, reinscripciones, becas e intercambios</p>
            </div>
          </div>
          <a [routerLink]="['/convocatorias']" [queryParams]="{categoria:'OFERTA_EDUCATIVA'}" class="ver-todas educativa-link">
            Ver todas <i class="pi pi-arrow-right"></i>
          </a>
        </div>

        @if (cargandoConvs()) {
          <div class="loading-grid">
            <div class="skeleton-card" *ngFor="let i of [1,2,3]"></div>
          </div>
        } @else if (educativas().length === 0) {
          <div class="empty-convs card">
            <i class="pi pi-calendar" style="font-size:2rem;color:#dee2e6"></i>
            <p>No hay convocatorias de inscripción activas en este momento.</p>
          </div>
        } @else {
          <div class="convs-grid">
            @for (c of educativas(); track c['id']) {
              <a [routerLink]="['/convocatorias', c['id']]" class="conv-card">
                <div class="conv-tipo-bar" [style.background]="tipoColor(c['tipo'])"></div>
                <div class="conv-body">
                  <div class="conv-header">
                    <span class="conv-tipo-badge educativa-badge">{{ tipoLabel(c['tipo']) }}</span>
                    @if (c['nombre_plantel']) {
                      <span class="conv-plantel-badge">{{ c['nombre_plantel'] }}</span>
                    } @else {
                      <span class="conv-plantel-badge all">Todos los planteles</span>
                    }
                  </div>
                  <h3 class="conv-titulo">{{ c['titulo'] }}</h3>
                  <p class="conv-cierre">
                    <i class="pi pi-calendar-times"></i>
                    Cierre: {{ c['fecha_cierre_postulacion'] | date:'d MMM yyyy' }}
                  </p>
                </div>
                <div class="conv-footer educativa-footer">Ver detalle <i class="pi pi-arrow-right"></i></div>
              </a>
            }
          </div>
        }
      </div>
    </section>

    <!-- ══ CONVOCATORIAS — RECURSOS HUMANOS ══════════════════════ -->
    <section class="section bg-light">
      <div class="container">
        <div class="section-header cat-header rrhh">
          <div class="cat-header-left">
            <i class="pi pi-briefcase cat-icon rrhh-icon"></i>
            <div>
              <h2 class="rrhh-title">Recursos Humanos</h2>
              <p class="cat-desc">Vacantes docentes y administrativas</p>
            </div>
          </div>
          <a [routerLink]="['/convocatorias']" [queryParams]="{categoria:'RECURSOS_HUMANOS'}" class="ver-todas rrhh-link">
            Ver todas <i class="pi pi-arrow-right"></i>
          </a>
        </div>

        @if (cargandoConvs()) {
          <div class="loading-grid">
            <div class="skeleton-card" *ngFor="let i of [1,2]"></div>
          </div>
        } @else if (rrhh().length === 0) {
          <div class="empty-convs card">
            <i class="pi pi-briefcase" style="font-size:2rem;color:#dee2e6"></i>
            <p>No hay vacantes activas en este momento.</p>
          </div>
        } @else {
          <div class="convs-grid">
            @for (c of rrhh(); track c['id']) {
              <a [routerLink]="['/convocatorias', c['id']]" class="conv-card rrhh-card">
                <div class="conv-tipo-bar" [style.background]="tipoColor(c['tipo'])"></div>
                <div class="conv-body">
                  <div class="conv-header">
                    <span class="conv-tipo-badge rrhh-badge">{{ tipoLabel(c['tipo']) }}</span>
                    @if (c['nombre_plantel']) {
                      <span class="conv-plantel-badge">{{ c['nombre_plantel'] }}</span>
                    } @else {
                      <span class="conv-plantel-badge all">Todos los planteles</span>
                    }
                  </div>
                  <h3 class="conv-titulo">{{ c['titulo'] }}</h3>
                  <p class="conv-cierre rrhh-cierre">
                    <i class="pi pi-calendar-times"></i>
                    Cierre: {{ c['fecha_cierre_postulacion'] | date:'d MMM yyyy' }}
                  </p>
                </div>
                <div class="conv-footer rrhh-footer">Ver detalle <i class="pi pi-arrow-right"></i></div>
              </a>
            }
          </div>
        }
      </div>
    </section>

    <!-- ══ PLANTELES ════════════════════════════════════════════ -->
    <section class="section bg-light">
      <div class="container">
        <div class="section-header centered">
          <h2>Nuestros Planteles</h2>
          <p>Presencia educativa en tres municipios del Estado de México</p>
        </div>
        <div class="planteles-grid">
          @for (p of planteles; track p.nombre) {
            <div class="plantel-card">
              <div class="plantel-header" [style.background]="p.color">
                <i class="pi pi-building" style="font-size:2rem;color:#d4a017"></i>
                <h3>{{ p.nombre }}</h3>
                <p>{{ p.municipio }}</p>
              </div>
              <div class="plantel-body">
                <div class="plantel-contact">
                  <div class="contact-row">
                    <i class="pi pi-map-marker"></i>
                    <span>{{ p.direccion }}, CP {{ p.cp }}</span>
                  </div>
                  @for (tel of p.telefonos; track tel) {
                    <div class="contact-row">
                      <i class="pi pi-phone"></i>
                      <a [href]="'tel:+52' + tel.replace(/-/g, '')">{{ tel }}</a>
                    </div>
                  }
                  <div class="contact-row">
                    <i class="pi pi-envelope"></i>
                    <a [href]="'mailto:' + p.email">{{ p.email }}</a>
                  </div>
                </div>
                <h4>Niveles disponibles</h4>
                @for (n of p.niveles; track n) {
                  <div class="nivel-item">
                    <i class="pi pi-check-circle" style="color:#198754"></i>
                    {{ n }}
                  </div>
                }
                <a [routerLink]="['/convocatorias']" [queryParams]="{plantel: p.nombre}" class="btn-ver-conv">
                  Ver convocatorias de {{ p.nombre }}
                  <i class="pi pi-arrow-right"></i>
                </a>
              </div>
            </div>
          }
        </div>
      </div>
    </section>

    <!-- ══ OFERTA EDUCATIVA ══════════════════════════════════════ -->
    <section class="section">
      <div class="container">
        <div class="section-header centered">
          <h2>Oferta Educativa</h2>
          <p>Formamos personas capaces de desarrollarse plenamente</p>
        </div>
        <div class="niveles-grid">
          @for (n of niveles; track n.nombre) {
            <div class="nivel-card">
              <i class="pi" [class]="n.icono" style="font-size:2rem;color:#8B0000"></i>
              <h3>{{ n.nombre }}</h3>
              <div class="nivel-org">{{ n.org }}</div>
              <p>{{ n.desc }}</p>
              <a [routerLink]="['/convocatorias']" [queryParams]="{nivel: n.nombre.toUpperCase()}"
                 class="nivel-link">Convocatorias de {{ n.nombre }} →</a>
            </div>
          }
        </div>
      </div>
    </section>

    <!-- ══ CÓMO FUNCIONA ════════════════════════════════════════ -->
    <section class="section bg-light">
      <div class="container">
        <div class="section-header centered">
          <h2>¿Cómo postularme?</h2>
          <p>Tu proceso de inscripción en 4 sencillos pasos</p>
        </div>
        <div class="pasos-grid">
          @for (paso of pasos; track paso.num) {
            <div class="paso-card">
              <div class="paso-num">{{ paso.num }}</div>
              <i class="pi" [class]="paso.icono" style="font-size:1.75rem;color:#8B0000"></i>
              <h4>{{ paso.titulo }}</h4>
              <p>{{ paso.desc }}</p>
            </div>
          }
        </div>
        <div style="text-align:center;margin-top:2.5rem">
          <a routerLink="/registro">
            <p-button label="Crear mi cuenta y comenzar"
              [style]="{'background':'#8B0000','border-color':'#8B0000'}" size="large"
              icon="pi pi-user-plus" />
          </a>
        </div>
      </div>
    </section>

    <!-- ══ CONTACTO RÁPIDO ══════════════════════════════════════ -->
    <section class="section contact-strip">
      <div class="container contact-inner">
        <div class="contact-info">
          <h3>¿Tienes dudas sobre el proceso?</h3>
          <p>Nuestro equipo de admisiones te atiende con gusto.</p>
        </div>
        <div class="contact-links">
          <a href="mailto:admisiones@nevadi.edu.mx" class="contact-btn">
            <i class="pi pi-envelope"></i> admisiones&#64;nevadi.edu.mx
          </a>
          <a href="https://institutonevadi.edu.mx" target="_blank" rel="noopener" class="contact-btn alt">
            <i class="pi pi-globe"></i> institutonevadi.edu.mx
          </a>
        </div>
      </div>
    </section>
  `,
  styles: [`
    /* ── Hero ──────────────────────────────────────────── */
    .hero {
      position: relative; min-height: 520px;
      display: flex; align-items: center;
      background: linear-gradient(135deg, #5a0000 0%, #8B0000 40%, #6b0000 100%);
      overflow: hidden;
    }
    .hero-bg {
      position: absolute; inset: 0;
      background: url("data:image/svg+xml,%3Csvg width='60' height='60' viewBox='0 0 60 60' xmlns='http://www.w3.org/2000/svg'%3E%3Cg fill='none' fill-rule='evenodd'%3E%3Cg fill='%23ffffff' fill-opacity='0.03'%3E%3Cpath d='M36 34v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zm0-30V0h-2v4h-4v2h4v4h2V6h4V4h-4zM6 34v-4H4v4H0v2h4v4h2v-4h4v-2H6zM6 4V0H4v4H0v2h4v4h2V6h4V4H6z'/%3E%3C/g%3E%3C/g%3E%3C/svg%3E");
      opacity: .4;
    }
    .hero-logo-pill {
      display: inline-flex;
      background: rgba(255,255,255,.95);
      border-radius: 10px;
      padding: 8px 18px;
      margin-bottom: 1.5rem;
      box-shadow: 0 2px 12px rgba(0,0,0,.25);
    }
    .hero-logo { height: 48px; width: auto; display: block; }
    .hero-content { position: relative; z-index: 1; color: #fff; padding: 4rem 0; }
    .hero-badge {
      display: inline-block; background: rgba(212,160,23,.2); border: 1px solid rgba(212,160,23,.5);
      color: #d4a017; font-size: .78rem; font-weight: 700; letter-spacing: .1em;
      text-transform: uppercase; padding: .3rem .85rem; border-radius: 20px; margin-bottom: 1.25rem;
    }
    .hero-title {
      font-family: Jost, sans-serif; font-size: clamp(2.4rem, 6vw, 4rem);
      font-weight: 800; line-height: 1.05; margin-bottom: .75rem; color: #fff;
    }
    .gold { color: #d4a017; }
    .hero-lema { font-size: 1.05rem; font-style: italic; opacity: .85; margin-bottom: .5rem; }
    .hero-desc { font-size: .9rem; opacity: .65; margin-bottom: 2rem; }
    .hero-ctas { display: flex; gap: 1rem; flex-wrap: wrap; }

    /* ── Sections ──────────────────────────────────────── */
    .section { padding: 4rem 0; }
    .bg-light { background: #f8f9fa; }
    .section-header {
      display: flex; justify-content: space-between; align-items: center; margin-bottom: 2rem;
    }
    .section-header.centered { flex-direction: column; text-align: center; gap: .5rem; }
    .section-header h2 { font-family: Jost, sans-serif; font-size: 1.6rem; font-weight: 700; color: #1a1a2e; }

    /* ── Cabeceras de categoría ─────────────────────────── */
    .cat-header { background: #f8fffe; border-radius: 10px; padding: .85rem 1.1rem; border: 1px solid #dee2e6; margin-bottom: 1.5rem !important; }
    .cat-header.rrhh { background: #fff8f8; border-color: #f5c6c6; }
    .cat-header-left { display: flex; align-items: center; gap: .75rem; }
    .cat-icon { font-size: 1.6rem; }
    .educativa-icon { color: #198754; }
    .rrhh-icon      { color: #8B0000; }
    .cat-header h2  { margin: 0 !important; }
    .rrhh-title { color: #8B0000 !important; }
    .cat-desc { font-size: .83rem; color: #6c757d; margin: .1rem 0 0; }
    .ver-todas { color: #8B0000; font-size: .9rem; font-weight: 600; display: flex; align-items: center; gap: .35rem; text-decoration: none; }
    .educativa-link { color: #198754 !important; }
    .rrhh-link      { color: #8B0000 !important; }

    /* ── Convocatorias grid ────────────────────────────── */
    .convs-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 1.25rem; }
    .conv-card { display: flex; flex-direction: column; background: #fff; border-radius: 12px; overflow: hidden; border: 1px solid #dee2e6; text-decoration: none; color: inherit; transition: box-shadow .2s, transform .2s; }
    .conv-card:hover { box-shadow: 0 8px 24px rgba(0,0,0,.12); transform: translateY(-3px); }
    .rrhh-card { border-color: #f5c6c6; }
    .conv-tipo-bar { height: 5px; }
    .conv-body { padding: 1.25rem; flex: 1; }
    .conv-header { display: flex; gap: .5rem; flex-wrap: wrap; margin-bottom: .75rem; }
    .conv-tipo-badge { font-size: .72rem; font-weight: 700; background: #f0e8d5; color: #8B4513; padding: .15rem .55rem; border-radius: 4px; }
    .educativa-badge { background: #d1e7dd; color: #0a3622; }
    .rrhh-badge      { background: #f8d7da; color: #6b0000; }
    .conv-plantel-badge { font-size: .72rem; background: #f8f9fa; color: #495057; padding: .15rem .55rem; border-radius: 4px; border: 1px solid #dee2e6; }
    .conv-plantel-badge.all { background: #e8f4fd; color: #084298; border-color: #b6d4fe; }
    .conv-titulo { font-size: .95rem; font-weight: 700; color: #1a1a2e; margin-bottom: .6rem; line-height: 1.4; }
    .conv-cierre { font-size: .8rem; color: #198754; display: flex; align-items: center; gap: .35rem; }
    .rrhh-cierre { color: #8B0000; }
    .conv-footer { padding: .75rem 1.25rem; background: #f8f9fa; font-size: .82rem; color: #198754; font-weight: 600; display: flex; align-items: center; gap: .35rem; }
    .educativa-footer { color: #198754; }
    .rrhh-footer      { color: #8B0000; }

    /* ── Empty convocatorias ────────────────────────────── */
    .empty-convs { text-align: center; padding: 3rem 2rem; display: flex; flex-direction: column; align-items: center; gap: 1rem; }
    .empty-convs h3 { color: #495057; }
    .empty-convs p { color: #6c757d; font-size: .9rem; max-width: 400px; }
    .loading-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1.25rem; }
    .skeleton-card { height: 180px; background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%); background-size: 200% 100%; animation: shimmer 1.5s infinite; border-radius: 12px; }
    @keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }

    /* ── Planteles ─────────────────────────────────────── */
    .planteles-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 1.5rem; }
    .plantel-card { background: #fff; border-radius: 14px; overflow: hidden; border: 1px solid #dee2e6; box-shadow: 0 2px 8px rgba(0,0,0,.05); }
    .plantel-header { padding: 1.75rem 1.5rem 1.25rem; color: #fff; display: flex; flex-direction: column; gap: .4rem; }
    .plantel-header h3 { font-family: Jost, sans-serif; font-size: 1.3rem; font-weight: 700; }
    .plantel-header p { font-size: .82rem; opacity: .75; }
    .plantel-body { padding: 1.25rem 1.5rem; }
    .plantel-body h4 { font-size: .78rem; text-transform: uppercase; letter-spacing: .07em; color: #6c757d; margin-bottom: .75rem; }
    .plantel-contact {
      background: #f8f9fa; border-radius: 8px; padding: .85rem 1rem;
      margin-bottom: 1rem; display: flex; flex-direction: column; gap: .4rem;
    }
    .contact-row {
      display: flex; align-items: flex-start; gap: .5rem; font-size: .82rem; color: #495057;
    }
    .contact-row i { color: #8B0000; font-size: .78rem; margin-top: 2px; flex-shrink: 0; }
    .contact-row a { color: #495057; text-decoration: none; }
    .contact-row a:hover { color: #8B0000; text-decoration: underline; }
    .nivel-item { display: flex; align-items: center; gap: .5rem; font-size: .88rem; color: #495057; margin-bottom: .4rem; }
    .btn-ver-conv {
      display: flex; align-items: center; gap: .4rem; justify-content: center;
      margin-top: 1.25rem; padding: .55rem; background: #f8f9fa; border: 1px solid #dee2e6;
      border-radius: 8px; color: #8B0000; font-size: .85rem; font-weight: 600; text-decoration: none;
      transition: background .15s;
    }
    .btn-ver-conv:hover { background: #fee2e2; }

    /* ── Niveles ──────────────────────────────────────── */
    .niveles-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1.5rem; }
    .nivel-card {
      background: #fff; border: 1px solid #dee2e6; border-radius: 14px; padding: 2rem 1.5rem;
      text-align: center; display: flex; flex-direction: column; align-items: center; gap: .6rem;
    }
    .nivel-card h3 { font-family: Jost, sans-serif; font-size: 1.15rem; font-weight: 700; color: #1a1a2e; }
    .nivel-org { background: #f0e8d5; color: #7d4e00; font-size: .72rem; font-weight: 700; padding: .2rem .6rem; border-radius: 4px; }
    .nivel-card p { color: #6c757d; font-size: .88rem; }
    .nivel-link { color: #8B0000; font-size: .85rem; font-weight: 600; text-decoration: none; }

    /* ── Pasos ───────────────────────────────────────── */
    .pasos-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 1.5rem; }
    .paso-card {
      background: #fff; border: 1px solid #dee2e6; border-radius: 14px; padding: 1.75rem 1.25rem;
      text-align: center; display: flex; flex-direction: column; align-items: center; gap: .5rem;
      position: relative;
    }
    .paso-num {
      width: 36px; height: 36px; border-radius: 50%; background: #8B0000; color: #fff;
      display: flex; align-items: center; justify-content: center;
      font-family: Jost, sans-serif; font-weight: 700; font-size: 1rem;
    }
    .paso-card h4 { font-size: .92rem; font-weight: 700; color: #1a1a2e; }
    .paso-card p  { font-size: .82rem; color: #6c757d; }

    /* ── Contact strip ──────────────────────────────── */
    .contact-strip { background: #8B0000; color: #fff; padding: 2.5rem 0; margin-top: 0; }
    .contact-inner { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1.5rem; }
    .contact-info h3 { font-family: Jost, sans-serif; font-size: 1.2rem; font-weight: 700; margin-bottom: .25rem; }
    .contact-info p  { opacity: .75; font-size: .9rem; }
    .contact-links { display: flex; gap: 1rem; flex-wrap: wrap; }
    .contact-btn {
      display: flex; align-items: center; gap: .5rem;
      background: rgba(255,255,255,.15); border: 1px solid rgba(255,255,255,.4);
      color: #fff; padding: .6rem 1.25rem; border-radius: 8px; font-size: .9rem; text-decoration: none;
      transition: background .15s;
    }
    .contact-btn:hover  { background: rgba(255,255,255,.25); }
    .contact-btn.alt { background: #d4a017; border-color: #d4a017; color: #1a1a2e; font-weight: 600; }

    /* ── Responsive ──────────────────────────────────── */
    @media (max-width: 900px) {
      .pasos-grid { grid-template-columns: repeat(2, 1fr); }
      .niveles-grid { grid-template-columns: 1fr; }
    }
    @media (max-width: 640px) {
      .pasos-grid { grid-template-columns: 1fr; }
      .planteles-grid { grid-template-columns: 1fr; }
      .hero-ctas { flex-direction: column; }
      .contact-inner { flex-direction: column; text-align: center; }
      .contact-links { justify-content: center; }
    }
  `],
})
export class InicioComponent implements OnInit {
  private readonly api = inject(PortalApiService);

  planteles        = PLANTELES;
  niveles          = NIVELES;
  convocatorias    = signal<any[]>([]);
  cargandoConvs    = signal(true);

  readonly pasos = [
    { num: 1, icono: 'pi-user-plus', titulo: 'Crea tu cuenta', desc: 'Regístrate con tu correo. Solo toma 2 minutos.' },
    { num: 2, icono: 'pi-search',    titulo: 'Elige convocatoria', desc: 'Filtra por plantel, nivel o tipo de proceso.' },
    { num: 3, icono: 'pi-upload',    titulo: 'Sube documentos', desc: 'Adjunta los documentos requeridos de forma segura.' },
    { num: 4, icono: 'pi-send',      titulo: 'Envía y da seguimiento', desc: 'Recibe notificaciones con el estado de tu postulación.' },
  ];

  readonly TIPO_LABEL: Record<string, string> = {
    INSCRIPCION: 'Inscripción', REINSCRIPCION: 'Reinscripción',
    VACANTE_DOCENTE: 'Vacante Docente', VACANTE_ADMINISTRATIVA: 'Vacante Administrativa',
    BECA: 'Beca', INTERCAMBIO: 'Intercambio', EXTRACURRICULAR: 'Extracurricular',
  };
  readonly TIPO_COLOR: Record<string, string> = {
    INSCRIPCION: '#198754', REINSCRIPCION: '#0d6efd', VACANTE_DOCENTE: '#8B0000',
    VACANTE_ADMINISTRATIVA: '#6f42c1', BECA: '#d4a017', INTERCAMBIO: '#0dcaf0',
    EXTRACURRICULAR: '#fd7e14',
  };

  ngOnInit() {
    this.api.get<any>('/convocatorias', { limit: 9 }).subscribe({
      next: r  => { this.convocatorias.set(r['data'] ?? r ?? []); this.cargandoConvs.set(false); },
      error: () => this.cargandoConvs.set(false),
    });
  }

  educativas() { return this.convocatorias().filter(c => c['categoria'] === 'OFERTA_EDUCATIVA'); }
  rrhh()       { return this.convocatorias().filter(c => c['categoria'] === 'RECURSOS_HUMANOS'); }

  tipoLabel(t: string) { return this.TIPO_LABEL[t] ?? t; }
  tipoColor(t: string) { return this.TIPO_COLOR[t] ?? '#6c757d'; }
}
