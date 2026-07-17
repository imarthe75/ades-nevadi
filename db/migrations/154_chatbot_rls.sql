-- =============================================================================
-- Migración 154 — Row Level Security real para /chatbot/sql (NL→SQL)
-- =============================================================================
-- Instituto Nevadi / ADES — 2026-07-17
--
-- Contexto (ver comentario en backend/app/api/v1/chatbot.py, hallazgo
-- 2026-07-16_auditoria_gaps_no_revisados.md #2): el aislamiento POR_PLANTEL/
-- POR_PROFESOR/POR_ALUMNO del chatbot NL→SQL era solo un hint de texto
-- inyectado en el prompt del LLM (_build_rls_context) — nunca verificado por
-- Postgres. Un LLM que ignora el hint (o un prompt-injection en la pregunta
-- del usuario) podía generar un SELECT sin el WHERE de aislamiento y leer
-- datos de OTRO plantel; el único control real era "solo SELECT" +
-- "READ ONLY" (no escritura), no aislamiento por fila.
--
-- Esta migración habilita RLS real en las tablas que el chatbot expone al
-- LLM (ver schema_summary en chatbot.py), con políticas basadas en 2 GUCs de
-- sesión que el backend debe fijar con SET LOCAL antes de ejecutar el SQL
-- generado (ver backend/app/db/chatbot_session.py):
--   app.rls_bypass      'true'|'false' — true solo si nivel_acceso = 0 (ADMIN_GLOBAL)
--   app.rls_plantel_id  uuid del plantel del usuario autenticado
--
-- Diseño deliberadamente acotado a nivel PLANTEL (no por-profesor ni
-- por-alumno individual): closes el riesgo crítico real (fuga cross-plantel,
-- la misma clase de bug BOLA/BFLA de toda esta sesión), sin la complejidad
-- adicional de políticas por-persona que requerirían resolver, por cada una
-- de estas 15 tablas, el camino de join hasta el profesor/alumno específico
-- (Pendiente explícitamente fuera de alcance — igual que lo documenta el
-- comentario original en chatbot.py: "el filtro POR_PLANTEL/POR_ALUMNO sigue
-- siendo un hint" pasa a "POR_PLANTEL ya no es solo un hint", POR_ALUMNO/
-- POR_PROFESOR siguen siendo hint hasta una fase 2).
--
-- CRÍTICO — por qué esto NO afecta ninguna conexión existente: tanto el BFF
-- Spring como el resto de FastAPI se conectan como ades_admin (superusuario,
-- ver db/migrations/080_ades_app_role.sql "Hallazgo A") — Postgres exime a
-- los superusuarios de RLS de forma automática e incondicional. Estas
-- políticas solo tienen efecto sobre el rol no-superusuario ades_app, usado
-- ÚNICAMENTE por la nueva conexión dedicada de solo-lectura del chatbot
-- (backend/app/db/chatbot_session.py) — no existía ningún otro consumidor de
-- ades_app antes de esta sesión.
-- =============================================================================

BEGIN;

-- Función helper: evita repetir la misma expresión current_setting(...) 15 veces.
CREATE OR REPLACE FUNCTION chatbot_rls_bypass() RETURNS BOOLEAN
LANGUAGE sql STABLE AS $$
  SELECT COALESCE(current_setting('app.rls_bypass', true), 'false') = 'true';
$$;

CREATE OR REPLACE FUNCTION chatbot_rls_plantel_id() RETURNS TEXT
LANGUAGE sql STABLE AS $$
  SELECT current_setting('app.rls_plantel_id', true);
$$;

-- ── Tablas con plantel_id directo ───────────────────────────────────────────
ALTER TABLE ades_planteles   ENABLE ROW LEVEL SECURITY;
ALTER TABLE ades_grados      ENABLE ROW LEVEL SECURITY;
ALTER TABLE ades_profesores  ENABLE ROW LEVEL SECURITY;
ALTER TABLE ades_estudiantes ENABLE ROW LEVEL SECURITY;
ALTER TABLE ades_usuarios    ENABLE ROW LEVEL SECURITY;

CREATE POLICY chatbot_plantel_scope ON ades_planteles FOR SELECT TO ades_app
  USING (chatbot_rls_bypass() OR id::text = chatbot_rls_plantel_id());

CREATE POLICY chatbot_plantel_scope ON ades_grados FOR SELECT TO ades_app
  USING (chatbot_rls_bypass() OR plantel_id::text = chatbot_rls_plantel_id());

CREATE POLICY chatbot_plantel_scope ON ades_profesores FOR SELECT TO ades_app
  USING (chatbot_rls_bypass() OR plantel_id::text = chatbot_rls_plantel_id());

CREATE POLICY chatbot_plantel_scope ON ades_estudiantes FOR SELECT TO ades_app
  USING (chatbot_rls_bypass() OR plantel_id::text = chatbot_rls_plantel_id());

CREATE POLICY chatbot_plantel_scope ON ades_usuarios FOR SELECT TO ades_app
  USING (chatbot_rls_bypass() OR plantel_id::text = chatbot_rls_plantel_id());

-- ── Tablas que requieren 1 join hasta el plantel ────────────────────────────
ALTER TABLE ades_grupos        ENABLE ROW LEVEL SECURITY;
ALTER TABLE ades_materias_plan ENABLE ROW LEVEL SECURITY;

CREATE POLICY chatbot_plantel_scope ON ades_grupos FOR SELECT TO ades_app
  USING (chatbot_rls_bypass() OR EXISTS (
    SELECT 1 FROM ades_grados gr
    WHERE gr.id = ades_grupos.grado_id AND gr.plantel_id::text = chatbot_rls_plantel_id()
  ));

CREATE POLICY chatbot_plantel_scope ON ades_materias_plan FOR SELECT TO ades_app
  USING (chatbot_rls_bypass() OR EXISTS (
    SELECT 1 FROM ades_grados gr
    WHERE gr.id = ades_materias_plan.grado_id AND gr.plantel_id::text = chatbot_rls_plantel_id()
  ));

ALTER TABLE ades_clases ENABLE ROW LEVEL SECURITY;
CREATE POLICY chatbot_plantel_scope ON ades_clases FOR SELECT TO ades_app
  USING (chatbot_rls_bypass() OR EXISTS (
    SELECT 1 FROM ades_profesores p
    WHERE p.id = ades_clases.profesor_id AND p.plantel_id::text = chatbot_rls_plantel_id()
  ));

-- ── Tablas colgadas de estudiante_id (vía ades_estudiantes.plantel_id) ──────
ALTER TABLE ades_inscripciones          ENABLE ROW LEVEL SECURITY;
ALTER TABLE ades_calificaciones_periodo ENABLE ROW LEVEL SECURITY;
ALTER TABLE ades_asistencias            ENABLE ROW LEVEL SECURITY;
ALTER TABLE ades_tareas_entregas        ENABLE ROW LEVEL SECURITY;
ALTER TABLE ades_reportes_conducta      ENABLE ROW LEVEL SECURITY;

CREATE POLICY chatbot_plantel_scope ON ades_inscripciones FOR SELECT TO ades_app
  USING (chatbot_rls_bypass() OR EXISTS (
    SELECT 1 FROM ades_estudiantes e
    WHERE e.id = ades_inscripciones.estudiante_id AND e.plantel_id::text = chatbot_rls_plantel_id()
  ));

CREATE POLICY chatbot_plantel_scope ON ades_calificaciones_periodo FOR SELECT TO ades_app
  USING (chatbot_rls_bypass() OR EXISTS (
    SELECT 1 FROM ades_estudiantes e
    WHERE e.id = ades_calificaciones_periodo.estudiante_id AND e.plantel_id::text = chatbot_rls_plantel_id()
  ));

CREATE POLICY chatbot_plantel_scope ON ades_asistencias FOR SELECT TO ades_app
  USING (chatbot_rls_bypass() OR EXISTS (
    SELECT 1 FROM ades_estudiantes e
    WHERE e.id = ades_asistencias.estudiante_id AND e.plantel_id::text = chatbot_rls_plantel_id()
  ));

CREATE POLICY chatbot_plantel_scope ON ades_tareas_entregas FOR SELECT TO ades_app
  USING (chatbot_rls_bypass() OR EXISTS (
    SELECT 1 FROM ades_estudiantes e
    WHERE e.id = ades_tareas_entregas.estudiante_id AND e.plantel_id::text = chatbot_rls_plantel_id()
  ));

CREATE POLICY chatbot_plantel_scope ON ades_reportes_conducta FOR SELECT TO ades_app
  USING (chatbot_rls_bypass() OR EXISTS (
    SELECT 1 FROM ades_estudiantes e
    WHERE e.id = ades_reportes_conducta.estudiante_id AND e.plantel_id::text = chatbot_rls_plantel_id()
  ));

-- ── ades_tareas: 2 joins (grupo → grado → plantel) ──────────────────────────
ALTER TABLE ades_tareas ENABLE ROW LEVEL SECURITY;
CREATE POLICY chatbot_plantel_scope ON ades_tareas FOR SELECT TO ades_app
  USING (chatbot_rls_bypass() OR EXISTS (
    SELECT 1 FROM ades_grupos g JOIN ades_grados gr ON gr.id = g.grado_id
    WHERE g.id = ades_tareas.grupo_id AND gr.plantel_id::text = chatbot_rls_plantel_id()
  ));

-- ── ades_personas: puede ser estudiante, profesor o usuario — cualquier
--    coincidencia con el plantel del que consulta es suficiente. ───────────
ALTER TABLE ades_personas ENABLE ROW LEVEL SECURITY;
CREATE POLICY chatbot_plantel_scope ON ades_personas FOR SELECT TO ades_app
  USING (chatbot_rls_bypass() OR EXISTS (
    SELECT 1 FROM ades_estudiantes e WHERE e.persona_id = ades_personas.id AND e.plantel_id::text = chatbot_rls_plantel_id()
  ) OR EXISTS (
    SELECT 1 FROM ades_profesores p WHERE p.persona_id = ades_personas.id AND p.plantel_id::text = chatbot_rls_plantel_id()
  ) OR EXISTS (
    SELECT 1 FROM ades_usuarios u WHERE u.persona_id = ades_personas.id AND u.plantel_id::text = chatbot_rls_plantel_id()
  ));

-- ── Catálogos compartidos sin datos sensibles por plantel: sin RLS ──────────
-- ades_niveles_educativos, ades_materias — mismo catálogo para todo el
-- instituto, no requieren aislamiento por fila.

COMMIT;

-- =============================================================================
-- Verificación rápida (no transaccional):
--   SELECT tablename, rowsecurity FROM pg_tables
--   WHERE schemaname='public' AND tablename LIKE 'ades_%' AND rowsecurity = true
--   ORDER BY tablename;
--   -- esperado: 15 filas (las listadas arriba)
-- =============================================================================
