from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text
from app.core.database import get_db

router = APIRouter()


@router.get("/health", tags=["sistema"])
async def health(db: AsyncSession = Depends(get_db)):
    result = await db.execute(text("SELECT uuidv7(), current_database(), version()"))
    row = result.one()
    return {
        "status": "ok",
        "db": row[1],
        "pg_version": row[2].split(" ")[0],
        "uuid_v7_sample": str(row[0]),
    }
