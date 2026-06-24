"""Configuración del motor de base de datos asíncrono y sesiones SQLAlchemy.

Usa ``asyncpg`` como driver y ``PgBouncer`` (puerto 6432) como proxy de pool de
conexiones en transaction mode. Por eso se deshabilitan los prepared statements
(``statement_cache_size=0`` y ``prepared_statement_cache_size=0``): asyncpg usa
``PREPARE/EXECUTE`` internamente, lo que es incompatible con PgBouncer en ese modo.

El pool del engine se mantiene pequeño (5 + 10 overflow) ya que PgBouncer
gestiona el pool real de conexiones hacia PostgreSQL.
"""
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from sqlalchemy.orm import DeclarativeBase
from .config import settings

# PgBouncer transaction mode requiere deshabilitar prepared statements en asyncpg.
# statement_cache_size=0 y prepared_statement_cache_size=0 aseguran que asyncpg
# no use PREPARE/EXECUTE que PgBouncer no puede multiplexar entre conexiones del pool.
_connect_args: dict = {}
if "asyncpg" in settings.DATABASE_URL:
    _connect_args = {
        "statement_cache_size": 0,
        "prepared_statement_cache_size": 0,
    }

engine = create_async_engine(
    settings.DATABASE_URL,
    pool_size=5,        # reducido — PgBouncer maneja el pool real
    max_overflow=10,
    pool_pre_ping=True,
    echo=(settings.ENVIRONMENT == "development"),
    connect_args=_connect_args,
)

AsyncSessionLocal = async_sessionmaker(
    bind=engine,
    class_=AsyncSession,
    expire_on_commit=False,
    autoflush=False,
)


class Base(DeclarativeBase):
    """Base declarativa de SQLAlchemy para todos los modelos ORM de ADES."""


async def get_db() -> AsyncSession:
    """Dependencia FastAPI que proporciona una sesión de BD asíncrona por request.

    Hace rollback automático ante cualquier excepción no controlada y cierra la
    sesión al finalizar el request (context manager de ``AsyncSessionLocal``).

    Yields:
        AsyncSession: sesión activa lista para usar con ``await db.execute(...)``.
    """
    async with AsyncSessionLocal() as session:
        try:
            yield session
        except Exception:
            await session.rollback()
            raise
