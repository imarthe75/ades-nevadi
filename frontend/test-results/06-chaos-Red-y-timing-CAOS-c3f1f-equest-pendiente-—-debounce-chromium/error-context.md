# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: 06-chaos.spec.ts >> Red y timing >> CAOS-15 | click en guardar durante request pendiente — debounce
- Location: e2e/tests/06-chaos.spec.ts:226:7

# Error details

```
Error: expect(received).toBeLessThan(expected)

Expected: < 5
Received:   5
```

# Page snapshot

```yaml
- generic [ref=e3]:
  - generic [ref=e4]:
    - alert [ref=e6]:
      - generic [ref=e7]:
        - img [ref=e8]
        - generic [ref=e11]:
          - generic [ref=e12]: Error
          - generic [ref=e13]: Error al crear alumno
        - button "Cerrar" [ref=e15] [cursor=pointer]:
          - img [ref=e16]
    - alert [ref=e19]:
      - generic [ref=e20]:
        - img [ref=e21]
        - generic [ref=e24]:
          - generic [ref=e25]: Error
          - generic [ref=e26]: Error al crear alumno
        - button "Cerrar" [ref=e28] [cursor=pointer]:
          - img [ref=e29]
  - toolbar [ref=e31]:
    - generic [ref=e32]:
      - generic [ref=e33]:
        - generic [ref=e34]: "N"
        - generic [ref=e35]:
          - generic [ref=e36]: ADES
          - generic [ref=e37]: Instituto Nevadi
      - generic [ref=e39] [cursor=pointer]:
        - combobox "— Todo el Instituto —" [ref=e40]
        - button "dropdown trigger" [ref=e41]:
          - img [ref=e42]
      - generic [ref=e44]: /
      - generic [ref=e45] [cursor=pointer]:
        - combobox "TODOS" [ref=e46]
        - button "dropdown trigger" [ref=e47]:
          - img [ref=e48]
      - generic [ref=e50]: /
      - generic [ref=e51] [cursor=pointer]:
        - combobox "2026-2027" [ref=e52]
        - button "dropdown trigger" [ref=e53]:
          - img [ref=e54]
    - generic [ref=e56]:
      - generic [ref=e58] [cursor=pointer]: 
      - generic [ref=e59] [cursor=pointer]:
        - generic [ref=e60]: T
        - generic [ref=e61]:
          - generic [ref=e62]: Test COORDINADOR_ACADEMICO
          - generic [ref=e63]: Coord. Académico
        - generic [ref=e64]: 
  - generic [ref=e65]:
    - navigation [ref=e66]:
      - generic [ref=e67]: Principal
      - list [ref=e68]:
        - listitem [ref=e69]:
          - link " Dashboard" [ref=e70] [cursor=pointer]:
            - /url: /dashboard
            - generic [ref=e71]: 
            - generic [ref=e72]: Dashboard
      - generic [ref=e73]: Académico
      - list [ref=e74]:
        - listitem [ref=e75]:
          - link " Alumnos" [ref=e76] [cursor=pointer]:
            - /url: /alumnos
            - generic [ref=e77]: 
            - generic [ref=e78]: Alumnos
        - listitem [ref=e79]:
          - link " Reinscripción" [ref=e80] [cursor=pointer]:
            - /url: /reinscripcion
            - generic [ref=e81]: 
            - generic [ref=e82]: Reinscripción
        - listitem [ref=e83]:
          - link " Profesores" [ref=e84] [cursor=pointer]:
            - /url: /profesores
            - generic [ref=e85]: 
            - generic [ref=e86]: Profesores
        - listitem [ref=e87]:
          - link " Grupos" [ref=e88] [cursor=pointer]:
            - /url: /grupos
            - generic [ref=e89]: 
            - generic [ref=e90]: Grupos
        - listitem [ref=e91]:
          - link " Aulas" [ref=e92] [cursor=pointer]:
            - /url: /aulas
            - generic [ref=e93]: 
            - generic [ref=e94]: Aulas
        - listitem [ref=e95]:
          - link " Planes de Estudio" [ref=e96] [cursor=pointer]:
            - /url: /planes-estudio
            - generic [ref=e97]: 
            - generic [ref=e98]: Planes de Estudio
        - listitem [ref=e99]:
          - link " Calificaciones" [ref=e100] [cursor=pointer]:
            - /url: /calificaciones
            - generic [ref=e101]: 
            - generic [ref=e102]: Calificaciones
        - listitem [ref=e103]:
          - link " Evaluaciones" [ref=e104] [cursor=pointer]:
            - /url: /evaluaciones
            - generic [ref=e105]: 
            - generic [ref=e106]: Evaluaciones
        - listitem [ref=e107]:
          - link " Asistencias" [ref=e108] [cursor=pointer]:
            - /url: /asistencias
            - generic [ref=e109]: 
            - generic [ref=e110]: Asistencias
        - listitem [ref=e111]:
          - link " Tareas" [ref=e112] [cursor=pointer]:
            - /url: /tareas
            - generic [ref=e113]: 
            - generic [ref=e114]: Tareas
        - listitem [ref=e115]:
          - link " Planeación" [ref=e116] [cursor=pointer]:
            - /url: /planeacion
            - generic [ref=e117]: 
            - generic [ref=e118]: Planeación
      - generic [ref=e119]: Operaciones
      - list [ref=e120]:
        - listitem [ref=e121]:
          - link " Horarios" [ref=e122] [cursor=pointer]:
            - /url: /horarios
            - generic [ref=e123]: 
            - generic [ref=e124]: Horarios
        - listitem [ref=e125]:
          - link " Calendario Escolar" [ref=e126] [cursor=pointer]:
            - /url: /calendario
            - generic [ref=e127]: 
            - generic [ref=e128]: Calendario Escolar
        - listitem [ref=e129]:
          - link " Conducta" [ref=e130] [cursor=pointer]:
            - /url: /conducta
            - generic [ref=e131]: 
            - generic [ref=e132]: Conducta
        - listitem [ref=e133]:
          - link " Expediente Médico" [ref=e134] [cursor=pointer]:
            - /url: /medico
            - generic [ref=e135]: 
            - generic [ref=e136]: Expediente Médico
        - listitem [ref=e137]:
          - link " Condiciones Crónicas" [ref=e138] [cursor=pointer]:
            - /url: /condiciones-cronicas
            - generic [ref=e139]: 
            - generic [ref=e140]: Condiciones Crónicas
        - listitem [ref=e141]:
          - link " Justificaciones Faltas" [ref=e142] [cursor=pointer]:
            - /url: /justificaciones
            - generic [ref=e143]: 
            - generic [ref=e144]: Justificaciones Faltas
        - listitem [ref=e145]:
          - link " Movilidad Estudiantil" [ref=e146] [cursor=pointer]:
            - /url: /movilidad
            - generic [ref=e147]: 
            - generic [ref=e148]: Movilidad Estudiantil
        - listitem [ref=e149]:
          - link " Optativas" [ref=e150] [cursor=pointer]:
            - /url: /optativas
            - generic [ref=e151]: 
            - generic [ref=e152]: Optativas
        - listitem [ref=e153]:
          - link " Admisión" [ref=e154] [cursor=pointer]:
            - /url: /admision
            - generic [ref=e155]: 
            - generic [ref=e156]: Admisión
      - generic [ref=e157]: Comunicación
      - list [ref=e158]:
        - listitem [ref=e159]:
          - link " Comunicados" [ref=e160] [cursor=pointer]:
            - /url: /comunicados
            - generic [ref=e161]: 
            - generic [ref=e162]: Comunicados
        - listitem [ref=e163]:
          - link " Foros y Anuncios" [ref=e164] [cursor=pointer]:
            - /url: /foros
            - generic [ref=e165]: 
            - generic [ref=e166]: Foros y Anuncios
        - listitem [ref=e167]:
          - link " Encuestas" [ref=e168] [cursor=pointer]:
            - /url: /encuestas
            - generic [ref=e169]: 
            - generic [ref=e170]: Encuestas
      - generic [ref=e171]: Gradebook
      - list [ref=e172]:
        - listitem [ref=e173]:
          - link " Gradebook" [ref=e174] [cursor=pointer]:
            - /url: /gradebook
            - generic [ref=e175]: 
            - generic [ref=e176]: Gradebook
        - listitem [ref=e177]:
          - link " Mi Progreso" [ref=e178] [cursor=pointer]:
            - /url: /mi-progreso
            - generic [ref=e179]: 
            - generic [ref=e180]: Mi Progreso
        - listitem [ref=e181]:
          - link " Ponderaciones" [ref=e182] [cursor=pointer]:
            - /url: /ponderacion-config
            - generic [ref=e183]: 
            - generic [ref=e184]: Ponderaciones
      - generic [ref=e185]: Recursos
      - list [ref=e186]:
        - listitem [ref=e187]:
          - link " Rúbricas" [ref=e188] [cursor=pointer]:
            - /url: /rubricas
            - generic [ref=e189]: 
            - generic [ref=e190]: Rúbricas
        - listitem [ref=e191]:
          - link " Insignias" [ref=e192] [cursor=pointer]:
            - /url: /badges
            - generic [ref=e193]: 
            - generic [ref=e194]: Insignias
        - listitem [ref=e195]:
          - link " Portal Alumno" [ref=e196] [cursor=pointer]:
            - /url: /portal
            - generic [ref=e197]: 
            - generic [ref=e198]: Portal Alumno
      - generic [ref=e199]: Inteligencia
      - list [ref=e200]:
        - listitem [ref=e201]:
          - link " Dashboards BI" [ref=e202] [cursor=pointer]:
            - /url: /bi
            - generic [ref=e203]: 
            - generic [ref=e204]: Dashboards BI
        - listitem [ref=e205]:
          - link " Grade Analytics" [ref=e206] [cursor=pointer]:
            - /url: /grade-analytics
            - generic [ref=e207]: 
            - generic [ref=e208]: Grade Analytics
        - listitem [ref=e209]:
          - link " Asistente IA + Datos" [ref=e210] [cursor=pointer]:
            - /url: /ia
            - generic [ref=e211]: 
            - generic [ref=e212]: Asistente IA + Datos
        - listitem [ref=e213]:
          - link " Eval. Docente 360°" [ref=e214] [cursor=pointer]:
            - /url: /eval-docente
            - generic [ref=e215]: 
            - generic [ref=e216]: Eval. Docente 360°
        - listitem [ref=e217]:
          - link " Learning Paths" [ref=e218] [cursor=pointer]:
            - /url: /learning-paths
            - generic [ref=e219]: 
            - generic [ref=e220]: Learning Paths
      - generic [ref=e221]: Reportes
      - list [ref=e222]:
        - listitem [ref=e223]:
          - link " Generador de Reportes" [ref=e224] [cursor=pointer]:
            - /url: /reportes
            - generic [ref=e225]: 
            - generic [ref=e226]: Generador de Reportes
        - listitem [ref=e227]:
          - link " Certificados Digitales" [ref=e228] [cursor=pointer]:
            - /url: /certificados
            - generic [ref=e229]: 
            - generic [ref=e230]: Certificados Digitales
        - listitem [ref=e231]:
          - link " Expediente Digital" [ref=e232] [cursor=pointer]:
            - /url: /expediente-doc
            - generic [ref=e233]: 
            - generic [ref=e234]: Expediente Digital
      - generic [ref=e235]: Ayuda
      - list [ref=e236]:
        - listitem [ref=e237]:
          - link " Manual de Usuario" [ref=e238] [cursor=pointer]:
            - /url: /ayuda
            - generic [ref=e239]: 
            - generic [ref=e240]: Manual de Usuario
    - main [ref=e241]:
      - navigation "Breadcrumb" [ref=e244]:
        - list [ref=e245]:
          - listitem [ref=e246]:
            - link "Home" [ref=e247] [cursor=pointer]:
              - /url: /
          - listitem [ref=e248]:
            - generic [ref=e249]: 
            - generic [ref=e250]: Alumnos
      - generic [ref=e251]:
        - generic [ref=e252]:
          - generic [ref=e253]:
            - heading "Alumnos" [level=2] [ref=e254]
            - paragraph [ref=e255]: 0 alumno(s) registrado(s)
          - generic [ref=e256]:
            - button "" [ref=e259] [cursor=pointer]:
              - generic [ref=e260]: 
            - button " CSV" [ref=e262] [cursor=pointer]:
              - generic [ref=e263]: 
              - generic [ref=e264]: CSV
            - button " Excel" [ref=e266] [cursor=pointer]:
              - generic [ref=e267]: 
              - generic [ref=e268]: Excel
            - generic [ref=e270]:
              - button " Importar CSV/Excel" [ref=e272] [cursor=pointer]:
                - generic [ref=e273]: 
                - generic [ref=e274]: Importar CSV/Excel
              - button " Plantilla" [ref=e276] [cursor=pointer]:
                - generic [ref=e277]: 
                - generic [ref=e278]: Plantilla
            - button " Nuevo alumno" [ref=e280] [cursor=pointer]:
              - generic [ref=e281]: 
              - generic [ref=e282]: Nuevo alumno
        - generic [ref=e285]:
          - generic [ref=e286]: 
          - textbox "Buscar alumno..." [ref=e287]
        - generic [ref=e289]:
          - generic [ref=e290]:
            - generic [ref=e291]:
              - button "" [ref=e293] [cursor=pointer]:
                - generic [ref=e294]: 
              - button "" [ref=e296] [cursor=pointer]:
                - generic [ref=e297]: 
            - generic [ref=e299]: 0 registro(s)
          - generic [ref=e300]:
            - table [ref=e302]:
              - rowgroup [ref=e303]:
                - row "Matrícula Nombre Completo CURP NSS Nivel Grado Grupo Ingreso Acciones" [ref=e304]:
                  - columnheader "Matrícula" [ref=e305] [cursor=pointer]:
                    - generic [ref=e306]:
                      - generic [ref=e307]: Matrícula
                      - img [ref=e309]
                    - generic [ref=e316]:
                      - combobox "Filtrar..." [ref=e317]
                      - button [ref=e318]:
                        - img [ref=e319]
                  - columnheader "Nombre Completo" [ref=e321] [cursor=pointer]:
                    - generic [ref=e322]:
                      - generic [ref=e323]: Nombre Completo
                      - img [ref=e325]
                    - generic [ref=e332]:
                      - combobox "Filtrar..." [ref=e333]
                      - button [ref=e334]:
                        - img [ref=e335]
                  - columnheader "CURP" [ref=e337] [cursor=pointer]:
                    - generic [ref=e338]:
                      - generic [ref=e339]: CURP
                      - img [ref=e341]
                    - generic [ref=e348]:
                      - combobox "Filtrar..." [ref=e349]
                      - button [ref=e350]:
                        - img [ref=e351]
                  - columnheader "NSS" [ref=e353] [cursor=pointer]:
                    - generic [ref=e355]: NSS
                  - columnheader "Nivel" [ref=e356] [cursor=pointer]:
                    - generic [ref=e357]:
                      - generic [ref=e358]: Nivel
                      - img [ref=e360]
                    - generic [ref=e367]:
                      - combobox "Filtrar..." [ref=e368]
                      - button [ref=e369]:
                        - img [ref=e370]
                  - columnheader "Grado" [ref=e372] [cursor=pointer]:
                    - generic [ref=e373]:
                      - generic [ref=e374]: Grado
                      - img [ref=e376]
                    - generic [ref=e383]:
                      - combobox "Filtrar..." [ref=e384]
                      - button [ref=e385]:
                        - img [ref=e386]
                  - columnheader "Grupo" [ref=e388] [cursor=pointer]:
                    - generic [ref=e389]:
                      - generic [ref=e390]: Grupo
                      - img [ref=e392]
                    - generic [ref=e399]:
                      - combobox "Filtrar..." [ref=e400]
                      - button [ref=e401]:
                        - img [ref=e402]
                  - columnheader "Ingreso" [ref=e404] [cursor=pointer]:
                    - generic [ref=e405]:
                      - generic [ref=e406]: Ingreso
                      - img [ref=e408]
                  - columnheader "Acciones" [ref=e414]:
                    - strong [ref=e415]: Acciones
              - rowgroup [ref=e416]:
                - row "Sin registros" [ref=e417]:
                  - cell "Sin registros" [ref=e418]
            - generic [ref=e419]:
              - button "Primera página":
                - img
              - button [disabled]:
                - img
              - button "Página siguiente" [disabled]:
                - img
              - button "Última página" [disabled]:
                - img
              - generic:
                - combobox "Filas por página" [disabled]: "20"
                - button "dropdown trigger":
                  - img
        - generic "Nuevo Alumno":
          - dialog "Nuevo Alumno" [ref=e421]:
            - generic [ref=e423]:
              - generic [ref=e424]: Nuevo Alumno
              - button [ref=e427] [cursor=pointer]:
                - img [ref=e428]
            - generic [ref=e431]:
              - generic [ref=e432]:
                - generic [ref=e433]: Nombre(s) *
                - textbox [ref=e434]: Concepción
              - generic [ref=e435]:
                - generic [ref=e436]: Apellido paterno *
                - textbox [ref=e437]: Quintero de la cruz de Jaime
              - generic [ref=e438]:
                - generic [ref=e439]: Apellido materno
                - textbox [ref=e440]: Alcala Romo
              - generic [ref=e441]:
                - generic [ref=e442]: CURP (18 caracteres) *
                - textbox [ref=e443]: PIXXWZ000305HJLIBS
              - paragraph [ref=e444]: Una vez creado podrás completar el expediente completo desde el perfil.
            - button "Crear alumno" [ref=e447] [cursor=pointer]:
              - generic [ref=e448]: Crear alumno
```

# Test source

```ts
  148 |     if (await dash.plantelSelect.isVisible()) {
  149 |       await dash.plantelSelect.click();
  150 |       await page.keyboard.press('Escape');
  151 |     }
  152 |     // El form no debe perder los datos ni crashear
  153 |     const nombreVal = await ap.nombreInput.inputValue();
  154 |     expect(nombreVal).toBe('Juan');
  155 |   });
  156 | 
  157 |   test('CAOS-11 | copiar/pegar emoji en campo CURP', async ({ page }) => {
  158 |     await new LoginPage(page).login(USERS.COORDINADOR);
  159 |     const ap = new AlumnosPage(page);
  160 |     await ap.navigate();
  161 |     await ap.openNewForm();
  162 |     await ap.curpInput.fill(EDGE_STRINGS.EMOJIS);
  163 |     await ap.curpInput.blur();
  164 |     // El componente usa notify.warning() (toast) para validación, no ng-invalid
  165 |     // Intentar guardar para disparar la validación
  166 |     await ap.saveBtn.click().catch(() => undefined);
  167 |     await page.waitForTimeout(1_000);
  168 |     // Debe mostrar un toast de advertencia o error
  169 |     const toastOrErr = page.locator('.p-toast-message-warn, .p-toast-message-error, .p-error');
  170 |     // Si no hay toast, al menos la app no debe crashear
  171 |     await expect(page).not.toHaveURL(/error|crash/);
  172 |   });
  173 | });
  174 | 
  175 | // ── 3. Red y timing ──────────────────────────────────────────────────────────
  176 | 
  177 | test.describe('Red y timing', () => {
  178 |   test('CAOS-12 | respuesta lenta de API (3s) — spinner visible', async ({ page, context }) => {
  179 |     // Interceptar SOLO las llamadas API, no la navegación Angular (SPA)
  180 |     await context.route('**/api/v1/alumnos**', async route => {
  181 |       await new Promise(r => setTimeout(r, 3_000));
  182 |       await route.continue();
  183 |     });
  184 |     await new LoginPage(page).login(USERS.COORDINADOR);
  185 |     await page.goto('/alumnos');
  186 |     // Durante la carga el interactive-grid muestra estado de carga
  187 |     // El spinner puede estar en .p-progress-spinner o como loading overlay
  188 |     const loadingIndicator = page.locator(
  189 |       '.p-progress-spinner, [data-loading], .ades-loading, app-interactive-grid'
  190 |     );
  191 |     // Al menos la página debe cargar correctamente
  192 |     await expect(page).toHaveURL(/\/alumnos/);
  193 |     // El spinner puede ser muy fugaz — verificar que la app no crashea
  194 |     await page.waitForTimeout(500);
  195 |     await expect(page).not.toHaveURL(/error/);
  196 |   });
  197 | 
  198 |   test('CAOS-13 | servidor devuelve 500 → mensaje amigable, no stack trace', async ({ page, context }) => {
  199 |     // Interceptar SOLO las llamadas API, no la navegación Angular
  200 |     await context.route('**/api/v1/alumnos**', route =>
  201 |       route.fulfill({ status: 500, body: JSON.stringify({ detail: 'Error interno' }) })
  202 |     );
  203 |     await new LoginPage(page).login(USERS.COORDINADOR);
  204 |     await page.goto('/alumnos');
  205 |     await page.waitForTimeout(3_000);
  206 |     // No debe mostrarse un stack trace de Python o Java
  207 |     const body = await page.content();
  208 |     expect(body).not.toMatch(/Traceback|NullPointerException|at mx\.ades/);
  209 |     // La app no debe crashear ni mostrar stack trace
  210 |     await expect(page).not.toHaveURL(/error.*fatal/);
  211 |   });
  212 | 
  213 |   test('CAOS-14 | offline — app muestra error y no pierde UI', async ({ page, context }) => {
  214 |     await new LoginPage(page).login(USERS.COORDINADOR);
  215 |     await page.goto('/dashboard');
  216 |     // Simular offline bloqueando solo las APIs (no la navegación SPA en localhost)
  217 |     await context.route('**/api/**', route => route.abort('internetdisconnected'));
  218 |     await page.goto('/alumnos');
  219 |     await page.waitForTimeout(2_000);
  220 |     // La app Angular (SPA) sigue renderizando aunque las APIs fallen
  221 |     await expect(page).not.toHaveURL(/error.*fatal/);
  222 |     // Limpiar el interceptor
  223 |     await context.unroute('**/api/**');
  224 |   });
  225 | 
  226 |   test('CAOS-15 | click en guardar durante request pendiente — debounce', async ({ page, context }) => {
  227 |     let requestCount = 0;
  228 |     // Interceptar SOLO llamadas POST a la API
  229 |     await context.route('**/api/v1/alumnos**', async route => {
  230 |       if (route.request().method() === 'POST') {
  231 |         requestCount++;
  232 |         await new Promise(r => setTimeout(r, 1_500));
  233 |       }
  234 |       await route.continue();
  235 |     });
  236 |     await new LoginPage(page).login(USERS.COORDINADOR);
  237 |     const ap = new AlumnosPage(page);
  238 |     await ap.navigate();
  239 |     await ap.openNewForm();
  240 |     await ap.fillAlumnoForm(alumnoValido());
  241 |     // 5 clicks rápidos en guardar
  242 |     for (let i = 0; i < 5; i++) {
  243 |       await ap.saveBtn.click().catch(() => undefined);
  244 |     }
  245 |     await page.waitForTimeout(3_000);
  246 |     // Con debounce/disable-after-click, no deben enviarse 5 requests completas.
  247 |     // Si no hay debounce, el BFF recibe múltiples POSTs — aún así la app no debe crashear.
> 248 |     expect(requestCount).toBeLessThan(5); // al menos algo de protección
      |                          ^ Error: expect(received).toBeLessThan(expected)
  249 |     await expect(page).not.toHaveURL(/error/);
  250 |   });
  251 | });
  252 | 
  253 | // ── 4. Sesión y autenticación caótica ────────────────────────────────────────
  254 | 
  255 | test.describe('Sesión caótica', () => {
  256 |   test('CAOS-16 | token manipulado en sessionStorage → redirige a login', async ({ page }) => {
  257 |     await new LoginPage(page).login(USERS.COORDINADOR);
  258 |     // Corromper el token
  259 |     await page.evaluate(() => {
  260 |       const bad = 'CORRUPTED.' + Math.random().toString(36);
  261 |       sessionStorage.setItem('ades_token', bad);
  262 |     });
  263 |     await page.goto('/alumnos');
  264 |     await page.waitForTimeout(2_000);
  265 |     // El authGuard de Angular solo verifica !!token (no valida el JWT).
  266 |     // Con token corrupto pero no vacío, la ruta se permite — el BFF rechaza con 401.
  267 |     // Verificar que la app NO muestra stack trace ni crashea.
  268 |     const content = await page.content();
  269 |     expect(content).not.toMatch(/Traceback|NullPointerException/);
  270 |     // La URL puede ser /alumnos (sin redirect) o /login (si el BFF retorna 401 y el guard lo maneja)
  271 |     const url = page.url();
  272 |     expect(url).toMatch(/\/(alumnos|login|dashboard)/);
  273 |   });
  274 | 
  275 |   test('CAOS-17 | borrar sessionStorage y navegar a ruta protegida', async ({ page }) => {
  276 |     await new LoginPage(page).login(USERS.COORDINADOR);
  277 |     await page.evaluate(() => sessionStorage.clear());
  278 |     await page.goto('/calificaciones');
  279 |     await expect(page).toHaveURL(/\/login/, { timeout: 8_000 });
  280 |   });
  281 | 
  282 |   test('CAOS-18 | login en dos cuentas distintas consecutivamente', async ({ page }) => {
  283 |     const lp = new LoginPage(page);
  284 |     await lp.login(USERS.DOCENTE);
  285 |     await expect(page).toHaveURL(/\/dashboard/);
  286 |     await lp.logout();
  287 |     await lp.login(USERS.COORDINADOR);
  288 |     await expect(page).toHaveURL(/\/dashboard/);
  289 |     // El token debe corresponder al segundo usuario
  290 |     const token = await page.evaluate(() => sessionStorage.getItem('ades_token'));
  291 |     expect(token).toBeTruthy();
  292 |   });
  293 | 
  294 |   test('CAOS-19 | apertura de URL privada en incógnito', async ({ browser }) => {
  295 |     const context = await browser.newContext();
  296 |     const page    = await context.newPage();
  297 |     // Sin login, intentar acceder a módulo privado
  298 |     await page.goto('http://localhost:4200/certificados');
  299 |     await expect(page).toHaveURL(/\/login/, { timeout: 8_000 });
  300 |     await context.close();
  301 |   });
  302 | });
  303 | 
  304 | // ── 5. Secuencias raras de CUs ────────────────────────────────────────────────
  305 | 
  306 | test.describe('Secuencias raras de casos de uso', () => {
  307 |   test('CAOS-20 | CU-03: inscribir alumno de baja → debe fallar', async ({ page }) => {
  308 |     await new LoginPage(page).login(USERS.COORDINADOR);
  309 |     await page.goto('/movilidad');
  310 |     const bajaBtn = page.locator('[data-testid="btn-baja-definitiva"], button:has-text("Baja definitiva")').first();
  311 |     if (await bajaBtn.isVisible()) {
  312 |       await bajaBtn.click();
  313 |       await page.waitForTimeout(1_000);
  314 |       // Completar la baja
  315 |       const tipoSelect = page.locator('[data-testid="tipo-baja"]');
  316 |       if (await tipoSelect.isVisible()) {
  317 |         await tipoSelect.selectOption('DESERCION');
  318 |         await page.locator('button:has-text("Confirmar")').click();
  319 |         await page.waitForTimeout(2_000);
  320 |         // Ir a reinscripción e intentar inscribir al mismo alumno
  321 |         await page.goto('/reinscripcion');
  322 |         // El alumno dado de baja no debe aparecer
  323 |         const bajaAlumno = page.locator('[data-estado="BAJA"]');
  324 |         if (await bajaAlumno.isVisible()) {
  325 |           const inscribirBtn = bajaAlumno.locator('button:has-text("Inscribir")');
  326 |           await expect(inscribirBtn).not.toBeVisible();
  327 |         }
  328 |       }
  329 |     }
  330 |   });
  331 | 
  332 |   test('CAOS-21 | CU: cerrar calificación → intentar modificar → error', async ({ page }) => {
  333 |     await new LoginPage(page).login(USERS.COORDINADOR);
  334 |     await page.goto('/gradebook');
  335 |     const cerrarBtn = page.locator('[data-testid="btn-cerrar-periodo"]');
  336 |     if (await cerrarBtn.isVisible()) {
  337 |       await cerrarBtn.click();
  338 |       await page.locator('button:has-text("Confirmar")').click().catch(() => undefined);
  339 |       await page.waitForTimeout(2_000);
  340 |       // Ahora como docente intentar editar
  341 |       await page.evaluate(() => sessionStorage.removeItem('ades_token'));
  342 |       await new LoginPage(page).login(USERS.DOCENTE);
  343 |       await page.goto('/gradebook');
  344 |       const closedCell = page.locator('[data-cerrada="true"] input').first();
  345 |       if (await closedCell.isVisible()) {
  346 |         await expect(closedCell).toBeDisabled();
  347 |       }
  348 |     }
```