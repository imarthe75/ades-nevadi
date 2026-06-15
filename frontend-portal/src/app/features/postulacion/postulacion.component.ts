import { Component, OnInit, signal, inject, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ProgressBarModule } from 'primeng/progressbar';
import { FileUploadModule } from 'primeng/fileupload';
import { TooltipModule } from 'primeng/tooltip';
import { PortalApiService } from '../../core/services/portal-api.service';

const ESTADO_LABEL: Record<string, string> = {
  BORRADOR: 'Borrador', ENVIADA: 'Enviada', EN_REVISION: 'En Revisión',
  PRESELECCIONADA: 'Preseleccionada', ACEPTADA: 'Aceptada',
  RECHAZADA: 'Rechazada', LISTA_ESPERA: 'Lista de Espera',
};

@Component({
  selector: 'app-postulacion',
  standalone: true,
  imports: [CommonModule, RouterLink, ButtonModule, ProgressSpinnerModule, ProgressBarModule, FileUploadModule, TooltipModule],
  template: `
    <div class="container" style="padding:2rem 1.5rem;max-width:820px">
      <a routerLink="/mis-postulaciones" class="back-link"><i class="pi pi-arrow-left"></i> Mis postulaciones</a>

      @if (loading()) {
        <div style="text-align:center;padding:4rem"><p-progressSpinner /></div>
      } @else if (post()) {
        <!-- Cabecera -->
        <div class="card" style="margin-top:1rem">
          <div style="display:flex;justify-content:space-between;align-items:flex-start;flex-wrap:wrap;gap:.5rem">
            <div>
              <span class="badge" [class]="'estado-' + post()['estado']">{{ estadoLabel(post()['estado']) }}</span>
              <h2 style="margin:.5rem 0 .25rem;font-size:1.2rem">{{ post()['convocatoria_titulo'] }}</h2>
              <p style="font-size:.82rem;color:#6c757d;font-family:monospace">Folio: {{ post()['folio'] }}</p>
            </div>
            @if (post()['estado'] === 'BORRADOR') {
              <p-button label="Enviar postulación" icon="pi pi-send"
                [style]="{'background':'#8B0000','border-color':'#8B0000'}"
                [loading]="enviando()" (onClick)="enviar()"
                pTooltip="Envía tu postulación cuando hayas subido todos los documentos" />
            }
          </div>

          <!-- Stepper de estado -->
          <div class="stepper" style="margin-top:1.25rem">
            @for (paso of pasos; track paso.clave) {
              <div class="paso" [class.activo]="post()['estado'] === paso.clave"
                   [class.completado]="pasoCompletado(paso.clave)">
                <div class="paso-dot"><i [class]="'pi ' + paso.icono"></i></div>
                <div class="paso-label">{{ paso.label }}</div>
              </div>
            }
          </div>

          @if (post()['estado'] !== 'BORRADOR') {
            <div class="info-enviada">
              <i class="pi pi-info-circle"></i>
              Tu postulación fue enviada. Recibirás notificación cuando cambie su estado.
            </div>
          }
        </div>

        <!-- Documentos -->
        <div class="card" style="margin-top:1rem">
          <h3 style="margin-bottom:1rem">Documentos</h3>

          <!-- Requisitos con estado -->
          @if (post()['requisitos']?.length) {
            <div class="req-list">
              @for (r of post()['requisitos']; track r['id']) {
                <div class="req-item" [class.cumplido]="r['cumplido']">
                  <i class="pi" [class]="r['cumplido'] ? 'pi-check-circle' : (r['es_obligatorio'] ? 'pi-times-circle' : 'pi-circle')"></i>
                  <div class="req-info">
                    <strong>{{ r['nombre'] }}</strong>
                    @if (r['es_obligatorio'] && !r['cumplido']) { <span class="obl">Obligatorio</span> }
                    @if (r['descripcion']) { <br><small>{{ r['descripcion'] }}</small> }
                  </div>
                  @if (!r['cumplido'] && post()['estado'] === 'BORRADOR') {
                    <label class="upload-btn" [for]="'upload-' + r['id']">
                      <i class="pi pi-upload"></i> Subir
                      <input type="file" [id]="'upload-' + r['id']" hidden
                        accept=".pdf,.jpg,.jpeg,.png,.doc,.docx"
                        (change)="subirArchivo($event, r['nombre'], r['id'])" />
                    </label>
                  }
                </div>
              }
            </div>
          }

          <!-- Documentos subidos -->
          @if (post()['documentos']?.length) {
            <h4 style="margin:1.25rem 0 .75rem;font-size:.9rem">Archivos subidos</h4>
            <div class="doc-list">
              @for (d of post()['documentos']; track d['id']) {
                <div class="doc-item">
                  <i class="pi pi-file-pdf" style="color:#dc3545"></i>
                  <div class="doc-info">
                    <strong>{{ d['nombre_original'] }}</strong>
                    @if (d['requisito_nombre']) { <span class="req-tag">{{ d['requisito_nombre'] }}</span> }
                    <br><small>{{ formatSize(d['tamano_bytes']) }}</small>
                  </div>
                  <div class="doc-actions">
                    <p-button icon="pi pi-download" severity="secondary" [text]="true" size="small"
                      pTooltip="Descargar" (onClick)="descargar(d)" />
                    @if (post()['estado'] === 'BORRADOR') {
                      <p-button icon="pi pi-trash" severity="danger" [text]="true" size="small"
                        pTooltip="Eliminar" (onClick)="eliminarDoc(d)" />
                    }
                  </div>
                </div>
              }
            </div>
          }

          <!-- Subir doc adicional -->
          @if (post()['estado'] === 'BORRADOR') {
            <div class="upload-extra">
              <label class="upload-btn" for="upload-libre">
                <i class="pi pi-plus"></i> Agregar documento adicional
                <input type="file" id="upload-libre" hidden accept=".pdf,.jpg,.jpeg,.png,.doc,.docx"
                  (change)="subirArchivo($event, 'DOCUMENTO_ADICIONAL', null)" />
              </label>
              <p style="font-size:.78rem;color:#6c757d;margin-top:.4rem">PDF, JPG, PNG, DOC, DOCX — máx. 10 MB por archivo</p>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .back-link { color: #8B0000; font-size: .88rem; display: flex; align-items: center; gap: .4rem; }
    .badge { display: inline-block; padding: .2rem .65rem; border-radius: 20px; font-size: .78rem; font-weight: 700; }
    .estado-BORRADOR        { background: #e9ecef; color: #495057; }
    .estado-ENVIADA         { background: #cfe2ff; color: #084298; }
    .estado-EN_REVISION     { background: #fff3cd; color: #664d03; }
    .estado-PRESELECCIONADA { background: #d1e7dd; color: #0a3622; }
    .estado-ACEPTADA        { background: #198754; color: #fff; }
    .estado-RECHAZADA       { background: #f8d7da; color: #842029; }
    .estado-LISTA_ESPERA    { background: #e2d9f3; color: #41237a; }
    .stepper { display: flex; gap: 0; overflow-x: auto; padding: .5rem 0; }
    .paso { display: flex; flex-direction: column; align-items: center; gap: .35rem; flex: 1; min-width: 80px; position: relative; }
    .paso:not(:last-child)::after { content: ''; position: absolute; top: 14px; left: 60%; width: 80%; height: 2px; background: #dee2e6; }
    .paso.completado:not(:last-child)::after { background: #198754; }
    .paso-dot { width: 28px; height: 28px; border-radius: 50%; background: #dee2e6; display: flex; align-items: center; justify-content: center; font-size: .75rem; z-index: 1; }
    .paso.activo .paso-dot { background: #8B0000; color: #fff; }
    .paso.completado .paso-dot { background: #198754; color: #fff; }
    .paso-label { font-size: .72rem; text-align: center; color: #6c757d; }
    .paso.activo .paso-label { color: #8B0000; font-weight: 600; }
    .info-enviada { display: flex; gap: .5rem; align-items: center; background: #cfe2ff; padding: .75rem; border-radius: 8px; font-size: .85rem; margin-top: 1rem; color: #084298; }
    .req-list { display: flex; flex-direction: column; gap: .6rem; }
    .req-item { display: flex; align-items: center; gap: .75rem; padding: .6rem .75rem; background: #f8f9fa; border-radius: 8px; font-size: .88rem; }
    .req-item i { font-size: 1.1rem; color: #6c757d; flex-shrink: 0; }
    .req-item.cumplido i { color: #198754; }
    .req-item:not(.cumplido) i.pi-times-circle { color: #dc3545; }
    .req-info { flex: 1; }
    .obl { background: #f8d7da; color: #842029; font-size: .72rem; padding: .1rem .4rem; border-radius: 4px; margin-left: .4rem; }
    .upload-btn { display: inline-flex; align-items: center; gap: .35rem; padding: .3rem .8rem; background: #8B0000; color: #fff; border-radius: 6px; font-size: .82rem; cursor: pointer; white-space: nowrap; }
    .upload-extra { margin-top: 1rem; }
    .doc-list { display: flex; flex-direction: column; gap: .5rem; }
    .doc-item { display: flex; align-items: center; gap: .75rem; padding: .6rem .75rem; border: 1px solid #dee2e6; border-radius: 8px; font-size: .88rem; }
    .doc-info { flex: 1; }
    .req-tag { background: #e2d9f3; color: #41237a; font-size: .72rem; padding: .1rem .4rem; border-radius: 4px; margin-left: .4rem; }
    .doc-actions { display: flex; gap: .25rem; }
  `],
})
export class PostulacionComponent implements OnInit {
  @Input() id!: string;

  private readonly api    = inject(PortalApiService);
  private readonly router = inject(Router);

  post     = signal<any | null>(null);
  loading  = signal(true);
  enviando = signal(false);

  readonly pasos = [
    { clave: 'BORRADOR',        label: 'Borrador',       icono: 'pi-pencil' },
    { clave: 'ENVIADA',         label: 'Enviada',        icono: 'pi-send' },
    { clave: 'EN_REVISION',     label: 'En revisión',    icono: 'pi-eye' },
    { clave: 'PRESELECCIONADA', label: 'Preseleccionada', icono: 'pi-star' },
    { clave: 'ACEPTADA',        label: 'Aceptada',       icono: 'pi-check' },
  ];
  private readonly ordenPasos = this.pasos.map(p => p.clave);

  ngOnInit() { this.cargar(); }

  cargar() {
    this.api.get<any>(`/usuario/postulaciones/${this.id}`).subscribe({
      next: d => { this.post.set(d); this.loading.set(false); },
      error: () => { this.loading.set(false); this.router.navigate(['/mis-postulaciones']); },
    });
  }

  enviar() {
    if (!confirm('¿Confirmas el envío de tu postulación? Una vez enviada no podrás agregar ni eliminar documentos.')) return;
    this.enviando.set(true);
    this.api.post<any>(`/usuario/postulaciones/${this.id}/enviar`, {}).subscribe({
      next: () => { this.enviando.set(false); this.cargar(); },
      error: (e: any) => {
        this.enviando.set(false);
        alert(e?.error?.mensaje ?? e?.error?.message ?? 'Error al enviar postulación');
      },
    });
  }

  subirArchivo(event: Event, tipo: string, requisitoId: string | null) {
    const input = event.target as HTMLInputElement;
    const file  = input.files?.[0];
    if (!file) return;
    const fd = new FormData();
    fd.append('archivo', file);
    fd.append('tipo_documento', tipo);
    if (requisitoId) fd.append('requisito_id', requisitoId);
    this.api.uploadFile(`/usuario/postulaciones/${this.id}/documentos`, fd).subscribe({
      next: () => this.cargar(),
      error: (e: any) => alert(e?.error?.mensaje ?? e?.error?.message ?? 'Error al subir el archivo'),
    });
    input.value = '';
  }

  eliminarDoc(doc: any) {
    if (!confirm(`¿Eliminar "${doc['nombre_original']}"?`)) return;
    this.api.delete(`/usuario/postulaciones/${this.id}/documentos/${doc['id']}`).subscribe({
      next: () => this.cargar(),
      error: (e: any) => alert(e?.error?.mensaje ?? 'Error al eliminar el documento'),
    });
  }

  descargar(doc: any) {
    this.api.get<any>(`/usuario/postulaciones/${this.id}/documentos/${doc['id']}/url`).subscribe({
      next: r => window.open(r['url'], '_blank'),
      error: () => alert('No se pudo generar el enlace de descarga'),
    });
  }

  pasoCompletado(clave: string) {
    const estadoActual = this.post()?.['estado'];
    if (!estadoActual) return false;
    const idx = this.ordenPasos.indexOf(estadoActual);
    const idxPaso = this.ordenPasos.indexOf(clave);
    return idxPaso < idx;
  }

  estadoLabel(e: string) { return ESTADO_LABEL[e] ?? e; }
  formatSize(bytes: number) {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }
}
