export interface TestUser {
  email: string;
  password: string;
  rol: string;
  nivelAcceso: number;
  plantelNombre?: string;
}

export const USERS: Record<string, TestUser> = {
  ADMIN_GLOBAL: {
    email: 'admin.global@test.ades',
    password: 'TestAdes2026!',
    rol: 'ADMIN_GLOBAL',
    nivelAcceso: 0,
  },
  ADMIN_PLANTEL: {
    email: 'admin.metepec@test.ades',
    password: 'TestAdes2026!',
    rol: 'ADMIN_PLANTEL',
    nivelAcceso: 1,
    plantelNombre: 'Metepec',
  },
  DIRECTOR: {
    email: 'director.metepec@test.ades',
    password: 'TestAdes2026!',
    rol: 'DIRECTOR',
    nivelAcceso: 2,
    plantelNombre: 'Metepec',
  },
  COORDINADOR: {
    email: 'coord.academico@test.ades',
    password: 'TestAdes2026!',
    rol: 'COORDINADOR_ACADEMICO',
    nivelAcceso: 3,
    plantelNombre: 'Metepec',
  },
  DOCENTE: {
    email: 'docente.primaria@test.ades',
    password: 'TestAdes2026!',
    rol: 'DOCENTE',
    nivelAcceso: 4,
    plantelNombre: 'Metepec',
  },
  PADRE: {
    email: 'padre.alumno@test.ades',
    password: 'TestAdes2026!',
    rol: 'PADRE_FAMILIA',
    nivelAcceso: 5,
  },
  ALUMNO: {
    email: 'alumno.sec@test.ades',
    password: 'TestAdes2026!',
    rol: 'ALUMNO',
    nivelAcceso: 5,
  },
};

export const BFF_BASE = process.env['BFF_URL'] ?? 'http://localhost:8080';
export const API_BASE = process.env['API_URL'] ?? 'http://localhost:8000/api/v1';
