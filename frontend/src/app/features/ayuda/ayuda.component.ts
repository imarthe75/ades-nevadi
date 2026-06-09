/**
 * FASE 13 (expandida) — Manual de usuario ADES Instituto Nevadi.
 *
 * Documentación completa organizada en:
 *   Tab 1: Manual por Rol — guías específicas para cada perfil
 *   Tab 2: Módulos        — referencia completa de cada módulo
 *   Tab 3: Servicios      — infraestructura y servicios open-source
 *   Tab 4: Seguridad      — roles, RBAC, buenas prácticas
 *   Tab 5: FAQ            — preguntas frecuentes y solución de problemas
 */
import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AccordionModule } from 'primeng/accordion';
import { TabsModule } from 'primeng/tabs';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import { ContextService } from '../../core/services/context.service';

interface Paso { titulo: string; pasos: string[]; nota?: string; tip?: string; }
interface Modulo { nombre: string; ruta: string; icono: string; fase: number; descripcion: string; secciones: Paso[]; }
interface FAQ { pregunta: string; respuesta: string; categoria: string; }

const MODULOS: Modulo[] = [
  {
    nombre: 'Dashboard', ruta: '/dashboard', icono: 'pi-home', fase: 1,
    descripcion: 'Vista general personalizada según el rol. KPIs en tiempo real del instituto, plantel o escuela.',
    secciones: [
      { titulo: 'Indicadores principales',
        pasos: ['Total de alumnos activos en el contexto seleccionado', 'Profesores con grupos activos', 'Grupos activos en el ciclo vigente', 'Alertas académicas pendientes de atención'],
        tip: 'El contexto cambia automáticamente según los selectores Plantel / Escuela / Ciclo del topbar.' },
      { titulo: 'Cambiar el contexto de visualización',
        pasos: ['En la barra superior selecciona el Plantel deseado', 'Selecciona la Escuela (nivel educativo) o "Todo el Plantel"', 'Elige el Ciclo Escolar vigente', 'El dashboard y todos los módulos se filtran automáticamente'],
        nota: 'Administradores globales pueden seleccionar "Todo el Instituto" para ver datos agregados de los 3 planteles.' },
    ],
  },
  {
    nombre: 'Alumnos', ruta: '/alumnos', icono: 'pi-graduation-cap', fase: 1,
    descripcion: 'Gestión completa de la población estudiantil. Listado paginado, búsqueda, exportación y expediente completo.',
    secciones: [
      { titulo: 'Buscar y filtrar alumnos',
        pasos: ['Escribe en el campo de búsqueda: nombre, apellido o matrícula', 'La búsqueda aplica automáticamente con 350ms de espera', 'Los resultados se filtran según el contexto activo de Plantel/Escuela', 'Usa los selectores del topbar para cambiar el contexto'],
        tip: 'Para ver alumnos de todo el instituto, selecciona "Todo el Instituto" en el selector de plantel.' },
      { titulo: 'Ver expediente completo',
        pasos: ['Clic en el ícono de expediente (tarjeta) en la fila del alumno', 'Se abre un panel lateral con datos personales, médicos y escolares', 'Los campos editables muestran un ícono de lápiz si tienes permisos', 'Guarda cambios con el botón "Guardar" en el panel'],
        nota: 'El expediente incluye: CURP, matrícula, datos médicos, contactos familiares, historial de inscripciones y conducta.' },
      { titulo: 'Exportar listado',
        pasos: ['Botón "CSV" — descarga el listado visible en formato CSV', 'Botón "Excel" — descarga en formato XLSX con formato institucional', 'Solo se exportan los registros del contexto actual'] },
      { titulo: 'Importar alumnos desde CSV',
        pasos: ['Menú Administración → pestaña Usuarios → botón "Importar CSV"', 'Descarga la plantilla de ejemplo para conocer el formato', 'Completa el archivo y súbelo', 'Revisa el reporte de errores antes de confirmar'],
        nota: 'Campos obligatorios: nombre, apellido_paterno, curp, fecha_nacimiento, grupo_id.' },
    ],
  },
  {
    nombre: 'Profesores', ruta: '/profesores', icono: 'pi-user', fase: 1,
    descripcion: 'Plantilla docente con datos de empleo, especialidades y asignaciones.',
    secciones: [
      { titulo: 'Consultar plantilla docente',
        pasos: ['Navega a Profesores en el menú lateral', 'Busca por nombre, número de empleado, RFC o cédula profesional', 'Clic en el ícono de expediente para ver datos completos', 'El expediente incluye: datos personales, RFC, NSS, cédula, turno, especialidad y asignaciones actuales'] },
      { titulo: 'Asignar profesor a materia/grupo',
        pasos: ['Ve al módulo Grupos y abre el grupo deseado', 'En asignaciones docentes, clic en "Asignar profesor"', 'Selecciona la materia y el profesor disponible', 'El sistema valida que no haya conflicto de horario'],
        nota: 'En Primaria: un profesor titular por grupo. En Secundaria y Preparatoria: un docente por materia.' },
    ],
  },
  {
    nombre: 'Calificaciones', ruta: '/calificaciones', icono: 'pi-star', fase: 2,
    descripcion: 'Registro y consulta de calificaciones por periodo. Libreta digital con alertas automáticas a padres.',
    secciones: [
      { titulo: 'Registrar calificaciones (Docente)',
        pasos: ['Selecciona el grupo y la materia en los selectores', 'La libreta muestra todos los alumnos del grupo', 'Haz clic en la celda del alumno para ingresar la calificación', 'La calificación se guarda al perder el foco o presionar Enter', 'Las calificaciones reprobatorias (<6.0) envían notificación automática al padre'],
        tip: 'Usa Tab para moverte entre celdas.' },
      { titulo: 'Vista Gradebook (Docente) — spreadsheet completo',
        pasos: ['Módulo Gradebook → más completo que la libreta básica', 'Columnas: actividades por tipo (examen, tarea, participación)', 'Fila resumen con promedio calculado automáticamente', 'Ajustes manuales requieren campo de justificación'] },
      { titulo: 'Generar boleta individual',
        pasos: ['Módulo Reportes → pestaña "Reporte Individual"', 'Busca al alumno en el selector', 'Selecciona la plantilla de boleta y el periodo deseado', 'Clic en "Generar PDF" — descarga automática'],
        nota: 'Las plantillas se gestionan en la pestaña "Subir Plantilla".' },
      { titulo: 'Generar boletas de todo el grupo (FASE 21)',
        pasos: ['Módulo Reportes → pestaña "Boletas Grupo"', 'Selecciona el grupo y la plantilla BOLETA', 'Elige periodo y si añadir marca de agua', 'Clic en "Generar PDF del grupo" — puede tardar 1-3 minutos', 'Descarga automática: PDF fusionado o ZIP individual'],
        tip: 'Si Stirling-PDF no está disponible, se descarga un ZIP con PDFs individuales.' },
    ],
  },
  {
    nombre: 'Asistencias', ruta: '/asistencias', icono: 'pi-check-square', fase: 2,
    descripcion: 'Registro diario de asistencias por clase. Alertas automáticas al 85% de umbral.',
    secciones: [
      { titulo: 'Registrar asistencia de una clase',
        pasos: ['Selecciona el grupo y la clase del día', 'La lista muestra todos los alumnos con estado PRESENTE por defecto', 'Marca AUSENTE, TARDE o JUSTIFICADO según corresponda', 'Opcionalmente agrega observación por alumno', 'Clic en "Guardar asistencia" para confirmar'],
        tip: 'Si la asistencia acumulada cae debajo del 85%, el padre recibe notificación push automática.' },
      { titulo: 'Consultar reporte de asistencia',
        pasos: ['Sección reporte del grupo o del alumno individual', 'El reporte muestra: total de clases, presencias, ausencias, porcentaje', 'Los alumnos con <85% aparecen destacados en rojo', 'Puedes filtrar por rango de fechas'] },
    ],
  },
  {
    nombre: 'Tareas y Entregas', ruta: '/tareas', icono: 'pi-file', fase: 2,
    descripcion: 'Gestión de tareas por grupo/materia. Entrega digital por alumnos, calificación por docente.',
    secciones: [
      { titulo: 'Crear una tarea (Docente)',
        pasos: ['Ve al módulo Tareas → "Nueva tarea"', 'Completa: título, descripción, fecha de entrega, materia y grupo', 'Opcionalmente adjunta rúbrica de evaluación', 'Guarda — aparece inmediatamente en el portal del alumno'] },
      { titulo: 'Calificar entregas (Docente)',
        pasos: ['En la tarea, clic en "Ver entregas"', 'Lista de alumnos con estado: Entregada / Pendiente / Tarde', 'Clic en la entrega para ver el archivo', 'Ingresa calificación — se refleja automáticamente en la libreta'] },
      { titulo: 'Entregar tarea (Alumno)',
        pasos: ['En "Mi Progreso", busca la tarea pendiente', 'Clic en el botón de entrega', 'Sube el archivo (PDF, DOCX, imagen — máximo 10MB)', 'El sistema confirma la recepción con fecha y hora'],
        nota: 'Los archivos se guardan en MinIO (almacenamiento local del instituto).' },
    ],
  },
  {
    nombre: 'Planes y Programas de Estudio', ruta: '/planes-estudio', icono: 'pi-table', fase: 19,
    descripcion: 'Mapa curricular interactivo. CRUD de materias, asignación a grados, temario y estadísticas.',
    secciones: [
      { titulo: 'Ver el mapa curricular',
        pasos: ['Selecciona el ciclo escolar', 'Elige el nivel (Primaria / Secundaria / Preparatoria)', 'La tabla muestra materias en filas y grados en columnas', 'Verde = obligatoria, naranja = optativa', 'El número en la celda indica horas semanales asignadas'],
        tip: 'Planes vigentes: CBU 2024 UAEMEX (Preparatoria) y NEM 2022 SEP (Primaria y Secundaria).' },
      { titulo: 'Asignar materia a un grado (Admin/Coordinador)',
        pasos: ['Las celdas vacías muestran botón "+"', 'Clic en "+" → asigna con 4h por defecto', 'Clic en las horas → edición inline → Enter para guardar', 'Clic en "×" → quita la asignación'] },
      { titulo: 'Gestionar el temario',
        pasos: ['Pestaña "Temario" → selecciona Nivel, Materia y Grado', 'Se muestra el temario con numeración', '"Agregar tema" → número, nombre, descripción, horas estimadas', 'Los docentes pueden ver y editar el temario de sus materias'] },
    ],
  },
  {
    nombre: 'Generador de Reportes', ruta: '/reportes', icono: 'pi-file-pdf', fase: 18,
    descripcion: 'Generación de PDFs desde plantillas Word/Excel (Carbone). Boletas individuales, grupales, constancias.',
    secciones: [
      { titulo: 'Crear una plantilla (DOCX)',
        pasos: ['Diseña el documento en Word o LibreOffice Writer', 'Usa marcadores Carbone para datos dinámicos:', '  {d.alumno.nombre_completo} — nombre completo del alumno', '  {d.alumno.matricula} — matrícula del alumno', '  {d.ciclo} — ciclo escolar activo', '  {#d.calificaciones}{d.materia}: {d.calificacion}{/d.calificaciones} — loop de calificaciones', 'Guarda como .docx y súbela en Reportes → Subir Plantilla'],
        nota: 'Documentación completa de marcadores Carbone en https://carbone.io/documentation.html' },
      { titulo: 'Generar PDF individual',
        pasos: ['Pestaña "Reporte Individual"', 'Busca al alumno, selecciona plantilla, periodo y tipo de documento', '"Generar PDF" → descarga automática'] },
      { titulo: 'Generar PDF de todo el grupo (Stirling-PDF)',
        pasos: ['Pestaña "Boletas Grupo"', 'Selecciona grupo, plantilla BOLETA, periodo opcional y marca de agua', '"Generar PDF del grupo" → proceso tarda 1-3 minutos', 'Descarga: PDF fusionado (si Stirling-PDF activo) o ZIP individual'] },
    ],
  },
  {
    nombre: 'Asistente IA + Consulta de Datos', ruta: '/ia', icono: 'pi-sparkles', fase: 4,
    descripcion: 'Asistente pedagógico Claude AI y motor de consulta en lenguaje natural con NL→SQL automático.',
    secciones: [
      { titulo: 'Chat pedagógico',
        pasos: ['Escribe preguntas sobre pedagogía, regulaciones SEP/UAEMEX o datos académicos', 'El asistente responde con contexto real del sistema', 'Usa sugerencias rápidas para consultas frecuentes', 'El historial se mantiene durante la sesión'],
        tip: 'Ejemplos: "¿Criterios de acreditación UAEMEX?", "Genera rúbrica para exposición oral", "Estrategias para alumnos en riesgo"' },
      { titulo: 'Consulta en lenguaje natural (NL→SQL)',
        pasos: ['Pestaña "Consulta de datos"', 'Escribe tu pregunta en español natural', 'El sistema genera SQL automáticamente y lo ejecuta respetando tu nivel de acceso', 'Los resultados aparecen como tabla y resumen en texto', 'Expande "SQL generado" para ver la consulta ejecutada'],
        nota: 'Las consultas respetan el RBAC. Un docente solo ve sus grupos; un padre solo ve datos de sus hijos.',
        tip: 'Ejemplos: "¿Cuántos alumnos tienen <80% de asistencia?", "¿Cuáles son las 5 materias con mayor reprobación?"' },
    ],
  },
  {
    nombre: 'Comunicados', ruta: '/comunicados', icono: 'pi-megaphone', fase: 5,
    descripcion: 'Publicación de avisos institucionales con acuse digital y notificaciones push automáticas.',
    secciones: [
      { titulo: 'Publicar un comunicado',
        pasos: ['Clic en "Nuevo comunicado"', 'Completa: título, contenido, tipo (GENERAL/URGENTE/OFICIAL)', 'Define el alcance: todos / plantel / nivel / grupo', 'Activa "Requiere acuse" si necesitas confirmación de lectura', 'Al guardar se envían notificaciones push automáticamente a los destinatarios'],
        tip: 'Comunicados URGENTES se destacan visualmente y llegan con prioridad alta en ntfy.' },
      { titulo: 'Acusar recibo (Padre/Alumno)',
        pasos: ['Los sin leer aparecen con badge en la campanita del topbar', 'Abre el módulo Comunicados o la lista de notificaciones', 'Lee el comunicado completo', 'Si requiere acuse, clic en "Confirmar lectura"'] },
    ],
  },
  {
    nombre: 'Notificaciones Push', ruta: '/', icono: 'pi-bell', fase: 20,
    descripcion: 'Sistema de notificaciones en tiempo real vía ntfy. Sin Firebase, sin app propietaria.',
    secciones: [
      { titulo: 'Activar notificaciones en el teléfono',
        pasos: ['Instala la app ntfy (gratuita) en iOS o Android desde la tienda', 'Abre tu perfil en ADES y copia tu ID de usuario', 'En la app ntfy, agrega el servidor: https://notify.ades.setag.mx', 'Suscríbete al tema: ades_{tu-id-de-usuario}', 'Las notificaciones llegarán aunque ADES no esté abierto'],
        tip: 'La app ntfy es gratuita y open-source. Disponible en iOS App Store y Google Play.' },
      { titulo: 'Activar notificaciones en el navegador',
        pasos: ['Al iniciar sesión ADES, aparece una solicitud de permiso de notificaciones', 'Clic en "Permitir" en el popup del navegador', 'Las notificaciones se recibirán mientras tengas ADES abierto en una pestaña'],
        nota: 'Las notificaciones del navegador solo funcionan mientras ADES está abierto. Para notificaciones permanentes, usa la app móvil ntfy.' },
      { titulo: 'Tipos de alertas automáticas',
        pasos: ['Calificación reprobatoria (<6.0): notificación al padre con materia y periodo', 'Asistencia baja (<85%): notificación al padre con porcentaje e inasistencias', 'Nuevo comunicado: notificación a todos los destinatarios del alcance', 'Alertas académicas detectadas por el sistema IA'],
      },
    ],
  },
  {
    nombre: 'Monitor del Sistema', ruta: '/monitor', icono: 'pi-heart-fill', fase: 22,
    descripcion: 'Panel de monitoreo en tiempo real. Estado de servicios, dashboards Grafana y workflows n8n. Solo ADMIN_GLOBAL.',
    secciones: [
      { titulo: 'Verificar el estado de los servicios',
        pasos: ['El panel muestra tarjetas con estado de cada servicio (verde = OK, rojo = error)', 'Clic en "Actualizar" para refrescar manualmente', 'Los servicios críticos: API, Postgres, Valkey, MinIO, Authentik'] },
      { titulo: 'Dashboards de Grafana',
        pasos: ['El iframe inferior muestra métricas en tiempo real: requests/s, latencia P95, errores', '"Abrir Grafana" para la interfaz completa en monitor.ades.setag.mx', 'Para crear dashboards: Grafana → Dashboards → New → Add panel'] },
      { titulo: 'Workflows de automatización (n8n)',
        pasos: ['La sección inferior lista los flujos activos', '"Abrir n8n" → accede a localhost:5678 para configurar flujos', 'Flujos predefinidos: alerta asistencia, alerta calificación, batch boletas, reporte semanal'],
        nota: 'n8n solo es accesible desde localhost — nunca está expuesto a internet.' },
    ],
  },
  {
    nombre: 'Administración', ruta: '/admin', icono: 'pi-cog', fase: 12,
    descripcion: 'Gestión central: usuarios, ciclos, grupos, identidad institucional y auditoría.',
    secciones: [
      { titulo: 'Gestionar usuarios',
        pasos: ['Pestaña "Usuarios" → buscar, activar/desactivar, cambiar rol', '"Nuevo usuario" → crear cuenta manual con todos los campos', '"Importar CSV" → carga masiva desde plantilla', 'El rol determina el acceso a datos y funcionalidades'] },
      { titulo: 'Configurar identidad institucional',
        pasos: ['Pestaña "Marca / Identidad"', 'Editar: nombre institución, slogan, colores, logotipo, favicon', 'Los cambios se aplican inmediatamente en toda la interfaz'] },
      { titulo: 'Auditoría del sistema',
        pasos: ['Pestaña "Auditoría" → últimas 200 mutaciones', 'Columnas: fecha, método, endpoint, entidad, usuario, IP, código HTTP', 'Solo visible para ADMIN_GLOBAL'],
        nota: 'Los registros de auditoría son inmutables y no pueden eliminarse.' },
    ],
  },
];

const SERVICIOS = [
  { nombre: 'ntfy', puerto: '2586 / notify.ades.setag.mx', descripcion: 'Push notifications sin Firebase. App móvil gratuita iOS/Android.', config: 'Instalar app ntfy → suscribirse al tema ades_{usuario_id} en notify.ades.setag.mx' },
  { nombre: 'Apache Superset', puerto: 'bi.ades.setag.mx', descripcion: 'BI y dashboards interactivos con KPIs del instituto.', config: 'Accesible desde el módulo "Dashboards BI" en el menú lateral.' },
  { nombre: 'Carbone', puerto: 'localhost:3001 (interno)', descripcion: 'Motor de reportes PDF desde plantillas Word/Excel.', config: 'Subir plantillas .docx en módulo Reportes → Subir Plantilla. Ver carbone.io para la sintaxis.' },
  { nombre: 'Stirling-PDF', puerto: 'localhost:8081 (interno)', descripcion: 'Merge, marca de agua, compresión de PDFs. Complemento de Carbone.', config: 'Usado automáticamente al generar boletas de grupo.' },
  { nombre: 'Flowise', puerto: 'localhost:3002 (interno)', descripcion: 'Orquestador visual de flujos IA para el chatbot.', config: 'Solo para administradores. Acceder desde localhost:3002 para configurar chatflows.' },
  { nombre: 'n8n', puerto: 'localhost:5678 (solo admin)', descripcion: 'Automatización de flujos: alertas académicas, batch boletas, reportes programados.', config: 'Acceder a localhost:5678. Importar workflows desde infrastructure/n8n/.' },
  { nombre: 'Grafana', puerto: '3003 / monitor.ades.setag.mx', descripcion: 'Dashboards de monitoreo del sistema en tiempo real.', config: 'Accesible desde el módulo Monitor del Sistema (ADMIN_GLOBAL).' },
  { nombre: 'Prometheus', puerto: 'localhost:9090 (interno)', descripcion: 'Recolección de métricas. Se scrape automáticamente desde FastAPI.', config: 'Interno. Configuración en infrastructure/prometheus/prometheus.yml.' },
];

const FAQS: FAQ[] = [
  { categoria: 'Acceso', pregunta: '¿Cómo recupero mi contraseña?', respuesta: 'En el login, clic en "¿Olvidaste tu contraseña?". Recibirás un correo con enlace válido por 30 minutos. Si no llega, revisa spam o contacta al administrador.' },
  { categoria: 'Acceso', pregunta: '¿Por qué no veo datos de todos los planteles?', respuesta: 'El acceso está limitado por tu rol. Directores ven su escuela, admins de plantel ven su plantel, solo ADMIN_GLOBAL ve todo. Si necesitas acceso adicional, solicítalo al administrador.' },
  { categoria: 'Calificaciones', pregunta: '¿Por qué no puedo editar una calificación de hace más de 30 días?', respuesta: 'Por política institucional, las calificaciones se bloquean pasados 30 días para garantizar integridad del historial. Para correcciones, el coordinador académico puede realizar el ajuste con justificación.' },
  { categoria: 'Calificaciones', pregunta: 'El promedio calculado no coincide con mi cálculo manual, ¿por qué?', respuesta: 'El promedio usa los esquemas de ponderación configurados para cada nivel y materia. En preparatoria UAEMEX los pesos difieren de secundaria SEP. Consulta Administración → Ponderaciones para ver los porcentajes exactos.' },
  { categoria: 'Asistencias', pregunta: '¿Cuándo se envía la alerta de asistencia baja al padre?', respuesta: 'La alerta se envía automáticamente cuando el porcentaje acumulado en el ciclo cae por debajo del 85%. Si el padre no la recibe, verificar que tenga la app ntfy instalada y esté suscrito al tema correcto.' },
  { categoria: 'Reportes', pregunta: 'La generación del PDF del grupo tarda mucho, ¿es normal?', respuesta: 'Sí, es normal. Carbone genera cada boleta individualmente y Stirling-PDF las fusiona. Para 30 alumnos puede tardar 2-4 minutos. No cierres la pestaña hasta que se descargue.' },
  { categoria: 'Notificaciones', pregunta: '¿Cómo activo las notificaciones push en mi teléfono?', respuesta: 'Instala la app ntfy (gratuita) en iOS o Android. Agrega el servidor notify.ades.setag.mx. Suscríbete al tema ades_{tu_id_de_usuario} — lo encuentras en tu perfil de ADES. Las notificaciones llegan aunque ADES no esté abierto.' },
  { categoria: 'Notificaciones', pregunta: 'No recibo notificaciones en el navegador aunque di permiso', respuesta: 'Las notificaciones del navegador requieren que ADES esté abierto. Verifica que el permiso no esté bloqueado (ícono de candado en la barra → Notificaciones → Permitir). Para notificaciones permanentes usa la app móvil ntfy.' },
  { categoria: 'Técnico', pregunta: '¿Cómo sé si todos los servicios están funcionando?', respuesta: 'Como ADMIN_GLOBAL, accede a "Monitor del Sistema" en el menú. Muestra el estado en tiempo real de todos los servicios. Los servicios en rojo requieren atención del equipo técnico.' },
  { categoria: 'Técnico', pregunta: '¿Dónde se guardan los archivos subidos?', respuesta: 'En MinIO, sistema de almacenamiento compatible con S3 instalado en el servidor del Instituto. Los archivos NO salen del servidor — todo es infraestructura propia.' },
  { categoria: 'Planes de Estudio', pregunta: '¿Puedo cambiar horas de una materia sin afectar el plan histórico?', respuesta: 'Sí. Al editar horas en el mapa, el cambio aplica solo al ciclo seleccionado. Los datos de ciclos anteriores permanecen intactos.' },
  { categoria: 'Planes de Estudio', pregunta: '¿Qué pasa si desactivo una materia?', respuesta: 'La materia se marca como inactiva y ya no aparece en el mapa curricular ni en las libretas. Las calificaciones históricas de esa materia se conservan. La desactivación es reversible desde el catálogo de materias.' },
];

const GUIAS_ROL = [
  {
    rol: 'ADMIN_GLOBAL', label: 'Administrador Global', severity: 'danger' as const,
    descripcion: 'Acceso completo a todos los planteles, niveles y módulos.',
    modulos: ['Dashboard (todos los planteles)', 'Administración completa', 'Todos los módulos académicos', 'Auditoría y Monitor del sistema', 'Planes de estudio', 'Reportes y boletas'],
    limitaciones: ['Sin limitaciones — puede ver y modificar todo el sistema'],
  },
  {
    rol: 'ADMIN_PLANTEL', label: 'Administrador de Plantel', severity: 'danger' as const,
    descripcion: 'Administración de un plantel completo (sus 3 escuelas).',
    modulos: ['Dashboard del plantel', 'Administración (su plantel)', 'Alumnos, profesores y grupos (su plantel)', 'Reportes'],
    limitaciones: ['No ve datos de otros planteles', 'No puede asignar roles de mayor jerarquía'],
  },
  {
    rol: 'DIRECTOR', label: 'Director(a) de Escuela', severity: 'warn' as const,
    descripcion: 'Supervisión de una escuela específica.',
    modulos: ['Dashboard de su escuela', 'Alumnos y profesores de su escuela', 'Calificaciones (consulta)', 'Grupos y comunicados'],
    limitaciones: ['Solo ve datos de su nivel educativo', 'No puede modificar planes de estudio'],
  },
  {
    rol: 'COORDINADOR_ACADEMICO', label: 'Coordinador Académico', severity: 'warn' as const,
    descripcion: 'Coordinación académica de un nivel. Configura ponderaciones y planes.',
    modulos: ['Planes y programas de estudio', 'Ponderaciones y esquemas de evaluación', 'Gradebook (supervisión)', 'Grupos y horarios'],
    limitaciones: ['Limitado a su nivel educativo y plantel'],
  },
  {
    rol: 'DOCENTE', label: 'Docente', severity: 'info' as const,
    descripcion: 'Acceso a sus grupos y materias asignadas.',
    modulos: ['Calificaciones (sus grupos)', 'Asistencias (sus clases)', 'Tareas y entregas', 'Gradebook', 'Planeación de clases', 'Evaluaciones y rúbricas'],
    limitaciones: ['Solo ve sus grupos y materias', 'No puede ver datos de otros docentes', 'No modifica expedientes'],
  },
  {
    rol: 'PADRE_FAMILIA', label: 'Padre / Tutor', severity: 'success' as const,
    descripcion: 'Vista del progreso de sus hijos. Notificaciones automáticas.',
    modulos: ['Portal de Padres', 'Comunicados (su familia)', 'Notificaciones push automáticas'],
    limitaciones: ['Solo ve datos de sus hijos vinculados', 'No puede modificar ningún dato'],
  },
  {
    rol: 'ALUMNO', label: 'Alumno', severity: 'success' as const,
    descripcion: 'Expediente académico propio y entrega de tareas.',
    modulos: ['Mi Progreso', 'Entrega de tareas', 'Comunicados del grupo/escuela', 'Badges y logros'],
    limitaciones: ['Solo ve sus propios datos'],
  },
];

@Component({
  selector: 'app-ayuda',
  standalone: true,
  imports: [CommonModule, FormsModule, AccordionModule, TabsModule, TagModule, ButtonModule, InputTextModule, TableModule],
  template: `
    <div class="page-header">
      <div>
        <h2><i class="pi pi-book" style="margin-right:.5rem"></i>Manual de Usuario ADES</h2>
        <p class="subtitle">Instituto Nevadi · Documentación completa del sistema</p>
      </div>
      <input pInputText [(ngModel)]="busqueda" placeholder="Buscar en el manual..."
        style="width:220px" (input)="onBusqueda()" />
    </div>

    @if (busqueda.trim()) {
      <div class="search-results">
        <h4>Resultados para "{{ busqueda }}"  <button class="clear-btn" (click)="limpiarBusqueda()">✕ Limpiar</button></h4>
        @if (resultadosBusqueda().length === 0) {
          <p style="color:var(--text-muted);font-size:.85rem">Sin resultados. Prueba con términos más generales.</p>
        }
        @for (r of resultadosBusqueda(); track r.titulo) {
          <div class="search-item" (click)="limpiarBusqueda()">
            <strong>{{ r.modulo }}</strong> → {{ r.titulo }}
            <p>{{ r.extracto }}</p>
          </div>
        }
      </div>
    } @else {
      <p-tabs value="roles">
        <p-tablist>
          <p-tab value="roles"><i class="pi pi-users"></i> Por Rol</p-tab>
          <p-tab value="modulos"><i class="pi pi-th-large"></i> Módulos</p-tab>
          <p-tab value="servicios"><i class="pi pi-server"></i> Servicios</p-tab>
          <p-tab value="seguridad"><i class="pi pi-shield"></i> Seguridad</p-tab>
          <p-tab value="faq"><i class="pi pi-question-circle"></i> FAQ</p-tab>
        </p-tablist>

        <p-tabpanels>

          <!-- ══ POR ROL ══ -->
          <p-tabpanel value="roles">
            <p class="section-desc">Selecciona tu rol para ver la guía de uso específica.</p>
            <p-accordion [multiple]="true">
              @for (guia of GUIAS_ROL; track guia.rol) {
                <p-accordion-panel>
                  <p-accordion-header>
                    <div style="display:flex;align-items:center;gap:.75rem">
                      <p-tag [value]="guia.label" [severity]="guia.severity" />
                      <span style="font-size:.83rem;color:var(--text-color-secondary)">{{ guia.descripcion }}</span>
                    </div>
                  </p-accordion-header>
                  <p-accordion-content>
                    <div class="rol-content">
                      <div class="rol-col">
                        <h5>Módulos accesibles</h5>
                        <ul>@for (m of guia.modulos; track m) { <li>{{ m }}</li> }</ul>
                      </div>
                      <div class="rol-col">
                        <h5>Limitaciones de acceso</h5>
                        <ul class="limitaciones">@for (l of guia.limitaciones; track l) { <li>{{ l }}</li> }</ul>
                      </div>
                    </div>
                  </p-accordion-content>
                </p-accordion-panel>
              }
            </p-accordion>
          </p-tabpanel>

          <!-- ══ MÓDULOS ══ -->
          <p-tabpanel value="modulos">
            <p class="section-desc">Documentación detallada de cada módulo con instrucciones paso a paso.</p>
            <p-accordion [multiple]="false">
              @for (mod of MODULOS; track mod.nombre) {
                <p-accordion-panel>
                  <p-accordion-header>
                    <div style="display:flex;align-items:center;gap:.75rem;flex:1;min-width:0">
                      <i class="pi {{ mod.icono }}" style="font-size:1rem;color:var(--primary-color);flex-shrink:0"></i>
                      <span style="font-weight:600;flex-shrink:0">{{ mod.nombre }}</span>
                      <p-tag value="F{{ mod.fase }}" severity="secondary" />
                      <span style="font-size:.8rem;color:var(--text-color-secondary);flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ mod.descripcion }}</span>
                    </div>
                  </p-accordion-header>
                  <p-accordion-content>
                    <div style="margin-bottom:.75rem">
                      <a [href]="mod.ruta" class="mod-link">Abrir módulo →</a>
                    </div>
                    @for (sec of mod.secciones; track sec.titulo) {
                      <div class="seccion">
                        <h5 class="sec-titulo">{{ sec.titulo }}</h5>
                        <ol class="sec-pasos">@for (p of sec.pasos; track p) { <li>{{ p }}</li> }</ol>
                        @if (sec.nota) { <div class="sec-nota"><i class="pi pi-info-circle"></i> {{ sec.nota }}</div> }
                        @if (sec.tip) { <div class="sec-tip"><i class="pi pi-lightbulb"></i> {{ sec.tip }}</div> }
                      </div>
                    }
                  </p-accordion-content>
                </p-accordion-panel>
              }
            </p-accordion>
          </p-tabpanel>

          <!-- ══ SERVICIOS ══ -->
          <p-tabpanel value="servicios">
            <p class="section-desc">Stack tecnológico open-source de ADES — todos auto-hospedados en infraestructura propia del Instituto.</p>
            <p-table [value]="SERVICIOS" styleClass="p-datatable-sm p-datatable-striped">
              <ng-template pTemplate="header">
                <tr>
                  <th style="width:130px">Servicio</th>
                  <th style="width:200px">URL / Puerto</th>
                  <th>Descripción</th>
                  <th>Configuración</th>
                </tr>
              </ng-template>
              <ng-template pTemplate="body" let-s>
                <tr>
                  <td><strong>{{ s.nombre }}</strong></td>
                  <td style="font-size:.75rem;font-family:monospace;color:#0f766e">{{ s.puerto }}</td>
                  <td style="font-size:.82rem">{{ s.descripcion }}</td>
                  <td style="font-size:.78rem;color:var(--text-secondary)">{{ s.config }}</td>
                </tr>
              </ng-template>
            </p-table>

            <div class="arch-note">
              <h4><i class="pi pi-lock" style="margin-right:.4rem"></i>Modelo de seguridad de la arquitectura</h4>
              <p>Todos los servicios internos (Carbone, Stirling-PDF, n8n, Prometheus, Flowise) <strong>no tienen acceso directo desde internet</strong>. FastAPI actúa como único proxy, validando JWT antes de reenviar peticiones.</p>
              <p>Servicios con acceso externo: <strong>ntfy</strong> (notify.ades.setag.mx — notificaciones móviles) y <strong>Grafana</strong> (monitor.ades.setag.mx — solo admin, restricción por IP recomendada).</p>
              <p>Los datos del Instituto <strong>nunca salen del servidor propio</strong> — sin dependencia de servicios cloud externos.</p>
            </div>
          </p-tabpanel>

          <!-- ══ SEGURIDAD ══ -->
          <p-tabpanel value="seguridad">
            <div class="sec-grid">
              <div class="sec-card">
                <h4><i class="pi pi-lock"></i> Autenticación</h4>
                <ul>
                  <li><strong>OIDC con Authentik:</strong> Protocolo estándar OpenID Connect.</li>
                  <li><strong>Personal docente:</strong> Google Workspace SSO (@institutonevadi.edu.mx) cuando esté configurado.</li>
                  <li><strong>Alumnos y padres:</strong> Cuentas locales en Authentik.</li>
                  <li><strong>JWT:</strong> Expiran en 1 hora. Tokens de actualización: 30 días.</li>
                </ul>
              </div>
              <div class="sec-card">
                <h4><i class="pi pi-shield"></i> Control de Acceso (RBAC)</h4>
                <ul>
                  <li>18 roles en 6 niveles (0=ADMIN_GLOBAL a 5=ALUMNO/PADRE)</li>
                  <li>Cada endpoint valida el nivel antes de devolver datos</li>
                  <li>Datos filtrados automáticamente por plantel/escuela/grupo según rol</li>
                  <li>No existe bypass de permisos desde la interfaz</li>
                </ul>
              </div>
              <div class="sec-card">
                <h4><i class="pi pi-history"></i> Auditoría Completa</h4>
                <ul>
                  <li>Todas las mutaciones (POST/PUT/PATCH/DELETE) quedan registradas</li>
                  <li>Se registra: usuario, IP origen, endpoint, método, código respuesta, duración</li>
                  <li>Registros inmutables — no se pueden modificar ni eliminar</li>
                  <li>Ver en Administración → pestaña Auditoría (ADMIN_GLOBAL)</li>
                </ul>
              </div>
              <div class="sec-card">
                <h4><i class="pi pi-database"></i> Datos y Privacidad</h4>
                <ul>
                  <li>Infraestructura 100% auto-hospedada — datos no salen del servidor del Instituto</li>
                  <li>Datos médicos: acceso restringido a personal autorizado</li>
                  <li>Expedientes de menores: protegidos por RBAC y auditoría completa</li>
                  <li>Archivos en MinIO local — no en servicios cloud externos</li>
                </ul>
              </div>
              <div class="sec-card">
                <h4><i class="pi pi-key"></i> Buenas Prácticas</h4>
                <ul>
                  <li>Usar contraseñas únicas y no compartir credenciales</li>
                  <li>Cerrar sesión en equipos compartidos</li>
                  <li>Reportar accesos no autorizados inmediatamente al administrador</li>
                  <li>El log de auditoría detecta IPs inusuales y accesos fuera de horario</li>
                  <li>Los roles deben asignarse con el mínimo privilegio necesario</li>
                </ul>
              </div>
              <div class="sec-card">
                <h4><i class="pi pi-file-check"></i> Firma Digital — Próximamente (FASE 28)</h4>
                <ul>
                  <li><strong>Nivel 1 (Integridad):</strong> Boletas y certificados firmados con llave del Instituto. Verificables con cualquier lector PDF. Estándar PAdES.</li>
                  <li><strong>Nivel 2 (Legal):</strong> Integración con FIEL/e.firm SAT para validez oficial ante SEP/UAEMEX.</li>
                  <li>Cada documento tendrá código QR de verificación único.</li>
                </ul>
              </div>
            </div>
          </p-tabpanel>

          <!-- ══ FAQ ══ -->
          <p-tabpanel value="faq">
            <p class="section-desc">Respuestas a las dudas más frecuentes sobre el uso del sistema.</p>
            @for (cat of categoriasFAQ(); track cat) {
              <h4 class="faq-cat">{{ cat }}</h4>
              <p-accordion [multiple]="true">
                @for (faq of faqsPorCategoria(cat); track faq.pregunta) {
                  <p-accordion-panel>
                    <p-accordion-header>{{ faq.pregunta }}</p-accordion-header>
                    <p-accordion-content>
                      <p style="font-size:.88rem;line-height:1.6;margin:0">{{ faq.respuesta }}</p>
                    </p-accordion-content>
                  </p-accordion-panel>
                }
              </p-accordion>
            }
          </p-tabpanel>

        </p-tabpanels>
      </p-tabs>
    }
  `,
  styles: [`
    .page-header { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:1.25rem; }
    .page-header h2 { margin:0; }
    .subtitle { font-size:.82rem; color:var(--text-color-secondary); margin:0; }
    .section-desc { font-size:.85rem; color:var(--text-color-secondary); margin:0 0 1rem; }

    /* ── Búsqueda ── */
    .search-results { padding:1rem 0; }
    .search-results h4 { margin:0 0 1rem; font-size:.9rem; display:flex;align-items:center;gap:.75rem }
    .clear-btn { background:none;border:1px solid var(--surface-300);border-radius:4px;padding:.15rem .5rem;cursor:pointer;font-size:.75rem;color:var(--text-secondary) }
    .search-item { padding:.75rem 1rem; border:1px solid var(--surface-200); border-radius:8px; margin-bottom:.5rem; cursor:pointer; transition:background .15s; }
    .search-item:hover { background:var(--surface-50); }
    .search-item strong { font-size:.85rem; color:var(--primary-color); }
    .search-item p { margin:.25rem 0 0; font-size:.8rem; color:var(--text-color-secondary); }

    /* ── Roles ── */
    .rol-content { display:grid; grid-template-columns:1fr 1fr; gap:1.5rem; padding:.5rem 0; }
    .rol-col h5 { font-size:.78rem; text-transform:uppercase; letter-spacing:.05em; color:var(--primary-color); margin:0 0 .6rem; }
    .rol-col ul { margin:0; padding-left:1.2rem; }
    .rol-col li { font-size:.84rem; margin-bottom:.25rem; line-height:1.5; }
    .limitaciones li { color:var(--text-secondary); }

    /* ── Módulos ── */
    .mod-link { font-size:.8rem;color:var(--primary-color);text-decoration:none;display:inline-block;margin-bottom:.5rem }
    .mod-link:hover { text-decoration:underline }
    .seccion { margin-bottom:1.25rem; padding-bottom:1rem; border-bottom:1px solid var(--surface-100); }
    .seccion:last-child { border-bottom:none; }
    .sec-titulo { font-size:.85rem; font-weight:700; color:var(--surface-900); margin:0 0 .6rem; }
    .sec-pasos { margin:0 0 .6rem; padding-left:1.4rem; }
    .sec-pasos li { font-size:.84rem; line-height:1.6; margin-bottom:.2rem; }
    .sec-nota { font-size:.8rem;color:#1d4ed8;background:#eff6ff;padding:.5rem .75rem;border-radius:6px;margin-top:.5rem;border-left:3px solid #3b82f6 }
    .sec-tip  { font-size:.8rem;color:#065f46;background:#f0fdf4;padding:.5rem .75rem;border-radius:6px;margin-top:.5rem;border-left:3px solid #10b981 }
    .sec-nota i, .sec-tip i { margin-right:.35rem; }

    /* ── Servicios ── */
    .arch-note { margin-top:1.5rem;padding:1rem 1.25rem;background:var(--surface-50);border-radius:8px;border:1px solid var(--surface-200) }
    .arch-note h4 { margin:0 0 .6rem;font-size:.88rem;color:var(--primary-color);display:flex;align-items:center }
    .arch-note p { font-size:.83rem;line-height:1.6;margin:0 0 .5rem }

    /* ── Seguridad ── */
    .sec-grid { display:grid;grid-template-columns:repeat(auto-fill,minmax(320px,1fr));gap:1rem }
    .sec-card { background:var(--surface-0);border:1px solid var(--surface-200);border-radius:10px;padding:1rem 1.25rem }
    .sec-card h4 { font-size:.88rem;font-weight:700;margin:0 0 .75rem;display:flex;align-items:center;gap:.5rem;color:var(--surface-900) }
    .sec-card h4 i { color:var(--primary-color) }
    .sec-card ul { margin:0;padding-left:1.2rem }
    .sec-card li { font-size:.83rem;line-height:1.55;margin-bottom:.25rem }

    /* ── FAQ ── */
    .faq-cat { font-size:.78rem;text-transform:uppercase;letter-spacing:.06em;color:var(--primary-color);margin:1.25rem 0 .5rem;border-bottom:1px solid var(--surface-200);padding-bottom:.4rem }
  `],
})
export class AyudaComponent {
  readonly ctx = inject(ContextService);

  busqueda = '';
  resultadosBusqueda = signal<{modulo:string;titulo:string;extracto:string}[]>([]);

  readonly GUIAS_ROL = GUIAS_ROL;
  readonly MODULOS   = MODULOS;
  readonly SERVICIOS = SERVICIOS;

  categoriasFAQ = () => [...new Set(FAQS.map(f => f.categoria))];
  faqsPorCategoria = (cat: string) => FAQS.filter(f => f.categoria === cat);

  onBusqueda(): void {
    const q = this.busqueda.trim().toLowerCase();
    if (!q) { this.resultadosBusqueda.set([]); return; }
    const res: {modulo:string;titulo:string;extracto:string}[] = [];
    for (const mod of MODULOS) {
      for (const sec of mod.secciones) {
        const texto = [sec.titulo, ...sec.pasos, sec.nota ?? '', sec.tip ?? ''].join(' ').toLowerCase();
        if (texto.includes(q)) {
          res.push({ modulo: mod.nombre, titulo: sec.titulo, extracto: sec.pasos[0].slice(0, 90) + '…' });
        }
      }
    }
    for (const faq of FAQS) {
      if ((faq.pregunta + faq.respuesta).toLowerCase().includes(q)) {
        res.push({ modulo: 'FAQ', titulo: faq.pregunta, extracto: faq.respuesta.slice(0, 90) + '…' });
      }
    }
    this.resultadosBusqueda.set(res.slice(0, 15));
  }

  limpiarBusqueda(): void { this.busqueda = ''; this.resultadosBusqueda.set([]); }
}
