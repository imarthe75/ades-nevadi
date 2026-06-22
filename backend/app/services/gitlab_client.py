"""
Cliente asíncrono para GitLab API v4.

Responsabilidades principales:
- Crear issues de remediación en proyectos auditados.
- Leer archivos y metadatos de proyecto cuando el pipeline lo requiere.
- Verificar secret token de webhook para invocaciones de confianza.
"""

from __future__ import annotations

import hmac
import hashlib
from dataclasses import dataclass
from typing import Any

import httpx


@dataclass(slots=True)
class GitLabClient:
    base_url: str
    api_token: str
    timeout_seconds: int = 20

    @property
    def _headers(self) -> dict[str, str]:
        return {
            "PRIVATE-TOKEN": self.api_token,
            "Content-Type": "application/json",
        }

    async def create_issue(
        self,
        project_id: int | str,
        title: str,
        description: str,
        labels: list[str] | None = None,
    ) -> dict[str, Any]:
        url = f"{self.base_url.rstrip('/')}/api/v4/projects/{project_id}/issues"
        payload: dict[str, Any] = {
            "title": title,
            "description": description,
        }
        if labels:
            payload["labels"] = ",".join(labels)

        async with httpx.AsyncClient(timeout=self.timeout_seconds) as client:
            resp = await client.post(url, headers=self._headers, json=payload)
            resp.raise_for_status()
            return resp.json()

    async def get_file(
        self,
        project_id: int | str,
        file_path: str,
        ref: str = "main",
    ) -> dict[str, Any]:
        safe_path = file_path.strip("/").replace("/", "%2F")
        url = (
            f"{self.base_url.rstrip('/')}/api/v4/projects/{project_id}/repository/files/"
            f"{safe_path}?ref={ref}"
        )
        async with httpx.AsyncClient(timeout=self.timeout_seconds) as client:
            resp = await client.get(url, headers=self._headers)
            resp.raise_for_status()
            return resp.json()

    async def get_project(self, project_id: int | str) -> dict[str, Any]:
        url = f"{self.base_url.rstrip('/')}/api/v4/projects/{project_id}"
        async with httpx.AsyncClient(timeout=self.timeout_seconds) as client:
            resp = await client.get(url, headers=self._headers)
            resp.raise_for_status()
            return resp.json()


def verify_gitlab_webhook_signature(
    raw_body: bytes,
    secret: str,
    provided_token: str | None,
) -> bool:
    """
    GitLab envía X-Gitlab-Token como secreto compartido.

    Se acepta comparación de igualdad directa y comparación HMAC para tolerar
    implementaciones que firman el payload.
    """
    if not secret:
        return True
    if not provided_token:
        return False

    if hmac.compare_digest(secret, provided_token):
        return True

    digest = hmac.new(secret.encode("utf-8"), raw_body, hashlib.sha256).hexdigest()
    return hmac.compare_digest(digest, provided_token)
