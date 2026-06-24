package mx.ades.modules.imports.query;

import mx.ades.modules.imports.domain.model.TipoEntidadImport;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Servicio de lectura CQRS para el módulo imports.
 * Genera metadatos de plantillas CSV para importación masiva de alumnos, profesores,
 * materias, grupos, aulas y pre-inscritos SEP.
 *
 * @author ADES
 * @since 2026
 */
@Service
public class ImportQueryService {

    private static final char CSV_SEP = ',';

    public record PlantillaInfo(
            TipoEntidadImport tipo,
            String nombreArchivo,
            String encabezado,
            String filaDemostracion
    ) {}

    public PlantillaInfo obtenerPlantilla(String entidad) {
        TipoEntidadImport tipo = TipoEntidadImport.ofClave(entidad);
        String encabezado = String.join(String.valueOf(CSV_SEP), tipo.columnasPlantilla());
        String demo = ejemploPara(tipo);
        return new PlantillaInfo(tipo, entidad + "_plantilla.csv", encabezado, demo);
    }

    public List<TipoEntidadImport> listarEntidades() {
        return Arrays.asList(TipoEntidadImport.values());
    }

    // ── filas de demostración ─────────────────────────────────────────────────

    private String ejemploPara(TipoEntidadImport tipo) {
        return switch (tipo) {
            case ALUMNOS ->
                    "Juan,Pérez,Gómez,PEGJ050101HMCRNS09,PEGJ050101AB1,M,2005-01-01," +
                    "5551234567,juan.perez@email.com,MEXICANA,TEN001,2026-09-01,12345678901," +
                    "Escuela Primaria Juárez,15EPR0123A,9.5,Beca SEP,500.00,FOLIO-2026-001,NUEVO";
            case PROFESORES ->
                    "María,López,Sánchez,LOSM800202MMCPNR05,LOSM800202XY2,F,1980-02-02," +
                    "5559876543,maria.lopez@email.com,EMP001,TIEMPO_COMPLETO,TEN001,98765432109," +
                    "1234567,Matemáticas,LICENCIATURA,2015-08-01";
            case MATERIAS ->
                    "Matemáticas I,MAT101,Preparatoria,5";
            case GRUPOS ->
                    "1A,MATUTINO,30,Primero,2026-2027";
            case AULAS ->
                    "Aula 101,A-101,SALON,35,MET001,Edificio A,1,true,false,true,false,true,5,ACTIVA,Uso general";
            case PREINSCRITOS_SEP ->
                    "Carlos,Ramírez,Torres,RATC120301HMCMRL09,2012-03-01,Primaria,1,TEN001,2026-2027," +
                    "Pedro Ramírez,5551234567,pedro@email.com,Escuela Primaria Juárez,9.5";
        };
    }
}
