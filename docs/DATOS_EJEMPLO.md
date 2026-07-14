# Cargar Datos de Ejemplo — ADES
## Nuevo Servidor (163.192.138.130)

---

## RESUMEN

En un entorno nuevo, PostgreSQL ejecuta automáticamente:
1. Migraciones SQL: `/db/migrations/` (001-114.sql)
2. Scripts de inicialización: `/db/scripts/init_multi_db.sh`
3. Seeds: `/db/seeds/` (datos de ejemplo)

**Esto ocurre automáticamente al crear el contenedor `ades-postgres`.**

---

## VERIFICAR ESTADO DE LA BD

```bash
# Acceder al contenedor PostgreSQL
docker-compose exec postgres psql -U ades_admin -d ades

# Dentro de psql:
\dt ades_*;              -- Listar tablas ADES
SELECT COUNT(*) FROM ades_alumnos;
SELECT COUNT(*) FROM ades_grupos;
SELECT COUNT(*) FROM ades_materias;
\q                        -- Salir
```

---

## SI LAS MIGRACIONES NO SE EJECUTARON AUTOMÁTICAMENTE

Ejecutar manualmente:

```bash
# 1. Aplicar todas las migraciones (001-114)
docker-compose exec -T postgres psql -U ades_admin -d ades < db/migrations/001_xxx.sql
docker-compose exec -T postgres psql -U ades_admin -d ades < db/migrations/002_xxx.sql
# ... (continuar con todas)

# O en batch:
for f in db/migrations/*.sql; do
  docker-compose exec -T postgres psql -U ades_admin -d ades < "$f"
done
```

---

## CARGAR SEEDS (Datos de Ejemplo)

Los seeds están en `/db/seeds/` y se deben ejecutar manualmente:

```bash
# Cargar seeds en orden:
docker-compose exec -T postgres psql -U ades_admin -d ades < db/seeds/01_planteles.sql
docker-compose exec -T postgres psql -U ades_admin -d ades < db/seeds/02_grupos.sql
docker-compose exec -T postgres psql -U ades_admin -d ades < db/seeds/03_alumnos.sql
docker-compose exec -T postgres psql -U ades_admin -d ades < db/seeds/04_materias.sql
docker-compose exec -T postgres psql -U ades_admin -d ades < db/seeds/05_calificaciones.sql
# ... (continuar con todos los seeds)
```

---

## VERIFICAR INTEGRIDAD DE DATOS

```bash
docker-compose exec postgres psql -U ades_admin -d ades <<EOF
  SELECT 'Planteles' AS tabla, COUNT(*) as registros FROM ades_planteles
  UNION ALL
  SELECT 'Grupos', COUNT(*) FROM ades_grupos
  UNION ALL
  SELECT 'Alumnos', COUNT(*) FROM ades_alumnos
  UNION ALL
  SELECT 'Materias', COUNT(*) FROM ades_materias
  UNION ALL
  SELECT 'Calificaciones', COUNT(*) FROM ades_calificaciones
  ORDER BY tabla;
EOF
```

---

## RESET COMPLETO (Si es necesario)

```bash
# ⚠️ DESTRUCTIVO — Borra todos los datos
docker-compose down -v

# Levantar nuevamente (re-crea todo desde cero)
docker-compose up -d postgres

# Esperar a que PostgreSQL esté listo
until docker-compose exec postgres pg_isready -U ades_admin -d ades 2>/dev/null; do
  echo "Esperando BD..."
  sleep 5
done

# Aplicar migraciones
for f in db/migrations/*.sql; do
  docker-compose exec -T postgres psql -U ades_admin -d ades < "$f"
done

# Cargar seeds
for f in db/seeds/*.sql; do
  docker-compose exec -T postgres psql -U ades_admin -d ades < "$f"
done
```

---

## AUDITORÍA DE DATOS

```bash
# Ver últimas migraciones aplicadas
docker-compose exec postgres psql -U ades_admin -d ades -c \
  "SELECT * FROM auditoria.log_auditoria ORDER BY timestamp DESC LIMIT 20;"

# Ver cobertura de auditoría
docker-compose exec postgres psql -U ades_admin -d ades -c \
  "SELECT * FROM auditoria.reporte_cobertura();"
```

---

## ESTRUCTURA DE SEEDS

```
db/seeds/
├── 01_planteles.sql              # Institución Nevadi (3 planteles)
├── 02_grupos.sql                 # Grupos por plantel y nivel
├── 03_alumnos.sql                # 500+ alumnos distribuidos
├── 04_materias.sql               # Currícula SEP/UAEMEX
├── 05_calificaciones.sql         # Calificaciones de ejemplo
├── 06_horarios.sql               # Franjas horarias (Fase 3)
├── 07_docentes.sql               # Personal docente
├── 08_usuarios.sql               # Cuentas de acceso (Authentik)
├── 09_padres_tutores.sql         # Padres/tutores de alumnos
├── 10_planes_estudio.sql         # Planes de estudio configurados
├── 11_eval_docente.sql           # Evaluaciones 360° de ejemplo
├── 12_conducta.sql               # Registros de conducta
└── ...
```

---

## PROBLEMAS COMUNES

### BD no se crea automáticamente

**Síntoma**: `psql: error: could not translate host name "postgres" to address`

**Solución**:
```bash
# Verificar que postgres está corriendo
docker-compose ps postgres

# Si no está Up, reiniciar
docker-compose restart postgres

# Esperar 30 segundos y reintentar
```

### Migraciones falla por FK constraints

**Síntoma**: `ERROR: insert or update on table "ades_xxx" violates foreign key constraint`

**Solución**:
```bash
# Aplicar migraciones en orden (respetado el numbering 001-114)
# Los seeds SIEMPRE van después de todas las migraciones
```

### Datos de ejemplo incompletos

**Síntoma**: `SELECT COUNT(*) FROM ades_alumnos;` retorna 0

**Solución**:
```bash
# Verificar que los seeds se ejecutaron
ls -la db/seeds/

# Cargar manualmente todos los seeds en orden
for f in db/seeds/*.sql; do
  echo "Cargando $f..."
  docker-compose exec -T postgres psql -U ades_admin -d ades < "$f"
done
```

---

## EXPORTAR DATOS (Backup)

```bash
# Full backup de la BD
docker-compose exec postgres pg_dump -U ades_admin ades > backup_$(date +%Y%m%d_%H%M%S).sql

# Restaurar desde backup
docker-compose exec -T postgres psql -U ades_admin -d ades < backup_20260710.sql
```

---

## REFERENCIA RÁPIDA

| Comando | Propósito |
|---|---|
| `docker-compose exec postgres psql -U ades_admin -d ades` | Shell interactivo |
| `docker-compose exec -T postgres psql -U ades_admin -d ades < file.sql` | Ejecutar script |
| `docker-compose logs -f postgres` | Ver logs de BD |
| `docker-compose down -v` | Borrar todo (irreversible) |
| `SELECT * FROM auditoria.log_auditoria;` | Ver audit trail |

---

**Generado**: 2026-07-10
**Próxima fase**: Ejecutar pruebas exploratorias después de confirmar datos de ejemplo
