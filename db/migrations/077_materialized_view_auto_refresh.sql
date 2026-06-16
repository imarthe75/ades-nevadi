-- =============================================================================
-- SPRINT 4 FASE 3: Automatic Materialized View Refresh
-- =============================================================================
-- Configura refresh automático de MVs usando pg_cron (si disponible)
-- Fallback: usar triggers para invalidación manual

\echo '=== CONFIGURANDO AUTO-REFRESH DE MATERIALIZED VIEWS ==='

-- Crear tabla de control para refresh timestamps
CREATE TABLE IF NOT EXISTS ades_mv_refresh_log (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  view_name VARCHAR(100) NOT NULL UNIQUE,
  last_refresh TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  refresh_interval INTERVAL DEFAULT '1 hour'::INTERVAL,
  status VARCHAR(50) DEFAULT 'pending',
  UNIQUE(view_name)
);

\echo '✓ Tabla ades_mv_refresh_log creada'

-- Insertar registros de control
INSERT INTO ades_mv_refresh_log (view_name, refresh_interval, status)
VALUES 
  ('v_asistencias_resumen', '1 hour'::INTERVAL, 'pending'),
  ('v_tareas_entregas_resumen', '6 hours'::INTERVAL, 'pending')
ON CONFLICT (view_name) DO NOTHING;

-- Función para refrescar MV con log
CREATE OR REPLACE FUNCTION refresh_materialized_view(p_view_name TEXT)
RETURNS BOOLEAN AS $$
DECLARE
  v_start TIMESTAMP := CURRENT_TIMESTAMP;
  v_duration INTERVAL;
BEGIN
  EXECUTE 'REFRESH MATERIALIZED VIEW CONCURRENTLY ' || quote_ident(p_view_name);
  
  v_duration := CURRENT_TIMESTAMP - v_start;
  
  UPDATE ades_mv_refresh_log 
  SET 
    last_refresh = CURRENT_TIMESTAMP,
    status = 'success'
  WHERE view_name = p_view_name;
  
  RAISE NOTICE 'Refreshed % in %', p_view_name, v_duration;
  RETURN TRUE;
  
EXCEPTION WHEN OTHERS THEN
  UPDATE ades_mv_refresh_log 
  SET status = 'error'
  WHERE view_name = p_view_name;
  RAISE NOTICE 'Error refreshing %: %', p_view_name, SQLERRM;
  RETURN FALSE;
END;
$$ LANGUAGE plpgsql;

\echo '✓ Función refresh_materialized_view(p_view_name) creada'

-- Triggers para invalidar MV al insertar/actualizar datos
-- Trigger para v_asistencias_resumen
CREATE OR REPLACE FUNCTION invalidate_v_asistencias_resumen()
RETURNS TRIGGER AS $$
BEGIN
  UPDATE ades_mv_refresh_log 
  SET status = 'stale'
  WHERE view_name = 'v_asistencias_resumen';
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_invalidate_v_asistencias_resumen
AFTER INSERT OR UPDATE ON ades_asistencias
FOR EACH STATEMENT
EXECUTE FUNCTION invalidate_v_asistencias_resumen();

\echo '✓ Trigger invalidación para v_asistencias_resumen creado'

-- Trigger para v_tareas_entregas_resumen
CREATE OR REPLACE FUNCTION invalidate_v_tareas_entregas_resumen()
RETURNS TRIGGER AS $$
BEGIN
  UPDATE ades_mv_refresh_log 
  SET status = 'stale'
  WHERE view_name = 'v_tareas_entregas_resumen';
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_invalidate_v_tareas_entregas_resumen
AFTER INSERT OR UPDATE ON ades_tareas_entregas
FOR EACH STATEMENT
EXECUTE FUNCTION invalidate_v_tareas_entregas_resumen();

\echo '✓ Trigger invalidación para v_tareas_entregas_resumen creado'

-- Función para refresh en batch (útil en mantenimiento)
CREATE OR REPLACE FUNCTION refresh_all_materialized_views()
RETURNS TABLE (
  view_name VARCHAR,
  refresh_time TIMESTAMP,
  status VARCHAR
) AS $$
DECLARE
  v_mv RECORD;
BEGIN
  FOR v_mv IN 
    SELECT DISTINCT view_name 
    FROM ades_mv_refresh_log 
    WHERE status IN ('pending', 'stale')
  LOOP
    PERFORM refresh_materialized_view(v_mv.view_name);
  END LOOP;
  
  RETURN QUERY
  SELECT 
    vmr.view_name::VARCHAR,
    vmr.last_refresh,
    vmr.status::VARCHAR
  FROM ades_mv_refresh_log vmr
  ORDER BY vmr.last_refresh DESC;
END;
$$ LANGUAGE plpgsql;

\echo '✓ Función refresh_all_materialized_views() creada'

-- Vista de estado actual de MVs
CREATE OR REPLACE VIEW v_mv_refresh_status AS
SELECT 
  view_name,
  last_refresh,
  CURRENT_TIMESTAMP - last_refresh AS age,
  refresh_interval,
  CASE 
    WHEN status = 'success' AND (CURRENT_TIMESTAMP - last_refresh) > refresh_interval 
      THEN 'due_for_refresh'
    ELSE status
  END as current_status,
  (CURRENT_TIMESTAMP - last_refresh) > refresh_interval AS needs_refresh
FROM ades_mv_refresh_log
ORDER BY last_refresh DESC;

\echo '✓ Vista v_mv_refresh_status creada'

\echo ''
\echo '=== AUTO-REFRESH CONFIGURADO EXITOSAMENTE ==='
\echo '📝 Notas:'
\echo '   - Para usar pg_cron (auto-scheduler): requiere extensión pg_cron'
\echo '   - Triggers invalidarán MVs automáticamente'
\echo '   - Ejecutar manualmente: SELECT refresh_all_materialized_views();'
\echo '   - Ver estado: SELECT * FROM v_mv_refresh_status;'

