import { Component, OnInit, signal, inject, Input, SecurityContext } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { RouterLink, Router } from '@angular/router';
import { DomSanitizer, SafeHtml, SafeResourceUrl } from '@angular/platform-browser';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { PortalApiService } from '../../core/services/portal-api.service';
import { PortalAuthService } from '../../core/services/portal-auth.service';

@Component({
  selector: 'app-convocatoria-detalle',
  standalone: true,
  imports: [CommonModule, RouterLink, ProgressSpinnerModule, ButtonModule, TagModule, DatePipe],
  template: `
    <div class="container det-page">
      <a routerLink="/convocatorias" class="back-link"><i class="pi pi-arrow-left"></i> Todas las convocatorias</a>

      @if (loading()) {
        <div style="text-align:center;padding:4rem"><p-progressSpinner /></div>
      } @else if (conv()) {
        <div class="card det-card">

          <!-- Header -->
          <div class="det-header">
            <div class="det-badges">
              <span class="badge-tipo" [style.background]="tipoColor(conv()['tipo'])">
                {{ tipoLabel(conv()['tipo']) }}
              </span>
              @if (conv()['categoria'] === 'RECURSOS_HUMANOS') {
                <span class="badge-cat cat-rrhh"><i class="pi pi-briefcase"></i> Recursos Humanos</span>
              } @else {
                <span class="badge-cat cat-edu"><i class="pi pi-graduation-cap"></i> Oferta Educativa</span>
              }
            </div>
            <h1 class="det-titulo">{{ conv()['titulo'] }}</h1>
            <div class="det-meta">
              @if (conv()['nombre_plantel']) {
                <span><i class="pi pi-building"></i> {{ conv()['nombre_plantel'] }}</span>
              } @else {
                <span><i class="pi pi-globe"></i> Todos los planteles</span>
              }
              @if (conv()['nombre_nivel']) {
                <span><i class="pi pi-graduation-cap"></i> {{ conv()['nombre_nivel'] }}</span>
              }
              <span><i class="pi pi-calendar"></i> Inicio: {{ conv()['fecha_inicio_postulacion'] | date:'d MMM yyyy' }}</span>
              <span class="cierre"><i class="pi pi-calendar-times"></i> Cierre: {{ conv()['fecha_cierre_postulacion'] | date:'d MMM yyyy' }}</span>
            </div>
            @if (conv()['cupo_maximo']) {
              <div class="cupo-info">
                <i class="pi pi-users"></i>
                <strong>Cupo:</strong> {{ conv()['cupo_actual'] }} / {{ conv()['cupo_maximo'] }} postulaciones registradas
              </div>
            }
          </div>

          <!-- ══════════════════════════════════════
               SECCIONES LMS — si existen
               ══════════════════════════════════════ -->
          @if (conv()['secciones']?.length) {
            <div class="lms-body">
              @for (s of conv()['secciones']; track s['id']) {
                <div class="lms-seccion" [attr.data-tipo]="s['tipo_seccion']">

                  @switch (s['tipo_seccion']) {

                    @case ('INTRO') {
                      <div class="sec-intro">
                        @if (s['titulo']) { <h2 class="sec-intro-titulo">{{ s['titulo'] }}</h2> }
                        @if (s['contenido']) {
                          <div class="sec-html" [innerHTML]="safeHtml(s['contenido'])"></div>
                        }
                      </div>
                    }

                    @case ('ENCABEZADO') {
                      <div class="sec-encabezado">
                        <h2>{{ s['titulo'] }}</h2>
                        <div class="sec-enc-line"></div>
                      </div>
                    }

                    @case ('TEXTO') {
                      <div class="sec-texto">
                        @if (s['titulo']) { <h3 class="sec-subtitulo">{{ s['titulo'] }}</h3> }
                        <div class="sec-html" [innerHTML]="safeHtml(s['contenido'])"></div>
                      </div>
                    }

                    @case ('LISTA') {
                      <div class="sec-lista">
                        @if (s['titulo']) { <h3 class="sec-subtitulo">{{ s['titulo'] }}</h3> }
                        <ul class="lms-lista">
                          @for (item of parseDatos(s['datos']); track $index) {
                            <li><i class="pi pi-check-circle"></i> {{ item }}</li>
                          }
                        </ul>
                      </div>
                    }

                    @case ('PROCESO') {
                      <div class="sec-proceso">
                        @if (s['titulo']) { <h3 class="sec-subtitulo">{{ s['titulo'] }}</h3> }
                        <div class="timeline">
                          @for (paso of parseDatos(s['datos']); track $index) {
                            <div class="timeline-item">
                              <div class="tl-num">{{ paso['num'] ?? ($index + 1) }}</div>
                              <div class="tl-content">
                                <strong>{{ paso['titulo'] }}</strong>
                                @if (paso['desc']) { <p>{{ paso['desc'] }}</p> }
                              </div>
                            </div>
                          }
                        </div>
                      </div>
                    }

                    @case ('FAQ') {
                      <div class="sec-faq">
                        @if (s['titulo']) { <h3 class="sec-subtitulo">{{ s['titulo'] }}</h3> }
                        <div class="faq-list">
                          @for (faq of parseDatos(s['datos']); track $index) {
                            <div class="faq-item" [class.open]="faqOpen().has(s['id'] + '-' + $index)">
                              <button class="faq-q" (click)="toggleFaq(s['id'] + '-' + $index)">
                                <span>{{ faq['pregunta'] }}</span>
                                <i class="pi" [class.pi-plus]="!faqOpen().has(s['id'] + '-' + $index)"
                                             [class.pi-minus]="faqOpen().has(s['id'] + '-' + $index)"></i>
                              </button>
                              @if (faqOpen().has(s['id'] + '-' + $index)) {
                                <div class="faq-a">{{ faq['respuesta'] }}</div>
                              }
                            </div>
                          }
                        </div>
                      </div>
                    }

                    @case ('VIDEO') {
                      <div class="sec-video">
                        @if (s['titulo']) { <h3 class="sec-subtitulo">{{ s['titulo'] }}</h3> }
                        @let vd = parseDatos(s['datos']);
                        @if (vd['tipo'] === 'youtube' || vd['tipo'] === 'vimeo') {
                          <div class="video-wrap">
                            <iframe [src]="safeVideoUrl(vd)" frameborder="0" allowfullscreen
                              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture">
                            </iframe>
                          </div>
                        } @else if (vd['url']) {
                          <video controls [src]="vd['url']" [poster]="vd['poster_url']" class="video-mp4"></video>
                        }
                      </div>
                    }

                    @case ('GALERIA') {
                      <div class="sec-galeria">
                        @if (s['titulo']) { <h3 class="sec-subtitulo">{{ s['titulo'] }}</h3> }
                        <div class="galeria-grid">
                          @for (img of parseDatos(s['datos']); track $index) {
                            <figure class="galeria-item">
                              <img [src]="img['url']" [alt]="img['alt'] ?? img['caption'] ?? ''" loading="lazy">
                              @if (img['caption']) { <figcaption>{{ img['caption'] }}</figcaption> }
                            </figure>
                          }
                        </div>
                      </div>
                    }

                    @case ('CTA') {
                      @let cd = parseDatos(s['datos']);
                      <div class="sec-cta" [class.cta-secundario]="cd['variante'] === 'secundario'">
                        @if (s['titulo']) { <p class="cta-titulo">{{ s['titulo'] }}</p> }
                        @if (s['contenido']) { <p class="cta-desc">{{ s['contenido'] }}</p> }
                        <a [href]="cd['url']" target="_blank" rel="noopener noreferrer" class="cta-btn">
                          {{ cd['texto'] ?? 'Ver más' }} <i class="pi pi-external-link"></i>
                        </a>
                      </div>
                    }

                    @case ('AVISO') {
                      @let ad = parseDatos(s['datos']);
                      <div class="sec-aviso" [class.aviso-warn]="ad['nivel'] === 'warn'"
                           [class.aviso-danger]="ad['nivel'] === 'danger'">
                        <i class="pi" [class.pi-info-circle]="!ad['icono']"
                           [attr.class]="ad['icono'] ? 'pi ' + ad['icono'] : null"></i>
                        <div>
                          @if (s['titulo']) { <strong>{{ s['titulo'] }}</strong><br> }
                          @if (s['contenido']) { <span>{{ s['contenido'] }}</span> }
                        </div>
                      </div>
                    }

                  }
                </div>
              }
            </div>
          } @else {
            <!-- Fallback: texto plano si no hay secciones LMS -->
            @if (conv()['descripcion']) {
              <div class="det-seccion">
                <h3>Descripción</h3>
                <p style="white-space:pre-line">{{ conv()['descripcion'] }}</p>
              </div>
            }
            @if (conv()['requisitos_generales']) {
              <div class="det-seccion">
                <h3>Requisitos Generales</h3>
                <p style="white-space:pre-line">{{ conv()['requisitos_generales'] }}</p>
              </div>
            }
          }

          <!-- Documentos requeridos (siempre visible) -->
          @if (conv()['requisitos_documentos']?.length) {
            <div class="det-seccion">
              <h3>Documentos Requeridos</h3>
              <ul class="docs-list">
                @for (r of conv()['requisitos_documentos']; track r['id']) {
                  <li>
                    <i class="pi pi-file" [style.color]="r['es_obligatorio'] ? '#8B0000' : '#6c757d'"></i>
                    <div>
                      <strong>{{ r['nombre'] }}</strong>
                      @if (r['es_obligatorio']) { <span class="obl">Obligatorio</span> }
                      @if (r['descripcion']) { <br><small style="color:#6c757d">{{ r['descripcion'] }}</small> }
                      <br><small>Formatos: {{ formatosMime(r['tipos_mime_permitidos']) }} | Máx: {{ r['tamano_maximo_mb'] }} MB</small>
                    </div>
                  </li>
                }
              </ul>
            </div>
          }

          <!-- Aviso privacidad -->
          <div class="privacidad-aviso">
            <i class="pi pi-shield"></i>
            <p>Los datos que proporciones serán tratados de forma confidencial conforme a nuestra
            <a routerLink="/aviso-privacidad">Política de Privacidad</a> y los derechos
            ARCO establecidos en la LFPDPPP.</p>
          </div>

          <!-- CTA postular -->
          <div class="det-cta-bar">
            @if (auth.isLoggedIn()) {
              <p-button label="Postularme" icon="pi pi-send"
                [style]="{'background':'#8B0000','border-color':'#8B0000'}"
                (onClick)="postular()" [loading]="postulando()" />
            } @else {
              <p-button label="Iniciar sesión para postular" icon="pi pi-sign-in"
                [style]="{'background':'#8B0000','border-color':'#8B0000'}"
                (onClick)="irLogin()" />
              <p-button label="Crear cuenta" severity="secondary" [outlined]="true"
                (onClick)="irRegistro()" />
            }
            <a routerLink="/seguimiento" class="link-seg">
              <i class="pi pi-search"></i> Consultar estado de postulación
            </a>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .det-page { padding: 2rem 1.5rem; max-width: 860px; }
    .back-link { color: #8B0000; font-size: .88rem; display: flex; align-items: center; gap: .4rem; text-decoration: none; }
    .back-link:hover { text-decoration: underline; }
    .det-card { margin-top: 1rem; padding: 1.75rem; }

    /* Header */
    .det-header { margin-bottom: 1.5rem; }
    .det-badges { display: flex; gap: .5rem; flex-wrap: wrap; margin-bottom: .65rem; }
    .badge-tipo { color: #fff; font-size: .75rem; font-weight: 700; padding: .25rem .65rem; border-radius: 4px; text-transform: uppercase; }
    .badge-cat { display: flex; align-items: center; gap: .3rem; font-size: .75rem; font-weight: 600; padding: .25rem .65rem; border-radius: 4px; }
    .cat-rrhh { background: #f8d7da; color: #842029; }
    .cat-edu  { background: #d1e7dd; color: #0f5132; }
    .det-titulo { font-size: 1.55rem; margin: .5rem 0 .75rem; line-height: 1.25; }
    .det-meta { display: flex; flex-wrap: wrap; gap: .5rem 1.25rem; font-size: .85rem; color: #6c757d; margin-bottom: 1rem; }
    .det-meta span { display: flex; align-items: center; gap: .3rem; }
    .cierre { color: #8B0000 !important; font-weight: 600; }
    .cupo-info { display: flex; align-items: center; gap: .5rem; background: #f8f9fa; border: 1px solid #dee2e6; border-radius: 6px; padding: .5rem .75rem; font-size: .88rem; }

    /* ── LMS Sections ── */
    .lms-body { display: flex; flex-direction: column; gap: 1.5rem; margin: 1.25rem 0; }
    .lms-seccion { border-radius: 8px; }

    /* INTRO */
    .sec-intro { background: linear-gradient(135deg, #8B0000 0%, #b22222 100%); color: #fff; padding: 2rem; border-radius: 8px; }
    .sec-intro-titulo { font-size: 1.35rem; margin: 0 0 .75rem; font-weight: 700; }
    .sec-intro .sec-html { opacity: .92; }
    .sec-intro .sec-html * { color: inherit; }

    /* ENCABEZADO */
    .sec-encabezado { padding: .5rem 0; }
    .sec-encabezado h2 { font-size: 1.1rem; font-weight: 700; color: #8B0000; margin: 0 0 .4rem; text-transform: uppercase; letter-spacing: .05em; }
    .sec-enc-line { height: 2px; background: linear-gradient(90deg, #8B0000 0%, transparent 100%); border-radius: 1px; }

    /* TEXTO */
    .sec-texto { padding: .25rem 0; }
    .sec-subtitulo { font-size: 1rem; font-weight: 600; color: #343a40; margin: 0 0 .6rem; }
    .sec-html { line-height: 1.65; color: #495057; }
    .sec-html p { margin: 0 0 .75rem; }
    .sec-html ul, .sec-html ol { margin: .5rem 0 .75rem 1.25rem; }
    .sec-html strong { color: #212529; }
    .sec-html a { color: #8B0000; }

    /* LISTA */
    .lms-lista { list-style: none; display: flex; flex-direction: column; gap: .5rem; }
    .lms-lista li { display: flex; align-items: flex-start; gap: .5rem; font-size: .92rem; }
    .lms-lista li .pi { color: #198754; margin-top: .15rem; flex-shrink: 0; }

    /* PROCESO */
    .timeline { display: flex; flex-direction: column; gap: 0; }
    .timeline-item { display: flex; gap: 1rem; padding-bottom: 1.25rem; position: relative; }
    .timeline-item:not(:last-child)::before { content: ''; position: absolute; left: 1.1rem; top: 2.4rem; bottom: 0; width: 2px; background: #dee2e6; }
    .tl-num { width: 2.25rem; height: 2.25rem; border-radius: 50%; background: #8B0000; color: #fff; font-weight: 700; font-size: .9rem; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
    .tl-content { padding-top: .3rem; }
    .tl-content strong { font-size: .95rem; }
    .tl-content p { margin: .25rem 0 0; font-size: .88rem; color: #6c757d; }

    /* FAQ */
    .faq-list { display: flex; flex-direction: column; gap: .5rem; }
    .faq-item { border: 1px solid #dee2e6; border-radius: 6px; overflow: hidden; }
    .faq-q { width: 100%; display: flex; justify-content: space-between; align-items: center; gap: .75rem; padding: .85rem 1rem; background: none; border: none; cursor: pointer; text-align: left; font-size: .92rem; font-weight: 600; color: #343a40; }
    .faq-item.open .faq-q { background: #f8f9fa; color: #8B0000; }
    .faq-a { padding: .75rem 1rem 1rem; font-size: .88rem; color: #495057; border-top: 1px solid #dee2e6; background: #fff; line-height: 1.55; }

    /* VIDEO */
    .video-wrap { position: relative; padding-bottom: 56.25%; height: 0; border-radius: 8px; overflow: hidden; }
    .video-wrap iframe { position: absolute; top: 0; left: 0; width: 100%; height: 100%; }
    .video-mp4 { width: 100%; border-radius: 8px; }

    /* GALERÍA */
    .galeria-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: .75rem; }
    .galeria-item { margin: 0; border-radius: 8px; overflow: hidden; }
    .galeria-item img { width: 100%; height: 140px; object-fit: cover; display: block; }
    .galeria-item figcaption { padding: .4rem .6rem; font-size: .78rem; color: #6c757d; background: #f8f9fa; }

    /* CTA */
    .sec-cta { background: #fff3cd; border: 1px solid #ffc107; border-radius: 8px; padding: 1.25rem 1.5rem; text-align: center; }
    .sec-cta.cta-secundario { background: #cff4fc; border-color: #0dcaf0; }
    .cta-titulo { font-size: 1rem; font-weight: 700; margin: 0 0 .4rem; }
    .cta-desc { font-size: .88rem; color: #6c757d; margin: 0 0 .85rem; }
    .cta-btn { display: inline-flex; align-items: center; gap: .4rem; background: #8B0000; color: #fff; padding: .55rem 1.25rem; border-radius: 6px; font-size: .9rem; font-weight: 600; text-decoration: none; }
    .cta-btn:hover { background: #6d0000; }

    /* AVISO */
    .sec-aviso { display: flex; gap: .75rem; align-items: flex-start; background: #e8f4fd; border-left: 3px solid #0d6efd; padding: .85rem 1rem; border-radius: 0 6px 6px 0; font-size: .88rem; }
    .sec-aviso.aviso-warn   { background: #fff3cd; border-color: #ffc107; }
    .sec-aviso.aviso-danger { background: #f8d7da; border-color: #dc3545; }
    .sec-aviso .pi { font-size: 1.1rem; margin-top: .1rem; flex-shrink: 0; }

    /* Sección clásica (fallback) */
    .det-seccion { margin: 1.25rem 0; }
    .det-seccion h3 { font-size: 1rem; margin-bottom: .5rem; color: #8B0000; }
    .docs-list { list-style: none; display: flex; flex-direction: column; gap: .6rem; }
    .docs-list li { display: flex; align-items: flex-start; gap: .6rem; padding: .5rem .75rem; background: #f8f9fa; border-radius: 6px; font-size: .88rem; }
    .obl { background: #f8d7da; color: #842029; font-size: .72rem; font-weight: 700; padding: .1rem .4rem; border-radius: 4px; margin-left: .4rem; }
    .privacidad-aviso { display: flex; gap: .6rem; background: #e8f4fd; border-left: 3px solid #0d6efd; padding: .75rem; border-radius: 0 6px 6px 0; font-size: .85rem; margin-top: 1.5rem; }
    .det-cta-bar { margin-top: 1.5rem; display: flex; gap: 1rem; flex-wrap: wrap; align-items: center; }
    .link-seg { color: #8B0000; font-size: .88rem; display: flex; align-items: center; gap: .35rem; }
  `],
})
export class ConvocatoriaDetalleComponent implements OnInit {
  @Input() id!: string;

  private readonly api       = inject(PortalApiService);
  private readonly router    = inject(Router);
  private readonly sanitizer = inject(DomSanitizer);
  readonly auth              = inject(PortalAuthService);

  conv       = signal<any | null>(null);
  loading    = signal(true);
  postulando = signal(false);
  faqOpen    = signal<Set<string>>(new Set());

  private readonly TIPO_LABEL: Record<string, string> = {
    INSCRIPCION: 'Inscripción', REINSCRIPCION: 'Reinscripción',
    VACANTE_DOCENTE: 'Vacante Docente', VACANTE_ADMINISTRATIVA: 'Vacante Administrativa',
    BECA: 'Beca', INTERCAMBIO: 'Intercambio', EXTRACURRICULAR: 'Extracurricular',
  };
  private readonly TIPO_COLOR: Record<string, string> = {
    INSCRIPCION: '#198754', REINSCRIPCION: '#0d6efd', VACANTE_DOCENTE: '#8B0000',
    VACANTE_ADMINISTRATIVA: '#6f42c1', BECA: '#d4a017', INTERCAMBIO: '#0dcaf0',
    EXTRACURRICULAR: '#fd7e14',
  };

  ngOnInit() {
    this.api.get<any>(`/convocatorias/${this.id}`).subscribe({
      next: d => { this.conv.set(d); this.loading.set(false); },
      error: () => { this.loading.set(false); this.router.navigate(['/convocatorias']); },
    });
  }

  safeHtml(html: string): SafeHtml {
    return this.sanitizer.sanitize(SecurityContext.HTML, html) ?? '';
  }

  safeVideoUrl(vd: any): SafeResourceUrl {
    let url = vd['url'] ?? '';
    if (vd['tipo'] === 'youtube') {
      const vid = this.extractYoutubeId(url);
      url = `https://www.youtube-nocookie.com/embed/${vid}?rel=0`;
    } else if (vd['tipo'] === 'vimeo') {
      const vid = url.split('/').pop();
      url = `https://player.vimeo.com/video/${vid}`;
    }
    return this.sanitizer.bypassSecurityTrustResourceUrl(url);
  }

  parseDatos(datos: any): any {
    if (!datos) return {};
    if (typeof datos === 'string') {
      try { return JSON.parse(datos); } catch { return {}; }
    }
    return datos;
  }

  toggleFaq(key: string) {
    const s = new Set(this.faqOpen());
    s.has(key) ? s.delete(key) : s.add(key);
    this.faqOpen.set(s);
  }

  postular() {
    this.postulando.set(true);
    this.api.post<any>('/usuario/postulaciones', {
      convocatoria_id: this.id,
      consentimiento_privacidad: true,
    }).subscribe({
      next: r => { this.postulando.set(false); this.router.navigate(['/postulacion', r['id']]); },
      error: (e: any) => {
        this.postulando.set(false);
        alert(e?.error?.mensaje ?? e?.error?.message ?? 'Error al crear postulación');
      },
    });
  }

  irLogin()    { this.router.navigate(['/login'],    { queryParams: { returnUrl: `/convocatorias/${this.id}` } }); }
  irRegistro() { this.router.navigate(['/registro'], { queryParams: { returnUrl: `/convocatorias/${this.id}` } }); }

  tipoLabel(t: string) { return this.TIPO_LABEL[t] ?? t; }
  tipoColor(t: string) { return this.TIPO_COLOR[t] ?? '#6c757d'; }
  formatosMime(mimes: string[]) {
    if (!mimes) return 'PDF';
    return mimes.map((m: string) => m.split('/')[1]?.toUpperCase() ?? m).join(', ');
  }

  private extractYoutubeId(url: string): string {
    const m = url.match(/(?:v=|youtu\.be\/|embed\/)([a-zA-Z0-9_-]{11})/);
    return m ? m[1] : url;
  }
}
