package mx.ades.modules.direcciones;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class DireccionesWriteService {

    private final JdbcTemplate jdbc;

    public DireccionesWriteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID crearDireccion(DireccionesController.DireccionPayload body, String usuario) {
        if (Boolean.TRUE.equals(body.getEsPrincipal())) {
            jdbc.update("UPDATE ades_direcciones SET es_principal = FALSE " +
                    "WHERE entidad_tipo = ? AND entidad_id = ? AND is_active = TRUE",
                    body.getEntidadTipo(), body.getEntidadId());
        }
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_direcciones " +
                "(id, entidad_tipo, entidad_id, tipo_direccion, es_principal, " +
                " tipo_via, calle, numero_exterior, numero_interior, " +
                " entre_calle_1, entre_calle_2, referencia, " +
                " codigo_postal_id, localidad_id, latitud, longitud, precision_gps, " +
                " usuario_creacion, usuario_modificacion) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id,
                body.getEntidadTipo(), body.getEntidadId(),
                body.getTipoDireccion() != null ? body.getTipoDireccion() : "PRINCIPAL",
                body.getEsPrincipal() != null ? body.getEsPrincipal() : false,
                body.getTipoVia(), body.getCalle(), body.getNumeroExterior(), body.getNumeroInterior(),
                body.getEntreCalles1(), body.getEntreCalles2(), body.getReferencia(),
                body.getCodigoPostalId(), body.getLocalidadId(),
                body.getLatitud(), body.getLongitud(), body.getPrecisionGps(),
                usuario, usuario);
        return id;
    }

    public void actualizarDireccion(UUID id, DireccionesController.DireccionPayload body,
                                     String entidadTipo, UUID entidadId, String usuario) {
        if (Boolean.TRUE.equals(body.getEsPrincipal())) {
            jdbc.update("UPDATE ades_direcciones SET es_principal = FALSE " +
                    "WHERE entidad_tipo = ? AND entidad_id = ? AND is_active = TRUE AND id != ?",
                    entidadTipo, entidadId, id);
        }
        StringBuilder sql = new StringBuilder(
                "UPDATE ades_direcciones SET row_version = row_version + 1, usuario_modificacion = ?");
        List<Object> params = new ArrayList<>();
        params.add(usuario);

        addStr(sql, params, "tipo_direccion", body.getTipoDireccion());
        if (body.getEsPrincipal() != null) { sql.append(", es_principal = ?"); params.add(body.getEsPrincipal()); }
        addStr(sql, params, "tipo_via", body.getTipoVia());
        addStr(sql, params, "calle", body.getCalle());
        addStr(sql, params, "numero_exterior", body.getNumeroExterior());
        addStr(sql, params, "numero_interior", body.getNumeroInterior());
        addStr(sql, params, "entre_calle_1", body.getEntreCalles1());
        addStr(sql, params, "entre_calle_2", body.getEntreCalles2());
        addStr(sql, params, "referencia", body.getReferencia());
        if (body.getCodigoPostalId() != null) { sql.append(", codigo_postal_id = ?"); params.add(body.getCodigoPostalId()); }
        if (body.getLocalidadId() != null) { sql.append(", localidad_id = ?"); params.add(body.getLocalidadId()); }
        if (body.getLatitud() != null) { sql.append(", latitud = ?"); params.add(body.getLatitud()); }
        if (body.getLongitud() != null) { sql.append(", longitud = ?"); params.add(body.getLongitud()); }
        addStr(sql, params, "precision_gps", body.getPrecisionGps());

        sql.append(" WHERE id = ?");
        params.add(id);
        jdbc.update(sql.toString(), params.toArray());
    }

    public int eliminarDireccion(UUID id) {
        return jdbc.update("UPDATE ades_direcciones SET is_active = FALSE WHERE id = ? AND is_active = TRUE", id);
    }

    public void setPrincipalDireccion(UUID id, String entidadTipo, UUID entidadId) {
        jdbc.update("UPDATE ades_direcciones SET es_principal = FALSE " +
                "WHERE entidad_tipo = ? AND entidad_id = ? AND is_active = TRUE", entidadTipo, entidadId);
        jdbc.update("UPDATE ades_direcciones SET es_principal = TRUE WHERE id = ?", id);
    }

    public UUID crearContacto(DireccionesController.PersonaContactoPayload body, String usuario) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_persona_contactos " +
                "(id, persona_id, medio, tipo, valor, etiqueta, es_principal, orden, notas, " +
                " usuario_creacion, usuario_modificacion) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id, body.getPersonaId(), body.getMedio(),
                body.getTipo() != null ? body.getTipo() : "PERSONAL",
                body.getValor(), body.getEtiqueta(),
                body.getEsPrincipal() != null ? body.getEsPrincipal() : false,
                body.getOrden() != null ? body.getOrden() : 1,
                body.getNotas(), usuario, usuario);
        return id;
    }

    public void actualizarContacto(UUID id, DireccionesController.PersonaContactoPayload body, String usuario) {
        StringBuilder sql = new StringBuilder(
                "UPDATE ades_persona_contactos SET row_version = row_version + 1, usuario_modificacion = ?");
        List<Object> params = new ArrayList<>();
        params.add(usuario);

        addStr(sql, params, "medio", body.getMedio());
        addStr(sql, params, "tipo", body.getTipo());
        addStr(sql, params, "valor", body.getValor());
        addStr(sql, params, "etiqueta", body.getEtiqueta());
        if (body.getEsPrincipal() != null) { sql.append(", es_principal = ?"); params.add(body.getEsPrincipal()); }
        if (body.getOrden() != null) { sql.append(", orden = ?"); params.add(body.getOrden()); }
        addStr(sql, params, "notas", body.getNotas());

        sql.append(" WHERE id = ?");
        params.add(id);
        jdbc.update(sql.toString(), params.toArray());
    }

    public int eliminarContacto(UUID id) {
        return jdbc.update("UPDATE ades_persona_contactos SET is_active = FALSE WHERE id = ? AND is_active = TRUE", id);
    }

    private void addStr(StringBuilder sql, List<Object> params, String col, String val) {
        if (val != null) { sql.append(", ").append(col).append(" = ?"); params.add(val); }
    }
}
