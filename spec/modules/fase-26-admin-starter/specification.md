# FASE 26 — Admin Starter (Variables, Catálogos, Menús, Privilegios, Geo)

## Resumen
La Fase 26 consolida funcionalidades base extraídas de un proyecto de referencia (starter), adaptándolas a la arquitectura de ADES Instituto Nevadi. Esta fase está dividida en submódulos fundamentales:

### 26-A: Variables y Catálogos Dinámicos
- Migración: `021_variables_catalogos.sql` (Sin uso de pgcrypto).
- Permite la configuración del sistema en tiempo de ejecución.
- Tablas: `ades_variables_sistema`, `ades_catalogos_sistema`, `ades_catalogo_items_sistema`.
- APIs: `/api/v1/config/variables`, `/api/v1/catalogos`.

### 26-B: Menús Dinámicos
- Migración: `022_menus_dinamicos.sql`.
- Permite renderizar la barra de navegación del shell en base al rol activo.
- Tablas: `ades_menus`, `ades_menu_roles`.
- APIs: `/api/v1/menus/mi-menu`.

### 26-C: Privilegios Granulares y Sincronización Multi-rol (JIT)
- Migración: `023_privilegios_multirol_trazabilidad.sql`.
- Habilita la autenticación transparente con Hashicorp Vault / Authentik, sincronizando grupos a roles dinámicamente (`get_ades_user`).
- Amplía la trazabilidad de la auditoría.
- Tablas: `ades_privilegios`, `ades_rol_privilegios`, `ades_usuario_roles`.

### 26-D: Notificaciones In-App del Sistema
- Migración: `024_notificaciones_sistema.sql`.
- Notificaciones de sistema con UI nativa (APEX-style).
- Tablas: `ades_notificaciones_sistema`.
- APIs: `/api/v1/notificaciones`.

### 26-E: Componente Geográfico y SEPOMEX
- Router `/api/v1/geo` para consultar `sepomex.*`.
- Componente frontend `<app-selector-geo>`.
- Provee autocompletado en cascada de ubicaciones postales para domicilios.
