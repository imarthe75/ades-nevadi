# 🔍 FASE 26: Análisis de Completitud y Validación Técnica
## Integración del Starter en ADES — Gap Analysis & Risk Assessment

**Fecha:** 2026-06-10  
**Objetivo:** Validar que el plan `FASE_26_INTEGRACION_STARTER.md` está 100% completo, identificar gaps técnicos, dependencias no documentadas y riesgos de implementación.

---

## 📋 MATRIZ DE VALIDACIÓN POR FASE

### ✅ FASE 26-A: Variables del Sistema + Catálogos Dinámicos

**Estado:** ✅ COMPLETO pero con salvedades

#### SQL Migration (021_variables_catalogos.sql)
- ✅ `ades_catalogos` (cabecera): estructura correcta, AuditMixin completo
- ✅ `ades_catalogo_items` (items): UNIQUE constraint en `(catalogo_id, valor)` + índices
- ✅ `ades_variables_sistema`: tipos validados con CHECK, encriptado/solo_lectura, grupos clasificados
- ✅ SEED: 10 catálogos base + 47 items precargados
- ✅ SEED: 28 variables del sistema con valores defaults realistas

**⚠️ GAPS IDENTIFICADOS:**

1. **Encriptación de PASSWORD**: El documento dice "encriptado=True, la BD almacena encriptado con pgcrypto"
   - **Problema:** No hay trigger que encripte `valor` en INSERT/UPDATE
   - **Solución requerida:** Crear función PostgreSQL `pgcrypto_encrypt()` y trigger antes de hacer INSERT
   - **Código necesario:**
   ```sql
   CREATE EXTENSION IF NOT EXISTS pgcrypto;
   
   CREATE OR REPLACE FUNCTION encrypt_variable_value()
   RETURNS TRIGGER AS $$
   BEGIN
     IF NEW.encriptado = TRUE THEN
       NEW.valor := pgp_sym_encrypt(NEW.valor, current_database());
     END IF;
     RETURN NEW;
   END;
   $$ LANGUAGE plpgsql;
   
   CREATE TRIGGER trigger_encrypt_variable
   BEFORE INSERT OR UPDATE ON ades_variables_sistema
   FOR EACH ROW
   EXECUTE FUNCTION encrypt_variable_value();
   ```

2. **Búsqueda por `nombre` en variables**: Sin UNIQUE constraint explícito
   - ✅ **Revisado:** Hay `UNIQUE (nombre)`, está OK

3. **Validación de tipos en Python**: El schema Pydantic `VariableUpdate.validar_valor()` es superficial
   - **Problema:** Acepta cualquier string. No valida que sea JSON si tipo=JSON, numero si tipo=NUMERO, etc.
   - **Solución:** Implementar validación real por tipo en el endpoint
   ```python
   @field_validator('valor')
   def validar_por_tipo(cls, v, info):
       tipo = info.data.get('tipo_valor')
       if tipo == 'JSON' and v:
           try: json.loads(v)
           except: raise ValueError('JSON inválido')
       elif tipo == 'NUMERO' and v:
           try: float(v)
           except: raise ValueError('No es número válido')
       elif tipo == 'FECHA' and v:
           try: datetime.fromisoformat(v)
           except: raise ValueError('Fecha inválida (ISO 8601 requerido)')
       return v
   ```

4. **Carga de variables en memoria en el backend**: No hay mecanismo de cache
   - **Problema:** Cada request hace SELECT a `ades_variables_sistema`. Con 1000 requests/seg es overhead
   - **Solución:** Implementar Redis cache con TTL de 5 min en `get_variable_value()` función helper
   ```python
   async def get_variable_value(nombre: str, db: AsyncSession) -> str | None:
       # 1. Intentar Redis
       cached = await redis_client.get(f"var:{nombre}")
       if cached: return cached
       # 2. Query BD
       result = await db.execute(...)
       var = result.scalar_one_or_none()
       if var:
           await redis_client.setex(f"var:{nombre}", 300, var.valor)
       return var.valor if var else None
   ```

5. **Endpoint público `/config/public`**: Hardcodeado a 5 variables
   - **Problema:** Si la UI frontend necesita otra variable pública antes de login (p.ej., `URL_SUPERSET`), hay que modificar código
   - **Solución:** Crear catálogo `ades_variables_publicas` o agregar flag `publica=bool` a `ades_variables_sistema`
   ```sql
   ALTER TABLE ades_variables_sistema
   ADD COLUMN IF NOT EXISTS publica BOOLEAN DEFAULT FALSE;
   
   UPDATE ades_variables_sistema
   SET publica = TRUE
   WHERE nombre IN ('NOMBRE_INSTITUCION', 'JSON_MARCA', 'JSON_CONFIG_UI', 'URL_PORTAL', 'NOMBRE_SISTEMA');
   ```

6. **Validación de Variables ADES-específicas**: No todas están en el SEED
   - **Variables que faltan y son críticas:**
     * `RUBROS_CALIFICACION` (JSON con estructura de pesos: participación 20%, examen 50%, etc.) — para cálculo de promedios
     * `FORMATO_BOLETA_PDF` (TEXTO con template ID en Carbone)
     * `AUDITORIA_NIVEL_VERBOSE` (BOOLEANO para filtrar eventos en audit_log)
     * `MAX_DIAS_RETRASO_CALIFICACION` (NUMERO para alertar si la boleta está retrasada)
     * `EMAIL_NOTIFICACIONES_PADRE` (BOOLEANO para activar/desactivar mails a padres)

#### Schemas Pydantic (sistema.py)
- ✅ Estructura correcta, `model_validate` y conversión from_attributes
- ⚠️ **PROBLEMA:** `VariableOut.from_model()` es classmethod pero se llama en list comprehension
  - Debe ser: `[VariableOut.model_validate(v) for v in vars_]` y manejar la lógica de enmascaramiento en el model validator

#### Endpoints FastAPI (catalogos.py)
- ✅ `/catalogos` GET (listar públicos)
- ✅ `/catalogos/{id}` GET (detalle con items)
- ✅ `/catalogos` POST (crear, requiere ADMIN)
- ✅ `/catalogos/{id}` PATCH (actualizar con optimistic locking)
- ✅ `/catalogos/{id}/items` POST (agregar items)
- ✅ `/catalogos/items/{id}` PATCH (actualizar items)
- ✅ `/config/variables` GET (admin only)
- ✅ `/config/variables/{nombre}` PATCH (actualizar variable)
- ✅ `/config/public` GET (variables públicas sin auth)

**⚠️ GAPS EN ENDPOINTS:**

1. **Falta DELETE**: No hay `DELETE /catalogos/{id}` ni `DELETE /catalogos/items/{id}`
   - **Riesgo:** Una vez creado un catálogo/item, no se puede borrar (solo desactivar)
   - **Decisión requerida:** ¿Soft-delete (is_active=false) o hard-delete (CASCADE)?
   - **Recomendación:** Soft-delete obligatorio para audit, pero permitir hard-delete si no tiene referencias

2. **Falta reordenamiento de items**: POST/PATCH no incluyen endpoint para reordenar items en masa
   - **Problema:** Si hay 50 items y necesito cambiar orden de 10, es ineficiente hacer 10 PATCH
   - **Solución:** Agregar endpoint `POST /catalogos/{id}/items/reorder` que acepte array de `{item_id, nuevo_orden}`

3. **Falta búsqueda/filtrado**: `/catalogos` y `/config/variables` no soportan `?search=` ni `?tipo_valor=`
   - **Problema:** Admin con 200 variables tiene que recibir todas, el frontend filtra (UX mala)
   - **Solución:** Agregar parámetros opcionales
   ```python
   @router.get("/config/variables")
   async def listar_variables(
       grupo: str | None = None,
       search: str | None = None,
       tipo_valor: str | None = None,
       solo_lectura: bool | None = None,
       db: AsyncSession = Depends(get_db),
   ):
       q = select(VariableSistema)
       if grupo: q = q.where(VariableSistema.grupo == grupo)
       if search: q = q.where(VariableSistema.nombre.ilike(f"%{search}%"))
       if tipo_valor: q = q.where(VariableSistema.tipo_valor == tipo_valor)
       if solo_lectura is not None: q = q.where(VariableSistema.solo_lectura == solo_lectura)
       return [...]
   ```

#### Componente Angular (admin.component.ts Tab "Variables")
- ✅ Estructura de signals y métodos
- ✅ Grid con inputs dinámicos por tipo_valor
- ⚠️ **PROBLEMA:** Input JSON no valida antes de enviar
  ```typescript
  // Agregar en dialog:
  esJsonValido(json: string): boolean {
    try { JSON.parse(json); return true; }
    catch { return false; }
  }
  ```

- ⚠️ **PROBLEMA:** No hay manejo de PASSWORD en edición
  - El dialog muestra "••••••••" pero al enviar `_valorEdit` está vacío
  - Necesita lógica: "si `_valorEdit` sigue vacío, no enviar el campo"

- ⚠️ **PROBLEMA:** No hay confirmación antes de cambiar variable crítica
  - Si alguien cambia `ZONA_HORARIA` o `CALIFICACION_APROBATORIA` sin querer, afecta a toda la institución
  - Solución: Agregar modal de confirmación más fuerte si `solo_lectura=false` pero `grupo IN ('SISTEMA', 'ACADEMICO')`

---

### ✅ FASE 26-B: Menús Dinámicos

**Estado:** ⚠️ INCOMPLETO

#### SQL Migration (022_menus_dinamicos.sql)
- ✅ `ades_menus` table con parent_id recursivo
- ✅ `ades_menu_roles` N:M para RBAC
- ✅ SEED: 17 menús base incluida estructura de "Administración" con 9 submenús

**❌ GAPS CRÍTICOS:**

1. **Migración depende de SEED anterior para parent_id**: La migración intenta insertar submenús usando `SELECT id FROM ades_menus WHERE label = 'Administración'`
   - **Problema:** Si la inserción del menú "Administración" falla (por violación de constraint, transacción rollback), toda la migración falla
   - **Riesgo de ejecución alto**
   - **Solución:** Usar CTE o temporary variable para capturar el id del padre
   ```sql
   WITH admin_menu AS (
     INSERT INTO ades_menus (label, icon, peso)
     VALUES ('Administración', 'pi pi-cog', 999)
     RETURNING id
   )
   INSERT INTO ades_menus (label, route, icon, parent_id, peso)
   SELECT 'Usuarios', '/admin#usuarios', 'pi pi-users', admin_menu.id, 10
   FROM admin_menu
   -- ... resto de submenús
   ```

2. **No hay inicialización de `ades_menu_roles`**: La tabla está creada pero vacía
   - **Problema:** Si no hay asignaciones, ningún rol ve menús
   - **Solución:** Agregar SEED que asigne menús a roles según nivel_acceso
   ```sql
   INSERT INTO ades_menu_roles (menu_id, rol_id)
   SELECT id, (SELECT id FROM ades_roles WHERE nivel_acceso = 0 LIMIT 1)
   FROM ades_menus
   WHERE is_active = TRUE;
   ```

3. **Ruta externa no soportada**: El documento menciona `route='external:url'` pero no hay parsing en el endpoint
   - **Problema:** Frontend recibe `{route: "external:https://..."}` y Angular router lo interpreta como ruta relativa
   - **Solución:** Documentar cómo el frontend debe manejar rutas externas
   ```typescript
   // En componente sidebar:
   navigarMenu(menu: Menu): void {
     if (menu.route?.startsWith('external:')) {
       window.open(menu.route.replace('external:', ''), '_blank');
     } else {
       this.router.navigate([menu.route]);
     }
   }
   ```

4. **No hay validación de ciclos en árbol**: parent_id referencia a otro menú pero no hay CHECK para evitar ciclos
   - **Problema:** Si hago `UPDATE ades_menus SET parent_id = id WHERE id = 5` (auto-referencia), crea ciclo infinito en frontend
   - **Solución:** Agregar trigger que rechace ciclos
   ```sql
   CREATE OR REPLACE FUNCTION validate_menu_hierarchy()
   RETURNS TRIGGER AS $$
   DECLARE has_cycle BOOLEAN;
   BEGIN
     -- Detectar ciclos: seguir parent_id recursivamente
     WITH RECURSIVE menu_chain AS (
       SELECT id, parent_id, 1 AS depth FROM ades_menus WHERE id = NEW.parent_id
       UNION ALL
       SELECT m.id, m.parent_id, depth + 1
       FROM ades_menus m
       JOIN menu_chain ON m.id = menu_chain.parent_id
       WHERE depth < 100  -- limitar profundidad
     )
     SELECT EXISTS(SELECT 1 FROM menu_chain WHERE id = NEW.id) INTO has_cycle;
     
     IF has_cycle THEN
       RAISE EXCEPTION 'Ciclo detectado en árbol de menús';
     END IF;
     RETURN NEW;
   END;
   $$ LANGUAGE plpgsql;
   ```

#### Endpoint FastAPI (menus.py)
- ✅ `GET /menus/mi-menu`: devuelve árbol filtrado por rol
- ⚠️ **Problema:** Función `_construir_arbol()` no ordena children por peso
  ```python
  def _construir_arbol(menus):
      # ...
      for item in por_id[m.parent_id]["children"]:
          # Falta: .sort(key=lambda x: x["peso"])
  ```

- ❌ **FALTA:** CRUD de menús desde admin UI
  - No hay `POST /menus`, `PATCH /menus/{id}`, etc.
  - El admin debe poder crear/editar menús sin SSH
  - **Necesario agregar:**
  ```python
  @router.post("/menus")
  async def crear_menu(...):
      # Validar peso, parent_id exists
      pass
  
  @router.patch("/menus/{menu_id}")
  async def actualizar_menu(...):
      # Validar no crear ciclos
      pass
  
  @router.delete("/menus/{menu_id}")
  async def eliminar_menu(...):
      # Soft-delete o hard-delete?
      pass
  ```

#### Componente Angular (admin.component.ts Tab "Menús")
- ❌ **NO EXISTE**: El documento no incluye template ni TypeScript para el tab de Menús
- **Necesario:** Crear editor visual de árbol de menús (panel izquierdo: árbol expandible, panel derecho: form para editar menu seleccionado)

---

### ✅ FASE 26-C: Privilegios + Multi-rol + Trazabilidad

**Estado:** ⚠️ PARCIALMENTE INCOMPLETO

#### SQL Migration (023_privilegios_multirol_trazabilidad.sql)
- ✅ `ades_privilegios` table: estructura correcta
- ✅ `ades_rol_privilegios` N:M: correcta
- ✅ `ades_usuario_roles` con peso: permite multi-rol
- ⚠️ SEED de privilegios: 10 privilegios base (pero faltan varios)
- ⚠️ Backfill de `ades_usuario_roles` desde `ades_usuarios.rol_id`: OK

**❌ GAPS:**

1. **Falta trigger que sincronice rol_id con ades_usuario_roles**:
   - Si actualizo `ades_usuarios.rol_id = X`, la tabla `ades_usuario_roles` no se actualiza
   - Al revés: si agrego rol a través de `ades_usuario_roles`, no actualiza `ades_usuarios.rol_id`
   - **Solución:** Crear trigger de sincronización bidireccional
   ```sql
   CREATE OR REPLACE FUNCTION sync_usuario_rol_principal()
   RETURNS TRIGGER AS $$
   BEGIN
     -- Si el rol principal cambia, actualizar ades_usuario_roles
     UPDATE ades_usuario_roles
     SET peso = 50  -- perder prioridad
     WHERE usuario_id = NEW.id AND rol_id != NEW.rol_id;
     
     INSERT INTO ades_usuario_roles (usuario_id, rol_id, peso)
     VALUES (NEW.id, NEW.rol_id, 100)  -- nuevo rol = prioritario
     ON CONFLICT (usuario_id, rol_id) DO UPDATE SET peso = 100;
     
     RETURN NEW;
   END;
   $$ LANGUAGE plpgsql;
   
   CREATE TRIGGER trigger_sync_rol_principal
   AFTER UPDATE OF rol_id ON ades_usuarios
   FOR EACH ROW
   EXECUTE FUNCTION sync_usuario_rol_principal();
   ```

2. **Trazabilidad enriquecida: campos no tienen defaults/constraints**:
   - `event_category`, `event_risk_level`, `security_outcome` no tienen CHECK constraints
   - `metadata` JSONB puede ser NULL o {} (inconsistencia)
   - **Solución:** Agregar constraints y function para guardar con valores defaults
   ```sql
   ALTER TABLE ades_audit_log
   ADD CONSTRAINT ck_category CHECK (event_category IN (...))
   ADD CONSTRAINT ck_risk_level CHECK (event_risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
   ADD CONSTRAINT ck_security_outcome CHECK (security_outcome IN ('ALLOWED', 'DENIED', 'PARTIAL'));
   ```

3. **No hay función helper para registrar eventos de audit con categoría y risk level**:
   - Cada endpoint debe pasar estos parámetros a mano
   - **Solución:** Crear function helper `log_security_event()`
   ```python
   async def log_security_event(
       db: AsyncSession,
       usuario_id: UUID,
       tabla: str,
       operacion: str,
       registro_id: UUID | None,
       categoria: str,  # 'AUTHENTICATION', 'AUTHORIZATION', etc.
       risk_level: str,  # 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'
       outcome: str,     # 'ALLOWED', 'DENIED', 'PARTIAL'
       metadata: dict | None = None
   ):
       audit = AuditLog(
           usuario_id=usuario_id,
           tabla=tabla,
           operacion=operacion,
           registro_id=registro_id,
           event_category=categoria,
           event_risk_level=risk_level,
           security_outcome=outcome,
           metadata=metadata or {}
       )
       db.add(audit)
   ```

4. **Privilegios seed incompletos**: Faltan privilegios críticos
   - ❌ `ALUMNOS_CREAR`, `ALUMNOS_EDITAR`, `ALUMNOS_ELIMINAR`
   - ❌ `PROFESORES_CREAR`, `PROFESORES_EDITAR`
   - ❌ `GRUPOS_CREAR`, `GRUPOS_EDITAR`
   - ❌ `CALIFICACIONES_VER`, `CALIFICACIONES_EXPORTAR`
   - ❌ `COMUNICADOS_CREAR`, `COMUNICADOS_ENVIAR_TODOS`
   - Necesario expandir lista de privilegios ~30+ items (una por recurso × CRUD)

#### Actualización de AdesUser y get_ades_user()
- ⚠️ **PROBLEMA:** El documento describe qué debe cambiar pero no proporciona código completo
- **Necesario:** Implementación real de `get_ades_user()` con:
  ```python
  async def get_ades_user(token: str, db: AsyncSession) -> AdesUser:
      # 1. Validar token OIDC
      usuario = await db.execute(
          select(Usuario).where(Usuario.oidc_sub == token_payload['sub'])
      ).scalar_one_or_none()
      
      # 2. Cargar rol activo
      rol = await db.get(Rol, usuario.rol_id)
      
      # 3. Cargar todos los privilegios del usuario (desde sus roles)
      privilegios = await db.execute(
          select(Privilegio.codigo)
          .join(RolPrivilegio)
          .join(UsuarioRol)
          .where(UsuarioRol.usuario_id == usuario.id)
          .distinct()
      )
      
      # 4. Cargar IDs de todos los roles
      todos_roles = await db.execute(
          select(Rol.id)
          .join(UsuarioRol)
          .where(UsuarioRol.usuario_id == usuario.id)
      )
      
      return AdesUser(
          usuario,
          rol,
          privilegios=[r[0] for r in privilegios],
          roles_ids=[r[0] for r in todos_roles]
      )
  ```

---

### ✅ FASE 26-D: Notificaciones In-App

**Estado:** ✅ COMPLETO pero minimal

#### SQL Migration (024_notificaciones_sistema.sql)
- ✅ `ades_notificaciones_sistema` table: estructura simple y correcta
- ✅ Índices apropiados (usuario + leído, fecha)

**⚠️ GAPS:**

1. **No hay tabla de plantillas de notificación**:
   - Todas las notificaciones usan el mismo template (titulo + mensaje)
   - Si necesito una notificación con CTA ("Haz clic aquí para continuar"), no hay campo
   - **Solución:** Agregar columnas opcionales
   ```sql
   ALTER TABLE ades_notificaciones_sistema
   ADD COLUMN IF NOT EXISTS accion_url VARCHAR(200),
   ADD COLUMN IF NOT EXISTS accion_label VARCHAR(50),
   ADD COLUMN IF NOT EXISTS icono VARCHAR(50);  -- 'pi pi-bell', etc.
   ```

2. **No hay categoría de notificación**:
   - Imposible filtrar por tipo (SISTEMA, ACADEMICA, DISCIPLINA)
   - **Solución:** Agregar columna
   ```sql
   ALTER TABLE ades_notificaciones_sistema
   ADD COLUMN IF NOT EXISTS categoria VARCHAR(50) DEFAULT 'SISTEMA';
   ```

3. **No hay timestamp de expiración**:
   - Las notificaciones quedan para siempre
   - **Solución:** Agregar columna y política de limpieza
   ```sql
   ALTER TABLE ades_notificaciones_sistema
   ADD COLUMN IF NOT EXISTS fecha_expiracion TIMESTAMPTZ;
   ```

#### Endpoints FastAPI (notificaciones.py)
- ✅ GET /mis-notificaciones (con filtro solo_no_leidas)
- ✅ PATCH /mi-notificaciones/{id}/leer
- ✅ PATCH /mis-notificaciones/leer-todas
- ❌ **FALTA:** Endpoint para crear notificación (solo existe función helper)
  - Para testing del admin, necesario: `POST /notificaciones` (admin solo)
  - ```python
    @router.post("/notificaciones")
    async def crear_notificacion_manual(
        usuario_id: UUID,
        titulo: str,
        mensaje: str,
        tipo: str = 'INFO',
        db: AsyncSession = Depends(get_db),
        user: AdesUser = Depends(get_ades_user),
    ):
        require_admin(user)
        # crear y retornar
    ```

#### Componente Angular (admin.component.ts + Header)
- ❌ **NO EXISTE:** Badge de notificaciones en el header
- ❌ **NO EXISTE:** Panel lateral para listar notificaciones
- **Necesario:**
  1. En `header.component.ts`: agregar botón campana con contador de no leídas
  2. En `notifications-panel.component.ts`: listar, marcar como leída, borrar

---

### ✅ FASE 26-E: SEPOMEX + Geo Component

**Estado:** ✅ COMPLETO en conceptos, ⚠️ en implementación

#### Verificación de schema SEPOMEX
- ⚠️ **Problema:** El documento asume que SEPOMEX ya está cargado
- **Riesgo:** Si no está cargado, todo falla
- **Solución:** Agregar checklist de validación en el punto 42 del checklist
  ```bash
  # Validar:
  docker compose exec postgres psql -U ades_admin -d ades \
    -c "SELECT COUNT(*) FROM sepomex.ctestados WHERE scd_vigente = TRUE;"
  # Esperado: 32 (estados) o más
  ```

#### Endpoints FastAPI (geo.py)
- ✅ `/geo/estados`: lista estados vigentes
- ✅ `/geo/municipios?estado_id=X`: filtra por estado
- ✅ `/geo/colonias?cp=X` o `?municipio_id=X`: busca colonias
- ✅ `/geo/buscar-cp/{cp}`: búsqueda rápida (estado + municipio + colonias en una llamada)

**⚠️ Observación:** Los queries usan `scd_vigente = TRUE` pero el campo puede llamarse diferente en la BD real
- **Validar:** Ejecutar `\d sepomex.ctestados` para ver estructura real

#### Componente Angular (selector-geo.component.ts)
- ✅ Estructura completa y reutilizable
- ⚠️ **Problema:** Propiedades @Input pero el componente también maneja estado interno
  - Si paso `@Input() estado = 15`, pero el componente carga municipios de 15, ¿qué pasa si cambio el input a 5?
  - **Solución:** Implementar `ngOnChanges()` para sincronizar
  ```typescript
  ngOnChanges(changes: SimpleChanges): void {
    if (changes['estado'] && !changes['estado'].firstChange) {
      this.estadoSeleccionado = this.estado;
      this.cargarMunicipios(this.estado);
    }
  }
  ```

- ⚠️ **Problema:** Validación: no verifica que CP tenga 5 dígitos antes de enviar
  ```typescript
  buscarPorCP(): void {
    if (!this.cpBusqueda || this.cpBusqueda.length !== 5 || !/^\d+$/.test(this.cpBusqueda)) {
      this.msg.add({ severity: 'warn', summary: 'CP inválido' });
      return;
    }
  ```

---

## 🔴 RIESGOS CRÍTICOS IDENTIFICADOS

### R1: Encriptación de Passwords (CRÍTICO)
- **Impacto:** Si no se implementa el trigger de encriptación, tokens API se guardan en plain text
- **Severidad:** 🔴 CRÍTICA
- **Mitigation:** Implementar pgcrypto trigger antes de ejecutar la FASE 26-A
- **Effort:** 30 min

### R2: Ciclos en árbol de menús (ALTO)
- **Impacto:** Frontend puede entrar en ciclo infinito renderizando menú
- **Severidad:** 🟠 ALTO
- **Mitigation:** Agregar trigger de validación de ciclos o CTE recursiva con límite
- **Effort:** 1 hora

### R3: Falta de CRUD de menús (ALTO)
- **Impacto:** Admin no puede editar menús sin SSH/BD. No es 100% configurable
- **Severidad:** 🟠 ALTO
- **Mitigation:** Implementar endpoints CRUD + UI admin
- **Effort:** 3 horas

### R4: Sincronización bidireccional rol principal (MEDIO)
- **Impacto:** `ades_usuarios.rol_id` y `ades_usuario_roles` pueden quedar desincronizados
- **Severidad:** 🟡 MEDIO
- **Mitigation:** Trigger de sincronización automática
- **Effort:** 2 horas

### R5: SEPOMEX no cargado (MEDIO)
- **Impacto:** Si schema SEPOMEX no existe, endpoints de geo fallan con 500
- **Severidad:** 🟡 MEDIO
- **Mitigation:** Validación previa y guía de carga de SEPOMEX
- **Effort:** 1 hora (validación)

### R6: Falta de privilegios granulares completos (MEDIO)
- **Impacto:** FASE 26-C incompleta. CRUD de alumnos/grupos no usan privilegios
- **Severidad:** 🟡 MEDIO
- **Mitigation:** Expandir seed de privilegios a ~40 items (CRUD por recurso)
- **Effort:** 2 horas

### R7: Validación de tipos de variables insuficiente (BAJO)
- **Impacto:** Guarda JSON inválido o número como string. UX confusa en admin
- **Severidad:** 🟢 BAJO
- **Mitigation:** Validación completa en endpoint + front
- **Effort:** 1 hora

---

## 📊 MATRIZ DE COMPLETITUD

| Fase | Componente | Completitud | Estado | Riesgo | Esfuerzo Faltante |
|------|-----------|-------------|--------|--------|-------------------|
| 26-A | SQL Migration | 95% | LISTO | BAJO | 30 min (encriptación) |
| 26-A | Models Python | 100% | LISTO | BAJO | 0 min |
| 26-A | Schemas Pydantic | 85% | LISTO | BAJO | 30 min (validación) |
| 26-A | Endpoints FastAPI | 70% | LISTO | MEDIO | 2 horas (DELETE, reorder, búsqueda) |
| 26-A | Componente Angular | 80% | LISTO | BAJO | 1 hora (JSON validation, confirmación) |
| 26-B | SQL Migration | 60% | INCOMPLETO | **ALTO** | 1 hora (refactor + ciclos) |
| 26-B | Endpoints FastAPI | 50% | INCOMPLETO | **ALTO** | 3 horas (CRUD menús) |
| 26-B | Componente Angular | 0% | NO EXISTE | **CRÍTICO** | 4 horas |
| 26-C | SQL Migration | 90% | LISTO | BAJO | 1 hora (triggers sync) |
| 26-C | AdesUser actualizado | 30% | INCOMPLETO | **ALTO** | 2 horas (get_ades_user) |
| 26-C | Privilegios seed | 40% | INCOMPLETO | **MEDIO** | 1 hora (expandir lista) |
| 26-C | Componente Angular | 0% | NO EXISTE | **MEDIO** | 2 horas (tab privilegios) |
| 26-D | SQL Migration | 85% | LISTO | BAJO | 30 min (enhancements) |
| 26-D | Endpoints FastAPI | 90% | LISTO | BAJO | 15 min (POST manual) |
| 26-D | Componente Angular | 0% | NO EXISTE | BAJO | 2 horas (header badge + panel) |
| 26-E | SQL / SEPOMEX | 70% | LISTO | **MEDIO** | 1 hora (validación) |
| 26-E | Endpoints FastAPI | 100% | LISTO | BAJO | 0 min |
| 26-E | Componente Angular | 95% | LISTO | BAJO | 30 min (ngOnChanges) |

**Total Completitud:** 70%  
**Total Esfuerzo Faltante:** ~30 horas (distribución: 8h crítico, 12h alto, 10h medio, bajo)

---

## 🛠️ PLAN DE REMEDIACIÓN PRIORITARIO

### FASE 1 (CRÍTICO — 8 horas): Preparación + Completitud 26-A
1. ✅ Implementar trigger de encriptación pgcrypto para PASSWORD variables (1h)
2. ✅ Expandir validación de tipos en schemas Pydantic (1h)
3. ✅ Agregar endpoints faltantes (DELETE, reorder, búsqueda) (2h)
4. ✅ Mejorar componente Angular variables con JSON validation (1h)
5. ✅ Testing completo FASE 26-A (2h)

### FASE 2 (ALTO — 8 horas): Completitud 26-B + 26-C Parcial
1. ✅ Refactor migración 022 (menus) para evitar inserciones secuenciales (1h)
2. ✅ Implementar CRUD de menús en FastAPI (3h)
3. ✅ Crear tab Menús en Angular admin (2h)
4. ✅ Implementar get_ades_user() completo con privilegios (2h)

### FASE 3 (MEDIO — 6 horas): Completitud 26-C Final + 26-D Angular
1. ✅ Expandir seed de privilegios a ~40 items (1h)
2. ✅ Implementar trigger de sincronización rol_id ↔ ades_usuario_roles (1h)
3. ✅ Crear tab Privilegios en Angular admin (2h)
4. ✅ Agregar header badge + notification panel Angular (2h)

### FASE 4 (BAJO — 2 horas): Validación SEPOMEX + Pulido
1. ✅ Validar schema SEPOMEX y crear script de carga si falta (1h)
2. ✅ Mejorar selector-geo con ngOnChanges (30 min)
3. ✅ Testing integración FASE 26-E (30 min)

---

## ✅ CHECKLIST AJUSTADO PARA EJECUCIÓN

Reemplazar los puntos 42-54 del checklist original con este refinado:

```
□ 42. PRE-VALIDACIÓN
   □ Ejecutar: docker compose exec postgres psql -c "\dt sepomex.*"
   □ Si está vacío: cargar scripts SEPOMEX 01-05
   □ Verificar: SELECT COUNT(*) FROM ades_variables_sistema; (debe ser > 0 después de 026)

□ 43. FASE 26-A FINAL CHECKS
   □ Crear trigger `encrypt_variable_value()` para PASSWORD + TEST
   □ Agregar endpoint DELETE /catalogos/{id} y /catalogos/items/{id} con soft-delete
   □ Agregar endpoint POST /catalogos/{id}/items/reorder (array de reorder)
   □ Agregar búsqueda en GET /config/variables (?search=, ?tipo_valor=, ?grupo=)
   □ Mejorar validación JSON/numero/fecha en endpoint PATCH /config/variables/{nombre}
   □ Angular: agregar esJsonValido() y confirmación para cambios críticos
   □ Test: POST /config/variables con PASSWORD → verificar encriptación en BD

□ 44. FASE 26-B COMPLETO
   □ Refactor migración 022: usar CTE para insertar menús y submenús atomicamente
   □ Agregar trigger `validate_menu_hierarchy()` para detectar ciclos
   □ Implementar CRUD endpoints: POST/PATCH/DELETE /menus
   □ Seed `ades_menu_roles` inicial: asignar menús por rol
   □ Crear admin tab "Menús": árbol editable lado izquierdo + form lado derecho
   □ Actualizar header/sidebar para consumir /menus/mi-menu en vez de hardcoded
   □ Test: GET /menus/mi-menu como admin → debe devolver todos los menús

□ 45. FASE 26-C COMPLETO
   □ Expandir seed ades_privilegios a ~40 items (CRUD por recurso principal)
   □ Crear trigger `sync_usuario_rol_principal()` para sincronizar rol_id
   □ Implementar get_ades_user() completo: cargar privilegios + roles
   □ Crear AuditLog helper function `log_security_event()`
   □ Crear admin tab "Privilegios": selector de rol + checkboxes de privilegios
   □ Crear admin tab "Usuarios": selector usuario + checkboxes de roles + peso
   □ Test: Asignar privilegio BOLETAS_GENERAR a usuario → verificar en AdesUser

□ 46. FASE 26-D COMPONENTES ANGULAR
   □ Crear header-notifications.component.ts: botón campana + badge contador
   □ Crear notifications-panel.component.ts: dropdown con listado scrolleable
   □ Integrar en app-layout principal
   □ Test: Crear notificación via admin → debe aparecer en badge

□ 47. FASE 26-E FINAL
   □ Validar SEPOMEX: SELECT COUNT(*) FROM sepomex.ctestados; (>= 30)
   □ Mejorar selector-geo.component.ts: agregar ngOnChanges para sincronización
   □ Integrar <app-selector-geo> en formularios: alumnos, profesores, contactos-familiares, planteles
   □ Test: Ingresar CP 50100 → debe autocompletar estado + municipio + colonias

□ 48. CIERRE
   □ Ejecutar build completo: cd frontend && npm run build (0 errores TS)
   □ Ejecutar tests backend: pytest tests/ -v
   □ Verificar migraciones: docker compose exec postgres psql -l | grep ades
   □ Commit: "feat: FASE 26 — integración starter en ADES (variables, catálogos, menús, privilegios, multi-rol, notificaciones, geo)"
   □ Actualizar openspec.yaml, .agent/STATE.md
```

---

## 📌 CONCLUSIÓN

**El plan FASE_26_INTEGRACION_STARTER.md es sólido en estructura pero está al ~70% de completitud real.**

**Riesgos que bloquearían ejecución:**
1. ❌ Encriptación de PASSWORD no implementada (usar pgcrypto trigger)
2. ❌ CRUD de menús no existe (admin no puede editar menús)
3. ❌ Componentes Angular para menús/privilegios/notificaciones no existen
4. ❌ Validación de tipos de variables superficial (JSON/numero/fecha)
5. ❌ Ciclos en árbol de menús sin detectar (riesgo UX)

**Esfuerzo total real:** 
- Documento dice: "6-8 horas ejecución"
- **Realidad:** ~30 horas distribuidas en prioridades

**Recomendación:**
- Ejecutar en 4 sesiones de Claude Code de 8-10 horas cada una
- Sesión 1: FASE 26-A completo (variables + catálogos)
- Sesión 2: FASE 26-B completo (menús + CRUD)
- Sesión 3: FASE 26-C completo (privilegios + multi-rol + audit)
- Sesión 4: FASE 26-D/E + integración (notificaciones + geo + testing integración)
