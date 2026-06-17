import { faker } from '@faker-js/faker/locale/es_MX';

faker.seed(20260616);

// ── CURP generation ─────────────────────────────────────────────────────────

const CONSONANTES = 'BCDFGHJKLMNÑPQRSTVWXYZ';
const VOCALES     = 'AEIOU';

function letra(set: string) {
  return set[Math.floor(Math.random() * set.length)];
}

export function curpValido(): string {
  const apellidoPat = letra(CONSONANTES) + letra(VOCALES) + letra(CONSONANTES) + letra(CONSONANTES);
  const apellidoMat = letra(CONSONANTES);
  const nombre      = letra(CONSONANTES);
  const año  = String(faker.number.int({ min: 2000, max: 2012 })).slice(2);
  const mes  = String(faker.number.int({ min: 1, max: 12 })).padStart(2, '0');
  const dia  = String(faker.number.int({ min: 1, max: 28 })).padStart(2, '0');
  const sexo = faker.helpers.arrayElement(['H', 'M']);
  const edo  = faker.helpers.arrayElement(['MC','DF','JL','PU','GR','VZ']);
  const cod  = faker.string.alphanumeric(3).toUpperCase();
  const dv   = faker.string.numeric(1);
  return `${apellidoPat}${apellidoMat}${nombre}${año}${mes}${dia}${sexo}${edo}${cod}${dv}`;
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

// ── Re-exportar faker con seed determinista ──────────────────────────────────

export { faker };
