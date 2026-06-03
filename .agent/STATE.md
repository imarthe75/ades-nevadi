# 📈 Estado y Bitácora del Agente Residente (STATE.md)

Este documento es el diario de vida y bitácora del agente. Debe ser leído en el **Rito de Inicio** y actualizado en el **Rito de Cierre**.

## 🔄 Rito de Inicio (Bootstrapping)
*Cada vez que inicies sesión o seas llamado, ejecuta estos pasos:*
1. Lee tu leyes en `.agent/AGENT.md`.
2. Lee tu propósito en `.agent/CONTEXT.md`.
3. Revisa la lista de pendientes de la última sesión en la sección **"Próximos Pasos"** de este archivo.
4. Verifica que los servicios de Valkey y Postgres estén saludables.

---

## 📅 Bitácora de Sesión Actual

### 🔑 Estado del Agente:
- **Última Conexión:** 2026-06-03
- **Estado Cognitivo:** Operacional ✅
- **ADRs Registrados:** 0001 (ADR Inicial de Génesis)

### 🏗️ Estado de Infraestructura (2026-06-03):

| Servicio           | Estado    | Notas |
|--------------------|-----------|-------|
| PostgreSQL 18      | ✅ healthy | 57 tablas, seeds cargados (54 grupos, 80 profesores, 1620 alumnos, ciclo 2026-2027) |
| Valkey 9.1.0       | ✅ healthy | |
| MinIO              | ✅ healthy | |
| Authentik server   | ✅ healthy | 2026.5.2 · accesible en https://auth.ades.setag.mx/ |
| Authentik worker   | ✅ healthy | |
| nginx              | ✅ running | TLS activo (Let's Encrypt) · bind mount /etc/letsencrypt |
| ades-api           | ⏳ pendiente | backend no construido aún |
| ades-frontend      | ⏳ pendiente | frontend no construido aún |
| superset           | ⏳ pendiente | imagen no construida aún |

### 🛠️ Tareas Completadas hoy:
- [x] Creación de la estructura del framework base.
- [x] Configuración de docker-compose (versiones fijas: pg18, valkey 9.1.0, authentik 2026.5.2, superset 6.1.0).
- [x] Redacción de leyes operacionales (AGENT.md) y contexto (CONTEXT.md).
- [x] Schema completo + seeds cargados en PostgreSQL 18.
- [x] Corrección nginx: bind mount /etc/letsencrypt, eliminado depends_on de servicios inexistentes.
- [x] Corrección authentik: typo AUTHENTIK_SECRET__KEY → AUTHENTIK_SECRET_KEY.
- [x] Authentik movido a subdominio propio https://auth.ades.setag.mx/ (cert emitido 2026-06-03, expira 2026-09-01).
- [x] nginx.conf limpio: ades.setag.mx = frontend/API, auth.ades.setag.mx = Authentik en raíz.

### 🚨 Lecciones Aprendidas:
- Los certs Let's Encrypt deben montarse como bind mount al host (`/etc/letsencrypt:/etc/letsencrypt:ro`), no como volumen Docker nombrado — el volumen queda vacío si el cert fue emitido fuera del ciclo de vida del compose.
- La variable de Authentik es `AUTHENTIK_SECRET_KEY` (guion simple), no `AUTHENTIK_SECRET__KEY`.
- `depends_on` en nginx debe incluir solo servicios que realmente existen y arrancan; agregar services no construidos bloquea el arranque de nginx.

### 🚀 Próximos Pasos:
- [ ] Setup inicial Authentik: cambiar contraseña akadmin, configurar dominio https://auth.ades.setag.mx/.
- [ ] Configurar Google Workspace SSO en Authentik para personal @institutonevadi.edu.mx.
- [ ] Crear aplicación OIDC `ades-frontend` en Authentik.
- [ ] Crear aplicación OIDC `superset` en Authentik.
- [x] Schema migrado a UUID v7 (`uuidv7()` nativo PG18) — todos los PKs y FKs.
- [x] Estructura académica completa: Ixtapan sec 3°, Metepec prep sem 1-6, Tenancingo prep sem 1-6.
- [x] 39 grados, 78 grupos (66 activos), 168 profesores, 1980 alumnos, 2054 usuarios.
- [x] Seed 002 v4 + 003 v4 con is_active en grupos futuros y auth local para docentes.
- [x] Análisis Moodle: 15 módulos identificados e incorporados al CONTEXT.md y README.
- [x] README v2.0 expansivo (~450 líneas): stack, DDD, fases, BD, instalación, troubleshooting, roadmap.
- [ ] Construir imagen ades-api (FastAPI backend — FASE 1).
- [ ] Construir imagen ades-frontend (Angular — FASE 1).
