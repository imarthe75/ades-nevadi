package mx.ades.modules.conducta;

import mx.ades.modules.conducta.domain.model.TipoFalta;
import mx.ades.modules.conducta.domain.model.TipoSancion;
import mx.ades.modules.planeacion.domain.model.EstadoTema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class ConductaDomainTest {

    // ── TipoFalta ─────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0}.requiereSeguimiento={1}")
    @CsvSource({"LEVE, false", "GRAVE, true", "MUY_GRAVE, true"})
    void tipoFalta_requiereSeguimiento(TipoFalta tipo, boolean esperado) {
        assertEquals(esperado, tipo.requiereSeguimiento());
    }

    // ── TipoSancion ───────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0}.requiereNotificacionPadres={1}")
    @CsvSource({
            "AMONESTACION_VERBAL, false",
            "AMONESTACION_ESCRITA, false",
            "CITATORIO_PADRES, false",
            "ASISTENCIA_PADRES, true",
            "SUSPENSION, true",
            "EXPULSION, true",
    })
    void tipoSancion_requiereNotificacion(TipoSancion tipo, boolean esperado) {
        assertEquals(esperado, tipo.requiereNotificacionPadres());
    }

    // ── EstadoTema (planeación) ───────────────────────────────────────────────

    @Test
    void estadoTema_from_sinDatos_esPENDIENTE() {
        assertEquals(EstadoTema.PENDIENTE, EstadoTema.from(false, false));
    }

    @Test
    void estadoTema_from_conPlaneacion_esPLANEADO() {
        assertEquals(EstadoTema.PLANEADO, EstadoTema.from(false, true));
    }

    @Test
    void estadoTema_from_conAvanceCompletado_esIMPARTIDO() {
        assertEquals(EstadoTema.IMPARTIDO, EstadoTema.from(true, true));
    }

    @ParameterizedTest(name = "{0}.puedeAvanzarA({1})={2}")
    @CsvSource({
            "PENDIENTE, PLANEADO, true",
            "PENDIENTE, IMPARTIDO, false",
            "PLANEADO, IMPARTIDO, true",
            "PLANEADO, PENDIENTE, false",
            "IMPARTIDO, PLANEADO, false",
            "IMPARTIDO, PENDIENTE, false",
    })
    void estadoTema_transiciones(EstadoTema origen, EstadoTema destino, boolean esperado) {
        assertEquals(esperado, origen.puedeAvanzarA(destino));
    }
}
