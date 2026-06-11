# FASE 26 — Integración del Starter en ADES
## Instrucciones para Claude Code

**Fecha:** 2026-06-09  
**Proyecto:** ADES — Administración Escolar Instituto Nevadi  
**Objetivo:** Incorporar las mejores capacidades del starter (catálogos dinámicos, variables del sistema, menús administrables, privilegios granulares, multi-rol, trazabilidad enriquecida, notificaciones in-app) sin reducir ninguna funcionalidad existente de ADES.

---

## CONTEXTO OBLIGATORIO — LEE ESTO PRIMERO

Antes de escribir cualquier línea de código, Claude Code debe:

1. Leer `CLAUDE.md` en la raíz del proyecto
2. Leer `.agent/CONTEXT.md` para entender la arquitectura completa
3. Leer `.agent/STATE.md` para conocer el estado actual
4. Leer `openspec.yaml` para respetar la estructura de especificaciones
5. Verificar el estado de los servicios: `docker compose ps`
6. Revisar la última migración aplicada: `ls db/migrations/ | sort | tail -5`

**Stack técnico de referencia:**
- Backend: FastAPI (Python 3.12) + SQLAlchemy 2.x async + PostgreSQL 18
- Frontend: Angular 19+ standalone components + PrimeNG 21+
- Auth: Authentik OIDC (usuarios sincronizados en `ades_usuarios`)
- Modelos base: todos heredan de `AuditMixin` que incluye `ref`, `is_active`, `fccreacion`, `fcmodificacion`, `usuario_creacion`, `usuario_modificacion`, `row_version`
- PKs: UUID v7 via `server_default=func.uuidv7()`
- Convención de nombres en BD: prefijo `ades_` en todas las tablas

---

## ANÁLISIS DEL STARTER

El starter exportado contiene las siguientes tablas que ADES no tiene o tiene de forma incompleta:

```
ct_catalogos          → cabecera de catálogos dinámicos
dt_catalogos          → items/valores de catálogos dinámicos
ct_variables_sistema  → variables configurables con 7 tipos de input
menus                 → árbol de navegación administrable
menu_roles            → asignación de menús a roles (N:M)
roles                 → roles del sistema (ADES ya tiene ades_roles, se amplía)
privilegios           → permisos granulares más allá del nivel_acceso
rol_privilegios       → relación roles ↔ privilegios (N:M)
usuario_roles         → un usuario puede tener N roles con peso
notificaciones        → notificaciones in-app del sistema (INFO/WARN/ERROR)
mensajes              → comunicados/anuncios globales (similar a ades_comunicados)
usuarios_mensajes     → acuse de lectura de mensajes
trazabilidad_negocio  → audit log enriquecido con metadata JSONB y risk level
```

**Tipos de valor para variables del sistema (del starter):**

| tipo_valor_ref | Tipo UI | Input Angular |
|---|---|---|
| 5 (TEXTO) | Texto libre | `<input pInputText>` |
| 6 (BOOLEANO) | Toggle | `<p-toggleswitch>` |
| 7 (JSON) | Editor JSON | `<textarea>` con validación JSON |
| 8 (NUMERO) | Número | `<p-inputnumber>` |
| 9 (FECHA) | Fecha | `<p-datepicker>` |
| 10 (HORA) | Hora | `<p-datepicker [timeOnly]="true">` |
| 11 (PASSWORD) | Secreto enmascarado | `<input type="password">` |

---

## FASE 26-A: Variables del Sistema + Catálogos Dinámicos

### A1 — Migración de Base de Datos

Crear archivo `db/migrations/021_variables_catalogos.sql`:

```sql
-- =============================================================================
-- MIGRACIÓN 021: Variables del Sistema + Catálogos Dinámicos
-- Origen: Starter → ADES
-- Fecha: 2026-06-09
-- =============================================================================

BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────
-- CATÁLOGOS DINÁMICOS
-- Patrón: cabecera (ades_catalogos) + items (ades_catalogo_items)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ades_catalogos (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    codigo      VARCHAR(80)  NOT NULL UNIQUE,   -- CAT_GENEROS, CAT_PARENTESCO...
    nombre      VARCHAR(150) NOT NULL,
    descripcion VARCHAR(500),
    -- AuditMixin
    ref                  UUID        DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);

COMMENT ON TABLE ades_catalogos IS 'Cabecera de catálogos dinámicos administrables desde el módulo Admin.';

CREATE TABLE IF NOT EXISTS ades_catalogo_items (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    catalogo_id  UUID NOT NULL REFERENCES ades_catalogos(id) ON DELETE RESTRICT,
    valor        VARCHAR(200) NOT NULL,
    descripcion  VARCHAR(500),
    orden        INTEGER NOT NULL DEFAULT 0,
    -- AuditMixin
    ref                  UUID        DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_catalogo_valor UNIQUE (catalogo_id, valor)
);

COMMENT ON TABLE ades_catalogo_items IS 'Items/valores de los catálogos dinámicos.';

CREATE INDEX IF NOT EXISTS idx_catalogo_items_catalogo ON ades_catalogo_items(catalogo_id);
CREATE INDEX IF NOT EXISTS idx_catalogo_items_activo ON ades_catalogo_items(catalogo_id, is_active);

-- ─────────────────────────────────────────────────────────────────────────────
-- VARIABLES DEL SISTEMA
-- tipo_valor referencia a items del catálogo CAT_TIPO_INPUT_VARS
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ades_variables_sistema (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    nombre          VARCHAR(100) NOT NULL UNIQUE,  -- Clave: ZONA_HORARIA, NOMBRE_INSTITUCION...
    tipo_valor      VARCHAR(20)  NOT NULL DEFAULT 'TEXTO',
                    -- TEXTO | BOOLEANO | JSON | NUMERO | FECHA | HORA | PASSWORD
    valor           TEXT,                          -- Almacenado como texto
    descripcion     VARCHAR(500),
    encriptado      BOOLEAN NOT NULL DEFAULT FALSE, -- TRUE para tipo PASSWORD
    solo_lectura    BOOLEAN NOT NULL DEFAULT FALSE, -- Protege variables críticas del sistema
    grupo           VARCHAR(50),                    -- SISTEMA | ACADEMICO | CORREO | GEO | IA | etc.
    -- AuditMixin
    ref                  UUID        DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT ck_tipo_valor CHECK (
        tipo_valor IN ('TEXTO', 'BOOLEANO', 'JSON', 'NUMERO', 'FECHA', 'HORA', 'PASSWORD')
    )
);

COMMENT ON TABLE ades_variables_sistema IS 'Variables de configuración del sistema, administrables desde el módulo Admin sin necesidad de SSH.';
COMMENT ON COLUMN ades_variables_sistema.encriptado IS 'Si TRUE, el valor se almacena encriptado con pgcrypto y la UI nunca devuelve el valor plano.';
COMMENT ON COLUMN ades_variables_sistema.solo_lectura IS 'Si TRUE, la variable no puede modificarse desde la UI (solo desde migraciones).';

CREATE INDEX IF NOT EXISTS idx_variables_grupo ON ades_variables_sistema(grupo);
CREATE INDEX IF NOT EXISTS idx_variables_activo ON ades_variables_sistema(is_active);

-- ─────────────────────────────────────────────────────────────────────────────
-- SEED: Catálogos base
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO ades_catalogos (codigo, nombre, descripcion) VALUES
    ('CAT_GENEROS',           'Géneros',                    'Géneros de las personas'),
    ('CAT_PARENTESCO',        'Parentesco',                 'Relación familiar del contacto con el alumno'),
    ('CAT_TIPO_CONTRATO',     'Tipo de Contrato',           'Tipo de contrato de los profesores'),
    ('CAT_NIVEL_ESTUDIOS',    'Nivel de Estudios',          'Máximo nivel educativo alcanzado'),
    ('CAT_TIPO_SANGRE',       'Tipo de Sangre',             'Grupos sanguíneos para expediente médico'),
    ('CAT_TURNO',             'Turno',                      'Turno escolar del grupo o profesor'),
    ('CAT_TIPO_FALTA',        'Tipo de Falta Disciplinaria','Clasificación de faltas disciplinarias'),
    ('CAT_TIPO_AUSENCIA',     'Tipo de Ausencia',           'Clasificación de ausencias en asistencias'),
    ('CAT_ESTADO_CIVIL',      'Estado Civil',               'Estado civil de personas'),
    ('CAT_TIPO_EVALUACION',   'Tipo de Evaluación',         'Modalidad del examen o evaluación')
ON CONFLICT (codigo) DO NOTHING;

-- Items de cada catálogo (ordenados por uso frecuente)
WITH cat AS (SELECT id, codigo FROM ades_catalogos)
INSERT INTO ades_catalogo_items (catalogo_id, valor, orden) VALUES
    -- CAT_GENEROS
    ((SELECT id FROM cat WHERE codigo='CAT_GENEROS'), 'Hombre',                  10),
    ((SELECT id FROM cat WHERE codigo='CAT_GENEROS'), 'Mujer',                   20),
    ((SELECT id FROM cat WHERE codigo='CAT_GENEROS'), 'No binario',              30),
    ((SELECT id FROM cat WHERE codigo='CAT_GENEROS'), 'Prefiero no indicar',     40),
    -- CAT_PARENTESCO
    ((SELECT id FROM cat WHERE codigo='CAT_PARENTESCO'), 'Padre',                10),
    ((SELECT id FROM cat WHERE codigo='CAT_PARENTESCO'), 'Madre',                20),
    ((SELECT id FROM cat WHERE codigo='CAT_PARENTESCO'), 'Tutor legal',          30),
    ((SELECT id FROM cat WHERE codigo='CAT_PARENTESCO'), 'Abuelo/a',             40),
    ((SELECT id FROM cat WHERE codigo='CAT_PARENTESCO'), 'Tío/a',                50),
    ((SELECT id FROM cat WHERE codigo='CAT_PARENTESCO'), 'Hermano/a mayor',      60),
    ((SELECT id FROM cat WHERE codigo='CAT_PARENTESCO'), 'Otro',                 99),
    -- CAT_TIPO_CONTRATO
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_CONTRATO'), 'TIEMPO_COMPLETO',   10),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_CONTRATO'), 'MEDIO_TIEMPO',      20),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_CONTRATO'), 'POR_HORAS',         30),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_CONTRATO'), 'EVENTUAL',          40),
    -- CAT_NIVEL_ESTUDIOS
    ((SELECT id FROM cat WHERE codigo='CAT_NIVEL_ESTUDIOS'), 'Primaria',         10),
    ((SELECT id FROM cat WHERE codigo='CAT_NIVEL_ESTUDIOS'), 'Secundaria',       20),
    ((SELECT id FROM cat WHERE codigo='CAT_NIVEL_ESTUDIOS'), 'Bachillerato',     30),
    ((SELECT id FROM cat WHERE codigo='CAT_NIVEL_ESTUDIOS'), 'Técnico',          35),
    ((SELECT id FROM cat WHERE codigo='CAT_NIVEL_ESTUDIOS'), 'Licenciatura',     40),
    ((SELECT id FROM cat WHERE codigo='CAT_NIVEL_ESTUDIOS'), 'Maestría',         50),
    ((SELECT id FROM cat WHERE codigo='CAT_NIVEL_ESTUDIOS'), 'Doctorado',        60),
    -- CAT_TIPO_SANGRE
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_SANGRE'), 'O+',  10),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_SANGRE'), 'O-',  20),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_SANGRE'), 'A+',  30),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_SANGRE'), 'A-',  40),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_SANGRE'), 'B+',  50),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_SANGRE'), 'B-',  60),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_SANGRE'), 'AB+', 70),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_SANGRE'), 'AB-', 80),
    -- CAT_TURNO
    ((SELECT id FROM cat WHERE codigo='CAT_TURNO'), 'MATUTINO',   10),
    ((SELECT id FROM cat WHERE codigo='CAT_TURNO'), 'VESPERTINO', 20),
    ((SELECT id FROM cat WHERE codigo='CAT_TURNO'), 'NOCTURNO',   30),
    -- CAT_TIPO_FALTA
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_FALTA'), 'Leve',     10),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_FALTA'), 'Moderada', 20),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_FALTA'), 'Grave',    30),
    -- CAT_TIPO_AUSENCIA
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_AUSENCIA'), 'Injustificada', 10),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_AUSENCIA'), 'Justificada',   20),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_AUSENCIA'), 'Retardo',       30),
    -- CAT_ESTADO_CIVIL
    ((SELECT id FROM cat WHERE codigo='CAT_ESTADO_CIVIL'), 'Soltero/a',   10),
    ((SELECT id FROM cat WHERE codigo='CAT_ESTADO_CIVIL'), 'Casado/a',    20),
    ((SELECT id FROM cat WHERE codigo='CAT_ESTADO_CIVIL'), 'Divorciado/a',30),
    ((SELECT id FROM cat WHERE codigo='CAT_ESTADO_CIVIL'), 'Viudo/a',     40),
    ((SELECT id FROM cat WHERE codigo='CAT_ESTADO_CIVIL'), 'Unión libre', 50),
    -- CAT_TIPO_EVALUACION
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_EVALUACION'), 'ORDINARIO',       10),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_EVALUACION'), 'EXTRAORDINARIO',  20),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_EVALUACION'), 'TITULO_SUFICIENCIA',30),
    ((SELECT id FROM cat WHERE codigo='CAT_TIPO_EVALUACION'), 'DIAGNOSTICO',     40)
ON CONFLICT (catalogo_id, valor) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- SEED: Variables del sistema ADES
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO ades_variables_sistema (nombre, tipo_valor, valor, descripcion, grupo, solo_lectura) VALUES
    -- GRUPO: SISTEMA
    ('NOMBRE_INSTITUCION',      'TEXTO',    'Instituto Nevadi',             'Nombre oficial de la institución educativa',                         'SISTEMA',         FALSE),
    ('NOMBRE_SISTEMA',          'TEXTO',    'ADES',                         'Nombre corto del sistema',                                          'SISTEMA',         TRUE),
    ('ZONA_HORARIA',            'TEXTO',    'America/Mexico_City',          'Zona horaria del servidor y la institución',                         'SISTEMA',         FALSE),
    ('URL_PORTAL',              'TEXTO',    'https://ades.setag.mx',        'URL pública de la aplicación',                                       'SISTEMA',         FALSE),
    ('URL_SERVICIOS',           'TEXTO',    'https://ades.setag.mx/api/v1', 'Base URL del API REST',                                              'SISTEMA',         FALSE),
    ('ACTIVAR_DEBUG',           'BOOLEANO', 'false',                        'Activa modo debug en el API (solo desarrollo)',                      'SISTEMA',         FALSE),
    ('PATH_TEMPORALES',         'TEXTO',    '/tmp',                         'Carpeta temporal para generación de archivos',                       'SISTEMA',         FALSE),
    ('HORA_RESPALDO_BD',        'HORA',     '02:00:00',                     'Hora programada del respaldo automático de base de datos',           'SISTEMA',         FALSE),
    ('JSON_CONFIG_UI',          'JSON',     '{"tema":"claro","idioma":"es"}','Configuración de la interfaz de usuario',                           'SISTEMA',         FALSE),
    ('JSON_MARCA',              'JSON',     '{"logo_url":"","color_primario":"#2563eb","slogan":"Formando el futuro"}','Identidad visual de la institución','SISTEMA',FALSE),
    -- GRUPO: GEO (SEPOMEX/INEGI)
    ('ID_PAIS_DEFAULT',         'NUMERO',   '52',                           'Clave internacional México ISO 3166-1 (para SEPOMEX)',               'GEO',             FALSE),
    ('ID_ESTADO_DEFAULT',       'NUMERO',   '15',                           'Clave de Estado de México (default para formularios)',               'GEO',             FALSE),
    ('ID_MUNICIPIO_DEFAULT',    'NUMERO',   '125',                          'Clave de municipio predeterminado',                                  'GEO',             FALSE),
    -- GRUPO: CORREO
    ('JSON_CORREO',             'JSON',     '{"host":"smtp.nevadi.mx","port":587,"from":"no-reply@nevadi.mx","tls":true}','Configuración del servidor de correo','CORREO',FALSE),
    -- GRUPO: NOTIFICACIONES
    ('HABILITAR_NOTIFICACIONES','BOOLEANO', 'true',                         'Activa el envío de notificaciones push vía ntfy',                    'NOTIFICACIONES',  FALSE),
    ('URL_NTFY',                'TEXTO',    'https://notify.ades.setag.mx', 'URL del servidor ntfy para notificaciones push',                     'NOTIFICACIONES',  FALSE),
    -- GRUPO: ACADEMICO (reglas de negocio educativas)
    ('ESCALA_CALIF_MIN',        'NUMERO',   '5',                            'Calificación mínima válida (SEP: 5, UAEMEX: 0)',                     'ACADEMICO',       FALSE),
    ('ESCALA_CALIF_MAX',        'NUMERO',   '10',                           'Calificación máxima válida',                                         'ACADEMICO',       FALSE),
    ('CALIFICACION_APROBATORIA','NUMERO',   '6',                            'Calificación mínima de aprobación',                                  'ACADEMICO',       FALSE),
    ('MAX_ALUMNOS_POR_GRUPO',   'NUMERO',   '30',                           'Capacidad máxima default por grupo (sobrecupo requiere autorización)','ACADEMICO',      FALSE),
    ('PORCENTAJE_AUSENTISMO_ALERTA','NUMERO','20',                          'Porcentaje de ausentismo que activa alertas automáticas',             'ACADEMICO',       FALSE),
    ('DIAS_AUSENCIAS_ALERTA',   'NUMERO',   '3',                            'Número de ausencias consecutivas que activa alerta al padre',        'ACADEMICO',       FALSE),
    ('FECHA_CIERRE_EJERCICIO',  'FECHA',    '2026-12-31T06:00:00.000Z',     'Fecha de cierre del ciclo escolar vigente',                          'ACADEMICO',       FALSE),
    -- GRUPO: REPORTES
    ('DIAS_VIGENCIA_LINK_BOLETA','NUMERO',  '30',                           'Días de validez de los links de descarga de boletas (MinIO presigned)','REPORTES',      FALSE),
    ('URL_CARBONE',             'TEXTO',    'http://carbone:3001',          'URL interna del microservicio Carbone (generación de PDFs)',         'REPORTES',        TRUE),
    ('URL_STIRLING',            'TEXTO',    'http://stirling-pdf:8081',     'URL interna de Stirling-PDF (marca de agua y compresión)',           'REPORTES',        TRUE),
    -- GRUPO: BI
    ('URL_SUPERSET',            'TEXTO',    'https://bi.ades.setag.mx',     'URL del servidor Apache Superset',                                   'BI',              FALSE),
    -- GRUPO: ALMACENAMIENTO
    ('URL_MINIO',               'TEXTO',    'https://minio.ades.setag.mx',  'URL pública de MinIO para descarga de archivos',                     'ALMACENAMIENTO',  FALSE),
    -- GRUPO: IA
    ('TOKEN_ANTHROPIC_API',     'PASSWORD', NULL,                           'API Key de Anthropic Claude (encriptada)',                           'IA',              FALSE),
    -- GRUPO: INTEGRACIONES (tokens externos encriptados)
    ('TOKEN_MINIO_ACCESS_KEY',  'PASSWORD', NULL,                           'Access Key de MinIO (encriptada)',                                   'INTEGRACIONES',   FALSE)
ON CONFLICT (nombre) DO UPDATE SET
    descripcion = EXCLUDED.descripcion,
    grupo = EXCLUDED.grupo;

-- Marcar las PASSWORD como encriptadas
UPDATE ades_variables_sistema
SET encriptado = TRUE
WHERE tipo_valor = 'PASSWORD';

COMMIT;
```

### A2 — Modelos Python (SQLAlchemy)

En `backend/app/models/`, crear o agregar en el archivo de modelos de sistema (puede ser `sistema.py`):

```python
# backend/app/models/sistema.py
"""
Modelos para Variables del Sistema y Catálogos Dinámicos.
Integración del patrón del starter → ADES.
"""
import uuid
from sqlalchemy import String, Boolean, Integer, ForeignKey, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship
from .base import AuditMixin, Base


class Catalogo(AuditMixin, Base):
    """Cabecera de catálogos dinámicos administrables desde Admin."""
    __tablename__ = "ades_catalogos"

    id:          Mapped[uuid.UUID] = mapped_column(primary_key=True, server_default="uuidv7()")
    codigo:      Mapped[str]       = mapped_column(String(80), nullable=False, unique=True)
    nombre:      Mapped[str]       = mapped_column(String(150), nullable=False)
    descripcion: Mapped[str | None]= mapped_column(String(500))

    items: Mapped[list["CatalogoItem"]] = relationship(
        back_populates="catalogo",
        cascade="all, delete-orphan",
        lazy="selectin"
    )


class CatalogoItem(AuditMixin, Base):
    """Items/valores de un catálogo dinámico."""
    __tablename__ = "ades_catalogo_items"
    __table_args__ = (UniqueConstraint("catalogo_id", "valor"),)

    id:          Mapped[uuid.UUID] = mapped_column(primary_key=True, server_default="uuidv7()")
    catalogo_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("ades_catalogos.id"), nullable=False)
    valor:       Mapped[str]       = mapped_column(String(200), nullable=False)
    descripcion: Mapped[str | None]= mapped_column(String(500))
    orden:       Mapped[int]       = mapped_column(Integer, default=0)

    catalogo: Mapped["Catalogo"] = relationship(back_populates="items")


TIPOS_VALOR_VALIDOS = ('TEXTO', 'BOOLEANO', 'JSON', 'NUMERO', 'FECHA', 'HORA', 'PASSWORD')


class VariableSistema(AuditMixin, Base):
    """
    Variables de configuración del sistema administrables desde el módulo Admin.
    No requiere reiniciar el servidor para aplicar cambios de negocio.
    """
    __tablename__ = "ades_variables_sistema"

    id:           Mapped[uuid.UUID] = mapped_column(primary_key=True, server_default="uuidv7()")
    nombre:       Mapped[str]       = mapped_column(String(100), nullable=False, unique=True)
    tipo_valor:   Mapped[str]       = mapped_column(String(20), nullable=False, default='TEXTO')
    valor:        Mapped[str | None]= mapped_column()              # TEXT en BD
    descripcion:  Mapped[str | None]= mapped_column(String(500))
    encriptado:   Mapped[bool]      = mapped_column(Boolean, default=False)
    solo_lectura: Mapped[bool]      = mapped_column(Boolean, default=False)
    grupo:        Mapped[str | None]= mapped_column(String(50))
```

### A3 — Schemas Pydantic

```python
# backend/app/schemas/sistema.py
from pydantic import BaseModel, field_validator
import uuid, json
from typing import Literal

TipoValor = Literal['TEXTO', 'BOOLEANO', 'JSON', 'NUMERO', 'FECHA', 'HORA', 'PASSWORD']

# ── Catálogos ──────────────────────────────────────────────────────────────────

class CatalogoItemCreate(BaseModel):
    valor: str
    descripcion: str | None = None
    orden: int = 0

class CatalogoItemOut(BaseModel):
    id: uuid.UUID
    catalogo_id: uuid.UUID
    valor: str
    descripcion: str | None
    orden: int
    is_active: bool
    row_version: int
    model_config = {"from_attributes": True}

class CatalogoItemUpdate(BaseModel):
    valor: str | None = None
    descripcion: str | None = None
    orden: int | None = None
    is_active: bool | None = None
    row_version: int  # Obligatorio para optimistic locking

class CatalogoCreate(BaseModel):
    codigo: str
    nombre: str
    descripcion: str | None = None

class CatalogoOut(BaseModel):
    id: uuid.UUID
    codigo: str
    nombre: str
    descripcion: str | None
    is_active: bool
    row_version: int
    items: list[CatalogoItemOut] = []
    model_config = {"from_attributes": True}

class CatalogoUpdate(BaseModel):
    nombre: str | None = None
    descripcion: str | None = None
    is_active: bool | None = None
    row_version: int

# ── Variables del Sistema ──────────────────────────────────────────────────────

class VariableOut(BaseModel):
    id: uuid.UUID
    nombre: str
    tipo_valor: str
    valor: str | None   # None si es PASSWORD
    descripcion: str | None
    encriptado: bool
    solo_lectura: bool
    grupo: str | None
    is_active: bool
    row_version: int
    model_config = {"from_attributes": True}

    @classmethod
    def from_model(cls, v):
        """Enmascara el valor si es PASSWORD."""
        obj = cls.model_validate(v)
        if v.encriptado or v.tipo_valor == 'PASSWORD':
            obj.valor = None  # Nunca exponer secretos
        return obj

class VariableUpdate(BaseModel):
    valor: str | None = None
    descripcion: str | None = None
    grupo: str | None = None
    row_version: int  # Obligatorio para optimistic locking

    @field_validator('valor')
    @classmethod
    def validar_valor(cls, v, info):
        # La validación real de tipo se hace en el endpoint con el tipo_valor del modelo
        return v

class VariableCreate(BaseModel):
    nombre: str
    tipo_valor: TipoValor
    valor: str | None = None
    descripcion: str | None = None
    encriptado: bool = False
    solo_lectura: bool = False
    grupo: str | None = None
```

### A4 — Endpoints FastAPI

```python
# backend/app/api/v1/catalogos.py
"""
CRUD de Catálogos Dinámicos y Variables del Sistema.
Solo accesible con nivel_acceso <= 1 (ADMIN_GLOBAL).
"""
import uuid
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from app.models.sistema import Catalogo, CatalogoItem, VariableSistema
from app.schemas.sistema import (
    CatalogoCreate, CatalogoOut, CatalogoUpdate,
    CatalogoItemCreate, CatalogoItemOut, CatalogoItemUpdate,
    VariableCreate, VariableOut, VariableUpdate,
)
from app.core.db import get_db
from app.core.security import get_ades_user, AdesUser
from app.core.optimistic_locking import check_row_version

router = APIRouter(tags=["catalogos-sistema"])


def require_admin(user: AdesUser) -> AdesUser:
    """Solo ADMIN_GLOBAL (nivel_acceso 0) puede gestionar catálogos y variables."""
    if user.nivel_acceso > 1:
        raise HTTPException(status_code=403, detail="Se requiere rol de Administrador Global")
    return user


# ── CATÁLOGOS ─────────────────────────────────────────────────────────────────

@router.get("/catalogos", response_model=list[CatalogoOut])
async def listar_catalogos(
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    """Lista todos los catálogos activos con sus items. Accesible para todos los roles."""
    result = await db.execute(
        select(Catalogo).where(Catalogo.is_active == True).order_by(Catalogo.codigo)
    )
    return result.scalars().all()


@router.get("/catalogos/{catalogo_id}", response_model=CatalogoOut)
async def obtener_catalogo(
    catalogo_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    cat = await db.get(Catalogo, catalogo_id)
    if not cat:
        raise HTTPException(404, "Catálogo no encontrado")
    return cat


@router.post("/catalogos", response_model=CatalogoOut, status_code=201)
async def crear_catalogo(
    data: CatalogoCreate,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    require_admin(user)
    cat = Catalogo(**data.model_dump(), usuario_creacion=str(user.id), usuario_modificacion=str(user.id))
    db.add(cat)
    await db.commit()
    await db.refresh(cat)
    return cat


@router.patch("/catalogos/{catalogo_id}", response_model=CatalogoOut)
async def actualizar_catalogo(
    catalogo_id: uuid.UUID,
    data: CatalogoUpdate,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    require_admin(user)
    cat = await db.get(Catalogo, catalogo_id)
    if not cat:
        raise HTTPException(404, "Catálogo no encontrado")
    check_row_version(cat, data.row_version)
    for field, value in data.model_dump(exclude={'row_version'}, exclude_unset=True).items():
        setattr(cat, field, value)
    cat.usuario_modificacion = str(user.id)
    await db.commit()
    await db.refresh(cat)
    return cat


# ── ITEMS DE CATÁLOGO ─────────────────────────────────────────────────────────

@router.post("/catalogos/{catalogo_id}/items", response_model=CatalogoItemOut, status_code=201)
async def agregar_item(
    catalogo_id: uuid.UUID,
    data: CatalogoItemCreate,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    require_admin(user)
    cat = await db.get(Catalogo, catalogo_id)
    if not cat:
        raise HTTPException(404, "Catálogo no encontrado")
    item = CatalogoItem(
        **data.model_dump(),
        catalogo_id=catalogo_id,
        usuario_creacion=str(user.id),
        usuario_modificacion=str(user.id)
    )
    db.add(item)
    await db.commit()
    await db.refresh(item)
    return item


@router.patch("/catalogos/items/{item_id}", response_model=CatalogoItemOut)
async def actualizar_item(
    item_id: uuid.UUID,
    data: CatalogoItemUpdate,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    require_admin(user)
    item = await db.get(CatalogoItem, item_id)
    if not item:
        raise HTTPException(404, "Item no encontrado")
    check_row_version(item, data.row_version)
    for field, value in data.model_dump(exclude={'row_version'}, exclude_unset=True).items():
        setattr(item, field, value)
    item.usuario_modificacion = str(user.id)
    await db.commit()
    await db.refresh(item)
    return item


# ── VARIABLES DEL SISTEMA ─────────────────────────────────────────────────────

@router.get("/config/variables", response_model=list[VariableOut])
async def listar_variables(
    grupo: str | None = None,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    require_admin(user)
    q = select(VariableSistema).where(VariableSistema.is_active == True)
    if grupo:
        q = q.where(VariableSistema.grupo == grupo)
    q = q.order_by(VariableSistema.grupo, VariableSistema.nombre)
    result = await db.execute(q)
    vars_ = result.scalars().all()
    # Enmascarar passwords antes de enviar
    return [VariableOut.from_model(v) for v in vars_]


@router.get("/config/public", response_model=list[VariableOut])
async def variables_publicas(db: AsyncSession = Depends(get_db)):
    """
    Endpoint público (sin auth) para variables no sensibles que el frontend necesita
    antes de login: NOMBRE_INSTITUCION, JSON_MARCA, JSON_CONFIG_UI.
    """
    result = await db.execute(
        select(VariableSistema).where(
            VariableSistema.is_active == True,
            VariableSistema.tipo_valor != 'PASSWORD',
            VariableSistema.encriptado == False,
            VariableSistema.nombre.in_([
                'NOMBRE_INSTITUCION', 'NOMBRE_SISTEMA',
                'JSON_CONFIG_UI', 'JSON_MARCA', 'URL_PORTAL'
            ])
        )
    )
    return [VariableOut.model_validate(v) for v in result.scalars().all()]


@router.patch("/config/variables/{nombre}", response_model=VariableOut)
async def actualizar_variable(
    nombre: str,
    data: VariableUpdate,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    require_admin(user)
    result = await db.execute(
        select(VariableSistema).where(VariableSistema.nombre == nombre)
    )
    var = result.scalar_one_or_none()
    if not var:
        raise HTTPException(404, f"Variable '{nombre}' no encontrada")
    if var.solo_lectura:
        raise HTTPException(403, f"La variable '{nombre}' es de solo lectura y no puede modificarse desde la UI")
    check_row_version(var, data.row_version)
    if data.valor is not None:
        var.valor = data.valor
    if data.descripcion is not None:
        var.descripcion = data.descripcion
    if data.grupo is not None:
        var.grupo = data.grupo
    var.usuario_modificacion = str(user.id)
    await db.commit()
    await db.refresh(var)
    return VariableOut.from_model(var)


@router.post("/config/variables", response_model=VariableOut, status_code=201)
async def crear_variable(
    data: VariableCreate,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    require_admin(user)
    existing = await db.execute(
        select(VariableSistema).where(VariableSistema.nombre == data.nombre)
    )
    if existing.scalar_one_or_none():
        raise HTTPException(409, f"Ya existe una variable con nombre '{data.nombre}'")
    var = VariableSistema(
        **data.model_dump(),
        usuario_creacion=str(user.id),
        usuario_modificacion=str(user.id)
    )
    db.add(var)
    await db.commit()
    await db.refresh(var)
    return VariableOut.from_model(var)
```

### A5 — Registrar router en main.py

```python
# En backend/app/main.py (o donde se registran los routers), agregar:
from app.api.v1.catalogos import router as catalogos_router

# Dentro de la función que registra los routers:
app.include_router(catalogos_router, prefix="/api/v1")
```

### A6 — Componente Angular: Tab Variables del Sistema

Agregar en `admin.component.ts` el tab de Variables del Sistema con un grid que muestre el input correcto según el `tipo_valor`:

```typescript
// Agregar en admin.component.ts

interface VariableSistema {
  id: string;
  nombre: string;
  tipo_valor: 'TEXTO' | 'BOOLEANO' | 'JSON' | 'NUMERO' | 'FECHA' | 'HORA' | 'PASSWORD';
  valor: string | null;
  descripcion: string | null;
  encriptado: boolean;
  solo_lectura: boolean;
  grupo: string | null;
  is_active: boolean;
  row_version: number;
  _editado?: boolean;
  _valorEdit?: string;   // copia para edición
}

// En la clase AdminComponent agregar:
variables = signal<VariableSistema[]>([]);
loadingVariables = signal(false);
gruposFiltro = signal<string[]>([]);
grupoSeleccionado = signal<string | null>(null);
variablesDlg = false;
variableEdit: VariableSistema | null = null;

cargarVariables(): void {
  this.loadingVariables.set(true);
  const params: Record<string, string> = {};
  if (this.grupoSeleccionado()) params['grupo'] = this.grupoSeleccionado()!;
  this.api.get<VariableSistema[]>('/config/variables', params).subscribe({
    next: vars => {
      this.variables.set(vars.map(v => ({ ...v, _valorEdit: v.valor ?? '' })));
      const grupos = [...new Set(vars.map(v => v.grupo).filter(Boolean))] as string[];
      this.gruposFiltro.set(['Todos', ...grupos.sort()]);
      this.loadingVariables.set(false);
    },
    error: () => this.loadingVariables.set(false),
  });
}

abrirEditarVariable(v: VariableSistema): void {
  if (v.solo_lectura) return;
  this.variableEdit = { ...v, _valorEdit: v.valor ?? '' };
  this.variablesDlg = true;
}

guardarVariable(): void {
  if (!this.variableEdit) return;
  const payload = {
    valor: this.variableEdit._valorEdit,
    row_version: this.variableEdit.row_version,
  };
  this.api.patch(`/config/variables/${this.variableEdit.nombre}`, payload).subscribe({
    next: () => {
      this.variablesDlg = false;
      this.msg.add({ severity: 'success', summary: 'Variable actualizada' });
      this.cargarVariables();
    },
    error: (e) => this.msg.add({ severity: 'error', summary: 'Error', detail: e.error?.detail }),
  });
}
```

**Template del Tab Variables (agregar en `<p-tabpanels>`):**

```html
<!-- ══ VARIABLES DEL SISTEMA ════════════════════════════════════════════════ -->
<p-tabpanel value="variables">
  <div class="tab-toolbar">
    <!-- Filtro por grupo -->
    <p-select
      [options]="gruposFiltro()"
      [(ngModel)]="grupoSeleccionadoStr"
      placeholder="Todos los grupos"
      (onChange)="onGrupoChange($event)"
      styleClass="ctx-selector" />
    <p-button label="Nueva variable" icon="pi pi-plus" (onClick)="abrirNuevaVariable()" />
  </div>

  <!-- Agrupado por grupo, con input correcto por tipo_valor -->
  @for (grupo of gruposActuales(); track grupo) {
    <div class="variable-grupo-header">
      <i class="pi pi-cog"></i> {{ grupo }}
    </div>
    <p-table
      [value]="variablesPorGrupo()[grupo]"
      [loading]="loadingVariables()"
      styleClass="p-datatable-sm">
      <ng-template pTemplate="header">
        <tr>
          <th style="width:220px">Variable</th>
          <th style="width:100px">Tipo</th>
          <th>Valor</th>
          <th style="width:300px">Descripción</th>
          <th style="width:60px"></th>
        </tr>
      </ng-template>
      <ng-template pTemplate="body" let-v>
        <tr [class.var-readonly]="v.solo_lectura">
          <td>
            <code class="var-nombre">{{ v.nombre }}</code>
          </td>
          <td>
            <p-tag
              [value]="v.tipo_valor"
              [severity]="tipoValorSeverity(v.tipo_valor)" />
          </td>
          <td>
            @if (v.tipo_valor === 'PASSWORD') {
              <span class="var-secret">••••••••</span>
            } @else if (v.tipo_valor === 'BOOLEANO') {
              <p-tag
                [value]="v.valor === 'true' ? 'Activo' : 'Inactivo'"
                [severity]="v.valor === 'true' ? 'success' : 'secondary'" />
            } @else if (v.tipo_valor === 'JSON') {
              <code class="var-json">{{ v.valor | slice:0:60 }}{{ (v.valor?.length ?? 0) > 60 ? '...' : '' }}</code>
            } @else {
              {{ v.valor }}
            }
          </td>
          <td class="var-desc">{{ v.descripcion }}</td>
          <td>
            @if (!v.solo_lectura) {
              <p-button
                icon="pi pi-pencil"
                [text]="true" [rounded]="true"
                pTooltip="Editar"
                (onClick)="abrirEditarVariable(v)" />
            } @else {
              <i class="pi pi-lock var-lock" pTooltip="Solo lectura"></i>
            }
          </td>
        </tr>
      </ng-template>
    </p-table>
  }
</p-tabpanel>
```

**Dialog de edición de variable (input dinámico):**

```html
<p-dialog [(visible)]="variablesDlg"
  [header]="variableEdit ? variableEdit.nombre : 'Variable'"
  [modal]="true" [style]="{width:'520px'}">
  @if (variableEdit) {
    <div style="display:flex;flex-direction:column;gap:1rem;">
      <p class="var-desc">{{ variableEdit.descripcion }}</p>

      <!-- Input dinámico según tipo_valor -->
      @switch (variableEdit.tipo_valor) {
        @case ('TEXTO') {
          <input pInputText [(ngModel)]="variableEdit._valorEdit" style="width:100%" />
        }
        @case ('PASSWORD') {
          <input type="password" pInputText [(ngModel)]="variableEdit._valorEdit"
                 placeholder="Ingrese nuevo valor secreto" style="width:100%" />
          <small>El valor actual está enmascarado por seguridad.</small>
        }
        @case ('BOOLEANO') {
          <div style="display:flex;align-items:center;gap:.75rem;">
            <p-toggleswitch
              [(ngModel)]="variableEdit._valorBool"
              (onChange)="variableEdit._valorEdit = $event.checked ? 'true' : 'false'" />
            <span>{{ variableEdit._valorEdit === 'true' ? 'Activo' : 'Inactivo' }}</span>
          </div>
        }
        @case ('NUMERO') {
          <p-inputNumber [(ngModel)]="variableEdit._valorNum"
            (onInput)="variableEdit._valorEdit = $event.value?.toString() ?? ''"
            styleClass="w-full" />
        }
        @case ('JSON') {
          <textarea pInputText [(ngModel)]="variableEdit._valorEdit"
            rows="8" style="width:100%;font-family:monospace;font-size:.8rem;"
            placeholder='{"clave": "valor"}'></textarea>
          <small [class.text-red-500]="!esJsonValido(variableEdit._valorEdit)">
            {{ esJsonValido(variableEdit._valorEdit) ? '✓ JSON válido' : '✗ JSON inválido' }}
          </small>
        }
        @case ('FECHA') {
          <p-datepicker [(ngModel)]="variableEdit._valorDate"
            (onSelect)="variableEdit._valorEdit = $event?.toISOString()"
            dateFormat="dd/mm/yy" styleClass="w-full" />
        }
        @case ('HORA') {
          <p-datepicker [(ngModel)]="variableEdit._valorDate"
            (onSelect)="variableEdit._valorEdit = $event?.toISOString()"
            [timeOnly]="true" hourFormat="24" styleClass="w-full" />
        }
      }

      <p-button label="Guardar" icon="pi pi-save"
        [disabled]="variableEdit.tipo_valor === 'JSON' && !esJsonValido(variableEdit._valorEdit)"
        (onClick)="guardarVariable()" />
    </div>
  }
</p-dialog>
```

### A7 — Tab Catálogos Dinámicos (admin.component.ts)

Agregar en el componente Admin el tab de Catálogos con patrón master-detail:

```html
<!-- ══ CATÁLOGOS DINÁMICOS ══════════════════════════════════════════════════ -->
<p-tabpanel value="catalogos">
  <div style="display:grid;grid-template-columns:280px 1fr;gap:1rem;min-height:500px;">

    <!-- Panel izquierdo: lista de catálogos -->
    <div class="cat-lista">
      <div class="tab-toolbar" style="padding:.5rem">
        <p-button label="Nuevo catálogo" icon="pi pi-plus" size="small"
          (onClick)="abrirNuevoCatalogo()" />
      </div>
      @for (cat of catalogos(); track cat.id) {
        <div class="cat-item" [class.activo]="catSeleccionado()?.id === cat.id"
          (click)="seleccionarCatalogo(cat)">
          <span class="cat-codigo">{{ cat.codigo }}</span>
          <span class="cat-nombre">{{ cat.nombre }}</span>
          <p-tag [value]="cat.items.length + ' items'" severity="secondary" />
        </div>
      }
    </div>

    <!-- Panel derecho: items del catálogo seleccionado (Interactive Grid editable) -->
    <div>
      @if (catSeleccionado()) {
        <div class="tab-toolbar">
          <h3>{{ catSeleccionado()!.nombre }}</h3>
          <p-button label="Agregar item" icon="pi pi-plus" size="small"
            (onClick)="abrirNuevoItem()" />
        </div>
        <app-interactive-grid
          [data]="catSeleccionado()!.items"
          [columns]="columnasCatalogo"
          [loading]="false"
          (rowSelected)="abrirEditarItem($event)" />
      } @else {
        <div class="empty-msg">Selecciona un catálogo para ver sus valores</div>
      }
    </div>
  </div>
</p-tabpanel>
```

---

## FASE 26-B: Menús Dinámicos

### B1 — Migración de Base de Datos

Crear `db/migrations/022_menus_dinamicos.sql`:

```sql
BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────
-- MENÚS ADMINISTRABLES
-- Árbol de navegación que antes estaba hardcodeado en el frontend.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ades_menus (
    id          SERIAL PRIMARY KEY,            -- int para FK simple, igual que starter
    label       VARCHAR(100) NOT NULL,         -- Texto visible en el menú
    route       VARCHAR(200),                  -- Ruta Angular: '/alumnos', 'external:url'
    icon        VARCHAR(80),                   -- Clase PrimeNG: 'pi pi-users'
    parent_id   INTEGER REFERENCES ades_menus(id),
    permission_id VARCHAR(80),                 -- 'alumnos:view', 'admin:manage' (futuro)
    peso        INTEGER NOT NULL DEFAULT 100,  -- Orden de aparición (menor = primero)
    -- AuditMixin
    ref                  UUID        DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);

COMMENT ON TABLE ades_menus IS 'Árbol de navegación administrable desde el módulo Admin, sin necesidad de redeploy del frontend.';
COMMENT ON COLUMN ades_menus.route IS 'Ruta Angular. Prefijo "external:" indica enlace externo.';

CREATE TABLE IF NOT EXISTS ades_menu_roles (
    menu_id   INTEGER REFERENCES ades_menus(id) ON DELETE CASCADE,
    rol_id    UUID REFERENCES ades_roles(id) ON DELETE CASCADE,
    PRIMARY KEY (menu_id, rol_id),
    -- AuditMixin simplificado
    ref                  UUID        DEFAULT gen_random_uuid() UNIQUE,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user
);

COMMENT ON TABLE ades_menu_roles IS 'Asignación de menús a roles. Un menú visible solo si el usuario tiene el rol asignado.';

-- ─────────────────────────────────────────────────────────────────────────────
-- SEED: estructura del menú actual de ADES
-- Replica el menú que actualmente está hardcodeado en el frontend
-- ─────────────────────────────────────────────────────────────────────────────

-- Menús de primer nivel (sin parent)
INSERT INTO ades_menus (label, route, icon, parent_id, peso) VALUES
    ('Dashboard',           '/dashboard',        'pi pi-home',          NULL, 10),
    ('Alumnos',             '/alumnos',          'pi pi-users',         NULL, 20),
    ('Profesores',          '/profesores',       'pi pi-id-card',       NULL, 30),
    ('Grupos',              '/grupos',           'pi pi-building',      NULL, 40),
    ('Calificaciones',      '/calificaciones',   'pi pi-star',          NULL, 50),
    ('Asistencias',         '/asistencias',      'pi pi-check-square',  NULL, 60),
    ('Evaluaciones',        '/evaluaciones',     'pi pi-file-edit',     NULL, 70),
    ('Tareas',              '/tareas',           'pi pi-book',          NULL, 80),
    ('Comunicados',         '/comunicados',      'pi pi-envelope',      NULL, 90),
    ('Conducta',            '/conducta',         'pi pi-flag',          NULL, 100),
    ('Expediente Médico',   '/medico',           'pi pi-heart',         NULL, 110),
    ('Gradebook',           '/gradebook',        'pi pi-table',         NULL, 120),
    ('Padres / Contactos',  '/padres',           'pi pi-heart-fill',    NULL, 130),
    ('BI / Analytics',      '/bi',               'pi pi-chart-bar',     NULL, 140),
    ('IA Académica',        '/ia',               'pi pi-sparkles',      NULL, 150),
    ('Geográficos',         '/geograficos',      'pi pi-map-marker',    NULL, 160),
    ('Administración',      NULL,                'pi pi-cog',           NULL, 999)
ON CONFLICT DO NOTHING;

-- Submenús de Administración (parent = id del menú 'Administración')
-- NOTA: Claude Code debe obtener el id del menú 'Administración' antes de insertar
-- Usar: INSERT ... SELECT id FROM ades_menus WHERE label = 'Administración'

INSERT INTO ades_menus (label, route, icon, parent_id, peso)
SELECT 'Usuarios',           '/admin#usuarios',   'pi pi-users',    id, 10 FROM ades_menus WHERE label = 'Administración'
UNION ALL
SELECT 'Roles',              '/admin#roles',      'pi pi-shield',   id, 20 FROM ades_menus WHERE label = 'Administración'
UNION ALL
SELECT 'Privilegios',        '/admin#privilegios','pi pi-lock',     id, 30 FROM ades_menus WHERE label = 'Administración'
UNION ALL
SELECT 'Menús',              '/admin#menus',      'pi pi-sitemap',  id, 40 FROM ades_menus WHERE label = 'Administración'
UNION ALL
SELECT 'Variables del Sistema','/admin#variables','pi pi-sliders',  id, 50 FROM ades_menus WHERE label = 'Administración'
UNION ALL
SELECT 'Catálogos',          '/admin#catalogos',  'pi pi-box',      id, 60 FROM ades_menus WHERE label = 'Administración'
UNION ALL
SELECT 'Ciclos Escolares',   '/admin#ciclos',     'pi pi-calendar', id, 70 FROM ades_menus WHERE label = 'Administración'
UNION ALL
SELECT 'Planteles',          '/admin#planteles',  'pi pi-building', id, 80 FROM ades_menus WHERE label = 'Administración'
UNION ALL
SELECT 'Auditoría',          '/admin#auditoria',  'pi pi-history',  id, 90 FROM ades_menus WHERE label = 'Administración';

COMMIT;
```

### B2 — Endpoint para el menú del usuario

```python
# backend/app/api/v1/menus.py
"""
Endpoint que devuelve el árbol de menú filtrado según el rol del usuario.
El frontend Angular lo consume al iniciar sesión para renderizar la navegación.
"""
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.models.menus import Menu, MenuRol  # Claude Code debe crear estos modelos
from app.core.db import get_db
from app.core.security import get_ades_user, AdesUser
import json

router = APIRouter(prefix="/menus", tags=["menus"])


@router.get("/mi-menu")
async def obtener_mi_menu(
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    """
    Devuelve el árbol de menús que el usuario puede ver según su rol.
    Si nivel_acceso == 0 (ADMIN_GLOBAL), devuelve todos los menús activos.
    """
    if user.nivel_acceso == 0:
        # Admin global ve todo
        result = await db.execute(
            select(Menu).where(Menu.is_active == True).order_by(Menu.peso)
        )
        menus = result.scalars().all()
    else:
        # Filtrar por rol del usuario
        result = await db.execute(
            select(Menu)
            .join(MenuRol, Menu.id == MenuRol.menu_id)
            .where(
                Menu.is_active == True,
                MenuRol.rol_id == user.rol_id
            )
            .order_by(Menu.peso)
        )
        menus = result.scalars().all()

    return _construir_arbol(menus)


def _construir_arbol(menus):
    """Convierte lista plana en árbol parent→children."""
    por_id = {m.id: {
        "id": m.id, "label": m.label, "route": m.route,
        "icon": m.icon, "peso": m.peso, "children": []
    } for m in menus}

    raiz = []
    for m in menus:
        nodo = por_id[m.id]
        if m.parent_id and m.parent_id in por_id:
            por_id[m.parent_id]["children"].append(nodo)
        elif not m.parent_id:
            raiz.append(nodo)

    return sorted(raiz, key=lambda x: x["peso"])
```

---

## FASE 26-C: Privilegios Granulares + Multi-rol + Trazabilidad Enriquecida

### C1 — Migración de Base de Datos

Crear `db/migrations/023_privilegios_multirol_trazabilidad.sql`:

```sql
BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────
-- PRIVILEGIOS GRANULARES
-- Permisos específicos más allá del nivel_acceso numérico (0-5).
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ades_privilegios (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    codigo      VARCHAR(80) NOT NULL UNIQUE,   -- 'BOLETAS_GENERAR', 'KAARDEX_APROBAR'
    nombre      VARCHAR(150),
    descripcion VARCHAR(500),
    modulo      VARCHAR(50),                   -- 'CALIFICACIONES', 'REPORTES', 'ADMIN'
    -- AuditMixin
    ref                  UUID        DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS ades_rol_privilegios (
    rol_id        UUID REFERENCES ades_roles(id) ON DELETE CASCADE,
    privilegio_id UUID REFERENCES ades_privilegios(id) ON DELETE CASCADE,
    PRIMARY KEY (rol_id, privilegio_id),
    -- AuditMixin simplificado
    ref           UUID DEFAULT gen_random_uuid() UNIQUE,
    row_version   INTEGER NOT NULL DEFAULT 1,
    fccreacion    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion VARCHAR(150) NOT NULL DEFAULT current_user
);

-- ─────────────────────────────────────────────────────────────────────────────
-- MULTI-ROL: Un usuario puede tener N roles con peso (mayor peso = rol activo)
-- Retrocompatible: ades_usuarios.rol_id se mantiene como "rol principal"
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ades_usuario_roles (
    usuario_id UUID REFERENCES ades_usuarios(id) ON DELETE CASCADE,
    rol_id     UUID REFERENCES ades_roles(id) ON DELETE CASCADE,
    peso       INTEGER NOT NULL DEFAULT 100,  -- Rol con mayor peso = activo por defecto
    PRIMARY KEY (usuario_id, rol_id),
    -- AuditMixin simplificado
    ref        UUID DEFAULT gen_random_uuid() UNIQUE,
    row_version INTEGER NOT NULL DEFAULT 1,
    fccreacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion VARCHAR(150) NOT NULL DEFAULT current_user
);

COMMENT ON TABLE ades_usuario_roles IS 'Permite multi-rol. El campo peso determina el rol activo principal (mayor peso = prioridad).';

-- Poblar ades_usuario_roles con los roles actuales (retrocompatibilidad)
INSERT INTO ades_usuario_roles (usuario_id, rol_id, peso)
SELECT id, rol_id, 100
FROM ades_usuarios
WHERE rol_id IS NOT NULL AND is_active = TRUE
ON CONFLICT (usuario_id, rol_id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- TRAZABILIDAD ENRIQUECIDA
-- Ampliar ades_audit_log con campos del starter (risk level, category, metadata)
-- ─────────────────────────────────────────────────────────────────────────────

-- Agregar columnas nuevas si no existen
ALTER TABLE ades_audit_log
    ADD COLUMN IF NOT EXISTS event_category   VARCHAR(50),
    -- AUTHENTICATION | AUTHORIZATION | IDENTITY_MANAGEMENT | ACADEMIC | SYSTEM
    ADD COLUMN IF NOT EXISTS event_risk_level VARCHAR(20),
    -- LOW | MEDIUM | HIGH | CRITICAL
    ADD COLUMN IF NOT EXISTS security_outcome VARCHAR(20),
    -- ALLOWED | DENIED | PARTIAL
    ADD COLUMN IF NOT EXISTS metadata         JSONB;

CREATE INDEX IF NOT EXISTS idx_audit_log_category ON ades_audit_log(event_category);
CREATE INDEX IF NOT EXISTS idx_audit_log_risk ON ades_audit_log(event_risk_level);
CREATE INDEX IF NOT EXISTS idx_audit_log_metadata ON ades_audit_log USING GIN (metadata);

COMMENT ON COLUMN ades_audit_log.event_category IS 'Categoría del evento: AUTHENTICATION, AUTHORIZATION, IDENTITY_MANAGEMENT, ACADEMIC, SYSTEM';
COMMENT ON COLUMN ades_audit_log.event_risk_level IS 'Nivel de riesgo del evento: LOW, MEDIUM, HIGH, CRITICAL';
COMMENT ON COLUMN ades_audit_log.metadata IS 'Datos adicionales del evento en JSONB (actores, recursos afectados, contexto)';

-- ─────────────────────────────────────────────────────────────────────────────
-- SEED: Privilegios base de ADES
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO ades_privilegios (codigo, nombre, descripcion, modulo) VALUES
    ('BOLETAS_GENERAR',          'Generar Boletas',             'Permite generar boletas en lote',                            'REPORTES'),
    ('KARDEX_APROBAR',           'Aprobar Kárdex',              'Permite aprobar el Kárdex oficial de preparatoria',          'REPORTES'),
    ('EXTRAORDINARIO_PROGRAMAR', 'Programar Extraordinarios',   'Permite inscribir alumnos a exámenes extraordinarios',       'EVALUACIONES'),
    ('CALIFICACIONES_EDITAR',    'Editar Calificaciones',       'Permite modificar calificaciones ya cerradas',               'CALIFICACIONES'),
    ('USUARIOS_ADMIN',           'Administrar Usuarios',        'Permite crear/editar/desactivar usuarios del sistema',       'ADMIN'),
    ('VARIABLES_EDITAR',         'Editar Variables del Sistema','Permite modificar variables de configuración',               'ADMIN'),
    ('IMPORTAR_DATOS',           'Importar Datos Masivos',      'Permite importar alumnos/profesores por CSV',                'ADMIN'),
    ('REPORTES_BI_VER',          'Ver BI / Analytics',         'Permite acceder a los dashboards de Superset',               'BI'),
    ('GRUPOS_ADMIN',             'Administrar Grupos',          'Permite crear/editar grupos y sobrecupos',                   'ACADEMICO'),
    ('CONDUCTA_GESTIONAR',       'Gestionar Conducta',          'Permite crear reportes disciplinarios y planes de mejora',   'OPERACION')
ON CONFLICT (codigo) DO NOTHING;

COMMIT;
```

### C2 — Actualizar el middleware de seguridad

```python
# En backend/app/core/security.py, actualizar AdesUser para incluir privilegios:

class AdesUser:
    """
    Contexto del usuario autenticado en ADES.
    Se carga una vez por request y contiene:
    - Datos básicos del usuario
    - Rol activo y nivel_acceso
    - Lista de privilegios (para permisos granulares)
    - Roles secundarios (multi-rol)
    """
    def __init__(self, usuario_row, rol_row, privilegios: list[str] = None, roles_ids: list[str] = None):
        self.id = usuario_row.id
        self.nombre_usuario = usuario_row.nombre_usuario
        self.email_institucional = usuario_row.email_institucional
        self.oidc_sub = usuario_row.oidc_sub
        self.rol_id = usuario_row.rol_id
        self.plantel_id = usuario_row.plantel_id
        self.nivel_educativo_id = usuario_row.nivel_educativo_id
        self.nivel_acceso = rol_row.nivel_acceso if rol_row else 5
        self.privilegios: list[str] = privilegios or []    # ['BOLETAS_GENERAR', 'KARDEX_APROBAR']
        self.todos_roles: list[str] = roles_ids or []      # UUIDs de todos los roles

    def tiene_privilegio(self, codigo: str) -> bool:
        """Verifica si el usuario tiene un privilegio específico."""
        return codigo in self.privilegios

    # Mantener compatibilidad con código existente
    @property
    def usuario_id(self):
        return self.id
```

---

## FASE 26-D: Notificaciones In-App del Sistema

### D1 — Migración de Base de Datos

Crear `db/migrations/024_notificaciones_sistema.sql`:

```sql
BEGIN;

CREATE TABLE IF NOT EXISTS ades_notificaciones_sistema (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    usuario_id   UUID NOT NULL REFERENCES ades_usuarios(id) ON DELETE CASCADE,
    titulo       VARCHAR(200) NOT NULL,
    mensaje      TEXT,
    tipo         VARCHAR(20)  NOT NULL DEFAULT 'INFO',  -- INFO | WARN | ERROR | SUCCESS
    leido        BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- No necesita AuditMixin completo, son registros del sistema
    CONSTRAINT ck_tipo_notif CHECK (tipo IN ('INFO', 'WARN', 'ERROR', 'SUCCESS'))
);

CREATE INDEX IF NOT EXISTS idx_notif_sistema_usuario ON ades_notificaciones_sistema(usuario_id, leido);
CREATE INDEX IF NOT EXISTS idx_notif_sistema_fecha ON ades_notificaciones_sistema(fecha_creacion DESC);

COMMENT ON TABLE ades_notificaciones_sistema IS
    'Notificaciones in-app generadas automáticamente por el sistema: asignación de roles, cambios de grupo, alertas académicas, etc. Complementa el push via ntfy.';

COMMIT;
```

### D2 — Endpoint y función de utilidad

```python
# backend/app/api/v1/notificaciones.py

from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update
from app.models.notificaciones import NotificacionSistema  # Claude Code debe crear
from app.core.db import get_db
from app.core.security import get_ades_user, AdesUser
import uuid

router = APIRouter(prefix="/notificaciones", tags=["notificaciones"])


@router.get("/mis-notificaciones")
async def mis_notificaciones(
    solo_no_leidas: bool = False,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    q = select(NotificacionSistema).where(
        NotificacionSistema.usuario_id == user.id
    ).order_by(NotificacionSistema.fecha_creacion.desc()).limit(50)

    if solo_no_leidas:
        q = q.where(NotificacionSistema.leido == False)

    result = await db.execute(q)
    return result.scalars().all()


@router.patch("/mis-notificaciones/{notif_id}/leer")
async def marcar_leida(
    notif_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    await db.execute(
        update(NotificacionSistema)
        .where(
            NotificacionSistema.id == notif_id,
            NotificacionSistema.usuario_id == user.id
        )
        .values(leido=True)
    )
    await db.commit()
    return {"ok": True}


@router.patch("/mis-notificaciones/leer-todas")
async def marcar_todas_leidas(
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    await db.execute(
        update(NotificacionSistema)
        .where(
            NotificacionSistema.usuario_id == user.id,
            NotificacionSistema.leido == False
        )
        .values(leido=True)
    )
    await db.commit()
    return {"ok": True}


# ── Función de utilidad para crear notificaciones desde cualquier parte del backend ──

async def crear_notificacion(
    db: AsyncSession,
    usuario_id: uuid.UUID,
    titulo: str,
    mensaje: str,
    tipo: str = 'INFO',  # INFO | WARN | ERROR | SUCCESS
):
    """
    Crea una notificación in-app para un usuario.
    Uso: await crear_notificacion(db, user.id, "Rol Asignado", "Se te asignó el rol Docente", "INFO")
    """
    notif = NotificacionSistema(
        usuario_id=usuario_id,
        titulo=titulo,
        mensaje=mensaje,
        tipo=tipo,
    )
    db.add(notif)
    # No hacer commit aquí, el caller maneja la transacción
    return notif
```

---

## FASE 26-E: SEPOMEX + INEGI + Componente Geográfico

### E1 — Verificar pipeline SEPOMEX existente

```bash
# Claude Code debe ejecutar esto para verificar qué existe:
docker compose exec postgres psql -U ades_admin -d ades -c "\dn" | grep sepomex
docker compose exec postgres psql -U ades_admin -d ades -c "\dt sepomex.*"
```

### E2 — Endpoints sobre el schema SEPOMEX existente

```python
# backend/app/api/v1/geo.py
"""
Endpoints geográficos sobre el catálogo SEPOMEX (ya cargado en schema sepomex.*).
"""
from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text
from app.core.db import get_db

router = APIRouter(prefix="/geo", tags=["geograficos"])


@router.get("/estados")
async def listar_estados(db: AsyncSession = Depends(get_db)):
    """Lista estados vigentes de México desde el catálogo SEPOMEX."""
    result = await db.execute(text("""
        SELECT llestado AS id, cvestado AS clave, dsestado AS nombre
        FROM sepomex.ctestados
        WHERE scd_vigente = TRUE
        ORDER BY dsestado
    """))
    return [dict(r) for r in result.mappings()]


@router.get("/municipios")
async def listar_municipios(
    estado_id: int = Query(..., description="ID del estado"),
    db: AsyncSession = Depends(get_db)
):
    """Lista municipios de un estado."""
    result = await db.execute(text("""
        SELECT llmunicipio AS id, cvmunicipio AS clave, dsmunicipo AS nombre
        FROM sepomex.ctmunicipios
        WHERE llestado = :estado_id AND scd_vigente = TRUE
        ORDER BY dsmunicipo
    """), {"estado_id": estado_id})
    return [dict(r) for r in result.mappings()]


@router.get("/colonias")
async def buscar_colonias(
    cp: str = Query(None, description="Código postal de 5 dígitos"),
    municipio_id: int = Query(None, description="ID de municipio"),
    db: AsyncSession = Depends(get_db)
):
    """Busca colonias/asentamientos por código postal o municipio."""
    if cp:
        result = await db.execute(text("""
            SELECT
                a.llasentamiento AS id,
                a.dsasentamiento AS colonia,
                cp.cvcodigopostal AS codigo_postal,
                m.dsmunicipo AS municipio,
                e.dsestado AS estado
            FROM sepomex.ctasentamientos a
            JOIN sepomex.ctcodigospostales cp ON cp.llcodigopostal = a.llcodigopostal
            JOIN sepomex.ctmunicipios m ON m.llmunicipio = a.llmunicipio
            JOIN sepomex.ctestados e ON e.llestado = m.llestado
            WHERE cp.cvcodigopostal = :cp
              AND a.scd_vigente = TRUE
            ORDER BY a.dsasentamiento
        """), {"cp": cp})
    elif municipio_id:
        result = await db.execute(text("""
            SELECT a.llasentamiento AS id, a.dsasentamiento AS colonia,
                   cp.cvcodigopostal AS codigo_postal
            FROM sepomex.ctasentamientos a
            JOIN sepomex.ctcodigospostales cp ON cp.llcodigopostal = a.llcodigopostal
            WHERE a.llmunicipio = :municipio_id AND a.scd_vigente = TRUE
            ORDER BY a.dsasentamiento
        """), {"municipio_id": municipio_id})
    else:
        return []

    return [dict(r) for r in result.mappings()]


@router.get("/buscar-cp/{cp}")
async def buscar_por_cp(cp: str, db: AsyncSession = Depends(get_db)):
    """Devuelve municipio + estado + colonias para un CP (útil para autocompletar)."""
    result = await db.execute(text("""
        SELECT
            e.dsestado AS estado,
            e.llestado AS estado_id,
            m.dsmunicipo AS municipio,
            m.llmunicipio AS municipio_id,
            cp.cvcodigopostal AS cp,
            json_agg(json_build_object('id', a.llasentamiento, 'colonia', a.dsasentamiento)
                     ORDER BY a.dsasentamiento) AS colonias
        FROM sepomex.ctasentamientos a
        JOIN sepomex.ctcodigospostales cp ON cp.llcodigopostal = a.llcodigopostal
        JOIN sepomex.ctmunicipios m ON m.llmunicipio = a.llmunicipio
        JOIN sepomex.ctestados e ON e.llestado = m.llestado
        WHERE cp.cvcodigopostal = :cp AND a.scd_vigente = TRUE
        GROUP BY e.dsestado, e.llestado, m.dsmunicipo, m.llmunicipio, cp.cvcodigopostal
    """), {"cp": cp})
    row = result.mappings().first()
    return dict(row) if row else None
```

### E3 — Componente Angular: Selector Geográfico Reutilizable

```typescript
// frontend/src/app/shared/components/selector-geo/selector-geo.component.ts
/**
 * Componente reutilizable de selección geográfica encadenada:
 * Estado → Municipio → Colonia (opcional búsqueda por CP)
 *
 * Uso:
 * <app-selector-geo [(estado)]="form.estado_id" [(municipio)]="form.municipio_id"
 *                  [(colonia)]="form.colonia" [(cp)]="form.cp" />
 */
import { Component, OnInit, inject, signal, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { ApiService } from '../../../core/services/api.service';

@Component({
  selector: 'app-selector-geo',
  standalone: true,
  imports: [CommonModule, FormsModule, SelectModule, InputTextModule, ButtonModule],
  template: `
    <div class="geo-grid">
      <!-- Búsqueda rápida por CP -->
      <div class="geo-cp">
        <label>Código Postal</label>
        <div style="display:flex;gap:.5rem">
          <input pInputText [(ngModel)]="cpBusqueda" placeholder="Ej: 50100" maxlength="5"
                 style="width:100px" (keyup.enter)="buscarPorCP()" />
          <p-button icon="pi pi-search" [text]="true" (onClick)="buscarPorCP()"
                    pTooltip="Autocompletar por CP" />
        </div>
      </div>

      <!-- Estado -->
      <div>
        <label>Estado *</label>
        <p-select
          [options]="estados()"
          [(ngModel)]="estadoSeleccionado"
          optionLabel="nombre" optionValue="id"
          placeholder="Seleccionar estado"
          (onChange)="onEstadoChange()"
          [filter]="true" filterBy="nombre"
          styleClass="w-full" />
      </div>

      <!-- Municipio -->
      <div>
        <label>Municipio *</label>
        <p-select
          [options]="municipios()"
          [(ngModel)]="municipioSeleccionado"
          optionLabel="nombre" optionValue="id"
          placeholder="Seleccionar municipio"
          [disabled]="!estadoSeleccionado"
          (onChange)="onMunicipioChange()"
          [filter]="true" filterBy="nombre"
          styleClass="w-full" />
      </div>

      <!-- Colonia -->
      <div>
        <label>Colonia</label>
        <p-select
          [options]="colonias()"
          [(ngModel)]="coloniaSeleccionada"
          optionLabel="colonia" optionValue="id"
          placeholder="Seleccionar colonia"
          [disabled]="!municipioSeleccionado"
          (onChange)="onColoniaChange()"
          [filter]="true" filterBy="colonia"
          styleClass="w-full" />
      </div>
    </div>
  `,
  styles: [`
    .geo-grid { display: grid; grid-template-columns: auto 1fr 1fr 1fr; gap: .75rem; align-items: end; }
    .geo-cp { display: flex; flex-direction: column; gap: .25rem; }
    label { font-size: .82rem; color: var(--text-color-secondary); display: block; margin-bottom: .2rem; }
  `]
})
export class SelectorGeoComponent implements OnInit {
  private readonly api = inject(ApiService);

  @Input() estado: number | null = null;
  @Input() municipio: number | null = null;
  @Input() colonia: string | null = null;
  @Input() cp: string | null = null;

  @Output() estadoChange   = new EventEmitter<number | null>();
  @Output() municipioChange = new EventEmitter<number | null>();
  @Output() coloniaChange  = new EventEmitter<string | null>();
  @Output() cpChange       = new EventEmitter<string | null>();

  estados    = signal<any[]>([]);
  municipios = signal<any[]>([]);
  colonias   = signal<any[]>([]);

  estadoSeleccionado: number | null = null;
  municipioSeleccionado: number | null = null;
  coloniaSeleccionada: string | null = null;
  cpBusqueda: string = '';

  ngOnInit(): void {
    this.cargarEstados();
    if (this.estado) {
      this.estadoSeleccionado = this.estado;
      this.cargarMunicipios(this.estado);
    }
    if (this.municipio) {
      this.municipioSeleccionado = this.municipio;
      this.cargarColonias(this.municipio);
    }
    if (this.cp) this.cpBusqueda = this.cp;
  }

  cargarEstados(): void {
    this.api.get<any[]>('/geo/estados').subscribe(e => this.estados.set(e));
  }

  onEstadoChange(): void {
    this.municipioSeleccionado = null;
    this.coloniaSeleccionada = null;
    this.municipios.set([]);
    this.colonias.set([]);
    this.estadoChange.emit(this.estadoSeleccionado);
    if (this.estadoSeleccionado) this.cargarMunicipios(this.estadoSeleccionado);
  }

  cargarMunicipios(estadoId: number): void {
    this.api.get<any[]>('/geo/municipios', { estado_id: estadoId })
      .subscribe(m => this.municipios.set(m));
  }

  onMunicipioChange(): void {
    this.coloniaSeleccionada = null;
    this.colonias.set([]);
    this.municipioChange.emit(this.municipioSeleccionado);
    if (this.municipioSeleccionado) this.cargarColonias(this.municipioSeleccionado);
  }

  cargarColonias(municipioId: number): void {
    this.api.get<any[]>('/geo/colonias', { municipio_id: municipioId })
      .subscribe(c => this.colonias.set(c));
  }

  onColoniaChange(): void {
    this.coloniaChange.emit(this.coloniaSeleccionada);
  }

  buscarPorCP(): void {
    if (!this.cpBusqueda || this.cpBusqueda.length !== 5) return;
    this.api.get<any>(`/geo/buscar-cp/${this.cpBusqueda}`).subscribe({
      next: data => {
        if (!data) return;
        this.cpChange.emit(this.cpBusqueda);
        // Autocompletar estado
        this.estadoSeleccionado = data.estado_id;
        this.estadoChange.emit(data.estado_id);
        // Cargar municipios y autocompletar
        this.cargarMunicipios(data.estado_id);
        setTimeout(() => {
          this.municipioSeleccionado = data.municipio_id;
          this.municipioChange.emit(data.municipio_id);
          // Colonias del CP
          this.colonias.set(data.colonias || []);
        }, 200);
      }
    });
  }
}
```

---

## CHECKLIST DE IMPLEMENTACIÓN PARA CLAUDE CODE

### Orden de ejecución obligatorio:

```
□ 1. Leer CLAUDE.md, .agent/CONTEXT.md, .agent/STATE.md
□ 2. Verificar estado del proyecto: git status, docker compose ps
□ 3. Identificar número de la última migración aplicada
□ 4. Crear spec/modules/fase-26-admin-starter/specification.md (documentar antes de codificar)

□ FASE 26-A (Variables + Catálogos):
  □ 5.  Crear y aplicar db/migrations/021_variables_catalogos.sql
  □ 6.  Verificar migración: SELECT COUNT(*) FROM ades_variables_sistema;
  □ 7.  Crear backend/app/models/sistema.py (Catalogo, CatalogoItem, VariableSistema)
  □ 8.  Crear backend/app/schemas/sistema.py
  □ 9.  Crear backend/app/api/v1/catalogos.py
  □ 10. Registrar router en main.py
  □ 11. Agregar Tab "Variables del Sistema" en admin.component.ts con inputs dinámicos
  □ 12. Agregar Tab "Catálogos" en admin.component.ts con master-detail
  □ 13. Compilar frontend: cd frontend && npm run build
  □ 14. Reiniciar API: docker compose restart ades-api
  □ 15. Test: GET /api/v1/config/variables (debe listar las 28+ variables)
  □ 16. Test: GET /api/v1/catalogos (debe listar los 10 catálogos con items)

□ FASE 26-B (Menús Dinámicos):
  □ 17. Crear y aplicar db/migrations/022_menus_dinamicos.sql
  □ 18. Crear backend/app/models/menus.py (Menu, MenuRol)
  □ 19. Crear backend/app/api/v1/menus.py
  □ 20. Registrar router en main.py
  □ 21. Modificar el componente de sidebar en Angular para que consuma /api/v1/menus/mi-menu
       en lugar de tener el menú hardcodeado
  □ 22. Agregar Tab "Menús" en admin.component.ts con árbol editable
  □ 23. Test: GET /api/v1/menus/mi-menu (debe devolver árbol de navegación)

□ FASE 26-C (Privilegios + Multi-rol + Trazabilidad):
  □ 24. Crear y aplicar db/migrations/023_privilegios_multirol_trazabilidad.sql
  □ 25. Verificar backfill: SELECT COUNT(*) FROM ades_usuario_roles;
  □ 26. Actualizar AdesUser en backend/app/core/security.py (agregar privilegios y todos_roles)
  □ 27. Actualizar get_ades_user() para cargar privilegios del usuario desde BD
  □ 28. Crear backend/app/models/privilegios.py (Privilegio, RolPrivilegio, UsuarioRol)
  □ 29. Agregar Tab "Privilegios" en admin.component.ts
  □ 30. Modificar Tab "Roles" en admin.component.ts para asignar privilegios y menús
  □ 31. Modificar Tab "Usuarios" en admin.component.ts para mostrar todos los roles
  □ 32. Actualizar backend/app/core/audit.py para registrar event_category y risk_level
  □ 33. Test: Verificar que un usuario con privilegio BOLETAS_GENERAR puede generar boletas

□ FASE 26-D (Notificaciones In-App):
  □ 34. Crear y aplicar db/migrations/024_notificaciones_sistema.sql
  □ 35. Crear backend/app/models/notificaciones.py (NotificacionSistema)
  □ 36. Crear backend/app/api/v1/notificaciones.py
  □ 37. Registrar router en main.py
  □ 38. Integrar crear_notificacion() en los endpoints que asignan/revocan roles
  □ 39. Agregar badge de notificaciones en el header del frontend (contador no leídas)
  □ 40. Crear panel lateral de notificaciones (botón campana → slide-panel con listado)
  □ 41. Test: Asignar rol a usuario → debe aparecer notificación in-app

□ FASE 26-E (SEPOMEX + Geo Component):
  □ 42. Verificar estado del schema sepomex: \dt sepomex.*
  □ 43. Si está vacío: ejecutar pipeline SEPOMEX (scripts 01-05 del starter)
  □ 44. Crear backend/app/api/v1/geo.py
  □ 45. Registrar router en main.py
  □ 46. Crear frontend/src/app/shared/components/selector-geo/selector-geo.component.ts
  □ 47. Integrar <app-selector-geo> en: alumnos (domicilio), profesores (domicilio),
         contactos-familiares, planteles
  □ 48. Agregar Tab "Geográficos" en admin.component.ts con estado del catálogo SEPOMEX
  □ 49. Test: GET /api/v1/geo/buscar-cp/50100 (debe devolver municipio + colonias)

□ CIERRE:
  □ 50. Actualizar spec/modules/fase-26-admin-starter/specification.md con lo implementado
  □ 51. Actualizar .agent/STATE.md
  □ 52. Actualizar openspec.yaml (agregar módulo fase-26)
  □ 53. Ejecutar compilación completa: cd frontend && npm run build (cero errores TypeScript)
  □ 54. Commit: "feat: FASE 26 — integración starter en ADES (variables, catálogos, menús, privilegios, multi-rol, notificaciones, geo)"
```

---

## PRINCIPIOS QUE CLAUDE CODE DEBE SEGUIR

1. **No romper nada existente.** Cada migración usa `IF NOT EXISTS` y `ON CONFLICT DO NOTHING`. Los campos nuevos en tablas existentes usan `ADD COLUMN IF NOT EXISTS`.

2. **AuditMixin en todos los modelos.** Toda tabla nueva hereda: `ref`, `is_active`, `fccreacion`, `fcmodificacion`, `usuario_creacion`, `usuario_modificacion`, `row_version`.

3. **Optimistic locking obligatorio.** Todos los endpoints PATCH/PUT reciben `row_version` y llaman a `check_row_version()` antes del commit.

4. **PKs = UUID v7.** `server_default="uuidv7()"` en todos los modelos nuevos.

5. **Prefijo `ades_` en todas las tablas.** Sin excepción.

6. **Passwords nunca en respuestas API.** Cualquier campo `encriptado=True` o `tipo_valor='PASSWORD'` debe devolverse como `null` en el JSON.

7. **Interactive Grid para todas las tablas del Admin.** Los tabs nuevos usan `<app-interactive-grid>` siguiendo el patrón de `spec/modules/fase-24-interactive-grid/specification.md`.

8. **Documentar en spec/ antes de codificar.** Crear `spec/modules/fase-26-admin-starter/specification.md` con un resumen de lo que se va a implementar.

9. **Verificar el build antes de commit.** `cd frontend && npm run build` debe completar con 0 errores TypeScript.

10. **SEPOMEX con schema propio.** Los catálogos geográficos viven en `sepomex.*`, los endpoints en `/api/v1/geo/` solo leen de ese schema.

---

## REFERENCIAS TÉCNICAS

- **Spec de referencia:** `spec/standards/api-design.md`
- **Interactive Grid:** `spec/modules/fase-24-interactive-grid/specification.md`
- **Casos de Uso relacionados:** `spec/modules/casos-de-uso/specification.md` § CU-01 (ciclos), CU-12 (BI)
- **Optimistic Locking:** `OPTIMISTIC_LOCKING_IMPLEMENTATION.md`
- **SEPOMEX pipeline:** Scripts `01_create_sepomex_schema_and_tables.sql` al `05_promote_to_public.sql`
- **Starter de referencia:** `export_202606091517.sql` (esquema analizado)
