package mx.ades.security;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/**
 * Representa la identidad del usuario autenticado dentro del dominio de ADES,
 * una vez resuelto desde el JWT de Authentik por {@code AdesUserService#resolveUser}.
 * <p>
 * Encapsula tanto la identidad OIDC ({@code id}, {@code username}, {@code email})
 * como el contexto escolar del usuario: plantel, nivel educativo, grado y grupo
 * activos, así como su nivel de acceso (1=alumno … 5=admin global) que gobierna
 * el scoping y las autorizaciones en toda la lógica de negocio.
 * </p>
 * <p>
 * <strong>Obligatorio:</strong> todo endpoint del BFF debe llamar a
 * {@code AdesUserService#resolveUser(Jwt)} y verificar {@code nivelAcceso} antes
 * de operar datos. Nunca pasar datos sin autenticación resuelta.
 * </p>
 *
 * @author ADES
 * @since 2026
 */
@Getter
@Builder
public class AdesUser {
    private final UUID id;
    private final String username;
    private final String email;
    private final UUID personaId;
    private final UUID plantelId;
    private final UUID nivelEducativoId;
    private final UUID gradoId;
    private final UUID grupoId;
    private final String nombreGrado;
    private final String nombreGrupo;
    private final String nombreCompleto;
    private final String nombrePlantel;
    private final String nombreNivel;
    private final UUID rolPrincipalId;
    private final List<String> roles;
    private final Integer nivelAcceso;
}

