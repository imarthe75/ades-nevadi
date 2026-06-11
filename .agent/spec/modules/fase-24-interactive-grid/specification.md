# FASE 24 — Interactive Grid APEX-Style & Gestión de Padres

**Version**: 1.0.0  
**Status**: In Progress  
**Last Updated**: 2026-06-09  
**Completion**: ~80% (Interactive Grid infrastructure complete, integration pending)

## Overview

FASE 24 delivers APEX-style interactive data grids and parent/family management features to modernize the ADES user interface and enable concurrent edit handling through optimistic locking.

## Features

### 1. Interactive Grid Component (APEX-Style)

#### Capabilities

- ✅ **Sortable Columns**: Click header to sort ascending/descending
- ✅ **Header Filters**: Per-column search with live filtering
- ✅ **Column Chooser**: Show/hide columns dynamically via dialog
- ✅ **Inline Editing**: Edit cells in-place with type validation
- ✅ **CSV Export**: Download filtered data as CSV
- ✅ **Pagination**: Configurable (10, 20, 50, 100 rows)
- ✅ **Global Search**: Search across all visible columns

#### Component Files

```
frontend/src/app/shared/components/interactive-grid/
├── interactive-grid.component.ts   # Main component (180+ lines)
├── grid-utils.ts                   # Schema definitions for 9 entities
└── grid.service.ts                 # Centralized grid logic
```

#### Usage Pattern

```typescript
import { InteractiveGridComponent } from '@shared/components/interactive-grid';
import { getGridColumns } from '@shared/components/interactive-grid/grid-utils';

@Component({
  imports: [InteractiveGridComponent]
})
export class AlumnosComponent {
  datos = signal([]);
  columnas = getGridColumns('alumnos');
  loading = signal(false);

  onRowSelect(row: any) { /* handle edit */ }
  onRowEdit(row: any) { /* save */ }
}
```

#### Pre-configured Schemas

| Entity | Columns | Sortable | Filterable | Editable |
|---|---|---|---|---|
| Alumnos | 8 | ✅ | ✅ | ❌ |
| Profesores | 6 | ✅ | ✅ | ❌ |
| Calificaciones | 6 | ✅ | ✅ | ✅ (calificacion) |
| Grupos | 8 | ✅ | ✅ | ❌ |
| Asistencias | 4 | ✅ | ✅ | ✅ (asistencia) |
| Usuarios | 8 | ✅ | ✅ | ❌ |
| Tareas | 7 | ✅ | ✅ | ❌ |
| Evaluaciones | 7 | ✅ | ✅ | ❌ |
| Comunicados | 7 | ✅ | ✅ | ❌ |

### 2. Gestión de Padres de Familia (Parent Management)

#### Features

- ✅ CRUD operations for family contacts
- ✅ Relationship type categorization (Padre, Madre, Tutor, etc.)
- ✅ Permission flags (tutor legal, emergency contact, can pick up)
- ✅ Shared custody support (toma_decision_conjunta)
- ✅ Occupational & education level tracking

#### Routes

```
/padres-admin — Accessible to roleGuard(1) [ADMIN/DIRECTOR]
```

#### Endpoints Used

```
GET    /api/v1/contactos?estudiante_id=<uuid>  — List contacts
POST   /api/v1/contactos                        — Create contact
PATCH  /api/v1/contactos/{id}                   — Update contact
DELETE /api/v1/contactos/{id}                   — Delete contact
```

### 3. Optimistic Locking (Row-Version Handling)

#### Backend Support

```python
# File: backend/app/core/optimistic_locking.py
from app.core.optimistic_locking import check_row_version, RowVersionConflict

@router.patch("/entity/{id}")
async def update(id: uuid.UUID, payload: UpdatePayload, db: AsyncSession):
    entity = await db.get(Entity, id)
    check_row_version(entity, payload.row_version)  # Raises HTTPException(409) on conflict
    entity.field = payload.field
    await db.commit()
```

#### Conflict Response

```json
HTTP 409 Conflict
{
  "status": "conflict",
  "message": "This record was modified by another user",
  "current_version": 7,
  "received_version": 5,
  "current_record": { /* latest data */ }
}
```

#### Row Version Behavior

- Auto-incremented on UPDATE via trigger `fn_auditoria_biu()`
- Included in all GET responses
- Required in PATCH/PUT request body
- Incremented on save regardless of changes (standard pattern)

## Database Changes (Migration 017)

### New Columns

| Table | Column | Type | Purpose |
|---|---|---|---|
| ades_usuarios | nivel_acceso | INTEGER | RBAC cache (0-5) |
| ades_estudiantes | folio_sep | VARCHAR | SEP identification |
| ades_estudiantes | tipo_alumno | VARCHAR | NUEVO/REGULAR/REINGRESO |
| ades_contactos_familiares | toma_decision_conjunta | BOOLEAN | Shared custody flag |
| ades_contactos_familiares | grado_responsabilidad | VARCHAR | PRINCIPAL/SECUNDARIO/CONSULTA |

### Indices Created

```sql
CREATE INDEX idx_ades_usuarios_nivel_acceso ON ades_usuarios(nivel_acceso);
CREATE INDEX idx_ades_estudiantes_tipo ON ades_estudiantes(tipo_alumno);
CREATE INDEX idx_ades_contactos_fam_decision ON ades_contactos_familiares(toma_decision_conjunta);
```

### Data Backfill

- 3,483 usuarios: nivel_acceso assigned from rol.nivel_acceso

## Implementation Status

### ✅ Completed

- [x] Interactive Grid component (interactive-grid.component.ts)
- [x] Grid utilities & schemas (grid-utils.ts)
- [x] Grid service (grid.service.ts)
- [x] Parent management component (padres-admin.component.ts)
- [x] Optimistic locking helper (optimistic_locking.py)
- [x] Migration 017 (schema changes)
- [x] FASE 24 documentation (CHANGELOG_FASE24.md)
- [x] OpenSpec integration

### ⏳ Pending (FASE 25)

- [ ] Integrate InteractiveGridComponent into alumnos.component.ts
- [ ] Integrate InteractiveGridComponent into profesores.component.ts
- [ ] Integrate InteractiveGridComponent into calificaciones.component.ts
- [ ] Integrate InteractiveGridComponent into grupos.component.ts
- [ ] Integrate InteractiveGridComponent into asistencias.component.ts
- [ ] Add check_row_version() to all PATCH/PUT endpoints
- [ ] End-to-end testing of concurrent edits
- [ ] Performance testing with >50k rows

## Testing Strategy

### Unit Tests

```bash
# Frontend
ng test --include='**/interactive-grid.spec.ts'
ng test --include='**/grid.service.spec.ts'

# Backend
pytest backend/tests/test_optimistic_locking.py
```

### E2E Tests

```bash
# Concurrent edit simulation
ng e2e --specs='**/concurrent-edit.e2e-spec.ts'
```

### Performance Testing

- [ ] Large dataset: >10k rows rendering
- [ ] Filter performance: complex queries
- [ ] Concurrent users: 5+ simultaneous edits
- [ ] Memory profiling: no memory leaks

## Breaking Changes

None. All changes are backwards-compatible.

## Migration Guide

For existing modules integrating Interactive Grid:

1. Import `InteractiveGridComponent`
2. Replace `<p-table>` with `<app-interactive-grid>`
3. Define `columnas = getGridColumns('entity-name')`
4. Implement `onRowSelect()` and `onRowEdit()` handlers
5. Test filters, sorts, exports

Example: See `alumnos.component.ts` (post-FASE 25)

## Dependencies

- Angular 19+
- PrimeNG 21+
- TypeScript 5+
- PostgreSQL 18+ (row_version column)

## Performance Metrics (Target)

| Metric | Target | Current |
|---|---|---|
| Grid render (1k rows) | <500ms | TBD |
| Filter update | <200ms | TBD |
| Export CSV (10k rows) | <2s | TBD |
| Concurrent edits (5 users) | <100ms per save | TBD |

## Related Documents

- [CHANGELOG_FASE24.md](../../../CHANGELOG_FASE24.md) — Release notes
- [Optimistic Locking ADR](../../../DECISIONS/ADR-0004-optimistic-locking.md) — Architecture decision
- [Interactive Grid Usage Guide](./USAGE.md) — Component tutorial

---

**Next Phase**: FASE 25 — Global Grid Integration  
**Estimated Duration**: 2-3 days  
**Team**: Frontend (2 engineers) + QA (1 engineer)
