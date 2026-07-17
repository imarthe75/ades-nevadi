package mx.ades.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cubre {@link AdesUserService#verificarPlantel} y {@link AdesUserService#getEffectivePlantelId},
 * el punto exacto donde han salido la mayoría de los huecos BOLA/BFLA encontrados en las
 * auditorías de 2026-07-14 a 2026-07-16 (umbral de scoping por plantel inconsistente entre
 * controllers). No se prueba {@code resolveUser} aquí porque depende de JDBC/JWT reales — eso
 * es terreno de test de integración, no de este unit test.
 * <p>
 * Las dependencias del constructor ({@code UsuarioRepository}, {@code RolRepository},
 * {@code JdbcTemplate}) se pasan como {@code null} a propósito: ninguno de los dos métodos
 * bajo prueba las toca (ambos son lógica pura sobre el {@link AdesUser} ya resuelto), así que
 * mockearlas solo añadiría ruido sin aportar nada a la prueba.
 */
class AdesUserServiceTest {

    private final AdesUserService service = new AdesUserService(null, null, null);

    private static AdesUser usuario(Integer nivelAcceso, UUID plantelId) {
        return AdesUser.builder()
                .id(UUID.randomUUID())
                .nivelAcceso(nivelAcceso)
                .plantelId(plantelId)
                .build();
    }

    // ── getEffectivePlantelId ────────────────────────────────────────────────

    @Test
    void admin_global_nivel_0_usa_el_plantel_solicitado_aunque_no_coincida_con_el_suyo() {
        UUID propio = UUID.randomUUID();
        UUID solicitado = UUID.randomUUID();
        AdesUser admin = usuario(0, propio);

        assertThat(service.getEffectivePlantelId(admin, solicitado)).isEqualTo(solicitado);
    }

    @Test
    void admin_global_nivel_0_puede_solicitar_null_para_ver_todos_los_planteles() {
        AdesUser admin = usuario(0, UUID.randomUUID());

        assertThat(service.getEffectivePlantelId(admin, null)).isNull();
    }

    @Test
    void no_admin_ignora_el_plantel_solicitado_y_usa_siempre_el_propio() {
        UUID propio = UUID.randomUUID();
        UUID solicitadoPorOtroPlantel = UUID.randomUUID();
        AdesUser docente = usuario(1, propio);

        assertThat(service.getEffectivePlantelId(docente, solicitadoPorOtroPlantel)).isEqualTo(propio);
    }

    @Test
    void no_admin_sin_plantel_propio_asignado_cae_al_solicitado() {
        UUID solicitado = UUID.randomUUID();
        AdesUser sinPlantel = usuario(2, null);

        assertThat(service.getEffectivePlantelId(sinPlantel, solicitado)).isEqualTo(solicitado);
    }

    @Test
    void nivel_acceso_null_cae_al_plantel_solicitado() {
        UUID solicitado = UUID.randomUUID();
        AdesUser sinNivel = usuario(null, UUID.randomUUID());

        assertThat(service.getEffectivePlantelId(sinNivel, solicitado)).isEqualTo(solicitado);
    }

    // ── verificarPlantel ─────────────────────────────────────────────────────

    @Test
    void admin_global_nivel_0_nunca_lanza_sin_importar_el_plantel_de_la_entidad() {
        AdesUser admin = usuario(0, UUID.randomUUID());

        assertThatCode(() -> service.verificarPlantel(admin, UUID.randomUUID(), "no debería lanzar"))
                .doesNotThrowAnyException();
    }

    @Test
    void nivel_acceso_null_no_lanza_por_disenio_defensivo() {
        AdesUser sinNivel = usuario(null, UUID.randomUUID());

        assertThatCode(() -> service.verificarPlantel(sinNivel, UUID.randomUUID(), "no debería lanzar"))
                .doesNotThrowAnyException();
    }

    @Test
    void no_admin_sin_plantel_propio_no_lanza_responsabilidad_del_llamador() {
        AdesUser sinPlantel = usuario(1, null);

        assertThatCode(() -> service.verificarPlantel(sinPlantel, UUID.randomUUID(), "no debería lanzar"))
                .doesNotThrowAnyException();
    }

    @Test
    void plantel_de_entidad_null_no_lanza_responsabilidad_del_llamador_verificar_404() {
        AdesUser docente = usuario(1, UUID.randomUUID());

        assertThatCode(() -> service.verificarPlantel(docente, null, "no debería lanzar"))
                .doesNotThrowAnyException();
    }

    @Test
    void mismo_plantel_no_lanza() {
        UUID plantel = UUID.randomUUID();
        AdesUser docente = usuario(1, plantel);

        assertThatCode(() -> service.verificarPlantel(docente, plantel, "no debería lanzar"))
                .doesNotThrowAnyException();
    }

    @Test
    void plantel_distinto_lanza_403_con_el_mensaje_provisto() {
        AdesUser docenteDelPlantelA = usuario(1, UUID.randomUUID());
        UUID plantelB = UUID.randomUUID();

        assertThatThrownBy(() ->
                service.verificarPlantel(docenteDelPlantelA, plantelB, "Sin acceso a este plantel"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).isEqualTo("Sin acceso a este plantel");
                });
    }
}
