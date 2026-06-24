# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: 07-fuzz.spec.ts >> Fuzz: formulario de alumnos >> FUZZ-01 | 30 alumnos con datos aleatorios — ninguno crashea la app
- Location: e2e/tests/07-fuzz.spec.ts:17:7

# Error details

```
Test timeout of 30000ms exceeded.
```

```
Error: expect(received).toBe(expected) // Object.is equality

Expected: 0
Received: 1
```

# Page snapshot

```yaml
- generic [active] [ref=e1]:
  - generic [ref=e3]:
    - status [ref=e4]
    - alert [ref=e7]:
      - generic [ref=e8]:
        - img [ref=e9]
        - generic [ref=e11]:
          - generic [ref=e12]: Creado
          - generic [ref=e13]: "Matrícula: MAT-000022"
        - button "Cerrar" [ref=e15] [cursor=pointer]:
          - img [ref=e16]
    - toolbar [ref=e18]:
      - generic [ref=e19]:
        - generic [ref=e20]:
          - generic [ref=e21]: "N"
          - generic [ref=e22]:
            - generic [ref=e23]: ADES
            - generic [ref=e24]: Instituto Nevadi
        - generic [ref=e26] [cursor=pointer]:
          - combobox "Plantel..." [ref=e27]
          - button "dropdown trigger" [ref=e28]:
            - img [ref=e29]
        - generic [ref=e31]: /
        - generic [ref=e32] [cursor=pointer]:
          - combobox "Nivel..." [ref=e33]
          - button "dropdown trigger" [ref=e34]:
            - img [ref=e35]
        - generic [ref=e37]: /
        - generic [ref=e38] [cursor=pointer]:
          - combobox "Ciclo..." [ref=e39]
          - button "dropdown trigger" [ref=e40]:
            - img [ref=e41]
        - generic [ref=e43]: /
        - generic [ref=e44] [cursor=pointer]:
          - combobox "Grado..." [ref=e45]
          - button "dropdown trigger" [ref=e46]:
            - img [ref=e47]
        - generic [ref=e49]: /
        - generic [ref=e50] [cursor=pointer]:
          - combobox "Grupo..." [ref=e51]
          - button "dropdown trigger" [ref=e52]:
            - img [ref=e53]
      - generic [ref=e55]:
        - button "Notificaciones" [ref=e56] [cursor=pointer]:
          - generic [ref=e57]: 
        - button "Cuenta de Test COORDINADOR_ACADEMICO" [ref=e58] [cursor=pointer]:
          - generic [ref=e59]: T
          - generic [ref=e60]:
            - generic [ref=e61]: Test COORDINADOR_ACADEMICO
            - generic [ref=e62]: Coord. Académico
          - generic [ref=e63]: 
    - generic [ref=e64]:
      - navigation "Navegación principal" [ref=e65]:
        - generic [ref=e66]: Principal
        - list [ref=e67]:
          - listitem [ref=e68]:
            - link " Dashboard" [ref=e69] [cursor=pointer]:
              - /url: /dashboard
              - generic [ref=e70]: 
              - generic [ref=e71]: Dashboard
        - generic [ref=e72]: Académico
        - list [ref=e73]:
          - listitem [ref=e74]:
            - link " Alumnos" [ref=e75] [cursor=pointer]:
              - /url: /alumnos
              - generic [ref=e76]: 
              - generic [ref=e77]: Alumnos
          - listitem [ref=e78]:
            - link " Reinscripción" [ref=e79] [cursor=pointer]:
              - /url: /reinscripcion
              - generic [ref=e80]: 
              - generic [ref=e81]: Reinscripción
          - listitem [ref=e82]:
            - link " Profesores" [ref=e83] [cursor=pointer]:
              - /url: /profesores
              - generic [ref=e84]: 
              - generic [ref=e85]: Profesores
          - listitem [ref=e86]:
            - link " Grupos" [ref=e87] [cursor=pointer]:
              - /url: /grupos
              - generic [ref=e88]: 
              - generic [ref=e89]: Grupos
          - listitem [ref=e90]:
            - link " Aulas" [ref=e91] [cursor=pointer]:
              - /url: /aulas
              - generic [ref=e92]: 
              - generic [ref=e93]: Aulas
          - listitem [ref=e94]:
            - link " Planes de Estudio" [ref=e95] [cursor=pointer]:
              - /url: /planes-estudio
              - generic [ref=e96]: 
              - generic [ref=e97]: Planes de Estudio
          - listitem [ref=e98]:
            - link " Calificaciones" [ref=e99] [cursor=pointer]:
              - /url: /calificaciones
              - generic [ref=e100]: 
              - generic [ref=e101]: Calificaciones
          - listitem [ref=e102]:
            - link " Evaluaciones" [ref=e103] [cursor=pointer]:
              - /url: /evaluaciones
              - generic [ref=e104]: 
              - generic [ref=e105]: Evaluaciones
          - listitem [ref=e106]:
            - link " Asistencias" [ref=e107] [cursor=pointer]:
              - /url: /asistencias
              - generic [ref=e108]: 
              - generic [ref=e109]: Asistencias
          - listitem [ref=e110]:
            - link " Tareas" [ref=e111] [cursor=pointer]:
              - /url: /tareas
              - generic [ref=e112]: 
              - generic [ref=e113]: Tareas
          - listitem [ref=e114]:
            - link " Planeación" [ref=e115] [cursor=pointer]:
              - /url: /planeacion
              - generic [ref=e116]: 
              - generic [ref=e117]: Planeación
        - generic [ref=e118]: Operaciones
        - list [ref=e119]:
          - listitem [ref=e120]:
            - link " Horarios" [ref=e121] [cursor=pointer]:
              - /url: /horarios
              - generic [ref=e122]: 
              - generic [ref=e123]: Horarios
          - listitem [ref=e124]:
            - link " Calendario Escolar" [ref=e125] [cursor=pointer]:
              - /url: /calendario
              - generic [ref=e126]: 
              - generic [ref=e127]: Calendario Escolar
          - listitem [ref=e128]:
            - link " Conducta" [ref=e129] [cursor=pointer]:
              - /url: /conducta
              - generic [ref=e130]: 
              - generic [ref=e131]: Conducta
          - listitem [ref=e132]:
            - link " Expediente Médico" [ref=e133] [cursor=pointer]:
              - /url: /medico
              - generic [ref=e134]: 
              - generic [ref=e135]: Expediente Médico
          - listitem [ref=e136]:
            - link " Condiciones Crónicas" [ref=e137] [cursor=pointer]:
              - /url: /condiciones-cronicas
              - generic [ref=e138]: 
              - generic [ref=e139]: Condiciones Crónicas
          - listitem [ref=e140]:
            - link " Justificaciones Faltas" [ref=e141] [cursor=pointer]:
              - /url: /justificaciones
              - generic [ref=e142]: 
              - generic [ref=e143]: Justificaciones Faltas
          - listitem [ref=e144]:
            - link " Movilidad Estudiantil" [ref=e145] [cursor=pointer]:
              - /url: /movilidad
              - generic [ref=e146]: 
              - generic [ref=e147]: Movilidad Estudiantil
          - listitem [ref=e148]:
            - link " Biblioteca" [ref=e149] [cursor=pointer]:
              - /url: /biblioteca
              - generic [ref=e150]: 
              - generic [ref=e151]: Biblioteca
          - listitem [ref=e152]:
            - link " Formato 911 SEP" [ref=e153] [cursor=pointer]:
              - /url: /estadistica-911
              - generic [ref=e154]: 
              - generic [ref=e155]: Formato 911 SEP
          - listitem [ref=e156]:
            - link " Kardex UAEMEX" [ref=e157] [cursor=pointer]:
              - /url: /kardex
              - generic [ref=e158]: 
              - generic [ref=e159]: Kardex UAEMEX
          - listitem [ref=e160]:
            - link " Acta Evaluación UAEMEX" [ref=e161] [cursor=pointer]:
              - /url: /acta-evaluacion
              - generic [ref=e162]: 
              - generic [ref=e163]: Acta Evaluación UAEMEX
          - listitem [ref=e164]:
            - link " Optativas" [ref=e165] [cursor=pointer]:
              - /url: /optativas
              - generic [ref=e166]: 
              - generic [ref=e167]: Optativas
          - listitem [ref=e168]:
            - link " Admisión" [ref=e169] [cursor=pointer]:
              - /url: /admision
              - generic [ref=e170]: 
              - generic [ref=e171]: Admisión
        - generic [ref=e172]: Comunicación
        - list [ref=e173]:
          - listitem [ref=e174]:
            - link " Comunicados" [ref=e175] [cursor=pointer]:
              - /url: /comunicados
              - generic [ref=e176]: 
              - generic [ref=e177]: Comunicados
          - listitem [ref=e178]:
            - link " Foros y Anuncios" [ref=e179] [cursor=pointer]:
              - /url: /foros
              - generic [ref=e180]: 
              - generic [ref=e181]: Foros y Anuncios
          - listitem [ref=e182]:
            - link " Encuestas" [ref=e183] [cursor=pointer]:
              - /url: /encuestas
              - generic [ref=e184]: 
              - generic [ref=e185]: Encuestas
          - listitem [ref=e186]:
            - link " Videoconferencias" [ref=e187] [cursor=pointer]:
              - /url: /videoconferencias
              - generic [ref=e188]: 
              - generic [ref=e189]: Videoconferencias
        - generic [ref=e190]: Gradebook
        - list [ref=e191]:
          - listitem [ref=e192]:
            - link " Gradebook" [ref=e193] [cursor=pointer]:
              - /url: /gradebook
              - generic [ref=e194]: 
              - generic [ref=e195]: Gradebook
          - listitem [ref=e196]:
            - link " Mi Progreso" [ref=e197] [cursor=pointer]:
              - /url: /mi-progreso
              - generic [ref=e198]: 
              - generic [ref=e199]: Mi Progreso
          - listitem [ref=e200]:
            - link " Ponderaciones" [ref=e201] [cursor=pointer]:
              - /url: /ponderacion-config
              - generic [ref=e202]: 
              - generic [ref=e203]: Ponderaciones
        - generic [ref=e204]: Recursos
        - list [ref=e205]:
          - listitem [ref=e206]:
            - link " Rúbricas" [ref=e207] [cursor=pointer]:
              - /url: /rubricas
              - generic [ref=e208]: 
              - generic [ref=e209]: Rúbricas
          - listitem [ref=e210]:
            - link " Insignias" [ref=e211] [cursor=pointer]:
              - /url: /badges
              - generic [ref=e212]: 
              - generic [ref=e213]: Insignias
          - listitem [ref=e214]:
            - link " Portal Alumno" [ref=e215] [cursor=pointer]:
              - /url: /portal
              - generic [ref=e216]: 
              - generic [ref=e217]: Portal Alumno
          - listitem [ref=e218]:
            - link " Contenido H5P" [ref=e219] [cursor=pointer]:
              - /url: /h5p
              - generic [ref=e220]: 
              - generic [ref=e221]: Contenido H5P
        - generic [ref=e222]: Inteligencia
        - list [ref=e223]:
          - listitem [ref=e224]:
            - link " Dashboards BI" [ref=e225] [cursor=pointer]:
              - /url: /bi
              - generic [ref=e226]: 
              - generic [ref=e227]: Dashboards BI
          - listitem [ref=e228]:
            - link " Grade Analytics" [ref=e229] [cursor=pointer]:
              - /url: /grade-analytics
              - generic [ref=e230]: 
              - generic [ref=e231]: Grade Analytics
          - listitem [ref=e232]:
            - link " Asistente IA + Datos" [ref=e233] [cursor=pointer]:
              - /url: /ia
              - generic [ref=e234]: 
              - generic [ref=e235]: Asistente IA + Datos
          - listitem [ref=e236]:
            - link " Eval. Docente 360°" [ref=e237] [cursor=pointer]:
              - /url: /eval-docente
              - generic [ref=e238]: 
              - generic [ref=e239]: Eval. Docente 360°
          - listitem [ref=e240]:
            - link " Learning Paths" [ref=e241] [cursor=pointer]:
              - /url: /learning-paths
              - generic [ref=e242]: 
              - generic [ref=e243]: Learning Paths
        - generic [ref=e244]: Reportes
        - list [ref=e245]:
          - listitem [ref=e246]:
            - link " Generador de Reportes" [ref=e247] [cursor=pointer]:
              - /url: /reportes
              - generic [ref=e248]: 
              - generic [ref=e249]: Generador de Reportes
          - listitem [ref=e250]:
            - link " Certificados Digitales" [ref=e251] [cursor=pointer]:
              - /url: /certificados
              - generic [ref=e252]: 
              - generic [ref=e253]: Certificados Digitales
          - listitem [ref=e254]:
            - link " Expediente Digital" [ref=e255] [cursor=pointer]:
              - /url: /expediente-doc
              - generic [ref=e256]: 
              - generic [ref=e257]: Expediente Digital
        - generic [ref=e258]: Ayuda
        - list [ref=e259]:
          - listitem [ref=e260]:
            - link " Manual de Usuario" [ref=e261] [cursor=pointer]:
              - /url: /ayuda
              - generic [ref=e262]: 
              - generic [ref=e263]: Manual de Usuario
      - main [ref=e264]:
        - navigation "Breadcrumb" [ref=e267]:
          - list [ref=e268]:
            - listitem [ref=e269]:
              - link "Home" [ref=e270] [cursor=pointer]:
                - /url: /
            - listitem [ref=e271]:
              - generic [ref=e272]: 
              - generic [ref=e273]: Alumnos
        - generic [ref=e274]:
          - alert [ref=e277]:
            - generic [ref=e278]:
              - img [ref=e279]
              - generic [ref=e281]:
                - generic [ref=e282]: Creado
                - generic [ref=e283]: "Matrícula: MAT-000022"
              - button "Cerrar" [ref=e285] [cursor=pointer]:
                - img [ref=e286]
          - generic [ref=e288]:
            - generic [ref=e289]:
              - heading "Alumnos" [level=2] [ref=e290]
              - paragraph [ref=e291]: 1,738 alumno(s) registrado(s)
            - generic [ref=e292]:
              - button "" [ref=e295] [cursor=pointer]:
                - generic [ref=e296]: 
              - button " CSV" [ref=e298] [cursor=pointer]:
                - generic [ref=e299]: 
                - generic [ref=e300]: CSV
              - button " Excel" [ref=e302] [cursor=pointer]:
                - generic [ref=e303]: 
                - generic [ref=e304]: Excel
              - generic [ref=e306]:
                - button " Importar CSV/Excel" [ref=e308] [cursor=pointer]:
                  - generic [ref=e309]: 
                  - generic [ref=e310]: Importar CSV/Excel
                - button " Plantilla" [ref=e312] [cursor=pointer]:
                  - generic [ref=e313]: 
                  - generic [ref=e314]: Plantilla
              - button " Nuevo alumno" [ref=e316] [cursor=pointer]:
                - generic [ref=e317]: 
                - generic [ref=e318]: Nuevo alumno
          - generic [ref=e323]:
            - generic [ref=e324]: 
            - textbox "Buscar alumno..." [ref=e325]
          - generic [ref=e327]:
            - generic [ref=e328]:
              - generic [ref=e329]:
                - button "Mostrar u ocultar columnas de la tabla" [ref=e331] [cursor=pointer]:
                  - generic [ref=e332]: 
                - button "Descargar datos como archivo CSV" [ref=e334] [cursor=pointer]:
                  - generic [ref=e335]: 
              - generic [ref=e337]: 1738 registro(s)
            - generic [ref=e338]:
              - img [ref=e340]
              - table [ref=e344]:
                - rowgroup [ref=e345]:
                  - row "Matrícula Nombre Completo CURP NSS Plantel Nivel Grado Grupo Ingreso Acciones" [ref=e346]:
                    - columnheader "Matrícula" [ref=e347] [cursor=pointer]:
                      - generic [ref=e348]:
                        - generic [ref=e349]: Matrícula
                        - img [ref=e351]
                      - generic [ref=e358]:
                        - combobox "Filtrar..." [ref=e359]
                        - button [ref=e360]:
                          - img [ref=e361]
                    - columnheader "Nombre Completo" [ref=e363] [cursor=pointer]:
                      - generic [ref=e364]:
                        - generic [ref=e365]: Nombre Completo
                        - img [ref=e367]
                      - generic [ref=e374]:
                        - combobox "Filtrar..." [ref=e375]
                        - button [ref=e376]:
                          - img [ref=e377]
                    - columnheader "CURP" [ref=e379] [cursor=pointer]:
                      - generic [ref=e380]:
                        - generic [ref=e381]: CURP
                        - img [ref=e383]
                      - generic [ref=e390]:
                        - combobox "Filtrar..." [ref=e391]
                        - button [ref=e392]:
                          - img [ref=e393]
                    - columnheader "NSS" [ref=e395] [cursor=pointer]:
                      - generic [ref=e397]: NSS
                    - columnheader "Plantel" [ref=e398] [cursor=pointer]:
                      - generic [ref=e399]:
                        - generic [ref=e400]: Plantel
                        - img [ref=e402]
                      - generic [ref=e409]:
                        - combobox "Filtrar..." [ref=e410]
                        - button [ref=e411]:
                          - img [ref=e412]
                    - columnheader "Nivel" [ref=e414] [cursor=pointer]:
                      - generic [ref=e415]:
                        - generic [ref=e416]: Nivel
                        - img [ref=e418]
                      - generic [ref=e425]:
                        - combobox "Filtrar..." [ref=e426]
                        - button [ref=e427]:
                          - img [ref=e428]
                    - columnheader "Grado" [ref=e430] [cursor=pointer]:
                      - generic [ref=e431]:
                        - generic [ref=e432]: Grado
                        - img [ref=e434]
                      - generic [ref=e441]:
                        - combobox "Filtrar..." [ref=e442]
                        - button [ref=e443]:
                          - img [ref=e444]
                    - columnheader "Grupo" [ref=e446] [cursor=pointer]:
                      - generic [ref=e447]:
                        - generic [ref=e448]: Grupo
                        - img [ref=e450]
                      - generic [ref=e457]:
                        - combobox "Filtrar..." [ref=e458]
                        - button [ref=e459]:
                          - img [ref=e460]
                    - columnheader "Ingreso" [ref=e462] [cursor=pointer]:
                      - generic [ref=e463]:
                        - generic [ref=e464]: Ingreso
                        - img [ref=e466]
                    - columnheader "Acciones" [ref=e472]:
                      - strong [ref=e473]: Acciones
                - rowgroup [ref=e474]:
                  - row "MAT-MEPR-00420 Camilo Acosta Cordero AOCC151128HMCCRM00 Metepec PRIMARIA Sexto grado B 2026-08-24 Editar este registro" [ref=e475] [cursor=pointer]:
                    - cell "MAT-MEPR-00420" [ref=e476]
                    - cell "Camilo Acosta Cordero" [ref=e477]
                    - cell "AOCC151128HMCCRM00" [ref=e478]
                    - cell [ref=e479]
                    - cell "Metepec" [ref=e480]
                    - cell "PRIMARIA" [ref=e481]
                    - cell "Sexto grado" [ref=e482]
                    - cell "B" [ref=e483]
                    - cell "2026-08-24" [ref=e484]
                    - cell "Editar este registro" [ref=e485]:
                      - button "Editar este registro" [ref=e487]:
                        - generic [ref=e488]: 
                  - row "MAT-MEPR-00270 Héctor Acosta Cabrera AOCH190726HMCCBC00 Metepec PRIMARIA Segundo grado B 2026-08-24 Editar este registro" [ref=e489] [cursor=pointer]:
                    - cell "MAT-MEPR-00270" [ref=e490]
                    - cell "Héctor Acosta Cabrera" [ref=e491]
                    - cell "AOCH190726HMCCBC00" [ref=e492]
                    - cell [ref=e493]
                    - cell "Metepec" [ref=e494]
                    - cell "PRIMARIA" [ref=e495]
                    - cell "Segundo grado" [ref=e496]
                    - cell "B" [ref=e497]
                    - cell "2026-08-24" [ref=e498]
                    - cell "Editar este registro" [ref=e499]:
                      - button "Editar este registro" [ref=e501]:
                        - generic [ref=e502]: 
                  - row "MAT-IXPR-01580 Lorena Acosta Velázquez AOVL161223MMCCLR00 Ixtapan de la Sal PRIMARIA Quinto grado A 2026-08-24 Editar este registro" [ref=e503] [cursor=pointer]:
                    - cell "MAT-IXPR-01580" [ref=e504]
                    - cell "Lorena Acosta Velázquez" [ref=e505]
                    - cell "AOVL161223MMCCLR00" [ref=e506]
                    - cell [ref=e507]
                    - cell "Ixtapan de la Sal" [ref=e508]
                    - cell "PRIMARIA" [ref=e509]
                    - cell "Quinto grado" [ref=e510]
                    - cell "A" [ref=e511]
                    - cell "2026-08-24" [ref=e512]
                    - cell "Editar este registro" [ref=e513]:
                      - button "Editar este registro" [ref=e515]:
                        - generic [ref=e516]: 
                  - row "MAT-IXPR-01261 Patricia Acosta Cisneros AOCP190806MMCCST00 Ixtapan de la Sal PRIMARIA Segundo grado B 2026-08-24 Editar este registro" [ref=e517] [cursor=pointer]:
                    - cell "MAT-IXPR-01261" [ref=e518]
                    - cell "Patricia Acosta Cisneros" [ref=e519]
                    - cell "AOCP190806MMCCST00" [ref=e520]
                    - cell [ref=e521]
                    - cell "Ixtapan de la Sal" [ref=e522]
                    - cell "PRIMARIA" [ref=e523]
                    - cell "Segundo grado" [ref=e524]
                    - cell "B" [ref=e525]
                    - cell "2026-08-24" [ref=e526]
                    - cell "Editar este registro" [ref=e527]:
                      - button "Editar este registro" [ref=e529]:
                        - generic [ref=e530]: 
                  - row "MAT-MEPA-00892 Perla Acosta Ortiz AOOP100609MMCCRR00 Metepec PREPARATORIA Segundo semestre B 2026-08-24 Editar este registro" [ref=e531] [cursor=pointer]:
                    - cell "MAT-MEPA-00892" [ref=e532]
                    - cell "Perla Acosta Ortiz" [ref=e533]
                    - cell "AOOP100609MMCCRR00" [ref=e534]
                    - cell [ref=e535]
                    - cell "Metepec" [ref=e536]
                    - cell "PREPARATORIA" [ref=e537]
                    - cell "Segundo semestre" [ref=e538]
                    - cell "B" [ref=e539]
                    - cell "2026-08-24" [ref=e540]
                    - cell "Editar este registro" [ref=e541]:
                      - button "Editar este registro" [ref=e543]:
                        - generic [ref=e544]: 
                  - row "MAT-000008 Rodrigo Agosto Garza Girón Cardona MERDFR050928HJLKJQ Metepec — — — 2026-06-24 Editar este registro" [ref=e545] [cursor=pointer]:
                    - cell "MAT-000008" [ref=e546]
                    - cell "Rodrigo Agosto Garza Girón Cardona" [ref=e547]
                    - cell "MERDFR050928HJLKJQ" [ref=e548]
                    - cell [ref=e549]
                    - cell "Metepec" [ref=e550]
                    - cell "—" [ref=e551]
                    - cell "—" [ref=e552]
                    - cell "—" [ref=e553]
                    - cell "2026-06-24" [ref=e554]
                    - cell "Editar este registro" [ref=e555]:
                      - button "Editar este registro" [ref=e557]:
                        - generic [ref=e558]: 
                  - row "MAT-MEPA-01050 Adriana Aguilar Mata AUMA080111MMCGTD00 Metepec PREPARATORIA Cuarto semestre B 2026-08-24 Editar este registro" [ref=e559] [cursor=pointer]:
                    - cell "MAT-MEPA-01050" [ref=e560]
                    - cell "Adriana Aguilar Mata" [ref=e561]
                    - cell "AUMA080111MMCGTD00" [ref=e562]
                    - cell [ref=e563]
                    - cell "Metepec" [ref=e564]
                    - cell "PREPARATORIA" [ref=e565]
                    - cell "Cuarto semestre" [ref=e566]
                    - cell "B" [ref=e567]
                    - cell "2026-08-24" [ref=e568]
                    - cell "Editar este registro" [ref=e569]:
                      - button "Editar este registro" [ref=e571]:
                        - generic [ref=e572]: 
                  - row "MAT-IXPR-01218 Carlos Aguilar Arriaga AUAC200210HMCGRR00 Ixtapan de la Sal PRIMARIA Primer grado B 2026-08-24 Editar este registro" [ref=e573] [cursor=pointer]:
                    - cell "MAT-IXPR-01218" [ref=e574]
                    - cell "Carlos Aguilar Arriaga" [ref=e575]
                    - cell "AUAC200210HMCGRR00" [ref=e576]
                    - cell [ref=e577]
                    - cell "Ixtapan de la Sal" [ref=e578]
                    - cell "PRIMARIA" [ref=e579]
                    - cell "Primer grado" [ref=e580]
                    - cell "B" [ref=e581]
                    - cell "2026-08-24" [ref=e582]
                    - cell "Editar este registro" [ref=e583]:
                      - button "Editar este registro" [ref=e585]:
                        - generic [ref=e586]: 
                  - row "MAT-MEPR-00191 Citlali Aguilar Zermeño AUZC201113MMCGRT00 Metepec PRIMARIA Primer grado A 2026-08-24 Editar este registro" [ref=e587] [cursor=pointer]:
                    - cell "MAT-MEPR-00191" [ref=e588]
                    - cell "Citlali Aguilar Zermeño" [ref=e589]
                    - cell "AUZC201113MMCGRT00" [ref=e590]
                    - cell [ref=e591]
                    - cell "Metepec" [ref=e592]
                    - cell "PRIMARIA" [ref=e593]
                    - cell "Primer grado" [ref=e594]
                    - cell "A" [ref=e595]
                    - cell "2026-08-24" [ref=e596]
                    - cell "Editar este registro" [ref=e597]:
                      - button "Editar este registro" [ref=e599]:
                        - generic [ref=e600]: 
                  - row "MAT-TEPR-01126 Constanza Aguilar Farías AUFC151002MMCGRN00 Tenancingo PRIMARIA Sexto grado A 2026-08-24 Editar este registro" [ref=e601] [cursor=pointer]:
                    - cell "MAT-TEPR-01126" [ref=e602]
                    - cell "Constanza Aguilar Farías" [ref=e603]
                    - cell "AUFC151002MMCGRN00" [ref=e604]
                    - cell [ref=e605]
                    - cell "Tenancingo" [ref=e606]
                    - cell "PRIMARIA" [ref=e607]
                    - cell "Sexto grado" [ref=e608]
                    - cell "A" [ref=e609]
                    - cell "2026-08-24" [ref=e610]
                    - cell "Editar este registro" [ref=e611]:
                      - button "Editar este registro" [ref=e613]:
                        - generic [ref=e614]: 
                  - row "MAT-TEPA-00943 Elena Aguilar Rosales AURE100922MMCGSL00 Tenancingo PREPARATORIA Segundo semestre B 2026-08-24 Editar este registro" [ref=e615] [cursor=pointer]:
                    - cell "MAT-TEPA-00943" [ref=e616]
                    - cell "Elena Aguilar Rosales" [ref=e617]
                    - cell "AURE100922MMCGSL00" [ref=e618]
                    - cell [ref=e619]
                    - cell "Tenancingo" [ref=e620]
                    - cell "PREPARATORIA" [ref=e621]
                    - cell "Segundo semestre" [ref=e622]
                    - cell "B" [ref=e623]
                    - cell "2026-08-24" [ref=e624]
                    - cell "Editar este registro" [ref=e625]:
                      - button "Editar este registro" [ref=e627]:
                        - generic [ref=e628]: 
                  - row "MAT-MEPA-00966 Elena Aguilar Yáñez AUYE091214MMCGNL00 Metepec PREPARATORIA Tercer semestre A 2026-08-24 Editar este registro" [ref=e629] [cursor=pointer]:
                    - cell "MAT-MEPA-00966" [ref=e630]
                    - cell "Elena Aguilar Yáñez" [ref=e631]
                    - cell "AUYE091214MMCGNL00" [ref=e632]
                    - cell [ref=e633]
                    - cell "Metepec" [ref=e634]
                    - cell "PREPARATORIA" [ref=e635]
                    - cell "Tercer semestre" [ref=e636]
                    - cell "A" [ref=e637]
                    - cell "2026-08-24" [ref=e638]
                    - cell "Editar este registro" [ref=e639]:
                      - button "Editar este registro" [ref=e641]:
                        - generic [ref=e642]: 
                  - row "MAT-TESE-00763 Genaro Aguilar Villaseñor AUVG120921HMCGLN00 Tenancingo SECUNDARIA Tercer grado B 2026-08-24 Editar este registro" [ref=e643] [cursor=pointer]:
                    - cell "MAT-TESE-00763" [ref=e644]
                    - cell "Genaro Aguilar Villaseñor" [ref=e645]
                    - cell "AUVG120921HMCGLN00" [ref=e646]
                    - cell [ref=e647]
                    - cell "Tenancingo" [ref=e648]
                    - cell "SECUNDARIA" [ref=e649]
                    - cell "Tercer grado" [ref=e650]
                    - cell "B" [ref=e651]
                    - cell "2026-08-24" [ref=e652]
                    - cell "Editar este registro" [ref=e653]:
                      - button "Editar este registro" [ref=e655]:
                        - generic [ref=e656]: 
                  - row "MAT-IXPR-01189 Héctor Aguilar Ramírez AURH200524HMCGMC00 Ixtapan de la Sal PRIMARIA Primer grado A 2026-08-24 Editar este registro" [ref=e657] [cursor=pointer]:
                    - cell "MAT-IXPR-01189" [ref=e658]
                    - cell "Héctor Aguilar Ramírez" [ref=e659]
                    - cell "AURH200524HMCGMC00" [ref=e660]
                    - cell [ref=e661]
                    - cell "Ixtapan de la Sal" [ref=e662]
                    - cell "PRIMARIA" [ref=e663]
                    - cell "Primer grado" [ref=e664]
                    - cell "A" [ref=e665]
                    - cell "2026-08-24" [ref=e666]
                    - cell "Editar este registro" [ref=e667]:
                      - button "Editar este registro" [ref=e669]:
                        - generic [ref=e670]: 
                  - row "MAT-TEPR-01083 Ignacio Aguilar Rodríguez AURI161207HMCGDG00 Tenancingo PRIMARIA Quinto grado A 2026-08-24 Editar este registro" [ref=e671] [cursor=pointer]:
                    - cell "MAT-TEPR-01083" [ref=e672]
                    - cell "Ignacio Aguilar Rodríguez" [ref=e673]
                    - cell "AURI161207HMCGDG00" [ref=e674]
                    - cell [ref=e675]
                    - cell "Tenancingo" [ref=e676]
                    - cell "PRIMARIA" [ref=e677]
                    - cell "Quinto grado" [ref=e678]
                    - cell "A" [ref=e679]
                    - cell "2026-08-24" [ref=e680]
                    - cell "Editar este registro" [ref=e681]:
                      - button "Editar este registro" [ref=e683]:
                        - generic [ref=e684]: 
                  - row "MAT-IXPR-01503 Miguel Aguilar Acosta AUAM180418HMCGCG00 Ixtapan de la Sal PRIMARIA Tercer grado B 2026-08-24 Editar este registro" [ref=e685] [cursor=pointer]:
                    - cell "MAT-IXPR-01503" [ref=e686]
                    - cell "Miguel Aguilar Acosta" [ref=e687]
                    - cell "AUAM180418HMCGCG00" [ref=e688]
                    - cell [ref=e689]
                    - cell "Ixtapan de la Sal" [ref=e690]
                    - cell "PRIMARIA" [ref=e691]
                    - cell "Tercer grado" [ref=e692]
                    - cell "B" [ref=e693]
                    - cell "2026-08-24" [ref=e694]
                    - cell "Editar este registro" [ref=e695]:
                      - button "Editar este registro" [ref=e697]:
                        - generic [ref=e698]: 
                  - row "MAT-IXPR-01234 Renato Aguilar Magaña AUMR190816HMCGGN00 Ixtapan de la Sal PRIMARIA Segundo grado A 2026-08-24 Editar este registro" [ref=e699] [cursor=pointer]:
                    - cell "MAT-IXPR-01234" [ref=e700]
                    - cell "Renato Aguilar Magaña" [ref=e701]
                    - cell "AUMR190816HMCGGN00" [ref=e702]
                    - cell [ref=e703]
                    - cell "Ixtapan de la Sal" [ref=e704]
                    - cell "PRIMARIA" [ref=e705]
                    - cell "Segundo grado" [ref=e706]
                    - cell "A" [ref=e707]
                    - cell "2026-08-24" [ref=e708]
                    - cell "Editar este registro" [ref=e709]:
                      - button "Editar este registro" [ref=e711]:
                        - generic [ref=e712]: 
                  - row "MAT-TESE-00710 Santiago Aguilar Macías AUMS130116HMCGCN00 Tenancingo SECUNDARIA Segundo grado B 2026-08-24 Editar este registro" [ref=e713] [cursor=pointer]:
                    - cell "MAT-TESE-00710" [ref=e714]
                    - cell "Santiago Aguilar Macías" [ref=e715]
                    - cell "AUMS130116HMCGCN00" [ref=e716]
                    - cell [ref=e717]
                    - cell "Tenancingo" [ref=e718]
                    - cell "SECUNDARIA" [ref=e719]
                    - cell "Segundo grado" [ref=e720]
                    - cell "B" [ref=e721]
                    - cell "2026-08-24" [ref=e722]
                    - cell "Editar este registro" [ref=e723]:
                      - button "Editar este registro" [ref=e725]:
                        - generic [ref=e726]: 
                  - row "MAT-TEPR-01099 Cristóbal Aguirre Delgado AUDC160913HMCGLR00 Tenancingo PRIMARIA Quinto grado B 2026-08-24 Editar este registro" [ref=e727] [cursor=pointer]:
                    - cell "MAT-TEPR-01099" [ref=e728]
                    - cell "Cristóbal Aguirre Delgado" [ref=e729]
                    - cell "AUDC160913HMCGLR00" [ref=e730]
                    - cell [ref=e731]
                    - cell "Tenancingo" [ref=e732]
                    - cell "PRIMARIA" [ref=e733]
                    - cell "Quinto grado" [ref=e734]
                    - cell "B" [ref=e735]
                    - cell "2026-08-24" [ref=e736]
                    - cell "Editar este registro" [ref=e737]:
                      - button "Editar este registro" [ref=e739]:
                        - generic [ref=e740]: 
                  - row "MAT-MEPA-01420 Gustavo Aguirre Arriaga AUAG110427HMCGRS00 Metepec PREPARATORIA Primer semestre A 2026-08-24 Editar este registro" [ref=e741] [cursor=pointer]:
                    - cell "MAT-MEPA-01420" [ref=e742]
                    - cell "Gustavo Aguirre Arriaga" [ref=e743]
                    - cell "AUAG110427HMCGRS00" [ref=e744]
                    - cell [ref=e745]
                    - cell "Metepec" [ref=e746]
                    - cell "PREPARATORIA" [ref=e747]
                    - cell "Primer semestre" [ref=e748]
                    - cell "A" [ref=e749]
                    - cell "2026-08-24" [ref=e750]
                    - cell "Editar este registro" [ref=e751]:
                      - button "Editar este registro" [ref=e753]:
                        - generic [ref=e754]: 
              - generic [ref=e755]:
                - button "Primera página":
                  - img
                - button [disabled]:
                  - img
                - generic [ref=e756]:
                  - button "Página 1" [ref=e757] [cursor=pointer]: "1"
                  - button "Página 2" [ref=e758] [cursor=pointer]: "2"
                  - button "Página 3" [ref=e759] [cursor=pointer]: "3"
                  - button "Página 4" [ref=e760] [cursor=pointer]: "4"
                  - button "Página 5" [ref=e761] [cursor=pointer]: "5"
                - button "Página siguiente" [ref=e762] [cursor=pointer]:
                  - img [ref=e763]
                - button "Última página" [ref=e765] [cursor=pointer]:
                  - img [ref=e766]
                - generic [ref=e768] [cursor=pointer]:
                  - combobox "Filas por página" [ref=e769]: "20"
                  - button "dropdown trigger" [ref=e770]:
                    - img [ref=e771]
          - generic "Nuevo Alumno"
  - complementary [ref=e773]:
    - generic [ref=e775]:
      - generic [ref=e776]: Perfil del Alumno
      - button [ref=e778] [cursor=pointer]:
        - img [ref=e779]
    - generic [ref=e781]:
      - generic [ref=e782]:
        - generic [ref=e783]: MAT-000022
        - generic [ref=e785]: ACTIVO
      - generic [ref=e786]:
        - generic [ref=e787]:
          - tablist [ref=e789]:
            - tab " Personal" [selected] [ref=e790] [cursor=pointer]:
              - generic [ref=e791]: 
              - text: Personal
            - tab " Domicilio" [ref=e792] [cursor=pointer]:
              - generic [ref=e793]: 
              - text: Domicilio
            - tab " Académico" [ref=e794] [cursor=pointer]:
              - generic [ref=e795]: 
              - text: Académico
            - tab " Salud" [ref=e796] [cursor=pointer]:
              - generic [ref=e797]: 
              - text: Salud
            - tab " Familia" [ref=e798] [cursor=pointer]:
              - generic [ref=e799]: 
              - text: Familia
            - tab " Bajas" [ref=e800] [cursor=pointer]:
              - generic [ref=e801]: 
              - text: Bajas
          - button "Siguiente" [ref=e802] [cursor=pointer]:
            - img [ref=e803]
        - tabpanel " Personal" [ref=e805]:
          - generic [ref=e806]:
            - heading "Identificación" [level=4] [ref=e807]
            - generic [ref=e808]:
              - generic [ref=e809]: Nombre(s)
              - textbox [ref=e810]: Camila
            - generic [ref=e811]:
              - generic [ref=e812]: Apellido paterno
              - textbox [ref=e813]: Cardona Galindo
            - generic [ref=e814]:
              - generic [ref=e815]: Apellido materno
              - textbox [ref=e816]
            - generic [ref=e817]:
              - generic [ref=e818]: CURP
              - textbox [ref=e819]: TUWXFQ031015MJLQYE
            - generic [ref=e820]:
              - generic [ref=e821]: Género (legal)
              - generic [ref=e822] [cursor=pointer]:
                - combobox "Seleccionar…" [ref=e823]
                - button "dropdown trigger" [ref=e824]:
                  - img [ref=e825]
            - generic [ref=e827]:
              - generic [ref=e828]:
                - text: Nombre social
                - generic [ref=e829]: 
              - textbox "Opcional — solo si difiere del nombre legal" [ref=e830]
            - generic [ref=e831]:
              - generic [ref=e832]: Género autopercibido
              - generic [ref=e833] [cursor=pointer]:
                - combobox "Seleccionar…" [ref=e834]
                - button "dropdown trigger" [ref=e835]:
                  - img [ref=e836]
            - generic [ref=e838]:
              - generic [ref=e839]: Pronombres
              - 'textbox "Ej: él/sus, ella/sus, elle/sus" [ref=e840]'
            - generic [ref=e841]:
              - generic [ref=e842]: Fecha nacimiento
              - combobox [ref=e844]
            - generic [ref=e845]:
              - generic [ref=e846]: Estado civil
              - generic [ref=e847] [cursor=pointer]:
                - combobox [ref=e848]
                - button "dropdown trigger" [ref=e849]:
                  - img [ref=e850]
            - generic [ref=e852]:
              - generic [ref=e853]: Nacionalidad
              - generic [ref=e854] [cursor=pointer]:
                - combobox "Seleccionar..." [ref=e855]
                - img [ref=e856]
                - button "dropdown trigger" [ref=e858]:
                  - img [ref=e859]
          - generic [ref=e861]:
            - heading "Lugar de nacimiento" [level=4] [ref=e862]
            - generic [ref=e863]:
              - generic [ref=e864]: País
              - generic [ref=e865] [cursor=pointer]:
                - combobox "México" [ref=e866]
                - img [ref=e867]
                - button "dropdown trigger" [ref=e869]:
                  - img [ref=e870]
            - generic [ref=e872]:
              - generic [ref=e873]: Estado
              - generic [ref=e874] [cursor=pointer]:
                - combobox "Seleccionar estado…" [ref=e875]
                - img [ref=e876]
                - button "dropdown trigger" [ref=e878]:
                  - img [ref=e879]
            - generic [ref=e881]:
              - generic [ref=e882]: Municipio
              - generic:
                - combobox "Seleccionar municipio…" [disabled]
                - button "dropdown trigger":
                  - img
        - text:       
      - generic [ref=e883]:
        - button "Cancelar" [ref=e885] [cursor=pointer]:
          - generic [ref=e886]: Cancelar
        - button " Guardar cambios" [ref=e888] [cursor=pointer]:
          - generic [ref=e889]: 
          - generic [ref=e890]: Guardar cambios
```

# Test source

```ts
  1   | /**
  2   |  * Suite de UI Fuzzing — Simulación de comportamiento humano caótico
  3   |  * Genera inputs aleatorios y secuencias inesperadas de forma sistemática
  4   |  */
  5   | import { test, expect, Page } from '@playwright/test';
  6   | import { LoginPage } from '../page-objects/login-page';
  7   | import { AlumnosPage } from '../page-objects/alumnos-page';
  8   | import { USERS } from '../fixtures/users';
  9   | import {
  10  |   faker, EDGE_STRINGS, curpValido, alumnoValido,
  11  |   EMAILS_INVALIDOS, CAL_INVALIDAS,
  12  | } from '../fixtures/data-generators';
  13  | 
  14  | // ── 1. Fuzzing de formularios ─────────────────────────────────────────────────
  15  | 
  16  | test.describe('Fuzz: formulario de alumnos', () => {
  17  |   test('FUZZ-01 | 30 alumnos con datos aleatorios — ninguno crashea la app', async ({ page }) => {
  18  |     await new LoginPage(page).login(USERS.COORDINADOR);
  19  |     const ap = new AlumnosPage(page);
  20  |     await ap.navigate();
  21  | 
  22  |     const results = { ok: 0, error: 0, crash: 0 };
  23  |     page.on('dialog', d => d.dismiss());
  24  | 
  25  |     for (let i = 0; i < 30; i++) {
  26  |       try {
  27  |         await ap.openNewForm();
  28  | 
  29  |         // Alternar entre datos válidos e inválidos
  30  |         const useValid = i % 3 !== 0;
  31  |         const curp  = useValid ? curpValido() : faker.string.alphanumeric(faker.number.int({ min: 0, max: 30 }));
  32  |         const nombre = faker.helpers.arrayElement([
  33  |           faker.person.firstName(),
  34  |           EDGE_STRINGS.EMOJIS,
  35  |           faker.string.alpha(faker.number.int({ min: 0, max: 500 })),
  36  |           EDGE_STRINGS.SQL_INJECTION,
  37  |           EDGE_STRINGS.XSS_BASIC,
  38  |         ]);
  39  | 
  40  |         await ap.curpInput.fill(curp);
  41  |         await ap.nombreInput.fill(nombre);
  42  |         await ap.apPaternoInput.fill(faker.person.lastName());
  43  |         // El formulario básico de alta no incluye fecha_nacimiento
  44  |         // Si el campo existe, se llena; si no, se omite
  45  |         const hasFechaNac = await ap.fechaNacInput.isVisible().catch(() => false);
  46  |         if (hasFechaNac) {
  47  |           await ap.fechaNacInput.fill(
  48  |             faker.helpers.arrayElement(['2010-05-15', '99-99-9999', faker.string.alpha(10), ''])
  49  |           );
  50  |         }
  51  | 
  52  |         await ap.saveBtn.click();
  53  |         await page.waitForTimeout(800);
  54  | 
  55  |         const isSuccess = await page.locator('.p-toast-message-success').isVisible();
  56  |         const isError   = await page.locator('.p-toast-message-error').isVisible();
  57  |         if (isSuccess) results.ok++;
  58  |         else if (isError) results.error++;
  59  | 
  60  |         // Cerrar dialog abierto
  61  |         await page.keyboard.press('Escape');
  62  |         await page.waitForTimeout(400);
  63  |         // Cerrar toasts
  64  |         await page.locator('.p-toast-close-button').all().then(btns =>
  65  |           Promise.all(btns.map(b => b.click().catch(() => undefined)))
  66  |         );
  67  | 
  68  |       } catch (e) {
  69  |         // Solo contar como crash si la app navega a URL de error
  70  |         const url = page.url();
  71  |         if (url.includes('fatal') || url.includes('exception')) {
  72  |           results.crash++;
  73  |         } else {
  74  |           results.error++; // timeout/locator = no crash, solo fallo de test
  75  |         }
  76  |         // Intentar recuperar el estado antes del siguiente ciclo
  77  |         try {
  78  |           await page.keyboard.press('Escape').catch(() => undefined);
  79  |           await page.waitForTimeout(300);
  80  |         } catch (innerE) {
  81  |           // Si el page está cerrado, detener el loop
  82  |           if ((innerE as Error)?.message?.includes('closed')) {
  83  |             console.error(`[FUZZ-01] Page closed at iteration ${i}, stopping`);
  84  |             results.crash++;
  85  |             break;
  86  |           }
  87  |         }
  88  |       }
  89  |     }
  90  | 
  91  |     console.log('Fuzz results:', results);
> 92  |     expect(results.crash).toBe(0); // No debe haber crashes de navegación fatal
      |                           ^ Error: expect(received).toBe(expected) // Object.is equality
  93  |     expect(page.url()).not.toMatch(/fatal|exception/);
  94  |   });
  95  | });
  96  | 
  97  | // ── 2. Fuzzing de búsqueda ────────────────────────────────────────────────────
  98  | 
  99  | test.describe('Fuzz: búsqueda y filtros', () => {
  100 |   const searchInputs = [
  101 |     '',
  102 |     ' ',
  103 |     'a',
  104 |     'García López',
  105 |     EDGE_STRINGS.SQL_INJECTION,
  106 |     EDGE_STRINGS.XSS_BASIC,
  107 |     EDGE_STRINGS.EMOJIS,
  108 |     EDGE_STRINGS.LONG_1000.slice(0, 100),
  109 |     '% _ %',          // SQL wildcards
  110 |     '\\n\\t\\r',      // escape sequences
  111 |     '日本語',          // Japonés
  112 |     'مرحبا',          // Árabe
  113 |     '123456789',
  114 |     '-1',
  115 |     'null',
  116 |     'undefined',
  117 |     'true',
  118 |     '{}',
  119 |     '[]',
  120 |     '<>',
  121 |   ];
  122 | 
  123 |   for (const input of searchInputs) {
  124 |     test(`FUZZ-02 | búsqueda: "${input.slice(0, 30)}"`, async ({ page }) => {
  125 |       await new LoginPage(page).login(USERS.COORDINADOR);
  126 |       const ap = new AlumnosPage(page);
  127 |       await ap.navigate();
  128 | 
  129 |       page.on('dialog', d => d.dismiss());
  130 |       await ap.searchFor(input);
  131 |       await page.waitForTimeout(1_000);
  132 | 
  133 |       // La app no debe crashear
  134 |       await expect(page).not.toHaveURL(/error.*fatal/);
  135 |       await expect(ap.table).toBeVisible();
  136 |     });
  137 |   }
  138 | });
  139 | 
  140 | // ── 3. Fuzzing de fechas ──────────────────────────────────────────────────────
  141 | 
  142 | test.describe('Fuzz: campos de fecha', () => {
  143 |   const badDates = [
  144 |     '00-00-0000',
  145 |     '99/99/9999',
  146 |     '2026-13-01',
  147 |     '2026-02-30',
  148 |     '1900-01-01',
  149 |     '9999-12-31',
  150 |     '-2026-01-01',
  151 |     'hoy',
  152 |     'mañana',
  153 |     EDGE_STRINGS.SQL_INJECTION,
  154 |     EDGE_STRINGS.EMOJIS,
  155 |     '2026',
  156 |     '2026-01',
  157 |     '01/01/26',
  158 |     '1-1-26',
  159 |     new Array(50).fill('2').join(''),
  160 |   ];
  161 | 
  162 |   test('FUZZ-03 | 16 fechas inválidas en campo fecha — ninguna crashea', async ({ page }) => {
  163 |     await new LoginPage(page).login(USERS.COORDINADOR);
  164 |     const ap = new AlumnosPage(page);
  165 |     await ap.navigate();
  166 |     await ap.openNewForm();
  167 | 
  168 |     // El formulario de alta de alumno no incluye fecha_nacimiento.
  169 |     // Si existe, probar inputs extremos; si no, verificar que la app no crashea.
  170 |     const hasFechaNac = await ap.fechaNacInput.isVisible({ timeout: 2_000 }).catch(() => false);
  171 |     if (!hasFechaNac) {
  172 |       await expect(page).not.toHaveURL(/error/);
  173 |       return;
  174 |     }
  175 | 
  176 |     for (const date of badDates) {
  177 |       await ap.fechaNacInput.fill(date);
  178 |       await ap.fechaNacInput.blur();
  179 |       await page.waitForTimeout(200);
  180 |       await expect(page).not.toHaveURL(/error/);
  181 |     }
  182 |   });
  183 | });
  184 | 
  185 | // ── 4. Fuzzing de calificaciones ──────────────────────────────────────────────
  186 | 
  187 | test.describe('Fuzz: inputs de calificación', () => {
  188 |   const allInvalidCals = [
  189 |     ...CAL_INVALIDAS.SEP.map(v => String(v)),
  190 |     ...CAL_INVALIDAS.UAEMEX.map(v => String(v)),
  191 |     EDGE_STRINGS.SQL_INJECTION,
  192 |     EDGE_STRINGS.XSS_BASIC,
```