"""
LLMService — abstracción de cliente OpenAI compatible (NVIDIA NIM / OpenAI).

Centraliza la creación del cliente, el modelo por defecto y el manejo de
indisponibilidad del API key. Todos los routers de IA inyectan este servicio
via FastAPI Depends() en lugar de instanciar OpenAI directamente.
"""
from __future__ import annotations

from functools import lru_cache
from typing import Any

from fastapi import HTTPException

from app.core.config import settings


class LLMService:
    """Thin wrapper alrededor del cliente OpenAI compatible."""

    def __init__(self, api_key: str, base_url: str, default_model: str) -> None:
        self._api_key = api_key
        self._base_url = base_url
        self._default_model = default_model

    # ── sync (para endpoints síncronos como /ai/chat) ──────────────────────

    def sync_client(self):
        if not self._api_key:
            raise HTTPException(status_code=503, detail="LLM API key no configurado")
        try:
            from openai import OpenAI  # type: ignore
        except ImportError as exc:
            raise HTTPException(status_code=503, detail="openai no disponible") from exc
        return OpenAI(api_key=self._api_key, base_url=self._base_url)

    def complete(
        self,
        messages: list[dict[str, str]],
        model: str | None = None,
        temperature: float = 0.7,
        max_tokens: int = 1024,
        **kwargs: Any,
    ) -> Any:
        client = self.sync_client()
        return client.chat.completions.create(
            model=model or self._default_model,
            messages=messages,
            temperature=temperature,
            max_tokens=max_tokens,
            **kwargs,
        )

    # ── async (para endpoints async) ───────────────────────────────────────

    def async_client(self):
        if not self._api_key:
            raise HTTPException(status_code=503, detail="LLM API key no configurado")
        try:
            from openai import AsyncOpenAI  # type: ignore
        except ImportError as exc:
            raise HTTPException(status_code=503, detail="openai no disponible") from exc
        return AsyncOpenAI(api_key=self._api_key, base_url=self._base_url)

    async def async_complete(
        self,
        messages: list[dict[str, str]],
        model: str | None = None,
        temperature: float = 0.7,
        max_tokens: int = 1024,
        **kwargs: Any,
    ) -> Any:
        client = self.async_client()
        return await client.chat.completions.create(
            model=model or self._default_model,
            messages=messages,
            temperature=temperature,
            max_tokens=max_tokens,
            **kwargs,
        )

    @property
    def default_model(self) -> str:
        return self._default_model

    @property
    def available(self) -> bool:
        return bool(self._api_key)


@lru_cache(maxsize=1)
def _build_llm_service() -> LLMService:
    return LLMService(
        api_key=settings.OPENAI_API_KEY,
        base_url=settings.OPENAI_BASE_URL,
        default_model=settings.OPENAI_MODEL,
    )


def get_llm_service() -> LLMService:
    """FastAPI dependency — returns the singleton LLMService."""
    return _build_llm_service()
