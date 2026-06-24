# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: 02-alumnos.spec.ts >> B. Errores de validación >> ALU-03 | CURP duplicado → 409 Conflict
- Location: e2e/tests/02-alumnos.spec.ts:77:7

# Error details

```
Test timeout of 30000ms exceeded.
```

```
Error: locator.click: Test timeout of 30000ms exceeded.
Call log:
  - waiting for locator('button:has-text("Nuevo alumno"), button:has-text("Nuevo"), [data-testid="btn-nuevo"]').first()
    - locator resolved to <button pc8="" pc24="" pc25="" data-p="" pripple="" type="button" autofocus="true" data-pc-name="button" data-pc-section="root" class="p-ripple p-button p-component">…</button>
  - attempting click action
    - waiting for element to be visible, enabled and stable
    - element is visible, enabled and stable
    - scrolling into view if needed
    - done scrolling
    - <p-tab pc345="" pc346="" role="tab" tabindex="-1" value="domicilio" data-pc-name="tab" aria-selected="false" aria-disabled="false" data-p-active="false" class="p-ripple p-tab" data-pc-section="root" data-p-disabled="false" id="pn_id_73_tab_domicilio" _ngcontent-ng-c2683843423="" aria-controls="pn_id_73_tabpanel_domicilio">…</p-tab> from <div pc14="" pc578="" pc579="" pfocustrap="" data-p-open="true" role="complementary" data-pc-name="drawer" data-pc-section="root" data-p="right open modal" class="p-component p-drawer p-drawer-open p-drawer-right p-drawer-enter-right p-enter-to">…</div> subtree intercepts pointer events
  - retrying click action
    - waiting for element to be visible, enabled and stable
    - element is visible, enabled and stable
    - scrolling into view if needed
    - done scrolling
    - <p-tab pc347="" pc348="" role="tab" tabindex="-1" value="academico" data-pc-name="tab" aria-selected="false" aria-disabled="false" data-p-active="false" class="p-ripple p-tab" data-pc-section="root" data-p-disabled="false" id="pn_id_73_tab_academico" _ngcontent-ng-c2683843423="" aria-controls="pn_id_73_tabpanel_academico">…</p-tab> from <div pc14="" pc578="" pc579="" pfocustrap="" data-p-open="true" role="complementary" data-pc-name="drawer" data-pc-section="root" data-p="right open modal" class="p-component p-drawer p-drawer-open p-drawer-right p-drawer-enter-right p-enter-to">…</div> subtree intercepts pointer events
  - retrying click action
    - waiting 20ms
    - waiting for element to be visible, enabled and stable
    - element is visible, enabled and stable
    - scrolling into view if needed
    - done scrolling
    - <p-tab pc347="" pc348="" role="tab" tabindex="-1" value="academico" data-pc-name="tab" aria-selected="false" aria-disabled="false" data-p-active="false" class="p-ripple p-tab" data-pc-section="root" data-p-disabled="false" id="pn_id_73_tab_academico" _ngcontent-ng-c2683843423="" aria-controls="pn_id_73_tabpanel_academico">…</p-tab> from <div pc14="" pc578="" pc579="" pfocustrap="" data-p-open="true" role="complementary" data-pc-name="drawer" data-pc-section="root" data-p="right open modal" class="p-component p-drawer p-drawer-open p-drawer-right p-drawer-enter-right p-enter-to">…</div> subtree intercepts pointer events
  - retrying click action
    - waiting 100ms
    - waiting for element to be visible, enabled and stable
    - element is visible, enabled and stable
    - scrolling into view if needed
    - done scrolling
    - <div class="p-drawer-header" data-pc-section="header">…</div> from <div pc14="" pc578="" pc579="" pfocustrap="" data-p-open="true" role="complementary" data-pc-name="drawer" data-pc-section="root" data-p="right open modal" class="p-component p-drawer p-drawer-open p-drawer-right p-drawer-enter-right p-enter-to">…</div> subtree intercepts pointer events
  - retrying click action
    - waiting 100ms
    13 × waiting for element to be visible, enabled and stable
       - element is visible, enabled and stable
       - scrolling into view if needed
       - done scrolling
       - <div class="p-drawer-header" data-pc-section="header">…</div> from <div pc14="" pc578="" pc579="" pfocustrap="" data-p-open="true" role="complementary" data-pc-name="drawer" data-pc-section="root" data-p="right open modal" class="p-component p-drawer p-drawer-open p-drawer-right">…</div> subtree intercepts pointer events
     - retrying click action
       - waiting 500ms
       - waiting for element to be visible, enabled and stable
       - element is visible, enabled and stable
       - scrolling into view if needed
       - done scrolling
       - <p-tab pc349="" pc350="" role="tab" value="salud" tabindex="-1" data-pc-name="tab" aria-selected="false" aria-disabled="false" data-p-active="false" class="p-ripple p-tab" data-pc-section="root" id="pn_id_73_tab_salud" data-p-disabled="false" _ngcontent-ng-c2683843423="" aria-controls="pn_id_73_tabpanel_salud">…</p-tab> from <div pc14="" pc578="" pc579="" pfocustrap="" data-p-open="true" role="complementary" data-pc-name="drawer" data-pc-section="root" data-p="right open modal" class="p-component p-drawer p-drawer-open p-drawer-right">…</div> subtree intercepts pointer events
     - retrying click action
       - waiting 500ms
       - waiting for element to be visible, enabled and stable
       - element is visible, enabled and stable
       - scrolling into view if needed
       - done scrolling
       - <p-tab pc349="" pc350="" role="tab" value="salud" tabindex="-1" data-pc-name="tab" aria-selected="false" aria-disabled="false" data-p-active="false" class="p-ripple p-tab" data-pc-section="root" id="pn_id_73_tab_salud" data-p-disabled="false" _ngcontent-ng-c2683843423="" aria-controls="pn_id_73_tabpanel_salud">…</p-tab> from <div pc14="" pc578="" pc579="" pfocustrap="" data-p-open="true" role="complementary" data-pc-name="drawer" data-pc-section="root" data-p="right open modal" class="p-component p-drawer p-drawer-open p-drawer-right">…</div> subtree intercepts pointer events
     - retrying click action
       - waiting 500ms
       - waiting for element to be visible, enabled and stable
       - element is visible, enabled and stable
       - scrolling into view if needed
       - done scrolling
       - <div class="p-drawer-header" data-pc-section="header">…</div> from <div pc14="" pc578="" pc579="" pfocustrap="" data-p-open="true" role="complementary" data-pc-name="drawer" data-pc-section="root" data-p="right open modal" class="p-component p-drawer p-drawer-open p-drawer-right">…</div> subtree intercepts pointer events
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
              - paragraph [ref=e265]: 1,723 alumno(s) registrado(s)
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
              - button " Nuevo alumno" [ref=e290] [cursor=pointer]:
                - generic [ref=e291]: 
                - generic [ref=e292]: Nuevo alumno
          - generic [ref=e297]:
            - generic [ref=e298]: 
            - textbox "Buscar alumno..." [ref=e299]
          - generic [ref=e301]:
            - generic [ref=e302]:
              - generic [ref=e303]:
                - button "Mostrar u ocultar columnas de la tabla" [ref=e305] [cursor=pointer]:
                  - generic [ref=e306]: 
                - button "Descargar datos como archivo CSV" [ref=e308] [cursor=pointer]:
                  - generic [ref=e309]: 
              - generic [ref=e311]: 1723 registro(s)
            - generic [ref=e312]:
              - table [ref=e314]:
                - rowgroup [ref=e315]:
                  - row "Matrícula Nombre Completo CURP NSS Plantel Nivel Grado Grupo Ingreso Acciones" [ref=e316]:
                    - columnheader "Matrícula" [ref=e317] [cursor=pointer]:
                      - generic [ref=e318]:
                        - generic [ref=e319]: Matrícula
                        - img [ref=e321]
                      - generic [ref=e328]:
                        - combobox "Filtrar..." [ref=e329]
                        - button [ref=e330]:
                          - img [ref=e331]
                    - columnheader "Nombre Completo" [ref=e333] [cursor=pointer]:
                      - generic [ref=e334]:
                        - generic [ref=e335]: Nombre Completo
                        - img [ref=e337]
                      - generic [ref=e344]:
                        - combobox "Filtrar..." [ref=e345]
                        - button [ref=e346]:
                          - img [ref=e347]
                    - columnheader "CURP" [ref=e349] [cursor=pointer]:
                      - generic [ref=e350]:
                        - generic [ref=e351]: CURP
                        - img [ref=e353]
                      - generic [ref=e360]:
                        - combobox "Filtrar..." [ref=e361]
                        - button [ref=e362]:
                          - img [ref=e363]
                    - columnheader "NSS" [ref=e365] [cursor=pointer]:
                      - generic [ref=e367]: NSS
                    - columnheader "Plantel" [ref=e368] [cursor=pointer]:
                      - generic [ref=e369]:
                        - generic [ref=e370]: Plantel
                        - img [ref=e372]
                      - generic [ref=e379]:
                        - combobox "Filtrar..." [ref=e380]
                        - button [ref=e381]:
                          - img [ref=e382]
                    - columnheader "Nivel" [ref=e384] [cursor=pointer]:
                      - generic [ref=e385]:
                        - generic [ref=e386]: Nivel
                        - img [ref=e388]
                      - generic [ref=e395]:
                        - combobox "Filtrar..." [ref=e396]
                        - button [ref=e397]:
                          - img [ref=e398]
                    - columnheader "Grado" [ref=e400] [cursor=pointer]:
                      - generic [ref=e401]:
                        - generic [ref=e402]: Grado
                        - img [ref=e404]
                      - generic [ref=e411]:
                        - combobox "Filtrar..." [ref=e412]
                        - button [ref=e413]:
                          - img [ref=e414]
                    - columnheader "Grupo" [ref=e416] [cursor=pointer]:
                      - generic [ref=e417]:
                        - generic [ref=e418]: Grupo
                        - img [ref=e420]
                      - generic [ref=e427]:
                        - combobox "Filtrar..." [ref=e428]
                        - button [ref=e429]:
                          - img [ref=e430]
                    - columnheader "Ingreso" [ref=e432] [cursor=pointer]:
                      - generic [ref=e433]:
                        - generic [ref=e434]: Ingreso
                        - img [ref=e436]
                    - columnheader "Acciones" [ref=e442]:
                      - strong [ref=e443]: Acciones
                - rowgroup [ref=e444]:
                  - row "MAT-MEPR-00420 Camilo Acosta Cordero AOCC151128HMCCRM00 Metepec PRIMARIA Sexto grado B 2026-08-24 Editar este registro" [ref=e445] [cursor=pointer]:
                    - cell "MAT-MEPR-00420" [ref=e446]
                    - cell "Camilo Acosta Cordero" [ref=e447]
                    - cell "AOCC151128HMCCRM00" [ref=e448]
                    - cell [ref=e449]
                    - cell "Metepec" [ref=e450]
                    - cell "PRIMARIA" [ref=e451]
                    - cell "Sexto grado" [ref=e452]
                    - cell "B" [ref=e453]
                    - cell "2026-08-24" [ref=e454]
                    - cell "Editar este registro" [ref=e455]:
                      - button "Editar este registro" [ref=e457]:
                        - generic [ref=e458]: 
                  - row "MAT-MEPR-00270 Héctor Acosta Cabrera AOCH190726HMCCBC00 Metepec PRIMARIA Segundo grado B 2026-08-24 Editar este registro" [ref=e459] [cursor=pointer]:
                    - cell "MAT-MEPR-00270" [ref=e460]
                    - cell "Héctor Acosta Cabrera" [ref=e461]
                    - cell "AOCH190726HMCCBC00" [ref=e462]
                    - cell [ref=e463]
                    - cell "Metepec" [ref=e464]
                    - cell "PRIMARIA" [ref=e465]
                    - cell "Segundo grado" [ref=e466]
                    - cell "B" [ref=e467]
                    - cell "2026-08-24" [ref=e468]
                    - cell "Editar este registro" [ref=e469]:
                      - button "Editar este registro" [ref=e471]:
                        - generic [ref=e472]: 
                  - row "MAT-IXPR-01580 Lorena Acosta Velázquez AOVL161223MMCCLR00 Ixtapan de la Sal PRIMARIA Quinto grado A 2026-08-24 Editar este registro" [ref=e473] [cursor=pointer]:
                    - cell "MAT-IXPR-01580" [ref=e474]
                    - cell "Lorena Acosta Velázquez" [ref=e475]
                    - cell "AOVL161223MMCCLR00" [ref=e476]
                    - cell [ref=e477]
                    - cell "Ixtapan de la Sal" [ref=e478]
                    - cell "PRIMARIA" [ref=e479]
                    - cell "Quinto grado" [ref=e480]
                    - cell "A" [ref=e481]
                    - cell "2026-08-24" [ref=e482]
                    - cell "Editar este registro" [ref=e483]:
                      - button "Editar este registro" [ref=e485]:
                        - generic [ref=e486]: 
                  - row "MAT-IXPR-01261 Patricia Acosta Cisneros AOCP190806MMCCST00 Ixtapan de la Sal PRIMARIA Segundo grado B 2026-08-24 Editar este registro" [ref=e487] [cursor=pointer]:
                    - cell "MAT-IXPR-01261" [ref=e488]
                    - cell "Patricia Acosta Cisneros" [ref=e489]
                    - cell "AOCP190806MMCCST00" [ref=e490]
                    - cell [ref=e491]
                    - cell "Ixtapan de la Sal" [ref=e492]
                    - cell "PRIMARIA" [ref=e493]
                    - cell "Segundo grado" [ref=e494]
                    - cell "B" [ref=e495]
                    - cell "2026-08-24" [ref=e496]
                    - cell "Editar este registro" [ref=e497]:
                      - button "Editar este registro" [ref=e499]:
                        - generic [ref=e500]: 
                  - row "MAT-MEPA-00892 Perla Acosta Ortiz AOOP100609MMCCRR00 Metepec PREPARATORIA Segundo semestre B 2026-08-24 Editar este registro" [ref=e501] [cursor=pointer]:
                    - cell "MAT-MEPA-00892" [ref=e502]
                    - cell "Perla Acosta Ortiz" [ref=e503]
                    - cell "AOOP100609MMCCRR00" [ref=e504]
                    - cell [ref=e505]
                    - cell "Metepec" [ref=e506]
                    - cell "PREPARATORIA" [ref=e507]
                    - cell "Segundo semestre" [ref=e508]
                    - cell "B" [ref=e509]
                    - cell "2026-08-24" [ref=e510]
                    - cell "Editar este registro" [ref=e511]:
                      - button "Editar este registro" [ref=e513]:
                        - generic [ref=e514]: 
                  - row "MAT-MEPA-01050 Adriana Aguilar Mata AUMA080111MMCGTD00 Metepec PREPARATORIA Cuarto semestre B 2026-08-24 Editar este registro" [ref=e515] [cursor=pointer]:
                    - cell "MAT-MEPA-01050" [ref=e516]
                    - cell "Adriana Aguilar Mata" [ref=e517]
                    - cell "AUMA080111MMCGTD00" [ref=e518]
                    - cell [ref=e519]
                    - cell "Metepec" [ref=e520]
                    - cell "PREPARATORIA" [ref=e521]
                    - cell "Cuarto semestre" [ref=e522]
                    - cell "B" [ref=e523]
                    - cell "2026-08-24" [ref=e524]
                    - cell "Editar este registro" [ref=e525]:
                      - button "Editar este registro" [ref=e527]:
                        - generic [ref=e528]: 
                  - row "MAT-IXPR-01218 Carlos Aguilar Arriaga AUAC200210HMCGRR00 Ixtapan de la Sal PRIMARIA Primer grado B 2026-08-24 Editar este registro" [ref=e529] [cursor=pointer]:
                    - cell "MAT-IXPR-01218" [ref=e530]
                    - cell "Carlos Aguilar Arriaga" [ref=e531]
                    - cell "AUAC200210HMCGRR00" [ref=e532]
                    - cell [ref=e533]
                    - cell "Ixtapan de la Sal" [ref=e534]
                    - cell "PRIMARIA" [ref=e535]
                    - cell "Primer grado" [ref=e536]
                    - cell "B" [ref=e537]
                    - cell "2026-08-24" [ref=e538]
                    - cell "Editar este registro" [ref=e539]:
                      - button "Editar este registro" [ref=e541]:
                        - generic [ref=e542]: 
                  - row "MAT-MEPR-00191 Citlali Aguilar Zermeño AUZC201113MMCGRT00 Metepec PRIMARIA Primer grado A 2026-08-24 Editar este registro" [ref=e543] [cursor=pointer]:
                    - cell "MAT-MEPR-00191" [ref=e544]
                    - cell "Citlali Aguilar Zermeño" [ref=e545]
                    - cell "AUZC201113MMCGRT00" [ref=e546]
                    - cell [ref=e547]
                    - cell "Metepec" [ref=e548]
                    - cell "PRIMARIA" [ref=e549]
                    - cell "Primer grado" [ref=e550]
                    - cell "A" [ref=e551]
                    - cell "2026-08-24" [ref=e552]
                    - cell "Editar este registro" [ref=e553]:
                      - button "Editar este registro" [ref=e555]:
                        - generic [ref=e556]: 
                  - row "MAT-TEPR-01126 Constanza Aguilar Farías AUFC151002MMCGRN00 Tenancingo PRIMARIA Sexto grado A 2026-08-24 Editar este registro" [ref=e557] [cursor=pointer]:
                    - cell "MAT-TEPR-01126" [ref=e558]
                    - cell "Constanza Aguilar Farías" [ref=e559]
                    - cell "AUFC151002MMCGRN00" [ref=e560]
                    - cell [ref=e561]
                    - cell "Tenancingo" [ref=e562]
                    - cell "PRIMARIA" [ref=e563]
                    - cell "Sexto grado" [ref=e564]
                    - cell "A" [ref=e565]
                    - cell "2026-08-24" [ref=e566]
                    - cell "Editar este registro" [ref=e567]:
                      - button "Editar este registro" [ref=e569]:
                        - generic [ref=e570]: 
                  - row "MAT-MEPA-00966 Elena Aguilar Yáñez AUYE091214MMCGNL00 Metepec PREPARATORIA Tercer semestre A 2026-08-24 Editar este registro" [ref=e571] [cursor=pointer]:
                    - cell "MAT-MEPA-00966" [ref=e572]
                    - cell "Elena Aguilar Yáñez" [ref=e573]
                    - cell "AUYE091214MMCGNL00" [ref=e574]
                    - cell [ref=e575]
                    - cell "Metepec" [ref=e576]
                    - cell "PREPARATORIA" [ref=e577]
                    - cell "Tercer semestre" [ref=e578]
                    - cell "A" [ref=e579]
                    - cell "2026-08-24" [ref=e580]
                    - cell "Editar este registro" [ref=e581]:
                      - button "Editar este registro" [ref=e583]:
                        - generic [ref=e584]: 
                  - row "MAT-TEPA-00943 Elena Aguilar Rosales AURE100922MMCGSL00 Tenancingo PREPARATORIA Segundo semestre B 2026-08-24 Editar este registro" [ref=e585] [cursor=pointer]:
                    - cell "MAT-TEPA-00943" [ref=e586]
                    - cell "Elena Aguilar Rosales" [ref=e587]
                    - cell "AURE100922MMCGSL00" [ref=e588]
                    - cell [ref=e589]
                    - cell "Tenancingo" [ref=e590]
                    - cell "PREPARATORIA" [ref=e591]
                    - cell "Segundo semestre" [ref=e592]
                    - cell "B" [ref=e593]
                    - cell "2026-08-24" [ref=e594]
                    - cell "Editar este registro" [ref=e595]:
                      - button "Editar este registro" [ref=e597]:
                        - generic [ref=e598]: 
                  - row "MAT-TESE-00763 Genaro Aguilar Villaseñor AUVG120921HMCGLN00 Tenancingo SECUNDARIA Tercer grado B 2026-08-24 Editar este registro" [ref=e599] [cursor=pointer]:
                    - cell "MAT-TESE-00763" [ref=e600]
                    - cell "Genaro Aguilar Villaseñor" [ref=e601]
                    - cell "AUVG120921HMCGLN00" [ref=e602]
                    - cell [ref=e603]
                    - cell "Tenancingo" [ref=e604]
                    - cell "SECUNDARIA" [ref=e605]
                    - cell "Tercer grado" [ref=e606]
                    - cell "B" [ref=e607]
                    - cell "2026-08-24" [ref=e608]
                    - cell "Editar este registro" [ref=e609]:
                      - button "Editar este registro" [ref=e611]:
                        - generic [ref=e612]: 
                  - row "MAT-IXPR-01189 Héctor Aguilar Ramírez AURH200524HMCGMC00 Ixtapan de la Sal PRIMARIA Primer grado A 2026-08-24 Editar este registro" [ref=e613] [cursor=pointer]:
                    - cell "MAT-IXPR-01189" [ref=e614]
                    - cell "Héctor Aguilar Ramírez" [ref=e615]
                    - cell "AURH200524HMCGMC00" [ref=e616]
                    - cell [ref=e617]
                    - cell "Ixtapan de la Sal" [ref=e618]
                    - cell "PRIMARIA" [ref=e619]
                    - cell "Primer grado" [ref=e620]
                    - cell "A" [ref=e621]
                    - cell "2026-08-24" [ref=e622]
                    - cell "Editar este registro" [ref=e623]:
                      - button "Editar este registro" [ref=e625]:
                        - generic [ref=e626]: 
                  - row "MAT-TEPR-01083 Ignacio Aguilar Rodríguez AURI161207HMCGDG00 Tenancingo PRIMARIA Quinto grado A 2026-08-24 Editar este registro" [ref=e627] [cursor=pointer]:
                    - cell "MAT-TEPR-01083" [ref=e628]
                    - cell "Ignacio Aguilar Rodríguez" [ref=e629]
                    - cell "AURI161207HMCGDG00" [ref=e630]
                    - cell [ref=e631]
                    - cell "Tenancingo" [ref=e632]
                    - cell "PRIMARIA" [ref=e633]
                    - cell "Quinto grado" [ref=e634]
                    - cell "A" [ref=e635]
                    - cell "2026-08-24" [ref=e636]
                    - cell "Editar este registro" [ref=e637]:
                      - button "Editar este registro" [ref=e639]:
                        - generic [ref=e640]: 
                  - row "MAT-IXPR-01503 Miguel Aguilar Acosta AUAM180418HMCGCG00 Ixtapan de la Sal PRIMARIA Tercer grado B 2026-08-24 Editar este registro" [ref=e641] [cursor=pointer]:
                    - cell "MAT-IXPR-01503" [ref=e642]
                    - cell "Miguel Aguilar Acosta" [ref=e643]
                    - cell "AUAM180418HMCGCG00" [ref=e644]
                    - cell [ref=e645]
                    - cell "Ixtapan de la Sal" [ref=e646]
                    - cell "PRIMARIA" [ref=e647]
                    - cell "Tercer grado" [ref=e648]
                    - cell "B" [ref=e649]
                    - cell "2026-08-24" [ref=e650]
                    - cell "Editar este registro" [ref=e651]:
                      - button "Editar este registro" [ref=e653]:
                        - generic [ref=e654]: 
                  - row "MAT-IXPR-01234 Renato Aguilar Magaña AUMR190816HMCGGN00 Ixtapan de la Sal PRIMARIA Segundo grado A 2026-08-24 Editar este registro" [ref=e655] [cursor=pointer]:
                    - cell "MAT-IXPR-01234" [ref=e656]
                    - cell "Renato Aguilar Magaña" [ref=e657]
                    - cell "AUMR190816HMCGGN00" [ref=e658]
                    - cell [ref=e659]
                    - cell "Ixtapan de la Sal" [ref=e660]
                    - cell "PRIMARIA" [ref=e661]
                    - cell "Segundo grado" [ref=e662]
                    - cell "A" [ref=e663]
                    - cell "2026-08-24" [ref=e664]
                    - cell "Editar este registro" [ref=e665]:
                      - button "Editar este registro" [ref=e667]:
                        - generic [ref=e668]: 
                  - row "MAT-TESE-00710 Santiago Aguilar Macías AUMS130116HMCGCN00 Tenancingo SECUNDARIA Segundo grado B 2026-08-24 Editar este registro" [ref=e669] [cursor=pointer]:
                    - cell "MAT-TESE-00710" [ref=e670]
                    - cell "Santiago Aguilar Macías" [ref=e671]
                    - cell "AUMS130116HMCGCN00" [ref=e672]
                    - cell [ref=e673]
                    - cell "Tenancingo" [ref=e674]
                    - cell "SECUNDARIA" [ref=e675]
                    - cell "Segundo grado" [ref=e676]
                    - cell "B" [ref=e677]
                    - cell "2026-08-24" [ref=e678]
                    - cell "Editar este registro" [ref=e679]:
                      - button "Editar este registro" [ref=e681]:
                        - generic [ref=e682]: 
                  - row "MAT-TEPR-01099 Cristóbal Aguirre Delgado AUDC160913HMCGLR00 Tenancingo PRIMARIA Quinto grado B 2026-08-24 Editar este registro" [ref=e683] [cursor=pointer]:
                    - cell "MAT-TEPR-01099" [ref=e684]
                    - cell "Cristóbal Aguirre Delgado" [ref=e685]
                    - cell "AUDC160913HMCGLR00" [ref=e686]
                    - cell [ref=e687]
                    - cell "Tenancingo" [ref=e688]
                    - cell "PRIMARIA" [ref=e689]
                    - cell "Quinto grado" [ref=e690]
                    - cell "B" [ref=e691]
                    - cell "2026-08-24" [ref=e692]
                    - cell "Editar este registro" [ref=e693]:
                      - button "Editar este registro" [ref=e695]:
                        - generic [ref=e696]: 
                  - row "MAT-MEPA-01420 Gustavo Aguirre Arriaga AUAG110427HMCGRS00 Metepec PREPARATORIA Primer semestre A 2026-08-24 Editar este registro" [ref=e697] [cursor=pointer]:
                    - cell "MAT-MEPA-01420" [ref=e698]
                    - cell "Gustavo Aguirre Arriaga" [ref=e699]
                    - cell "AUAG110427HMCGRS00" [ref=e700]
                    - cell [ref=e701]
                    - cell "Metepec" [ref=e702]
                    - cell "PREPARATORIA" [ref=e703]
                    - cell "Primer semestre" [ref=e704]
                    - cell "A" [ref=e705]
                    - cell "2026-08-24" [ref=e706]
                    - cell "Editar este registro" [ref=e707]:
                      - button "Editar este registro" [ref=e709]:
                        - generic [ref=e710]: 
                  - row "MAT-IXPR-01652 Omar Aguirre Guzmán AUGO151215HMCGZM00 Ixtapan de la Sal PRIMARIA Sexto grado B 2026-08-24 Editar este registro" [ref=e711] [cursor=pointer]:
                    - cell "MAT-IXPR-01652" [ref=e712]
                    - cell "Omar Aguirre Guzmán" [ref=e713]
                    - cell "AUGO151215HMCGZM00" [ref=e714]
                    - cell [ref=e715]
                    - cell "Ixtapan de la Sal" [ref=e716]
                    - cell "PRIMARIA" [ref=e717]
                    - cell "Sexto grado" [ref=e718]
                    - cell "B" [ref=e719]
                    - cell "2026-08-24" [ref=e720]
                    - cell "Editar este registro" [ref=e721]:
                      - button "Editar este registro" [ref=e723]:
                        - generic [ref=e724]: 
              - generic [ref=e725]:
                - button "Primera página":
                  - img
                - button [disabled]:
                  - img
                - generic [ref=e726]:
                  - button "Página 1" [ref=e727] [cursor=pointer]: "1"
                  - button "Página 2" [ref=e728] [cursor=pointer]: "2"
                  - button "Página 3" [ref=e729] [cursor=pointer]: "3"
                  - button "Página 4" [ref=e730] [cursor=pointer]: "4"
                  - button "Página 5" [ref=e731] [cursor=pointer]: "5"
                - button "Página siguiente" [ref=e732] [cursor=pointer]:
                  - img [ref=e733]
                - button "Última página" [ref=e735] [cursor=pointer]:
                  - img [ref=e736]
                - generic [ref=e738] [cursor=pointer]:
                  - combobox "Filas por página" [ref=e739]: "20"
                  - button "dropdown trigger" [ref=e740]:
                    - img [ref=e741]
          - generic "Nuevo Alumno"
  - complementary [ref=e743]:
    - generic [ref=e745]:
      - generic [ref=e746]: Perfil del Alumno
      - button [ref=e748] [cursor=pointer]:
        - img [ref=e749]
    - generic [ref=e751]:
      - generic [ref=e752]:
        - generic [ref=e753]: MAT-000007
        - generic [ref=e755]: ACTIVO
      - generic [ref=e756]:
        - generic [ref=e757]:
          - tablist [ref=e759]:
            - tab " Personal" [selected] [ref=e760] [cursor=pointer]:
              - generic [ref=e761]: 
              - text: Personal
            - tab " Domicilio" [ref=e762] [cursor=pointer]:
              - generic [ref=e763]: 
              - text: Domicilio
            - tab " Académico" [ref=e764] [cursor=pointer]:
              - generic [ref=e765]: 
              - text: Académico
            - tab " Salud" [ref=e766] [cursor=pointer]:
              - generic [ref=e767]: 
              - text: Salud
            - tab " Familia" [ref=e768] [cursor=pointer]:
              - generic [ref=e769]: 
              - text: Familia
            - tab " Bajas" [ref=e770] [cursor=pointer]:
              - generic [ref=e771]: 
              - text: Bajas
          - button "Siguiente" [ref=e772] [cursor=pointer]:
            - img [ref=e773]
        - tabpanel " Personal" [ref=e775]:
          - generic [ref=e776]:
            - heading "Identificación" [level=4] [ref=e777]
            - generic [ref=e778]:
              - generic [ref=e779]: Nombre(s)
              - textbox [ref=e780]: Magdalena
            - generic [ref=e781]:
              - generic [ref=e782]: Apellido paterno
              - textbox [ref=e783]: Manzanares de Canales
            - generic [ref=e784]:
              - generic [ref=e785]: Apellido materno
              - textbox [ref=e786]: Tejeda Chávez
            - generic [ref=e787]:
              - generic [ref=e788]: CURP
              - textbox [ref=e789]: WAQJMY100110MJLLAQ
            - generic [ref=e790]:
              - generic [ref=e791]: Género (legal)
              - generic [ref=e792] [cursor=pointer]:
                - combobox "Seleccionar…" [ref=e793]
                - button "dropdown trigger" [ref=e794]:
                  - img [ref=e795]
            - generic [ref=e797]:
              - generic [ref=e798]:
                - text: Nombre social
                - generic [ref=e799]: 
              - textbox "Opcional — solo si difiere del nombre legal" [ref=e800]
            - generic [ref=e801]:
              - generic [ref=e802]: Género autopercibido
              - generic [ref=e803] [cursor=pointer]:
                - combobox "Seleccionar…" [ref=e804]
                - button "dropdown trigger" [ref=e805]:
                  - img [ref=e806]
            - generic [ref=e808]:
              - generic [ref=e809]: Pronombres
              - 'textbox "Ej: él/sus, ella/sus, elle/sus" [ref=e810]'
            - generic [ref=e811]:
              - generic [ref=e812]: Fecha nacimiento
              - combobox [ref=e814]
            - generic [ref=e815]:
              - generic [ref=e816]: Estado civil
              - generic [ref=e817] [cursor=pointer]:
                - combobox [ref=e818]
                - button "dropdown trigger" [ref=e819]:
                  - img [ref=e820]
            - generic [ref=e822]:
              - generic [ref=e823]: Nacionalidad
              - generic [ref=e824] [cursor=pointer]:
                - combobox "Seleccionar..." [ref=e825]
                - img [ref=e826]
                - button "dropdown trigger" [ref=e828]:
                  - img [ref=e829]
          - generic [ref=e831]:
            - heading "Lugar de nacimiento" [level=4] [ref=e832]
            - generic [ref=e833]:
              - generic [ref=e834]: País
              - generic [ref=e835] [cursor=pointer]:
                - combobox "México" [ref=e836]
                - img [ref=e837]
                - button "dropdown trigger" [ref=e839]:
                  - img [ref=e840]
            - generic [ref=e842]:
              - generic [ref=e843]: Estado
              - generic [ref=e844] [cursor=pointer]:
                - combobox "Seleccionar estado…" [ref=e845]
                - img [ref=e846]
                - button "dropdown trigger" [ref=e848]:
                  - img [ref=e849]
            - generic [ref=e851]:
              - generic [ref=e852]: Municipio
              - generic:
                - combobox "Seleccionar municipio…" [disabled]
                - button "dropdown trigger":
                  - img
        - text:       
      - generic [ref=e853]:
        - button "Cancelar" [ref=e855] [cursor=pointer]:
          - generic [ref=e856]: Cancelar
        - button " Guardar cambios" [ref=e858] [cursor=pointer]:
          - generic [ref=e859]: 
          - generic [ref=e860]: Guardar cambios
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
> 46  |     await this.newBtn.first().click();
      |                               ^ Error: locator.click: Test timeout of 30000ms exceeded.
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
  67  |     await this.saveBtn.click();
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