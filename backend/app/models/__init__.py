from .base import AuditMixin
from .academica import Escuela, Plantel, NivelEducativo, PlantelNivel, Grado, CicloEscolar, Grupo
from .personas import Estatus, Rol, Persona, Profesor, Estudiante, Inscripcion, Usuario
from .materias import Materia, MateriaPlan, Tema, AsignacionDocente
from .operacion import (
    PeriodoEvaluacion, Clase, Asistencia, CalificacionPeriodo,
    Tarea, TareaEntrega, CalificacionEntrega, Archivo,
)
from .fase3 import (
    Aula, Horario, DisponibilidadDocente,
    PersonalSalud, ExpedienteMedico, IncidenteMedico,
    ReporteConducta, ReporteAcademico,
)

__all__ = [
    "AuditMixin",
    "Escuela", "Plantel", "NivelEducativo", "PlantelNivel", "Grado", "CicloEscolar", "Grupo",
    "Estatus", "Rol", "Persona", "Profesor", "Estudiante", "Inscripcion", "Usuario",
    "Materia", "MateriaPlan", "Tema", "AsignacionDocente",
    "PeriodoEvaluacion", "Clase", "Asistencia", "CalificacionPeriodo",
    "Tarea", "TareaEntrega", "CalificacionEntrega", "Archivo",
    "Aula", "Horario", "DisponibilidadDocente",
    "PersonalSalud", "ExpedienteMedico", "IncidenteMedico",
    "ReporteConducta", "ReporteAcademico",
]
