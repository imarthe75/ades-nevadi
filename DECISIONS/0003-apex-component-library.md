# ADR-0003 — APEX Component Library como sistema de diseño

**Estado:** Aceptado  
**Fecha:** 2026-06-10  
**Autor:** Agente Residente v2.0

## Contexto

El frontend usaba PrimeNG directamente en cada componente con `providers: [MessageService]`,
`ToastModule`, y `p-toast` locales. Esto creaba instancias aisladas del toast, notificaciones
que no aparecían, y duplicación de código cross-componente.

Adicionalmente, el proyecto tiene una librería Angular interna (`apex-component-library`)
con 31+ componentes que emulan Oracle APEX (interactive grid, modal dialog, toast, breadcrumb,
form, search, etc.) y que no estaban integrados en el sistema principal.

## Decisión

1. **`MessageService` global**: Proveer en `app.config.ts` (root-level), no por componente.
2. **`apex-toast-container`**: Único en `ShellComponent`, reemplaza todos los `<p-toast />` locales.
3. **`ApexNotificationService`**: Inyectar en todos los feature components; eliminar `inject(MessageService)` directo.
4. **Menú estático**: Reemplazar `GET /api/v1/menus/mi-menu` con `_allNavGroups` hardcoded en `ShellComponent`, filtrado por `ctx.nivelAcceso()` via `computed()`.
5. **Signals**: Toda visibilidad de dialogs/drawers usa `signal(false)` + `[visible]="x()" (visibleChange)="x.set($event)"`.

## Consecuencias

- Toast global funciona independientemente del componente activo
- Eliminados 20 `providers: [MessageService]` locales
- Menú con 11 secciones (Principal, Académico, Operaciones, Comunicación, Gradebook, Recursos, Mi Familia, Inteligencia, Reportes, Sistema, Ayuda) filtrado por rol
- Angular 22 zoneless: solo Signals disparan change detection en OnPush
- La tabla `ades_menus` y endpoint `/menus/mi-menu` siguen en BD/API para uso futuro (reportes, auditoría de acceso)
