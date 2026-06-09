# OpenSpec Integration Guide for Claude Code Agents

**Version**: 1.0.0  
**Updated**: 2026-06-09  
**Framework**: https://github.com/Fission-AI/OpenSpec

## Overview

This guide explains how to use ADES specifications (OpenSpec) when working with Claude Code agents.

## What is OpenSpec?

OpenSpec is a collaborative specification framework that allows teams to:
- Write specifications in markdown with YAML frontmatter
- Version control specifications like code
- Link specifications to implementation
- Track spec compliance and changes

## Reading Specifications

When starting a task, always:

1. **Read the relevant specification** from `spec/` directory
2. **Check the status** (Active, In Progress, Planned)
3. **Understand the requirements** before implementing
4. **Reference the spec** in your PR description

### Example

```bash
# Before implementing calificaciones feature:
1. Read: spec/modules/fase-02-academico/specification.md
2. Check: Status = Active
3. Understand: Grading scales, period calculations, SEP compliance
4. Implement: Following the spec exactly
5. Document: PR references spec sections
```

## Updating Specifications

When you:

### Add a New Feature
1. Create a new spec file in `spec/modules/<phase>/`
2. Include overview, requirements, endpoints, database changes
3. Update `openspec.yaml` if it's a major feature
4. Link from related specs

### Fix a Bug
1. Update the spec if behavior changed
2. Explain why in the PR (what was wrong)
3. Mark spec section as "Clarified" with date

### Change an API
1. Update `spec/standards/api-design.md` if pattern changed
2. Update endpoint specs in `spec/api/v1-endpoints.md`
3. Create ADR (Architectural Decision Record) in `DECISIONS/`
4. Include migration guide for clients

## File Structure Reference

```
spec/
├── README.md                         # Spec index
├── api/
│   ├── v1-endpoints.md              # All REST endpoints
│   └── schemas.md                   # Request/Response definitions
├── modules/
│   ├── fase-01-maestros/
│   ├── fase-02-academico/
│   ├── fase-03-operacion/
│   ├── fase-04-ia/
│   └── fase-24-interactive-grid/    # Latest: APEX-style grids
├── standards/
│   ├── api-design.md                # HTTP, status codes, auth
│   ├── database.md                  # Schema patterns
│   ├── frontend-components.md       # Angular component standards
│   └── security.md                  # RBAC, JWT, audit trails
├── compliance/
│   ├── sep-requirements.md          # Mexican education ministry
│   └── uaemex-requirements.md       # University prep standards
└── infrastructure/
    └── docker-compose.md            # Service definitions
```

## Using Specs with Claude Code

### When Planning

```
/code-review ultra
(Before implementation, upload this spec for review context)
```

### When Implementing

Reference the spec section:

```python
# api/v1/calificaciones.py:45
# Spec: spec/modules/fase-02-academico/specification.md § Calificaciones API
# Required: POST /api/v1/calificaciones with row_version for optimistic locking
```

### When Testing

```typescript
// Feature spec defines expected behavior
// Tests verify we match the spec exactly
describe('Calificaciones API', () => {
  it('should fail with 409 if row_version conflicts', () => {
    // Spec: optimistic_locking § Conflict Response
    expect(response.status).toBe(409);
    expect(response.body.current_version).toBe(7);
  });
});
```

## OpenSpec + Claude Code Workflow

### 1. Plan (Agent: Plan)

```
INPUT: Task description + user request
→ Agent reads: openspec.yaml + relevant specs
→ OUTPUT: Implementation plan referencing spec sections
```

### 2. Implement (Agent: Claude)

```
INPUT: Implementation plan from planning agent
→ Agent implements following specs exactly
→ References spec in code comments & commit messages
→ OUTPUT: Code + updated specs if needed
```

### 3. Review (Agent: /code-review ultra)

```
INPUT: PR with spec references
→ Cloud agent validates: Does implementation match spec?
→ Checks: API design standards, database patterns, security requirements
→ OUTPUT: Findings with spec section references
```

### 4. Document (Automatic)

```
→ CHANGELOG updated from spec changes
→ Migration guides auto-generated from spec deltas
→ API docs regenerated from spec/api/
```

## Spec Status Meanings

| Status | Meaning | Action |
|---|---|---|
| Active | Implemented & tested | Follow spec strictly |
| In Progress | Being worked on | Spec may change; coordinate |
| Planned | Approved but not started | Don't implement yet; discuss if confused |
| Deprecated | Old version; migrate | Don't use; migrate to new spec |
| Archived | Historical reference only | Reference only; don't implement |

## Common Patterns

### Adding an API Endpoint

1. **Spec**: Add to `spec/api/v1-endpoints.md`
   ```markdown
   ## POST /api/v1/calificaciones
   Create a student grade. Requires auth (nivel_acceso ≤ 3).
   
   Request:
   ```json
   {
     "estudiante_id": "uuid",
     "materia_id": "uuid",
     "periodo": 1,
     "calificacion": 8.5,
     "row_version": 5
   }
   ```
   
   Response: 201 Created + Calificacion object
   Errors: 409 Conflict (row_version), 422 Validation
   ```

2. **Code**: Implement in `backend/app/api/v1/calificaciones.py`
   ```python
   @router.post("/calificaciones", response_model=CalificacionOut, status_code=201)
   async def crear_calificacion(
       data: CalificacionCreate,  # Request schema from spec
       db: AsyncSession = Depends(get_db),
       user: AdesUser = Depends(get_ades_user),
   ):
       # Implementation matching spec exactly
       ...
   ```

3. **Test**: Verify spec compliance
   ```python
   def test_create_calificacion_409_conflict():
       # Spec § Errors: 409 Conflict
       response = post("/calificaciones", data={"row_version": 3})
       assert response.status_code == 409
   ```

### Adding a Database Column

1. **Spec**: Update `spec/standards/database.md`
   ```markdown
   ## ades_usuarios
   ...
   | nivel_acceso | INTEGER | RBAC cache; assigned from rol.nivel_acceso |
   ...
   ```

2. **Migration**: Create `db/migrations/017_campos_faltantes.sql`
   ```sql
   -- Spec: spec/standards/database.md § ades_usuarios
   ALTER TABLE ades_usuarios ADD COLUMN nivel_acceso INTEGER DEFAULT 5;
   ```

3. **Code**: Use in backend
   ```python
   # Spec § RBAC Levels
   if user.nivel_acceso > 2:  # From spec
       raise HTTPException(403, "Insufficient permissions")
   ```

## Linking Specs to Code

### Code Comment Pattern

```python
# Spec: spec/modules/fase-02-academico/specification.md § Calificaciones
# Requirement: "Calificaciones must support row_version for optimistic locking"
# See: spec/standards/api-design.md § Optimistic Locking
def crear_calificacion(...):
    ...
```

### Commit Message Pattern

```
feat: add calificaciones endpoint

- Implements POST /api/v1/calificaciones
- Follows spec/standards/api-design.md § REST Endpoints
- Includes optimistic locking per spec/standards/api-design.md § Optimistic Locking
- Adds migration 017 per spec/standards/database.md

Spec references:
- spec/modules/fase-02-academico/specification.md
- spec/api/v1-endpoints.md § Calificaciones

Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>
```

## When Specs Conflict with Reality

If you find that:
1. **Code doesn't match spec** → Update spec (not code!)
   - Spec is contract; reality reflects issues
   - Document why in ADR

2. **Spec is incomplete** → Add to spec
   - Don't guess; ask team or create ADR

3. **Spec contradicts CLAUDE.md** → CLAUDE.md wins
   - CLAUDE.md = mandatory rules
   - Spec = additional context

## Using /code-review ultra with Specs

When requesting code review:

```bash
/code-review ultra

[Attach this spec if not already in repo]
spec/modules/fase-24-interactive-grid/specification.md

[The agent will:]
1. Read the spec
2. Review implementation against spec
3. Report: "Matches spec? Yes/No/Partial"
4. Flag: Any deviations with spec references
```

## Questions & Support

For OpenSpec questions:
1. Check `openspec.yaml` for structure
2. Read `spec/README.md` for navigation
3. Look at existing specs as examples
4. Ask in team Slack or create discussion

---

**Framework**: https://github.com/Fission-AI/OpenSpec  
**Last Updated**: 2026-06-09  
**Maintained By**: ADES Development Team
