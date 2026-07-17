"""Validación SSRF para URLs de destino controladas por el usuario.

Hallazgo real 2026-07-17 (evaluación OWASP API7 — SSRF, primera ronda que
cubrió este ítem, ver docs/hallazgos/2026-07-16_reporte_fiabilidad_3dias_y_plan.md
"nunca evaluado"): ``POST /webhooks`` deja registrar una ``url`` completamente
libre a cualquier usuario con ``nivel_acceso <= 2`` (Director/Coordinador, no
solo Admin Global), y ``webhook_dispatcher.py`` la usa tal cual en
``httpx.AsyncClient().post(url, ...)`` cada vez que dispara un evento de
negocio. Sobre la infraestructura real de este proyecto (Oracle Cloud,
servidor único, ver CLAUDE.md), eso permite alcanzar el endpoint de metadata
del cloud (``169.254.169.254``) o servicios internos del propio host
(``ades-postgres:5432``, ``ades-valkey:6379``, etc.) — y la respuesta queda
además legible vía ``GET /webhooks/{id}/logs`` (oráculo de lectura interna).

Esta validación se aplica en DOS puntos (defensa en profundidad real, no
cosmética):
  1. Al registrar/actualizar el webhook (``webhooks.py``) — rechazo temprano,
     mejor feedback al usuario.
  2. Justo antes de cada despacho real (``webhook_dispatcher.py``) — la
     resolución DNS puede cambiar entre el registro y el envío (DNS
     rebinding); validar solo en el registro no es suficiente.
"""
from __future__ import annotations

import ipaddress
import socket
from urllib.parse import urlparse

_ESQUEMAS_PERMITIDOS = {"http", "https"}

# Hosts explícitamente bloqueados además de los rangos de IP privada/reservada
# — cubre nombres DNS internos de Docker/infra que no son IP literal.
_HOSTS_BLOQUEADOS = {
    "localhost", "ades-postgres", "ades-pgbouncer", "ades-valkey",
    "ades-authentik-server", "ades-authentik-worker", "ades-api", "ades-bff",
    "ades-frontend", "ades-nginx", "ades-minio", "ades-superset",
    "metadata", "metadata.google.internal",
}


class UrlNoPermitidaError(ValueError):
    """La URL de destino apunta a un host interno/privado — SSRF bloqueado."""


def validar_url_publica(url: str) -> None:
    """Levanta :class:`UrlNoPermitidaError` si ``url`` no es un destino externo seguro.

    Verifica esquema (solo http/https), rechaza hosts internos conocidos por
    nombre, y resuelve el hostname para rechazar cualquier IP privada, de
    loopback, link-local (incluye 169.254.169.254, metadata de todo cloud
    mayor), multicast o reservada — no solo el string tal como llegó.
    """
    parsed = urlparse(url)

    if parsed.scheme not in _ESQUEMAS_PERMITIDOS:
        raise UrlNoPermitidaError(f"Esquema no permitido: {parsed.scheme!r} (solo http/https)")

    host = parsed.hostname
    if not host:
        raise UrlNoPermitidaError("URL sin host válido")

    if host.lower() in _HOSTS_BLOQUEADOS:
        raise UrlNoPermitidaError(f"Host interno no permitido: {host}")

    # Resolver el hostname a IP(s) reales — un dominio público puede apuntar
    # a una IP privada (DNS rebinding / configuración maliciosa deliberada).
    try:
        infos = socket.getaddrinfo(host, None)
    except socket.gaierror as exc:
        raise UrlNoPermitidaError(f"No se pudo resolver el host: {host}") from exc

    for info in infos:
        ip_str = info[4][0]
        try:
            ip = ipaddress.ip_address(ip_str)
        except ValueError:
            continue
        if (
            ip.is_private or ip.is_loopback or ip.is_link_local
            or ip.is_multicast or ip.is_reserved or ip.is_unspecified
        ):
            raise UrlNoPermitidaError(
                f"El host {host} resuelve a una IP interna/privada ({ip_str}) — no permitido"
            )
