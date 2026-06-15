package mx.ades.shared.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class NivelTipoTest {

    @ParameterizedTest
    @CsvSource({"PRIMARIA,true", "SECUNDARIA,true", "PREPARATORIA,false"})
    void esSEP_debeDevolverCorrecto(String nombre, boolean esperado) {
        NivelTipo nivel = NivelTipo.fromNombre(nombre);
        assertEquals(esperado, nivel.esSEP());
    }

    @Test
    void fromNombre_enMayusculasYMinusculas_debeResolverIgual() {
        assertEquals(NivelTipo.PRIMARIA, NivelTipo.fromNombre("primaria"));
        assertEquals(NivelTipo.SECUNDARIA, NivelTipo.fromNombre("Secundaria"));
        assertEquals(NivelTipo.PREPARATORIA, NivelTipo.fromNombre("PREPARATORIA"));
    }

    @Test
    void fromNombre_desconocido_debeLanzarExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> NivelTipo.fromNombre("BACHILLERATO"));
    }
}
