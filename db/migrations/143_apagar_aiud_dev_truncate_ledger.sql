/*
 * Archivo: 143_apagar_aiud_dev_truncate_ledger.sql
 * Fecha: 2026-07-15
 * Ambiente: desarrollo (decisión explícita del usuario 2026-07-15)
 *
 * El sistema aún está en etapa de desarrollo/pre-liberación (aunque el host es producción
 * de infraestructura). Se apagan los 3 audit_aiud activos (ades_horario_corrida,
 * ades_webhooks, ades_webhook_logs) y se trunca el ledger — las filas eran de pruebas
 * anteriores al endurecimiento del ledger (migración 141 (fn_auditoria_biu) y 137/138/139 (endurecimiento del ledger)) y no debe
 * arrastrarse una cadena con hashes generados por lógica ya reemplazada. La activación de
 * audit_aiud en las tablas con PII se hace en el go-live (ver R-1 en
 * docs/hallazgos/2026-07-15_plan_remediacion.md), no antes.
 */
DROP TRIGGER IF EXISTS audit_aiud ON public.ades_horario_corrida;
DROP TRIGGER IF EXISTS audit_aiud ON public.ades_webhooks;
DROP TRIGGER IF EXISTS audit_aiud ON public.ades_webhook_logs;

TRUNCATE TABLE auditoria.log_auditoria;
