package mx.ades.modules.portal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Servicio de email para el portal de convocatorias.
 * Actualmente registra los mensajes en log (modo development).
 * Para producción: configurar spring.mail.* y descomentar JavaMailSender.
 */
@Service
@Slf4j
public class PortalEmailService {

    @Value("${portal.base-url:https://portalnvd.setag.mx}")
    private String baseUrl;

    @Value("${portal.arco-email:privacidad@nevadi.edu.mx}")
    private String arcoEmail;

    public void enviarVerificacion(String destinatario, String nombre, String token) {
        String link = baseUrl + "/verificar-email/" + token;
        log.info("[PORTAL EMAIL] Verificación → {} | Link: {}", destinatario, link);
        // TODO producción: enviar via JavaMailSender
        // Subject: "Verifica tu correo — Portal Nevadi"
        // Body: "Hola {nombre}, haz clic en {link} para confirmar tu cuenta."
    }

    public void enviarConfirmacionPostulacion(String destinatario, String nombre, String folio, String tituloConvocatoria) {
        log.info("[PORTAL EMAIL] Confirmación postulación {} → {} | Convocatoria: {}", folio, destinatario, tituloConvocatoria);
        // Subject: "Postulación recibida — {folio}"
        // Body: Incluir folio para seguimiento, link a {baseUrl}/seguimiento/{folio}, aviso privacidad
    }

    public void enviarCambioEstado(String destinatario, String nombre, String folio, String estado, String observaciones) {
        log.info("[PORTAL EMAIL] Cambio estado postulación {} → {} | Nuevo estado: {}", folio, destinatario, estado);
        // Subject: "Actualización de tu postulación {folio}"
    }

    public void enviarAcuseArco(String destinatario, String nombre, String tipo, String folio) {
        log.info("[PORTAL EMAIL] Acuse ARCO {} → {} | Tipo: {}", folio, destinatario, tipo);
        // Subject: "Solicitud ARCO recibida — {folio}"
        // Body: Informar plazo de respuesta 20 días hábiles, datos de contacto {arcoEmail}
    }

    public void enviarRecuperacionClave(String destinatario, String token) {
        String link = baseUrl + "/nueva-clave/" + token;
        log.info("[PORTAL EMAIL] Recuperación clave → {} | Link: {}", destinatario, link);
        // Subject: "Recuperar contraseña — Portal Nevadi"
    }
}
