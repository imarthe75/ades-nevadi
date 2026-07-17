import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-aviso-privacidad',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="container" style="padding:2.5rem 1.5rem;max-width:760px;margin:0 auto">
      <div style="margin-bottom:1.5rem">
        <a routerLink="/" class="back-link"><i class="pi pi-arrow-left"></i> Volver al inicio</a>
      </div>

      <div class="card" style="padding:2rem">
        <h1 style="color:#8B0000;font-size:1.4rem;margin-bottom:.25rem">Aviso de Privacidad</h1>
        <p style="color:#6c757d;font-size:.85rem;margin-bottom:2rem">Versión 1.1 — Actualizado: julio 2026</p>

        <section>
          <h2>Identidad del Responsable</h2>
          <p><strong>Educación para Ser, Toluca, A.C.</strong> ("el Instituto"), con domicilio en
          Campos Elíseos #400, piso 10, Colonia Polanco IV Sección, Delegación Miguel Hidalgo,
          Ciudad de México, C.P. 11000, es el responsable del tratamiento de sus datos personales conforme a la
          <em>Ley Federal de Protección de Datos Personales en Posesión de los Particulares</em> (LFPDPPP).</p>
        </section>

        <section>
          <h2>Datos Personales Recabados</h2>
          <p>A través del Portal Público de Convocatorias recabamos los siguientes datos personales:</p>
          <ul>
            <li><strong>Identificación:</strong> nombre completo, correo electrónico, teléfono.</li>
            <li><strong>Documentos:</strong> credenciales, constancias, fotografías y demás documentos que usted suba voluntariamente durante el proceso de postulación.</li>
            <li><strong>Técnicos:</strong> dirección IP, agente de navegador, fecha y hora de acceso (registros del sistema).</li>
          </ul>
          <p>Los datos de salud, datos financieros u otras categorías sensibles <strong>no</strong> son recabados por este Portal salvo que usted los incluya voluntariamente en documentos adjuntos.</p>
        </section>

        <section>
          <h2>Finalidades del Tratamiento</h2>
          <p><strong>Finalidades primarias</strong> (necesarias para la relación con el Instituto):</p>
          <ul>
            <li>Gestionar su proceso de inscripción, reinscripción o postulación a vacantes.</li>
            <li>Comunicarle el resultado de su solicitud y próximos pasos.</li>
            <li>Cumplir obligaciones legales ante las autoridades educativas (SEP, UAEMEX).</li>
          </ul>
          <p><strong>Finalidades secundarias</strong> (puede oponerse sin afectar su proceso):</p>
          <ul>
            <li>Enviarte comunicaciones sobre actividades o eventos del Instituto.</li>
            <li>Análisis estadísticos internos de manera anónima y agregada.</li>
          </ul>
        </section>

        <section>
          <h2>Transferencias de Datos</h2>
          <p>Sus datos podrán ser compartidos únicamente con:</p>
          <ul>
            <li>Autoridades educativas federales o estatales cuando así lo exija la ley.</li>
            <li>Proveedores de servicios tecnológicos (almacenamiento en servidores propios ubicados en México).</li>
          </ul>
          <p>El Instituto <strong>no vende, arrienda ni cede</strong> sus datos a terceros con fines comerciales.</p>
        </section>

        <section>
          <h2>Derechos ARCO</h2>
          <p>Usted tiene derecho a <strong>Acceder, Rectificar, Cancelar u Oponerse</strong> al tratamiento
          de sus datos personales en cualquier momento. Para ejercerlos:</p>
          <ul>
            <li>Complete el <a routerLink="/arco">formulario de solicitud ARCO</a> en este portal.</li>
            <li>Envíe su solicitud a <a href="mailto:administracion@institutonevadi.org.mx">administracion@institutonevadi.org.mx</a>.</li>
          </ul>
          <p>Responderemos en un plazo máximo de <strong>15 días hábiles</strong> contados a partir de la recepción de su solicitud.</p>
        </section>

        <section>
          <h2>Uso de Cookies y Tecnologías Similares</h2>
          <p>Este portal utiliza almacenamiento local del navegador (<em>localStorage</em>) exclusivamente para
          mantener su sesión activa durante la visita. No utilizamos cookies de rastreo ni publicidad de terceros.</p>
        </section>

        <section>
          <h2>Seguridad de la Información</h2>
          <p>Los documentos que usted suba son almacenados en servidores bajo control exclusivo del Instituto,
          cifrados en reposo, y únicamente accesibles mediante enlaces temporales con vigencia de 15 minutos.
          Las contraseñas se guardan usando funciones de hashing seguro (bcrypt).</p>
        </section>

        <section>
          <h2>Cambios al Aviso de Privacidad</h2>
          <p>Cualquier modificación a este aviso se publicará en este portal con al menos 15 días de anticipación.
          El uso continuo del portal después de la publicación de cambios constituirá su aceptación.</p>
        </section>

        <section>
          <h2>Contacto</h2>
          <p>Para ejercer sus derechos ARCO o cualquier duda sobre el tratamiento de sus datos:</p>
          <p>Email: <a href="mailto:administracion@institutonevadi.org.mx">administracion@institutonevadi.org.mx</a></p>
          <p>Educación para Ser, Toluca, A.C. — Ciudad de México, México</p>
        </section>
      </div>
    </div>
  `,
  styles: [`
    .back-link { color: #8B0000; font-size: .88rem; display: flex; align-items: center; gap: .4rem; }
    section { margin-bottom: 1.75rem; }
    h2 { font-size: 1.05rem; color: #1a1a2e; margin-bottom: .5rem; padding-bottom: .4rem; border-bottom: 1px solid #dee2e6; }
    p { font-size: .9rem; line-height: 1.6; color: #444; margin: .5rem 0; }
    ul { padding-left: 1.25rem; margin: .5rem 0; }
    li { font-size: .9rem; line-height: 1.6; color: #444; margin-bottom: .25rem; }
    a { color: #8B0000; }
    em { color: #495057; }
  `],
})
export class AvisoPrivacidadComponent {}
