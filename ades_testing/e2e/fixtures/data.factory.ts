import { faker } from '@faker-js/faker';

/**
 * DataFactory — Generate consistent test data
 * Creates users, expedientes, groups, etc. with realistic data
 */
export class DataFactory {
  private static counter = 0;

  /**
   * Reset counter (call between test runs)
   */
  static reset(): void {
    this.counter = 0;
  }

  /**
   * Generate unique ID
   */
  private static generateId(): string {
    return `test-${Date.now()}-${++this.counter}`;
  }

  /**
   * Create an admin user
   */
  static createAdminUser() {
    return {
      email: `admin-${this.generateId()}@ades.test`,
      password: 'AdminTest@123456',
      firstName: 'Admin',
      lastName: 'Usuario',
      rol: 'ADMIN',
      nivel_acceso: 0, // Highest level
      permisos: ['*'], // All permissions
    };
  }

  /**
   * Create a teacher user
   */
  static createTeacherUser() {
    return {
      email: `teacher-${this.generateId()}@ades.test`,
      password: 'TeacherTest@123456',
      firstName: faker.name.firstName(),
      lastName: faker.name.lastName(),
      rol: 'TEACHER',
      nivel_acceso: 2,
      permisos: ['CALIFICACIONES', 'PLANEACION', 'ASISTENCIA'],
    };
  }

  /**
   * Create a parent user
   */
  static createParentUser() {
    return {
      email: `parent-${this.generateId()}@ades.test`,
      password: 'ParentTest@123456',
      firstName: faker.name.firstName(),
      lastName: faker.name.lastName(),
      rol: 'PARENT',
      nivel_acceso: 4, // Limited access
      permisos: ['CALIFICACIONES_HIJO', 'ASISTENCIA_HIJO'],
    };
  }

  /**
   * Create a student user
   */
  static createStudentUser() {
    return {
      email: `student-${this.generateId()}@ades.test`,
      password: 'StudentTest@123456',
      firstName: faker.name.firstName(),
      lastName: faker.name.lastName(),
      rol: 'STUDENT',
      nivel_acceso: 5, // Minimal access
      permisos: ['MIS_CALIFICACIONES', 'MI_HORARIO'],
    };
  }

  /**
   * Create an expediente (student file)
   */
  static createExpediente() {
    return {
      id: this.generateId(),
      alumno_id: 'test-alumno-' + this.generateId(),
      descripcion: `Expediente de ${faker.name.firstName()} - ${faker.date.past().toISOString()}`,
      estado: 'ACTIVO',
      archivo: null, // Will be uploaded in test
      notas: faker.lorem.paragraph(2),
      fecha_creacion: new Date().toISOString(),
    };
  }

  /**
   * Create a group/class
   */
  static createGrupo() {
    const grades = ['1°', '2°', '3°', '4°', '5°', '6°'];
    const sections = ['A', 'B', 'C', 'D'];

    return {
      id: this.generateId(),
      nombre: `${grades[Math.floor(Math.random() * grades.length)]} ${sections[Math.floor(Math.random() * sections.length)]}`,
      grado_id: 'test-grado-' + this.generateId(),
      profesor_titular_id: 'test-profesor-' + this.generateId(),
      ciclo_escolar_id: '2025-2026',
      capacidad: 30,
      estado: 'ACTIVO',
    };
  }

  /**
   * Create a calificación (grade)
   */
  static createCalificacion() {
    return {
      id: this.generateId(),
      alumno_id: 'test-alumno-' + this.generateId(),
      materia_id: 'test-materia-' + this.generateId(),
      periodo: 'P1',
      calificacion: Math.floor(Math.random() * 5) + 6, // 6-10
      estado: 'CALIFICADO',
      fecha_calificacion: new Date().toISOString(),
    };
  }

  /**
   * Create a tarea (assignment)
   */
  static createTarea() {
    return {
      id: this.generateId(),
      titulo: `Tarea: ${faker.lorem.words(3)}`,
      descripcion: faker.lorem.paragraph(2),
      fecha_entrega: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(), // 1 week from now
      materia_id: 'test-materia-' + this.generateId(),
      grupo_id: 'test-grupo-' + this.generateId(),
      estado: 'ACTIVA',
    };
  }

  /**
   * Create login credentials (admin)
   */
  static getAdminCredentials() {
    return {
      email: 'admin@ades.test',
      password: 'Admin@123456', // Must exist in test database
    };
  }

  /**
   * Create login credentials (teacher)
   */
  static getTeacherCredentials() {
    return {
      email: 'teacher@ades.test',
      password: 'Teacher@123456', // Must exist in test database
    };
  }

  /**
   * Create login credentials (parent)
   */
  static getParentCredentials() {
    return {
      email: 'parent@ades.test',
      password: 'Parent@123456', // Must exist in test database
    };
  }

  /**
   * Create a random email
   */
  static randomEmail(): string {
    return `test-${this.generateId()}@ades.test`;
  }

  /**
   * Create a random password (valid)
   */
  static randomPassword(): string {
    return `Test@${Math.random().toString(36).substring(7)}`;
  }

  /**
   * Create test file content
   */
  static createTestFile() {
    return {
      name: `test-${this.generateId()}.pdf`,
      content: Buffer.from('PDF test content'),
      mimeType: 'application/pdf',
    };
  }
}
