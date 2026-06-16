package mx.ades.modules.imports;

import mx.ades.modules.imports.domain.model.TipoEntidadImport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ImportsDomainTest {

    // ── clave() ──────────────────────────────────────────────────────────────

    @Test
    void clave_convierte_nombre_a_kebab_case() {
        assertThat(TipoEntidadImport.PREINSCRITOS_SEP.clave()).isEqualTo("preinscritos-sep");
        assertThat(TipoEntidadImport.ALUMNOS.clave()).isEqualTo("alumnos");
    }

    // ── ofClave() ─────────────────────────────────────────────────────────────

    @Test
    void ofClave_resuelve_por_clave_kebab() {
        assertThat(TipoEntidadImport.ofClave("alumnos")).isEqualTo(TipoEntidadImport.ALUMNOS);
        assertThat(TipoEntidadImport.ofClave("preinscritos-sep")).isEqualTo(TipoEntidadImport.PREINSCRITOS_SEP);
        assertThat(TipoEntidadImport.ofClave("aulas")).isEqualTo(TipoEntidadImport.AULAS);
    }

    @Test
    void ofClave_resuelve_por_nombre_enum_case_insensitive() {
        assertThat(TipoEntidadImport.ofClave("ALUMNOS")).isEqualTo(TipoEntidadImport.ALUMNOS);
        assertThat(TipoEntidadImport.ofClave("Profesores")).isEqualTo(TipoEntidadImport.PROFESORES);
    }

    @Test
    void ofClave_lanza_excepcion_para_entidad_desconocida() {
        assertThatIllegalArgumentException().isThrownBy(() -> TipoEntidadImport.ofClave("desconocida"))
                .withMessageContaining("desconocida");
    }

    // ── permitePara() ─────────────────────────────────────────────────────────

    @Test
    void alumnos_permite_niveles_1_y_2() {
        assertThat(TipoEntidadImport.ALUMNOS.permitePara(1)).isTrue();
        assertThat(TipoEntidadImport.ALUMNOS.permitePara(2)).isTrue();
        assertThat(TipoEntidadImport.ALUMNOS.permitePara(3)).isFalse();
    }

    @Test
    void aulas_permite_hasta_nivel_3_coordinador() {
        assertThat(TipoEntidadImport.AULAS.permitePara(1)).isTrue();
        assertThat(TipoEntidadImport.AULAS.permitePara(3)).isTrue();
        assertThat(TipoEntidadImport.AULAS.permitePara(4)).isFalse();
    }

    // ── reglas de dominio ─────────────────────────────────────────────────────

    @Test
    void alumnos_profesores_preincritos_tienen_validacion_curp() {
        assertThat(TipoEntidadImport.ALUMNOS.tieneValidacionCurp()).isTrue();
        assertThat(TipoEntidadImport.PROFESORES.tieneValidacionCurp()).isTrue();
        assertThat(TipoEntidadImport.PREINSCRITOS_SEP.tieneValidacionCurp()).isTrue();
        assertThat(TipoEntidadImport.MATERIAS.tieneValidacionCurp()).isFalse();
        assertThat(TipoEntidadImport.GRUPOS.tieneValidacionCurp()).isFalse();
    }

    @Test
    void materias_no_requiere_plantel() {
        assertThat(TipoEntidadImport.MATERIAS.requierePlantel()).isFalse();
        assertThat(TipoEntidadImport.ALUMNOS.requierePlantel()).isTrue();
        assertThat(TipoEntidadImport.AULAS.requierePlantel()).isTrue();
    }

    @Test
    void campos_obligatorios_no_vacios() {
        for (TipoEntidadImport t : TipoEntidadImport.values()) {
            assertThat(t.camposObligatorios()).isNotEmpty();
            assertThat(t.columnasPlantilla()).isNotEmpty();
        }
    }
}
