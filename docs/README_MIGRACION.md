# ADES — Migración a Nuevo Servidor
## 2026-07-10 | IP: 163.192.138.130 (2 cores, 12GB RAM)

---

## 📋 DOCUMENTACIÓN GENERADA

Todos estos archivos se crearon como parte de la migración del 10 de julio de 2026:

### 1. **RESUMEN_MIGRACION.txt** ← LEER PRIMERO
   - Resumen ejecutivo de la migración
   - Estado actual (bootstrap en progreso)
   - Próximos pasos detallados
   - Criterios de éxito

### 2. **MIGRACION_2026_07_10.md**
   - Documentación técnica completa
   - Cambios en docker-compose.yml
   - Reducción de memoria (45%)
   - Servicios desactivados
   - Credenciales de referencia

### 3. **INSTRUCCIONES_POST_BOOTSTRAP.md** ← LEER DESPUÉS DE BOOTSTRAP
   - Orden exacto de ejecución (5 pasos)
   - Validación de cada paso
   - Solución de problemas comunes
   - Monitoreo continuo
   - Checklist final

### 4. **DATOS_EJEMPLO.md**
   - Cómo cargar datos de ejemplo
   - Migraciones automáticas vs manuales
   - Verificación de integridad
   - Reset completo (si es necesario)
   - Troubleshooting de bases de datos

### 5. **init-certbot.sh** (Script ejecutable)
   - Obtiene certificados SSL/TLS automáticamente
   - Configura Let's Encrypt webroot
   - Reinicia nginx con certificados
   - Ejecutable: `bash init-certbot.sh`

### 6. **validate-bootstrap.sh** (Script ejecutable)
   - Valida que todos los servicios FASE 1 están levantados
   - Verifica salud de PostgreSQL, FastAPI, Spring Boot
   - Confirma datos de ejemplo cargados
   - Ejecutable: `bash validate-bootstrap.sh`

---

## 🚀 FLUJO DE TRABAJO

```
AHORA (2026-07-10 15:15 UTC)
    ↓
    Docker compilando imágenes...
    ↓
    (Esperar 10-15 minutos)
    ↓
PASO 1: bash validate-bootstrap.sh
    ✓ Verifica que servicios están UP
    ✓ Confirma PostgreSQL listo
    ↓
PASO 2: Cargar datos de ejemplo (DATOS_EJEMPLO.md)
    ✓ Migraciones ya aplicadas (001-114)
    ✓ Cargar seeds si es necesario
    ↓
PASO 3: bash init-certbot.sh
    ✓ Obtener certificados SSL
    ✓ Configurar nginx con HTTPS
    ↓
PASO 4: Configurar Authentik (INSTRUCCIONES_POST_BOOTSTRAP.md)
    ✓ Crear aplicaciones OIDC
    ✓ Generar Client Secrets
    ✓ Reiniciar servicios
    ↓
PASO 5: python ades_testing/01_ades_explorer_v4_complete.py
    ✓ Ejecutar pruebas exploratorias
    ✓ Validar 52 módulos del sistema
    ✓ Generar reporte

RESULTADO: Sistema ADES completamente funcional en nuevo servidor
```

---

## 📊 ESTADO ACTUAL

| Componente | Estado | Notas |
|---|---|---|
| Docker | ✅ Instalado | v29.1.3 |
| docker-compose | ✅ Instalado | v5.3.1 |
| .env | ✅ Creado | Secretos seguros |
| docker-compose.yml | ✅ Optimizado | Memoria reducida 45% |
| Servicios FASE 1 | 🔄 Compilando | ETA: 10-15 min |
| Servicios opcionales | 🔲 Desactivados | Se pueden habilitar luego |
| Documentación | ✅ Completa | 6 archivos + scripts |
| Certificados SSL | ⏳ Pendiente | Ejecutar init-certbot.sh |
| Datos de ejemplo | ⏳ Pendiente | Cargar post-bootstrap |
| Pruebas | ⏳ Pendiente | Ejecutar post-OIDC |

---

## 🎯 METAS DE ÉXITO

**Fase Bootstrap (hoy)**
- [ ] Todos los servicios FASE 1 levantados
- [ ] PostgreSQL con 114 migraciones aplicadas
- [ ] Datos de ejemplo cargados
- [ ] validate-bootstrap.sh pasa 15+ pruebas

**Fase SSL**
- [ ] Certificados Let's Encrypt válidos
- [ ] nginx escuchando en 443
- [ ] HTTPS funcional en ades.setag.mx

**Fase OIDC**
- [ ] Authentik bootstrap completado
- [ ] Aplicaciones OIDC creadas
- [ ] Client Secrets en .env
- [ ] Servicios reiniciados

**Fase Validación**
- [ ] Pruebas exploratorias: 52/52 módulos
- [ ] Reporte generado sin errores críticos
- [ ] Todos los endpoints accesibles

---

## 📞 RECURSOS

### Archivos de Configuración
```
/opt/ades/
├── .env                              ← Secretos (NO commitear)
├── docker-compose.yml                ← Orquestación
├── infrastructure/
│   ├── nginx/                        ← Reverse proxy
│   ├── authentik/                    ← OIDC IdP
│   ├── prometheus/                   ← Monitoreo
│   └── grafana/                      ← Dashboards
└── db/
    ├── migrations/                   ← DDL 001-114
    └── seeds/                        ← Datos de ejemplo
```

### Comandos Frecuentes
```bash
# Ver estado
docker-compose ps

# Ver logs
docker-compose logs -f [servicio]

# Acceder a BD
docker-compose exec postgres psql -U ades_admin -d ades

# Reiniciar servicio
docker-compose restart [servicio]

# Bajar todo
docker-compose down

# Validar bootstrap
bash validate-bootstrap.sh

# Obtener certificados
bash init-certbot.sh
```

### URLs Locales (Desarrollo)
- PostgreSQL: localhost:5432
- Valkey: localhost:6379
- SeaweedFS: localhost:9000 (S3) / 8888 (Filer)
- Authentik: localhost:9010
- FastAPI: localhost:8000
- Spring Boot: localhost:8080
- Frontend: localhost:4200
- Superset: localhost:8088
- Grafana: localhost:3003
- Prometheus: localhost:9090

### URLs en Producción
- ades.setag.mx (nginx reverse proxy - HTTPS)
- auth.ades.setag.mx (Authentik - HTTPS)
- bi.ades.setag.mx (Superset - HTTPS)
- monitor.ades.setag.mx (Grafana - HTTPS)

---

## ⚠️ NOTAS CRÍTICAS

1. **DNS es CRÍTICO**
   - `nslookup ades.setag.mx` DEBE retornar `163.192.138.130`
   - Sin esto, Certbot fallará y no habrá HTTPS

2. **Recursos Limitados**
   - Servidor: 2 cores, 12GB RAM
   - NO ejecutar múltiples builds simultáneamente
   - Monitor con: `docker stats`

3. **Migraciones Automáticas**
   - PostgreSQL ejecuta 001-114.sql al iniciar
   - PRIMER BOOT puede tardar 5-10 minutos
   - NO INTERRUMPIR durante este periodo

4. **Servicios Desactivados**
   - Vault, Flowise, ntfy, Stirling-PDF, n8n, Paperless, H5P
   - Descomentar en docker-compose.yml para activar
   - Requieren servidor con ≥4 cores, 24GB RAM

5. **Datos Sensibles**
   - .env contiene contraseñas — NUNCA commitear
   - Backup de .env en lugar seguro (no en Git)
   - Cambiar contraseñas admin después del bootstrap

---

## 🔄 VERSIONING

| Documento | Versión | Fecha | Cambios |
|---|---|---|---|
| RESUMEN_MIGRACION.txt | 1.0 | 2026-07-10 | Inicial |
| MIGRACION_2026_07_10.md | 1.0 | 2026-07-10 | Inicial |
| INSTRUCCIONES_POST_BOOTSTRAP.md | 1.0 | 2026-07-10 | Inicial |
| DATOS_EJEMPLO.md | 1.0 | 2026-07-10 | Inicial |
| README_MIGRACION.md | 1.0 | 2026-07-10 | Inicial (este) |
| init-certbot.sh | 1.0 | 2026-07-10 | Inicial |
| validate-bootstrap.sh | 1.0 | 2026-07-10 | Inicial |

---

## 📞 SOPORTE

**Problemas comunes**: Ver **INSTRUCCIONES_POST_BOOTSTRAP.md** sección "Problemas y Soluciones"

**Contacto**: imarthe75@gmail.com

**Servidor anterior**: 129.213.35.140 (en transición)
**Servidor nuevo**: 163.192.138.130 (actualmente en bootstrap)

---

**Status**: Bootstrap en progreso
**ETA de conclusión**: 2026-07-10 16:00 UTC
**Próxima actualización**: Cuando PostgreSQL esté levantado

