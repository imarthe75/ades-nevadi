package mx.ades.modules.foros;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/foros")
@RequiredArgsConstructor
public class ForoController {

    private final ForoRepository repository;
    private final MensajeForoRepository mensajeRepository;
    private final RespuestaForoRepository respuestaRepository;
    private final AnuncioRepository anuncioRepository;
    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    @Data
    public static class ForoCreateRequest {
        private String nombre;
        private String descripcion;
        private String tipo = "GRUPO";
        private UUID grupoId;
        private UUID plantelId;
        private UUID materiaId;
        private Boolean esModerado = false;
    }

    @Data
    public static class MensajeForoRequest {
        private String asunto;
        private String contenido;
        private String adjuntoUrl;
    }

    @Data
    public static class RespuestaForoRequest {
        private String contenido;
        private String adjuntoUrl;
    }

    @Data
    public static class AnuncioRequest {
        private String titulo;
        private String contenido;
        private UUID plantelId;
        private String nivelEducativo;
        private String fechaInicio;
        private String fechaFin;
        private Boolean esUrgente = false;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(value = "grupo_id", required = false) UUID grupoId,
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @RequestParam(value = "tipo", required = false) String tipo,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);

        StringBuilder query = new StringBuilder(
                "SELECT f.id, f.nombre, f.descripcion, f.tipo, f.es_moderado, f.materia_id, f.fecha_creacion, " +
                "g.nombre_grupo, pl.nombre_plantel, mat.nombre_materia, " +
                "(SELECT COUNT(*) FROM ades_mensajes_foro WHERE foro_id = f.id AND is_active = TRUE) as total_mensajes " +
                "FROM ades_foros f " +
                "LEFT JOIN ades_grupos g ON g.id = f.grupo_id " +
                "LEFT JOIN ades_planteles pl ON pl.id = f.plantel_id " +
                "LEFT JOIN ades_materias mat ON mat.id = f.materia_id " +
                "WHERE f.is_active = TRUE ");

        List<Object> params = new ArrayList<>();

        if (grupoId != null) {
            query.append("AND f.grupo_id = ? ");
            params.add(grupoId);
        }
        if (plantelId != null) {
            query.append("AND f.plantel_id = ? ");
            params.add(plantelId);
        }
        if (materiaId != null) {
            query.append("AND f.materia_id = ? ");
            params.add(materiaId);
        }
        if (tipo != null && !tipo.isBlank()) {
            query.append("AND f.tipo = ? ");
            params.add(tipo);
        }

        // Apply access restrictions for parent roles (level 5)
        if (user.getNivelAcceso() != null && user.getNivelAcceso() == 5) {
            query.append("AND f.tipo IN ('GENERAL', 'PLANTEL', 'GRUPO', 'TUTORES', 'MATERIA') ");
        }

        query.append("ORDER BY f.tipo, f.nombre");

        return ResponseEntity.ok(jdbc.queryForList(query.toString(), params.toArray()));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(
            @RequestBody ForoCreateRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere Coordinador o superior");
        }

        Foro f = new Foro();
        f.setNombre(body.getNombre());
        f.setDescripcion(body.getDescripcion());
        f.setTipo(body.getTipo());
        f.setGrupoId(body.getGrupoId());
        f.setPlantelId(body.getPlantelId());
        f.setMateriaId(body.getMateriaId());
        f.setEsModerado(body.getEsModerado());
        f.setCreadoPor(user.getId());

        Foro saved = repository.save(f);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", saved.getId(), "message", "Foro creado"));
    }

    @GetMapping("/{id}/mensajes")
    public ResponseEntity<List<Map<String, Object>>> listarMensajes(
            @PathVariable("id") UUID id,
            @RequestParam(value = "skip", defaultValue = "0") int skip,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {

        String sql = "SELECT m.id, m.asunto, m.contenido, m.adjunto_url, m.fecha_creacion, m.usuario_creacion, " +
                "(SELECT COUNT(*) FROM ades_respuestas_foro WHERE mensaje_id = m.id AND is_active = TRUE) as respuestas " +
                "FROM ades_mensajes_foro m " +
                "WHERE m.foro_id = ? AND m.is_active = TRUE AND m.mensaje_padre_id IS NULL " +
                "ORDER BY m.fecha_creacion DESC LIMIT ? OFFSET ?";

        return ResponseEntity.ok(jdbc.queryForList(sql, id, limit, skip));
    }

    @PostMapping("/{id}/mensajes")
    public ResponseEntity<Map<String, Object>> publicarMensaje(
            @PathVariable("id") UUID id,
            @RequestBody MensajeForoRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        Foro foro = repository.findById(id)
                .filter(Foro::getIsActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Foro no encontrado"));

        AdesUser user = userService.resolveUser(jwt);
        String estado = Boolean.TRUE.equals(foro.getEsModerado()) ? "PENDIENTE" : "PUBLICADO";

        MensajeForo m = new MensajeForo();
        m.setForoId(id);
        m.setAsunto(body.getAsunto());
        m.setContenido(body.getContenido());
        m.setAdjuntoUrl(body.getAdjuntoUrl());
        m.setEstado(estado);
        m.setAutorId(user.getId());

        MensajeForo saved = mensajeRepository.save(m);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", saved.getId(), "estado", estado, "message", "Mensaje publicado"));
    }

    @PostMapping("/{foroId}/mensajes/{mensajeId}/responder")
    public ResponseEntity<Map<String, Object>> responderMensaje(
            @PathVariable("foroId") UUID foroId,
            @PathVariable("mensajeId") UUID mensajeId,
            @RequestBody RespuestaForoRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        MensajeForo m = mensajeRepository.findById(mensajeId)
                .filter(msg -> msg.getForoId().equals(foroId) && msg.getIsActive())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mensaje no encontrado"));

        AdesUser user = userService.resolveUser(jwt);

        RespuestaForo rf = new RespuestaForo();
        rf.setMensajeId(mensajeId);
        rf.setContenido(body.getContenido());
        rf.setAdjuntoUrl(body.getAdjuntoUrl());
        rf.setAutorId(user.getId());

        RespuestaForo saved = respuestaRepository.save(rf);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", saved.getId(), "message", "Respuesta publicada"));
    }

    @GetMapping("/{foroId}/mensajes/{mensajeId}/respuestas")
    public ResponseEntity<List<Map<String, Object>>> listarRespuestas(
            @PathVariable("foroId") UUID foroId,
            @PathVariable("mensajeId") UUID mensajeId) {

        String sql = "SELECT id, contenido, adjunto_url, fecha_creacion, usuario_creacion " +
                "FROM ades_respuestas_foro " +
                "WHERE mensaje_id = ? AND is_active = TRUE " +
                "ORDER BY fecha_creacion";

        return ResponseEntity.ok(jdbc.queryForList(sql, mensajeId));
    }

    @GetMapping("/anuncios")
    public ResponseEntity<List<Map<String, Object>>> listarAnuncios(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "nivel_educativo", required = false) String nivelEducativo,
            @RequestParam(value = "solo_vigentes", defaultValue = "true") boolean soloVigentes) {

        StringBuilder query = new StringBuilder(
                "SELECT id, titulo, contenido, es_urgente, nivel_educativo, " +
                "fecha_inicio, fecha_fin, fecha_creacion, usuario_creacion " +
                "FROM ades_anuncios " +
                "WHERE is_active = TRUE ");

        List<Object> params = new ArrayList<>();

        if (soloVigentes) {
            query.append("AND fecha_inicio <= CURRENT_DATE AND (fecha_fin IS NULL OR fecha_fin >= CURRENT_DATE) ");
        }
        if (plantelId != null) {
            query.append("AND (plantel_id = ? OR plantel_id IS NULL) ");
            params.add(plantelId);
        }
        if (nivelEducativo != null && !nivelEducativo.isBlank()) {
            query.append("AND (nivel_educativo = ? OR nivel_educativo IS NULL) ");
            params.add(nivelEducativo);
        }

        query.append("ORDER BY es_urgente DESC, fecha_creacion DESC");

        return ResponseEntity.ok(jdbc.queryForList(query.toString(), params.toArray()));
    }

    @PostMapping("/anuncios")
    public ResponseEntity<Map<String, Object>> publicarAnuncio(
            @RequestBody AnuncioRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere Coordinador o superior");
        }

        Anuncio a = new Anuncio();
        a.setTitulo(body.getTitulo());
        a.setContenido(body.getContenido());
        a.setPlantelId(body.getPlantelId());
        a.setNivelEducativo(body.getNivelEducativo());
        if (body.getFechaInicio() != null) {
            a.setFechaInicio(LocalDate.parse(body.getFechaInicio()));
        }
        if (body.getFechaFin() != null) {
            a.setFechaFin(LocalDate.parse(body.getFechaFin()));
        }
        a.setEsUrgente(body.getEsUrgente());

        Anuncio saved = anuncioRepository.save(a);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", saved.getId(), "message", "Anuncio publicado"));
    }

    @PatchMapping("/mensajes/{mensajeId}/moderar")
    public ResponseEntity<Map<String, Object>> moderar(
            @PathVariable("mensajeId") UUID mensajeId,
            @RequestParam("estado") String estado,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere rol de Coordinador o superior para moderar contenido.");
        }

        if (!List.of("PUBLICADO", "RECHAZADO", "PENDIENTE").contains(estado.toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado inválido.");
        }

        MensajeForo m = mensajeRepository.findById(mensajeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mensaje no encontrado."));

        boolean isActive = !"RECHAZADO".equalsIgnoreCase(estado);
        m.setEstado(estado.toUpperCase());
        m.setIsActive(isActive);
        mensajeRepository.save(m);

        return ResponseEntity.ok(Map.of("message", "Mensaje moderado correctamente. Estado: " + estado));
    }
}
