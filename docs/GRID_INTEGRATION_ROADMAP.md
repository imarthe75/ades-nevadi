# Interactive Grid Integration Roadmap

**Status**: FASE 25 — Module-by-module integration  
**Last Updated**: 2026-06-09  
**Priority**: Medium (improves UX consistency and code reusability)

## Completed (✅)

- [x] **Alumnos** — 8 columns (matricula, nombre_completo, curp, nss, nivel, grado, grupo, fecha_ingreso)
- [x] **Profesores** — 6 columns (numero_empleado, nombre_completo, rfc, tipo_contrato, especialidad, turno)
- [x] **Grupos** — 4 columns (nivel_y_grado, nombre_grupo, ocupacion, estado) + admin edit

---

## Priority 1 — Read-Only Simple Tables (Easy integration)

### 1. **Evaluaciones** → Integrable ✓

**Current Pattern**: p-table with read-only columns  
**Data**: Evaluación objects (nombre, descripcion, fecha, grupo, estado)  
**Integration Effort**: LOW (30 min)  
**Steps**:
1. Import `InteractiveGridComponent`, `ColumnConfig`
2. Define columns (7 total):
   ```typescript
   [
     { field: 'nombre', header: 'Evaluación', sortable: true, filterable: true, width: '200px' },
     { field: 'grupo_label', header: 'Grupo', sortable: true, filterable: true, width: '150px' },
     { field: 'materia_nombre', header: 'Materia', sortable: true, filterable: true, width: '150px' },
     { field: 'fecha_evaluacion', header: 'Fecha', sortable: true, filterable: false, width: '120px' },
     { field: 'tipo', header: 'Tipo', sortable: true, filterable: true, width: '100px' },
     { field: 'puntaje_maximo', header: 'Puntaje Max', sortable: false, filterable: false, width: '100px' },
     { field: 'estado', header: 'Estado', sortable: true, filterable: true, width: '100px' },
   ]
   ```
3. Transform API response: map data with `_original` field
4. Replace `<p-table>` with `<app-interactive-grid>`
5. Test: filters, sorting, exports

**File to update**: `frontend/src/app/features/evaluaciones/evaluaciones.component.ts`

---

### 2. **Comunicados** → Integrable ✓

**Current Pattern**: p-table with read-only display  
**Data**: Comunicado objects (asunto, contenido, fecha, remitente, destinatarios)  
**Integration Effort**: LOW (30 min)  
**Columns** (7 total):
```typescript
{ field: 'asunto', header: 'Asunto', sortable: true, filterable: true, width: '220px' },
{ field: 'remitente_nombre', header: 'De', sortable: true, filterable: true, width: '150px' },
{ field: 'fecha_envio', header: 'Fecha', sortable: true, filterable: false, width: '120px' },
{ field: 'tipo_comunicado', header: 'Tipo', sortable: true, filterable: true, width: '120px' },
{ field: 'leido', header: 'Leído', sortable: false, filterable: false, width: '80px' },
{ field: 'destinatarios_count', header: 'Destinatarios', sortable: false, filterable: false, width: '120px' },
{ field: 'fecha_vencimiento', header: 'Vence', sortable: true, filterable: false, width: '120px' },
```

**File to update**: `frontend/src/app/features/comunicados/comunicados.component.ts`

---

## Priority 2 — Complex Tables with Specialized Patterns (Moderate effort)

### 3. **Tareas** → Partial Integration

**Current Pattern**: Multi-section component (Tareas table + Entregas table + Stats)  
**Data**: Tarea objects (titulo, fecha_entrega, puntaje_maximo)  
**Integration Effort**: MEDIUM (1 hour)  
**Option A**: Keep two separate tables (current)  
**Option B**: Merge into single grid with grouping/filtering  

**Decision**: Keep as-is for now. Tareas has complex state management (entregas, uploads).  
**Future**: Phase 26 could introduce "grouped grid" component for multi-table views.

---

### 4. **Asistencias** → Keep Current Pattern

**Current Pattern**: Status button toggling (PRESENTE, AUSENTE, TARDE, JUSTIFICADO)  
**Integration Effort**: HIGH (requires custom cell editor)  
**Decision**: **Defer** — Asistencias uses inline button-based editing which doesn't fit standard Grid pattern.  
**Future**: Phase 27 could add `CellEditorType: 'button-group'` to InteractiveGridComponent.

---

## Priority 3 — Admin Modules (Needs RBAC guards)

### 5. **Usuarios** (Admin) → Integrable ✓

**Current Pattern**: p-table in admin module (roles, permissions, is_active)  
**Integration Effort**: LOW-MEDIUM (40 min)  
**Columns** (8 total):
```typescript
{ field: 'oidc_email', header: 'Email', sortable: true, filterable: true, width: '200px' },
{ field: 'oidc_name', header: 'Nombre Completo', sortable: true, filterable: true, width: '220px' },
{ field: 'rol_nombre', header: 'Rol', sortable: true, filterable: true, width: '150px' },
{ field: 'nivel_acceso', header: 'Nivel', sortable: true, filterable: true, width: '100px' },
{ field: 'es_vigente', header: 'Activo', sortable: true, filterable: true, width: '100px' },
{ field: 'fecha_ultimo_acceso', header: 'Último Acceso', sortable: true, filterable: false, width: '150px' },
{ field: 'is_superuser', header: 'Superuser', sortable: false, filterable: true, width: '100px' },
{ field: 'fecha_creacion', header: 'Creado', sortable: true, filterable: false, width: '120px' },
```

**File to update**: `frontend/src/app/features/admin/admin.component.ts`

---

## Implementation Order

**Week 1 (Current)**:
- [x] Alumnos, Profesores, Grupos (COMPLETED)
- [x] SSL Certificate for notify.ades.setag.mx (COMPLETED)

**Week 2 (Recommended)**:
1. [ ] Evaluaciones (30 min) — Low effort, high value
2. [ ] Comunicados (30 min) — Low effort, high value
3. [ ] Usuarios/Admin (40 min) — Medium effort, admin-only but important

**Week 3+**:
- [ ] Asistencias (defer until Phase 27 with button-group editor)
- [ ] Tareas (keep current pattern OR redesign in Phase 26)

---

## Template for New Module Integration

### Step 1: Import
```typescript
import { InteractiveGridComponent, ColumnConfig } from '@shared/components/interactive-grid';
```

### Step 2: Define Columns
```typescript
columnas: ColumnConfig[] = [
  { field: 'nombre', header: 'Nombre', sortable: true, filterable: true, width: '200px' },
  { field: 'estado', header: 'Estado', sortable: true, filterable: true, width: '100px' },
  { field: 'fecha', header: 'Fecha', sortable: true, filterable: false, width: '120px' },
];
```

### Step 3: Add Signal for Grid Data
```typescript
datosMiModulo = signal<any[]>([]);
```

### Step 4: Transform API Data
```typescript
this.miModulosDatos.set(resp.data.map(item => ({
  id: item.id,
  nombre: item.nombre,
  estado: item.estado ? 'Activo' : 'Inactivo',
  fecha: item.fecha,
  _original: item, // Store original for detail views
})));
```

### Step 5: Replace Template
```html
<!-- Before -->
<p-table [value]="miModulos()" ...>
  <ng-template pTemplate="header"> ... </ng-template>
  <ng-template pTemplate="body"> ... </ng-template>
</p-table>

<!-- After -->
<app-interactive-grid
  [data]="datosMiModulo()"
  [columns]="columnas"
  [loading]="loading()"
  (rowSelected)="onRowSelect($event)"
/>
```

### Step 6: Handle Row Selection
```typescript
onRowSelect(row: any): void {
  const item = row._original || this.miModulos().find(x => x.id === row.id);
  // Open detail view, edit dialog, etc.
}
```

---

## Architecture Notes

### Data Transformation Pattern
All grid data should include:
- **Display fields**: Formatted strings for UI display
- **Sortable fields**: Comparable types (strings, numbers, dates)
- **Filterable fields**: Text fields that make sense to search
- **_original field**: Reference to original API object for detail operations

### Column Configuration Best Practices
- **Width**: Set explicit widths for predictable layout
  - Small columns (status): 80-100px
  - Medium columns (names): 150-200px
  - Large columns (description): 220-300px
- **Sortable**: true for most fields except complex objects
- **Filterable**: true only for text/simple fields (not dates or numbers typically)

### Performance Considerations
- Grid renders up to 500 rows per page without issue
- Use pagination for >1000 rows
- Lazy loading implemented via parent component (not grid component)
- Client-side filtering works best with <5000 items

---

## Testing Checklist

For each integrated module:
- [ ] Grid renders without errors
- [ ] Column headers visible and correct
- [ ] Data displayed in rows correctly
- [ ] Sortable columns work (click header to sort asc/desc)
- [ ] Header filters work (type in filter input)
- [ ] Column chooser opens (click list icon in toolbar)
- [ ] CSV export works (click download icon, check file content)
- [ ] Pagination works (select 10/20/50/100 rows per page)
- [ ] Row selection works (if rowSelected handler implemented)
- [ ] Responsive on mobile (grid should scroll horizontally)
- [ ] No console errors
- [ ] No memory leaks (check DevTools)

---

## Spec References

- `spec/modules/fase-24-interactive-grid/specification.md`
- `spec/standards/api-design.md`
- `.agent/OPENSPEC_GUIDE.md`

## Next Review

**Date**: 2026-06-16  
**Focus**: Evaluate integration progress, adjust timeline if needed

---

**Maintained By**: ADES Development Team  
**Last Updated**: 2026-06-09
