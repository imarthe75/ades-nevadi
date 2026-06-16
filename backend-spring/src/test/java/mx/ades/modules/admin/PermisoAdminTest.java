package mx.ades.modules.admin;

import mx.ades.modules.admin.domain.model.PermisoAdmin;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PermisoAdminTest {

    @Test
    void admin_global_nivel_0_es_admin_global() {
        PermisoAdmin p = new PermisoAdmin(0);
        assertThat(p.esAdminGlobal()).isTrue();
        assertThat(p.esAdmin()).isTrue();
    }

    @Test
    void admin_plantel_nivel_1_es_admin_pero_no_global() {
        PermisoAdmin p = new PermisoAdmin(1);
        assertThat(p.esAdminGlobal()).isFalse();
        assertThat(p.esAdmin()).isTrue();
    }

    @Test
    void nivel_2_no_es_admin() {
        PermisoAdmin p = new PermisoAdmin(2);
        assertThat(p.esAdmin()).isFalse();
    }

    @Test
    void puede_asignar_rol_mismo_nivel() {
        PermisoAdmin admin = new PermisoAdmin(1);
        assertThat(admin.puedeAsignarRol(1)).isTrue();
        assertThat(admin.puedeAsignarRol(2)).isTrue();
    }

    @Test
    void no_puede_asignar_rol_mayor_jerarquia() {
        PermisoAdmin admin = new PermisoAdmin(1);
        assertThat(admin.puedeAsignarRol(0)).isFalse();
    }

    @Test
    void admin_global_puede_editar_otros_planteles() {
        PermisoAdmin global = new PermisoAdmin(0);
        assertThat(global.puedeEditarOtrosPlantelUsuarios()).isTrue();
    }

    @Test
    void admin_plantel_no_puede_editar_otros_planteles() {
        PermisoAdmin plantel = new PermisoAdmin(1);
        assertThat(plantel.puedeEditarOtrosPlantelUsuarios()).isFalse();
    }
}
