package mx.ades.modules.admin;

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
import mx.ades.modules.catalogos.CicloEscolar;
import mx.ades.modules.catalogos.CicloEscolarRepository;
import mx.ades.modules.catalogos.NivelEducativo;
import mx.ades.modules.catalogos.NivelEducativoRepository;
import mx.ades.modules.planteles.Plantel;
import mx.ades.modules.planteles.PlantelRepository;
import mx.ades.modules.grupos.Grupo;
import mx.ades.modules.grupos.GrupoRepository;
import mx.ades.modules.usuarios.Usuario;
import mx.ades.modules.usuarios.UsuarioRepository;
import mx.ades.modules.usuarios.Rol;
import mx.ades.modules.usuarios.RolRepository;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final CicloEscolarRepository cicloRepository;
    private final NivelEducativoRepository nivelRepository;
    private final PlantelRepository plantelRepository;
    private final GrupoRepository grupoRepository;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final IdentidadInstitucionalRepository marcaRepository;
    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    private void requireAdmin(AdesUser user) {
        if (user.getNivelAcceso() > 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere rol ADMIN_GLOBAL o ADMIN_PLANTEL");
        }
    }

    // ── CICLOS ESCOLARES ──────────────────────────────────────────────────────

    @Data
    public static class CicloCreateRequest {
        private String nombreCiclo;
        private UUID nivelEducativoId;
        private LocalDate fechaInicio;
        private LocalDate fechaFin;
        private String tipoCiclo = "ANUAL";
        private Boolean esVigente = false;
    }

    @Data
    public static class CicloUpdateRequest {
        private String nombreCiclo;
        private LocalDate fechaInicio;
        private LocalDate fechaFin;
        private String tipoCiclo;
        private Boolean esVigente;
    }

    @GetMapping("/ciclos")
    public ResponseEntity<List<Map<String, Object>>> listarCiclos(
            @RequestParam(value = "nivel", required = false) String nivel,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        StringBuilder sql = new StringBuilder(
                "SELECT c.id, c.nombre_ciclo, c.nivel_educativo_id, c.fecha_inicio, c.fecha_fin, " +
                "c.tipo_ciclo, c.es_vigente, c.is_active, n.nombre_nivel " +
                "FROM ades_ciclos_escolares c " +
                "JOIN ades_niveles_educativos n ON n.id = c.nivel_educativo_id " +
                "WHERE c.is_active = TRUE "
        );

        List<Object> params = new ArrayList<>();
        if (nivel != null && !nivel.isBlank()) {
            sql.append("AND UPPER(n.nombre_nivel) = ? ");
            params.add(nivel.toUpperCase());
        }

        sql.append("ORDER BY c.fecha_inicio DESC");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/ciclos")
    public ResponseEntity<CicloEscolar> crearCiclo(
            @RequestBody CicloCreateRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        if (body.getFechaFin().isBefore(body.getFechaInicio()) || body.getFechaFin().isEqual(body.getFechaInicio())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "fecha_fin debe ser posterior a fecha_inicio");
        }

        NivelEducativo nivel = nivelRepository.findById(body.getNivelEducativoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nivel educativo no encontrado"));

        CicloEscolar ciclo = new CicloEscolar();
        ciclo.setNombreCiclo(body.getNombreCiclo());
        ciclo.setNivelEducativo(nivel);
        ciclo.setFechaInicio(body.getFechaInicio());
        ciclo.setFechaFin(body.getFechaFin());
        ciclo.setTipoCiclo(body.getTipoCiclo());
        ciclo.setEsVigente(body.getEsVigente());

        CicloEscolar saved = cicloRepository.save(ciclo);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PatchMapping("/ciclos/{id}")
    public ResponseEntity<CicloEscolar> actualizarCiclo(
            @PathVariable("id") UUID id,
            @RequestBody CicloUpdateRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        CicloEscolar ciclo = cicloRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ciclo no encontrado"));

        if (body.getNombreCiclo() != null) ciclo.setNombreCiclo(body.getNombreCiclo());
        if (body.getFechaInicio() != null) ciclo.setFechaInicio(body.getFechaInicio());
        if (body.getFechaFin() != null) ciclo.setFechaFin(body.getFechaFin());
        if (body.getTipoCiclo() != null) ciclo.setTipoCiclo(body.getTipoCiclo());
        if (body.getEsVigente() != null) {
            ciclo.setEsVigente(body.getEsVigente());
            if (Boolean.TRUE.equals(body.getEsVigente())) {
                // Desactivar vigencia de otros ciclos del mismo nivel
                jdbc.update("UPDATE ades_ciclos_escolares SET es_vigente = FALSE " +
                        "WHERE nivel_educativo_id = ? AND id != ?",
                        ciclo.getNivelEducativo().getId(), id);
            }
        }

        CicloEscolar updated = cicloRepository.save(ciclo);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/ciclos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarCiclo(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        CicloEscolar ciclo = cicloRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ciclo no encontrado"));

        if (Boolean.TRUE.equals(ciclo.getEsVigente())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede eliminar el ciclo vigente");
        }

        ciclo.setIsActive(false);
        cicloRepository.save(ciclo);
    }

    // ── USUARIOS ──────────────────────────────────────────────────────────────

    @Data
    public static class UsuarioCreateRequest {
        private String nombre;
        private String apellidoPaterno;
        private String apellidoMaterno;
        private String curp;
        private String genero;
        private LocalDate fechaNacimiento;
        private UUID rolId;
        private UUID plantelId;
        private UUID nivelEducativoId;
        private String emailInstitucional;
    }

    @Data
    public static class UsuarioUpdateRequest {
        private UUID rolId;
        private UUID plantelId;
        private UUID nivelEducativoId;
        private Boolean isActive;
    }

    @GetMapping("/usuarios")
    public ResponseEntity<List<Map<String, Object>>> listarUsuarios(
            @RequestParam(value = "buscar", required = false) String buscar,
            @RequestParam(value = "rol", required = false) String rol,
            @RequestParam(value = "pagina", defaultValue = "1") int pagina,
            @RequestParam(value = "por_pagina", defaultValue = "50") int porPagina,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        StringBuilder sql = new StringBuilder(
                "SELECT u.id, u.nombre_usuario, u.email_institucional, u.plantel_id, u.nivel_educativo_id, u.is_active, " +
                "p.nombre || ' ' || p.apellido_paterno || COALESCE(' ' || p.apellido_materno, '') AS nombre_completo, " +
                "r.nombre_rol AS rol, r.nivel_acceso, " +
                "pl.nombre_plantel, nl.nombre_nivel " +
                "FROM ades_usuarios u " +
                "JOIN ades_personas p ON p.id = u.persona_id " +
                "JOIN ades_roles r ON r.id = u.rol_id " +
                "LEFT JOIN ades_planteles pl ON pl.id = u.plantel_id " +
                "LEFT JOIN ades_niveles_educativos nl ON nl.id = u.nivel_educativo_id " +
                "WHERE 1=1 "
        );

        List<Object> params = new ArrayList<>();
        if (user.getPlantelId() != null) {
            sql.append("AND u.plantel_id = ? ");
            params.add(user.getPlantelId());
        }
        if (rol != null && !rol.isBlank()) {
            sql.append("AND UPPER(r.nombre_rol) = ? ");
            params.add(rol.toUpperCase());
        }
        if (buscar != null && !buscar.isBlank()) {
            sql.append("AND (u.nombre_usuario ILIKE ? OR u.email_institucional ILIKE ? OR p.nombre ILIKE ? OR p.apellido_paterno ILIKE ?) ");
            String term = "%" + buscar + "%";
            params.add(term);
            params.add(term);
            params.add(term);
            params.add(term);
        }

        sql.append("ORDER BY r.nivel_acceso ASC, u.nombre_usuario ASC ");
        sql.append("LIMIT ? OFFSET ?");
        params.add(porPagina);
        params.add((pagina - 1) * porPagina);

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/usuarios")
    public ResponseEntity<Map<String, Object>> crearUsuario(
            @RequestBody UsuarioCreateRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        Rol rol = rolRepository.findById(body.getRolId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rol no encontrado"));

        if (rol.getNivelAcceso() < user.getNivelAcceso()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puede crear usuarios con mayor jerarquía");
        }

        // Validar CURP y Email
        mx.ades.common.ValidationUtils.validarCURP(body.getCurp());
        mx.ades.common.ValidationUtils.validarEmail(body.getEmailInstitucional());

        // Verificar CURP único
        List<Map<String, Object>> dupPersona = jdbc.queryForList("SELECT id FROM ades_personas WHERE UPPER(curp) = ?", body.getCurp().toUpperCase().trim());
        if (!dupPersona.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una persona con CURP " + body.getCurp().toUpperCase());
        }

        // Crear persona
        UUID personaId = UUID.randomUUID();
        jdbc.update("INSERT INTO ades_personas (id, nombre, apellido_paterno, apellido_materno, curp, genero, fecha_nacimiento) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                personaId, body.getNombre().trim(), body.getApellidoPaterno().trim(),
                body.getApellidoMaterno() != null ? body.getApellidoMaterno().trim() : null,
                body.getCurp().toUpperCase().trim(), body.getGenero(), body.getFechaNacimiento());

        // Slugify nombre_usuario
        String slug = (body.getNombre().substring(0, 1) + body.getApellidoPaterno().replace(" ", "")).toLowerCase();
        if (slug.length() > 9) slug = slug.substring(0, 9);
        String username = slug;
        int counter = 1;
        while (true) {
            List<Map<String, Object>> dupUser = jdbc.queryForList("SELECT id FROM ades_usuarios WHERE nombre_usuario = ?", username);
            if (dupUser.isEmpty()) break;
            username = slug + counter;
            counter++;
        }

        String email = body.getEmailInstitucional() != null ? body.getEmailInstitucional().trim() : (username + "@nevadi.edu.mx");

        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO ades_usuarios (id, persona_id, nombre_usuario, email_institucional, rol_id, plantel_id, nivel_educativo_id, clave_hash) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDIENTE_OIDC')",
                userId, personaId, username, email, body.getRolId(), body.getPlantelId(), body.getNivelEducativoId());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", userId,
                "nombre_usuario", username,
                "email_institucional", email,
                "nombre_completo", body.getNombre() + " " + body.getApellidoPaterno(),
                "rol", rol.getNombreRol(),
                "is_active", true
        ));
    }

    @PatchMapping("/usuarios/{id}")
    public ResponseEntity<Map<String, Object>> actualizarUsuario(
            @PathVariable("id") UUID id,
            @RequestBody UsuarioUpdateRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        if (user.getPlantelId() != null && !user.getPlantelId().equals(usuario.getPlantelId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puede editar usuarios de otro plantel");
        }

        if (body.getRolId() != null) {
            Rol rol = rolRepository.findById(body.getRolId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rol no encontrado"));
            if (rol.getNivelAcceso() < user.getNivelAcceso()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puede asignar un rol de mayor jerarquía");
            }
            usuario.setRol(rol);
        }

        if (body.getPlantelId() != null) usuario.setPlantelId(body.getPlantelId());
        if (body.getNivelEducativoId() != null) usuario.setNivelEducativoId(body.getNivelEducativoId());
        if (body.getIsActive() != null) usuario.setIsActive(body.getIsActive());

        usuarioRepository.save(usuario);

        return ResponseEntity.ok(Map.of("ok", true, "id", id));
    }

    // ── MARCA / IDENTIDAD INSTITUCIONAL ──────────────────────────────────────

    @Data
    public static class MarcaItemUpdate {
        private String tipoElemento;
        private String valor;
    }

    @GetMapping("/marca")
    public ResponseEntity<List<IdentidadInstitucional>> obtenerMarca(
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);
        List<IdentidadInstitucional> items = marcaRepository.findByIsActiveTrueAndPlantelIdIsNullOrderByTipoElemento();
        return ResponseEntity.ok(items);
    }

    @PutMapping("/marca")
    public ResponseEntity<Map<String, Object>> actualizarMarca(
            @RequestBody List<MarcaItemUpdate> items,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        if (user.getNivelAcceso() > 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo ADMIN_GLOBAL puede modificar la identidad");
        }

        for (MarcaItemUpdate item : items) {
            Optional<IdentidadInstitucional> opt = marcaRepository.findByTipoElementoAndPlantelIdIsNullAndIsActiveTrue(item.getTipoElemento());
            IdentidadInstitucional reg;
            if (opt.isPresent()) {
                reg = opt.get();
            } else {
                reg = new IdentidadInstitucional();
                reg.setTipoElemento(item.getTipoElemento());
            }

            String val = item.getValor() != null ? item.getValor() : "";
            if (item.getTipoElemento().contains("COLOR")) {
                reg.setColorHex(val);
            } else if (item.getTipoElemento().contains("URL") || item.getTipoElemento().equals("LOGO") || item.getTipoElemento().equals("FAVICON_URL")) {
                reg.setUrlArchivo(val);
            } else {
                reg.setTextoElemento(val);
            }
            marcaRepository.save(reg);
        }

        return ResponseEntity.ok(Map.of("ok", true, "updated", items.size()));
    }

    // ── PLANTELES ────────────────────────────────────────────────────────────

    @Data
    public static class PlantelAdminUpdate {
        private String nombrePlantel;
        private String claveCt;
    }

    @PatchMapping("/planteles/{id}")
    public ResponseEntity<Plantel> actualizarPlantel(
            @PathVariable("id") UUID id,
            @RequestBody PlantelAdminUpdate body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        if (user.getNivelAcceso() > 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo ADMIN_GLOBAL puede editar planteles");
        }

        Plantel plantel = plantelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plantel no encontrado"));

        if (body.getNombrePlantel() != null) plantel.setNombrePlantel(body.getNombrePlantel());
        if (body.getClaveCt() != null) plantel.setClaveCt(body.getClaveCt());

        Plantel saved = plantelRepository.save(plantel);
        return ResponseEntity.ok(saved);
    }

    // ── GRUPOS (admin) ───────────────────────────────────────────────────────

    @Data
    public static class GrupoAdminUpdate {
        private String nombreGrupo;
        private Integer capacidadMaxima;
        private String turno;
        private UUID profesorTitularId;
        private Boolean isActive;
    }

    @GetMapping("/grupos")
    public ResponseEntity<List<Map<String, Object>>> listarGruposAdmin(
            @RequestParam(value = "plantel_id", required = false) UUID inputPlantelId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        StringBuilder sql = new StringBuilder(
                "SELECT g.id, g.nombre_grupo, g.capacidad_maxima, g.turno, g.ciclo_escolar_id, g.is_active, " +
                "gr.nombre_grado, gr.numero_grado, n.nombre_nivel " +
                "FROM ades_grupos g " +
                "JOIN ades_grados gr ON gr.id = g.grado_id " +
                "JOIN ades_niveles_educativos n ON n.id = gr.nivel_educativo_id " +
                "WHERE 1=1 "
        );

        List<Object> params = new ArrayList<>();
        UUID plid = user.getPlantelId() != null ? user.getPlantelId() : inputPlantelId;
        if (plid != null) {
            sql.append("AND gr.plantel_id = ? ");
            params.add(plid);
        }
        if (user.getNivelEducativoId() != null) {
            sql.append("AND gr.nivel_educativo_id = ? ");
            params.add(user.getNivelEducativoId());
        }
        if (cicloId != null) {
            sql.append("AND g.ciclo_escolar_id = ? ");
            params.add(cicloId);
        }

        sql.append("ORDER BY n.nombre_nivel ASC, gr.numero_grado ASC, g.nombre_grupo ASC");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/grupos")
    public ResponseEntity<Grupo> crearGrupo(
            @RequestBody Grupo grupo,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        // Validaciones básicas de integridad
        jdbc.queryForList("SELECT id FROM ades_grados WHERE id = ?", grupo.getGradoId());
        jdbc.queryForList("SELECT id FROM ades_ciclos_escolares WHERE id = ?", grupo.getCicloEscolarId());

        Grupo saved = grupoRepository.save(grupo);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PatchMapping("/grupos/{id}")
    public ResponseEntity<Grupo> actualizarGrupoAdmin(
            @PathVariable("id") UUID id,
            @RequestBody GrupoAdminUpdate body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAdmin(user);

        Grupo grupo = grupoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo no encontrado"));

        if (body.getNombreGrupo() != null) grupo.setNombreGrupo(body.getNombreGrupo());
        if (body.getCapacidadMaxima() != null) grupo.setCapacidadMaxima(body.getCapacidadMaxima());
        if (body.getTurno() != null) grupo.setTurno(body.getTurno());
        if (body.getProfesorTitularId() != null) grupo.setProfesorTitularId(body.getProfesorTitularId());
        if (body.getIsActive() != null) grupo.setIsActive(body.getIsActive());

        Grupo saved = grupoRepository.save(grupo);
        return ResponseEntity.ok(saved);
    }
}
