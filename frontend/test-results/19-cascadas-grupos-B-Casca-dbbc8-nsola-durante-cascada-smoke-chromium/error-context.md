# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: 19-cascadas-grupos.spec.ts >> B. Cascadas Grupos - Ciclo a Grado filtracion por Nivel >> GRP-CASCADE-06 | Sin errores en consola durante cascada @smoke
- Location: e2e/tests/19-cascadas-grupos.spec.ts:328:7

# Error details

```
Test timeout of 30000ms exceeded.
```

```
Error: locator.click: Test timeout of 30000ms exceeded.
Call log:
  - waiting for locator('[data-testid="btn-nuevo-grupo"]')

```

# Page snapshot

```yaml
- generic [ref=e3]:
  - status [ref=e4]
  - toolbar [ref=e5]:
    - generic [ref=e6]:
      - generic [ref=e7]:
        - generic [ref=e8]: "N"
        - generic [ref=e9]:
          - generic [ref=e10]: ADES
          - generic [ref=e11]: Instituto Nevadi
      - generic [ref=e13] [cursor=pointer]:
        - combobox "Plantel..." [ref=e14]
        - button "dropdown trigger" [ref=e15]:
          - img [ref=e16]
      - generic [ref=e18]: /
      - generic [ref=e19] [cursor=pointer]:
        - combobox "Nivel..." [ref=e20]
        - button "dropdown trigger" [ref=e21]:
          - img [ref=e22]
      - generic [ref=e24]: /
      - generic [ref=e25] [cursor=pointer]:
        - combobox "Ciclo..." [ref=e26]
        - button "dropdown trigger" [ref=e27]:
          - img [ref=e28]
      - generic [ref=e30]: /
      - generic [ref=e31] [cursor=pointer]:
        - combobox "Grado..." [ref=e32]
        - button "dropdown trigger" [ref=e33]:
          - img [ref=e34]
      - generic [ref=e36]: /
      - generic [ref=e37] [cursor=pointer]:
        - combobox "Grupo..." [ref=e38]
        - button "dropdown trigger" [ref=e39]:
          - img [ref=e40]
    - generic [ref=e42]:
      - button "Notificaciones" [ref=e43] [cursor=pointer]:
        - generic [ref=e44]: 
      - button "Cuenta de Test ADMIN_GLOBAL" [ref=e45] [cursor=pointer]:
        - generic [ref=e46]: T
        - generic [ref=e47]:
          - generic [ref=e48]: Test ADMIN_GLOBAL
          - generic [ref=e49]: Admin Global
        - generic [ref=e50]: 
  - generic [ref=e51]:
    - navigation "Navegación principal" [ref=e52]:
      - generic [ref=e53]: Principal
      - list [ref=e54]:
        - listitem [ref=e55]:
          - link " Dashboard" [ref=e56] [cursor=pointer]:
            - /url: /dashboard
            - generic [ref=e57]: 
            - generic [ref=e58]: Dashboard
      - generic [ref=e59]: Académico
      - list [ref=e60]:
        - listitem [ref=e61]:
          - link " Alumnos" [ref=e62] [cursor=pointer]:
            - /url: /alumnos
            - generic [ref=e63]: 
            - generic [ref=e64]: Alumnos
        - listitem [ref=e65]:
          - link " Reinscripción" [ref=e66] [cursor=pointer]:
            - /url: /reinscripcion
            - generic [ref=e67]: 
            - generic [ref=e68]: Reinscripción
        - listitem [ref=e69]:
          - link " Cierre de Ciclo" [ref=e70] [cursor=pointer]:
            - /url: /cierre-ciclo
            - generic [ref=e71]: 
            - generic [ref=e72]: Cierre de Ciclo
        - listitem [ref=e73]:
          - link " Gestión de Padres" [ref=e74] [cursor=pointer]:
            - /url: /padres-admin
            - generic [ref=e75]: 
            - generic [ref=e76]: Gestión de Padres
        - listitem [ref=e77]:
          - link " Profesores" [ref=e78] [cursor=pointer]:
            - /url: /profesores
            - generic [ref=e79]: 
            - generic [ref=e80]: Profesores
        - listitem [ref=e81]:
          - link " Grupos" [ref=e82] [cursor=pointer]:
            - /url: /grupos
            - generic [ref=e83]: 
            - generic [ref=e84]: Grupos
        - listitem [ref=e85]:
          - link " Aulas" [ref=e86] [cursor=pointer]:
            - /url: /aulas
            - generic [ref=e87]: 
            - generic [ref=e88]: Aulas
        - listitem [ref=e89]:
          - link " Planes de Estudio" [ref=e90] [cursor=pointer]:
            - /url: /planes-estudio
            - generic [ref=e91]: 
            - generic [ref=e92]: Planes de Estudio
        - listitem [ref=e93]:
          - link " Calificaciones" [ref=e94] [cursor=pointer]:
            - /url: /calificaciones
            - generic [ref=e95]: 
            - generic [ref=e96]: Calificaciones
        - listitem [ref=e97]:
          - link " Evaluaciones" [ref=e98] [cursor=pointer]:
            - /url: /evaluaciones
            - generic [ref=e99]: 
            - generic [ref=e100]: Evaluaciones
        - listitem [ref=e101]:
          - link " Asistencias" [ref=e102] [cursor=pointer]:
            - /url: /asistencias
            - generic [ref=e103]: 
            - generic [ref=e104]: Asistencias
        - listitem [ref=e105]:
          - link " Tareas" [ref=e106] [cursor=pointer]:
            - /url: /tareas
            - generic [ref=e107]: 
            - generic [ref=e108]: Tareas
        - listitem [ref=e109]:
          - link " Planeación" [ref=e110] [cursor=pointer]:
            - /url: /planeacion
            - generic [ref=e111]: 
            - generic [ref=e112]: Planeación
      - generic [ref=e113]: Operaciones
      - list [ref=e114]:
        - listitem [ref=e115]:
          - link " Horarios" [ref=e116] [cursor=pointer]:
            - /url: /horarios
            - generic [ref=e117]: 
            - generic [ref=e118]: Horarios
        - listitem [ref=e119]:
          - link " Calendario Escolar" [ref=e120] [cursor=pointer]:
            - /url: /calendario
            - generic [ref=e121]: 
            - generic [ref=e122]: Calendario Escolar
        - listitem [ref=e123]:
          - link " Conducta" [ref=e124] [cursor=pointer]:
            - /url: /conducta
            - generic [ref=e125]: 
            - generic [ref=e126]: Conducta
        - listitem [ref=e127]:
          - link " Expediente Médico" [ref=e128] [cursor=pointer]:
            - /url: /medico
            - generic [ref=e129]: 
            - generic [ref=e130]: Expediente Médico
        - listitem [ref=e131]:
          - link " Condiciones Crónicas" [ref=e132] [cursor=pointer]:
            - /url: /condiciones-cronicas
            - generic [ref=e133]: 
            - generic [ref=e134]: Condiciones Crónicas
        - listitem [ref=e135]:
          - link " Justificaciones Faltas" [ref=e136] [cursor=pointer]:
            - /url: /justificaciones
            - generic [ref=e137]: 
            - generic [ref=e138]: Justificaciones Faltas
        - listitem [ref=e139]:
          - link " Movilidad Estudiantil" [ref=e140] [cursor=pointer]:
            - /url: /movilidad
            - generic [ref=e141]: 
            - generic [ref=e142]: Movilidad Estudiantil
        - listitem [ref=e143]:
          - link " Biblioteca" [ref=e144] [cursor=pointer]:
            - /url: /biblioteca
            - generic [ref=e145]: 
            - generic [ref=e146]: Biblioteca
        - listitem [ref=e147]:
          - link " Formato 911 SEP" [ref=e148] [cursor=pointer]:
            - /url: /estadistica-911
            - generic [ref=e149]: 
            - generic [ref=e150]: Formato 911 SEP
        - listitem [ref=e151]:
          - link " Kardex UAEMEX" [ref=e152] [cursor=pointer]:
            - /url: /kardex
            - generic [ref=e153]: 
            - generic [ref=e154]: Kardex UAEMEX
        - listitem [ref=e155]:
          - link " Acta Evaluación UAEMEX" [ref=e156] [cursor=pointer]:
            - /url: /acta-evaluacion
            - generic [ref=e157]: 
            - generic [ref=e158]: Acta Evaluación UAEMEX
        - listitem [ref=e159]:
          - link " Optativas" [ref=e160] [cursor=pointer]:
            - /url: /optativas
            - generic [ref=e161]: 
            - generic [ref=e162]: Optativas
        - listitem [ref=e163]:
          - link " Admisión" [ref=e164] [cursor=pointer]:
            - /url: /admision
            - generic [ref=e165]: 
            - generic [ref=e166]: Admisión
      - generic [ref=e167]: Recursos Humanos
      - list [ref=e168]:
        - listitem [ref=e169]:
          - link " Personal No-Docente" [ref=e170] [cursor=pointer]:
            - /url: /personal-admin
            - generic [ref=e171]: 
            - generic [ref=e172]: Personal No-Docente
        - listitem [ref=e173]:
          - link " Licencias y Permisos" [ref=e174] [cursor=pointer]:
            - /url: /licencias
            - generic [ref=e175]: 
            - generic [ref=e176]: Licencias y Permisos
        - listitem [ref=e177]:
          - link " Capacitaciones" [ref=e178] [cursor=pointer]:
            - /url: /capacitaciones
            - generic [ref=e179]: 
            - generic [ref=e180]: Capacitaciones
        - listitem [ref=e181]:
          - link " Expediente Laboral" [ref=e182] [cursor=pointer]:
            - /url: /expediente-laboral
            - generic [ref=e183]: 
            - generic [ref=e184]: Expediente Laboral
        - listitem [ref=e185]:
          - link " Disponibilidad Docente" [ref=e186] [cursor=pointer]:
            - /url: /disponibilidad
            - generic [ref=e187]: 
            - generic [ref=e188]: Disponibilidad Docente
        - listitem [ref=e189]:
          - link " Asistencia Personal" [ref=e190] [cursor=pointer]:
            - /url: /asistencia-personal
            - generic [ref=e191]: 
            - generic [ref=e192]: Asistencia Personal
      - generic [ref=e193]: Comunicación
      - list [ref=e194]:
        - listitem [ref=e195]:
          - link " Comunicados" [ref=e196] [cursor=pointer]:
            - /url: /comunicados
            - generic [ref=e197]: 
            - generic [ref=e198]: Comunicados
        - listitem [ref=e199]:
          - link " Foros y Anuncios" [ref=e200] [cursor=pointer]:
            - /url: /foros
            - generic [ref=e201]: 
            - generic [ref=e202]: Foros y Anuncios
        - listitem [ref=e203]:
          - link " Encuestas" [ref=e204] [cursor=pointer]:
            - /url: /encuestas
            - generic [ref=e205]: 
            - generic [ref=e206]: Encuestas
        - listitem [ref=e207]:
          - link " Videoconferencias" [ref=e208] [cursor=pointer]:
            - /url: /videoconferencias
            - generic [ref=e209]: 
            - generic [ref=e210]: Videoconferencias
      - generic [ref=e211]: Gradebook
      - list [ref=e212]:
        - listitem [ref=e213]:
          - link " Gradebook" [ref=e214] [cursor=pointer]:
            - /url: /gradebook
            - generic [ref=e215]: 
            - generic [ref=e216]: Gradebook
        - listitem [ref=e217]:
          - link " Mi Progreso" [ref=e218] [cursor=pointer]:
            - /url: /mi-progreso
            - generic [ref=e219]: 
            - generic [ref=e220]: Mi Progreso
        - listitem [ref=e221]:
          - link " Ponderaciones" [ref=e222] [cursor=pointer]:
            - /url: /ponderacion-config
            - generic [ref=e223]: 
            - generic [ref=e224]: Ponderaciones
      - generic [ref=e225]: Recursos
      - list [ref=e226]:
        - listitem [ref=e227]:
          - link " Rúbricas" [ref=e228] [cursor=pointer]:
            - /url: /rubricas
            - generic [ref=e229]: 
            - generic [ref=e230]: Rúbricas
        - listitem [ref=e231]:
          - link " Insignias" [ref=e232] [cursor=pointer]:
            - /url: /badges
            - generic [ref=e233]: 
            - generic [ref=e234]: Insignias
        - listitem [ref=e235]:
          - link " Portal Alumno" [ref=e236] [cursor=pointer]:
            - /url: /portal
            - generic [ref=e237]: 
            - generic [ref=e238]: Portal Alumno
        - listitem [ref=e239]:
          - link " Contenido H5P" [ref=e240] [cursor=pointer]:
            - /url: /h5p
            - generic [ref=e241]: 
            - generic [ref=e242]: Contenido H5P
      - generic [ref=e243]: Convocatorias
      - list [ref=e244]:
        - listitem [ref=e245]:
          - link " Gestión Convocatorias" [ref=e246] [cursor=pointer]:
            - /url: /portal-admin
            - generic [ref=e247]: 
            - generic [ref=e248]: Gestión Convocatorias
      - generic [ref=e249]: Mi Familia
      - list [ref=e250]:
        - listitem [ref=e251]:
          - link " Portal de Padres" [ref=e252] [cursor=pointer]:
            - /url: /padres
            - generic [ref=e253]: 
            - generic [ref=e254]: Portal de Padres
        - listitem [ref=e255]:
          - link " Mi Progreso" [ref=e256] [cursor=pointer]:
            - /url: /mi-progreso
            - generic [ref=e257]: 
            - generic [ref=e258]: Mi Progreso
        - listitem [ref=e259]:
          - link " Comunicados" [ref=e260] [cursor=pointer]:
            - /url: /comunicados
            - generic [ref=e261]: 
            - generic [ref=e262]: Comunicados
      - generic [ref=e263]: Inteligencia
      - list [ref=e264]:
        - listitem [ref=e265]:
          - link " Dashboards BI" [ref=e266] [cursor=pointer]:
            - /url: /bi
            - generic [ref=e267]: 
            - generic [ref=e268]: Dashboards BI
        - listitem [ref=e269]:
          - link " Grade Analytics" [ref=e270] [cursor=pointer]:
            - /url: /grade-analytics
            - generic [ref=e271]: 
            - generic [ref=e272]: Grade Analytics
        - listitem [ref=e273]:
          - link " Asistente IA + Datos" [ref=e274] [cursor=pointer]:
            - /url: /ia
            - generic [ref=e275]: 
            - generic [ref=e276]: Asistente IA + Datos
        - listitem [ref=e277]:
          - link " Eval. Docente 360°" [ref=e278] [cursor=pointer]:
            - /url: /eval-docente
            - generic [ref=e279]: 
            - generic [ref=e280]: Eval. Docente 360°
        - listitem [ref=e281]:
          - link " Learning Paths" [ref=e282] [cursor=pointer]:
            - /url: /learning-paths
            - generic [ref=e283]: 
            - generic [ref=e284]: Learning Paths
      - generic [ref=e285]: Reportes
      - list [ref=e286]:
        - listitem [ref=e287]:
          - link " Generador de Reportes" [ref=e288] [cursor=pointer]:
            - /url: /reportes
            - generic [ref=e289]: 
            - generic [ref=e290]: Generador de Reportes
        - listitem [ref=e291]:
          - link " Certificados Digitales" [ref=e292] [cursor=pointer]:
            - /url: /certificados
            - generic [ref=e293]: 
            - generic [ref=e294]: Certificados Digitales
        - listitem [ref=e295]:
          - link " Expediente Digital" [ref=e296] [cursor=pointer]:
            - /url: /expediente-doc
            - generic [ref=e297]: 
            - generic [ref=e298]: Expediente Digital
      - generic [ref=e299]: Sistema
      - list [ref=e300]:
        - listitem [ref=e301]:
          - link " Monitor del Sistema" [ref=e302] [cursor=pointer]:
            - /url: /monitor
            - generic [ref=e303]: 
            - generic [ref=e304]: Monitor del Sistema
        - listitem [ref=e305]:
          - link " Administración" [ref=e306] [cursor=pointer]:
            - /url: /admin
            - generic [ref=e307]: 
            - generic [ref=e308]: Administración
      - generic [ref=e309]: Ayuda
      - list [ref=e310]:
        - listitem [ref=e311]:
          - link " Manual de Usuario" [ref=e312] [cursor=pointer]:
            - /url: /ayuda
            - generic [ref=e313]: 
            - generic [ref=e314]: Manual de Usuario
    - main [ref=e315]:
      - navigation "Breadcrumb" [ref=e318]:
        - list [ref=e319]:
          - listitem [ref=e320]:
            - link "Home" [ref=e321] [cursor=pointer]:
              - /url: /
          - listitem [ref=e322]:
            - generic [ref=e323]: 
            - generic [ref=e324]: Administración
      - generic [ref=e325]:
        - generic:
          - alertdialog
        - generic [ref=e326]:
          - heading "Administración" [level=2] [ref=e327]
          - paragraph [ref=e328]: Gestión de usuarios, ciclos, planteles, variables del sistema y catálogos
        - generic [ref=e329]:
          - generic [ref=e330]: 
          - generic [ref=e331]: Vista global — todos los planteles. Selecciona un plantel en la barra superior para filtrar Usuarios y Grupos.
        - generic [ref=e332]:
          - generic [ref=e333]:
            - tablist [ref=e335]:
              - tab " Usuarios" [ref=e336] [cursor=pointer]:
                - generic [ref=e337]: 
                - text: Usuarios
              - tab " Roles" [ref=e338] [cursor=pointer]:
                - generic [ref=e339]: 
                - text: Roles
              - tab " Menús" [ref=e340] [cursor=pointer]:
                - generic [ref=e341]: 
                - text: Menús
              - tab " Permisos" [ref=e342] [cursor=pointer]:
                - generic [ref=e343]: 
                - text: Permisos
              - tab " Ciclos Escolares" [ref=e344] [cursor=pointer]:
                - generic [ref=e345]: 
                - text: Ciclos Escolares
              - tab " Planteles" [ref=e346] [cursor=pointer]:
                - generic [ref=e347]: 
                - text: Planteles
              - tab " Grupos" [active] [selected] [ref=e348] [cursor=pointer]:
                - generic [ref=e349]: 
                - text: Grupos
              - tab " Variables del Sistema" [ref=e350] [cursor=pointer]:
                - generic [ref=e351]: 
                - text: Variables del Sistema
              - tab " Reglas de Promoción" [ref=e352] [cursor=pointer]:
                - generic [ref=e353]: 
                - text: Reglas de Promoción
              - tab " Franjas Horarias" [ref=e354] [cursor=pointer]:
                - generic [ref=e355]: 
                - text: Franjas Horarias
              - tab " Eval. Cualitativa" [ref=e356] [cursor=pointer]:
                - generic [ref=e357]: 
                - text: Eval. Cualitativa
              - tab " Catálogos" [ref=e358] [cursor=pointer]:
                - generic [ref=e359]: 
                - text: Catálogos
              - tab " Geográficos" [ref=e360] [cursor=pointer]:
                - generic [ref=e361]: 
                - text: Geográficos
              - tab " Marca / Identidad" [ref=e362] [cursor=pointer]:
                - generic [ref=e363]: 
                - text: Marca / Identidad
              - tab " Auditoría" [ref=e364] [cursor=pointer]:
                - generic [ref=e365]: 
                - text: Auditoría
            - button "Siguiente" [ref=e366] [cursor=pointer]:
              - img [ref=e367]
          - text:                                                                
          - tabpanel " Grupos" [ref=e369]:
            - generic [ref=e370]:
              - generic [ref=e371] [cursor=pointer]:
                - combobox "Filtrar por ciclo" [ref=e372]
                - img [ref=e373]
                - button "dropdown trigger" [ref=e375]:
                  - img [ref=e376]
              - button " Nuevo grupo" [ref=e379] [cursor=pointer]:
                - generic [ref=e380]: 
                - generic [ref=e381]: Nuevo grupo
            - generic [ref=e383]:
              - generic [ref=e384]:
                - generic [ref=e385]:
                  - button "Mostrar u ocultar columnas de la tabla" [ref=e387] [cursor=pointer]:
                    - generic [ref=e388]: 
                  - button "Descargar datos como archivo CSV" [ref=e390] [cursor=pointer]:
                    - generic [ref=e391]: 
                - generic [ref=e393]: 72 registro(s)
              - generic [ref=e394]:
                - table [ref=e396]:
                  - rowgroup [ref=e397]:
                    - row "Nivel / Grado Grupo Ocupación Turno Estado Acciones" [ref=e398]:
                      - columnheader "Nivel / Grado" [ref=e399] [cursor=pointer]:
                        - generic [ref=e400]:
                          - generic [ref=e401]: Nivel / Grado
                          - img [ref=e403]
                        - generic [ref=e410]:
                          - combobox "Filtrar..." [ref=e411]
                          - button [ref=e412]:
                            - img [ref=e413]
                      - columnheader "Grupo" [ref=e415] [cursor=pointer]:
                        - generic [ref=e416]:
                          - generic [ref=e417]: Grupo
                          - img [ref=e419]
                        - generic [ref=e426]:
                          - combobox "Filtrar..." [ref=e427]
                          - button [ref=e428]:
                            - img [ref=e429]
                      - columnheader "Ocupación" [ref=e431] [cursor=pointer]:
                        - generic [ref=e433]: Ocupación
                      - columnheader "Turno" [ref=e434] [cursor=pointer]:
                        - generic [ref=e435]:
                          - generic [ref=e436]: Turno
                          - img [ref=e438]
                        - generic [ref=e445]:
                          - combobox "Filtrar..." [ref=e446]
                          - button [ref=e447]:
                            - img [ref=e448]
                      - columnheader "Estado" [ref=e450] [cursor=pointer]:
                        - generic [ref=e451]:
                          - generic [ref=e452]: Estado
                          - img [ref=e454]
                        - generic [ref=e461]:
                          - combobox "Filtrar..." [ref=e462]
                          - button [ref=e463]:
                            - img [ref=e464]
                      - columnheader "Acciones" [ref=e466]:
                        - strong [ref=e467]: Acciones
                  - rowgroup [ref=e468]:
                    - row "PREPARATORIA Primer semestre A 0 / 30 MATUTINO Activo Editar este registro" [ref=e469] [cursor=pointer]:
                      - cell "PREPARATORIA Primer semestre" [ref=e470]
                      - cell "A" [ref=e471]
                      - cell "0 / 30" [ref=e472]
                      - cell "MATUTINO" [ref=e473]
                      - cell "Activo" [ref=e474]
                      - cell "Editar este registro" [ref=e475]:
                        - button "Editar este registro" [ref=e477]:
                          - generic [ref=e478]: 
                    - row "PREPARATORIA Primer semestre A 0 / 30 MATUTINO Activo Editar este registro" [ref=e479] [cursor=pointer]:
                      - cell "PREPARATORIA Primer semestre" [ref=e480]
                      - cell "A" [ref=e481]
                      - cell "0 / 30" [ref=e482]
                      - cell "MATUTINO" [ref=e483]
                      - cell "Activo" [ref=e484]
                      - cell "Editar este registro" [ref=e485]:
                        - button "Editar este registro" [ref=e487]:
                          - generic [ref=e488]: 
                    - row "PREPARATORIA Primer semestre B 0 / 30 MATUTINO Activo Editar este registro" [ref=e489] [cursor=pointer]:
                      - cell "PREPARATORIA Primer semestre" [ref=e490]
                      - cell "B" [ref=e491]
                      - cell "0 / 30" [ref=e492]
                      - cell "MATUTINO" [ref=e493]
                      - cell "Activo" [ref=e494]
                      - cell "Editar este registro" [ref=e495]:
                        - button "Editar este registro" [ref=e497]:
                          - generic [ref=e498]: 
                    - row "PREPARATORIA Primer semestre B 0 / 30 MATUTINO Activo Editar este registro" [ref=e499] [cursor=pointer]:
                      - cell "PREPARATORIA Primer semestre" [ref=e500]
                      - cell "B" [ref=e501]
                      - cell "0 / 30" [ref=e502]
                      - cell "MATUTINO" [ref=e503]
                      - cell "Activo" [ref=e504]
                      - cell "Editar este registro" [ref=e505]:
                        - button "Editar este registro" [ref=e507]:
                          - generic [ref=e508]: 
                    - row "PREPARATORIA Segundo semestre A 0 / 30 MATUTINO Activo Editar este registro" [ref=e509] [cursor=pointer]:
                      - cell "PREPARATORIA Segundo semestre" [ref=e510]
                      - cell "A" [ref=e511]
                      - cell "0 / 30" [ref=e512]
                      - cell "MATUTINO" [ref=e513]
                      - cell "Activo" [ref=e514]
                      - cell "Editar este registro" [ref=e515]:
                        - button "Editar este registro" [ref=e517]:
                          - generic [ref=e518]: 
                    - row "PREPARATORIA Segundo semestre A 0 / 30 MATUTINO Activo Editar este registro" [ref=e519] [cursor=pointer]:
                      - cell "PREPARATORIA Segundo semestre" [ref=e520]
                      - cell "A" [ref=e521]
                      - cell "0 / 30" [ref=e522]
                      - cell "MATUTINO" [ref=e523]
                      - cell "Activo" [ref=e524]
                      - cell "Editar este registro" [ref=e525]:
                        - button "Editar este registro" [ref=e527]:
                          - generic [ref=e528]: 
                    - row "PREPARATORIA Segundo semestre B 0 / 30 MATUTINO Activo Editar este registro" [ref=e529] [cursor=pointer]:
                      - cell "PREPARATORIA Segundo semestre" [ref=e530]
                      - cell "B" [ref=e531]
                      - cell "0 / 30" [ref=e532]
                      - cell "MATUTINO" [ref=e533]
                      - cell "Activo" [ref=e534]
                      - cell "Editar este registro" [ref=e535]:
                        - button "Editar este registro" [ref=e537]:
                          - generic [ref=e538]: 
                    - row "PREPARATORIA Segundo semestre B 0 / 30 MATUTINO Activo Editar este registro" [ref=e539] [cursor=pointer]:
                      - cell "PREPARATORIA Segundo semestre" [ref=e540]
                      - cell "B" [ref=e541]
                      - cell "0 / 30" [ref=e542]
                      - cell "MATUTINO" [ref=e543]
                      - cell "Activo" [ref=e544]
                      - cell "Editar este registro" [ref=e545]:
                        - button "Editar este registro" [ref=e547]:
                          - generic [ref=e548]: 
                    - row "PREPARATORIA Tercer semestre A 0 / 30 MATUTINO Activo Editar este registro" [ref=e549] [cursor=pointer]:
                      - cell "PREPARATORIA Tercer semestre" [ref=e550]
                      - cell "A" [ref=e551]
                      - cell "0 / 30" [ref=e552]
                      - cell "MATUTINO" [ref=e553]
                      - cell "Activo" [ref=e554]
                      - cell "Editar este registro" [ref=e555]:
                        - button "Editar este registro" [ref=e557]:
                          - generic [ref=e558]: 
                    - row "PREPARATORIA Tercer semestre B 0 / 30 MATUTINO Activo Editar este registro" [ref=e559] [cursor=pointer]:
                      - cell "PREPARATORIA Tercer semestre" [ref=e560]
                      - cell "B" [ref=e561]
                      - cell "0 / 30" [ref=e562]
                      - cell "MATUTINO" [ref=e563]
                      - cell "Activo" [ref=e564]
                      - cell "Editar este registro" [ref=e565]:
                        - button "Editar este registro" [ref=e567]:
                          - generic [ref=e568]: 
                    - row "PREPARATORIA Cuarto semestre A 0 / 30 MATUTINO Activo Editar este registro" [ref=e569] [cursor=pointer]:
                      - cell "PREPARATORIA Cuarto semestre" [ref=e570]
                      - cell "A" [ref=e571]
                      - cell "0 / 30" [ref=e572]
                      - cell "MATUTINO" [ref=e573]
                      - cell "Activo" [ref=e574]
                      - cell "Editar este registro" [ref=e575]:
                        - button "Editar este registro" [ref=e577]:
                          - generic [ref=e578]: 
                    - row "PREPARATORIA Cuarto semestre B 0 / 30 MATUTINO Activo Editar este registro" [ref=e579] [cursor=pointer]:
                      - cell "PREPARATORIA Cuarto semestre" [ref=e580]
                      - cell "B" [ref=e581]
                      - cell "0 / 30" [ref=e582]
                      - cell "MATUTINO" [ref=e583]
                      - cell "Activo" [ref=e584]
                      - cell "Editar este registro" [ref=e585]:
                        - button "Editar este registro" [ref=e587]:
                          - generic [ref=e588]: 
                    - row "PRIMARIA Primer grado A 0 / 30 MATUTINO Activo Editar este registro" [ref=e589] [cursor=pointer]:
                      - cell "PRIMARIA Primer grado" [ref=e590]
                      - cell "A" [ref=e591]
                      - cell "0 / 30" [ref=e592]
                      - cell "MATUTINO" [ref=e593]
                      - cell "Activo" [ref=e594]
                      - cell "Editar este registro" [ref=e595]:
                        - button "Editar este registro" [ref=e597]:
                          - generic [ref=e598]: 
                    - row "PRIMARIA Primer grado A 0 / 30 MATUTINO Activo Editar este registro" [ref=e599] [cursor=pointer]:
                      - cell "PRIMARIA Primer grado" [ref=e600]
                      - cell "A" [ref=e601]
                      - cell "0 / 30" [ref=e602]
                      - cell "MATUTINO" [ref=e603]
                      - cell "Activo" [ref=e604]
                      - cell "Editar este registro" [ref=e605]:
                        - button "Editar este registro" [ref=e607]:
                          - generic [ref=e608]: 
                    - row "PRIMARIA Primer grado A 0 / 30 MATUTINO Activo Editar este registro" [ref=e609] [cursor=pointer]:
                      - cell "PRIMARIA Primer grado" [ref=e610]
                      - cell "A" [ref=e611]
                      - cell "0 / 30" [ref=e612]
                      - cell "MATUTINO" [ref=e613]
                      - cell "Activo" [ref=e614]
                      - cell "Editar este registro" [ref=e615]:
                        - button "Editar este registro" [ref=e617]:
                          - generic [ref=e618]: 
                    - row "PRIMARIA Primer grado B 0 / 30 MATUTINO Activo Editar este registro" [ref=e619] [cursor=pointer]:
                      - cell "PRIMARIA Primer grado" [ref=e620]
                      - cell "B" [ref=e621]
                      - cell "0 / 30" [ref=e622]
                      - cell "MATUTINO" [ref=e623]
                      - cell "Activo" [ref=e624]
                      - cell "Editar este registro" [ref=e625]:
                        - button "Editar este registro" [ref=e627]:
                          - generic [ref=e628]: 
                    - row "PRIMARIA Primer grado B 0 / 30 MATUTINO Activo Editar este registro" [ref=e629] [cursor=pointer]:
                      - cell "PRIMARIA Primer grado" [ref=e630]
                      - cell "B" [ref=e631]
                      - cell "0 / 30" [ref=e632]
                      - cell "MATUTINO" [ref=e633]
                      - cell "Activo" [ref=e634]
                      - cell "Editar este registro" [ref=e635]:
                        - button "Editar este registro" [ref=e637]:
                          - generic [ref=e638]: 
                    - row "PRIMARIA Primer grado B 0 / 30 MATUTINO Activo Editar este registro" [ref=e639] [cursor=pointer]:
                      - cell "PRIMARIA Primer grado" [ref=e640]
                      - cell "B" [ref=e641]
                      - cell "0 / 30" [ref=e642]
                      - cell "MATUTINO" [ref=e643]
                      - cell "Activo" [ref=e644]
                      - cell "Editar este registro" [ref=e645]:
                        - button "Editar este registro" [ref=e647]:
                          - generic [ref=e648]: 
                    - row "PRIMARIA Segundo grado A 0 / 30 MATUTINO Activo Editar este registro" [ref=e649] [cursor=pointer]:
                      - cell "PRIMARIA Segundo grado" [ref=e650]
                      - cell "A" [ref=e651]
                      - cell "0 / 30" [ref=e652]
                      - cell "MATUTINO" [ref=e653]
                      - cell "Activo" [ref=e654]
                      - cell "Editar este registro" [ref=e655]:
                        - button "Editar este registro" [ref=e657]:
                          - generic [ref=e658]: 
                    - row "PRIMARIA Segundo grado A 0 / 30 MATUTINO Activo Editar este registro" [ref=e659] [cursor=pointer]:
                      - cell "PRIMARIA Segundo grado" [ref=e660]
                      - cell "A" [ref=e661]
                      - cell "0 / 30" [ref=e662]
                      - cell "MATUTINO" [ref=e663]
                      - cell "Activo" [ref=e664]
                      - cell "Editar este registro" [ref=e665]:
                        - button "Editar este registro" [ref=e667]:
                          - generic [ref=e668]: 
                - generic [ref=e669]:
                  - button "Primera página":
                    - img
                  - button [disabled]:
                    - img
                  - generic [ref=e670]:
                    - button "Página 1" [ref=e671] [cursor=pointer]: "1"
                    - button "Página 2" [ref=e672] [cursor=pointer]: "2"
                    - button "Página 3" [ref=e673] [cursor=pointer]: "3"
                    - button "Página 4" [ref=e674] [cursor=pointer]: "4"
                  - button "Página siguiente" [ref=e675] [cursor=pointer]:
                    - img [ref=e676]
                  - button "Última página" [ref=e678] [cursor=pointer]:
                    - img [ref=e679]
                  - generic [ref=e681] [cursor=pointer]:
                    - combobox "Filas por página" [ref=e682]: "20"
                    - button "dropdown trigger" [ref=e683]:
                      - img [ref=e684]
          - text:          
```

# Test source

```ts
  248 | 
  249 |     // Navega a Administración > Grupos
  250 |     await page.click('text=Administración');
  251 |     await page.waitForTimeout(1_000);
  252 | 
  253 |     const gruposTab = page.locator('[role="tab"]:has-text("Grupos")').first();
  254 |     if (await gruposTab.isVisible()) {
  255 |       await gruposTab.click();
  256 |     }
  257 |     await page.waitForTimeout(1_200);
  258 | 
  259 |     // Abre nuevo grupo
  260 |     const nuevoBtn = page.locator('[data-testid="btn-nuevo-grupo"]');
  261 |     await nuevoBtn.click();
  262 |     await page.waitForTimeout(1_200);
  263 | 
  264 |     // Completa formulario
  265 |     const nombreInput = page.locator('[data-testid="input-nombre-grupo"]');
  266 |     const capacidadInput = page.locator('[data-testid="input-capacidad"]');
  267 |     const turnoSelect = page.locator('[data-testid="select-turno"]');
  268 |     const cicloSelect = page.locator('[data-testid="select-ciclo"]');
  269 |     const gradoSelect = page.locator('[data-testid="select-grado"]');
  270 |     const guardarBtn = page.locator('[data-testid="btn-guardar"]');
  271 | 
  272 |     // Rellena nombre
  273 |     await nombreInput.fill('TestGrp' + Date.now().toString().slice(-3));
  274 |     await page.waitForTimeout(300);
  275 | 
  276 |     // Rellena capacidad
  277 |     await capacidadInput.fill('30');
  278 |     await page.waitForTimeout(300);
  279 | 
  280 |     // Selecciona turno
  281 |     await turnoSelect.click();
  282 |     await page.waitForTimeout(600);
  283 |     const turnoOption = page.locator('.p-select-option, [role="option"]').filter({ hasText: /MATUTINO/ }).first();
  284 |     if (await turnoOption.isVisible()) {
  285 |       await turnoOption.click();
  286 |       await page.waitForTimeout(600);
  287 |     }
  288 | 
  289 |     // Selecciona ciclo Primaria
  290 |     await cicloSelect.click();
  291 |     await page.waitForTimeout(600);
  292 |     const primariaCiclo = page.locator('.p-select-option, [role="option"]').filter({ hasText: /Primaria/ }).first();
  293 |     await primariaCiclo.click();
  294 |     await page.waitForTimeout(800);
  295 | 
  296 |     // Selecciona grado Primaria
  297 |     await gradoSelect.click();
  298 |     await page.waitForTimeout(600);
  299 |     const primarioGrado = page.locator('.p-select-option, [role="option"]').filter({ hasText: /Primaria.*Primer/ }).first();
  300 |     if (await primarioGrado.isVisible()) {
  301 |       await primarioGrado.click();
  302 |       await page.waitForTimeout(600);
  303 |     }
  304 | 
  305 |     // Intenta guardar e intercepta respuesta
  306 |     let postStatus: number | null = null;
  307 |     page.on('response', async (response) => {
  308 |       if (response.url().includes('/api/v1/admin/grupos') && response.request().method() === 'POST') {
  309 |         postStatus = response.status();
  310 |       }
  311 |     });
  312 | 
  313 |     await guardarBtn.click();
  314 |     await page.waitForTimeout(2_000);
  315 | 
  316 |     if (postStatus) {
  317 |       expect([201, 400]).toContain(postStatus);
  318 |       if (postStatus === 201) {
  319 |         console.log('[INFO] GRP-CASCADE-05: POST exitoso (201), cascada válida');
  320 |       } else {
  321 |         console.log('[WARNING] GRP-CASCADE-05: POST rechazado (400), validación backend activa');
  322 |       }
  323 |     } else {
  324 |       console.log('[INFO] GRP-CASCADE-05: Sin POST interceptado, pero flujo completo');
  325 |     }
  326 |   });
  327 | 
  328 |   test('GRP-CASCADE-06 | Sin errores en consola durante cascada @smoke', async ({ page }) => {
  329 |     const apiResponses = attachApiMonitor(page);
  330 |     const getErrors = attachConsoleMonitor(page);
  331 | 
  332 |     await new LoginPage(page).login(USERS.ADMIN_GLOBAL);
  333 |     await page.goto('/dashboard', { waitUntil: 'domcontentloaded' });
  334 |     await page.waitForTimeout(1_500);
  335 | 
  336 |     // Navega a Administración > Grupos
  337 |     await page.click('text=Administración');
  338 |     await page.waitForTimeout(1_000);
  339 | 
  340 |     const gruposTab = page.locator('[role="tab"]:has-text("Grupos")').first();
  341 |     if (await gruposTab.isVisible()) {
  342 |       await gruposTab.click();
  343 |     }
  344 |     await page.waitForTimeout(1_200);
  345 | 
  346 |     // Abre nuevo grupo
  347 |     const nuevoBtn = page.locator('[data-testid="btn-nuevo-grupo"]');
> 348 |     await nuevoBtn.click();
      |                    ^ Error: locator.click: Test timeout of 30000ms exceeded.
  349 |     await page.waitForTimeout(1_200);
  350 | 
  351 |     // Monitorea errores en consola
  352 |     const errors: string[] = [];
  353 |     page.on('console', (msg) => {
  354 |       if (msg.type() === 'error') {
  355 |         errors.push(msg.text());
  356 |       }
  357 |     });
  358 | 
  359 |     // Realiza cascada
  360 |     const cicloSelect = page.locator('[data-testid="select-ciclo"]');
  361 |     const gradoSelect = page.locator('[data-testid="select-grado"]');
  362 | 
  363 |     await cicloSelect.click();
  364 |     await page.waitForTimeout(600);
  365 |     const primariaCiclo = page.locator('.p-select-option, [role="option"]').filter({ hasText: /Primaria/ }).first();
  366 |     await primariaCiclo.click();
  367 |     await page.waitForTimeout(800);
  368 | 
  369 |     await gradoSelect.click();
  370 |     await page.waitForTimeout(600);
  371 |     const primarioGrado = page.locator('.p-select-option, [role="option"]').filter({ hasText: /Primaria/ }).first();
  372 |     if (await primarioGrado.isVisible()) {
  373 |       await primarioGrado.click();
  374 |       await page.waitForTimeout(600);
  375 |     }
  376 | 
  377 |     if (errors.length === 0) {
  378 |       console.log('[INFO] GRP-CASCADE-06: Cascada sin errores de consola ✓');
  379 |     } else {
  380 |       console.warn('[FINDING][P2] GRP-CASCADE-06: Errores en consola: ' + errors.join(', '));
  381 |     }
  382 | 
  383 |     expect(errors.length).toBe(0);
  384 |   });
  385 | });
  386 | 
  387 | test.describe('C. Validación Cascada — Estado consistente', () => {
  388 | 
  389 |   test('GRP-CASCADE-07 | TypeScript compila sin errores @smoke', async ({ page }) => {
  390 |     // Verificación estática: el código compila
  391 |     await page.goto('/dashboard', { waitUntil: 'domcontentloaded' });
  392 |     const isAngularRunning = await page.evaluate(() => {
  393 |       return (window as any).ng !== undefined;
  394 |     });
  395 |     expect(isAngularRunning).toBeTruthy();
  396 |     console.log('[INFO] GRP-CASCADE-07: Angular inicializado correctamente');
  397 |   });
  398 | });
  399 | 
```