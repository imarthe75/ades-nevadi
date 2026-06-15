import { Component, inject, signal, HostListener } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { PortalAuthService } from './core/services/portal-auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, ButtonModule],
  template: `
    <!-- ── Barra de navegación ─────────────────────────────────────── -->
    <header class="nvd-header" [class.scrolled]="scrolled()">
      <div class="container header-inner">

        <!-- Logo + Nombre -->
        <a routerLink="/" class="brand">
          <div class="brand-logo-pill">
            <img src="nevadi-logo.jpg" alt="Instituto Nevadi" class="brand-logo-img" />
          </div>
          <div class="brand-sep"></div>
          <div class="brand-sub-text">Portal Institucional</div>
        </a>

        <!-- Nav links desktop -->
        <nav class="nav-links" [class.open]="menuAbierto()">
          <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{exact:true}"
             (click)="menuAbierto.set(false)">Inicio</a>
          <a routerLink="/convocatorias" routerLinkActive="active"
             (click)="menuAbierto.set(false)">Convocatorias</a>
          <a routerLink="/seguimiento"
             (click)="menuAbierto.set(false)">Seguimiento</a>

          <a href="https://ades.setag.mx" target="_blank" rel="noopener" class="btn-nav-ades"
             title="Acceso al sistema interno ADES (personal)">
            <i class="pi pi-sign-in"></i> Personal
          </a>

          @if (auth.isLoggedIn()) {
            <a routerLink="/mis-postulaciones" routerLinkActive="active"
               (click)="menuAbierto.set(false)">Mis Postulaciones</a>
            <div class="nav-divider"></div>
            <span class="user-chip">
              <i class="pi pi-user"></i> {{ primerNombre() }}
            </span>
            <button class="btn-logout" (click)="auth.logout()">
              <i class="pi pi-sign-out"></i> Salir
            </button>
          } @else {
            <div class="nav-divider"></div>
            <a routerLink="/login" class="btn-nav-login" (click)="menuAbierto.set(false)">
              Iniciar sesión
            </a>
            <a routerLink="/registro" class="btn-nav-reg" (click)="menuAbierto.set(false)">
              Registrarse
            </a>
          }
        </nav>

        <!-- Hamburger mobile -->
        <button class="hamburger" (click)="menuAbierto.set(!menuAbierto())" aria-label="Menú">
          <span></span><span></span><span></span>
        </button>
      </div>
    </header>

    <!-- Overlay mobile menu -->
    @if (menuAbierto()) {
      <div class="nav-overlay" (click)="menuAbierto.set(false)"></div>
    }

    <main>
      <router-outlet />
    </main>

    <!-- ── Footer ──────────────────────────────────────────────────── -->
    <footer class="nvd-footer">
      <div class="container footer-grid">

        <!-- Columna 1: Institución -->
        <div class="footer-col">
          <div class="footer-brand">
            <div class="footer-logo-pill">
              <img src="nevadi-logo.jpg" alt="Instituto Nevadi" class="footer-logo-img" />
            </div>
          </div>
          <p class="footer-lema">"El único camino para salir adelante es la educación"</p>
          <a href="https://institutonevadi.edu.mx" target="_blank" rel="noopener" class="footer-web">
            <i class="pi pi-globe"></i> institutonevadi.edu.mx
          </a>
        </div>

        <!-- Columna 2: Planteles -->
        <div class="footer-col">
          <h4>Nuestros Planteles</h4>
          <ul>
            <li>
              <i class="pi pi-map-marker"></i>
              <span>
                <strong>Metepec</strong><br>
                Prol. Heriberto Enríquez 1001<br>
                CP 52140 · 722-325-3683
              </span>
            </li>
            <li>
              <i class="pi pi-map-marker"></i>
              <span>
                <strong>Tenancingo</strong><br>
                Carr. Tenancingo-Tenería S/N<br>
                CP 52400 · 714-142-4323
              </span>
            </li>
            <li>
              <i class="pi pi-map-marker"></i>
              <span>
                <strong>Ixtapan de la Sal</strong><br>
                Independencia Pte. 5<br>
                CP 51900 · 721-143-3015
              </span>
            </li>
          </ul>
        </div>

        <!-- Columna 3: Niveles -->
        <div class="footer-col">
          <h4>Oferta Educativa</h4>
          <ul>
            <li><i class="pi pi-book"></i> Primaria — SEP</li>
            <li><i class="pi pi-book"></i> Secundaria — SEP</li>
            <li><i class="pi pi-graduation-cap"></i> Preparatoria — UAEMEX</li>
          </ul>
        </div>

        <!-- Columna 4: Legal + Accesos -->
        <div class="footer-col">
          <h4>Portal</h4>
          <ul>
            <li><a routerLink="/convocatorias">Convocatorias</a></li>
            <li><a routerLink="/seguimiento">Consultar postulación</a></li>
            <li><a routerLink="/aviso-privacidad">Aviso de Privacidad</a></li>
            <li><a routerLink="/arco">Derechos ARCO</a></li>
            <li>
              <a href="mailto:admisiones@nevadi.edu.mx">
                <i class="pi pi-envelope"></i> admisiones&#64;nevadi.edu.mx
              </a>
            </li>
          </ul>
          <div class="footer-ades-link">
            <a href="https://ades.setag.mx" target="_blank" rel="noopener" class="btn-ades-interno">
              <i class="pi pi-sign-in"></i> Acceso Personal / ADES
            </a>
          </div>
        </div>

      </div>
      <div class="footer-bottom">
        <span>© {{ year }} Instituto Nevadi S.C. — Todos los derechos reservados.</span>
        <span>Powered by ADES Nevadi</span>
      </div>
    </footer>
  `,
  styles: [`
    /* ── Header ──────────────────────────────────── */
    .nvd-header {
      background: #8B0000;
      color: #fff;
      padding: .6rem 0;
      position: sticky; top: 0; z-index: 200;
      transition: box-shadow .2s, padding .2s;
    }
    .nvd-header.scrolled { box-shadow: 0 4px 20px rgba(0,0,0,.35); padding: .4rem 0; }

    .header-inner {
      display: flex; align-items: center; justify-content: space-between; gap: 1rem;
    }
    .brand { display: flex; align-items: center; gap: .75rem; color: #fff; text-decoration: none; flex-shrink: 0; }
    .brand-logo-pill {
      background: rgba(255,255,255,.96);
      border-radius: 6px;
      padding: 3px 8px;
      display: flex;
      align-items: center;
      box-shadow: 0 1px 4px rgba(0,0,0,.2);
    }
    .brand-logo-img { height: 28px; width: auto; object-fit: contain; display: block; }
    .brand-sep { width: 1px; height: 26px; background: rgba(255,255,255,.35); }
    .brand-sub-text { font-size: .72rem; color: rgba(255,255,255,.75); font-family: Jost, sans-serif; letter-spacing: .04em; }

    .nav-links {
      display: flex; align-items: center; gap: 1rem; flex-wrap: wrap;
    }
    .nav-links a {
      color: rgba(255,255,255,.82); text-decoration: none; font-size: .88rem;
      transition: color .15s; white-space: nowrap;
    }
    .nav-links a:hover, .nav-links a.active { color: #fff; }
    .nav-links a.active { font-weight: 600; border-bottom: 2px solid #d4a017; padding-bottom: 2px; }

    .nav-divider { width: 1px; height: 20px; background: rgba(255,255,255,.25); margin: 0 .25rem; }
    .user-chip {
      font-size: .82rem; background: rgba(255,255,255,.15);
      padding: .25rem .7rem; border-radius: 20px; white-space: nowrap;
    }
    .btn-logout {
      background: transparent; border: 1px solid rgba(255,255,255,.4); color: #fff;
      padding: .3rem .75rem; border-radius: 6px; cursor: pointer; font-size: .82rem;
      display: flex; align-items: center; gap: .35rem;
    }
    .btn-logout:hover { background: rgba(255,255,255,.12); }
    .btn-nav-ades {
      border: 1px solid rgba(212,160,23,.5); color: #d4a017 !important;
      padding: .3rem .75rem; border-radius: 6px; font-size: .82rem;
      display: inline-flex; align-items: center; gap: .35rem;
      white-space: nowrap;
    }
    .btn-nav-ades:hover { background: rgba(212,160,23,.15); }
    .btn-nav-login {
      border: 1px solid rgba(255,255,255,.5); padding: .3rem .85rem; border-radius: 6px;
    }
    .btn-nav-reg {
      background: #d4a017 !important; color: #1a1a2e !important;
      padding: .3rem .85rem; border-radius: 6px; font-weight: 700;
    }

    .hamburger {
      display: none; flex-direction: column; gap: 5px; background: none; border: none;
      cursor: pointer; padding: .4rem;
    }
    .hamburger span {
      display: block; width: 24px; height: 2px; background: #fff; border-radius: 2px;
    }
    .nav-overlay {
      position: fixed; inset: 0; background: rgba(0,0,0,.5); z-index: 150;
    }

    /* ── Footer ───────────────────────────────────── */
    .nvd-footer {
      background: #1a1a2e;
      color: rgba(255,255,255,.72);
      padding: 3rem 0 0;
      margin-top: 4rem;
      font-size: .88rem;
    }
    .footer-grid {
      display: grid;
      grid-template-columns: 1.4fr 1fr 1fr 1.2fr;
      gap: 2.5rem;
      padding-bottom: 2.5rem;
    }
    .footer-col h4 {
      color: #d4a017; font-family: Jost, sans-serif; font-size: .8rem;
      text-transform: uppercase; letter-spacing: .1em;
      margin-bottom: .85rem; font-weight: 700;
    }
    .footer-col ul { list-style: none; display: flex; flex-direction: column; gap: .55rem; }
    .footer-col li { display: flex; align-items: flex-start; gap: .5rem; line-height: 1.4; }
    .footer-col li i { color: #d4a017; font-size: .85rem; margin-top: 2px; flex-shrink: 0; }
    .footer-col a { color: rgba(255,255,255,.65); text-decoration: none; transition: color .15s; }
    .footer-col a:hover { color: #d4a017; }

    .footer-brand {
      display: flex; align-items: center; gap: .6rem;
      margin-bottom: .75rem;
    }
    .footer-logo-pill {
      background: rgba(255,255,255,.95);
      border-radius: 6px;
      padding: 4px 10px;
      display: inline-flex;
      align-items: center;
    }
    .footer-logo-img { height: 32px; width: auto; object-fit: contain; display: block; }
    .footer-lema {
      font-size: .82rem; font-style: italic; color: rgba(255,255,255,.55);
      line-height: 1.5; margin-bottom: .75rem;
    }
    .footer-web {
      display: inline-flex; align-items: center; gap: .35rem;
      color: #d4a017 !important; font-size: .82rem;
    }

    .footer-ades-link {
      margin-top: 1.25rem;
      padding-top: 1rem;
      border-top: 1px solid rgba(255,255,255,.1);
    }
    .btn-ades-interno {
      display: inline-flex; align-items: center; gap: .5rem;
      background: rgba(212,160,23,.15); border: 1px solid rgba(212,160,23,.4);
      color: #d4a017 !important; padding: .45rem .9rem; border-radius: 8px;
      font-size: .82rem; font-weight: 600; text-decoration: none !important;
      transition: background .15s;
    }
    .btn-ades-interno:hover { background: rgba(212,160,23,.28); }

    .footer-bottom {
      border-top: 1px solid rgba(255,255,255,.1);
      padding: 1rem 2rem;
      display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: .5rem;
      font-size: .78rem; color: rgba(255,255,255,.4);
    }

    /* ── Responsive ───────────────────────────────── */
    @media (max-width: 900px) {
      .footer-grid { grid-template-columns: 1fr 1fr; }
    }
    @media (max-width: 640px) {
      .hamburger { display: flex; }
      .nav-links {
        display: none; flex-direction: column; align-items: flex-start;
        position: fixed; top: 0; right: 0; bottom: 0; width: 280px;
        background: #6b0000; padding: 4.5rem 1.5rem 2rem; z-index: 200;
        overflow-y: auto; gap: .25rem;
      }
      .nav-links.open { display: flex; }
      .nav-links a { font-size: 1rem; padding: .5rem 0; width: 100%; }
      .nav-divider { width: 100%; height: 1px; margin: .5rem 0; }
      .footer-grid { grid-template-columns: 1fr; gap: 1.5rem; }
      .footer-bottom { flex-direction: column; text-align: center; padding: 1rem; }
      .brand-sub { display: none; }
    }
  `],
})
export class AppComponent {
  readonly auth = inject(PortalAuthService);
  readonly year = new Date().getFullYear();
  scrolled   = signal(false);
  menuAbierto = signal(false);

  primerNombre() {
    return this.auth.nombreCompleto().split(' ')[0];
  }

  @HostListener('window:scroll')
  onScroll() { this.scrolled.set(window.scrollY > 30); }
}
