# API Design Standards for ADES

**Version**: 1.0.0  
**Status**: Active  
**Last Updated**: 2026-06-09

## Overview

REST API design principles and standards for ADES backend (FastAPI).

## Endpoints Naming Convention

### Naming Pattern

```
/api/v1/{resource}/{id}/{sub-resource}
```

### Examples

- `GET /api/v1/alumnos` — List students
- `POST /api/v1/alumnos` — Create student
- `GET /api/v1/alumnos/{id}` — Get specific student
- `PATCH /api/v1/alumnos/{id}` — Update student
- `DELETE /api/v1/alumnos/{id}` — Delete student
- `GET /api/v1/estudiantes/{id}/calificaciones` — Student grades

## HTTP Methods

| Method | Purpose | Idempotent | Safe |
|---|---|---|---|
| GET | Retrieve data | Yes | Yes |
| POST | Create resource | No | No |
| PATCH | Partial update | No | No |
| PUT | Full replace (avoid) | Yes | No |
| DELETE | Remove resource | No | No |

**Note**: Use PATCH for partial updates, not PUT.

## Authentication & Authorization

### Headers

```
Authorization: Bearer <jwt_token>
Content-Type: application/json
```

### Token Format

JWT from Authentik OIDC with claims:
- `sub` — User ID (oidc_sub)
- `email` — Email address
- `aud` — Client ID (must match OIDC_CLIENT_ID)

### RBAC Levels

```python
0 = ADMIN_GLOBAL       # Full system access
1 = DIRECTOR           # One campus + all levels
2 = SUBDIRECTOR        # One campus + one level
3 = DOCENTE            # One group, can grade
4 = ESTUDIANTE         # Own data only
5 = PADRE/TUTOR        # Child data only
```

## Response Format

### Success (2xx)

```json
{
  "data": { /* resource or array */ },
  "message": "Operation successful",
  "status": "success"
}
```

### Errors (4xx, 5xx)

```json
{
  "status": "error",
  "message": "Human-readable error message",
  "detail": "Technical details if needed",
  "code": "ERROR_CODE"
}
```

## Status Codes

| Code | Meaning | Usage |
|---|---|---|
| 200 | OK | Successful GET, PATCH, DELETE |
| 201 | Created | Successful POST |
| 204 | No Content | DELETE with no response body |
| 400 | Bad Request | Invalid input parameters |
| 401 | Unauthorized | Missing or invalid token |
| 403 | Forbidden | Authenticated but lacks permission |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Optimistic lock / row_version mismatch |
| 422 | Unprocessable | Validation error (use detail field) |
| 500 | Server Error | Unexpected error |

## Pagination

### Query Parameters

- `pagina: int` (default: 1, min: 1)
- `por_pagina: int` (default: 20, min: 1, max: 1000)

### Response

```json
{
  "data": [ /* items */ ],
  "total": 350,
  "pagina": 1,
  "por_pagina": 20,
  "total_paginas": 18
}
```

## Filtering & Search

### Query Pattern

```
GET /api/v1/alumnos?buscar=juan&rol=DOCENTE&plantel_id=xxx
```

### Common Filters

- `buscar` — Full-text search on text fields
- `rol` — Filter by user role
- `plantel_id` — Filter by campus
- `nivel_educativo_id` — Filter by education level
- `es_vigente` — Filter active records only

## Sorting

### Query Parameter

```
GET /api/v1/alumnos?sort=nombre&order=asc
```

### Convention

- `sort` — Field name (snake_case)
- `order` — `asc` or `desc` (default: asc)

## Versioning

- API version in URL: `/api/v1/`, `/api/v2/`, etc.
- Breaking changes increment major version
- Non-breaking additions use same version

## Optimistic Locking

### Headers

Include `row_version` in request body for mutating operations:

```json
{
  "nombre": "New name",
  "row_version": 5
}
```

### Conflict Response (409)

```json
{
  "status": "conflict",
  "message": "Record was modified by another user",
  "current_version": 6,
  "received_version": 5,
  "current_record": { /* updated data */ }
}
```

## Audit Trail

All mutating endpoints (POST, PATCH, DELETE) are automatically logged with:
- User ID & name
- IP address
- Action type
- Timestamp
- Row version increment

See: `/api/v1/auditoria` for logs.

## Rate Limiting (Planned)

TBD — To be implemented in API Gateway.

## CORS

Configured in FastAPI settings. Allowed origins:
- `http://localhost:4200` (dev)
- `https://ades.setag.mx` (prod)

## Migration Guide

When introducing breaking changes:

1. Document in ADR (Architectural Decision Record)
2. Update this spec with rationale
3. Create migration guide for clients
4. Increment major version
5. Support old version for 30 days

---

**Last Reviewed**: 2026-06-09 | **Next Review**: 2026-09-09
