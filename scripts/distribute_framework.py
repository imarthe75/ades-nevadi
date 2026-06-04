#!/usr/bin/env python3
"""
Script utilitario — ADES Instituto Nevadi
Sincroniza los artefactos del framework residente (skills, rules, agents)
dentro del mismo repositorio ades-nevadi.

Originalmente este script distribuía el framework a múltiples proyectos
(ecosistema multi-repo). En ADES es un proyecto único, por lo que este script
solo verifica la integridad de los artefactos del agente residente.
"""
import os

ADES_ROOT = "/opt/ades"

FRAMEWORK_ARTIFACTS = [
    ".agent/AGENT.md",
    ".agent/CONTEXT.md",
    ".agent/RULES.md",
    ".agent/HEURISTICS.md",
    ".agent/STATE.md",
    "agents/architect.md",
    "agents/code-reviewer.md",
    "rules/common/database-style.md",
    "skills/postgres-audit/SKILL.md",
    "skills/database-liquibase-postgresql/SKILL.md",
    "auditoria.sql",
    "DECISIONS/",
]

def verificar_artefactos():
    print("ADES Instituto Nevadi — Verificación de artefactos del agente residente")
    print(f"Root: {ADES_ROOT}\n")

    faltantes = []
    for artifact in FRAMEWORK_ARTIFACTS:
        ruta = os.path.join(ADES_ROOT, artifact)
        existe = os.path.exists(ruta)
        estado = "OK" if existe else "FALTA"
        print(f"  [{estado}] {artifact}")
        if not existe:
            faltantes.append(artifact)

    print()
    if faltantes:
        print(f"ADVERTENCIA: {len(faltantes)} artefacto(s) faltante(s).")
        for f in faltantes:
            print(f"  - {f}")
    else:
        print("Todos los artefactos del framework residente están presentes.")

if __name__ == "__main__":
    verificar_artefactos()
