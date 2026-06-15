package mx.ades.modules.gradebook;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/esquemas-ponderacion")
@RequiredArgsConstructor
public class EsquemasPonderacionController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    @Data
    public static class ItemIn {
        private String tipoItem;
        private String nombrePersonalizado;
        private Double pesoPorcentaje;
        private Integer ordenDisplay = 1;
    }

    @Data
    public static class EsquemaIn {
        private String nombre;
        private UUID nivelEducativoId;
        private UUID materiaId;
        private LocalDate vigenteDesde;
        private LocalDate vigenteHasta;
        private List<ItemIn> items;
    }

    private void validarSuma100(List<ItemIn> items) {
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los ítems de ponderación son requeridos");
        }
        double total = 0.0;
        for (ItemIn i : items) {
            if (i.getPesoPorcentaje() != null) {
                total += i.getPesoPorcentaje();
            }
        }
        if (Math.abs(total - 100.0) > 0.01) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los pesos deben sumar 100% (suma actual: " + total + "%)");
        }
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listarEsquemas(
            @RequestParam(value = "nivel_educativo_id", required = false) UUID nivelEducativoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder(
                "SELECT ep.id, ep.nombre, ep.vigente_desde, ep.vigente_hasta, " +
                "ep.activo, ep.materia_id, " +
                "ne.nombre_nivel, " +
                "m.nombre_materia, " +
                "(SELECT json_agg(json_build_object( " +
                "            'id', ip.id, " +
                "            'tipo_item', ip.tipo_item, " +
                "            'nombre_personalizado', ip.nombre_personalizado, " +
                "            'peso_porcentaje', ip.peso_porcentaje, " +
                "            'orden_display', ip.orden_display) " +
                "        ORDER BY ip.orden_display) " +
                " FROM ades_items_ponderacion ip " +
                " WHERE ip.esquema_id = ep.id AND ip.is_active = TRUE " +
                ") AS items " +
                "FROM ades_esquemas_ponderacion ep " +
                "JOIN ades_niveles_educativos ne ON ne.id = ep.nivel_educativo_id " +
                "LEFT JOIN ades_materias m ON m.id = ep.materia_id " +
                "WHERE ep.is_active = TRUE "
        );

        List<Object> params = new ArrayList<>();
        if (nivelEducativoId != null) {
            sql.append("AND ep.nivel_educativo_id = ? ");
            params.add(nivelEducativoId);
        }
        if (materiaId != null) {
            sql.append("AND (ep.materia_id = ? OR ep.materia_id IS NULL) ");
            params.add(materiaId);
        }

        sql.append("ORDER BY ne.nombre_nivel, ep.vigente_desde DESC");

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        
        // Convert PG JSON String representation to map if necessary (or let Spring Boot Jackson do it)
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/efectivo/{materiaId}")
    public ResponseEntity<Map<String, Object>> esquemaEfectivo(
            @PathVariable("materiaId") UUID materiaId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        String sql = "SELECT ep.id, ep.nombre, ep.vigente_desde, ep.vigente_hasta, " +
                "ep.materia_id, ne.nombre_nivel, ne.escala_maxima, " +
                "ne.minimo_aprobatorio, " +
                "(SELECT json_agg(json_build_object( " +
                "            'id', ip.id, " +
                "            'tipo_item', ip.tipo_item, " +
                "            'nombre_personalizado', ip.nombre_personalizado, " +
                "            'peso_porcentaje', ip.peso_porcentaje, " +
                "            'orden_display', ip.orden_display) " +
                "        ORDER BY ip.orden_display) " +
                " FROM ades_items_ponderacion ip " +
                " WHERE ip.esquema_id = ep.id AND ip.is_active = TRUE " +
                ") AS items " +
                "FROM ades_esquemas_ponderacion ep " +
                "JOIN ades_niveles_educativos ne ON ne.id = ep.nivel_educativo_id " +
                "JOIN ades_materias m ON m.nivel_educativo_id = ne.id " +
                "WHERE m.id = ? " +
                "AND ep.activo = TRUE " +
                "AND (ep.vigente_hasta IS NULL OR ep.vigente_hasta >= CURRENT_DATE) " +
                "AND ep.vigente_desde <= CURRENT_DATE " +
                "ORDER BY (ep.materia_id = ?) DESC NULLS LAST, " +
                "ep.vigente_desde DESC " +
                "LIMIT 1";

        List<Map<String, Object>> rows = jdbc.queryForList(sql, materiaId, materiaId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay esquema de ponderación para esta materia");
        }
        return ResponseEntity.ok(rows.get(0));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crearEsquema(
            @RequestBody EsquemaIn body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        validarSuma100(body.getItems());

        UUID esquemaId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_esquemas_ponderacion " +
                "(id, nombre, nivel_educativo_id, materia_id, vigente_desde, vigente_hasta, creado_por, activo, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)",
                esquemaId, body.getNombre(), body.getNivelEducativoId(), body.getMateriaId(),
                body.getVigenteDesde(), body.getVigenteHasta(), user.getId(), user.getUsername(), user.getUsername()
        );

        for (ItemIn i : body.getItems()) {
            jdbc.update(
                    "INSERT INTO ades_items_ponderacion " +
                    "(id, esquema_id, tipo_item, nombre_personalizado, peso_porcentaje, orden_display, usuario_creacion, usuario_modificacion) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), esquemaId, i.getTipoItem(), i.getNombrePersonalizado(),
                    BigDecimal.valueOf(i.getPesoPorcentaje()), i.getOrdenDisplay(), user.getUsername(), user.getUsername()
            );
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", esquemaId.toString(), "message", "Esquema creado"));
    }

    @PutMapping("/{esquemaId}")
    public ResponseEntity<Map<String, Object>> actualizarEsquema(
            @PathVariable("esquemaId") UUID esquemaId,
            @RequestBody EsquemaIn body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        validarSuma100(body.getItems());

        int exists = jdbc.update(
                "UPDATE ades_esquemas_ponderacion " +
                "SET nombre = ?, nivel_educativo_id = ?, materia_id = ?, vigente_desde = ?, vigente_hasta = ?, " +
                "usuario_modificacion = ?, row_version = row_version + 1, fecha_modificacion = CURRENT_TIMESTAMP " +
                "WHERE id = ? AND is_active = TRUE",
                body.getNombre(), body.getNivelEducativoId(), body.getMateriaId(),
                body.getVigenteDesde(), body.getVigenteHasta(), user.getUsername(), esquemaId
        );

        if (exists == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Esquema no encontrado");
        }

        // Soft delete items and insert new ones
        jdbc.update("UPDATE ades_items_ponderacion SET is_active = FALSE WHERE esquema_id = ?", esquemaId);

        for (ItemIn i : body.getItems()) {
            jdbc.update(
                    "INSERT INTO ades_items_ponderacion " +
                    "(id, esquema_id, tipo_item, nombre_personalizado, peso_porcentaje, orden_display, usuario_creacion, usuario_modificacion) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), esquemaId, i.getTipoItem(), i.getNombrePersonalizado(),
                    BigDecimal.valueOf(i.getPesoPorcentaje()), i.getOrdenDisplay(), user.getUsername(), user.getUsername()
            );
        }

        return ResponseEntity.ok(Map.of("message", "Esquema actualizado"));
    }

    @DeleteMapping("/{esquemaId}")
    public ResponseEntity<Map<String, Object>> desactivarEsquema(
            @PathVariable("esquemaId") UUID esquemaId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        int updated = jdbc.update(
                "UPDATE ades_esquemas_ponderacion " +
                "SET activo = FALSE, is_active = FALSE, usuario_modificacion = ?, fecha_modificacion = CURRENT_TIMESTAMP " +
                "WHERE id = ?",
                user.getUsername(), esquemaId
        );

        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Esquema no encontrado");
        }

        return ResponseEntity.ok(Map.of("message", "Esquema desactivado"));
    }
}
