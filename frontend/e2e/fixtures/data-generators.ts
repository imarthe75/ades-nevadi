import { faker } from '@faker-js/faker/locale/es_MX';

faker.seed(20260616);

// ── CURP generation ─────────────────────────────────────────────────────────

const CONSONANTES = 'BCDFGHJKLMNPQRSTVWXYZ';
const VOCALES     = 'AEIOU';

function letra(set: string) {
  return set[Math.floor(Math.random() * set.length)];
}

/**
 * Genera una CURP de 18 caracteres que cumple el patrón RENAPO real
 * (`^[A-Z]{4}\d{6}[HMX][A-Z]{5}[A-Z\d]\d$`, ver `ApexValidators.isCURP()`).
 * <p>
 * Bug corregido (2026-07-16): la versión anterior concatenaba 4+1+1=6 letras
 * antes de la fecha (`apellidoPat` ya era un bloque de 4 caracteres por sí
 * solo, en vez de solo letra+vocal), produciendo un string de 19 caracteres
 * que NUNCA pasaba la regex — el botón "Guardar" del alta rápida de alumno
 * quedaba deshabilitado para siempre y los tests que crean un alumno
 * (ALU-02, AUD-01, fuzz de alumnos) hacían timeout de 30s esperando un click
 * que nunca podía completarse. Estructura real: 4 letras (1ª letra apellido
 * paterno + 1ª vocal interna + 1ª letra apellido materno + 1ª letra nombre)
 * + 6 dígitos (fecha) + sexo + 2 letras entidad + 3 consonantes internas +
 * diferenciador alfanumérico + dígito verificador = 18.
 */
export function curpValido(): string {
  const letra1 = letra(CONSONANTES);
  const vocal  = letra(VOCALES);
  const letra3 = letra(CONSONANTES);
  const letra4 = letra(CONSONANTES);
  const año  = String(faker.number.int({ min: 2000, max: 2012 })).slice(2);
  const mes  = String(faker.number.int({ min: 1, max: 12 })).padStart(2, '0');
  const dia  = String(faker.number.int({ min: 1, max: 28 })).padStart(2, '0');
  const sexo = faker.helpers.arrayElement(['H', 'M']);
  const edo  = faker.helpers.arrayElement(['MC','DF','JL','PU','GR','VZ']);
  const cons1 = letra(CONSONANTES);
  const cons2 = letra(CONSONANTES);
  const cons3 = letra(CONSONANTES);
  const diferenciador = letra(CONSONANTES + VOCALES + '0123456789');
  const dv = faker.string.numeric(1);
  return `${letra1}${vocal}${letra3}${letra4}${año}${mes}${dia}${sexo}${edo}${cons1}${cons2}${cons3}${diferenciador}${dv}`;
}

export function curpInvalido(): string {
  return faker.helpers.arrayElement([
    '',
    '123',
    'XXXXXXXXXXXXXXXXXXX',         // 19 chars (too long)
    'abc-123!@#$%^&*()',
    ' ',
    'AAAA000000AAAAAA0'.repeat(2), // 34 chars
    '😅😅😅😅😅😅😅😅😅😅',
  ]);
}

export function rfcValido(): string {
  const letras = faker.string.alpha({ length: 4, casing: 'upper' });
  const año    = String(faker.number.int({ min: 70, max: 99 }));
  const mes    = String(faker.number.int({ min: 1, max: 12 })).padStart(2, '0');
  const dia    = String(faker.number.int({ min: 1, max: 28 })).padStart(2, '0');
  const homo   = faker.string.alphanumeric(3).toUpperCase();
  return `${letras}${año}${mes}${dia}${homo}`;
}

// ── Nombres ──────────────────────────────────────────────────────────────────

export function nombreCompleto() {
  return {
    nombre:           faker.person.firstName(),
    apellido_paterno: faker.person.lastName(),
    apellido_materno: faker.person.lastName(),
  };
}

// ── Strings de frontera ──────────────────────────────────────────────────────

export const EDGE_STRINGS = {
  EMPTY:         '',
  SPACE_ONLY:    '   ',
  LONG_1000:     faker.string.alpha(1000),
  LONG_10K:      faker.string.alpha(10_000),
  SQL_INJECTION: "'; DROP TABLE ades_estudiantes; --",
  XSS_BASIC:     '<script>alert(1)</script>',
  XSS_IMG:       '<img src=x onerror=alert(1)>',
  EMOJIS:        '😅😂🎉🔥💯🤡👻🦊🌈',
  EMOJIS_LONG:   '😅'.repeat(200),
  UNICODE_MIX:   '你好世界 مرحبا привет שלום',
  NULL_BYTE:     'hola\x00mundo',
  NEWLINES:      'línea1\nlínea2\r\nlínea3',
  TABS:          '\t\t\t',
  HTML_ENTITIES: '&lt;script&gt;alert&amp;',
  FORMULA_INJ:   '=CMD|"/C calc"!A0',         // CSV injection
  PATH_TRAV:     '../../../../etc/passwd',
  ZERO:          '0',
  NEGATIVE:      '-1',
  FLOAT_BIG:     '9999999.99',
  NAN:           'NaN',
  INFINITY:      'Infinity',
  BOOL_STRING:   'true',
  JSON_FRAG:     '{"key": "value"}',
};

// ── Calificaciones fuera de rango ────────────────────────────────────────────

export const CAL_INVALIDAS = {
  SEP:    [-1, 10.1, 11, 100, NaN, Infinity, ''],
  UAEMEX: [-1, 100.1, 101, 1000, NaN, Infinity, ''],
};

// ── Emails ───────────────────────────────────────────────────────────────────

export function emailValido(): string {
  return faker.internet.email({ provider: 'nevadi.edu.mx' });
}

export const EMAILS_INVALIDOS = [
  '',
  'noatsign',
  '@sin-usuario.com',
  'sin-dominio@',
  'espacio en@medio.com',
  'a@b',
  '<script>@hack.com',
  'a'.repeat(200) + '@nevadi.edu.mx',
];

// ── Fechas ───────────────────────────────────────────────────────────────────

export function fechaFutura(): string {
  const d = new Date();
  d.setDate(d.getDate() + faker.number.int({ min: 1, max: 365 }));
  return d.toISOString().split('T')[0];
}

export function fechaPasada(): string {
  const d = new Date();
  d.setDate(d.getDate() - faker.number.int({ min: 1, max: 365 }));
  return d.toISOString().split('T')[0];
}

export function fechaHoy(): string {
  return new Date().toISOString().split('T')[0];
}

// ── Alumno completo ──────────────────────────────────────────────────────────

export function alumnoValido() {
  const { nombre, apellido_paterno, apellido_materno } = nombreCompleto();
  return {
    curp:             curpValido(),
    nombre,
    apellido_paterno,
    apellido_materno,
    fecha_nacimiento: faker.date
      .between({ from: '2005-01-01', to: '2014-12-31' })
      .toISOString()
      .split('T')[0],
    sexo: faker.helpers.arrayElement(['M', 'F']),
    email: emailValido(),
  };
}

// ── Ponderación ──────────────────────────────────────────────────────────────

export function ponderacionConSuma100() {
  return [
    { tipo_item: 'examen',      peso_porcentaje: 60 },
    { tipo_item: 'tarea',       peso_porcentaje: 25 },
    { tipo_item: 'participacion', peso_porcentaje: 15 },
  ];
}

export function ponderacionConSumaIncorrecta() {
  return [
    { tipo_item: 'examen', peso_porcentaje: 70 },
    { tipo_item: 'tarea',  peso_porcentaje: 20 },
  ]; // suma = 90, no 100
}

// ── Profesor ─────────────────────────────────────────────────────────────────

export function profesorValido() {
  const { nombre, apellido_paterno, apellido_materno } = nombreCompleto();
  return {
    rfc:              rfcValido(),
    numero_empleado:  String(faker.number.int({ min: 10000, max: 99999 })),
    nombre,
    apellido_paterno,
    apellido_materno,
    email:            emailValido(),
    telefono:         faker.phone.number({ style: 'national' }),
  };
}

// ── Sanción disciplinaria ────────────────────────────────────────────────────

export const TIPOS_SANCION = ['LLAMADA_ATENCION', 'SUSPENSION_1DIA', 'SUSPENSION_3DIAS', 'CONDICIONAL'] as const;

export function sancionValida() {
  return {
    tipo:        faker.helpers.arrayElement(TIPOS_SANCION),
    descripcion: faker.lorem.sentences(2),     // >20 chars garantizado
    fecha:       fechaHoy(),
  };
}

export function sancionSinDescripcion() {
  return {
    tipo:        TIPOS_SANCION[0],
    descripcion: '',
    fecha:       fechaHoy(),
  };
}

// ── Licencia RRHH ────────────────────────────────────────────────────────────

export const TIPOS_LICENCIA = ['MEDICA', 'PERSONAL', 'MATERNIDAD', 'PATERNIDAD'] as const;

export function licenciaValida() {
  const inicio = new Date();
  const fin    = new Date(inicio);
  fin.setDate(fin.getDate() + faker.number.int({ min: 1, max: 10 }));
  return {
    tipo:         faker.helpers.arrayElement(TIPOS_LICENCIA),
    fecha_inicio: inicio.toISOString().split('T')[0],
    fecha_fin:    fin.toISOString().split('T')[0],
    motivo:       faker.lorem.sentence(),
  };
}

export function licenciaFechasInvertidas() {
  const inicio = new Date();
  const fin    = new Date(inicio);
  fin.setDate(fin.getDate() - faker.number.int({ min: 1, max: 5 }));  // fin ANTES de inicio
  return {
    tipo:         TIPOS_LICENCIA[0],
    fecha_inicio: inicio.toISOString().split('T')[0],
    fecha_fin:    fin.toISOString().split('T')[0],
    motivo:       'Fechas invertidas para test',
  };
}

// ── Aspirante a admisión ─────────────────────────────────────────────────────

export const ESTADOS_ADMISION = ['PREINSCRITO', 'EN_PROCESO', 'ACEPTADO', 'RECHAZADO'] as const;

export function aspiranteValido() {
  const { nombre, apellido_paterno, apellido_materno } = nombreCompleto();
  return {
    curp:             curpValido(),
    nombre,
    apellido_paterno,
    apellido_materno,
    fecha_nacimiento: faker.date
      .between({ from: '2008-01-01', to: '2014-12-31' })
      .toISOString()
      .split('T')[0],
    email_tutor: emailValido(),
    nivel_educativo: faker.helpers.arrayElement(['PRIMARIA', 'SECUNDARIA', 'PREPARATORIA']),
  };
}

// ── Re-exportar faker con seed determinista ──────────────────────────────────

export { faker };
