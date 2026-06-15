import { Component, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { RouterLink } from '@angular/router';
import { PortalApiService } from '../../core/services/portal-api.service';

const TIPOS = [
  { label: 'Acceso — Obtener copia de mis datos', value: 'ACCESO' },
  { label: 'Rectificación — Corregir mis datos', value: 'RECTIFICACION' },
  { label: 'Cancelación — Eliminar mis datos', value: 'CANCELACION' },
  { label: 'Oposición — Oponerme al tratamiento', value: 'OPOSICION' },
];

@Component({
  selector: 'app-arco',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, InputTextModule, TextareaModule, SelectModule, ButtonModule, CheckboxModule],
  template: `
    <div class="container" style="padding:2.5rem 1.5rem;max-width:680px;margin:0 auto">
      <h2 style="color:#8B0000">Derechos ARCO</h2>
      <p style="color:#6c757d;font-size:.9rem;margin-bottom:.5rem">
        Conforme a la <strong>Ley Federal de Protección de Datos Personales en Posesión de los Particulares (LFPDPPP)</strong>,
        tienes derecho a Acceder, Rectificar, Cancelar u Oponerte al tratamiento de tus datos personales.
      </p>
      <p style="color:#6c757d;font-size:.88rem;margin-bottom:2rem">
        Responderemos dentro de los <strong>20 días hábiles</strong> siguientes a la recepción de tu solicitud.
        Para más información consulta nuestro <a routerLink="/aviso-privacidad" style="color:#8B0000">Aviso de Privacidad</a>.
      </p>

      @if (exito()) {
        <div class="alert-success">
          <i class="pi pi-check-circle" style="font-size:1.5rem;color:#198754;flex-shrink:0"></i>
          <div>
            <strong>Solicitud recibida</strong>
            <p>Tu solicitud ARCO ha sido registrada. Recibirás respuesta en el correo indicado en un plazo máximo de 20 días hábiles.</p>
            <p>Número de folio: <strong style="font-family:monospace">{{ folioRespuesta() }}</strong></p>
          </div>
        </div>
      } @else {
        @if (error()) {
          <div class="alert-error"><i class="pi pi-times-circle"></i> {{ error() }}</div>
        }

        <div class="card" style="padding:1.5rem">
          <div class="form-group">
            <label class="required">Tipo de derecho</label>
            <p-select [options]="tipos" [(ngModel)]="form.tipo" optionLabel="label" optionValue="value"
              placeholder="Selecciona el derecho a ejercer" styleClass="w-full" />
          </div>
          <div class="form-group">
            <label class="required">Nombre completo</label>
            <input pInputText [(ngModel)]="form.nombre" placeholder="Nombre y apellidos" class="w-full" />
          </div>
          <div class="form-group">
            <label class="required">Correo electrónico</label>
            <input pInputText type="email" [(ngModel)]="form.email" placeholder="correo@ejemplo.com" class="w-full" />
          </div>
          <div class="form-group">
            <label>Folio de postulación (si aplica)</label>
            <input pInputText [(ngModel)]="form.folioPostulacion" placeholder="NVD-INSC-2026-00001"
              style="font-family:monospace" class="w-full" />
          </div>
          <div class="form-group">
            <label class="required">Descripción de la solicitud</label>
            <textarea pTextarea [(ngModel)]="form.descripcion" rows="5" class="w-full"
              placeholder="Describe de forma clara qué datos deseas acceder, rectificar, cancelar u oponerte a su tratamiento."></textarea>
          </div>

          <div class="privacidad-check">
            <p-checkbox [(ngModel)]="form.consentimiento" [binary]="true" inputId="arco-priv" />
            <label for="arco-priv">
              Confirmo que la información proporcionada es correcta y acepto el
              <a routerLink="/aviso-privacidad">Aviso de Privacidad</a>.
            </label>
          </div>

          <p-button label="Enviar solicitud ARCO" icon="pi pi-shield"
            [style]="{'background':'#8B0000','border-color':'#8B0000'}"
            [loading]="cargando()" [disabled]="!form.consentimiento"
            styleClass="w-full" (onClick)="enviar()" />
        </div>
      }

      <div class="contacto-arco">
        <p>Responsable de datos: <strong>Instituto Nevadi, A.C.</strong></p>
        <p>Correo ARCO: <a href="mailto:privacidad@nevadi.edu.mx" style="color:#8B0000">privacidad@nevadi.edu.mx</a></p>
      </div>
    </div>
  `,
  styles: [`
    .form-group { margin-bottom: 1rem; }
    .form-group label { display: block; font-size: .85rem; font-weight: 600; margin-bottom: .35rem; color: #495057; }
    .required::after { content: ' *'; color: #8B0000; }
    .privacidad-check { display: flex; align-items: flex-start; gap: .6rem; margin: 1rem 0 1.25rem; font-size: .85rem; line-height: 1.4; }
    .privacidad-check label a { color: #8B0000; }
    .alert-success { display: flex; gap: .75rem; background: #d1e7dd; border-radius: 8px; padding: 1.25rem; color: #0a3622; }
    .alert-success strong { display: block; margin-bottom: .25rem; }
    .alert-success p { font-size: .9rem; margin: .25rem 0; }
    .alert-error { background: #f8d7da; color: #842029; border-radius: 8px; padding: .65rem 1rem; font-size: .88rem; display: flex; align-items: center; gap: .5rem; margin-bottom: 1rem; }
    .contacto-arco { background: #f8f9fa; border-radius: 8px; padding: 1rem; margin-top: 1.5rem; font-size: .85rem; color: #6c757d; }
    .contacto-arco p { margin: .2rem 0; }
    :host ::ng-deep .w-full { width: 100%; }
  `],
})
export class ArcoComponent {
  private readonly api = inject(PortalApiService);

  tipos = TIPOS;
  form  = { tipo: '', nombre: '', email: '', folioPostulacion: '', descripcion: '', consentimiento: false };
  cargando       = signal(false);
  exito          = signal(false);
  error          = signal('');
  folioRespuesta = signal('');

  enviar() {
    if (!this.form.tipo || !this.form.nombre || !this.form.email || !this.form.descripcion) {
      this.error.set('Completa todos los campos obligatorios.');
      return;
    }
    this.error.set('');
    this.cargando.set(true);
    this.api.post<any>('/arco', {
      tipo: this.form.tipo,
      nombre_solicitante: this.form.nombre.trim(),
      email_solicitante: this.form.email.trim(),
      folio_postulacion: this.form.folioPostulacion || null,
      descripcion: this.form.descripcion.trim(),
      consentimiento_privacidad: true,
    }).subscribe({
      next: r => {
        this.cargando.set(false);
        this.folioRespuesta.set(r['folio'] ?? '');
        this.exito.set(true);
      },
      error: (e: any) => {
        this.cargando.set(false);
        this.error.set(e?.error?.mensaje ?? e?.error?.message ?? 'Error al enviar la solicitud.');
      },
    });
  }
}
