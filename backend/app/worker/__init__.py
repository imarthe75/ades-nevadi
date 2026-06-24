"""Paquete worker: tareas Celery asíncronas del backend ADES.

Expone el objeto ``celery_app`` y registra todos los módulos de tareas
(boletas, notificaciones, blockchain, sepomex, ocr) para que el worker
los descubra automáticamente al arrancar.
"""
