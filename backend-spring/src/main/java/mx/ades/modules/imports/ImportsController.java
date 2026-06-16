package mx.ades.modules.imports;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ades.modules.imports.domain.model.TipoEntidadImport;
import mx.ades.modules.imports.query.ImportQueryService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/v1/imports")
@RequiredArgsConstructor
@Slf4j
public class ImportsController {

    private final AdesUserService userService;
    private final ImportQueryService importQueryService;
    private final ImportsWriteService importWrite;

    private static final long MAX_FILE_BYTES = 10 * 1024 * 1024; // 10 MB

    @Data
    public static class ErrorFila {
        private final int fila;
        private final String dato;
        private final String error;
    }

    @Data
    @Builder
    public static class ImportResult {
        private String entidad;
        private int total;
        private int exitosos;
        private int errores;
        private List<ErrorFila> detalleErrores;
    }

    @GetMapping("/plantillas/{entidad}")
    public ResponseEntity<byte[]> descargarPlantilla(
            @PathVariable("entidad") String entidad,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        ImportQueryService.PlantillaInfo info;
        try {
            info = importQueryService.obtenerPlantilla(entidad);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }

        String csv = info.encabezado() + "\n" + info.filaDemostracion() + "\n";
        byte[] content = csv.getBytes(StandardCharsets.UTF_8);

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        responseHeaders.setContentDispositionFormData("attachment", info.nombreArchivo());

        return ResponseEntity.ok().headers(responseHeaders).body(content);
    }

    @GetMapping("/entidades")
    public ResponseEntity<List<Map<String, Object>>> listarEntidades(@AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        List<Map<String, Object>> result = new ArrayList<>();
        for (TipoEntidadImport t : importQueryService.listarEntidades()) {
            result.add(Map.of(
                    "clave", t.clave(),
                    "campos_obligatorios", t.camposObligatorios(),
                    "requiere_plantel", t.requierePlantel(),
                    "tiene_validacion_curp", t.tieneValidacionCurp()
            ));
        }
        return ResponseEntity.ok(result);
    }

    private void validarArchivo(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo no contiene datos");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo supera el límite de 10 MB");
        }
        String ext = getExtension(file.getOriginalFilename());
        if (!List.of("csv", "xlsx").contains(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de archivo no permitido: " + file.getContentType());
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int idx = filename.lastIndexOf('.');
        return idx == -1 ? "" : filename.substring(idx + 1).toLowerCase();
    }

    private UUID resolverPlantel(String clave, Map<String, UUID> plantelesClave, Map<String, UUID> plantelesNombre) {
        if (clave == null || clave.isBlank()) {
            return null;
        }
        UUID id = plantelesClave.get(clave);
        if (id != null) return id;

        id = plantelesNombre.get(clave.toLowerCase());
        if (id != null) return id;

        try {
            return UUID.fromString(clave);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /alumnos
    // ──────────────────────────────────────────────────────────────────────────
    @PostMapping("/alumnos")
    public ResponseEntity<ImportResult> importarAlumnos(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAcceso(user, TipoEntidadImport.ALUMNOS);

        validarArchivo(file);

        ImportadorUtil.ParsedFile parsed;
        try {
            parsed = ImportadorUtil.parseFile(file.getBytes(), file.getOriginalFilename());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error al parsear el archivo: " + e.getMessage());
        }

        if (parsed.getRows().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo no contiene datos");
        }

        Map<String, UUID> plantelesClave = importWrite.loadPlantelesByClave();
        Map<String, UUID> plantelesNombre = importWrite.loadPlantelesByNombre();
        UUID estatusId = importWrite.loadEstatusId("ESTUDIANTE", "INSCRITO");
        long seq = importWrite.countEstudiantes();

        List<ErrorFila> errores = new ArrayList<>();
        int exitosos = 0;

        for (int i = 0; i < parsed.getRows().size(); i++) {
            List<String> row = parsed.getRows().get(i);
            int rowNum = i + 2;
            String curp = ImportadorUtil.getCol(row, parsed.getHeaders(), "curp").toUpperCase();
            String nombre = ImportadorUtil.getCol(row, parsed.getHeaders(), "nombre");

            if (curp.isEmpty() || nombre.isEmpty()) {
                errores.add(new ErrorFila(rowNum, "fila " + rowNum, "'nombre' y 'curp' son obligatorios"));
                continue;
            }

            if (importWrite.existePersonaCurp(curp)) {
                errores.add(new ErrorFila(rowNum, curp, "CURP ya registrada"));
                continue;
            }

            String clavePlantel = ImportadorUtil.getCol(row, parsed.getHeaders(), "clave_plantel", "plantel", "clave_ct");
            UUID plantelId = resolverPlantel(clavePlantel, plantelesClave, plantelesNombre);
            if (plantelId == null) {
                errores.add(new ErrorFila(rowNum, curp, "Plantel no encontrado: '" + clavePlantel + "'"));
                continue;
            }

            seq++;
            String matricula = String.format("MAT-%06d", seq);

            try {
                importWrite.insertarAlumno(
                        nombre,
                        ImportadorUtil.getCol(row, parsed.getHeaders(), "apellido_paterno"),
                        ImportadorUtil.getCol(row, parsed.getHeaders(), "apellido_materno"),
                        curp,
                        ImportadorUtil.getCol(row, parsed.getHeaders(), "genero"),
                        ImportadorUtil.parseDate(ImportadorUtil.getCol(row, parsed.getHeaders(), "fecha_nacimiento")),
                        plantelId, matricula, estatusId, user.getUsername());
                exitosos++;
            } catch (Exception e) {
                log.error("Error inserting alumno row {}", rowNum, e);
                errores.add(new ErrorFila(rowNum, curp, e.getMessage()));
                seq--;
            }
        }

        return ResponseEntity.ok(ImportResult.builder()
                .entidad("alumnos")
                .total(parsed.getRows().size())
                .exitosos(exitosos)
                .errores(errores.size())
                .detalleErrores(errores)
                .build());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /profesores
    // ──────────────────────────────────────────────────────────────────────────
    @PostMapping("/profesores")
    public ResponseEntity<ImportResult> importarProfesores(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAcceso(user, TipoEntidadImport.PROFESORES);

        validarArchivo(file);

        ImportadorUtil.ParsedFile parsed;
        try {
            parsed = ImportadorUtil.parseFile(file.getBytes(), file.getOriginalFilename());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error al parsear el archivo: " + e.getMessage());
        }

        if (parsed.getRows().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo no contiene datos");
        }

        Map<String, UUID> plantelesClave = importWrite.loadPlantelesByClave();
        Map<String, UUID> plantelesNombre = importWrite.loadPlantelesByNombre();
        UUID estatusId = importWrite.loadEstatusId("PROFESOR", "ACTIVO");

        List<ErrorFila> errores = new ArrayList<>();
        int exitosos = 0;

        for (int i = 0; i < parsed.getRows().size(); i++) {
            List<String> row = parsed.getRows().get(i);
            int rowNum = i + 2;
            String curp = ImportadorUtil.getCol(row, parsed.getHeaders(), "curp").toUpperCase();
            String nombre = ImportadorUtil.getCol(row, parsed.getHeaders(), "nombre");
            String numEmp = ImportadorUtil.getCol(row, parsed.getHeaders(), "numero_empleado", "num_empleado", "empleado");

            if (curp.isEmpty() || nombre.isEmpty() || numEmp.isEmpty()) {
                errores.add(new ErrorFila(rowNum, "fila " + rowNum, "'nombre', 'curp' y 'numero_empleado' son obligatorios"));
                continue;
            }

            if (importWrite.existePersonaCurp(curp)) {
                errores.add(new ErrorFila(rowNum, curp, "CURP ya registrada"));
                continue;
            }

            String clavePlantel = ImportadorUtil.getCol(row, parsed.getHeaders(), "clave_plantel", "plantel", "clave_ct");
            UUID plantelId = resolverPlantel(clavePlantel, plantelesClave, plantelesNombre);
            if (plantelId == null) {
                errores.add(new ErrorFila(rowNum, curp, "Plantel no encontrado: '" + clavePlantel + "'"));
                continue;
            }

            String tipoContrato = ImportadorUtil.getCol(row, parsed.getHeaders(), "tipo_contrato", "contrato");
            if (tipoContrato.isEmpty()) tipoContrato = "BASE";

            try {
                importWrite.insertarProfesor(
                        nombre,
                        ImportadorUtil.getCol(row, parsed.getHeaders(), "apellido_paterno"),
                        ImportadorUtil.getCol(row, parsed.getHeaders(), "apellido_materno"),
                        curp,
                        ImportadorUtil.getCol(row, parsed.getHeaders(), "genero"),
                        ImportadorUtil.parseDate(ImportadorUtil.getCol(row, parsed.getHeaders(), "fecha_nacimiento")),
                        plantelId, numEmp, tipoContrato, estatusId, user.getUsername());
                exitosos++;
            } catch (Exception e) {
                log.error("Error inserting profesor row {}", rowNum, e);
                errores.add(new ErrorFila(rowNum, curp, e.getMessage()));
            }
        }

        return ResponseEntity.ok(ImportResult.builder()
                .entidad("profesores")
                .total(parsed.getRows().size())
                .exitosos(exitosos)
                .errores(errores.size())
                .detalleErrores(errores)
                .build());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /materias
    // ──────────────────────────────────────────────────────────────────────────
    @PostMapping("/materias")
    public ResponseEntity<ImportResult> importarMaterias(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAcceso(user, TipoEntidadImport.MATERIAS);

        validarArchivo(file);

        ImportadorUtil.ParsedFile parsed;
        try {
            parsed = ImportadorUtil.parseFile(file.getBytes(), file.getOriginalFilename());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error al parsear el archivo: " + e.getMessage());
        }

        if (parsed.getRows().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo no contiene datos");
        }

        Map<String, UUID> niveles = importWrite.loadNiveles();

        List<ErrorFila> errores = new ArrayList<>();
        int exitosos = 0;

        for (int i = 0; i < parsed.getRows().size(); i++) {
            List<String> row = parsed.getRows().get(i);
            int rowNum = i + 2;
            String nombreMat = ImportadorUtil.getCol(row, parsed.getHeaders(), "nombre_materia", "materia", "nombre");
            if (nombreMat.isEmpty()) {
                errores.add(new ErrorFila(rowNum, "fila " + rowNum, "'nombre_materia' es obligatorio"));
                continue;
            }

            String nombreNivel = ImportadorUtil.getCol(row, parsed.getHeaders(), "nombre_nivel", "nivel");
            UUID nivelId = niveles.get(nombreNivel.toLowerCase());
            if (nivelId == null) {
                try {
                    nivelId = UUID.fromString(nombreNivel);
                } catch (IllegalArgumentException e) {
                    errores.add(new ErrorFila(rowNum, nombreMat, "Nivel educativo no encontrado: '" + nombreNivel + "'"));
                    continue;
                }
            }

            try {
                importWrite.insertarMateria(
                        nombreMat,
                        ImportadorUtil.getCol(row, parsed.getHeaders(), "clave_materia", "clave"),
                        nivelId,
                        ImportadorUtil.parseDouble(ImportadorUtil.getCol(row, parsed.getHeaders(), "horas_semana", "horas")),
                        user.getUsername());
                exitosos++;
            } catch (Exception e) {
                errores.add(new ErrorFila(rowNum, nombreMat, e.getMessage()));
            }
        }

        return ResponseEntity.ok(ImportResult.builder()
                .entidad("materias")
                .total(parsed.getRows().size())
                .exitosos(exitosos)
                .errores(errores.size())
                .detalleErrores(errores)
                .build());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /grupos
    // ──────────────────────────────────────────────────────────────────────────
    @PostMapping("/grupos")
    public ResponseEntity<ImportResult> importarGrupos(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAcceso(user, TipoEntidadImport.GRUPOS);

        validarArchivo(file);

        ImportadorUtil.ParsedFile parsed;
        try {
            parsed = ImportadorUtil.parseFile(file.getBytes(), file.getOriginalFilename());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error al parsear el archivo: " + e.getMessage());
        }

        if (parsed.getRows().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo no contiene datos");
        }

        Map<String, UUID> grados = importWrite.loadGrados();
        Map<String, UUID> ciclos = importWrite.loadCiclos();

        List<ErrorFila> errores = new ArrayList<>();
        int exitosos = 0;

        for (int i = 0; i < parsed.getRows().size(); i++) {
            List<String> row = parsed.getRows().get(i);
            int rowNum = i + 2;
            String nombreGrupo = ImportadorUtil.getCol(row, parsed.getHeaders(), "nombre_grupo", "grupo");
            if (nombreGrupo.isEmpty()) {
                errores.add(new ErrorFila(rowNum, "fila " + rowNum, "'nombre_grupo' es obligatorio"));
                continue;
            }

            String nombreGrado = ImportadorUtil.getCol(row, parsed.getHeaders(), "nombre_grado", "grado");
            UUID gradoId = grados.get(nombreGrado.toLowerCase());
            if (gradoId == null) {
                try {
                    gradoId = UUID.fromString(nombreGrado);
                } catch (IllegalArgumentException e) {
                    errores.add(new ErrorFila(rowNum, nombreGrupo, "Grado no encontrado: '" + nombreGrado + "'"));
                    continue;
                }
            }

            String nombreCiclo = ImportadorUtil.getCol(row, parsed.getHeaders(), "nombre_ciclo", "ciclo", "ciclo_escolar");
            UUID cicloId = ciclos.get(nombreCiclo.toLowerCase());
            if (cicloId == null) {
                try {
                    cicloId = UUID.fromString(nombreCiclo);
                } catch (IllegalArgumentException e) {
                    errores.add(new ErrorFila(rowNum, nombreGrupo, "Ciclo escolar no encontrado: '" + nombreCiclo + "'"));
                    continue;
                }
            }

            String turno = ImportadorUtil.getCol(row, parsed.getHeaders(), "turno");
            if (turno.isEmpty()) turno = "MATUTINO";

            Integer capacidad = ImportadorUtil.parseInt(ImportadorUtil.getCol(row, parsed.getHeaders(), "capacidad_maxima", "capacidad"));
            if (capacidad == null) capacidad = 35;

            try {
                importWrite.insertarGrupo(nombreGrupo, gradoId, cicloId, turno, capacidad, user.getUsername());
                exitosos++;
            } catch (Exception e) {
                errores.add(new ErrorFila(rowNum, nombreGrupo, e.getMessage()));
            }
        }

        return ResponseEntity.ok(ImportResult.builder()
                .entidad("grupos")
                .total(parsed.getRows().size())
                .exitosos(exitosos)
                .errores(errores.size())
                .detalleErrores(errores)
                .build());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /aulas
    // ──────────────────────────────────────────────────────────────────────────
    @PostMapping("/aulas")
    public ResponseEntity<ImportResult> importarAulas(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAcceso(user, TipoEntidadImport.AULAS);

        validarArchivo(file);

        ImportadorUtil.ParsedFile parsed;
        try {
            parsed = ImportadorUtil.parseFile(file.getBytes(), file.getOriginalFilename());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error al parsear el archivo: " + e.getMessage());
        }

        if (parsed.getRows().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo no contiene datos");
        }

        Map<String, UUID> plantelesClave = importWrite.loadPlantelesByClave();

        List<ErrorFila> errores = new ArrayList<>();
        int exitosos = 0;

        for (int i = 0; i < parsed.getRows().size(); i++) {
            List<String> row = parsed.getRows().get(i);
            int rowNum = i + 2;
            String nombreAula = ImportadorUtil.getCol(row, parsed.getHeaders(), "nombre_aula", "nombre", "aula");
            if (nombreAula.isEmpty()) {
                errores.add(new ErrorFila(rowNum, "fila " + rowNum, "'nombre_aula' es obligatorio"));
                continue;
            }

            String clavePlantel = ImportadorUtil.getCol(row, parsed.getHeaders(), "clave_plantel", "plantel");
            UUID plantelId = plantelesClave.get(clavePlantel.toLowerCase());

            String tipoAula = ImportadorUtil.getCol(row, parsed.getHeaders(), "tipo_aula", "tipo");
            if (tipoAula.isEmpty()) tipoAula = "SALON";

            Integer capacidad = ImportadorUtil.parseInt(ImportadorUtil.getCol(row, parsed.getHeaders(), "capacidad", "capacidad_maxima"));
            if (capacidad == null) capacidad = 30;

            boolean tieneProyector = parseBoolCol(row, parsed.getHeaders(), "tiene_proyector");
            boolean tienePizarra = parseBoolCol(row, parsed.getHeaders(), "tiene_pizarra_digital");
            boolean tieneInternet = parseBoolCol(row, parsed.getHeaders(), "tiene_internet");
            String observaciones = ImportadorUtil.getCol(row, parsed.getHeaders(), "observaciones", "ubicacion", "ubicacion_fisica");
            if (observaciones.isEmpty()) observaciones = null;

            try {
                importWrite.insertarAula(nombreAula, tipoAula, capacidad, plantelId,
                        tieneProyector, tienePizarra, tieneInternet, observaciones, user.getUsername());
                exitosos++;
            } catch (Exception e) {
                errores.add(new ErrorFila(rowNum, nombreAula, e.getMessage()));
            }
        }

        return ResponseEntity.ok(ImportResult.builder()
                .entidad("aulas")
                .total(parsed.getRows().size())
                .exitosos(exitosos)
                .errores(errores.size())
                .detalleErrores(errores)
                .build());
    }

    private boolean parseBoolCol(List<String> row, List<String> headers, String colName) {
        String val = ImportadorUtil.getCol(row, headers, colName).trim().toUpperCase();
        return List.of("SI", "SÍ", "YES", "TRUE", "1", "S").contains(val);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /preinscritos-sep
    // ──────────────────────────────────────────────────────────────────────────
    @PostMapping("/preinscritos-sep")
    public ResponseEntity<ImportResult> importarPreinscritosSep(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireAcceso(user, TipoEntidadImport.PREINSCRITOS_SEP);

        validarArchivo(file);

        ImportadorUtil.ParsedFile parsed;
        try {
            parsed = ImportadorUtil.parseFile(file.getBytes(), file.getOriginalFilename());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error al parsear el archivo: " + e.getMessage());
        }

        if (parsed.getRows().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo no contiene datos");
        }

        Map<String, UUID> plantelesClave = importWrite.loadPlantelesByClave();
        Map<String, UUID> plantelesNombre = importWrite.loadPlantelesByNombre();
        Map<String, UUID> ciclos = importWrite.loadCiclos();

        List<ErrorFila> errores = new ArrayList<>();
        int exitosos = 0;

        for (int i = 0; i < parsed.getRows().size(); i++) {
            List<String> row = parsed.getRows().get(i);
            int rowNum = i + 2;
            String curp = ImportadorUtil.getCol(row, parsed.getHeaders(), "curp").toUpperCase();
            String nombre = ImportadorUtil.getCol(row, parsed.getHeaders(), "nombre");
            String apellidoP = ImportadorUtil.getCol(row, parsed.getHeaders(), "apellido_paterno", "apellido_p");
            String apellidoM = ImportadorUtil.getCol(row, parsed.getHeaders(), "apellido_materno", "apellido_m");
            if (apellidoM.isEmpty()) apellidoM = null;
            String fechaNac = ImportadorUtil.getCol(row, parsed.getHeaders(), "fecha_nacimiento", "fecha_nac");
            String nivel = ImportadorUtil.getCol(row, parsed.getHeaders(), "nivel_solicitado", "nivel");
            String gradoStr = ImportadorUtil.getCol(row, parsed.getHeaders(), "grado_solicitado", "grado");
            String clavePlantel = ImportadorUtil.getCol(row, parsed.getHeaders(), "clave_plantel", "plantel", "clave_ct");
            String nombreCiclo = ImportadorUtil.getCol(row, parsed.getHeaders(), "nombre_ciclo", "ciclo", "ciclo_escolar");
            String tutor = ImportadorUtil.getCol(row, parsed.getHeaders(), "nombre_tutor", "tutor");
            if (tutor.isEmpty()) tutor = "Tutor Pendiente";

            if (curp.isEmpty() || nombre.isEmpty() || apellidoP.isEmpty() || fechaNac.isEmpty() || nivel.isEmpty() || gradoStr.isEmpty() || nombreCiclo.isEmpty()) {
                errores.add(new ErrorFila(rowNum, "fila " + rowNum, "Campos obligatorios faltantes (nombre, apellido_p, curp, fecha_nac, nivel, grado, ciclo)"));
                continue;
            }

            if (importWrite.existeAdmisionActiva(curp)) {
                errores.add(new ErrorFila(rowNum, curp, "Ya existe una solicitud de admisión activa para esta CURP"));
                continue;
            }

            if (importWrite.existePersonaCurp(curp)) {
                errores.add(new ErrorFila(rowNum, curp, "CURP ya registrada en el sistema escolar (alumno o usuario activo)"));
                continue;
            }

            UUID plantelId = resolverPlantel(clavePlantel, plantelesClave, plantelesNombre);
            if (plantelId == null) {
                errores.add(new ErrorFila(rowNum, curp, "Plantel no encontrado: '" + clavePlantel + "'"));
                continue;
            }

            UUID cicloId = ciclos.get(nombreCiclo.toLowerCase());
            if (cicloId == null) {
                try {
                    cicloId = UUID.fromString(nombreCiclo);
                } catch (IllegalArgumentException e) {
                    errores.add(new ErrorFila(rowNum, curp, "Ciclo escolar no encontrado: '" + nombreCiclo + "'"));
                    continue;
                }
            }

            Integer grado = ImportadorUtil.parseInt(gradoStr);
            if (grado == null) {
                errores.add(new ErrorFila(rowNum, curp, "Grado no válido: '" + gradoStr + "'"));
                continue;
            }

            try {
                importWrite.insertarPreinscritoSEP(
                        nombre, apellidoP, apellidoM, curp,
                        ImportadorUtil.parseDate(fechaNac), nivel, grado, plantelId, cicloId,
                        tutor,
                        ImportadorUtil.getCol(row, parsed.getHeaders(), "telefono_tutor", "tel_tutor"),
                        ImportadorUtil.getCol(row, parsed.getHeaders(), "email_tutor", "correo_tutor"),
                        ImportadorUtil.getCol(row, parsed.getHeaders(), "escuela_procedencia", "procedencia"),
                        ImportadorUtil.parseDouble(ImportadorUtil.getCol(row, parsed.getHeaders(), "promedio_procedencia", "promedio")),
                        user.getUsername());
                exitosos++;
            } catch (Exception e) {
                errores.add(new ErrorFila(rowNum, curp, e.getMessage()));
            }
        }

        return ResponseEntity.ok(ImportResult.builder()
                .entidad("preinscritos_sep")
                .total(parsed.getRows().size())
                .exitosos(exitosos)
                .errores(errores.size())
                .detalleErrores(errores)
                .build());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void requireAcceso(AdesUser user, TipoEntidadImport tipo) {
        if (user.getNivelAcceso() == null || !tipo.permitePara(user.getNivelAcceso())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Permisos insuficientes para importar " + tipo.clave());
        }
    }
}
