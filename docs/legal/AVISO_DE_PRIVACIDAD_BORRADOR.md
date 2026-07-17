# Aviso de Privacidad — BORRADOR (requiere revisión legal antes de publicar)

> ⚠️ **Este documento es un borrador técnico, no una entrega legal terminada.**
> Fue generado a partir del esquema real de datos que captura el sistema ADES
> (ver sección "Cómo se generó este borrador"), para servir de punto de partida
> a quien tenga autoridad legal en el Instituto Nevadi. **No debe publicarse ni
> entregarse a padres/personal sin que un abogado o el área administrativa
> competente lo revise, complete los campos marcados `[COMPLETAR]` y lo
> apruebe.** No constituye asesoría legal.
>
> Fundamento: Ley Federal de Protección de Datos Personales en Posesión de los
> Particulares (LFPDPPP) y su Reglamento — México. Aplica porque el Instituto
> Nevadi es una institución privada ("particular"), no un ente de gobierno (que
> se regiría por la ley de datos en posesión de sujetos obligados, distinta).
>
> **⚠️ Actualización 2026-07-17 — hallazgo que requiere decisión legal, no
> resuelto aquí:** se cotejó este borrador contra el aviso de privacidad
> **ya publicado** por el Instituto en
> `https://institutonevadi.edu.mx/aviso_de_privacidad.html`. Ese aviso público
> declara textualmente **"no recabamos datos personales sensibles"** — pero el
> sistema ADES sí captura datos de salud (alergias, condiciones crónicas, tipo
> de sangre, discapacidad) e identidad de género, que la propia LFPDPPP (Art. 3
> fr. VI) clasifica como sensibles. Esto es una discrepancia real entre lo que
> el Instituto declara públicamente y lo que el sistema efectivamente recaba —
> **no es algo que un borrador técnico pueda resolver por sí solo**: hace falta
> que quien tenga autoridad legal decida si (a) se actualiza el aviso público
> para reconocer estos datos sensibles, o (b) existe/debe crearse un
> consentimiento específico y separado para ellos (p. ej. en el expediente
> médico de inscripción) que no sustituye la actualización del aviso general.
> Ver sección 10 para el resto de discrepancias encontradas.

---

## Aviso de Privacidad Integral

**Responsable:** **Educación para Ser, Toluca, A.C.** (razón social real,
tomada del aviso de privacidad publicado en
`https://institutonevadi.edu.mx/aviso_de_privacidad.html` el 2026-07-17 —
**no coincide** con "Instituto Nevadi, A.C.", nombre que usaba hasta hoy el
borrador del portal de admisiones, ver nota de discrepancia en la sección 10),
con domicilio en Campos Elíseos #400, piso 10, Colonia Polanco IV Sección,
Delegación Miguel Hidalgo, Ciudad de México, C.P. 11000, es responsable del
tratamiento de los datos personales que usted nos proporcione, en términos de
la Ley Federal de Protección de Datos Personales en Posesión de los
Particulares (LFPDPPP).

### 1. ¿Quién es el Responsable y cómo contactarlo?

`[COMPLETAR: el aviso público solo da un correo genérico de administración
(ver abajo), no el nombre de una persona designada como "Responsable" —
Art. 3 fr. XIV LFPDPPP exige que exista alguien con autoridad real, no
solo un buzón. Falta designar/documentar quién es esa persona en el
Instituto.]`

Correo para ejercer derechos ARCO (Acceso, Rectificación, Cancelación,
Oposición): **administracion@institutonevadi.org.mx** (tomado del aviso
público real; plazo de respuesta ahí declarado: 15 días hábiles — más corto
que el estándar LFPDPPP de 20 días, verificar que sea intencional).

### 2. ¿Qué datos personales recabamos?

El sistema ADES recaba, según el rol de la persona, las siguientes categorías
de datos:

**De alumnos:**
- Identificación: nombre completo, CURP, fecha de nacimiento, género,
  lugar de nacimiento, nacionalidad, matrícula, fotografía (credencial escolar)
- Contacto: teléfono, correo electrónico personal
- Académicos: calificaciones, asistencia, tareas, evaluaciones, planes de
  aprendizaje personalizados, historial de conducta y disciplina
- Salud (dato sensible — Art. 3 fr. VI LFPDPPP): tipo de sangre, alergias,
  medicamentos autorizados, condiciones crónicas, discapacidad, número de
  seguridad social (IMSS/ISSSTE), información de seguro médico
- Socioeconómicos: nivel socioeconómico del hogar, información de becas
  (tipo y monto, cuando aplica)
- Identidad de género (dato sensible cuando difiere del sexo legal): género
  autopercibido, nombre social, pronombres — recabados únicamente si el
  alumno/tutor decide proporcionarlos, con el propósito de trato respetuoso
  en el aula

**De padres, madres y tutores legales:**
- Identificación y contacto: nombre completo, teléfono(s), correo electrónico,
  RFC (cuando se requiere para trámites), ocupación, nivel de estudios
- Relación con el alumno: parentesco, si es tutor legal, si puede recoger al
  alumno del plantel

**De personal docente y administrativo:**
- Identificación: nombre completo, CURP, RFC, fecha de nacimiento, género
- Contacto: teléfono, correo electrónico
- Laborales: número de empleado, puesto, tipo de contrato, fecha de ingreso,
  cédula profesional, nivel de estudios, especialidad
- Previsionales y de nómina: número de seguridad social (IMSS/ISSSTE), CLABE
  interbancaria, banco — **datos financieros, tratados con cifrado**
- Evaluación docente (cuando aplica): resultados de evaluación 360°

**Datos técnicos:** el sistema usa autenticación mediante un proveedor de
identidad (Authentik) y registra eventos de auditoría (quién hizo qué
operación y cuándo) sobre información académica y administrativa, con fines
de seguridad y trazabilidad — no con fines de perfilamiento comercial.

**No recabamos:** datos de tarjetas de pago ni información bancaria de padres
(el Instituto no opera pasarela de pago; las cuotas se gestionan fuera de este
sistema). No se recaban datos biométricos (huella digital, reconocimiento
facial).

### 3. ¿Para qué fines recabamos sus datos?

**Finalidades primarias** (necesarias para la relación educativa; sin ellas
no podemos prestar el servicio):
- Prestación del servicio educativo: inscripción, seguimiento académico,
  evaluación, control escolar
- Cumplimiento de obligaciones ante autoridades educativas: SEP (Primaria y
  Secundaria) y UAEMEX (Preparatoria) — incluye reportes oficiales como el
  Reporte 911 y expedición de boletas, kardex, constancias y certificados
- Seguridad y bienestar del alumno: atención médica de emergencia, contacto
  con tutores, seguimiento de conducta
- Gestión de nómina y expediente laboral del personal

**Finalidades secundarias** (usted puede oponerse a estas sin que afecte la
relación educativa — Art. 8 y 9 LFPDPPP):
- `[COMPLETAR: si el Instituto usa fotos/videos de alumnos en materiales de
  difusión, redes sociales, eventos — describir aquí y mecanismo de
  oposición. Si no aplica, indicar "No se usan datos para fines secundarios
  distintos a los primarios."]`

### 4. Datos personales sensibles

Algunos de los datos que recabamos son **sensibles** en términos del Art. 3
fr. VI LFPDPPP (su uso indebido podría causar discriminación o un riesgo
grave): **datos de salud** (alergias, condiciones crónicas, discapacidad,
tipo de sangre) e **identidad/expresión de género** cuando difiere del sexo
legal. Recabamos estos datos únicamente para la seguridad del alumno
(atención médica) y para un trato respetuoso (identidad de género), con su
consentimiento expreso.

### 5. Consentimiento — datos de menores de edad

La mayoría de las personas titulares de los datos académicos son **menores
de edad**. En términos del Art. 8 LFPDPPP y su Reglamento, el consentimiento
para el tratamiento de datos de un menor lo otorga su padre, madre o tutor
legal al momento de la inscripción. `[COMPLETAR: describir el mecanismo real
por el que se obtiene y registra este consentimiento — p. ej. firma en el
contrato/formato de inscripción — y si existe evidencia documental archivada.]`

### 6. Transferencias de datos

Sus datos podrán transferirse, sin requerir su consentimiento (excepciones
del Art. 10 LFPDPPP), a:
- **SEP** y **UAEMEX**, por mandato legal, para control escolar y reportes
  oficiales (Reporte 911, CCT, boletas, kardex)
- Proveedores que actúan como **encargados** (procesan datos por cuenta del
  Instituto, bajo contrato, no como responsables independientes): hosting e
  infraestructura del sistema ADES, proveedor de autenticación (Authentik)

No se venden ni comparten datos con fines de mercadotecnia ajenos al
Instituto.

### 7. ¿Cómo puede ejercer sus derechos ARCO?

Usted (o el padre/tutor de un alumno menor de edad) puede en cualquier
momento **Acceder** a sus datos, **Rectificarlos** si son inexactos,
**Cancelarlos** cuando considere que no se requieren para las finalidades
señaladas, u **Oponerse** a su tratamiento para fines secundarios, enviando
una solicitud a **administracion@institutonevadi.org.mx** (correo real del
aviso público — `[COMPLETAR: confirmar si este buzón realmente se monitorea
y quién lo atiende; el aviso público no designa una persona/área específica]`)
con: nombre completo,
documento que acredite identidad (o representación legal del menor),
descripción clara de los datos y la solicitud, y cualquier documento que
facilite la localización de los datos.

### 8. Seguridad de sus datos

El sistema ADES aplica medidas técnicas y administrativas para proteger sus
datos, entre ellas: cifrado en tránsito (HTTPS/TLS), control de acceso
basado en rol y plantel, registro de auditoría de operaciones, y cifrado en
reposo (AES-256-GCM) para CURP, teléfono y correo personal — aplicado el
2026-07-11 a las 5,178 personas registradas en el sistema (ver
`docs/hallazgos/2026-07-11_bug_transaccional_patch_personas.md` para el
detalle técnico y la verificación). `[NOTA TÉCNICA: el texto plano de estos
campos NO se ha retirado de la base de datos — el cifrado es una capa
adicional de protección, no un reemplazo. Retirar el texto plano es una
migración de mayor alcance, pendiente.]`

### 9. Cambios a este aviso

Cualquier modificación a este aviso de privacidad se hará del conocimiento de
los titulares a través de `[COMPLETAR: medio — p. ej. portal del Instituto,
comunicado a padres]`.

**Última actualización:** `[COMPLETAR fecha de publicación real]` — el aviso
público consultado el 2026-07-17 tampoco declara su propia fecha de última
actualización (hallazgo, no es solo un vacío de este borrador).

### 10. Discrepancias encontradas al cotejar contra el aviso público real (2026-07-17)

Se comparó este borrador contra `https://institutonevadi.edu.mx/aviso_de_privacidad.html`
(el aviso que el Instituto ya tiene publicado). Hallazgos, de mayor a menor
severidad:

1. **Datos sensibles (ver banner al inicio del documento) — el más grave.**
   El aviso público dice "no recabamos datos sensibles"; ADES sí recaba salud
   e identidad de género. Requiere decisión legal explícita, no un ajuste de
   texto.
2. **Razón social distinta a la que usa el sistema hoy.** El aviso público usa
   **"Educación para Ser, Toluca, A.C."** como responsable legal. El
   componente `frontend-portal/src/app/features/aviso-privacidad/aviso-privacidad.component.ts`
   (aviso del portal de admisiones, ya en producción) decía **"Instituto
   Nevadi, A.C."** — nombre distinto. Corregido en esa misma sesión para que
   coincida con el aviso público real (ver commit/diff de
   `aviso-privacidad.component.ts`), pero **confirmar con el Instituto cuál
   de los dos nombres es el correcto/vigente** — "Instituto Nevadi" podría ser
   el nombre comercial y "Educación para Ser, Toluca, A.C." la razón social
   legal (común en México), en cuyo caso ambos textos son coherentes; o podría
   ser un error real en uno de los dos avisos.
3. **Domicilio distinto.** El aviso público ubica al responsable en
   Polanco, Ciudad de México. El componente del portal decía "Estado de
   México" — corregido también, mismo comentario que el punto 2 sobre
   confirmar cuál es el domicilio fiscal real vs. el domicilio operativo del
   plantel.
4. **Correo de contacto ARCO distinto.** El portal usaba
   `privacidad@nevadi.edu.mx` (dominio `.edu.mx`); el aviso público real usa
   `administracion@institutonevadi.org.mx` (dominio `.org.mx`, buzón de
   administración general, no uno dedicado a privacidad). Corregido en el
   componente para usar el correo real — pero un buzón de "administración"
   genérico no cumple del todo el espíritu del Art. 3 fr. XIV LFPDPPP (persona
   designada); queda como el mismo pendiente ya señalado en la sección 1.
5. **Plazo de respuesta ARCO:** el aviso público declara 15 días hábiles (más
   estricto que el default LFPDPPP de 20) — mantenido tal cual en este
   borrador por ser lo realmente publicado, no un error a corregir.
6. **El aviso público no cubre, ni tiene por qué cubrir**, todo lo que ADES
   recaba (calificaciones, conducta, nómina, expediente laboral) — es un aviso
   más acotado, aparentemente pensado para el sitio institucional/portal de
   admisiones. Este borrador integral sigue siendo necesario como pieza
   separada y más amplia para el sistema completo, no un reemplazo 1:1.

---

## Cómo se generó este borrador

Se derivó revisando el esquema real de base de datos del sistema ADES
(tablas `ades_personas`, `ades_estudiantes`, `ades_contactos_familiares`,
`ades_expediente_medico`, `ades_personal_administrativo`, `ades_docentes`,
etc. en `db/migrations/`) el 2026-07-11, para que las categorías de datos
listadas correspondan a lo que el sistema **realmente captura**, no a una
plantilla genérica. No se tuvo acceso a información institucional (razón
social exacta, domicilio fiscal, quién es la persona Responsable) — de ahí
los campos `[COMPLETAR]`.

## Checklist antes de publicar

- [ ] Todos los `[COMPLETAR]` llenados por alguien con autoridad legal/administrativa
- [ ] Confirmado el mecanismo real de consentimiento de tutores (sección 5)
- [ ] Revisado por asesor legal del Instituto
- [ ] Definido el canal real para solicitudes ARCO (no un correo que nadie monitorea)
- [ ] Publicado en un lugar accesible (portal, físico en recepción) antes de recabar datos de nuevos alumnos/personal
- [ ] Revisar si aplica registro/aviso simplificado ante el INAI según el volumen de datos tratados
