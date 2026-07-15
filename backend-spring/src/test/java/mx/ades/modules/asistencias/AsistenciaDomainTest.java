package mx.ades.modules.asistencias;

import mx.ades.modules.asistencias.domain.model.Asistencia;
import mx.ades.modules.asistencias.domain.model.EstatusAsistencia;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AsistenciaDomainTest {

    private final UUID claseId      = UUID.randomUUID();
    private final UUID estudianteId = UUID.randomUUID();

    // ── Factory method ────────────────────────────────────────────────────────

    @Test
    void registrar_conEstatusNull_debePorDefectoAUSENTE() {
        Asistencia a = Asistencia.registrar(claseId, estudianteId, null, null);
        assertEquals(EstatusAsistencia.AUSENTE, a.estatus());
    }

    @Test
    void registrar_debeGenerarIdNonNull() {
        Asistencia a = Asistencia.registrar(claseId, estudianteId, EstatusAsistencia.PRESENTE, null);
        assertNotNull(a.id());
    }

    @Test
    void registrar_conClaseIdNull_debeLanzarExcepcion() {
        assertThrows(NullPointerException.class,
                () -> Asistencia.registrar(null, estudianteId, EstatusAsistencia.PRESENTE, null));
    }

    @Test
    void registrar_conEstudianteIdNull_debeLanzarExcepcion() {
        assertThrows(NullPointerException.class,
                () -> Asistencia.registrar(claseId, null, EstatusAsistencia.PRESENTE, null));
    }

    // ── Regla 80 % SEP/UAEMEX ────────────────────────────────────────────────

    @ParameterizedTest(name = "total={0}, asistidas={1} → acredita={2}")
    @CsvSource({
            "10, 10, true",   // 100 %
            "10,  8, true",   // 80 % — justo el límite
            "10,  7, false",  // 70 % — no acredita
            "10,  0, false",  // 0 %
            " 0,  0, true",   // sin clases → acredita (sin datos)
            "20, 16, true",   // 80 % en 20 clases
            "20, 15, false",  // 75 %
    })
    void acreditaAsistencia_regla80pct(long total, long asistidas, boolean esperado) {
        assertEquals(esperado, Asistencia.acreditaAsistencia(total, asistidas));
    }

    // ── EstatusAsistencia helpers ─────────────────────────────────────────────

    @Test
    void cuentaComoAsistencia_PRESENTE_y_TARDE_y_JUSTIFICADO() {
        assertTrue(EstatusAsistencia.PRESENTE.cuentaComoAsistencia());
        assertTrue(EstatusAsistencia.TARDE.cuentaComoAsistencia());
        assertTrue(EstatusAsistencia.JUSTIFICADO.cuentaComoAsistencia());
        assertFalse(EstatusAsistencia.AUSENTE.cuentaComoAsistencia());
    }
}
