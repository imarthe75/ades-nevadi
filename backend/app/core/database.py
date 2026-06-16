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
    pass


async def get_db() -> AsyncSession:
    async with AsyncSessionLocal() as session:
        try:
            yield session
        except Exception:
            await session.rollback()
            raise
