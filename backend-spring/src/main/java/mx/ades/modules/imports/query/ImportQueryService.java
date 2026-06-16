package mx.ades.modules.imports.query;

import mx.ades.modules.imports.domain.model.TipoEntidadImport;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

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
                    "Juan,Pérez,Gómez,PEGJ050101HMCRNS09,M,2005-01-01,2026-09-01,TEN001";
            case PROFESORES ->
                    "María,López,Sánchez,LOSM800202MMCPNR05,F,1980-02-02,EMP001,TIEMPO_COMPLETO,TEN001";
            case MATERIAS ->
                    "Matemáticas I,MAT101,Preparatoria,5";
            case GRUPOS ->
                    "1A,MATUTINO,30,Primero,2026-2027";
            case AULAS ->
                    "Aula 101,SALON,35,MET001,true,false,true,Uso general";
            case PREINSCRITOS_SEP ->
                    "Carlos,Ramírez,Torres,RATC120301HMCMRL09,2012-03-01,Primaria,1,TEN001,2026-2027," +
                    "Pedro Ramírez,5551234567,pedro@email.com,Escuela Primaria Juárez,9.5";
        };
    }
}
