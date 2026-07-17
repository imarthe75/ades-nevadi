package mx.ades.security;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.usuarios.Rol;
import mx.ades.modules.usuarios.RolRepository;
import mx.ades.modules.usuarios.Usuario;
import mx.ades.modules.usuarios.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdesUserService {

    private final UsuarioRepository usuarioRepo;
    private final RolRepository rolRepo;
    private final JdbcTemplate jdbc;

    @Transactional
    public AdesUser resolveUser(Jwt jwt) {
        String sub = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String username = jwt.getClaimAsString("preferred_username");

        Usuario usuario = usuarioRepo
                .findByOidcSubOrEmailOrUsername(sub, email, username)
                .orElseThrow(() -> {
                    System.out.println("USER NOT FOUND IN DB. sub=" + sub + ", email=" + email + ", username=" + username);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Usuario no registrado en ADES. Contacta al administrador.");
                });

        List<String> jwtGroups = jwt.getClaimAsStringList("groups");
        if (jwtGroups == null || jwtGroups.isEmpty()) {
            jwtGroups = jwt.getClaimAsStringList("roles");
        }
        if (jwtGroups != null && !jwtGroups.isEmpty()) {
            syncRoles(usuario.getId(), jwtGroups);
        }

        return buildAdesUser(usuario);
    }

    private void syncRoles(UUID userId, List<String> jwtGroups) {
        List<UUID> dbRoleIds = jdbc.queryForList(
                "SELECT rol_id FROM ades_usuario_roles WHERE usuario_id = ?",
                UUID.class, userId);
        List<UUID> jwtRoleIds = rolRepo.findByNombreRolIn(jwtGroups)
                .stream().map(Rol::getId).toList();

        if (!new HashSet<>(dbRoleIds).equals(new HashSet<>(jwtRoleIds))) {
            jdbc.update("DELETE FROM ades_usuario_roles WHERE usuario_id = ?", userId);
            jwtRoleIds.forEach(rid ->
                    jdbc.update("INSERT INTO ades_usuario_roles (usuario_id, rol_id, peso) VALUES (?,?,100)",
                            userId, rid));
        }
    }

    /**
     * Devuelve el plantel_id efectivo para queries de datos sensibles.
     * - nivel_acceso = 0 (ADMIN_GLOBAL): usa el plantelId del request (puede ser null = todos)
     * - nivel_acceso &gt; 0: fuerza el plantel del usuario — no puede ver datos de otros planteles
     * <p>
     * (Corregido 2026-07-16 — decisión explícita del usuario: el umbral original
     * {@code > 1} trataba nivel_acceso 1 = ADMIN_PLANTEL como "superadmin" con alcance
     * institucional libre, contradiciendo tanto la descripción del rol en
     * {@code db/seeds/001_datos_base.sql} ("Administrador de un plantel específico")
     * como {@code PermisoAdmin.puedeEditarOtrosPlantelUsuarios()}, que ya restringe la
     * operación cross-plantel a solo nivel_acceso 0. Este método es el punto central
     * usado por ~10 controladores — AlumnoController, BienestarController,
     * AsignacionDocenteController, LearningPathsController, MedicoController,
     * MovilidadController, PersonalAdminController, PortalController,
     * ProfesorController, StatsController — así que corregirlo aquí propaga el fix a
     * todos ellos sin tocar cada uno.
     */
    public UUID getEffectivePlantelId(AdesUser user, UUID requestedPlantelId) {
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 0 && user.getPlantelId() != null) {
            return user.getPlantelId();
        }
        return requestedPlantelId;
    }

    /**
     * BOLA fix compartido (2026-07-16): verifica que el plantel de una entidad — ya
     * resuelto por el llamador via su propio QueryService/JdbcTemplate, cada módulo
     * conserva su propia forma de averiguarlo — coincida con el plantel del usuario
     * autenticado, con un umbral único y consistente: solo nivelAcceso 0
     * (ADMIN_GLOBAL) mantiene alcance institucional real; TODO el resto de roles
     * (1=ADMIN_PLANTEL, 2=DIRECTOR, 3=COORDINADOR_ACADEMICO, 4=DOCENTE, ... —
     * db/seeds/001_datos_base.sql) está acotado a su propio plantel.
     * <p>
     * Antes de este helper cada controlador reimplementaba esta comparación con su
     * propio umbral copiado a mano — y en la práctica cada uno usaba uno distinto
     * ({@code == 3}, {@code <= 1}, {@code <= 2}, {@code > 2}...), dejando roles
     * explícitamente plantel-acotados sin restricción en unos módulos sí y en otros
     * no (hallazgo code-review 2026-07-16). Centralizar solo la decisión de
     * autorización aquí — no el query de resolución del plantel de la entidad, que
     * varía demasiado entre módulos (JOINs, tablas distintas) — hace que el próximo
     * endpoint que llame a este método reciba el umbral correcto por defecto.
     *
     * @param user             usuario autenticado
     * @param plantelEntidadId plantel_id real de la entidad consultada, o {@code null}
     *                         si no se pudo resolver (en cuyo caso no se aplica el
     *                         chequeo — responsabilidad del llamador lanzar 404 si
     *                         la entidad no existe)
     * @param mensajeError     mensaje del 403 si el plantel no coincide
     */
    public void verificarPlantel(AdesUser user, UUID plantelEntidadId, String mensajeError) {
        if (user.getNivelAcceso() == null || user.getNivelAcceso() == 0 || user.getPlantelId() == null) {
            return;
        }
        if (plantelEntidadId != null && !user.getPlantelId().equals(plantelEntidadId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, mensajeError);
        }
    }

    /**
     * BOLA fix compartido (2026-07-17): extraído tras encontrar el mismo patrón
     * copiado a mano en 6 controllers ({@code ConductaController},
     * {@code GradeAnalyticsController}, {@code ActaController} (vía
     * {@code ActaQueryService.plantelDeGrupo}), {@code LearningPathsController},
     * {@code PlanesEstudioController}, {@code AsignacionDocenteController}) — cada
     * uno con su propia copia de {@code SELECT gr.plantel_id FROM ades_grupos g
     * JOIN ades_grados gr ON gr.id = g.grado_id WHERE g.id = ?} + chequeo de
     * {@link #verificarPlantel}. Centralizar aquí evita que el próximo endpoint
     * que necesite "verificar que este grupo es de mi plantel" reintroduzca el
     * mismo bug BOLA por copy-paste que ya pasó una vez en esta cola (ver
     * {@code docs/hallazgos/2026-07-16_auditoria_gaps_no_revisados.md}).
     *
     * @param user         usuario autenticado
     * @param grupoId      id del grupo a verificar
     * @param mensajeError mensaje del 403 si el grupo no pertenece a su plantel
     * @throws ResponseStatusException 404 si el grupo no existe, 403 si es de otro plantel
     */
    public void verificarAccesoGrupo(AdesUser user, UUID grupoId, String mensajeError) {
        List<UUID> plantelRows = jdbc.queryForList(
                "SELECT gr.plantel_id FROM ades_grupos g " +
                "JOIN ades_grados gr ON gr.id = g.grado_id " +
                "WHERE g.id = ?", UUID.class, grupoId);
        if (plantelRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo no encontrado");
        }
        verificarPlantel(user, plantelRows.get(0), mensajeError);
    }

    /** Variante con mensaje de error por defecto de {@link #verificarAccesoGrupo(AdesUser, UUID, String)}. */
    public void verificarAccesoGrupo(AdesUser user, UUID grupoId) {
        verificarAccesoGrupo(user, grupoId, "El grupo no pertenece a su plantel");
    }

    private AdesUser buildAdesUser(Usuario usuario) {
        List<String> roles = jdbc.queryForList(
                "SELECT r.nombre_rol FROM ades_roles r " +
                        "JOIN ades_usuario_roles ur ON ur.rol_id = r.id " +
                        "WHERE ur.usuario_id = ?",
                String.class, usuario.getId());

        if (roles.isEmpty() && usuario.getRol() != null) {
            roles = List.of(usuario.getRol().getNombreRol());
        }

        String nombreCompleto = null;
        if (usuario.getPersonaId() != null) {
            try {
                nombreCompleto = jdbc.queryForObject(
                        "SELECT TRIM(CONCAT(nombre, ' ', apellido_paterno, ' ', COALESCE(apellido_materno, ''))) FROM ades_personas WHERE id = ?",
                        String.class, usuario.getPersonaId());
            } catch (Exception e) {}
        }

        // Resuelve ades_profesores.id para el docente autenticado — distinto de
        // persona_id (ver hallazgo 2026-07-16: comparar personaId contra columnas
        // docente_id/profesor_id/personal_id, que en realidad referencian
        // ades_profesores.id, bloqueaba el autoservicio de todo docente real).
        UUID profesorId = null;
        if (usuario.getPersonaId() != null) {
            try {
                profesorId = jdbc.queryForObject(
                        "SELECT id FROM ades_profesores WHERE persona_id = ?",
                        UUID.class, usuario.getPersonaId());
            } catch (Exception e) {}
        }

        String nombrePlantel = null;
        if (usuario.getPlantelId() != null) {
            try {
                nombrePlantel = jdbc.queryForObject(
                        "SELECT nombre_plantel FROM ades_planteles WHERE id = ?",
                        String.class, usuario.getPlantelId());
            } catch (Exception e) {}
        }

        String nombreNivel = null;
        if (usuario.getNivelEducativoId() != null) {
            try {
                nombreNivel = jdbc.queryForObject(
                        "SELECT nombre_nivel FROM ades_niveles_educativos WHERE id = ?",
                        String.class, usuario.getNivelEducativoId());
            } catch (Exception e) {}
        }

        UUID grupoId = null;
        UUID gradoId = null;
        String nombreGrupo = null;
        String nombreGrado = null;

        if (roles.contains("ALUMNO")) {
            try {
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT i.grupo_id, g.grado_id, g.nombre_grupo, gr.nombre_grado " +
                        "FROM ades_inscripciones i " +
                        "JOIN ades_grupos g ON g.id = i.grupo_id " +
                        "JOIN ades_grados gr ON gr.id = g.grado_id " +
                        "JOIN ades_estudiantes est ON est.id = i.estudiante_id " +
                        "WHERE est.persona_id = ? AND i.is_active = TRUE LIMIT 1",
                        usuario.getPersonaId());
                if (!rows.isEmpty()) {
                    Map<String, Object> row = rows.get(0);
                    grupoId = (UUID) row.get("grupo_id");
                    gradoId = (UUID) row.get("grado_id");
                    nombreGrupo = (String) row.get("nombre_grupo");
                    nombreGrado = (String) row.get("nombre_grado");
                }
            } catch (Exception e) {}
        } else if (roles.contains("DOCENTE")) {
            try {
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT DISTINCT c.grupo_id, g.grado_id, g.nombre_grupo, gr.nombre_grado " +
                        "FROM ades_clases c " +
                        "JOIN ades_grupos g ON g.id = c.grupo_id " +
                        "JOIN ades_grados gr ON gr.id = g.grado_id " +
                        "JOIN ades_profesores prof ON prof.id = c.profesor_id " +
                        "WHERE prof.persona_id = ?",
                        usuario.getPersonaId());
                if (rows.size() == 1) {
                    Map<String, Object> row = rows.get(0);
                    grupoId = (UUID) row.get("grupo_id");
                    gradoId = (UUID) row.get("grado_id");
                    nombreGrupo = (String) row.get("nombre_grupo");
                    nombreGrado = (String) row.get("nombre_grado");
                }
            } catch (Exception e) {}
        }

        return AdesUser.builder()
                .id(usuario.getId())
                .username(usuario.getNombreUsuario())
                .email(usuario.getEmailInstitucional())
                .personaId(usuario.getPersonaId())
                .profesorId(profesorId)
                .plantelId(usuario.getPlantelId())
                .nivelEducativoId(usuario.getNivelEducativoId())
                .gradoId(gradoId)
                .grupoId(grupoId)
                .nombreGrado(nombreGrado)
                .nombreGrupo(nombreGrupo)
                .nombreCompleto(nombreCompleto)
                .nombrePlantel(nombrePlantel)
                .nombreNivel(nombreNivel)
                .rolPrincipalId(usuario.getRol() != null ? usuario.getRol().getId() : null)
                .roles(roles)
                .nivelAcceso(usuario.getRol() != null ? usuario.getRol().getNivelAcceso() : 99)
                .build();
    }
}
