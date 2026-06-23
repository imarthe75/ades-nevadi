package mx.ades.modules.admin;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.modules.admin.domain.model.PermisoAdmin;
import mx.ades.modules.admin.query.AdminQueryService;
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
    private final AdminWriteService writeService;
    private final AdminQueryService queryService;
    private final ConfigQueryService configQueryService;

    private PermisoAdmin permisoAdmin(AdesUser user) {
        PermisoAdmin permiso = new PermisoAdmin(user.getNivelAcceso() != null ? user.getNivelAcceso() : 99);
        if (!permiso.esAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere rol ADMIN_GLOBAL o ADMIN_PLANTEL");
        }
        return permiso;
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
        permisoAdmin(user);
        return ResponseEntity.ok(queryService.listarCiclos(nivel));
    }

    @PostMapping("/ciclos")
    public ResponseEntity<CicloEscolar> crearCiclo(
            @RequestBody CicloCreateRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        permisoAdmin(user);

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

        return ResponseEntity.status(HttpStatus.CREATED).body(cicloRepository.save(ciclo));
    }

    @PatchMapping("/ciclos/{id}")
    public ResponseEntity<CicloEscolar> actualizarCiclo(
            @PathVariable("id") UUID id,
            @RequestBody CicloUpdateRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        permisoAdmin(user);

        CicloEscolar ciclo = cicloRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ciclo no encontrado"));

        if (body.getNombreCiclo() != null) ciclo.setNombreCiclo(body.getNombreCiclo());
        if (body.getFechaInicio() != null) ciclo.setFechaInicio(body.getFechaInicio());
        if (body.getFechaFin() != null) ciclo.setFechaFin(body.getFechaFin());
        if (body.getTipoCiclo() != null) ciclo.setTipoCiclo(body.getTipoCiclo());
        if (body.getEsVigente() != null) {
            ciclo.setEsVigente(body.getEsVigente());
            if (Boolean.TRUE.equals(body.getEsVigente())) {
                writeService.desactivarCiclosAnteriores(ciclo.getNivelEducativo().getId(), id);
            }
        }

        return ResponseEntity.ok(cicloRepository.save(ciclo));
    }

    @DeleteMapping("/ciclos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarCiclo(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        permisoAdmin(user);

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
        private Integer rowVersion; // Optimistic locking — enviar la versión leída
    }

    @GetMapping("/usuarios")
    public ResponseEntity<List<Map<String, Object>>> listarUsuarios(
            @RequestParam(value = "buscar", required = false) String buscar,
            @RequestParam(value = "rol", required = false) String rol,
            @RequestParam(value = "pagina", defaultValue = "1") int pagina,
            @RequestParam(value = "por_pagina", defaultValue = "50") int porPagina,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        permisoAdmin(user);
        return ResponseEntity.ok(queryService.listarUsuarios(buscar, rol, user.getPlantelId(), pagina, porPagina));
    }

    @PostMapping("/usuarios")
    public ResponseEntity<Map<String, Object>> crearUsuario(
            @RequestBody UsuarioCreateRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        PermisoAdmin permiso = permisoAdmin(user);

        if (body.getRolId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rolId es requerido");

        Rol rol = rolRepository.findById(body.getRolId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rol no encontrado"));

        if (!permiso.puedeAsignarRol(rol.getNivelAcceso())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puede crear usuarios con mayor jerarquía");
        }

        mx.ades.common.ValidationUtils.validarCURP(body.getCurp());
        mx.ades.common.ValidationUtils.validarEmail(body.getEmailInstitucional());

        if (queryService.curpExiste(body.getCurp())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una persona con CURP " + body.getCurp().toUpperCase());
        }

        UUID personaId = writeService.insertPersona(
            body.getNombre().trim(), body.getApellidoPaterno().trim(),
            body.getApellidoMaterno() != null ? body.getApellidoMaterno().trim() : null,
            body.getCurp().toUpperCase().trim(), body.getGenero(), body.getFechaNacimiento());

        String slug = (body.getNombre().substring(0, 1) + body.getApellidoPaterno().replace(" ", "")).toLowerCase();
        if (slug.length() > 9) slug = slug.substring(0, 9);
        String username = slug;
        int counter = 1;
        while (queryService.usernameExiste(username)) {
            username = slug + counter++;
        }

        String email = body.getEmailInstitucional() != null ? body.getEmailInstitucional().trim() : (username + "@nevadi.edu.mx");
        UUID userId = writeService.insertUsuario(personaId, username, email,
            body.getRolId(), body.getPlantelId(), body.getNivelEducativoId());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", userId, "nombre_usuario", username,
                "email_institucional", email,
                "nombre_completo", body.getNombre() + " " + body.getApellidoPaterno(),
                "rol", rol.getNombreRol(), "is_active", true));
    }

    @PatchMapping("/usuarios/{id}")
    public ResponseEntity<Map<String, Object>> actualizarUsuario(
            @PathVariable("id") UUID id,
            @RequestBody UsuarioUpdateRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        PermisoAdmin permiso = permisoAdmin(user);

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        // Optimistic locking: rechazar si el cliente envió versión y no coincide
        if (body.getRowVersion() != null && !body.getRowVersion().equals(usuario.getRowVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "El registro fue modificado por otro usuario. Recarga y vuelve a intentarlo. " +
                "(versión enviada: " + body.getRowVersion() + ", actual: " + usuario.getRowVersion() + ")");
        }

        if (!permiso.puedeEditarOtrosPlantelUsuarios() && user.getPlantelId() != null
                && !user.getPlantelId().equals(usuario.getPlantelId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puede editar usuarios de otro plantel");
        }

        if (body.getRolId() != null) {
            Rol rol = rolRepository.findById(body.getRolId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rol no encontrado"));
            if (!permiso.puedeAsignarRol(rol.getNivelAcceso())) {
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
        permisoAdmin(user);
        return ResponseEntity.ok(marcaRepository.findByIsActiveTrueAndPlantelIdIsNullOrderByTipoElemento());
    }

    @PutMapping("/marca")
    public ResponseEntity<Map<String, Object>> actualizarMarca(
            @RequestBody List<MarcaItemUpdate> items,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        PermisoAdmin permiso = permisoAdmin(user);

        if (!permiso.esAdminGlobal()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo ADMIN_GLOBAL puede modificar la identidad");
        }

        for (MarcaItemUpdate item : items) {
            Optional<IdentidadInstitucional> opt = marcaRepository.findByTipoElementoAndPlantelIdIsNullAndIsActiveTrue(item.getTipoElemento());
            IdentidadInstitucional reg = opt.orElseGet(() -> {
                IdentidadInstitucional nuevo = new IdentidadInstitucional();
                nuevo.setTipoElemento(item.getTipoElemento());
                return nuevo;
            });

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
        PermisoAdmin permiso = permisoAdmin(user);

        if (!permiso.esAdminGlobal()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo ADMIN_GLOBAL puede editar planteles");
        }

        Plantel plantel = plantelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plantel no encontrado"));

        if (body.getNombrePlantel() != null) plantel.setNombrePlantel(body.getNombrePlantel());
        if (body.getClaveCt() != null) plantel.setClaveCt(body.getClaveCt());

        return ResponseEntity.ok(plantelRepository.save(plantel));
    }

    // ── GRUPOS (admin) ───────────────────────────────────────────────────────

    @Data
    public static class GrupoAdminUpdate {
        private String nombreGrupo;
        private Integer capacidadMaxima;
        private String turno;
        private UUID profesorTitularId;
        private Boolean isActive;
        private UUID gradoId;
        private UUID cicloEscolarId;
    }

    @GetMapping("/grupos")
    public ResponseEntity<List<Map<String, Object>>> listarGruposAdmin(
            @RequestParam(value = "plantel_id", required = false) UUID inputPlantelId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        permisoAdmin(user);
        UUID plantelId = user.getPlantelId() != null ? user.getPlantelId() : inputPlantelId;
        return ResponseEntity.ok(queryService.listarGrupos(plantelId, user.getNivelEducativoId(), cicloId));
    }

    @PostMapping("/grupos")
    public ResponseEntity<Grupo> crearGrupo(
            @RequestBody Grupo grupo,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        permisoAdmin(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(grupoRepository.save(grupo));
    }

    @PatchMapping("/grupos/{id}")
    public ResponseEntity<Grupo> actualizarGrupoAdmin(
            @PathVariable("id") UUID id,
            @RequestBody GrupoAdminUpdate body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        permisoAdmin(user);

        Grupo grupo = grupoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo no encontrado"));

        if (body.getNombreGrupo() != null) grupo.setNombreGrupo(body.getNombreGrupo());
        if (body.getCapacidadMaxima() != null) grupo.setCapacidadMaxima(body.getCapacidadMaxima());
        if (body.getTurno() != null) grupo.setTurno(body.getTurno());
        if (body.getProfesorTitularId() != null) grupo.setProfesorTitularId(body.getProfesorTitularId());
        if (body.getIsActive() != null) grupo.setIsActive(body.getIsActive());
        if (body.getGradoId() != null) grupo.setGradoId(body.getGradoId());
        if (body.getCicloEscolarId() != null) grupo.setCicloEscolarId(body.getCicloEscolarId());

        return ResponseEntity.ok(grupoRepository.save(grupo));
    }

    // ── CONFIGURACIÓN DEL SISTEMA ─────────────────────────────────────────────

    @GetMapping("/config")
    public ResponseEntity<List<Map<String, Object>>> listarConfig(
            @RequestParam(value = "grupo", required = false) String grupo,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        permisoAdmin(user);
        return ResponseEntity.ok(configQueryService.listarConfig(grupo));
    }

    @Data
    public static class ConfigUpdateRequest {
        private Object valor;
    }

    @PatchMapping("/config/{clave}")
    public ResponseEntity<Map<String, Object>> actualizarConfig(
            @PathVariable("clave") String clave,
            @RequestBody ConfigUpdateRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        permisoAdmin(user);
        if (body.getValor() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "valor requerido");
        return ResponseEntity.ok(configQueryService.actualizarConfig(clave, body.getValor()));
    }

    // ── ESCALAS CUALITATIVAS ──────────────────────────────────────────────────

    @GetMapping("/config/escalas-cualitativas")
    public ResponseEntity<List<Map<String, Object>>> listarEscalas(
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        permisoAdmin(user);
        return ResponseEntity.ok(configQueryService.listarEscalasCualitativas());
    }

    @Data
    public static class EscalaUpdateRequest {
        private String nombre;
        private String descripcion;
        private Object valores_json;
        private Boolean is_active;
    }

    @PutMapping("/config/escalas-cualitativas/{id}")
    public ResponseEntity<Map<String, Object>> actualizarEscala(
            @PathVariable("id") String id,
            @RequestBody EscalaUpdateRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        permisoAdmin(user);
        Map<String, Object> bodyMap = new HashMap<>();
        if (body.getNombre() != null)      bodyMap.put("nombre", body.getNombre());
        if (body.getDescripcion() != null) bodyMap.put("descripcion", body.getDescripcion());
        if (body.getValores_json() != null) bodyMap.put("valores_json", body.getValores_json());
        if (body.getIs_active() != null)   bodyMap.put("is_active", body.getIs_active());
        return ResponseEntity.ok(configQueryService.actualizarEscala(id, bodyMap));
    }

    // ── ROLES ────────────────────────────────────────────────────────────────

    @GetMapping("/roles")
    public ResponseEntity<List<Map<String, Object>>> listarRoles(
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        permisoAdmin(user);
        return ResponseEntity.ok(queryService.listarRoles());
    }

    @Data
    public static class RolUpdateRequest {
        private String descripcion;
    }

    @PatchMapping("/roles/{id}")
    public ResponseEntity<Map<String, Object>> actualizarRol(
            @PathVariable("id") UUID id,
            @RequestBody RolUpdateRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        PermisoAdmin permiso = permisoAdmin(user);
        if (!permiso.esAdminGlobal())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo ADMIN_GLOBAL puede editar roles");
        Rol rol = rolRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rol no encontrado"));
        if (body.getDescripcion() != null) rol.setDescripcion(body.getDescripcion());
        rolRepository.save(rol);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── MENÚS ────────────────────────────────────────────────────────────────

    @GetMapping("/menus")
    public ResponseEntity<List<Map<String, Object>>> listarMenusAdmin(
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        permisoAdmin(user);
        return ResponseEntity.ok(queryService.listarMenus());
    }

    @Data
    public static class MenuUpdateRequest {
        private String label;
        private Integer nivelMaximo;
        private Integer nivelMinimo;
        private Boolean activo;
    }

    @PatchMapping("/menus/{clave}")
    public ResponseEntity<Map<String, Object>> actualizarMenu(
            @PathVariable("clave") String clave,
            @RequestBody MenuUpdateRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        PermisoAdmin permiso = permisoAdmin(user);
        if (!permiso.esAdminGlobal())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo ADMIN_GLOBAL puede editar menús");
        return ResponseEntity.ok(queryService.actualizarMenu(clave, body.getLabel(),
                body.getNivelMaximo(), body.getNivelMinimo(), body.getActivo()));
    }

    // ── PERMISOS POR ROL ─────────────────────────────────────────────────────

    @GetMapping("/permisos-rol")
    public ResponseEntity<List<Map<String, Object>>> listarPermisosRol(
            @RequestParam(value = "rol_id", required = false) UUID rolId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        permisoAdmin(user);
        return ResponseEntity.ok(queryService.listarPermisosRol(rolId));
    }

    @Data
    public static class PermisoRolRequest {
        private UUID rolId;
        private String menuClave;
        private Boolean puedeVer;
        private Boolean puedeEditar;
        private Boolean puedeCrear;
        private Boolean puedeEliminar;
    }

    @PutMapping("/permisos-rol")
    public ResponseEntity<Map<String, Object>> upsertPermisoRol(
            @RequestBody PermisoRolRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        PermisoAdmin permiso = permisoAdmin(user);
        if (!permiso.esAdminGlobal())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo ADMIN_GLOBAL puede editar permisos");
        if (body.getRolId() == null || body.getMenuClave() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rolId y menuClave son requeridos");
        return ResponseEntity.ok(queryService.upsertPermisoRol(
                body.getRolId(), body.getMenuClave(),
                body.getPuedeVer(), body.getPuedeEditar(),
                body.getPuedeCrear(), body.getPuedeEliminar()));
    }
}
