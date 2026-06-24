import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { TagModule } from 'primeng/tag';

interface VerificacionResult {
  folio: string;
  tipo_certificado: string;
  nombre_alumno: string;
  alumno_curp: string;
  plantel: string;
  ciclo: string;
  nivel_educativo: string;
  grado_completado: string | null;
  promedio_final: number | null;
  fecha_emision: string;
  vigente: boolean;
  estado_firma: string;
  fecha_firma: string | null;
  autenticidad: 'VERIFICADO' | 'EMITIDO' | 'INVALIDO' | 'REVOCADO';
  mensaje: string;
  firma_valida: boolean | null;
  blockchain_tx: string | null;
  blockchain_status: string | null;
  fecha_anclaje: string | null;
  blockchain_network: string | null;
}

/**
 * Página pública de verificación de certificados digitales por folio.
 * No requiere autenticación. Valida la firma Ed25519 del certificado y
 * muestra los datos del documento al ciudadano o institución verificadora.
 */
@Component({
  selector: 'app-verificar',
  standalone: true,
  imports: [CommonModule, RouterModule, ProgressSpinnerModule, TagModule],
  template: `
    <div class="verif-bg">
      <div class="verif-card">

        <!-- Header -->
        <div class="verif-header">
          <div class="verif-logo">N</div>
          <div>
            <div class="verif-inst">Instituto Nevadi</div>
            <div class="verif-sub">Verificación de Certificados Digitales</div>
          </div>
        </div>

        @if (cargando()) {
          <div class="verif-loading">
            <p-progressSpinner strokeWidth="4" />
            <p>Verificando certificado…</p>
          </div>
        } @else if (error()) {
          <div class="verif-error">
            <i class="pi pi-times-circle verif-icon verif-icon-error"></i>
            <h3>Certificado no encontrado</h3>
            <p>El folio <strong>{{ folioParam }}</strong> no corresponde a ningún certificado
              registrado en Instituto Nevadi.</p>
          </div>
        } @else if (result()) {
          <!-- Badge de autenticidad -->
          <div class="verif-badge-row">
            @switch (result()!.autenticidad) {
              @case ('VERIFICADO') {
                <div class="verif-badge verif-ok">
                  <i class="pi pi-shield-check"></i>
                  <span>CERTIFICADO AUTÉNTICO</span>
                </div>
              }
              @case ('EMITIDO') {
                <div class="verif-badge verif-info">
                  <i class="pi pi-info-circle"></i>
                  <span>EMITIDO POR INSTITUTO NEVADI</span>
                </div>
              }
              @case ('INVALIDO') {
                <div class="verif-badge verif-danger">
                  <i class="pi pi-times-circle"></i>
                  <span>FIRMA INVÁLIDA</span>
                </div>
              }
              @case ('REVOCADO') {
                <div class="verif-badge verif-danger">
                  <i class="pi pi-ban"></i>
                  <span>CERTIFICADO REVOCADO</span>
                </div>
              }
            }
          </div>

          <p class="verif-mensaje">{{ result()!.mensaje }}</p>

          <!-- Datos del certificado -->
          <div class="verif-datos">
            <div class="verif-section-title">Datos del certificado</div>
            <table class="verif-table">
              <tr>
                <th>Titular:</th>
                <td><strong>{{ result()!.nombre_alumno }}</strong></td>
              </tr>
              <tr>
                <th>CURP:</th>
                <td>{{ result()!.alumno_curp || '—' }}</td>
              </tr>
              <tr>
                <th>Tipo:</th>
                <td>{{ tipoLabel(result()!.tipo_certificado) }}</td>
              </tr>
              <tr>
                <th>Nivel educativo:</th>
                <td>{{ result()!.nivel_educativo }}</td>
              </tr>
              @if (result()!.grado_completado) {
                <tr>
                  <th>Grado completado:</th>
                  <td>{{ result()!.grado_completado }}</td>
                </tr>
              }
              @if (result()!.promedio_final) {
                <tr>
                  <th>Promedio final:</th>
                  <td><strong style="color:#D02030">{{ result()!.promedio_final }}</strong></td>
                </tr>
              }
              <tr>
                <th>Plantel:</th>
                <td>{{ result()!.plantel }}</td>
              </tr>
              <tr>
                <th>Ciclo escolar:</th>
                <td>{{ result()!.ciclo }}</td>
              </tr>
              <tr>
                <th>Fecha de emisión:</th>
                <td>{{ result()!.fecha_emision | date:'dd/MM/yyyy' }}</td>
              </tr>
              <tr>
                <th>Vigente:</th>
                <td>{{ result()!.vigente ? 'Sí' : 'No' }}</td>
              </tr>
              <tr>
                <th>Folio:</th>
                <td><code>{{ result()!.folio }}</code></td>
              </tr>
            </table>
          </div>

          <!-- Firma digital -->
          <div class="verif-firma-block">
            <div class="verif-section-title">Firma digital</div>
            <div class="verif-firma-row">
              @if (result()!.firma_valida === true) {
                <i class="pi pi-check-circle" style="color:#22c55e;font-size:1.5rem"></i>
                <div>
                  <div class="verif-firma-label">Firma Ed25519 válida</div>
                  @if (result()!.fecha_firma) {
                    <div class="verif-firma-meta">Firmado el {{ result()!.fecha_firma | date:'dd/MM/yyyy HH:mm' }}</div>
                  }
                </div>
              } @else if (result()!.firma_valida === false) {
                <i class="pi pi-times-circle" style="color:#ef4444;font-size:1.5rem"></i>
                <div>
                  <div class="verif-firma-label" style="color:#ef4444">Firma inválida — documento posiblemente alterado</div>
                </div>
              } @else {
                <i class="pi pi-clock" style="color:#f59e0b;font-size:1.5rem"></i>
                <div>
                  <div class="verif-firma-label">Sin firma criptográfica</div>
                  <div class="verif-firma-meta">Certificado emitido pero no firmado digitalmente</div>
                </div>
              }
            </div>
          </div>

          <!-- Anclaje Blockchain -->
          <div class="verif-firma-block" style="border-left: 4px solid #16a34a">
            <div class="verif-section-title">Anclaje en Blockchain (Polygon PoS)</div>
            <div class="verif-firma-row">
              @if (result()!.blockchain_status === 'ANCLADO') {
                <i class="pi pi-link" style="color:#16a34a;font-size:1.5rem"></i>
                <div>
                  <div class="verif-firma-label" style="color:#16a34a">Hash anclado de forma inmutable</div>
                  <div class="verif-firma-meta">
                    Red: {{ result()!.blockchain_network }} <br>
                    Fecha: {{ result()!.fecha_anclaje | date:'dd/MM/yyyy HH:mm' }} <br>
                    Transacción: 
                    <a [href]="blockchainLink(result()!.blockchain_tx, result()!.blockchain_network)" target="_blank" style="color:#D02030;text-decoration:none;word-break:break-all;font-family:monospace">
                      {{ result()!.blockchain_tx }}
                    </a>
                  </div>
                </div>
              } @else if (result()!.blockchain_status === 'PENDIENTE') {
                <i class="pi pi-spin pi-spinner" style="color:#d97706;font-size:1.5rem"></i>
                <div>
                  <div class="verif-firma-label" style="color:#d97706">Registro en blockchain pendiente</div>
                  <div class="verif-firma-meta">La transacción se está procesando en segundo plano.</div>
                </div>
              } @else {
                <i class="pi pi-info-circle" style="color:#64748b;font-size:1.5rem"></i>
                <div>
                  <div class="verif-firma-label" style="color:#64748b">Sin anclaje en Blockchain</div>
                  <div class="verif-firma-meta">Este certificado aún no ha sido registrado en la blockchain.</div>
                </div>
              }
            </div>
          </div>
        }

        <!-- Footer -->
        <div class="verif-footer">
          <a href="https://institutonevadi.edu.mx" target="_blank">institutonevadi.edu.mx</a>
          &nbsp;&bull;&nbsp;
          <a routerLink="/login">Ingresar al sistema</a>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .verif-bg {
      min-height: 100vh; background: #f8f9fa;
      display: flex; align-items: center; justify-content: center;
      font-family: 'Inter', Arial, sans-serif; padding: 2rem;
    }
    .verif-card {
      background: #fff; border-radius: 12px;
      box-shadow: 0 4px 24px rgba(0,0,0,.1);
      max-width: 580px; width: 100%; padding: 2.5rem;
    }
    .verif-header {
      display: flex; align-items: center; gap: 1rem;
      margin-bottom: 2rem; padding-bottom: 1.25rem;
      border-bottom: 2px solid #D02030;
    }
    .verif-logo {
      width: 48px; height: 48px; border-radius: 10px;
      background: #D02030; color: #fff;
      display: flex; align-items: center; justify-content: center;
      font-weight: 800; font-size: 1.4rem; flex-shrink: 0;
    }
    .verif-inst { font-size: 1.2rem; font-weight: 700; color: #D02030; }
    .verif-sub  { font-size: .8rem; color: #888; }

    .verif-loading { text-align: center; padding: 3rem; color: #888; }

    .verif-badge-row { margin-bottom: 1rem; }
    .verif-badge {
      display: inline-flex; align-items: center; gap: .6rem;
      padding: .65rem 1.25rem; border-radius: 8px;
      font-weight: 700; font-size: 1rem; letter-spacing: .5px;
    }
    .verif-badge i { font-size: 1.2rem; }
    .verif-ok     { background: #f0fdf4; color: #166534; border: 2px solid #86efac; }
    .verif-info   { background: #eff6ff; color: #1e40af; border: 2px solid #93c5fd; }
    .verif-danger { background: #fef2f2; color: #991b1b; border: 2px solid #fca5a5; }

    .verif-mensaje { font-size: .88rem; color: #555; margin-bottom: 1.5rem; }

    .verif-section-title { font-size: .7rem; font-weight: 700; text-transform: uppercase;
      letter-spacing: .1em; color: #888; margin-bottom: .6rem; }

    .verif-datos  { margin-bottom: 1.5rem; }
    .verif-table  { width: 100%; border-collapse: collapse; font-size: .87rem; }
    .verif-table tr { border-bottom: 1px solid #f1f5f9; }
    .verif-table tr:last-child { border-bottom: none; }
    .verif-table th { text-align: left; padding: .4rem .5rem; color: #888; font-weight: 500; width: 45%; }
    .verif-table td { padding: .4rem .5rem; color: #1a1a1a; }
    .verif-table code { font-size: .78rem; background: #f1f5f9; padding: .1rem .4rem; border-radius: 3px; }

    .verif-firma-block { background: #f8fafc; border-radius: 8px; padding: 1rem; margin-bottom: 1.5rem; }
    .verif-firma-row { display: flex; align-items: flex-start; gap: .75rem; }
    .verif-firma-label { font-size: .88rem; font-weight: 600; }
    .verif-firma-meta  { font-size: .78rem; color: #888; }

    .verif-error { text-align: center; padding: 2rem; }
    .verif-icon  { font-size: 3rem; margin-bottom: .75rem; display: block; }
    .verif-icon-error { color: #ef4444; }
    .verif-error h3 { margin-bottom: .5rem; }
    .verif-error p  { color: #888; font-size: .88rem; }

    .verif-footer { text-align: center; font-size: .78rem; color: #aaa; padding-top: 1rem;
      border-top: 1px solid #f1f5f9; }
    .verif-footer a { color: #D02030; text-decoration: none; }
    .verif-footer a:hover { text-decoration: underline; }
  `],
})
export class VerificarComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api  = inject(ApiService);

  result   = signal<VerificacionResult | null>(null);
  cargando = signal(true);
  error    = signal(false);
  folioParam = '';

  ngOnInit(): void {
    this.folioParam = this.route.snapshot.paramMap.get('folio') ?? '';
    if (!this.folioParam) { this.cargando.set(false); this.error.set(true); return; }

    this.api.get<VerificacionResult>(
      `/certificados/verificar/${this.folioParam.toUpperCase()}`
    ).subscribe({
      next: (r) => { this.result.set(r); this.cargando.set(false); },
      error: () => { this.error.set(true); this.cargando.set(false); },
    });
  }

  tipoLabel(tipo: string): string {
    const m: Record<string, string> = {
      ESTUDIOS: 'Certificado de Estudios',
      CONDUCTA: 'Certificado de Buena Conducta',
      PARTICIPACION: 'Certificado de Participación',
      MERITO_ACADEMICO: 'Certificado de Mérito Académico',
      ASISTENCIA_PERFECTA: 'Certificado de Asistencia Perfecta',
    };
    return m[tipo] ?? tipo;
  }

  blockchainLink(tx: string | null, network: string | null): string {
    if (!tx) return '#';
    if (network === 'MOCK') {
      return `/verificar/mock-tx/${tx}`;
    }
    return `https://amoy.polygonscan.com/tx/${tx}`;
  }
}
