# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: 02-alumnos.spec.ts >> B. Errores de validación >> ALU-12 | CURP de 5 chars → warning longitud
- Location: e2e/tests/02-alumnos.spec.ts:119:7

# Error details

```
Test timeout of 30000ms exceeded.
```

```
Error: locator.click: Test timeout of 30000ms exceeded.
Call log:
  - waiting for locator('button:has-text("Crear alumno"), button:has-text("Guardar"), [data-testid="btn-guardar"]')
    - locator resolved to <button disabled pc260="" pc261="" pc262="" data-p="" pripple="" type="button" autofocus="true" data-pc-name="button" data-p-disabled="true" data-pc-section="root" class="p-ripple p-button p-component">…</button>
  - attempting click action
    2 × waiting for element to be visible, enabled and stable
      - element is not enabled
    - retrying click action
    - waiting 20ms
    2 × waiting for element to be visible, enabled and stable
      - element is not enabled
    - retrying click action
      - waiting 100ms
    53 × waiting for element to be visible, enabled and stable
       - element is not enabled
     - retrying click action
       - waiting 500ms

```

# Page snapshot

```yaml
- generic [active] [ref=e1]:
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
        - button "Cuenta de Test COORDINADOR_ACADEMICO" [ref=e45] [cursor=pointer]:
          - generic [ref=e46]: T
          - generic [ref=e47]:
            - generic [ref=e48]: Test COORDINADOR_ACADEMICO
            - generic [ref=e49]: Coord. Académico
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
            - link " Profesores" [ref=e70] [cursor=pointer]:
              - /url: /profesores
              - generic [ref=e71]: 
              - generic [ref=e72]: Profesores
          - listitem [ref=e73]:
            - link " Grupos" [ref=e74] [cursor=pointer]:
              - /url: /grupos
              - generic [ref=e75]: 
              - generic [ref=e76]: Grupos
          - listitem [ref=e77]:
            - link " Aulas" [ref=e78] [cursor=pointer]:
              - /url: /aulas
              - generic [ref=e79]: 
              - generic [ref=e80]: Aulas
          - listitem [ref=e81]:
            - link " Planes de Estudio" [ref=e82] [cursor=pointer]:
              - /url: /planes-estudio
              - generic [ref=e83]: 
              - generic [ref=e84]: Planes de Estudio
          - listitem [ref=e85]:
            - link " Calificaciones" [ref=e86] [cursor=pointer]:
              - /url: /calificaciones
              - generic [ref=e87]: 
              - generic [ref=e88]: Calificaciones
          - listitem [ref=e89]:
            - link " Evaluaciones" [ref=e90] [cursor=pointer]:
              - /url: /evaluaciones
              - generic [ref=e91]: 
              - generic [ref=e92]: Evaluaciones
          - listitem [ref=e93]:
            - link " Asistencias" [ref=e94] [cursor=pointer]:
              - /url: /asistencias
              - generic [ref=e95]: 
              - generic [ref=e96]: Asistencias
          - listitem [ref=e97]:
            - link " Tareas" [ref=e98] [cursor=pointer]:
              - /url: /tareas
              - generic [ref=e99]: 
              - generic [ref=e100]: Tareas
          - listitem [ref=e101]:
            - link " Planeación" [ref=e102] [cursor=pointer]:
              - /url: /planeacion
              - generic [ref=e103]: 
              - generic [ref=e104]: Planeación
        - generic [ref=e105]: Operaciones
        - list [ref=e106]:
          - listitem [ref=e107]:
            - link " Horarios" [ref=e108] [cursor=pointer]:
              - /url: /horarios
              - generic [ref=e109]: 
              - generic [ref=e110]: Horarios
          - listitem [ref=e111]:
            - link " Calendario Escolar" [ref=e112] [cursor=pointer]:
              - /url: /calendario
              - generic [ref=e113]: 
              - generic [ref=e114]: Calendario Escolar
          - listitem [ref=e115]:
            - link " Conducta" [ref=e116] [cursor=pointer]:
              - /url: /conducta
              - generic [ref=e117]: 
              - generic [ref=e118]: Conducta
          - listitem [ref=e119]:
            - link " Expediente Médico" [ref=e120] [cursor=pointer]:
              - /url: /medico
              - generic [ref=e121]: 
              - generic [ref=e122]: Expediente Médico
          - listitem [ref=e123]:
            - link " Condiciones Crónicas" [ref=e124] [cursor=pointer]:
              - /url: /condiciones-cronicas
              - generic [ref=e125]: 
              - generic [ref=e126]: Condiciones Crónicas
          - listitem [ref=e127]:
            - link " Justificaciones Faltas" [ref=e128] [cursor=pointer]:
              - /url: /justificaciones
              - generic [ref=e129]: 
              - generic [ref=e130]: Justificaciones Faltas
          - listitem [ref=e131]:
            - link " Movilidad Estudiantil" [ref=e132] [cursor=pointer]:
              - /url: /movilidad
              - generic [ref=e133]: 
              - generic [ref=e134]: Movilidad Estudiantil
          - listitem [ref=e135]:
            - link " Biblioteca" [ref=e136] [cursor=pointer]:
              - /url: /biblioteca
              - generic [ref=e137]: 
              - generic [ref=e138]: Biblioteca
          - listitem [ref=e139]:
            - link " Formato 911 SEP" [ref=e140] [cursor=pointer]:
              - /url: /estadistica-911
              - generic [ref=e141]: 
              - generic [ref=e142]: Formato 911 SEP
          - listitem [ref=e143]:
            - link " Kardex UAEMEX" [ref=e144] [cursor=pointer]:
              - /url: /kardex
              - generic [ref=e145]: 
              - generic [ref=e146]: Kardex UAEMEX
          - listitem [ref=e147]:
            - link " Acta Evaluación UAEMEX" [ref=e148] [cursor=pointer]:
              - /url: /acta-evaluacion
              - generic [ref=e149]: 
              - generic [ref=e150]: Acta Evaluación UAEMEX
          - listitem [ref=e151]:
            - link " Optativas" [ref=e152] [cursor=pointer]:
              - /url: /optativas
              - generic [ref=e153]: 
              - generic [ref=e154]: Optativas
          - listitem [ref=e155]:
            - link " Admisión" [ref=e156] [cursor=pointer]:
              - /url: /admision
              - generic [ref=e157]: 
              - generic [ref=e158]: Admisión
        - generic [ref=e159]: Comunicación
        - list [ref=e160]:
          - listitem [ref=e161]:
            - link " Comunicados" [ref=e162] [cursor=pointer]:
              - /url: /comunicados
              - generic [ref=e163]: 
              - generic [ref=e164]: Comunicados
          - listitem [ref=e165]:
            - link " Foros y Anuncios" [ref=e166] [cursor=pointer]:
              - /url: /foros
              - generic [ref=e167]: 
              - generic [ref=e168]: Foros y Anuncios
          - listitem [ref=e169]:
            - link " Encuestas" [ref=e170] [cursor=pointer]:
              - /url: /encuestas
              - generic [ref=e171]: 
              - generic [ref=e172]: Encuestas
          - listitem [ref=e173]:
            - link " Videoconferencias" [ref=e174] [cursor=pointer]:
              - /url: /videoconferencias
              - generic [ref=e175]: 
              - generic [ref=e176]: Videoconferencias
        - generic [ref=e177]: Gradebook
        - list [ref=e178]:
          - listitem [ref=e179]:
            - link " Gradebook" [ref=e180] [cursor=pointer]:
              - /url: /gradebook
              - generic [ref=e181]: 
              - generic [ref=e182]: Gradebook
          - listitem [ref=e183]:
            - link " Mi Progreso" [ref=e184] [cursor=pointer]:
              - /url: /mi-progreso
              - generic [ref=e185]: 
              - generic [ref=e186]: Mi Progreso
          - listitem [ref=e187]:
            - link " Ponderaciones" [ref=e188] [cursor=pointer]:
              - /url: /ponderacion-config
              - generic [ref=e189]: 
              - generic [ref=e190]: Ponderaciones
        - generic [ref=e191]: Recursos
        - list [ref=e192]:
          - listitem [ref=e193]:
            - link " Rúbricas" [ref=e194] [cursor=pointer]:
              - /url: /rubricas
              - generic [ref=e195]: 
              - generic [ref=e196]: Rúbricas
          - listitem [ref=e197]:
            - link " Insignias" [ref=e198] [cursor=pointer]:
              - /url: /badges
              - generic [ref=e199]: 
              - generic [ref=e200]: Insignias
          - listitem [ref=e201]:
            - link " Portal Alumno" [ref=e202] [cursor=pointer]:
              - /url: /portal
              - generic [ref=e203]: 
              - generic [ref=e204]: Portal Alumno
          - listitem [ref=e205]:
            - link " Contenido H5P" [ref=e206] [cursor=pointer]:
              - /url: /h5p
              - generic [ref=e207]: 
              - generic [ref=e208]: Contenido H5P
        - generic [ref=e209]: Inteligencia
        - list [ref=e210]:
          - listitem [ref=e211]:
            - link " Dashboards BI" [ref=e212] [cursor=pointer]:
              - /url: /bi
              - generic [ref=e213]: 
              - generic [ref=e214]: Dashboards BI
          - listitem [ref=e215]:
            - link " Grade Analytics" [ref=e216] [cursor=pointer]:
              - /url: /grade-analytics
              - generic [ref=e217]: 
              - generic [ref=e218]: Grade Analytics
          - listitem [ref=e219]:
            - link " Asistente IA + Datos" [ref=e220] [cursor=pointer]:
              - /url: /ia
              - generic [ref=e221]: 
              - generic [ref=e222]: Asistente IA + Datos
          - listitem [ref=e223]:
            - link " Eval. Docente 360°" [ref=e224] [cursor=pointer]:
              - /url: /eval-docente
              - generic [ref=e225]: 
              - generic [ref=e226]: Eval. Docente 360°
          - listitem [ref=e227]:
            - link " Learning Paths" [ref=e228] [cursor=pointer]:
              - /url: /learning-paths
              - generic [ref=e229]: 
              - generic [ref=e230]: Learning Paths
        - generic [ref=e231]: Reportes
        - list [ref=e232]:
          - listitem [ref=e233]:
            - link " Generador de Reportes" [ref=e234] [cursor=pointer]:
              - /url: /reportes
              - generic [ref=e235]: 
              - generic [ref=e236]: Generador de Reportes
          - listitem [ref=e237]:
            - link " Certificados Digitales" [ref=e238] [cursor=pointer]:
              - /url: /certificados
              - generic [ref=e239]: 
              - generic [ref=e240]: Certificados Digitales
          - listitem [ref=e241]:
            - link " Expediente Digital" [ref=e242] [cursor=pointer]:
              - /url: /expediente-doc
              - generic [ref=e243]: 
              - generic [ref=e244]: Expediente Digital
        - generic [ref=e245]: Ayuda
        - list [ref=e246]:
          - listitem [ref=e247]:
            - link " Manual de Usuario" [ref=e248] [cursor=pointer]:
              - /url: /ayuda
              - generic [ref=e249]: 
              - generic [ref=e250]: Manual de Usuario
      - main [ref=e251]:
        - navigation "Breadcrumb" [ref=e254]:
          - list [ref=e255]:
            - listitem [ref=e256]:
              - link "Home" [ref=e257] [cursor=pointer]:
                - /url: /
            - listitem [ref=e258]:
              - generic [ref=e259]: 
              - generic [ref=e260]: Alumnos
        - generic [ref=e261]:
          - generic [ref=e262]:
            - generic [ref=e263]:
              - heading "Alumnos" [level=2] [ref=e264]
              - paragraph [ref=e265]: 2,028 alumno(s) registrado(s)
            - generic [ref=e266]:
              - button "" [ref=e269] [cursor=pointer]:
                - generic [ref=e270]: 
              - button " CSV" [ref=e272] [cursor=pointer]:
                - generic [ref=e273]: 
                - generic [ref=e274]: CSV
              - button " Excel" [ref=e276] [cursor=pointer]:
                - generic [ref=e277]: 
                - generic [ref=e278]: Excel
              - generic [ref=e280]:
                - button " Importar CSV/Excel" [ref=e282] [cursor=pointer]:
                  - generic [ref=e283]: 
                  - generic [ref=e284]: Importar CSV/Excel
                - button " Plantilla" [ref=e286] [cursor=pointer]:
                  - generic [ref=e287]: 
                  - generic [ref=e288]: Plantilla
              - button " Asignar grupo" [ref=e290] [cursor=pointer]:
                - generic [ref=e291]: 
                - generic [ref=e292]: Asignar grupo
              - button " Nuevo alumno" [ref=e294] [cursor=pointer]:
                - generic [ref=e295]: 
                - generic [ref=e296]: Nuevo alumno
          - generic [ref=e301]:
            - generic [ref=e302]: 
            - textbox "Buscar alumno..." [ref=e303]
          - generic [ref=e305]:
            - generic [ref=e306]:
              - generic [ref=e307]:
                - button "Mostrar u ocultar columnas de la tabla" [ref=e309] [cursor=pointer]:
                  - generic [ref=e310]: 
                - button "Descargar datos como archivo CSV" [ref=e312] [cursor=pointer]:
                  - generic [ref=e313]: 
              - generic [ref=e315]: 20 registro(s)
            - generic [ref=e316]:
              - table [ref=e318]:
                - rowgroup [ref=e319]:
                  - row "Matrícula Nombre Completo CURP NSS Plantel Nivel Grado Grupo Ingreso Acciones" [ref=e320]:
                    - columnheader "Matrícula" [ref=e321] [cursor=pointer]:
                      - generic [ref=e322]:
                        - generic [ref=e323]: Matrícula
                        - img [ref=e325]
                      - generic [ref=e332]:
                        - combobox "Filtrar..." [ref=e333]
                        - button [ref=e334]:
                          - img [ref=e335]
                    - columnheader "Nombre Completo" [ref=e337] [cursor=pointer]:
                      - generic [ref=e338]:
                        - generic [ref=e339]: Nombre Completo
                        - img [ref=e341]
                      - generic [ref=e348]:
                        - combobox "Filtrar..." [ref=e349]
                        - button [ref=e350]:
                          - img [ref=e351]
                    - columnheader "CURP" [ref=e353] [cursor=pointer]:
                      - generic [ref=e354]:
                        - generic [ref=e355]: CURP
                        - img [ref=e357]
                      - generic [ref=e364]:
                        - combobox "Filtrar..." [ref=e365]
                        - button [ref=e366]:
                          - img [ref=e367]
                    - columnheader "NSS" [ref=e369] [cursor=pointer]:
                      - generic [ref=e371]: NSS
                    - columnheader "Plantel" [ref=e372] [cursor=pointer]:
                      - generic [ref=e373]:
                        - generic [ref=e374]: Plantel
                        - img [ref=e376]
                      - generic [ref=e383]:
                        - combobox "Filtrar..." [ref=e384]
                        - button [ref=e385]:
                          - img [ref=e386]
                    - columnheader "Nivel" [ref=e388] [cursor=pointer]:
                      - generic [ref=e389]:
                        - generic [ref=e390]: Nivel
                        - img [ref=e392]
                      - generic [ref=e399]:
                        - combobox "Filtrar..." [ref=e400]
                        - button [ref=e401]:
                          - img [ref=e402]
                    - columnheader "Grado" [ref=e404] [cursor=pointer]:
                      - generic [ref=e405]:
                        - generic [ref=e406]: Grado
                        - img [ref=e408]
                      - generic [ref=e415]:
                        - combobox "Filtrar..." [ref=e416]
                        - button [ref=e417]:
                          - img [ref=e418]
                    - columnheader "Grupo" [ref=e420] [cursor=pointer]:
                      - generic [ref=e421]:
                        - generic [ref=e422]: Grupo
                        - img [ref=e424]
                      - generic [ref=e431]:
                        - combobox "Filtrar..." [ref=e432]
                        - button [ref=e433]:
                          - img [ref=e434]
                    - columnheader "Ingreso" [ref=e436] [cursor=pointer]:
                      - generic [ref=e437]:
                        - generic [ref=e438]: Ingreso
                        - img [ref=e440]
                    - columnheader "Acciones" [ref=e446]:
                      - strong [ref=e447]: Acciones
                - rowgroup [ref=e448]:
                  - row "MAT-TEPA-00372 Cristian Acosta Romero AORC101225HMCCMR00 Tenancingo PREPARATORIA Segundo semestre B 2026-08-24T00:00:00.000Z Editar este registro" [ref=e449] [cursor=pointer]:
                    - cell "MAT-TEPA-00372" [ref=e450]
                    - cell "Cristian Acosta Romero" [ref=e451]
                    - cell "AORC101225HMCCMR00" [ref=e452]
                    - cell [ref=e453]
                    - cell "Tenancingo" [ref=e454]
                    - cell "PREPARATORIA" [ref=e455]
                    - cell "Segundo semestre" [ref=e456]
                    - cell "B" [ref=e457]
                    - cell "2026-08-24T00:00:00.000Z" [ref=e458]
                    - cell "Editar este registro" [ref=e459]:
                      - button "Editar este registro" [ref=e461]:
                        - generic [ref=e462]: 
                  - row "MAT-TEPA-00274 Hilda Acosta Cantú AOCH110223MMCCNL00 Tenancingo PREPARATORIA Primer semestre B 2026-08-24T00:00:00.000Z Editar este registro" [ref=e463] [cursor=pointer]:
                    - cell "MAT-TEPA-00274" [ref=e464]
                    - cell "Hilda Acosta Cantú" [ref=e465]
                    - cell "AOCH110223MMCCNL00" [ref=e466]
                    - cell [ref=e467]
                    - cell "Tenancingo" [ref=e468]
                    - cell "PREPARATORIA" [ref=e469]
                    - cell "Primer semestre" [ref=e470]
                    - cell "B" [ref=e471]
                    - cell "2026-08-24T00:00:00.000Z" [ref=e472]
                    - cell "Editar este registro" [ref=e473]:
                      - button "Editar este registro" [ref=e475]:
                        - generic [ref=e476]: 
                  - row "MAT-IXSE-01433 Ivonne Acosta Ibarra AOII140711MMCCBV00 Ixtapan de la Sal SECUNDARIA Primer grado A 2026-08-24T00:00:00.000Z Editar este registro" [ref=e477] [cursor=pointer]:
                    - cell "MAT-IXSE-01433" [ref=e478]
                    - cell "Ivonne Acosta Ibarra" [ref=e479]
                    - cell "AOII140711MMCCBV00" [ref=e480]
                    - cell [ref=e481]
                    - cell "Ixtapan de la Sal" [ref=e482]
                    - cell "SECUNDARIA" [ref=e483]
                    - cell "Primer grado" [ref=e484]
                    - cell "A" [ref=e485]
                    - cell "2026-08-24T00:00:00.000Z" [ref=e486]
                    - cell "Editar este registro" [ref=e487]:
                      - button "Editar este registro" [ref=e489]:
                        - generic [ref=e490]: 
                  - row "MAT-TEPR-01242 Javier Acosta Nájera AONJ180808HMCCJV00 Tenancingo PRIMARIA Tercer grado A 2026-08-24T00:00:00.000Z Editar este registro" [ref=e491] [cursor=pointer]:
                    - cell "MAT-TEPR-01242" [ref=e492]
                    - cell "Javier Acosta Nájera" [ref=e493]
                    - cell "AONJ180808HMCCJV00" [ref=e494]
                    - cell [ref=e495]
                    - cell "Tenancingo" [ref=e496]
                    - cell "PRIMARIA" [ref=e497]
                    - cell "Tercer grado" [ref=e498]
                    - cell "A" [ref=e499]
                    - cell "2026-08-24T00:00:00.000Z" [ref=e500]
                    - cell "Editar este registro" [ref=e501]:
                      - button "Editar este registro" [ref=e503]:
                        - generic [ref=e504]: 
                  - row "MAT-IXPR-01817 Liliana Acosta Negrete AONL190828MMCCGL00 Ixtapan de la Sal PRIMARIA Segundo grado B 2026-08-24T00:00:00.000Z Editar este registro" [ref=e505] [cursor=pointer]:
                    - cell "MAT-IXPR-01817" [ref=e506]
                    - cell "Liliana Acosta Negrete" [ref=e507]
                    - cell "AONL190828MMCCGL00" [ref=e508]
                    - cell [ref=e509]
                    - cell "Ixtapan de la Sal" [ref=e510]
                    - cell "PRIMARIA" [ref=e511]
                    - cell "Segundo grado" [ref=e512]
                    - cell "B" [ref=e513]
                    - cell "2026-08-24T00:00:00.000Z" [ref=e514]
                    - cell "Editar este registro" [ref=e515]:
                      - button "Editar este registro" [ref=e517]:
                        - generic [ref=e518]: 
                  - row "MAT-IXPR-01837 Nayeli Acosta Mancilla AOMN180719MMCCNY00 Ixtapan de la Sal PRIMARIA Tercer grado A 2026-08-24T00:00:00.000Z Editar este registro" [ref=e519] [cursor=pointer]:
                    - cell "MAT-IXPR-01837" [ref=e520]
                    - cell "Nayeli Acosta Mancilla" [ref=e521]
                    - cell "AOMN180719MMCCNY00" [ref=e522]
                    - cell [ref=e523]
                    - cell "Ixtapan de la Sal" [ref=e524]
                    - cell "PRIMARIA" [ref=e525]
                    - cell "Tercer grado" [ref=e526]
                    - cell "A" [ref=e527]
                    - cell "2026-08-24T00:00:00.000Z" [ref=e528]
                    - cell "Editar este registro" [ref=e529]:
                      - button "Editar este registro" [ref=e531]:
                        - generic [ref=e532]: 
                  - row "MAT-TESE-00157 Octavio Acosta Gallegos AOGO140805HMCCLC00 Tenancingo SECUNDARIA Primer grado A 2026-08-24T00:00:00.000Z Editar este registro" [ref=e533] [cursor=pointer]:
                    - cell "MAT-TESE-00157" [ref=e534]
                    - cell "Octavio Acosta Gallegos" [ref=e535]
                    - cell "AOGO140805HMCCLC00" [ref=e536]
                    - cell [ref=e537]
                    - cell "Tenancingo" [ref=e538]
                    - cell "SECUNDARIA" [ref=e539]
                    - cell "Primer grado" [ref=e540]
                    - cell "A" [ref=e541]
                    - cell "2026-08-24T00:00:00.000Z" [ref=e542]
                    - cell "Editar este registro" [ref=e543]:
                      - button "Editar este registro" [ref=e545]:
                        - generic [ref=e546]: 
                  - row "MAT-TEPR-01253 Patricia Acosta Cisneros AOCP180806MMCCST00 Tenancingo PRIMARIA Tercer grado B 2026-08-24T00:00:00.000Z Editar este registro" [ref=e547] [cursor=pointer]:
                    - cell "MAT-TEPR-01253" [ref=e548]
                    - cell "Patricia Acosta Cisneros" [ref=e549]
                    - cell "AOCP180806MMCCST00" [ref=e550]
                    - cell [ref=e551]
                    - cell "Tenancingo" [ref=e552]
                    - cell "PRIMARIA" [ref=e553]
                    - cell "Tercer grado" [ref=e554]
                    - cell "B" [ref=e555]
                    - cell "2026-08-24T00:00:00.000Z" [ref=e556]
                    - cell "Editar este registro" [ref=e557]:
                      - button "Editar este registro" [ref=e559]:
                        - generic [ref=e560]: 
                  - row "MAT-MEPR-00886 Perla Acosta Ortiz AOOP190609MMCCRR00 Metepec PRIMARIA Segundo grado B 2026-08-24T00:00:00.000Z Editar este registro" [ref=e561] [cursor=pointer]:
                    - cell "MAT-MEPR-00886" [ref=e562]
                    - cell "Perla Acosta Ortiz" [ref=e563]
                    - cell "AOOP190609MMCCRR00" [ref=e564]
                    - cell [ref=e565]
                    - cell "Metepec" [ref=e566]
                    - cell "PRIMARIA" [ref=e567]
                    - cell "Segundo grado" [ref=e568]
                    - cell "B" [ref=e569]
                    - cell "2026-08-24T00:00:00.000Z" [ref=e570]
                    - cell "Editar este registro" [ref=e571]:
                      - button "Editar este registro" [ref=e573]:
                        - generic [ref=e574]: 
                  - row "MAT-TEPR-01263 Regina Acosta Delgado AODR181204MMCCLG00 Tenancingo PRIMARIA Tercer grado B 2026-08-24T00:00:00.000Z Editar este registro" [ref=e575] [cursor=pointer]:
                    - cell "MAT-TEPR-01263" [ref=e576]
                    - cell "Regina Acosta Delgado" [ref=e577]
                    - cell "AODR181204MMCCLG00" [ref=e578]
                    - cell [ref=e579]
                    - cell "Tenancingo" [ref=e580]
                    - cell "PRIMARIA" [ref=e581]
                    - cell "Tercer grado" [ref=e582]
                    - cell "B" [ref=e583]
                    - cell "2026-08-24T00:00:00.000Z" [ref=e584]
                    - cell "Editar este registro" [ref=e585]:
                      - button "Editar este registro" [ref=e587]:
                        - generic [ref=e588]: 
                  - row "MAT-MEPR-01043 Adriana Aguilar Mata AUMA160111MMCGTD00 Metepec PRIMARIA Quinto grado B 2026-08-24T00:00:00.000Z Editar este registro" [ref=e589] [cursor=pointer]:
                    - cell "MAT-MEPR-01043" [ref=e590]
                    - cell "Adriana Aguilar Mata" [ref=e591]
                    - cell "AUMA160111MMCGTD00" [ref=e592]
                    - cell [ref=e593]
                    - cell "Metepec" [ref=e594]
                    - cell "PRIMARIA" [ref=e595]
                    - cell "Quinto grado" [ref=e596]
                    - cell "B" [ref=e597]
                    - cell "2026-08-24T00:00:00.000Z" [ref=e598]
                    - cell "Editar este registro" [ref=e599]:
                      - button "Editar este registro" [ref=e601]:
                        - generic [ref=e602]: 
                  - row "MAT-TEPA-00679 Beatriz Aguilar Escamilla AUEB070527MMCGST00 Tenancingo PREPARATORIA Quinto semestre B 2026-08-24T00:00:00.000Z Editar este registro" [ref=e603] [cursor=pointer]:
                    - cell "MAT-TEPA-00679" [ref=e604]
                    - cell "Beatriz Aguilar Escamilla" [ref=e605]
                    - cell "AUEB070527MMCGST00" [ref=e606]
                    - cell [ref=e607]
                    - cell "Tenancingo" [ref=e608]
                    - cell "PREPARATORIA" [ref=e609]
                    - cell "Quinto semestre" [ref=e610]
                    - cell "B" [ref=e611]
                    - cell "2026-08-24T00:00:00.000Z" [ref=e612]
                    - cell "Editar este registro" [ref=e613]:
                      - button "Editar este registro" [ref=e615]:
                        - generic [ref=e616]: 
                  - row "MAT-IXPR-01730 Dylan Aguilar Valencia AUVD200515HMCGLY00 Ixtapan de la Sal PRIMARIA Primer grado A 2026-08-24T00:00:00.000Z Editar este registro" [ref=e617] [cursor=pointer]:
                    - cell "MAT-IXPR-01730" [ref=e618]
                    - cell "Dylan Aguilar Valencia" [ref=e619]
                    - cell "AUVD200515HMCGLY00" [ref=e620]
                    - cell [ref=e621]
                    - cell "Ixtapan de la Sal" [ref=e622]
                    - cell "PRIMARIA" [ref=e623]
                    - cell "Primer grado" [ref=e624]
                    - cell "A" [ref=e625]
                    - cell "2026-08-24T00:00:00.000Z" [ref=e626]
                    - cell "Editar este registro" [ref=e627]:
                      - button "Editar este registro" [ref=e629]:
                        - generic [ref=e630]: 
                  - row "MAT-MEPR-00938 Elena Aguilar Rosales AURE180922MMCGSL00 Metepec PRIMARIA Tercer grado B 2026-08-24T00:00:00.000Z Editar este registro" [ref=e631] [cursor=pointer]:
                    - cell "MAT-MEPR-00938" [ref=e632]
                    - cell "Elena Aguilar Rosales" [ref=e633]
                    - cell "AURE180922MMCGSL00" [ref=e634]
                    - cell [ref=e635]
                    - cell "Metepec" [ref=e636]
                    - cell "PRIMARIA" [ref=e637]
                    - cell "Tercer grado" [ref=e638]
                    - cell "B" [ref=e639]
                    - cell "2026-08-24T00:00:00.000Z" [ref=e640]
                    - cell "Editar este registro" [ref=e641]:
                      - button "Editar este registro" [ref=e643]:
                        - generic [ref=e644]: 
                  - row "MAT-MEPR-00985 Gustavo Aguilar Domínguez AUDG170125HMCGMS00 Metepec PRIMARIA Cuarto grado A 2026-08-24T00:00:00.000Z Editar este registro" [ref=e645] [cursor=pointer]:
                    - cell "MAT-MEPR-00985" [ref=e646]
                    - cell "Gustavo Aguilar Domínguez" [ref=e647]
                    - cell "AUDG170125HMCGMS00" [ref=e648]
                    - cell [ref=e649]
                    - cell "Metepec" [ref=e650]
                    - cell "PRIMARIA" [ref=e651]
                    - cell "Cuarto grado" [ref=e652]
                    - cell "A" [ref=e653]
                    - cell "2026-08-24T00:00:00.000Z" [ref=e654]
                    - cell "Editar este registro" [ref=e655]:
                      - button "Editar este registro" [ref=e657]:
                        - generic [ref=e658]: 
                  - row "MAT-IXSE-01464 Javier Aguilar Carmona AUCJ140704HMCGRV00 Ixtapan de la Sal SECUNDARIA Primer grado B 2026-08-24T00:00:00.000Z Editar este registro" [ref=e659] [cursor=pointer]:
                    - cell "MAT-IXSE-01464" [ref=e660]
                    - cell "Javier Aguilar Carmona" [ref=e661]
                    - cell "AUCJ140704HMCGRV00" [ref=e662]
                    - cell [ref=e663]
                    - cell "Ixtapan de la Sal" [ref=e664]
                    - cell "SECUNDARIA" [ref=e665]
                    - cell "Primer grado" [ref=e666]
                    - cell "B" [ref=e667]
                    - cell "2026-08-24T00:00:00.000Z" [ref=e668]
                    - cell "Editar este registro" [ref=e669]:
                      - button "Editar este registro" [ref=e671]:
                        - generic [ref=e672]: 
                  - row "MAT-TESE-01419 Noemí Aguilar Lugo AULN120616MMCGGM00 Tenancingo SECUNDARIA Tercer grado B 2026-08-24T00:00:00.000Z Editar este registro" [ref=e673] [cursor=pointer]:
                    - cell "MAT-TESE-01419" [ref=e674]
                    - cell "Noemí Aguilar Lugo" [ref=e675]
                    - cell "AULN120616MMCGGM00" [ref=e676]
                    - cell [ref=e677]
                    - cell "Tenancingo" [ref=e678]
                    - cell "SECUNDARIA" [ref=e679]
                    - cell "Tercer grado" [ref=e680]
                    - cell "B" [ref=e681]
                    - cell "2026-08-24T00:00:00.000Z" [ref=e682]
                    - cell "Editar este registro" [ref=e683]:
                      - button "Editar este registro" [ref=e685]:
                        - generic [ref=e686]: 
                  - row "MAT-MEPA-00707 Santiago Aguilar Macías AUMS060116HMCGCN00 Metepec PREPARATORIA Sexto semestre A 2026-08-24T00:00:00.000Z Editar este registro" [ref=e687] [cursor=pointer]:
                    - cell "MAT-MEPA-00707" [ref=e688]
                    - cell "Santiago Aguilar Macías" [ref=e689]
                    - cell "AUMS060116HMCGCN00" [ref=e690]
                    - cell [ref=e691]
                    - cell "Metepec" [ref=e692]
                    - cell "PREPARATORIA" [ref=e693]
                    - cell "Sexto semestre" [ref=e694]
                    - cell "A" [ref=e695]
                    - cell "2026-08-24T00:00:00.000Z" [ref=e696]
                    - cell "Editar este registro" [ref=e697]:
                      - button "Editar este registro" [ref=e699]:
                        - generic [ref=e700]: 
                  - row "MAT-MEPR-01093 Cristóbal Aguirre Delgado AUDC150913HMCGLR00 Metepec PRIMARIA Sexto grado B 2026-08-24T00:00:00.000Z Editar este registro" [ref=e701] [cursor=pointer]:
                    - cell "MAT-MEPR-01093" [ref=e702]
                    - cell "Cristóbal Aguirre Delgado" [ref=e703]
                    - cell "AUDC150913HMCGLR00" [ref=e704]
                    - cell [ref=e705]
                    - cell "Metepec" [ref=e706]
                    - cell "PRIMARIA" [ref=e707]
                    - cell "Sexto grado" [ref=e708]
                    - cell "B" [ref=e709]
                    - cell "2026-08-24T00:00:00.000Z" [ref=e710]
                    - cell "Editar este registro" [ref=e711]:
                      - button "Editar este registro" [ref=e713]:
                        - generic [ref=e714]: 
                  - row "MAT-MEPA-00743 Gabriel Aguirre Galván AUGG060125HMCGLB00 Metepec PREPARATORIA Sexto semestre B 2026-08-24T00:00:00.000Z Editar este registro" [ref=e715] [cursor=pointer]:
                    - cell "MAT-MEPA-00743" [ref=e716]
                    - cell "Gabriel Aguirre Galván" [ref=e717]
                    - cell "AUGG060125HMCGLB00" [ref=e718]
                    - cell [ref=e719]
                    - cell "Metepec" [ref=e720]
                    - cell "PREPARATORIA" [ref=e721]
                    - cell "Sexto semestre" [ref=e722]
                    - cell "B" [ref=e723]
                    - cell "2026-08-24T00:00:00.000Z" [ref=e724]
                    - cell "Editar este registro" [ref=e725]:
                      - button "Editar este registro" [ref=e727]:
                        - generic [ref=e728]: 
              - generic [ref=e729]:
                - button "Primera página":
                  - img
                - button [disabled]:
                  - img
                - button "Página 1" [ref=e731] [cursor=pointer]: "1"
                - button "Página siguiente" [disabled]:
                  - img
                - button "Última página" [disabled]:
                  - img
                - generic [ref=e732] [cursor=pointer]:
                  - combobox "Filas por página" [ref=e733]: "20"
                  - button "dropdown trigger" [ref=e734]:
                    - img [ref=e735]
          - generic "Nuevo Alumno"
          - generic "Asignación masiva de grupo"
  - dialog "Nuevo Alumno" [ref=e738]:
    - generic [ref=e740]:
      - generic [ref=e741]: Nuevo Alumno
      - button [ref=e744] [cursor=pointer]:
        - img [ref=e745]
    - generic [ref=e748]:
      - generic [ref=e750]:
        - generic [ref=e751]: Nombre(s) *
        - 'textbox "Ej: Juan Carlos" [ref=e752]': Ana
        - generic [ref=e753]: ℹ️ Nombre completo del alumno (se permiten hasta 100 caracteres)
        - generic [ref=e754]: 3 / 100 caracteres
      - generic [ref=e756]:
        - generic [ref=e757]: Apellido paterno *
        - 'textbox "Ej: García" [ref=e758]': Pérez
        - generic [ref=e759]: ℹ️ Primer apellido del alumno
        - generic [ref=e760]: 5 / 100 caracteres
      - generic [ref=e762]:
        - generic [ref=e763]: Apellido materno
        - 'textbox "Ej: López" [ref=e764]'
        - generic [ref=e765]: ℹ️ Segundo apellido (opcional)
        - generic [ref=e766]: 0 / 100 caracteres
      - generic [ref=e768]:
        - generic [ref=e769]: CURP *
        - textbox "AAAA999999HAAAAA01" [ref=e770]: "12345"
        - generic [ref=e771]: "ℹ️ 18 caracteres: 4 letras + 6 dígitos fecha + 1 sexo (H/M/X) + 5 letras + 1 letra/dígito + 1 dígito verificador"
        - generic [ref=e772]: 5 / 18 caracteres
        - generic [ref=e773]: Debe tener exactamente 18 caracteres
      - paragraph [ref=e774]: Una vez creado podrás completar el expediente completo desde el perfil.
    - button "Crear alumno" [disabled] [ref=e777]:
      - generic [ref=e778]: Crear alumno
```

# Test source

```ts
  1   | import { Page, expect } from '@playwright/test';
  2   | import { BasePage } from './base-page';
  3   | 
  4   | export class AlumnosPage extends BasePage {
  5   |   // Lista principal — apex-interactive-grid
  6   |   readonly table    = this.page.locator('app-interactive-grid, [data-testid="tabla-alumnos"], p-table').first();
  7   |   readonly rows     = this.page.locator('tr.data-row, .p-datatable-row, [data-testid="grid-row"]');
  8   | 
  9   |   // Búsqueda — apex-search component renders <input class="apex-search-input">
  10  |   readonly searchInput = this.page.locator('input.apex-search-input, [data-testid="search-alumnos"]');
  11  | 
  12  |   // Botones
  13  |   readonly newBtn     = this.page.locator('button:has-text("Nuevo alumno"), button:has-text("Nuevo"), [data-testid="btn-nuevo"]');
  14  |   readonly importBtn  = this.page.locator('[data-testid="btn-importar"], button:has-text("Importar")');
  15  | 
  16  |   // Dialog de alta rápida (apex-modal-dialog → .apex-dialog)
  17  |   // Los inputs están en orden: Nombre, Ap. Paterno, Ap. Materno, CURP
  18  |   readonly dlgInputs     = this.page.locator('.apex-dialog input, apex-modal-dialog input');
  19  |   readonly nombreInput   = this.dlgInputs.nth(0);
  20  |   readonly apPaternoInput = this.dlgInputs.nth(1);
  21  |   readonly apMaternoInput = this.dlgInputs.nth(2);
  22  |   readonly curpInput     = this.page.locator('.apex-dialog input[maxlength="18"], input[maxlength="18"]');
  23  | 
  24  |   // Fecha nacimiento — el formulario básico de alta no lo incluye, locator seguro para fuzz
  25  |   readonly fechaNacInput = this.page.locator(
  26  |     'input[type="date"], [data-testid="fecha-nacimiento"], .apex-dialog input[type="date"]'
  27  |   ).first();
  28  | 
  29  |   // Botón Guardar/Crear dentro del dialog
  30  |   readonly saveBtn = this.page.locator(
  31  |     'button:has-text("Crear alumno"), button:has-text("Guardar"), [data-testid="btn-guardar"]'
  32  |   );
  33  | 
  34  |   async navigate() {
  35  |     await this.page.goto('/alumnos', { waitUntil: 'domcontentloaded' });
  36  |     await this.waitSpinner();
  37  |     // Esperar a que la tabla o el botón "Nuevo" estén presentes antes de continuar
  38  |     await this.page.waitForSelector(
  39  |       'app-interactive-grid, [data-testid="tabla-alumnos"], p-table, button:has-text("Nuevo")',
  40  |       { timeout: 15_000 }
  41  |     );
  42  |   }
  43  | 
  44  |   async openNewForm() {
  45  |     await this.newBtn.first().waitFor({ state: 'visible', timeout: 10_000 });
  46  |     await this.newBtn.first().click();
  47  |     await expect(this.page.locator('.apex-dialog, [role="dialog"]')).toBeVisible({ timeout: 10_000 });
  48  |     await this.page.waitForTimeout(300);
  49  |   }
  50  | 
  51  |   async fillAlumnoForm(data: {
  52  |     curp: string;
  53  |     nombre: string;
  54  |     apellido_paterno: string;
  55  |     apellido_materno?: string;
  56  |     fecha_nacimiento?: string;
  57  |   }) {
  58  |     await this.fillAndBlur(this.nombreInput,    data.nombre);
  59  |     await this.fillAndBlur(this.apPaternoInput, data.apellido_paterno);
  60  |     if (data.apellido_materno) {
  61  |       await this.fillAndBlur(this.apMaternoInput, data.apellido_materno);
  62  |     }
  63  |     await this.fillAndBlur(this.curpInput, data.curp);
  64  |   }
  65  | 
  66  |   async save() {
> 67  |     await this.saveBtn.click();
      |                        ^ Error: locator.click: Test timeout of 30000ms exceeded.
  68  |   }
  69  | 
  70  |   async saveAndExpectSuccess() {
  71  |     await this.save();
  72  |     await this.waitForToast('success');
  73  |   }
  74  | 
  75  |   async saveAndExpectError(message?: string) {
  76  |     await this.save();
  77  |     await this.waitForToast('error');
  78  |     if (message) {
  79  |       await expect(this.page.locator('.p-toast-detail')).toContainText(message);
  80  |     }
  81  |   }
  82  | 
  83  |   async searchFor(query: string) {
  84  |     await this.searchInput.fill(query);
  85  |     await this.page.waitForTimeout(400);
  86  |     await this.waitSpinner();
  87  |   }
  88  | 
  89  |   async expectRowCount(min: number) {
  90  |     const count = await this.rows.count();
  91  |     expect(count).toBeGreaterThanOrEqual(min);
  92  |   }
  93  | 
  94  |   async getFirstRowText(): Promise<string> {
  95  |     return (await this.rows.first().textContent()) ?? '';
  96  |   }
  97  | 
  98  |   async clickFirstRow() {
  99  |     await this.rows.first().click();
  100 |   }
  101 | 
  102 |   async uploadCsv(filePath: string) {
  103 |     const input = this.page.locator('input[type="file"]');
  104 |     await this.importBtn.click();
  105 |     await input.setInputFiles(filePath);
  106 |     await this.page.locator('button:has-text("Importar")').last().click();
  107 |   }
  108 | }
  109 | 
```