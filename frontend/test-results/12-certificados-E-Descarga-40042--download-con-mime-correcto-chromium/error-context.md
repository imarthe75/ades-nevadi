# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: 12-certificados.spec.ts >> E. Descarga de PDF de certificado >> CER-E2E-10 | botón descargar PDF lanza download con mime correcto
- Location: e2e/tests/12-certificados.spec.ts:270:7

# Error details

```
Test timeout of 30000ms exceeded.
```

```
Error: page.goto: Test timeout of 30000ms exceeded.
Call log:
  - navigating to "http://localhost:4200/certificados", waiting until "networkidle"

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
      - button "Cuenta de Test DIRECTOR" [ref=e45] [cursor=pointer]:
        - generic [ref=e46]: T
        - generic [ref=e47]:
          - generic [ref=e48]: Test DIRECTOR
          - generic [ref=e49]: Director(a)
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
          - link " Profesores" [ref=e74] [cursor=pointer]:
            - /url: /profesores
            - generic [ref=e75]: 
            - generic [ref=e76]: Profesores
        - listitem [ref=e77]:
          - link " Grupos" [ref=e78] [cursor=pointer]:
            - /url: /grupos
            - generic [ref=e79]: 
            - generic [ref=e80]: Grupos
        - listitem [ref=e81]:
          - link " Aulas" [ref=e82] [cursor=pointer]:
            - /url: /aulas
            - generic [ref=e83]: 
            - generic [ref=e84]: Aulas
        - listitem [ref=e85]:
          - link " Planes de Estudio" [ref=e86] [cursor=pointer]:
            - /url: /planes-estudio
            - generic [ref=e87]: 
            - generic [ref=e88]: Planes de Estudio
        - listitem [ref=e89]:
          - link " Calificaciones" [ref=e90] [cursor=pointer]:
            - /url: /calificaciones
            - generic [ref=e91]: 
            - generic [ref=e92]: Calificaciones
        - listitem [ref=e93]:
          - link " Evaluaciones" [ref=e94] [cursor=pointer]:
            - /url: /evaluaciones
            - generic [ref=e95]: 
            - generic [ref=e96]: Evaluaciones
        - listitem [ref=e97]:
          - link " Asistencias" [ref=e98] [cursor=pointer]:
            - /url: /asistencias
            - generic [ref=e99]: 
            - generic [ref=e100]: Asistencias
        - listitem [ref=e101]:
          - link " Tareas" [ref=e102] [cursor=pointer]:
            - /url: /tareas
            - generic [ref=e103]: 
            - generic [ref=e104]: Tareas
        - listitem [ref=e105]:
          - link " Planeación" [ref=e106] [cursor=pointer]:
            - /url: /planeacion
            - generic [ref=e107]: 
            - generic [ref=e108]: Planeación
      - generic [ref=e109]: Operaciones
      - list [ref=e110]:
        - listitem [ref=e111]:
          - link " Horarios" [ref=e112] [cursor=pointer]:
            - /url: /horarios
            - generic [ref=e113]: 
            - generic [ref=e114]: Horarios
        - listitem [ref=e115]:
          - link " Calendario Escolar" [ref=e116] [cursor=pointer]:
            - /url: /calendario
            - generic [ref=e117]: 
            - generic [ref=e118]: Calendario Escolar
        - listitem [ref=e119]:
          - link " Conducta" [ref=e120] [cursor=pointer]:
            - /url: /conducta
            - generic [ref=e121]: 
            - generic [ref=e122]: Conducta
        - listitem [ref=e123]:
          - link " Expediente Médico" [ref=e124] [cursor=pointer]:
            - /url: /medico
            - generic [ref=e125]: 
            - generic [ref=e126]: Expediente Médico
        - listitem [ref=e127]:
          - link " Condiciones Crónicas" [ref=e128] [cursor=pointer]:
            - /url: /condiciones-cronicas
            - generic [ref=e129]: 
            - generic [ref=e130]: Condiciones Crónicas
        - listitem [ref=e131]:
          - link " Justificaciones Faltas" [ref=e132] [cursor=pointer]:
            - /url: /justificaciones
            - generic [ref=e133]: 
            - generic [ref=e134]: Justificaciones Faltas
        - listitem [ref=e135]:
          - link " Movilidad Estudiantil" [ref=e136] [cursor=pointer]:
            - /url: /movilidad
            - generic [ref=e137]: 
            - generic [ref=e138]: Movilidad Estudiantil
        - listitem [ref=e139]:
          - link " Biblioteca" [ref=e140] [cursor=pointer]:
            - /url: /biblioteca
            - generic [ref=e141]: 
            - generic [ref=e142]: Biblioteca
        - listitem [ref=e143]:
          - link " Formato 911 SEP" [ref=e144] [cursor=pointer]:
            - /url: /estadistica-911
            - generic [ref=e145]: 
            - generic [ref=e146]: Formato 911 SEP
        - listitem [ref=e147]:
          - link " Kardex UAEMEX" [ref=e148] [cursor=pointer]:
            - /url: /kardex
            - generic [ref=e149]: 
            - generic [ref=e150]: Kardex UAEMEX
        - listitem [ref=e151]:
          - link " Acta Evaluación UAEMEX" [ref=e152] [cursor=pointer]:
            - /url: /acta-evaluacion
            - generic [ref=e153]: 
            - generic [ref=e154]: Acta Evaluación UAEMEX
        - listitem [ref=e155]:
          - link " Optativas" [ref=e156] [cursor=pointer]:
            - /url: /optativas
            - generic [ref=e157]: 
            - generic [ref=e158]: Optativas
        - listitem [ref=e159]:
          - link " Admisión" [ref=e160] [cursor=pointer]:
            - /url: /admision
            - generic [ref=e161]: 
            - generic [ref=e162]: Admisión
      - generic [ref=e163]: Recursos Humanos
      - list [ref=e164]:
        - listitem [ref=e165]:
          - link " Personal No-Docente" [ref=e166] [cursor=pointer]:
            - /url: /personal-admin
            - generic [ref=e167]: 
            - generic [ref=e168]: Personal No-Docente
        - listitem [ref=e169]:
          - link " Licencias y Permisos" [ref=e170] [cursor=pointer]:
            - /url: /licencias
            - generic [ref=e171]: 
            - generic [ref=e172]: Licencias y Permisos
        - listitem [ref=e173]:
          - link " Capacitaciones" [ref=e174] [cursor=pointer]:
            - /url: /capacitaciones
            - generic [ref=e175]: 
            - generic [ref=e176]: Capacitaciones
        - listitem [ref=e177]:
          - link " Expediente Laboral" [ref=e178] [cursor=pointer]:
            - /url: /expediente-laboral
            - generic [ref=e179]: 
            - generic [ref=e180]: Expediente Laboral
        - listitem [ref=e181]:
          - link " Disponibilidad Docente" [ref=e182] [cursor=pointer]:
            - /url: /disponibilidad
            - generic [ref=e183]: 
            - generic [ref=e184]: Disponibilidad Docente
        - listitem [ref=e185]:
          - link " Asistencia Personal" [ref=e186] [cursor=pointer]:
            - /url: /asistencia-personal
            - generic [ref=e187]: 
            - generic [ref=e188]: Asistencia Personal
      - generic [ref=e189]: Comunicación
      - list [ref=e190]:
        - listitem [ref=e191]:
          - link " Comunicados" [ref=e192] [cursor=pointer]:
            - /url: /comunicados
            - generic [ref=e193]: 
            - generic [ref=e194]: Comunicados
        - listitem [ref=e195]:
          - link " Foros y Anuncios" [ref=e196] [cursor=pointer]:
            - /url: /foros
            - generic [ref=e197]: 
            - generic [ref=e198]: Foros y Anuncios
        - listitem [ref=e199]:
          - link " Encuestas" [ref=e200] [cursor=pointer]:
            - /url: /encuestas
            - generic [ref=e201]: 
            - generic [ref=e202]: Encuestas
        - listitem [ref=e203]:
          - link " Videoconferencias" [ref=e204] [cursor=pointer]:
            - /url: /videoconferencias
            - generic [ref=e205]: 
            - generic [ref=e206]: Videoconferencias
      - generic [ref=e207]: Gradebook
      - list [ref=e208]:
        - listitem [ref=e209]:
          - link " Gradebook" [ref=e210] [cursor=pointer]:
            - /url: /gradebook
            - generic [ref=e211]: 
            - generic [ref=e212]: Gradebook
        - listitem [ref=e213]:
          - link " Mi Progreso" [ref=e214] [cursor=pointer]:
            - /url: /mi-progreso
            - generic [ref=e215]: 
            - generic [ref=e216]: Mi Progreso
        - listitem [ref=e217]:
          - link " Ponderaciones" [ref=e218] [cursor=pointer]:
            - /url: /ponderacion-config
            - generic [ref=e219]: 
            - generic [ref=e220]: Ponderaciones
      - generic [ref=e221]: Recursos
      - list [ref=e222]:
        - listitem [ref=e223]:
          - link " Rúbricas" [ref=e224] [cursor=pointer]:
            - /url: /rubricas
            - generic [ref=e225]: 
            - generic [ref=e226]: Rúbricas
        - listitem [ref=e227]:
          - link " Insignias" [ref=e228] [cursor=pointer]:
            - /url: /badges
            - generic [ref=e229]: 
            - generic [ref=e230]: Insignias
        - listitem [ref=e231]:
          - link " Portal Alumno" [ref=e232] [cursor=pointer]:
            - /url: /portal
            - generic [ref=e233]: 
            - generic [ref=e234]: Portal Alumno
        - listitem [ref=e235]:
          - link " Contenido H5P" [ref=e236] [cursor=pointer]:
            - /url: /h5p
            - generic [ref=e237]: 
            - generic [ref=e238]: Contenido H5P
      - generic [ref=e239]: Convocatorias
      - list [ref=e240]:
        - listitem [ref=e241]:
          - link " Gestión Convocatorias" [ref=e242] [cursor=pointer]:
            - /url: /portal-admin
            - generic [ref=e243]: 
            - generic [ref=e244]: Gestión Convocatorias
      - generic [ref=e245]: Inteligencia
      - list [ref=e246]:
        - listitem [ref=e247]:
          - link " Dashboards BI" [ref=e248] [cursor=pointer]:
            - /url: /bi
            - generic [ref=e249]: 
            - generic [ref=e250]: Dashboards BI
        - listitem [ref=e251]:
          - link " Grade Analytics" [ref=e252] [cursor=pointer]:
            - /url: /grade-analytics
            - generic [ref=e253]: 
            - generic [ref=e254]: Grade Analytics
        - listitem [ref=e255]:
          - link " Asistente IA + Datos" [ref=e256] [cursor=pointer]:
            - /url: /ia
            - generic [ref=e257]: 
            - generic [ref=e258]: Asistente IA + Datos
        - listitem [ref=e259]:
          - link " Eval. Docente 360°" [ref=e260] [cursor=pointer]:
            - /url: /eval-docente
            - generic [ref=e261]: 
            - generic [ref=e262]: Eval. Docente 360°
        - listitem [ref=e263]:
          - link " Learning Paths" [ref=e264] [cursor=pointer]:
            - /url: /learning-paths
            - generic [ref=e265]: 
            - generic [ref=e266]: Learning Paths
      - generic [ref=e267]: Reportes
      - list [ref=e268]:
        - listitem [ref=e269]:
          - link " Generador de Reportes" [ref=e270] [cursor=pointer]:
            - /url: /reportes
            - generic [ref=e271]: 
            - generic [ref=e272]: Generador de Reportes
        - listitem [ref=e273]:
          - link " Certificados Digitales" [ref=e274] [cursor=pointer]:
            - /url: /certificados
            - generic [ref=e275]: 
            - generic [ref=e276]: Certificados Digitales
        - listitem [ref=e277]:
          - link " Expediente Digital" [ref=e278] [cursor=pointer]:
            - /url: /expediente-doc
            - generic [ref=e279]: 
            - generic [ref=e280]: Expediente Digital
      - generic [ref=e281]: Ayuda
      - list [ref=e282]:
        - listitem [ref=e283]:
          - link " Manual de Usuario" [ref=e284] [cursor=pointer]:
            - /url: /ayuda
            - generic [ref=e285]: 
            - generic [ref=e286]: Manual de Usuario
    - main [ref=e287]:
      - navigation "Breadcrumb" [ref=e290]:
        - list [ref=e291]:
          - listitem [ref=e292]:
            - link "Home" [ref=e293] [cursor=pointer]:
              - /url: /
          - listitem [ref=e294]:
            - generic [ref=e295]: 
            - generic [ref=e296]: Certificados
      - generic [ref=e297]:
        - generic [ref=e299]:
          - heading "Certificados Digitales" [level=2] [ref=e300]
          - paragraph [ref=e301]: Emisión y verificación Ed25519 · Instituto Nevadi
        - generic [ref=e302]:
          - generic [ref=e303]:
            - generic [ref=e304]: "50"
            - text: Total emitidos
          - generic [ref=e305]:
            - generic [ref=e306]: "0"
            - text: Firmados Ed25519
          - generic [ref=e307]:
            - generic [ref=e308]: "50"
            - text: Pendientes firma
        - generic [ref=e309]:
          - table [ref=e311]:
            - rowgroup [ref=e312]:
              - row "Folio Alumno Tipo Nivel / Grado Ciclo Promedio Fecha Estado Firma Anclaje Blockchain Acciones" [ref=e313]:
                - columnheader "Folio" [ref=e314] [cursor=pointer]:
                  - text: Folio
                  - img [ref=e316]
                - columnheader "Alumno" [ref=e322] [cursor=pointer]:
                  - text: Alumno
                  - img [ref=e324]
                - columnheader "Tipo" [ref=e330] [cursor=pointer]:
                  - text: Tipo
                  - img [ref=e332]
                - columnheader "Nivel / Grado" [ref=e338]
                - columnheader "Ciclo" [ref=e339]
                - columnheader "Promedio" [ref=e340]
                - columnheader "Fecha" [ref=e341] [cursor=pointer]:
                  - text: Fecha
                  - img [ref=e343]
                - columnheader "Estado Firma" [ref=e349]
                - columnheader "Anclaje Blockchain" [ref=e350]
                - columnheader "Acciones" [ref=e351]
            - rowgroup [ref=e352]:
              - row "CERT-E52448242B Aarón Vela Valdivia CERTIFICADO_NIVEL SECUNDARIA · 3 2026-2027 7.9 16/07/2027 Pendiente  PENDIENTE " [ref=e353]:
                - cell "CERT-E52448242B" [ref=e354]:
                  - code [ref=e355]: CERT-E52448242B
                - cell "Aarón Vela Valdivia" [ref=e356]
                - cell "CERTIFICADO_NIVEL" [ref=e357]
                - cell "SECUNDARIA · 3" [ref=e358]
                - cell "2026-2027" [ref=e359]
                - cell "7.9" [ref=e360]
                - cell "16/07/2027" [ref=e361]
                - cell "Pendiente" [ref=e362]:
                  - generic [ref=e364]: Pendiente
                - cell " PENDIENTE" [ref=e365]:
                  - generic [ref=e366]:
                    - generic [ref=e367]: 
                    - generic [ref=e368]: PENDIENTE
                - cell "" [ref=e369]:
                  - button "" [ref=e371] [cursor=pointer]:
                    - generic [ref=e372]: 
              - row "CERT-82111E7D04 Efraín Galván Pineda CERTIFICADO_NIVEL SECUNDARIA · 3 2026-2027 8.2 16/07/2027 Pendiente  PENDIENTE " [ref=e373]:
                - cell "CERT-82111E7D04" [ref=e374]:
                  - code [ref=e375]: CERT-82111E7D04
                - cell "Efraín Galván Pineda" [ref=e376]
                - cell "CERTIFICADO_NIVEL" [ref=e377]
                - cell "SECUNDARIA · 3" [ref=e378]
                - cell "2026-2027" [ref=e379]
                - cell "8.2" [ref=e380]
                - cell "16/07/2027" [ref=e381]
                - cell "Pendiente" [ref=e382]:
                  - generic [ref=e384]: Pendiente
                - cell " PENDIENTE" [ref=e385]:
                  - generic [ref=e386]:
                    - generic [ref=e387]: 
                    - generic [ref=e388]: PENDIENTE
                - cell "" [ref=e389]:
                  - button "" [ref=e391] [cursor=pointer]:
                    - generic [ref=e392]: 
              - row "CERT-3B4AD1AC49 Pablo Morales Herrera CERTIFICADO_NIVEL SECUNDARIA · 3 2026-2027 8.2 16/07/2027 Pendiente  PENDIENTE " [ref=e393]:
                - cell "CERT-3B4AD1AC49" [ref=e394]:
                  - code [ref=e395]: CERT-3B4AD1AC49
                - cell "Pablo Morales Herrera" [ref=e396]
                - cell "CERTIFICADO_NIVEL" [ref=e397]
                - cell "SECUNDARIA · 3" [ref=e398]
                - cell "2026-2027" [ref=e399]
                - cell "8.2" [ref=e400]
                - cell "16/07/2027" [ref=e401]
                - cell "Pendiente" [ref=e402]:
                  - generic [ref=e404]: Pendiente
                - cell " PENDIENTE" [ref=e405]:
                  - generic [ref=e406]:
                    - generic [ref=e407]: 
                    - generic [ref=e408]: PENDIENTE
                - cell "" [ref=e409]:
                  - button "" [ref=e411] [cursor=pointer]:
                    - generic [ref=e412]: 
              - row "CERT-6256B49BD9 Genaro Valdez Montero CERTIFICADO_NIVEL SECUNDARIA · 3 2026-2027 7.5 16/07/2027 Pendiente  PENDIENTE " [ref=e413]:
                - cell "CERT-6256B49BD9" [ref=e414]:
                  - code [ref=e415]: CERT-6256B49BD9
                - cell "Genaro Valdez Montero" [ref=e416]
                - cell "CERTIFICADO_NIVEL" [ref=e417]
                - cell "SECUNDARIA · 3" [ref=e418]
                - cell "2026-2027" [ref=e419]
                - cell "7.5" [ref=e420]
                - cell "16/07/2027" [ref=e421]
                - cell "Pendiente" [ref=e422]:
                  - generic [ref=e424]: Pendiente
                - cell " PENDIENTE" [ref=e425]:
                  - generic [ref=e426]:
                    - generic [ref=e427]: 
                    - generic [ref=e428]: PENDIENTE
                - cell "" [ref=e429]:
                  - button "" [ref=e431] [cursor=pointer]:
                    - generic [ref=e432]: 
              - row "CERT-85449D61C8 Regina Naranjo Nieto CERTIFICADO_NIVEL SECUNDARIA · 3 2026-2027 8 16/07/2027 Pendiente  PENDIENTE " [ref=e433]:
                - cell "CERT-85449D61C8" [ref=e434]:
                  - code [ref=e435]: CERT-85449D61C8
                - cell "Regina Naranjo Nieto" [ref=e436]
                - cell "CERTIFICADO_NIVEL" [ref=e437]
                - cell "SECUNDARIA · 3" [ref=e438]
                - cell "2026-2027" [ref=e439]
                - cell "8" [ref=e440]
                - cell "16/07/2027" [ref=e441]
                - cell "Pendiente" [ref=e442]:
                  - generic [ref=e444]: Pendiente
                - cell " PENDIENTE" [ref=e445]:
                  - generic [ref=e446]:
                    - generic [ref=e447]: 
                    - generic [ref=e448]: PENDIENTE
                - cell "" [ref=e449]:
                  - button "" [ref=e451] [cursor=pointer]:
                    - generic [ref=e452]: 
              - row "CERT-0D0D5895F4 Jorge Meléndez Reyes CERTIFICADO_NIVEL SECUNDARIA · 3 2026-2027 7.8 16/07/2027 Pendiente  PENDIENTE " [ref=e453]:
                - cell "CERT-0D0D5895F4" [ref=e454]:
                  - code [ref=e455]: CERT-0D0D5895F4
                - cell "Jorge Meléndez Reyes" [ref=e456]
                - cell "CERTIFICADO_NIVEL" [ref=e457]
                - cell "SECUNDARIA · 3" [ref=e458]
                - cell "2026-2027" [ref=e459]
                - cell "7.8" [ref=e460]
                - cell "16/07/2027" [ref=e461]
                - cell "Pendiente" [ref=e462]:
                  - generic [ref=e464]: Pendiente
                - cell " PENDIENTE" [ref=e465]:
                  - generic [ref=e466]:
                    - generic [ref=e467]: 
                    - generic [ref=e468]: PENDIENTE
                - cell "" [ref=e469]:
                  - button "" [ref=e471] [cursor=pointer]:
                    - generic [ref=e472]: 
              - row "CERT-E828A8DED9 Cristian Almanza Maldonado CERTIFICADO_NIVEL SECUNDARIA · 3 2026-2027 8 16/07/2027 Pendiente  PENDIENTE " [ref=e473]:
                - cell "CERT-E828A8DED9" [ref=e474]:
                  - code [ref=e475]: CERT-E828A8DED9
                - cell "Cristian Almanza Maldonado" [ref=e476]
                - cell "CERTIFICADO_NIVEL" [ref=e477]
                - cell "SECUNDARIA · 3" [ref=e478]
                - cell "2026-2027" [ref=e479]
                - cell "8" [ref=e480]
                - cell "16/07/2027" [ref=e481]
                - cell "Pendiente" [ref=e482]:
                  - generic [ref=e484]: Pendiente
                - cell " PENDIENTE" [ref=e485]:
                  - generic [ref=e486]:
                    - generic [ref=e487]: 
                    - generic [ref=e488]: PENDIENTE
                - cell "" [ref=e489]:
                  - button "" [ref=e491] [cursor=pointer]:
                    - generic [ref=e492]: 
              - row "CERT-20BE738DBB Salvador Vázquez Ramírez CERTIFICADO_NIVEL SECUNDARIA · 3 2026-2027 8.2 16/07/2027 Pendiente  PENDIENTE " [ref=e493]:
                - cell "CERT-20BE738DBB" [ref=e494]:
                  - code [ref=e495]: CERT-20BE738DBB
                - cell "Salvador Vázquez Ramírez" [ref=e496]
                - cell "CERTIFICADO_NIVEL" [ref=e497]
                - cell "SECUNDARIA · 3" [ref=e498]
                - cell "2026-2027" [ref=e499]
                - cell "8.2" [ref=e500]
                - cell "16/07/2027" [ref=e501]
                - cell "Pendiente" [ref=e502]:
                  - generic [ref=e504]: Pendiente
                - cell " PENDIENTE" [ref=e505]:
                  - generic [ref=e506]:
                    - generic [ref=e507]: 
                    - generic [ref=e508]: PENDIENTE
                - cell "" [ref=e509]:
                  - button "" [ref=e511] [cursor=pointer]:
                    - generic [ref=e512]: 
              - row "CERT-F2FF3B0C18 Helena Díaz Duarte CERTIFICADO_NIVEL SECUNDARIA · 3 2026-2027 8.1 16/07/2027 Pendiente  PENDIENTE " [ref=e513]:
                - cell "CERT-F2FF3B0C18" [ref=e514]:
                  - code [ref=e515]: CERT-F2FF3B0C18
                - cell "Helena Díaz Duarte" [ref=e516]
                - cell "CERTIFICADO_NIVEL" [ref=e517]
                - cell "SECUNDARIA · 3" [ref=e518]
                - cell "2026-2027" [ref=e519]
                - cell "8.1" [ref=e520]
                - cell "16/07/2027" [ref=e521]
                - cell "Pendiente" [ref=e522]:
                  - generic [ref=e524]: Pendiente
                - cell " PENDIENTE" [ref=e525]:
                  - generic [ref=e526]:
                    - generic [ref=e527]: 
                    - generic [ref=e528]: PENDIENTE
                - cell "" [ref=e529]:
                  - button "" [ref=e531] [cursor=pointer]:
                    - generic [ref=e532]: 
              - row "CERT-319113DD3E Jorge Cervantes Cortés CERTIFICADO_NIVEL SECUNDARIA · 3 2026-2027 7.8 16/07/2027 Pendiente  PENDIENTE " [ref=e533]:
                - cell "CERT-319113DD3E" [ref=e534]:
                  - code [ref=e535]: CERT-319113DD3E
                - cell "Jorge Cervantes Cortés" [ref=e536]
                - cell "CERTIFICADO_NIVEL" [ref=e537]
                - cell "SECUNDARIA · 3" [ref=e538]
                - cell "2026-2027" [ref=e539]
                - cell "7.8" [ref=e540]
                - cell "16/07/2027" [ref=e541]
                - cell "Pendiente" [ref=e542]:
                  - generic [ref=e544]: Pendiente
                - cell " PENDIENTE" [ref=e545]:
                  - generic [ref=e546]:
                    - generic [ref=e547]: 
                    - generic [ref=e548]: PENDIENTE
                - cell "" [ref=e549]:
                  - button "" [ref=e551] [cursor=pointer]:
                    - generic [ref=e552]: 
              - row "CERT-476DE21410 Denise Vega Montoya CERTIFICADO_NIVEL SECUNDARIA · 3 2026-2027 8 16/07/2027 Pendiente  PENDIENTE " [ref=e553]:
                - cell "CERT-476DE21410" [ref=e554]:
                  - code [ref=e555]: CERT-476DE21410
                - cell "Denise Vega Montoya" [ref=e556]
                - cell "CERTIFICADO_NIVEL" [ref=e557]
                - cell "SECUNDARIA · 3" [ref=e558]
                - cell "2026-2027" [ref=e559]
                - cell "8" [ref=e560]
                - cell "16/07/2027" [ref=e561]
                - cell "Pendiente" [ref=e562]:
                  - generic [ref=e564]: Pendiente
                - cell " PENDIENTE" [ref=e565]:
                  - generic [ref=e566]:
                    - generic [ref=e567]: 
                    - generic [ref=e568]: PENDIENTE
                - cell "" [ref=e569]:
                  - button "" [ref=e571] [cursor=pointer]:
                    - generic [ref=e572]: 
              - row "CERT-402FB7EB11 Lourdes Beltrán Caballero CERTIFICADO_NIVEL SECUNDARIA · 3 2026-2027 7.9 16/07/2027 Pendiente  PENDIENTE " [ref=e573]:
                - cell "CERT-402FB7EB11" [ref=e574]:
                  - code [ref=e575]: CERT-402FB7EB11
                - cell "Lourdes Beltrán Caballero" [ref=e576]
                - cell "CERTIFICADO_NIVEL" [ref=e577]
                - cell "SECUNDARIA · 3" [ref=e578]
                - cell "2026-2027" [ref=e579]
                - cell "7.9" [ref=e580]
                - cell "16/07/2027" [ref=e581]
                - cell "Pendiente" [ref=e582]:
                  - generic [ref=e584]: Pendiente
                - cell " PENDIENTE" [ref=e585]:
                  - generic [ref=e586]:
                    - generic [ref=e587]: 
                    - generic [ref=e588]: PENDIENTE
                - cell "" [ref=e589]:
                  - button "" [ref=e591] [cursor=pointer]:
                    - generic [ref=e592]: 
              - row "CERT-33932C53FB Gilberto López Gómez CERTIFICADO_NIVEL SECUNDARIA · 3 2026-2027 8 16/07/2027 Pendiente  PENDIENTE " [ref=e593]:
                - cell "CERT-33932C53FB" [ref=e594]:
                  - code [ref=e595]: CERT-33932C53FB
                - cell "Gilberto López Gómez" [ref=e596]
                - cell "CERTIFICADO_NIVEL" [ref=e597]
                - cell "SECUNDARIA · 3" [ref=e598]
                - cell "2026-2027" [ref=e599]
                - cell "8" [ref=e600]
                - cell "16/07/2027" [ref=e601]
                - cell "Pendiente" [ref=e602]:
                  - generic [ref=e604]: Pendiente
                - cell " PENDIENTE" [ref=e605]:
                  - generic [ref=e606]:
                    - generic [ref=e607]: 
                    - generic [ref=e608]: PENDIENTE
                - cell "" [ref=e609]:
                  - button "" [ref=e611] [cursor=pointer]:
                    - generic [ref=e612]: 
              - row "CERT-37424DCEA9 Itzel Lerma Pacheco CERTIFICADO_NIVEL SECUNDARIA · 3 2026-2027 7.9 16/07/2027 Pendiente  PENDIENTE " [ref=e613]:
                - cell "CERT-37424DCEA9" [ref=e614]:
                  - code [ref=e615]: CERT-37424DCEA9
                - cell "Itzel Lerma Pacheco" [ref=e616]
                - cell "CERTIFICADO_NIVEL" [ref=e617]
                - cell "SECUNDARIA · 3" [ref=e618]
                - cell "2026-2027" [ref=e619]
                - cell "7.9" [ref=e620]
                - cell "16/07/2027" [ref=e621]
                - cell "Pendiente" [ref=e622]:
                  - generic [ref=e624]: Pendiente
                - cell " PENDIENTE" [ref=e625]:
                  - generic [ref=e626]:
                    - generic [ref=e627]: 
                    - generic [ref=e628]: PENDIENTE
                - cell "" [ref=e629]:
                  - button "" [ref=e631] [cursor=pointer]:
                    - generic [ref=e632]: 
              - row "CERT-ADAD126523 Jacqueline Plascencia Mancilla CERTIFICADO_NIVEL SECUNDARIA · 3 2026-2027 8.1 16/07/2027 Pendiente  PENDIENTE " [ref=e633]:
                - cell "CERT-ADAD126523" [ref=e634]:
                  - code [ref=e635]: CERT-ADAD126523
                - cell "Jacqueline Plascencia Mancilla" [ref=e636]
                - cell "CERTIFICADO_NIVEL" [ref=e637]
                - cell "SECUNDARIA · 3" [ref=e638]
                - cell "2026-2027" [ref=e639]
                - cell "8.1" [ref=e640]
                - cell "16/07/2027" [ref=e641]
                - cell "Pendiente" [ref=e642]:
                  - generic [ref=e644]: Pendiente
                - cell " PENDIENTE" [ref=e645]:
                  - generic [ref=e646]:
                    - generic [ref=e647]: 
                    - generic [ref=e648]: PENDIENTE
                - cell "" [ref=e649]:
                  - button "" [ref=e651] [cursor=pointer]:
                    - generic [ref=e652]: 
              - row "CERT-88594B402F Denise Partida Ochoa CERTIFICADO_NIVEL SECUNDARIA · 3 2026-2027 8.2 16/07/2027 Pendiente  PENDIENTE " [ref=e653]:
                - cell "CERT-88594B402F" [ref=e654]:
                  - code [ref=e655]: CERT-88594B402F
                - cell "Denise Partida Ochoa" [ref=e656]
                - cell "CERTIFICADO_NIVEL" [ref=e657]
                - cell "SECUNDARIA · 3" [ref=e658]
                - cell "2026-2027" [ref=e659]
                - cell "8.2" [ref=e660]
                - cell "16/07/2027" [ref=e661]
                - cell "Pendiente" [ref=e662]:
                  - generic [ref=e664]: Pendiente
                - cell " PENDIENTE" [ref=e665]:
                  - generic [ref=e666]:
                    - generic [ref=e667]: 
                    - generic [ref=e668]: PENDIENTE
                - cell "" [ref=e669]:
                  - button "" [ref=e671] [cursor=pointer]:
                    - generic [ref=e672]: 
              - row "CERT-0688AF07A0 Iker Mejía Díaz CERTIFICADO_NIVEL SECUNDARIA · 3 2026-2027 8.1 16/07/2027 Pendiente  PENDIENTE " [ref=e673]:
                - cell "CERT-0688AF07A0" [ref=e674]:
                  - code [ref=e675]: CERT-0688AF07A0
                - cell "Iker Mejía Díaz" [ref=e676]
                - cell "CERTIFICADO_NIVEL" [ref=e677]
                - cell "SECUNDARIA · 3" [ref=e678]
                - cell "2026-2027" [ref=e679]
                - cell "8.1" [ref=e680]
                - cell "16/07/2027" [ref=e681]
                - cell "Pendiente" [ref=e682]:
                  - generic [ref=e684]: Pendiente
                - cell " PENDIENTE" [ref=e685]:
                  - generic [ref=e686]:
                    - generic [ref=e687]: 
                    - generic [ref=e688]: PENDIENTE
                - cell "" [ref=e689]:
                  - button "" [ref=e691] [cursor=pointer]:
                    - generic [ref=e692]: 
              - row "CERT-78092BB707 Óscar Lozano Arellano CERTIFICADO_NIVEL SECUNDARIA · 3 2026-2027 8 16/07/2027 Pendiente  PENDIENTE " [ref=e693]:
                - cell "CERT-78092BB707" [ref=e694]:
                  - code [ref=e695]: CERT-78092BB707
                - cell "Óscar Lozano Arellano" [ref=e696]
                - cell "CERTIFICADO_NIVEL" [ref=e697]
                - cell "SECUNDARIA · 3" [ref=e698]
                - cell "2026-2027" [ref=e699]
                - cell "8" [ref=e700]
                - cell "16/07/2027" [ref=e701]
                - cell "Pendiente" [ref=e702]:
                  - generic [ref=e704]: Pendiente
                - cell " PENDIENTE" [ref=e705]:
                  - generic [ref=e706]:
                    - generic [ref=e707]: 
                    - generic [ref=e708]: PENDIENTE
                - cell "" [ref=e709]:
                  - button "" [ref=e711] [cursor=pointer]:
                    - generic [ref=e712]: 
              - row "CERT-09B341A091 Jacobo Lara Téllez CERTIFICADO_NIVEL SECUNDARIA · 3 2026-2027 7.8 16/07/2027 Pendiente  PENDIENTE " [ref=e713]:
                - cell "CERT-09B341A091" [ref=e714]:
                  - code [ref=e715]: CERT-09B341A091
                - cell "Jacobo Lara Téllez" [ref=e716]
                - cell "CERTIFICADO_NIVEL" [ref=e717]
                - cell "SECUNDARIA · 3" [ref=e718]
                - cell "2026-2027" [ref=e719]
                - cell "7.8" [ref=e720]
                - cell "16/07/2027" [ref=e721]
                - cell "Pendiente" [ref=e722]:
                  - generic [ref=e724]: Pendiente
                - cell " PENDIENTE" [ref=e725]:
                  - generic [ref=e726]:
                    - generic [ref=e727]: 
                    - generic [ref=e728]: PENDIENTE
                - cell "" [ref=e729]:
                  - button "" [ref=e731] [cursor=pointer]:
                    - generic [ref=e732]: 
              - row "CERT-121EDB8D24 Mateo Villaseñor Caballero CERTIFICADO_NIVEL SECUNDARIA · 3 2026-2027 8 16/07/2027 Pendiente  PENDIENTE " [ref=e733]:
                - cell "CERT-121EDB8D24" [ref=e734]:
                  - code [ref=e735]: CERT-121EDB8D24
                - cell "Mateo Villaseñor Caballero" [ref=e736]
                - cell "CERTIFICADO_NIVEL" [ref=e737]
                - cell "SECUNDARIA · 3" [ref=e738]
                - cell "2026-2027" [ref=e739]
                - cell "8" [ref=e740]
                - cell "16/07/2027" [ref=e741]
                - cell "Pendiente" [ref=e742]:
                  - generic [ref=e744]: Pendiente
                - cell " PENDIENTE" [ref=e745]:
                  - generic [ref=e746]:
                    - generic [ref=e747]: 
                    - generic [ref=e748]: PENDIENTE
                - cell "" [ref=e749]:
                  - button "" [ref=e751] [cursor=pointer]:
                    - generic [ref=e752]: 
          - generic [ref=e753]:
            - button "Primera página":
              - img
            - button [disabled]:
              - img
            - generic [ref=e754]:
              - button "Página 1" [ref=e755] [cursor=pointer]: "1"
              - button "Página 2" [ref=e756] [cursor=pointer]: "2"
              - button "Página 3" [ref=e757] [cursor=pointer]: "3"
            - button "Página siguiente" [ref=e758] [cursor=pointer]:
              - img [ref=e759]
            - button "Última página" [ref=e761] [cursor=pointer]:
              - img [ref=e762]
            - generic [ref=e764] [cursor=pointer]:
              - combobox "Filas por página" [ref=e765]: "20"
              - button "dropdown trigger" [ref=e766]:
                - img [ref=e767]
```

# Test source

```ts
  172 |     EDGE_STRINGS.PATH_TRAV,
  173 |     '../../../etc/passwd',
  174 |     '\x00null\x00',
  175 |     'a'.repeat(500),
  176 |     '',
  177 |     '   ',
  178 |     EDGE_STRINGS.EMOJIS,
  179 |     EDGE_STRINGS.UNICODE_MIX,
  180 |     '{}',
  181 |     '[]',
  182 |     'undefined',
  183 |     'null',
  184 |   ];
  185 | 
  186 |   for (const folio of foliosExtremos) {
  187 |     test(`CER-FUZZ | folio: "${folio.slice(0, 25).replace(/\n/g, '\\n')}"`, async ({ request }) => {
  188 |       const encoded = encodeURIComponent(folio);
  189 |       const res = await request.get(`${API_BASE}/certificados/verificar/${encoded}`);
  190 |       // Nunca debe retornar 500 ni ejecutar código
  191 |       expect(res.status()).not.toBe(500);
  192 |       expect([200, 400, 404, 422]).toContain(res.status());
  193 | 
  194 |       // Verificar que el body no contiene stack traces
  195 |       if (!res.ok()) {
  196 |         const text = await res.text().catch(() => '');
  197 |         expect(text).not.toMatch(/Traceback|NullPointerException|at mx\.ades/);
  198 |       }
  199 |     });
  200 |   }
  201 | });
  202 | 
  203 | // ── D. Verificación de campos criptográficos via API ─────────────────────────
  204 | 
  205 | test.describe('D. Integridad criptográfica via BFF', () => {
  206 |   test('CER-E2E-08 | certificado firmado tiene hash_sha256 y firma_ed25519 via API', async ({ page }) => {
  207 |     await new LoginPage(page).login(USERS.DIRECTOR);
  208 | 
  209 |     // Usar fetch en la página (IPv4) en lugar de page.request (puede resolver a IPv6)
  210 |     const result = await page.evaluate(async () => {
  211 |       const keys = ['ades_token', 'access_token', 'token'];
  212 |       let tok = '';
  213 |       for (const k of keys) { tok = sessionStorage.getItem(k) ?? ''; if (tok) break; }
  214 | 
  215 |       const res = await fetch('/api/v1/certificados', {
  216 |         headers: { Authorization: `Bearer ${tok}` },
  217 |       }).catch(() => null);
  218 |       if (!res?.ok) return null;
  219 |       return res.json().catch(() => null);
  220 |     });
  221 | 
  222 |     if (!result) { test.skip(); return; }
  223 | 
  224 |     const certificados = Array.isArray(result) ? result : (result.items ?? result.data ?? []);
  225 |     const firmados = certificados.filter((c: Record<string, unknown>) =>
  226 |       c['estado_firma'] === 'FIRMADO' || c['estado'] === 'FIRMADO'
  227 |     );
  228 | 
  229 |     if (firmados.length === 0) {
  230 |       console.log('[CER-E2E-08] Sin certificados FIRMADO en el entorno de QA — skip');
  231 |       test.skip(); return;
  232 |     }
  233 | 
  234 |     const cert = firmados[0] as Record<string, unknown>;
  235 |     expect(cert['firma_ed25519'] || cert['firmaEd25519']).toBeTruthy();
  236 |     expect(cert['hash_sha256'] || cert['hashSha256']).toBeTruthy();
  237 |   });
  238 | 
  239 |   test('CER-E2E-09 | estado esVerificable solo para FIRMADO', async ({ page }) => {
  240 |     await new LoginPage(page).login(USERS.DIRECTOR);
  241 | 
  242 |     const result = await page.evaluate(async () => {
  243 |       const keys = ['ades_token', 'access_token', 'token'];
  244 |       let tok = '';
  245 |       for (const k of keys) { tok = sessionStorage.getItem(k) ?? ''; if (tok) break; }
  246 | 
  247 |       const res = await fetch('/api/v1/certificados', {
  248 |         headers: { Authorization: `Bearer ${tok}` },
  249 |       }).catch(() => null);
  250 |       if (!res?.ok) return null;
  251 |       return res.json().catch(() => null);
  252 |     });
  253 | 
  254 |     if (!result) { test.skip(); return; }
  255 | 
  256 |     const certificados = Array.isArray(result) ? result : (result.items ?? result.data ?? []);
  257 |     const emitidos = certificados.filter((c: Record<string, unknown>) =>
  258 |       (c['estado_firma'] === 'EMITIDO' || c['estado'] === 'EMITIDO') &&
  259 |       !(c['firma_ed25519'])
  260 |     );
  261 |     for (const cert of emitidos as Record<string, unknown>[]) {
  262 |       expect(cert['es_verificable'] ?? cert['esVerificable']).toBeFalsy();
  263 |     }
  264 |   });
  265 | });
  266 | 
  267 | // ── E. PDF descarga ───────────────────────────────────────────────────────────
  268 | 
  269 | test.describe('E. Descarga de PDF de certificado', () => {
  270 |   test('CER-E2E-10 | botón descargar PDF lanza download con mime correcto', async ({ page }) => {
  271 |     await new LoginPage(page).login(USERS.DIRECTOR);
> 272 |     await page.goto('/certificados', { waitUntil: 'networkidle' });
      |                ^ Error: page.goto: Test timeout of 30000ms exceeded.
  273 | 
  274 |     // Esperar tabla de certificados
  275 |     await page.waitForSelector('p-table tbody tr, table tbody tr', { timeout: 5_000 });
  276 | 
  277 |     const filas = await page.locator('tbody tr').count();
  278 |     if (filas === 0) {
  279 |       console.log('⚠️  No hay certificados — test skipped');
  280 |       test.skip();
  281 |       return;
  282 |     }
  283 | 
  284 |     // Buscar botón descargar en primera fila
  285 |     const primeraCelda = page.locator('tbody tr').first();
  286 |     const downloadBtn = await primeraCelda.locator(
  287 |       '[data-testid="btn-descargar-pdf"], button:has-text("Descargar"), button:has-text("PDF"), [aria-label*="escargar"]'
  288 |     ).first();
  289 | 
  290 |     if (!await downloadBtn.isVisible({ timeout: 2_000 }).catch(() => false)) {
  291 |       console.log('⚠️  Botón descargar no visible — test skipped');
  292 |       test.skip();
  293 |       return;
  294 |     }
  295 | 
  296 |     // Interceptar descarga
  297 |     const downloadPromise = page.waitForEvent('download');
  298 |     await downloadBtn.click();
  299 | 
  300 |     // Esperar descarga
  301 |     const download = await downloadPromise;
  302 | 
  303 |     // Verificar nombre archivo
  304 |     const filename = download.suggestedFilename();
  305 |     expect(filename).toMatch(/\.pdf$/i);
  306 | 
  307 |     // Verificar tamaño > 1KB
  308 |     const path = await download.path();
  309 |     const fs = require('fs');
  310 |     const stats = fs.statSync(path);
  311 |     expect(stats.size).toBeGreaterThan(1024);
  312 | 
  313 |     // Verificar header PDF
  314 |     const buffer = fs.readFileSync(path);
  315 |     const header = buffer.toString('utf8', 0, 5);
  316 |     expect(header).toBe('%PDF-');
  317 | 
  318 |     console.log(`✅ CER-E2E-10 PASSED — ${filename} (${stats.size} bytes)`);
  319 |   });
  320 | });
  321 | 
```