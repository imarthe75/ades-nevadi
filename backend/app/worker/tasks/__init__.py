"""Paquete de tareas Celery para el worker ADES.

Cada submódulo agrupa tareas relacionadas por dominio:

- blockchain   — anclaje de certificados en Polygon PoS
- boletas      — generación batch de PDFs de boletas (NEM y UAEMEX)
- notificaciones — alertas académicas y refresco de vistas BI
- ocr          — procesamiento OCR vía Paperless-ngx
- sepomex      — sincronización semanal del catálogo postal
"""
