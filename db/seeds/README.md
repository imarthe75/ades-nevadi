# Seeds ADES — orden de ejecución

Los seeds cargan datos iniciales y de simulación **después** de aplicar todas las
migraciones de `db/migrations/`. Las migraciones traen catálogos oficiales (geográficos,
niveles, currícula SEP/UAEMEX, ponderación, menús/permisos, config NEM).

## Orden

| # | Archivo | Propósito |
|---|---------|-----------|
| 1 | `001_datos_base.sql` | Catálogos base / institución / planteles |
| 2 | `002_grupos_profesores.sql` | Grados y grupos por plantel/ciclo |
| 3 | `003_alumnos_padres.sql` | (legado) alta básica de alumnos/padres |
| 4 | `004_plan_estudios.sql` | Vínculo materias↔grado del plan |
| 5 | `005_disponibilidad_aulas.sql` | Aulas y disponibilidad |
| 6 | **`006_simulacion_integral.py`** | **Simulación INTEGRAL del ciclo escolar (recomendado)** |

> Para un entorno de demo/QA basta aplicar migraciones + ejecutar **006**, que regenera
> todo lo operativo con datos realistas (ver abajo). Los seeds 001–005 cubren el backbone
> que 006 **preserva** (planteles, grados, grupos, currícula).

## 006_simulacion_integral.py — Simulación integral

Regenera **todo lo operativo** con datos realistas y **sin duplicados de persona**
(nombres mexicanos curados + CURP de 18 caracteres con homoclave única + direcciones
reales del catálogo de CP), y simula el **ciclo escolar completo de extremo a extremo**:

preinscripción → inscripción → personal (docente/administrativo/salud) → planificación
semanal → clases → asistencias (+justificaciones) → tareas/proyectos → exámenes →
calificaciones (numérica + cualitativa NEM A/B/C/D en 1°-2° primaria) → conducta
(+sanciones) → expedientes médicos (+crónicas/medicamentos/incidentes) → evaluación
docente 360° → comunicación (comunicados/anuncios/foros) → reinscripción → estadísticos.

**Preserva:** planteles, currícula oficial (materias_plan, temas), grados, grupos, ciclos,
ponderación, escalas (incl. cualitativa NEM), catálogos, menús/permisos, `ades_config`
y las cuentas institucionales `@institutonevadi.edu.mx` (login OIDC).

**Ejecución** (corre en el host, sin contraseñas; usa el socket local de confianza
`docker compose exec postgres psql`):

```bash
cd /opt/ades
python3 db/seeds/006_simulacion_integral.py
```

Es **idempotente**: cada corrida re-vacía lo operativo y regenera. Parámetros de volumen
ajustables al inicio del script (`ALUMNOS_POR_GRUPO`, `CLASES_POR_PERIODO`, etc.) — útil
si el disco del entorno de desarrollo está ajustado.

## Limpieza de espacio

`scripts/limpieza_dev.sh` — limpieza segura de disco (logs de contenedores, prune de
imágenes dangling, backups viejos, VACUUM). El generador 006 puede dejar bloat tras
varias corridas; un `VACUUM` posterior recupera el espacio para reuso.

## `_obsoletos/`

Versiones duplicadas antiguas (`*_v2/v3/v4.sql`, `003_datos_operativos.py`) archivadas;
no se ejecutan. Conservadas solo como referencia histórica.
