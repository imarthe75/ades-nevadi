# Migración ADES — Nuevo Servidor
## 2026-07-10 | IP: 163.192.138.130

---

## ESTADO DE LA MIGRACIÓN

### ✅ Completado

1. **Docker & Docker Compose instalados**
   - Docker 29.1.3 instalado y activo
   - docker-compose v5.3.1 instalado en `/usr/local/bin/`

2. **Archivo .env creado con secretos seguros**
   - Ubicación: `/opt/ades/.env`
   - Contraseñas generadas con `openssl rand -hex`
   - IP actualizada: 163.192.138.130
   - Bucket MinIO: ades-archivos

3. **docker-compose.yml optimizado para recursos limitados**
   - Servidor: 2 cores, 12GB RAM (antes: 4 cores, 24GB)
   - PostgreSQL: 1GB límite (antes 2GB)
   - Valkey: 256MB máx (antes 512MB)
   - Authentik: 500M + 300M workers (antes 800M + 600M)
   - FastAPI/BFF: 256M cada uno (antes 512M)
   - Superset: 800M (antes 1.5GB)
   - Prometheus retention: 7 días (antes 30)

4. **Servicios desactivados (Fases posteriores)**
   - Vault/vault-init (gestión de secretos premium)
   - Carbone (generador PDF - Fase 18)
   - Flowise (chatbot IA - Fase 17)
   - ntfy (push notifications - Fase 20)
   - Stirling-PDF (procesamiento PDF - Fase 21)
   - n8n (automatización - Fase 23)
   - Paperless (gestión documental - Fase 28)
   - H5P (contenido educativo - Fase 25)

5. **Script Certbot preparado**
   - Ubicación: `/opt/ades/init-certbot.sh`
   - Obtiene certificados HTTPS automáticamente
   - Ejecutable post-bootstrap

### 🔄 En Progreso

- **Docker Compose levantando servicios**
  - Compilando imágenes (ades-api, ades-bff, ades-frontend, superset)
  - Creando volúmenes persistentes
  - Inicializando bases de datos (PostgreSQL, Authentik)

### ⏭️ Próximos Pasos (POST-BOOTSTRAP)

1. **Verificar servicios levantados**
   ```bash
   sudo docker-compose ps
   sudo docker-compose logs -f ades-api    # ver logs
   ```

2. **Obtener certificados SSL**
   ```bash
   bash /opt/ades/init-certbot.sh
   # O manualmente:
   sudo docker-compose run --rm certbot certonly --webroot \
     -w /var/www/certbot \
     -d ades.setag.mx \
     --email admin@setag.mx \
     --agree-tos --no-eff-email
   ```

3. **Verificar DNS — CRÍTICO**
   - `nslookup ades.setag.mx` debe retornar **163.192.138.130**
   - Sin esto, Certbot fallará y nginx no tendrá HTTPS

4. **Ejecutar pruebas exploratorias**
   ```bash
   cd /opt/ades/ades_testing
   python 01_ades_explorer_v4_complete.py
   python 02_claude_qa_analyzer.py
   python 03_report_generator.py
   ```

5. **Verificar Authentik bootstrap**
   - Puerto: `http://localhost:9010` (interno)
   - Crear aplicaciones OIDC para frontend/superset
   - Configurar SMTP si es necesario

---

## ARCHIVO .ENV — REFERENCIA RÁPIDA

| Variable | Valor |
|---|---|
| POSTGRES_DB | ades |
| POSTGRES_USER | ades_admin |
| POSTGRES_PASSWORD | *[seguro]* |
| VALKEY_PASSWORD | *[seguro]* |
| AUTHENTIK_SECRET_KEY | *[seguro - 32 hex]* |
| MINIO_BUCKET | ades-archivos |
| CERTBOT_DOMAIN | ades.setag.mx |
| ENVIRONMENT | development |

---

## CAMBIOS PRINCIPALES

### Reducción de Memoria

```
Antes (4 cores, 24GB):           Después (2 cores, 12GB):
─────────────────────           ─────────────────────
postgres:      2GB              postgres:      1GB     (-50%)
valkey:        600M             valkey:        300M    (-50%)
authentik:     800M+600M        authentik:     500M+300M (-37%)
superset:      1.5GB            superset:      800M    (-47%)
─────────────────────           ─────────────────────
Total:        ~14.5GB           Total:        ~8GB    (-45%)
```

### Desactivación de Servicios No-Críticos

Guardados comentados en `docker-compose.yml` — pueden re-activarse cuando:
- Se tenga servidor con más recursos (12GB+)
- Se alcance la Fase correspondiente

---

## CREDENCIALES

| Servicio | Usuario | Contraseña | Puerto |
|---|---|---|---|
| PostgreSQL | ades_admin | *[.env]* | 5432 |
| Valkey | — | *[.env]* | 6379 |
| Authentik | admin | *[BOOTSTRAP]* | 9000 |
| MinIO/SeaweedFS | ades_minio | *[.env]* | 9000 |
| Superset | admin | *[.env]* | 8088 |
| Grafana | admin | *[.env]* | 3003 |
| BFF | — | JWT | 8080 |
| FastAPI | — | JWT | 8000 |

---

## MONITOREO POST-BOOTSTRAP

```bash
# Ver logs en tiempo real
docker-compose logs -f ades-api ades-bff postgres

# Health check de servicios
curl http://localhost:8000/api/v1/health
curl http://localhost:8080/actuator/health
curl http://localhost:9000/admin/

# Ver métricas
http://localhost:9090  (Prometheus)
http://localhost:3003  (Grafana)
```

---

## CHECKLIST DE VALIDACIÓN

- [ ] `docker-compose ps` muestra todos los servicios corriendo (excepto los desactivados)
- [ ] PostgreSQL sano: `docker-compose exec postgres pg_isready`
- [ ] Valkey sano: `docker-compose exec valkey valkey-cli ping`
- [ ] Authentik bootstrap completado sin errores
- [ ] Frontend compilado en `/opt/ades/frontend/dist/` (si es ng serve)
- [ ] BFF compilado en `/opt/ades/backend-spring/target/` (si es maven)
- [ ] nginx reverse proxy funciona: `curl http://localhost:80`
- [ ] Certificados Let's Encrypt: `ls /etc/letsencrypt/live/ades.setag.mx/`
- [ ] HTTPS funciona: `curl https://ades.setag.mx`
- [ ] Pruebas exploratorias ejecutadas y sin fallos críticos

---

## ROLLBACK (Si es necesario)

```bash
# Bajar todos los servicios
docker-compose down

# Borrar datos (CUIDADO — irrecuperable)
docker-compose down -v

# Volver al servidor anterior (contactar administrador)
```

---

## CONTACTO / NOTAS

- **Servidor anterior**: 129.213.35.140 (en transición)
- **Servidor nuevo**: 163.192.138.130 (actualmente levantando)
- **Estado**: En bootstrap — esperando a que todos los servicios se levanten
- **Siguiente actualización**: Validar salud de servicios + obtener certificados SSL

---

**Generado**: 2026-07-10 14:55 UTC
**Responsable**: Claude Code Agent
**Fase**: Infraestructura (Fase 1 — Bootstrap)
