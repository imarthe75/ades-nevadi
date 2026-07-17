# 🛡️ ECC Agent Workflow Rules (RULES.md)

Este archivo define las reglas de desarrollo y hooks de sistema inspirados en **affaan-m/ECC** para asegurar la calidad de código, triaje y control del arnés del agente.

## 📌 Flujo de Trabajo (Harness Workflow)

1. **Investigación y Contexto Primero:**
   - Queda prohibido modificar código sin antes listar el mapa técnico local en `.agent/MAP.md`.
   - Si no existe el archivo `.agent/STATE.md`, se debe invocar la inicialización automática del proyecto (`npm run init-project`).

2. **Higiene de Contexto y Token Saving:**
   - Al ejecutar comandos o búsquedas, limita el output y la cantidad de líneas devueltas (ej. `head -n 20`).
   - Evita lecturas completas de archivos binarios o bases de datos sin filtros.
   - El diseño de frontend debe alinearse con el estilo Oracle APEX: interactive grids, master-detail, LOV y edición directa sobre tablas.

3. **Caché de Respuestas y Memoria:**
   - Integración nativa con Valkey. La lógica de control debe verificar la caché semántica local en Valkey (`6379`) antes de enviar consultas redundantes al modelo.

4. **Soberanía y Seguridad de Datos:**
   - Ninguna credencial, token (PAT) o credencial de base de datos puede ser subida a repositorios públicos o expuesta en logs.
   - Las carpetas `./data` y los archivos `.env` deben estar permanentemente en el `.gitignore`.

5. **Documentación Obligatoria y Exhaustiva:**
   - Es obligatorio documentar exhaustivamente todo cambio de código.
   - Esto incluye añadir docstrings con tipos, descripciones de parámetros y retornos para cada nueva función/método, así como comentarios explicativos en clases y bloques de código complejos.

6. **Auditoría de Seguridad y Funcionalidad:**
   - Siempre se deben aplicar las auditorías necesarias en la base de datos y en la lógica de negocio para garantizar el cumplimiento de una seguridad estricta y de la funcionalidad solicitada de forma impecable.

7. **Autoexploración y Autoescaneo de Componentes:**
   - Cada vez que se desarrolle o modifique un componente, este debe ser autoexplorado y autoescaneado para asegurar que sea 100% seguro con respecto a los estándares definidos y cumpla al 100% con la funcionalidad esperada.

8. **Análisis Exploratorios:**
   - Realizar análisis exploratorios profundos antes y durante el diseño o la implementación de cualquier cambio o componente nuevo para identificar posibles fallas, vulnerabilidades o inconsistencias lógicas de manera proactiva.

9. **⚠️ REGLA CRÍTICA DE ACCESO SSH — NUNCA CERRAR EL PUERTO 22.**
   - El acceso al servidor único de ADES (`ades.setag.mx`) es únicamente por certificado
     (`PasswordAuthentication no`). Un cierre accidental del puerto 22 (firewall, iptables,
     security list/NSG) deja el servidor inaccesible de forma irrecuperable — ya ocurrió una
     vez durante una auditoría de seguridad y obligó a eliminar y recrear el servidor completo.
   - Antes de tocar reglas de firewall/iptables/NSG, verificar SIEMPRE que el puerto 22
     permanezca `ACCEPT`. Usuario de respaldo: `ades` (grupo sudo), misma `authorized_keys`
     que `ubuntu` para login passwordless por certificado.
   - Texto completo y contexto en `CLAUDE.md` (sección "ESTÁNDARES DE SEGURIDAD OBLIGATORIOS").

## 📋 Reglas Mandatorias específicas de ADES (ver `CLAUDE.md` para el texto completo de cada una)

Este proyecto (ADES) añade 25 reglas mandatorias propias sobre el framework genérico de
arriba. Se listan aquí solo los títulos para que ningún agente que arranque leyendo únicamente
`.agent/` las pase por alto — el detalle técnico completo vive en `CLAUDE.md` § REGLAS
MANDATORIAS, que es la fuente de verdad y debe consultarse antes de tocar código:

1. PKs siempre UUID (`uuidv7()`/`gen_random_uuid()`) — nunca SERIAL/BIGINT/INTEGER.
2. FKs siempre referencian UUID.
3. Toda tabla `ades_*` nueva lleva columnas de auditoría (`ref`, `row_version`,
   `fecha_creacion`, `fecha_modificacion`, `usuario_creacion`, `usuario_modificacion`) vía
   `AdesBaseEntity`/`AdesAuditEntity` — nunca declaradas sueltas.
4. Triggers de auditoría obligatorios (`auditoria.asignar_biu()`) al final de cada migración
   con tablas nuevas.
5. El trigger `audit_biu` gestiona `ref`/`row_version`/timestamps/usuarios automáticamente —
   nunca asignar a mano.
6. En el go-live: `auditoria.asignar_triggers()` activa también `audit_aiud`.
7. Verificar cobertura con `auditoria.reporte_cobertura()`.
8. Volúmenes Docker mapeados a `./data/*`.
9. UI estilo Oracle APEX (interactive grids, master-detail, LOV, edición inline) — coincide
   con el mandato #4 de `AGENT.md` y el punto 2 de este archivo.
10. `docker compose down -v` no debe perder datos con volúmenes bien configurados.
11. `.gitignore` incluye `data/`, `.env`, `.agent/brain/`, `node_modules/`.
12. Migraciones numeradas con 3 dígitos en `db/migrations/`.
13. Endpoints mutantes siempre pasan por `AuditMiddleware`/`AuditHttpFilter`.
14. Auditorías obligatorias de seguridad y lógica de negocio — coincide con el punto 6 de
    este archivo.
15. Autoexploración y autoescaneo de cada componente — coincide con el punto 7 de este archivo.
16. Análisis exploratorios profundos antes de implementar — coincide con el punto 8 de este
    archivo.
17. Documentación completa del código — coincide con el punto 5 de este archivo y el mandato
    #5 de `AGENT.md`.
18. Todo `.md` generado va en `docs/`, nunca en raíz (excepto `CLAUDE.md`/`README.md`).
19. JOINs correlacionados por FK real — nunca un `JOIN` sin condición ni fan-out sin revisar
    en agregados (`SUM`/`COUNT`/`AVG`).
20. Contrato de claves documentado en `Map<String,Object>` de `*QueryService` — el consumidor
    Angular se revisa en el mismo PR.
21. Prohibido `git commit`/`git push` a agentes/sub-agentes salvo instrucción explícita y
    textual del usuario en ese mismo prompt.
22. Repo siempre limpio de artefactos generados (`test-results/`, `playwright-report/`,
    reportes de cobertura, etc.) — nunca trackeados en git, siempre en `.gitignore`.
23. Soberanía absoluta sobre los datos — coincide exactamente con la regla de oro #1 de
    `AGENT.md` (nada de bases vectoriales en la nube ni SaaS externo de memoria).
24. Feedback visual obligatorio en mutaciones: todo botón `post/put/patch/delete` lleva un
    signal de loading propio wireado al `[loading]` real del botón (verificar mapeando
    método↔botón, no con grep de conteo) — metodología en
    `.agent/skills/frontend-heuristicas-audit/SKILL.md`.
25. Validación estructural real (no solo `required`/`maxlength`) en CURP/RFC/NSS/teléfono/CP
    vía `AdesValidators` — `AdesFormatDirective` solo sanea caracteres al escribir, no valida
    la forma completa; ambas capas son complementarias.

**Estándares de seguridad obligatorios** (STRIDE, OWASP Top 10 2021, OWASP API Security Top 10,
NIST AC-3/AU-3/AU-12/SI-10/SC-8, GDPR/LFPDPPP, supply chain, SDLC security) y las
**10 Heurísticas Cognitivas de Nielsen aplicadas a ADES** (con implementación concreta por
principio) también viven íntegramente en `CLAUDE.md` — `HEURISTICS.md` en este directorio
cubre la heurística de comportamiento del *agente* (loop prevention, ahorro de contexto, ADRs),
no la heurística de *usabilidad del producto ADES*, que es la de `CLAUDE.md`.
