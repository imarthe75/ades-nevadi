package mx.ades.modules.calificaciones;

import mx.ades.modules.calificaciones.domain.model.Calificacion;
import mx.ades.modules.calificaciones.domain.model.EstatusPromocion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CalificacionDomainTest {

    private final UUID estudianteId = UUID.randomUUID();
    private final UUID materiaId    = UUID.randomUUID();
    private final UUID periodoId    = UUID.randomUUID();

    @Test
    void calificacion_acreditada_debeReportarAPROBADO() {
        var c = new Calificacion(UUID.randomUUID(), estudianteId, null, materiaId, periodoId,
                new BigDecimal("8.5"), true, null);
        assertEquals(EstatusPromocion.APROBADO, c.estatusPromocion());
        assertFalse(c.estatusPromocion().esReprobado());
    }

    @Test
    void calificacion_noAcreditada_debeReportarREPROBADO() {
        var c = new Calificacion(UUID.randomUUID(), estudianteId, null, materiaId, periodoId,
                new BigDecimal("4.9"), false, null);
        assertEquals(EstatusPromocion.REPROBADO, c.estatusPromocion());
        assertTrue(c.estatusPromocion().esReprobado());
    }

    @ParameterizedTest(name = "esAcreditado={0} → {1}")
    @CsvSource({"true, APROBADO", "false, REPROBADO"})
    void estatusPromocion_from_esAcreditado(boolean esAcreditado, EstatusPromocion esperado) {
        assertEquals(esperado, EstatusPromocion.from(esAcreditado));
    }

    @Test
    void calificacion_conEstudianteIdNull_debeLanzarExcepcion() {
        assertThrows(NullPointerException.class,
                () -> new Calificacion(null, null, null, materiaId, periodoId,
                        BigDecimal.TEN, false, null));
    }
}
