# ✅ CHECKLIST PRE-COMMIT: 16 PUNTOS DE OPTIMIZACIÓN

**Estado:** 🔴 CRÍTICA - NO MERGE SIN COMPLETAR TODOS LOS PUNTOS  
**Aplicable a:** Todas las PRs (Backend + Frontend)  
**Última actualización:** 2026-07-08

---

## 🚨 REGLA DE ORO

**SI CUALQUIERA DE ESTOS 16 PUNTOS FALTA → PR RECHAZADA**

No importa si funciona. No importa si los tests pasan. Los 16 puntos son **NON-NEGOTIABLE**.

---

## BACKEND (9 ITEMS)

### ✅ 1. @EntityGraph en Relaciones

**Verificación:**
```bash
# Buscar todos los repositories con relaciones
find backend-spring -name "*Repository.java" -exec grep -l "@OneToMany\|@ManyToOne" {} \;

# Contar @EntityGraph presentes
grep -r "@EntityGraph" backend-spring/src | wc -l
# Esperado: ≥ 20 (para 20+ repositorios con relaciones)
```

**Checklist:**
- [ ] Si hay `.findBy*()` con relaciones → DEBE tener `@EntityGraph`
- [ ] Si hay `.findAll()` con relaciones → DEBE tener `@EntityGraph`
- [ ] Validar: `grep "@EntityGraph" repository.java | wc -l ≥ número de métodos`
- [ ] Test: Ejecutar reporte 911 con 100 estudiantes, verificar query count = 1

**Bloquea merge si:** Cualquier `find*()` con FK sin `@EntityGraph`

---

### ✅ 2. Índices en Base de Datos

**Verificación:**
```bash
# Listar índices actuales
psql -U ades_admin -d ades -c "\di"

# Contar índices
psql -U ades_admin -d ades -c "SELECT COUNT(*) FROM pg_indexes WHERE schemaname='public';"
# Esperado: ≥ 195 (45 existentes + 150 nuevos)

# Buscar columnas FK sin índice
psql -U ades_admin -d ades -c "
SELECT t.table_name, c.column_name 
FROM information_schema.columns c
JOIN information_schema.tables t ON c.table_name = t.table_name
WHERE c.column_name LIKE '%_id' AND t.table_schema='public'
EXCEPT
SELECT t.table_name, a.attname
FROM pg_attribute a
JOIN pg_class c ON a.attrelid = c.oid
JOIN pg_namespace n ON c.relnamespace = n.oid
JOIN information_schema.tables t ON c.relname = t.table_name
WHERE n.nspname = 'public' AND a.attname LIKE '%_id';"
```

**Checklist:**
- [ ] Toda FK tiene índice simple O compuesto
- [ ] Campos de búsqueda (nombre, codigo, email) tienen índice
- [ ] Migration creada para nuevos índices (120_create_missing_indexes.sql)
- [ ] Test: EXPLAIN ANALYZE muestra "Index Scan" (no "Seq Scan")

**Bloquea merge si:** Tabla con >1000 registros sin índice en FK

---

### ✅ 3. JOIN FETCH en Queries

**Verificación:**
```bash
# Buscar todas las queries
grep -r "@Query" backend-spring/src --include="*.java" | head -20

# Buscar JOIN FETCH presentes
grep -r "JOIN FETCH" backend-spring/src | wc -l
# Esperado: ≥ 15
```

**Checklist:**
- [ ] Si `@Query` contiene relaciones → DEBE incluir `JOIN FETCH`
- [ ] Si método toca `.get*()` en relación → DEBE tener @Transactional(readOnly=true)
- [ ] Test: Sin LazyInitializationException en logs
- [ ] Test: Query count = esperado (verificar con QueryCountHolder)

**Bloquea merge si:** Query accede a LAZY relation sin JOIN FETCH

---

### ✅ 4. Pageable + Page<DTO> en Endpoints

**Verificación:**
```bash
# Buscar todos los @GetMapping
grep -r "@GetMapping" backend-spring/src/main/java --include="*.java" | wc -l

# Buscar que usan List<
grep -r "@GetMapping" -A 5 backend-spring/src | grep "List<" | grep -v "Pageable"
# Esperado: 0 resultados (todos deben usar Pageable)

# Buscar Page<
grep -r "Page<" backend-spring/src/main/java --include="*.java" | wc -l
# Esperado: ≥ 32 (uno por cada endpoint)
```

**Checklist:**
- [ ] TODO `@GetMapping` que devuelve lista → parámetro `Pageable`
- [ ] TODO retorno → `Page<DTO>` (no `List<Entity>`)
- [ ] DTO existe y mapea correctamente (NO serializar Entity)
- [ ] Test: Payload < 500KB para primera página
- [ ] Test: Respuesta incluye `totalElements`, `totalPages`

**Bloquea merge si:** Endpoint devuelve `List<Entity>` sin Pageable

---

### ✅ 5-8. Frontend (ver sección Frontend)

---

### ✅ 9. @Cacheable con TTL

**Verificación:**
```bash
# Buscar @Cacheable presentes
grep -r "@Cacheable" backend-spring/src | wc -l
# Esperado: ≥ 40

# Buscar @CacheEvict (para invalidación)
grep -r "@CacheEvict" backend-spring/src | wc -l
# Esperado: ≥ 20
```

**Checklist:**
- [ ] Métodos que se leen >10 veces/día tienen @Cacheable
- [ ] Métodos write correspondientes tienen @CacheEvict
- [ ] Redis/Valkey configurado con TTL
- [ ] Test: Hit rate > 80% (monitorear con actuator `/actuator/cache`)
- [ ] Test: Invalidación funciona post-update

**Bloquea merge si:** Método leído >100 veces/día sin @Cacheable

---

### ✅ 10. Batch Operations (saveAll)

**Verificación:**
```bash
# Buscar loops con save()
grep -r "\.save(" backend-spring/src --include="*.java" -B 2 -A 2 | grep "for\|while"
# Esperado: 0 resultados

# Buscar saveAll()
grep -r "\.saveAll(" backend-spring/src --include="*.java" | wc -l
# Esperado: ≥ 10

# Verificar batch_size en application.yml
grep -r "batch_size" . --include="*.yml" | grep hibernate
# Esperado: batch_size=20 (o configurable)
```

**Checklist:**
- [ ] NUNCA `for (item : items) repo.save(item)`
- [ ] SIEMPRE `repo.saveAll(items)` cuando >1 operación
- [ ] application.yml tiene: `spring.jpa.properties.hibernate.jdbc.batch_size=20`
- [ ] Test: INSERT de 1000 items < 5 segundos

**Bloquea merge si:** Encontrado loop con `.save()` dentro

---

### ✅ 11. Compression (gzip)

**Verificación:**
```bash
# Verificar nginx
grep "gzip on" /etc/nginx/nginx.conf

# Verificar Spring
grep "server.compression.enabled" application.yml
# Esperado: true

# Test en vivo
curl -I http://localhost:8080/api/v1/usuarios
# Debe mostrar: Content-Encoding: gzip
```

**Checklist:**
- [ ] nginx.conf: `gzip on;`
- [ ] application.yml: `server.compression.enabled: true`
- [ ] Test: curl -I → Content-Encoding: gzip presente

**Bloquea merge si:** Compression deshabilitada

---

### ✅ 12. HikariCP Tuning

**Verificación:**
```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20     # Validar vs concurrencia
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

**Checklist:**
- [ ] `maximum-pool-size` correcto para carga esperada
- [ ] `minimum-idle ≥ 2`
- [ ] Test: Bajo carga, active connections < 80% pool

**Bloquea merge si:** Pool sin configurar

---

### ✅ 13. Prepared Statements (Spring Data Automático)

**Verificación:**
```bash
# Buscar SQL concatenation (NUNCA)
grep -r "\".*+ " backend-spring/src --include="*.java" | grep -i select
# Esperado: 0 resultados

# Buscar parametrización correcta
grep -r "WHERE.*?1\|WHERE.*?2" backend-spring/src --include="*.java" | wc -l
# Esperado: > 0 (Spring Data hace esto automáticamente)
```

**Checklist:**
- [ ] NUNCA usar `"SELECT * FROM users WHERE id = '" + id + "'"`
- [ ] SIEMPRE usar `repo.findById(id)` o @Query con `?1`
- [ ] Spring Data parameteriza automáticamente

**Bloquea merge si:** SQL concatenation manual encontrado

---

## FRONTEND (7 ITEMS)

### ✅ 5. ChangeDetectionStrategy.OnPush

**Verificación:**
```bash
# Buscar componentes SIN OnPush
grep -r "@Component" frontend/src/app --include="*.ts" -A 3 | grep -B 3 -v "OnPush" | grep selector | wc -l
# Esperado: 0 (todos deben tener OnPush)

# Buscar componentes CON OnPush
grep -r "ChangeDetectionStrategy.OnPush" frontend/src/app --include="*.ts" | wc -l
# Esperado: 79 (todos los componentes)
```

**Checklist:**
- [ ] TODOS los componentes tienen `changeDetection: ChangeDetectionStrategy.OnPush`
- [ ] Test: CPU < 10% en navegación (vs 30% actual)
- [ ] Test: No hay "ExpressionChangedAfterCheckError"

**Bloquea merge si:** Componente nuevo sin OnPush

---

### ✅ 6. implements OnDestroy + Cleanup

**Verificación:**
```bash
# Buscar componentes con .subscribe() pero SIN OnDestroy
grep -r "\.subscribe(" frontend/src/app --include="*.ts" -l | while read f; do
  if ! grep -q "implements.*OnDestroy" "$f"; then
    echo "SIN OnDestroy: $f"
  fi
done
# Esperado: 0 resultados

# Buscar takeUntil
grep -r "takeUntil(" frontend/src/app --include="*.ts" | wc -l
# Esperado: ≥ 481 (todos los subscribe)
```

**Checklist:**
- [ ] Si hay `.subscribe()` → DEBE heredar BaseComponent
- [ ] TODOS los subscribe usan `.pipe(takeUntil(this.destroy$))`
- [ ] ngOnDestroy heredado de BaseComponent
- [ ] Test: Memory DevTools sin acumulación

**Bloquea merge si:** Componente con .subscribe() sin takeUntil

---

### ✅ 7. Memory Leaks Test (DevTools)

**Verificación:**
```bash
# Test manual en DevTools
1. Abrir DevTools → Memory tab
2. Tomar Heap Snapshot inicial
3. Navegar 10 veces (ir a componente → volver)
4. Tomar Heap Snapshot final
5. Comparar: Diferencia < 5MB (vs >50MB actual)
```

**Checklist:**
- [ ] Dev ejecutó Memory profiler test
- [ ] Heap snapshot: diferencia < 5MB
- [ ] Detached DOM nodes: < 10
- [ ] DevTools console: sin errores de subscripción

**Bloquea merge si:** Memory test muestra leak > 10MB

---

### ✅ 8. loading="lazy" en Imágenes

**Verificación:**
```bash
# Buscar <img> SIN loading="lazy"
grep -r "<img" frontend/src --include="*.html" | grep -v "loading=" | grep -v "hero\|logo"
# Esperado: 0 resultados (excepto hero/logo que necesitan eager load)
```

**Checklist:**
- [ ] TODAS las imágenes tienen `loading="lazy"`
- [ ] Excepto: hero image, logo (que necesitan eager)
- [ ] Test: Navegador no carga imagen fuera viewport

**Bloquea merge si:** Imagen sin loading="lazy"

---

### ✅ 14. HTTP Headers (Cache-Control + ETag)

**Verificación:**
```bash
curl -I http://localhost:4200/api/v1/usuarios
# Debe mostrar:
# Cache-Control: max-age=3600, public
# ETag: "abc123def456"

curl http://localhost:4200/api/v1/usuarios -H 'If-None-Match: "abc123def456"'
# Debe retornar 304 Not Modified
```

**Checklist:**
- [ ] Backend retorna `Cache-Control` header
- [ ] Backend retorna `ETag` header
- [ ] Frontend maneja `304 Not Modified`
- [ ] Test: Segunda request → 304 (sin re-download)

**Bloquea merge si:** Sin Cache-Control header

---

### ✅ 15. Image Optimization (WebP + srcset)

**Verificación:**
```bash
# Buscar imágenes
find frontend/src -name "*.png" -o -name "*.jpg" | head -10

# Verificar tamaño
ls -lh frontend/src/assets/images/*.png | awk '{print $5 " " $9}'
# Thumbnail: < 50KB
# Full: < 200KB

# Buscar WebP
find frontend/src -name "*.webp" | wc -l
# Esperado: > 0
```

**Checklist:**
- [ ] Imágenes tienen WebP + JPG fallback
- [ ] Thumbnails < 50KB
- [ ] srcset responsive (mobile, tablet, desktop)
- [ ] Test: DevTools Network → imagen cargada en WebP

**Bloquea merge si:** Imagen sin WebP (> 100KB)

---

### ✅ 16. Transaction Isolation

**Verificación:**
```sql
-- Verificar deadlocks
SELECT * FROM pg_stat_activity WHERE wait_event_type = 'Lock';
# Esperado: 0 resultados

-- Verificar isolation level
SHOW transaction_isolation;
# Esperado: serializable (si hay locks)
```

**Checklist:**
- [ ] Locks siempre en ORDEN CONSISTENTE (id1, id2, id3)
- [ ] No hay deadlock loops
- [ ] pg_stat_activity: deadlocks = 0
- [ ] Test: Concurrent writes sin deadlock

**Bloquea merge si:** Deadlock loop posible

---

## 🎯 FLUJO DE CODE REVIEW

```
1. Developer abre PR
2. Code Review comienza
3. Para CADA cambio backend/frontend:
   ├─ ¿Toca relaciones? → Verifica items 1, 3
   ├─ ¿Toca endpoints? → Verifica items 4, 11, 13, 14
   ├─ ¿Toca componentes? → Verifica items 5, 6, 7, 8, 15
   ├─ ¿Toca datos? → Verifica items 2, 9, 10
   └─ ¿Toca transactions? → Verifica item 16
4. SI todos los items = ✅ → APRUEBA
5. SI cualquier item = ❌ → RECHAZA con comentario específico
6. Developer CORRIGE → RESUBMIT
7. Después de aprobación → MERGE
```

---

## 📋 TEMPLATE DE RECHAZO

```
❌ PR RECHAZADA - Punto #X no cumple

Punto: [Descripción]
Status: No cumple
Requerimiento: [Qué falta]
Acción: [Qué hacer para arreglarlo]

Comandos para verificar:
[Comando específico]

Referencias:
- CHECKLIST_PRECOMMIT_16_PUNTOS.md
- 16_PUNTOS_OPTIMIZACION_COMPLETO.md
```

---

## 📊 MÉTRICAS POST-FIX

Después de merging, verificar:

| Métrica | Antes | Después | Target |
|---------|-------|---------|--------|
| Memory | 250MB/30min | 50MB/30min | ✅ |
| API Response | 30s | <1s | ✅ |
| DB CPU | 95% | 20% | ✅ |
| Page FCP | 3s | 0.8s | ✅ |
| Bundle | 5MB | 500KB | ✅ |

---

**CONCLUSIÓN:** Sin estos 16 puntos, NO se mergea. Punto.
