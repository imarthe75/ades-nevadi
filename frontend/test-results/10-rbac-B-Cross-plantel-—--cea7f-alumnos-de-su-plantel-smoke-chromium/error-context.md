# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: 10-rbac.spec.ts >> B. Cross-plantel — aislamiento de datos >> RBAC-04 | ADMIN_PLANTEL — API retorna solo alumnos de su plantel @smoke
- Location: e2e/tests/10-rbac.spec.ts:141:7

# Error details

```
Error: expect(received).toBe(expected) // Object.is equality

Expected: true
Received: false
```

# Page snapshot

```yaml
- generic [ref=e3]:
  - toolbar [ref=e4]:
    - generic [ref=e5]:
      - generic [ref=e6]:
        - generic [ref=e7]: "N"
        - generic [ref=e8]:
          - generic [ref=e9]: ADES
          - generic [ref=e10]: Instituto Nevadi
      - generic [ref=e12] [cursor=pointer]:
        - combobox "Plantel..." [ref=e13]
        - button "dropdown trigger" [ref=e14]:
          - img [ref=e15]
      - generic [ref=e17]: /
      - generic [ref=e18] [cursor=pointer]:
        - combobox "Nivel..." [ref=e19]
        - button "dropdown trigger" [ref=e20]:
          - img [ref=e21]
      - generic [ref=e23]: /
      - generic [ref=e24] [cursor=pointer]:
        - combobox "Ciclo..." [ref=e25]
        - button "dropdown trigger" [ref=e26]:
          - img [ref=e27]
    - generic [ref=e29]:
      - generic [ref=e31] [cursor=pointer]: 
      - generic [ref=e32] [cursor=pointer]:
        - generic [ref=e33]: T
        - generic [ref=e34]:
          - generic [ref=e35]: Test ADMIN_PLANTEL
          - generic [ref=e36]: Admin Plantel
        - generic [ref=e37]: 
  - generic [ref=e38]:
    - navigation [ref=e39]:
      - generic [ref=e40]: Principal
      - list [ref=e41]:
        - listitem [ref=e42]:
          - link " Dashboard" [ref=e43] [cursor=pointer]:
            - /url: /dashboard
            - generic [ref=e44]: 
            - generic [ref=e45]: Dashboard
      - generic [ref=e46]: Académico
      - list [ref=e47]:
        - listitem [ref=e48]:
          - link " Alumnos" [ref=e49] [cursor=pointer]:
            - /url: /alumnos
            - generic [ref=e50]: 
            - generic [ref=e51]: Alumnos
        - listitem [ref=e52]:
          - link " Reinscripción" [ref=e53] [cursor=pointer]:
            - /url: /reinscripcion
            - generic [ref=e54]: 
            - generic [ref=e55]: Reinscripción
        - listitem [ref=e56]:
          - link " Cierre de Ciclo" [ref=e57] [cursor=pointer]:
            - /url: /cierre-ciclo
            - generic [ref=e58]: 
            - generic [ref=e59]: Cierre de Ciclo
        - listitem [ref=e60]:
          - link " Gestión de Padres" [ref=e61] [cursor=pointer]:
            - /url: /padres-admin
            - generic [ref=e62]: 
            - generic [ref=e63]: Gestión de Padres
        - listitem [ref=e64]:
          - link " Profesores" [ref=e65] [cursor=pointer]:
            - /url: /profesores
            - generic [ref=e66]: 
            - generic [ref=e67]: Profesores
        - listitem [ref=e68]:
          - link " Grupos" [ref=e69] [cursor=pointer]:
            - /url: /grupos
            - generic [ref=e70]: 
            - generic [ref=e71]: Grupos
        - listitem [ref=e72]:
          - link " Aulas" [ref=e73] [cursor=pointer]:
            - /url: /aulas
            - generic [ref=e74]: 
            - generic [ref=e75]: Aulas
        - listitem [ref=e76]:
          - link " Planes de Estudio" [ref=e77] [cursor=pointer]:
            - /url: /planes-estudio
            - generic [ref=e78]: 
            - generic [ref=e79]: Planes de Estudio
        - listitem [ref=e80]:
          - link " Calificaciones" [ref=e81] [cursor=pointer]:
            - /url: /calificaciones
            - generic [ref=e82]: 
            - generic [ref=e83]: Calificaciones
        - listitem [ref=e84]:
          - link " Evaluaciones" [ref=e85] [cursor=pointer]:
            - /url: /evaluaciones
            - generic [ref=e86]: 
            - generic [ref=e87]: Evaluaciones
        - listitem [ref=e88]:
          - link " Asistencias" [ref=e89] [cursor=pointer]:
            - /url: /asistencias
            - generic [ref=e90]: 
            - generic [ref=e91]: Asistencias
        - listitem [ref=e92]:
          - link " Tareas" [ref=e93] [cursor=pointer]:
            - /url: /tareas
            - generic [ref=e94]: 
            - generic [ref=e95]: Tareas
        - listitem [ref=e96]:
          - link " Planeación" [ref=e97] [cursor=pointer]:
            - /url: /planeacion
            - generic [ref=e98]: 
            - generic [ref=e99]: Planeación
      - generic [ref=e100]: Operaciones
      - list [ref=e101]:
        - listitem [ref=e102]:
          - link " Horarios" [ref=e103] [cursor=pointer]:
            - /url: /horarios
            - generic [ref=e104]: 
            - generic [ref=e105]: Horarios
        - listitem [ref=e106]:
          - link " Calendario Escolar" [ref=e107] [cursor=pointer]:
            - /url: /calendario
            - generic [ref=e108]: 
            - generic [ref=e109]: Calendario Escolar
        - listitem [ref=e110]:
          - link " Conducta" [ref=e111] [cursor=pointer]:
            - /url: /conducta
            - generic [ref=e112]: 
            - generic [ref=e113]: Conducta
        - listitem [ref=e114]:
          - link " Expediente Médico" [ref=e115] [cursor=pointer]:
            - /url: /medico
            - generic [ref=e116]: 
            - generic [ref=e117]: Expediente Médico
        - listitem [ref=e118]:
          - link " Condiciones Crónicas" [ref=e119] [cursor=pointer]:
            - /url: /condiciones-cronicas
            - generic [ref=e120]: 
            - generic [ref=e121]: Condiciones Crónicas
        - listitem [ref=e122]:
          - link " Justificaciones Faltas" [ref=e123] [cursor=pointer]:
            - /url: /justificaciones
            - generic [ref=e124]: 
            - generic [ref=e125]: Justificaciones Faltas
        - listitem [ref=e126]:
          - link " Movilidad Estudiantil" [ref=e127] [cursor=pointer]:
            - /url: /movilidad
            - generic [ref=e128]: 
            - generic [ref=e129]: Movilidad Estudiantil
        - listitem [ref=e130]:
          - link " Optativas" [ref=e131] [cursor=pointer]:
            - /url: /optativas
            - generic [ref=e132]: 
            - generic [ref=e133]: Optativas
        - listitem [ref=e134]:
          - link " Admisión" [ref=e135] [cursor=pointer]:
            - /url: /admision
            - generic [ref=e136]: 
            - generic [ref=e137]: Admisión
      - generic [ref=e138]: Recursos Humanos
      - list [ref=e139]:
        - listitem [ref=e140]:
          - link " Personal No-Docente" [ref=e141] [cursor=pointer]:
            - /url: /personal-admin
            - generic [ref=e142]: 
            - generic [ref=e143]: Personal No-Docente
        - listitem [ref=e144]:
          - link " Licencias y Permisos" [ref=e145] [cursor=pointer]:
            - /url: /licencias
            - generic [ref=e146]: 
            - generic [ref=e147]: Licencias y Permisos
        - listitem [ref=e148]:
          - link " Capacitaciones" [ref=e149] [cursor=pointer]:
            - /url: /capacitaciones
            - generic [ref=e150]: 
            - generic [ref=e151]: Capacitaciones
        - listitem [ref=e152]:
          - link " Expediente Laboral" [ref=e153] [cursor=pointer]:
            - /url: /expediente-laboral
            - generic [ref=e154]: 
            - generic [ref=e155]: Expediente Laboral
        - listitem [ref=e156]:
          - link " Disponibilidad Docente" [ref=e157] [cursor=pointer]:
            - /url: /disponibilidad
            - generic [ref=e158]: 
            - generic [ref=e159]: Disponibilidad Docente
        - listitem [ref=e160]:
          - link " Asistencia Personal" [ref=e161] [cursor=pointer]:
            - /url: /asistencia-personal
            - generic [ref=e162]: 
            - generic [ref=e163]: Asistencia Personal
      - generic [ref=e164]: Comunicación
      - list [ref=e165]:
        - listitem [ref=e166]:
          - link " Comunicados" [ref=e167] [cursor=pointer]:
            - /url: /comunicados
            - generic [ref=e168]: 
            - generic [ref=e169]: Comunicados
        - listitem [ref=e170]:
          - link " Foros y Anuncios" [ref=e171] [cursor=pointer]:
            - /url: /foros
            - generic [ref=e172]: 
            - generic [ref=e173]: Foros y Anuncios
        - listitem [ref=e174]:
          - link " Encuestas" [ref=e175] [cursor=pointer]:
            - /url: /encuestas
            - generic [ref=e176]: 
            - generic [ref=e177]: Encuestas
      - generic [ref=e178]: Gradebook
      - list [ref=e179]:
        - listitem [ref=e180]:
          - link " Gradebook" [ref=e181] [cursor=pointer]:
            - /url: /gradebook
            - generic [ref=e182]: 
            - generic [ref=e183]: Gradebook
        - listitem [ref=e184]:
          - link " Mi Progreso" [ref=e185] [cursor=pointer]:
            - /url: /mi-progreso
            - generic [ref=e186]: 
            - generic [ref=e187]: Mi Progreso
        - listitem [ref=e188]:
          - link " Ponderaciones" [ref=e189] [cursor=pointer]:
            - /url: /ponderacion-config
            - generic [ref=e190]: 
            - generic [ref=e191]: Ponderaciones
      - generic [ref=e192]: Recursos
      - list [ref=e193]:
        - listitem [ref=e194]:
          - link " Rúbricas" [ref=e195] [cursor=pointer]:
            - /url: /rubricas
            - generic [ref=e196]: 
            - generic [ref=e197]: Rúbricas
        - listitem [ref=e198]:
          - link " Insignias" [ref=e199] [cursor=pointer]:
            - /url: /badges
            - generic [ref=e200]: 
            - generic [ref=e201]: Insignias
        - listitem [ref=e202]:
          - link " Portal Alumno" [ref=e203] [cursor=pointer]:
            - /url: /portal
            - generic [ref=e204]: 
            - generic [ref=e205]: Portal Alumno
      - generic [ref=e206]: Inteligencia
      - list [ref=e207]:
        - listitem [ref=e208]:
          - link " Dashboards BI" [ref=e209] [cursor=pointer]:
            - /url: /bi
            - generic [ref=e210]: 
            - generic [ref=e211]: Dashboards BI
        - listitem [ref=e212]:
          - link " Grade Analytics" [ref=e213] [cursor=pointer]:
            - /url: /grade-analytics
            - generic [ref=e214]: 
            - generic [ref=e215]: Grade Analytics
        - listitem [ref=e216]:
          - link " Asistente IA + Datos" [ref=e217] [cursor=pointer]:
            - /url: /ia
            - generic [ref=e218]: 
            - generic [ref=e219]: Asistente IA + Datos
        - listitem [ref=e220]:
          - link " Eval. Docente 360°" [ref=e221] [cursor=pointer]:
            - /url: /eval-docente
            - generic [ref=e222]: 
            - generic [ref=e223]: Eval. Docente 360°
        - listitem [ref=e224]:
          - link " Learning Paths" [ref=e225] [cursor=pointer]:
            - /url: /learning-paths
            - generic [ref=e226]: 
            - generic [ref=e227]: Learning Paths
      - generic [ref=e228]: Reportes
      - list [ref=e229]:
        - listitem [ref=e230]:
          - link " Generador de Reportes" [ref=e231] [cursor=pointer]:
            - /url: /reportes
            - generic [ref=e232]: 
            - generic [ref=e233]: Generador de Reportes
        - listitem [ref=e234]:
          - link " Certificados Digitales" [ref=e235] [cursor=pointer]:
            - /url: /certificados
            - generic [ref=e236]: 
            - generic [ref=e237]: Certificados Digitales
        - listitem [ref=e238]:
          - link " Expediente Digital" [ref=e239] [cursor=pointer]:
            - /url: /expediente-doc
            - generic [ref=e240]: 
            - generic [ref=e241]: Expediente Digital
      - generic [ref=e242]: Sistema
      - list [ref=e243]:
        - listitem [ref=e244]:
          - link " Monitor del Sistema" [ref=e245] [cursor=pointer]:
            - /url: /monitor
            - generic [ref=e246]: 
            - generic [ref=e247]: Monitor del Sistema
        - listitem [ref=e248]:
          - link " Administración" [ref=e249] [cursor=pointer]:
            - /url: /admin
            - generic [ref=e250]: 
            - generic [ref=e251]: Administración
      - generic [ref=e252]: Ayuda
      - list [ref=e253]:
        - listitem [ref=e254]:
          - link " Manual de Usuario" [ref=e255] [cursor=pointer]:
            - /url: /ayuda
            - generic [ref=e256]: 
            - generic [ref=e257]: Manual de Usuario
    - main [ref=e258]:
      - navigation "Breadcrumb" [ref=e261]:
        - list [ref=e262]:
          - listitem [ref=e263]:
            - link "Home" [ref=e264] [cursor=pointer]:
              - /url: /
          - listitem [ref=e265]:
            - generic [ref=e266]: 
            - generic [ref=e267]: Dashboard
      - generic [ref=e269]:
        - button "" [ref=e272] [cursor=pointer]:
          - generic [ref=e273]: 
        - generic [ref=e274]:
          - generic [ref=e275]:
            - heading "Dashboard" [level=2] [ref=e276]
            - paragraph [ref=e277]:
              - generic [ref=e278]: 
              - text: Instituto Nevadi — Vista global
          - generic [ref=e279]:
            - generic [ref=e280]: Test ADMIN_PLANTEL
            - generic [ref=e281]: Admin Plantel
        - generic [ref=e282]:
          - generic [ref=e283] [cursor=pointer]:
            - generic [ref=e285]: 
            - generic [ref=e286]: Alumnos inscritos
            - generic [ref=e288]: 
          - generic [ref=e289] [cursor=pointer]:
            - generic [ref=e291]: 
            - generic [ref=e292]: Profesores activos
            - generic [ref=e294]: 
          - generic [ref=e295] [cursor=pointer]:
            - generic [ref=e297]: 
            - generic [ref=e298]: Grupos activos
            - generic [ref=e300]: 
          - generic [ref=e301] [cursor=pointer]:
            - generic [ref=e303]: 
            - generic [ref=e304]: Clases hoy
        - separator
        - generic [ref=e306]:
          - heading "Planteles del Instituto" [level=4] [ref=e307]
          - generic [ref=e309]:
            - generic [ref=e310]: 
            - textbox "Buscar plantel..." [ref=e311]
        - separator
        - separator
        - heading "Accesos rápidos" [level=4] [ref=e317]
        - generic [ref=e318]:
          - link " Calificaciones" [ref=e319] [cursor=pointer]:
            - /url: /calificaciones
            - generic [ref=e320]: 
            - generic [ref=e321]: Calificaciones
          - link " Asistencias" [ref=e322] [cursor=pointer]:
            - /url: /asistencias
            - generic [ref=e323]: 
            - generic [ref=e324]: Asistencias
          - link " Tareas" [ref=e325] [cursor=pointer]:
            - /url: /tareas
            - generic [ref=e326]: 
            - generic [ref=e327]: Tareas
          - link " Comunicados" [ref=e328] [cursor=pointer]:
            - /url: /comunicados
            - generic [ref=e329]: 
            - generic [ref=e330]: Comunicados
          - link " Gradebook" [ref=e331] [cursor=pointer]:
            - /url: /gradebook
            - generic [ref=e332]: 
            - generic [ref=e333]: Gradebook
          - link " Grade Analytics" [ref=e334] [cursor=pointer]:
            - /url: /grade-analytics
            - generic [ref=e335]: 
            - generic [ref=e336]: Grade Analytics
          - link " Portal Alumno" [ref=e337] [cursor=pointer]:
            - /url: /portal
            - generic [ref=e338]: 
            - generic [ref=e339]: Portal Alumno
          - link " Reportes" [ref=e340] [cursor=pointer]:
            - /url: /reportes
            - generic [ref=e341]: 
            - generic [ref=e342]: Reportes
```

# Test source

```ts
  56  |     await page.goto('/admin', { waitUntil: 'domcontentloaded' });
  57  |     await page.waitForTimeout(2_000);
  58  | 
  59  |     // Si Angular no guarda → documenta el finding, no falla el test
  60  |     const currentUrl = page.url();
  61  |     if (currentUrl.includes('/admin')) {
  62  |       console.warn('[FINDING][P1] RBAC-01: Angular carga /admin para DOCENTE con storage elevado — falta RouteGuard');
  63  |     }
  64  | 
  65  |     // Lo que IMPORTA: el BFF debe rechazar cualquier API call admin-only
  66  |     const adminApiCalls = apiResponses().filter(r =>
  67  |       (r.url.includes('/admin') || r.url.includes('/usuarios/admin')) &&
  68  |       r.status < 400
  69  |     );
  70  |     if (adminApiCalls.length > 0) {
  71  |       console.warn('[FINDING][P1] RBAC-01: APIs admin retornaron 2xx con storage elevado:', adminApiCalls.map(r => r.url));
  72  |     }
  73  | 
  74  |     // No debe haber 5xx
  75  |     assertNoServerErrors(apiResponses());
  76  |     await expect(page.locator('app-root')).toBeVisible();
  77  |   });
  78  | 
  79  |   test('RBAC-02 | token firmado falso con claim admin → BFF rechaza con 401 @smoke', async ({ page }) => {
  80  |     const payload = JSON.stringify({ sub: 'attacker', rol: 'ADMIN_GLOBAL', nivel_acceso: 0, exp: 9999999999 });
  81  |     // btoa works in both browser and Node (Playwright) contexts
  82  |     const fakeAdminToken =
  83  |       'eyJhbGciOiJIUzI1NiJ9.' +
  84  |       btoa(unescape(encodeURIComponent(payload))) +
  85  |       '.INVALID_SIGNATURE';
  86  | 
  87  |     const res = await page.request.get(`${BFF_BASE}/api/v1/alumnos`, {
  88  |       headers: { Authorization: `Bearer ${fakeAdminToken}` },
  89  |     });
  90  |     expect(res.status()).toBe(401);
  91  |   });
  92  | 
  93  |   test('RBAC-03 | DOCENTE POST a endpoint admin → rechazado por BFF @smoke', async ({ page }) => {
  94  |     // Obtener token real de docente vía ApiClient (sin depender de sessionStorage)
  95  |     const tokenRes = await page.request.post(`${BFF_BASE}/api/v1/auth/token`, {
  96  |       data: { username: USERS.DOCENTE.email, password: USERS.DOCENTE.password },
  97  |     }).catch(() => null);
  98  | 
  99  |     let token = '';
  100 |     if (tokenRes?.ok()) {
  101 |       const body = await tokenRes.json().catch(() => null);
  102 |       token = body?.access_token ?? '';
  103 |     }
  104 | 
  105 |     if (!token) {
  106 |       // Fallback: login por UI y capturar token de sessionStorage
  107 |       const lp = new LoginPage(page);
  108 |       await lp.login(USERS.DOCENTE);
  109 |       token = await page.evaluate(() =>
  110 |         sessionStorage.getItem('ades_token') ??
  111 |         sessionStorage.getItem('access_token') ??
  112 |         sessionStorage.getItem('token') ?? ''
  113 |       );
  114 |     }
  115 | 
  116 |     if (!token) {
  117 |       console.warn('[RBAC-03] No se pudo obtener token de DOCENTE — test parcial');
  118 |       return;
  119 |     }
  120 | 
  121 |     // POST a ruta de admin — debe ser rechazado
  122 |     const endpoints = [
  123 |       '/api/v1/admin/usuarios',
  124 |       '/api/v1/usuarios',
  125 |     ];
  126 | 
  127 |     for (const ep of endpoints) {
  128 |       const res = await page.request.post(`${BFF_BASE}${ep}`, {
  129 |         headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
  130 |         data: { email: 'hacker@test.com', rol: 'ADMIN_GLOBAL', nombre: 'Hacker' },
  131 |       });
  132 |       // 401, 403, 404 (ruta no existe), 405 (método no permitido) — todos son OK
  133 |       expect([401, 403, 404, 405, 422]).toContain(res.status());
  134 |     }
  135 |   });
  136 | });
  137 | 
  138 | // ── B. Cross-plantel ──────────────────────────────────────────────────────────
  139 | 
  140 | test.describe('B. Cross-plantel — aislamiento de datos', () => {
  141 |   test('RBAC-04 | ADMIN_PLANTEL — API retorna solo alumnos de su plantel @smoke', async ({ page }) => {
  142 |     const lp = new LoginPage(page);
  143 |     await lp.login(USERS.ADMIN_PLANTEL);
  144 | 
  145 |     // Obtener token
  146 |     const token = await page.evaluate(() =>
  147 |       sessionStorage.getItem('ades_token') ??
  148 |       sessionStorage.getItem('access_token') ?? ''
  149 |     );
  150 |     if (!token) { test.skip(); return; }
  151 | 
  152 |     const res = await page.request.get(`${BFF_BASE}/api/v1/alumnos?limit=5`, {
  153 |       headers: { Authorization: `Bearer ${token}` },
  154 |     });
  155 |     // 200 OK con datos del propio plantel o 401 si el token no está en storage
> 156 |     expect([200, 401].includes(res.status())).toBe(true);
      |                                               ^ Error: expect(received).toBe(expected) // Object.is equality
  157 |     if (res.ok()) {
  158 |       const body = await res.json();
  159 |       const alumnos = Array.isArray(body) ? body : (body.items ?? body.data ?? []);
  160 |       expect(alumnos.length).toBeGreaterThanOrEqual(0);
  161 |     }
  162 |   });
  163 | 
  164 |   test('RBAC-05 | plantel_id ajeno en param → BFF filtra por RBAC o rechaza', async ({ page }) => {
  165 |     const lp = new LoginPage(page);
  166 |     await lp.login(USERS.DOCENTE);
  167 | 
  168 |     const token = await page.evaluate(() =>
  169 |       sessionStorage.getItem('ades_token') ??
  170 |       sessionStorage.getItem('access_token') ?? ''
  171 |     );
  172 |     if (!token) { test.skip(); return; }
  173 | 
  174 |     const fakePlantelId = '00000000-0000-0000-0000-000000000001';
  175 |     const res = await page.request.get(`${BFF_BASE}/api/v1/alumnos?plantel_id=${fakePlantelId}&limit=5`, {
  176 |       headers: { Authorization: `Bearer ${token}` },
  177 |     });
  178 | 
  179 |     // El BFF puede:
  180 |     // a) Ignorar el param y devolver 200 con datos del propio plantel (RBAC server-side)
  181 |     // b) Rechazar con 400/403
  182 |     // c) Retornar 200 vacío (filtro por plantel ajeno da 0 resultados)
  183 |     // Lo que NUNCA debe pasar: 200 con datos de un plantel diferente al del usuario
  184 |     expect(res.status()).not.toBe(500);
  185 |     if (res.ok()) {
  186 |       const body = await res.json().catch(() => null);
  187 |       // Solo registramos si hay datos — la verificación de que son del plantel correcto
  188 |       // requeriría conocer el plantel_id real del usuario, que no tenemos aquí
  189 |       console.log(`[RBAC-05] BFF con plantel_id ajeno retornó ${res.status()} — verificar manualmente que no filtra cross-plantel`);
  190 |     }
  191 |     await expect(page.locator('app-root')).toBeVisible();
  192 |   });
  193 | 
  194 |   test('RBAC-06 | UUID aleatorio de alumno ajeno → 403 o 404 @smoke', async ({ page }) => {
  195 |     const lp = new LoginPage(page);
  196 |     await lp.login(USERS.DOCENTE);
  197 |     const token = await page.evaluate(() =>
  198 |       sessionStorage.getItem('ades_token') ??
  199 |       sessionStorage.getItem('access_token') ?? ''
  200 |     );
  201 |     if (!token) { test.skip(); return; }
  202 | 
  203 |     const fakeAlumnoId = '11111111-1111-1111-1111-111111111111';
  204 |     const res = await page.request.get(`${BFF_BASE}/api/v1/alumnos/${fakeAlumnoId}`, {
  205 |       headers: { Authorization: `Bearer ${token}` },
  206 |     });
  207 |     expect([401, 403, 404]).toContain(res.status());
  208 |   });
  209 | });
  210 | 
  211 | // ── C. Rutas protegidas por nivel (Angular Route Guards) ──────────────────────
  212 | 
  213 | test.describe('C. Route Guards — hallazgos de Angular', () => {
  214 |   /**
  215 |    * CONTEXTO: Se confirmó que Angular NO implementa Route Guards para estos módulos.
  216 |    * El BFF rechaza las llamadas API, pero la UI carga la ruta.
  217 |    * Estos tests documentan el hallazgo con console.warn y verifican que
  218 |    * al menos el BFF bloquea las llamadas de datos.
  219 |    */
  220 | 
  221 |   const protectedRoutes = [
  222 |     { route: '/admin',          label: 'admin panel' },
  223 |     { route: '/cierre-ciclo',   label: 'cierre de ciclo' },
  224 |     { route: '/licencias',      label: 'RRHH licencias' },
  225 |     { route: '/expediente-laboral', label: 'expediente laboral' },
  226 |   ];
  227 | 
  228 |   for (const { route, label } of protectedRoutes) {
  229 |     test(`RBAC-07 | DOCENTE en ${label} → BFF bloquea APIs (guard ausente documentado)`, async ({ page }) => {
  230 |       const apiResponses = attachApiMonitor(page);
  231 |       const lp = new LoginPage(page);
  232 |       await lp.login(USERS.DOCENTE);
  233 | 
  234 |       await page.goto(route, { waitUntil: 'domcontentloaded' });
  235 |       await page.waitForTimeout(1_500);
  236 | 
  237 |       const url = page.url();
  238 |       if (url.includes(route.replace('/', ''))) {
  239 |         console.warn(`[FINDING][P2] RBAC-07: Angular carga ${route} para DOCENTE — falta CanActivate RouteGuard`);
  240 |       }
  241 | 
  242 |       // Lo importante: sin 5xx ni crash
  243 |       assertNoServerErrors(apiResponses());
  244 |       await expect(page.locator('app-root')).toBeVisible();
  245 |     });
  246 |   }
  247 | 
  248 |   test('RBAC-10 | COORDINADOR en /certificados — botón emitir ausente o deshabilitado', async ({ page }) => {
  249 |     await new LoginPage(page).login(USERS.COORDINADOR);
  250 |     await page.goto('/certificados', { waitUntil: 'domcontentloaded' });
  251 |     await page.waitForTimeout(2_000);
  252 | 
  253 |     // Si la ruta carga, el botón emitir NO debe estar habilitado para coordinador
  254 |     const emitirBtn = page.locator('[data-testid="btn-emitir-certificado"], button:has-text("Emitir")');
  255 |     const count = await emitirBtn.count();
  256 |     if (count > 0) {
```