# ADES Specifications

OpenSpec repository for the ADES (Administración Escolar) system.

## Structure

```
spec/
├── api/                      # API specifications
│   ├── v1-endpoints.md      # REST API endpoint definitions
│   └── schemas.md           # Request/Response schemas
├── modules/                  # Feature/Module specifications
│   ├── fase-01-maestros/    # Phase 1: Master data
│   ├── fase-02-academico/   # Phase 2: Academic
│   ├── fase-03-operacion/   # Phase 3: Operations
│   ├── fase-04-ia/          # Phase 4: AI
│   └── fase-24-interactive-grid/  # Phase 24: Interactive Grid
├── standards/               # Technical standards
│   ├── database.md         # Schema standards
│   ├── api-design.md       # API conventions
│   ├── frontend-components.md  # UI/Component patterns
│   └── security.md         # Auth & RBAC standards
├── compliance/              # Regulatory & standards compliance
│   ├── sep-requirements.md  # Mexican education ministry
│   └── uaemex-requirements.md  # University prep standards
└── infrastructure/          # DevOps & Cloud
    └── docker-compose.md    # Service definitions
```

## Version Control

- **Latest**: 1.0.0
- **Status**: Active Development
- **Last Updated**: 2026-06-09

## How to Contribute

1. Read the relevant specification
2. Propose changes via PR with spec reasoning
3. Tag @team for review
4. Merge once approved

## Related Documents

- [CLAUDE.md](../CLAUDE.md) - Project guidelines
- [openspec.yaml](../openspec.yaml) - OpenSpec configuration
- [.agent/CONTEXT.md](../.agent/CONTEXT.md) - Full system context

## Using with Claude Code

When working with Claude Code agents:

1. Agents read this `openspec.yaml` for context
2. Reference spec files when proposing changes
3. Update specs when adding new features
4. Use `/code-review ultra` for major changes

---

**OpenSpec Version**: 1.0.0 | **Framework**: https://github.com/Fission-AI/OpenSpec
