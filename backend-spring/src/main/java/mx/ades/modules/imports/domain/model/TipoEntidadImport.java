package mx.ades.modules.imports.domain.model;

import java.util.Arrays;
import java.util.List;

public enum TipoEntidadImport {

    ALUMNOS(
            2,
            new String[]{"nombre", "curp"},
            new String[]{"nombre", "apellido_paterno", "apellido_materno", "curp", "rfc", "genero",
                    "fecha_nacimiento", "telefono", "email_personal", "nacionalidad",
                    "clave_plantel", "fecha_ingreso", "nss", "escuela_procedencia",
                    "clave_ct_procedencia", "promedio_procedencia", "beca_tipo", "beca_monto",
                    "folio_sep", "tipo_alumno",
                    // columnas opcionales de padre/tutor — si presentes se crea cuenta PADRE_FAMILIA
                    "nombre_padre", "apellido_paterno_padre", "apellido_materno_padre",
                    "curp_padre", "email_padre", "telefono_padre"}
    ),
    PROFESORES(
            2,
            new String[]{"nombre", "curp", "numero_empleado"},
            new String[]{"nombre", "apellido_paterno", "apellido_materno", "curp", "rfc", "genero",
                    "fecha_nacimiento", "telefono", "email_personal", "numero_empleado",
                    "tipo_contrato", "clave_plantel", "nss", "cedula_profesional",
                    "especialidad", "nivel_estudios", "fecha_ingreso_inst"}
    ),
    MATERIAS(
            2,
            new String[]{"nombre_materia", "clave_materia"},
            new String[]{"nombre_materia", "clave_materia", "nombre_nivel", "tipo_materia", "horas_semana"}
    ),
    GRUPOS(
            2,
            new String[]{"nombre_grupo", "nombre_grado", "nombre_ciclo"},
            new String[]{"nombre_grupo", "turno", "capacidad_maxima", "nombre_grado", "nombre_ciclo"}
    ),
    AULAS(
            3,
            new String[]{"nombre_aula", "clave_plantel"},
            new String[]{"nombre_aula", "clave_aula", "tipo_aula", "capacidad_alumnos", "clave_plantel",
                    "edificio", "piso", "tiene_proyector", "tiene_pizarra_digital", "tiene_pizarron",
                    "tiene_aire_acondicionado", "tiene_internet", "num_computadoras",
                    "estado_aula", "observaciones"}
    ),
    PREINSCRITOS_SEP(
            2,
            new String[]{"nombre", "curp", "nivel_solicitado"},
            new String[]{"nombre", "apellido_paterno", "apellido_materno", "curp", "fecha_nacimiento",
                    "nivel_solicitado", "grado_solicitado", "clave_plantel", "nombre_ciclo",
                    "nombre_tutor", "telefono_tutor", "email_tutor", "escuela_procedencia", "promedio_procedencia"}
    );

    private final int nivelAccesoMaximoPermitido;
    private final String[] camposObligatorios;
    private final String[] columnasPlantilla;

    TipoEntidadImport(int nivelAccesoMaximoPermitido, String[] camposObligatorios, String[] columnasPlantilla) {
        this.nivelAccesoMaximoPermitido = nivelAccesoMaximoPermitido;
        this.camposObligatorios = camposObligatorios;
        this.columnasPlantilla = columnasPlantilla;
    }

    /** true cuando el nivel_acceso del usuario es ≤ máximo permitido (menor = más privilegio). */
    public boolean permitePara(int nivelAccesoUsuario) {
        return nivelAccesoUsuario <= nivelAccesoMaximoPermitido;
    }

    public boolean tieneValidacionCurp() {
        return this == ALUMNOS || this == PROFESORES || this == PREINSCRITOS_SEP;
    }

    public boolean requierePlantel() {
        return this != MATERIAS;
    }

    public List<String> camposObligatorios() {
        return Arrays.asList(camposObligatorios);
    }

    public String[] columnasPlantilla() {
        return columnasPlantilla;
    }

    public String clave() {
        return name().toLowerCase().replace('_', '-');
    }

    public static TipoEntidadImport ofClave(String clave) {
        for (TipoEntidadImport t : values()) {
            if (t.clave().equals(clave) || t.name().equalsIgnoreCase(clave)) return t;
        }
        throw new IllegalArgumentException("Entidad de importación no válida: " + clave);
    }
}
