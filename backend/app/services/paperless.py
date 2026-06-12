"""
services/paperless.py
=====================
Cliente async para la API REST de Paperless-ngx.

Gestiona la comunicacion con el motor OCR/documental Paperless-ngx,
incluyendo: subida de documentos, descarga, busqueda full-text y
recuperacion del texto OCR extraido.

Todos los metodos son async y usan httpx.AsyncClient con timeout razonable.
El token de API se lee desde Settings (inyectado por Vault al arrancar).
"""

import io
import logging
from typing import Any

import httpx

from app.core.config import settings

log = logging.getLogger(__name__)

# Timeout en segundos para operaciones de Paperless (OCR puede tardar)
_TIMEOUT = httpx.Timeout(connect=10.0, read=120.0, write=60.0, pool=5.0)


def _headers() -> dict[str, str]:
    """Devuelve los headers de autenticacion para Paperless-ngx."""
    return {
        "Authorization": f"Token {settings.PAPERLESS_API_TOKEN}",
        "Accept": "application/json",
    }


async def subir_documento(
    nombre: str,
    contenido: bytes,
    tipo_mime: str = "application/pdf",
    titulo: str | None = None,
    tags: list[int] | None = None,
) -> int | None:
    """
    Sube un documento a Paperless-ngx via POST /api/documents/post_document/.

    Args:
        nombre:     Nombre del archivo (ej. 'curp_garcia_lopez.pdf').
        contenido:  Bytes del archivo.
        tipo_mime:  MIME type del archivo (default: application/pdf).
        titulo:     Titulo opcional para Paperless. Si None, usa 'nombre'.
        tags:       Lista de IDs de etiquetas de Paperless a asignar.

    Returns:
        task_id de la tarea de importacion en Paperless, o None si falla.
        NOTA: Paperless procesa async; el ID del documento se obtiene
        consultando el estado de la tarea con obtener_estado_tarea().
    """
    url = f"{settings.PAPERLESS_URL}/api/documents/post_document/"
    form_data: dict[str, Any] = {}
    if titulo:
        form_data["title"] = titulo
    if tags:
        for tag_id in tags:
            form_data["tags"] = str(tag_id)

    files = {"document": (nombre, io.BytesIO(contenido), tipo_mime)}

    try:
        async with httpx.AsyncClient(timeout=_TIMEOUT) as client:
            resp = await client.post(
                url,
                headers={"Authorization": f"Token {settings.PAPERLESS_API_TOKEN}"},
                data=form_data,
                files=files,
            )
            resp.raise_for_status()
            # Paperless devuelve el UUID de la tarea de importacion como string
            task_id = resp.text.strip().strip('"')
            log.info("paperless.subir_documento: tarea=%s archivo=%s", task_id, nombre)
            return task_id
    except httpx.HTTPStatusError as exc:
        log.error("paperless.subir_documento HTTP %s: %s", exc.response.status_code, exc.response.text)
        return None
    except Exception as exc:
        log.error("paperless.subir_documento error: %s", exc)
        return None


async def obtener_estado_tarea(task_id: str) -> dict | None:
    """
    Consulta el estado de una tarea de importacion de Paperless.

    Returns:
        Dict con campos: status, result (doc_id cuando completa), task_id.
    """
    url = f"{settings.PAPERLESS_URL}/api/tasks/?task_id={task_id}"
    try:
        async with httpx.AsyncClient(timeout=_TIMEOUT) as client:
            resp = await client.get(url, headers=_headers())
            resp.raise_for_status()
            data = resp.json()
            if data and len(data) > 0:
                return data[0]
            return None
    except Exception as exc:
        log.error("paperless.obtener_estado_tarea %s: %s", task_id, exc)
        return None


async def obtener_documento(doc_id: int) -> dict | None:
    """
    Obtiene los metadatos de un documento por su ID en Paperless.

    Returns:
        Dict con: id, title, content (texto OCR), created, tags, etc.
    """
    url = f"{settings.PAPERLESS_URL}/api/documents/{doc_id}/"
    try:
        async with httpx.AsyncClient(timeout=_TIMEOUT) as client:
            resp = await client.get(url, headers=_headers())
            resp.raise_for_status()
            return resp.json()
    except httpx.HTTPStatusError as exc:
        if exc.response.status_code == 404:
            log.warning("paperless.obtener_documento: doc_id=%s no encontrado", doc_id)
        else:
            log.error("paperless.obtener_documento HTTP %s: %s", exc.response.status_code, exc.response.text)
        return None
    except Exception as exc:
        log.error("paperless.obtener_documento error doc_id=%s: %s", doc_id, exc)
        return None


async def descargar_documento(doc_id: int) -> bytes | None:
    """
    Descarga el archivo original de un documento en Paperless.

    Returns:
        Bytes del documento (PDF, imagen, etc.), o None si falla.
    """
    url = f"{settings.PAPERLESS_URL}/api/documents/{doc_id}/download/"
    try:
        async with httpx.AsyncClient(timeout=_TIMEOUT) as client:
            resp = await client.get(url, headers=_headers())
            resp.raise_for_status()
            return resp.content
    except Exception as exc:
        log.error("paperless.descargar_documento doc_id=%s: %s", doc_id, exc)
        return None


async def obtener_preview(doc_id: int) -> bytes | None:
    """
    Obtiene la imagen de preview (thumbnail) de un documento en Paperless.

    Returns:
        Bytes de la imagen PNG/JPEG, o None si falla.
    """
    url = f"{settings.PAPERLESS_URL}/api/documents/{doc_id}/preview/"
    try:
        async with httpx.AsyncClient(timeout=_TIMEOUT) as client:
            resp = await client.get(url, headers=_headers())
            resp.raise_for_status()
            return resp.content
    except Exception as exc:
        log.error("paperless.obtener_preview doc_id=%s: %s", doc_id, exc)
        return None


async def buscar_documentos(
    query: str = "",
    page: int = 1,
    page_size: int = 25,
) -> dict:
    """
    Busqueda full-text en Paperless-ngx.

    Args:
        query:     Termino de busqueda (texto libre, soporta operadores).
        page:      Pagina de resultados.
        page_size: Resultados por pagina.

    Returns:
        Dict con: count, next, previous, results (lista de documentos).
    """
    params: dict[str, Any] = {
        "page": page,
        "page_size": page_size,
    }
    if query:
        params["query"] = query

    url = f"{settings.PAPERLESS_URL}/api/documents/"
    try:
        async with httpx.AsyncClient(timeout=_TIMEOUT) as client:
            resp = await client.get(url, headers=_headers(), params=params)
            resp.raise_for_status()
            return resp.json()
    except Exception as exc:
        log.error("paperless.buscar_documentos query='%s': %s", query, exc)
        return {"count": 0, "results": []}


async def obtener_texto_ocr(doc_id: int) -> str:
    """
    Obtiene el texto extraido por OCR de un documento.

    Returns:
        Texto extraido como string, o cadena vacia si falla.
    """
    doc = await obtener_documento(doc_id)
    if doc and doc.get("content"):
        return doc["content"]
    return ""


async def eliminar_documento(doc_id: int) -> bool:
    """
    Elimina un documento de Paperless-ngx.

    Returns:
        True si se elimino correctamente, False en caso de error.
    """
    url = f"{settings.PAPERLESS_URL}/api/documents/{doc_id}/"
    try:
        async with httpx.AsyncClient(timeout=_TIMEOUT) as client:
            resp = await client.delete(url, headers=_headers())
            return resp.status_code == 204
    except Exception as exc:
        log.error("paperless.eliminar_documento doc_id=%s: %s", doc_id, exc)
        return False


async def verificar_conexion() -> bool:
    """
    Verifica que Paperless-ngx este accesible y el token sea valido.

    Returns:
        True si Paperless responde con HTTP 200, False en caso contrario.
    """
    url = f"{settings.PAPERLESS_URL}/api/documents/"
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(5.0)) as client:
            resp = await client.get(url, headers=_headers())
            return resp.status_code == 200
    except Exception:
        return False
