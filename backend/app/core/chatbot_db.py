"""Conexión de base de datos dedicada y de solo-lectura para /chatbot/sql.

Mig. 154 (2026-07-17, docs/hallazgos/2026-07-16_auditoria_gaps_no_revisados.md
#2): el aislamiento POR_PLANTEL/POR_PROFESOR/POR_ALUMNO del NL→SQL del chatbot
era solo un hint de texto en el prompt del LLM — nunca verificado por Postgres.
Este módulo provee una sesión que:

1. Se conecta como ``ades_app`` (rol NO superusuario, ver
   ``db/migrations/080_ades_app_role.sql``) en vez de ``ades_admin`` (el rol
   que usa el resto de la app vía ``database.py`` — superusuario, por lo que
   Row Level Security nunca se le aplicaría, sin importar cuántas políticas
   se creen).
2. Fija los GUC de sesión ``app.rls_bypass``/``app.rls_plantel_id`` con
   ``SET LOCAL`` (transaction-scoped, seguro bajo PgBouncer en modo
   transacción — no requiere ``DISCARD ALL`` porque ``SET LOCAL`` ya se
   revierte solo al terminar la transacción) que las políticas de la
   mig. 154 verifican por fila en las 15 tablas expuestas al LLM.

Si ``CHATBOT_DB_URL`` no está configurada (rol ades_app sin aprovisionar en
este entorno), cae de vuelta al engine de ``ades_admin`` de ``database.py``
— mismo comportamiento (sin RLS real) que antes de esta migración, con una
advertencia en el log para que no pase desapercibido en producción.
"""
from __future__ import annotations

import logging
import uuid
from contextlib import asynccontextmanager
from typing import AsyncIterator

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.core.config import settings
from app.core.database import AsyncSessionLocal as _AdminSessionLocal
from app.core.security import AdesUser

log = logging.getLogger(__name__)

_chatbot_engine = None
_ChatbotSessionLocal = None

if settings.CHATBOT_DB_URL:
    _connect_args: dict = {}
    if "asyncpg" in settings.CHATBOT_DB_URL:
        _connect_args = {"statement_cache_size": 0, "prepared_statement_cache_size": 0}
    _chatbot_engine = create_async_engine(
        settings.CHATBOT_DB_URL,
        pool_size=3,
        max_overflow=5,
        pool_pre_ping=True,
        connect_args=_connect_args,
    )
    _ChatbotSessionLocal = async_sessionmaker(
        bind=_chatbot_engine, class_=AsyncSession, expire_on_commit=False, autoflush=False,
    )
else:
    log.warning(
        "CHATBOT_DB_URL no configurada — /chatbot/sql usará la conexión ades_admin "
        "(superusuario) de DATABASE_URL, sin aplicar Row Level Security real "
        "(mig. 154). Configurar ADES_APP_DB_PASSWORD en .env para habilitarla."
    )


@asynccontextmanager
async def chatbot_readonly_session(user: AdesUser) -> AsyncIterator[AsyncSession]:
    """Sesión de solo-lectura con RLS real aplicado, aislada por el plantel de ``user``.

    Uso: ``async with chatbot_readonly_session(user) as db: await db.execute(...)``.
    La transacción queda en modo ``READ ONLY`` y con los GUC de aislamiento
    fijados ANTES de que el llamador ejecute el SQL generado por el LLM.
    """
    factory = _ChatbotSessionLocal or _AdminSessionLocal
    bypass = "true" if user.nivel_acceso == 0 else "false"

    # SET/SET LOCAL es un comando de utilidad de Postgres — NO acepta bind
    # parameters ($1) como una query normal, así que el valor debe ir
    # interpolado en el texto del SQL. Para no abrir una inyección ahí,
    # plantel_id se valida estrictamente como UUID (rechaza cualquier otra
    # cosa) antes de interpolarlo; bypass sale de un mapeo fijo, no de input.
    plantel_id = ""
    if user.plantel_id:
        plantel_id = str(uuid.UUID(str(user.plantel_id)))

    async with factory() as session:
        try:
            await session.execute(text("SET TRANSACTION READ ONLY"))
            # SET LOCAL (no SET) — transaction-scoped, seguro con PgBouncer en
            # modo transacción (se revierte solo al COMMIT/ROLLBACK, sin
            # depender de server_reset_query).
            await session.execute(text(f"SET LOCAL app.rls_bypass = '{bypass}'"))
            await session.execute(text(f"SET LOCAL app.rls_plantel_id = '{plantel_id}'"))
            yield session
        except Exception:
            await session.rollback()
            raise
