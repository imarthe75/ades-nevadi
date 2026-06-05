"""
Optimistic Locking helper — manejo de ediciones concurrentes con row_version.

Patrón:
  1. Frontend envía row_version actual al editar
  2. Backend verifica que row_version no ha cambiado
  3. Si cambió, retorna 409 Conflict con la versión actual
  4. Frontend maneja el conflicto: mostrar nuevo valor + permitir re-intentar
  5. Evita sobrescribir cambios de otros usuarios

Uso en endpoints:
  from app.core.optimistic_locking import check_row_version, RowVersionConflict

  try:
    check_row_version(db_entity, payload.row_version)
    # continuar con update
  except RowVersionConflict as e:
    raise HTTPException(status_code=409, detail=str(e))
"""

from fastapi import HTTPException


class RowVersionConflict(Exception):
    """Excepción cuando hay conflicto de row_version."""
    def __init__(self, current_version: int, received_version: int | None = None):
        self.current_version = current_version
        self.received_version = received_version
        msg = f"Conflict: received row_version={received_version}, current={current_version}. "
        msg += "Another user modified this record. Reload and try again."
        super().__init__(msg)


def check_row_version(db_entity: any, received_version: int | None = None) -> None:
    """
    Verificar que la row_version del cliente coincide con la del servidor.

    Levanta RowVersionConflict si hay desajuste.

    Parámetros:
      db_entity: Entidad ORM con atributo row_version
      received_version: Versión que envió el cliente (None = no valida)

    Ejemplo:
      try:
        check_row_version(usuario_db, payload.row_version)
        usuario_db.nombre = "nuevo"
        await db.commit()
      except RowVersionConflict:
        raise HTTPException(409, "Conflicto de edición")
    """
    if received_version is None:
        return  # No validar si no envía versión

    if db_entity.row_version != received_version:
        raise RowVersionConflict(
            current_version=db_entity.row_version,
            received_version=received_version
        )


def format_conflict_response(exc: RowVersionConflict, record: dict) -> dict:
    """
    Formatear respuesta de conflicto para frontend.

    Retorna el estado actual del registro + versión para que el usuario
    pueda ver qué cambió antes de re-intentar.

    Ejemplo de respuesta:
      {
        "status": "conflict",
        "message": "Otro usuario modificó este registro",
        "current_record": {...},
        "current_version": 7
      }
    """
    return {
        "status": "conflict",
        "message": "Este registro fue modificado por otro usuario",
        "detail": f"Tu versión: {exc.received_version}, versión actual: {exc.current_version}",
        "current_record": record,
        "current_version": exc.current_version,
    }
