from fastapi import APIRouter
from .health import router as health_router
from .ai_assistant import router as ai_router
from .chatbot import router as chatbot_router
from .carbone import router as carbone_router
from .push import router as push_router
from .pdf_tools import router as pdf_tools_router
from .automations import router as automations_router
from .webhooks import router as webhooks_router
from .ia_avanzada import router as ia_avanzada_router
from .certificados import router as certificados_router
from .expediente import router as expediente_router

api_router = APIRouter()

api_router.include_router(health_router)
api_router.include_router(ai_router)
api_router.include_router(chatbot_router)
api_router.include_router(carbone_router)
api_router.include_router(push_router)
api_router.include_router(pdf_tools_router)
api_router.include_router(automations_router)
api_router.include_router(webhooks_router)
api_router.include_router(ia_avanzada_router)
api_router.include_router(certificados_router)
api_router.include_router(expediente_router)
