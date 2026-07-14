# Instrucciones Post-Bootstrap — ADES
## Servidor: 163.192.138.130 | Fecha: 2026-07-10

---

## ORDEN DE EJECUCIÓN

Ejecutar **en este orden exacto** una vez que Docker termine de compilar:

### PASO 1: Validar que todo está levantado ✓
```bash
cd /opt/ades
bash validate-bootstrap.sh
```

**Esperado**: 15+ pruebas pasadas, especialmente PostgreSQL y servicios principales.

Si algo falla:
```bash
# Ver logs detallados
sudo docker-compose logs -f [nombre_servicio]

# Reiniciar un servicio
sudo docker-compose restart [nombre_servicio]
```

---

### PASO 2: Cargar Datos de Ejemplo
```bash
# Nota: Las migraciones (001-114) se ejecutan automáticamente al crear PostgreSQL
# Verificar que están cargadas:
sudo docker-compose exec postgres psql -U ades_admin -d ades -c \
  "SELECT COUNT(*) FROM ades_alumnos;"

# Si retorna 0, cargar seeds manualmente:
for f in db/seeds/*.sql; do
  echo "Cargando $f..."
  sudo docker-compose exec -T postgres psql -U ades_admin -d ades < "$f"
done

# Verificar integridad
bash DATOS_EJEMPLO.md  # Lee las instrucciones de verificación
```

**Criterios de éxito**:
- `ades_alumnos`: > 500 registros
- `ades_grupos`: > 20 registros
- `ades_materias`: > 50 registros
- `ades_planteles`: 3 registros (Metepec, Tenancingo, Ixtapan)

---

### PASO 3: Obtener Certificados SSL/TLS
```bash
# **CRÍTICO**: Asegurar que DNS está correcto
nslookup ades.setag.mx
# Debe retornar: 163.192.138.130

# Obtener certificados Let's Encrypt
bash init-certbot.sh

# El script:
# 1. Espera a que nginx esté listo
# 2. Ejecuta certbot webroot challenge
# 3. Reinicia nginx con certificados

# Verificar certificados
ls -la /etc/letsencrypt/live/ades.setag.mx/

# Probar HTTPS
curl -I https://ades.setag.mx/
```

**Criterios de éxito**:
- Certificados en `/etc/letsencrypt/live/ades.setag.mx/`
- HTTP 200 en `curl https://ades.setag.mx/`
- nginx escuchando en 443

---

### PASO 4: Configurar Authentik (OIDC)
```bash
# Acceder a Authentik admin
# URL: http://localhost:9010 (solo localhost en desarrollo)
# O vía nginx: https://auth.ades.setag.mx/

# Credenciales iniciales (bootstrap)
# Usuario: admin
# Contraseña: [AUTHENTIK_BOOTSTRAP_PASSWORD en .env]

# TAREAS:
# 1. Cambiar contraseña admin
# 2. Crear aplicación OIDC "ades-frontend"
#    - Client ID: ades-frontend
#    - Client Secret: [generar]
#    - Redirect URI: https://ades.setag.mx/callback
# 3. Crear aplicación OIDC "superset"
#    - Client ID: superset
#    - Client Secret: [generar]
# 4. Configurar Google SSO (opcional)
#    - Consola Google Cloud
#    - OAuth 2.0 credentials
# 5. Configurar SMTP (opcional)
#    - Si se dejó vacío en .env, no se enviarán emails

# Guardar Client Secrets en .env después
nano .env
# Actualizar: OIDC_CLIENT_SECRET, SUPERSET_OIDC_CLIENT_SECRET

# Reiniciar servicios que usan OIDC
docker-compose restart ades-bff ades-frontend superset
```

---

### PASO 5: Ejecutar Pruebas Exploratorias
```bash
# Las pruebas automatizan validación de 52 módulos
cd /opt/ades/ades_testing

# Ejecutar exploración completa
python 01_ades_explorer_v4_complete.py

# Generar análisis de inconsistencias
python 02_claude_qa_analyzer.py

# Generar reporte HTML
python 03_report_generator.py

# Ver resultados
open ades_testing_report_YYYY-MM-DD.html
# O en terminal:
cat ades_testing_report_YYYY-MM-DD.txt
```

**Esperado**:
- 52/52 módulos explorados
- < 10 inconsistencias críticas
- Reporte generado exitosamente

---

## VERIFICACIONES RÁPIDAS

### ¿Está PostgreSQL listo?
```bash
sudo docker-compose exec postgres pg_isready -U ades_admin -d ades
# Respuesta: "accepting connections" = OK
```

### ¿Tengo datos de ejemplo?
```bash
sudo docker-compose exec postgres psql -U ades_admin -d ades -t -c \
  "SELECT COUNT(*) FROM ades_alumnos; SELECT COUNT(*) FROM ades_grupos;"
```

### ¿Funcionan los endpoints?
```bash
# FastAPI
curl http://localhost:8000/api/v1/health

# Spring Boot BFF
curl http://localhost:8080/actuator/health

# nginx reverse proxy
curl http://localhost:80/

# Superset
curl http://localhost:8088/
```

### ¿Dónde están los logs?
```bash
# Todos los servicios
docker-compose logs -f

# Un servicio específico
docker-compose logs -f ades-api

# Últimas líneas
docker-compose logs --tail=50 ades-bff
```

---

## PROBLEMAS Y SOLUCIONES

### Problema: "PostgreSQL no responde"
```bash
# Reiniciar
docker-compose restart postgres

# Esperar 30 segundos
sleep 30

# Verificar
docker-compose logs postgres | tail -50
```

### Problema: "Docker compose no encuentra servicios"
```bash
# Verificar que estás en /opt/ades
pwd

# Verificar docker-compose.yml existe
ls -la docker-compose.yml

# Comprobar sintaxis YAML
docker-compose config > /dev/null && echo "OK"
```

### Problema: "Certificados SSL no funcionan"
```bash
# Verificar DNS
nslookup ades.setag.mx
# DEBE retornar 163.192.138.130

# Si no, actualizar DNS en tu registrador
# Esperar propagación (5-15 minutos)

# Reintentar Certbot
bash init-certbot.sh
```

### Problema: "Authentik no se inicia"
```bash
# Ver logs detallados
docker-compose logs authentik-server | tail -100

# Verificar variable AUTHENTIK_BOOTSTRAP_PASSWORD en .env
grep AUTHENTIK_BOOTSTRAP /opt/ades/.env

# Resetear (destructivo)
docker-compose down -v postgres
docker-compose up -d postgres
sleep 60
```

---

## MONITOREO CONTINUO

### Dashboards
- **Grafana**: http://localhost:3003 (admin/[contraseña])
- **Prometheus**: http://localhost:9090
- **Superset**: http://localhost:8088 (admin/[contraseña])
- **Authentik**: http://localhost:9010 (admin/[contraseña])

### Métricas en tiempo real
```bash
# Uso de recursos
docker stats

# Conexiones a BD
docker-compose exec postgres psql -U ades_admin -d ades -c \
  "SELECT datname, count(*) FROM pg_stat_activity GROUP BY datname;"

# Tamaño de BD
docker-compose exec postgres psql -U ades_admin -d ades -c \
  "SELECT pg_database.datname, pg_size_pretty(pg_database_size(pg_database.datname)) \
   FROM pg_database WHERE datname LIKE 'ades%';"
```

---

## CHECKLIST FINAL

- [ ] `bash validate-bootstrap.sh` → 15+ pruebas pasadas
- [ ] PostgreSQL tiene datos de ejemplo (ALUMNOS > 500)
- [ ] Certificados SSL en `/etc/letsencrypt/live/ades.setag.mx/`
- [ ] Authentik operativo y bootstrap completado
- [ ] OIDC applications creadas (ades-frontend, superset)
- [ ] Client Secrets actualizados en .env
- [ ] Servicios reiniciados post-OIDC
- [ ] Pruebas exploratorias completadas (52/52 módulos)
- [ ] Reporte de pruebas revisado sin errores críticos
- [ ] Grafana accesible y mostrando métricas
- [ ] Superset accesible y conectado a PostgreSQL
- [ ] Frontend cargando sin errores de OIDC
- [ ] Backups programados (si no está)

---

## PRÓXIMAS FASES (Cuando sea apropiado)

Habilitar en docker-compose.yml:
1. **Fase 17**: Flowise (chatbot IA) — 1GB RAM
2. **Fase 18**: Carbone (generación PDF) — 1GB RAM
3. **Fase 20**: ntfy (push notifications) — 128MB RAM
4. **Fase 21**: Stirling-PDF (procesamiento) — 1.5GB RAM
5. **Fase 23**: n8n (automatización) — 1GB RAM
6. **Fase 28**: Paperless (gestión documental) — 1GB RAM

**Total recursos necesarios**: 12GB+ RAM

---

## ROLL-BACK

Si algo sale mal y necesitas volver atrás:
```bash
# OPCIÓN 1: Reiniciar servicios individuales
docker-compose restart [servicio]

# OPCIÓN 2: Bajar y levantar todo
docker-compose down
docker-compose up -d

# OPCIÓN 3: Borrar todo (DESTRUCTIVO)
docker-compose down -v
# Esto borra TODOS los volúmenes persistentes

# OPCIÓN 4: Volver al servidor anterior
# Contactar a administrador para restore del servidor 129.213.35.140
```

---

## REFERENCIAS RÁPIDAS

| Tarea | Comando |
|---|---|
| Ver estado | `docker-compose ps` |
| Ver logs | `docker-compose logs -f [servicio]` |
| Ejecutar comando en BD | `docker-compose exec postgres psql -U ades_admin -d ades` |
| Restart de servicio | `docker-compose restart [servicio]` |
| Bajar todo | `docker-compose down` |
| Estadísticas | `docker stats` |
| Certificados | `ls /etc/letsencrypt/live/ades.setag.mx/` |
| Backup BD | `docker-compose exec postgres pg_dump -U ades_admin ades > backup.sql` |

---

**Última actualización**: 2026-07-10 15:15 UTC
**Estado**: En bootstrap — esperando a que servicios se levanten
**Próxima revisión**: Después de que PostgreSQL esté UP
