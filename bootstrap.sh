#!/bin/bash
# =============================================================================
# ADES — Bootstrap / Disaster Recovery
# =============================================================================
# Levanta el proyecto completo desde cero (servidor nuevo o migrado) hasta
# quedar listo para trabajar: Docker sano, firewall correcto, stack completo,
# migraciones + seeds, certificados TLS reales, OIDC configurado, usuarios
# de prueba por rol.
#
# Idempotente — se puede volver a ejecutar en cualquier momento sin duplicar
# ni romper nada. Revisa /opt/ades/CLAUDE.md antes de tocar este archivo.
#
# Uso:
#   sudo bash bootstrap.sh              # todo
#   sudo bash bootstrap.sh --no-certs   # sin Let's Encrypt real ni firewall 80/443
#   sudo bash bootstrap.sh --no-seeds   # sin datos de ejemplo/simulación
# =============================================================================

set -uo pipefail
cd /opt/ades

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✓${NC} $1"; }
warn() { echo -e "${YELLOW}⚠${NC} $1"; }
err()  { echo -e "${RED}✗${NC} $1"; }
step() { echo -e "\n${YELLOW}━━━ $1 ━━━${NC}"; }

DO_CERTS=1
DO_SEEDS=1
for arg in "$@"; do
  case "$arg" in
    --no-certs) DO_CERTS=0 ;;
    --no-seeds) DO_SEEDS=0 ;;
  esac
done

DOMAINS=(ades.setag.mx auth.ades.setag.mx bi.ades.setag.mx minio.ades.setag.mx notify.ades.setag.mx monitor.ades.setag.mx portalnvd.setag.mx)

# =============================================================================
# FASE 0 — Regla de oro: el puerto 22 NUNCA se cierra
# =============================================================================
assert_ssh_open() {
    if ! sudo iptables -C INPUT -p tcp --dport 22 -j ACCEPT 2>/dev/null \
       && ! sudo iptables -C INPUT -p tcp -m state --state NEW --dport 22 -j ACCEPT 2>/dev/null; then
        err "PUERTO 22 NO TIENE REGLA ACCEPT — abortando cualquier cambio de firewall."
        err "Ver CLAUDE.md → 'REGLA CRÍTICA DE ACCESO SSH'. Revisa manualmente antes de continuar."
        return 1
    fi
    ok "Puerto 22 (SSH) confirmado ACCEPT"
    return 0
}

# =============================================================================
# FASE 1 — Docker daemon sano
# =============================================================================
step "FASE 1 — Docker daemon"
if ! sudo systemctl is-active --quiet docker; then
    warn "Docker no está activo, intentando recuperar (daemon-reload + reset-failed)..."
    sudo systemctl daemon-reload
    sudo systemctl reset-failed docker.socket docker.service 2>/dev/null || true
    sudo systemctl start docker.socket
    sudo systemctl start docker.service
    sleep 3
fi
if sudo systemctl is-active --quiet docker; then
    ok "Docker daemon activo"
else
    err "Docker daemon no pudo iniciarse — revisar: sudo journalctl -u docker -n 80"
    exit 1
fi

# Membresía en grupo docker (evita necesitar sudo en el día a día)
for u in ubuntu ades; do
    if id "$u" &>/dev/null && ! id -nG "$u" | grep -qw docker; then
        sudo usermod -aG docker "$u"
        ok "Usuario '$u' añadido al grupo docker (efectivo en el próximo login)"
    fi
done

# =============================================================================
# FASE 2 — Firewall (puerto 22 SIEMPRE primero, nunca se toca)
# =============================================================================
step "FASE 2 — Firewall"
assert_ssh_open || exit 1

if [ "$DO_CERTS" -eq 1 ]; then
    for port in 80 443; do
        if ! sudo iptables -C INPUT -p tcp --dport "$port" -j ACCEPT 2>/dev/null; then
            sudo iptables -I INPUT -p tcp --dport "$port" -j ACCEPT
            ok "Regla ACCEPT añadida para puerto $port"
        else
            ok "Puerto $port ya tiene regla ACCEPT"
        fi
    done
    assert_ssh_open || exit 1
    if command -v netfilter-persistent &>/dev/null; then
        sudo netfilter-persistent save && ok "Reglas de iptables persistidas"
    else
        warn "netfilter-persistent no disponible — las reglas no sobrevivirán un reinicio"
    fi
else
    warn "--no-certs: firewall 80/443 sin modificar"
fi

# =============================================================================
# FASE 3 — Usuario admin de respaldo (passwordless por certificado + sudo local)
# =============================================================================
step "FASE 3 — Usuario de respaldo 'ades'"
if id ades &>/dev/null; then
    ok "Usuario 'ades' ya existe"
else
    sudo useradd -m -s /bin/bash -G sudo,docker ades
    echo 'ades:Ad3s_N3v4d1!' | sudo chpasswd
    ok "Usuario 'ades' creado (grupo sudo, contraseña solo válida para sudo local)"
fi
if [ -f /home/ubuntu/.ssh/authorized_keys ]; then
    sudo mkdir -p /home/ades/.ssh
    sudo cp /home/ubuntu/.ssh/authorized_keys /home/ades/.ssh/authorized_keys
    sudo chmod 700 /home/ades/.ssh
    sudo chmod 600 /home/ades/.ssh/authorized_keys
    sudo chown -R ades:ades /home/ades/.ssh
    ok "authorized_keys sincronizado (login passwordless por certificado)"
fi

# =============================================================================
# FASE 4 — Stack completo
# =============================================================================
step "FASE 4 — docker compose up -d (build + start de todo el stack)"
sudo docker compose build 2>&1 | tail -20
sudo docker compose up -d 2>&1 | tail -40
ok "docker compose up -d ejecutado"

step "FASE 4b — Esperar PostgreSQL healthy"
for i in $(seq 1 30); do
    if sudo docker compose ps postgres --format "{{.Status}}" | grep -q healthy; then
        ok "PostgreSQL healthy"
        break
    fi
    sleep 3
done

# =============================================================================
# FASE 5 — Bases de datos adicionales (authentik, superset, n8n, paperless)
# =============================================================================
step "FASE 5 — Bases de datos adicionales"
sudo docker compose exec -T postgres bash < db/scripts/init_multi_db.sh 2>&1 | tail -20
ok "Bases de datos adicionales verificadas/creadas"

# =============================================================================
# FASE 6 — Migraciones (todas, en orden — idempotentes con IF NOT EXISTS)
# =============================================================================
step "FASE 6 — Migraciones"
MIG_FAIL=0
for f in $(ls db/migrations/*.sql | sort -V); do
    if ! sudo docker compose exec -T postgres psql -U ades_admin -d ades -v ON_ERROR_STOP=1 -f "/docker-entrypoint-initdb.d/migrations/$(basename "$f")" >/tmp/migout.log 2>&1; then
        # fallback: el archivo puede no estar bind-mounted si el volumen ya existía antes de este commit
        if ! sudo docker compose exec -T postgres psql -U ades_admin -d ades -v ON_ERROR_STOP=1 < "$f" >/tmp/migout.log 2>&1; then
            err "Migración falló: $(basename "$f")"
            tail -5 /tmp/migout.log
            MIG_FAIL=$((MIG_FAIL + 1))
        fi
    fi
done
if [ "$MIG_FAIL" -eq 0 ]; then
    ok "Todas las migraciones aplicadas sin errores"
else
    warn "$MIG_FAIL migración(es) con errores — revisar arriba (puede ser esperado si ya estaban aplicadas de otra forma)"
fi

# =============================================================================
# FASE 7 — nginx: certificados
# =============================================================================
step "FASE 7 — nginx / certificados TLS"
sudo mkdir -p /etc/letsencrypt/live
for d in "${DOMAINS[@]}"; do
    if [ ! -f "/etc/letsencrypt/live/$d/fullchain.pem" ]; then
        sudo mkdir -p "/etc/letsencrypt/live/$d"
        sudo openssl req -x509 -nodes -newkey rsa:2048 -days 3 \
            -keyout "/etc/letsencrypt/live/$d/privkey.pem" \
            -out "/etc/letsencrypt/live/$d/fullchain.pem" \
            -subj "/CN=$d" >/dev/null 2>&1
        ok "Certificado placeholder autofirmado generado para $d"
    fi
done
sudo docker compose up -d --no-deps nginx 2>&1 | tail -5

if [ "$DO_CERTS" -eq 1 ]; then
    step "FASE 7b — Certificados reales Let's Encrypt"
    MY_IP=$(curl -s -4 --max-time 5 ifconfig.me || echo "")
    for c in $(sudo docker ps -a --filter "name=certbot-run" --format "{{.Names}}"); do sudo docker rm -f "$c" >/dev/null 2>&1; done
    for d in "${DOMAINS[@]}"; do
        DOMAIN_IP=$(getent hosts "$d" | awk '{print $1}' | head -1)
        if [ -z "$MY_IP" ] || [ "$DOMAIN_IP" != "$MY_IP" ]; then
            warn "$d no resuelve a la IP pública de este servidor ($MY_IP) — se mantiene certificado autofirmado"
            continue
        fi
        ISSUER=$(echo | openssl x509 -noout -issuer -in "/etc/letsencrypt/live/$d/fullchain.pem" 2>/dev/null || echo "")
        if echo "$ISSUER" | grep -q "Let's Encrypt"; then
            ok "$d ya tiene certificado real de Let's Encrypt"
            continue
        fi
        # limpiar estado huérfano (cert autofirmado sin lineage de certbot) para evitar sufijo -0001
        if [ ! -f "/etc/letsencrypt/renewal/$d.conf" ]; then
            sudo rm -rf "/etc/letsencrypt/live/$d" "/etc/letsencrypt/archive/$d"
        fi
        if timeout 60 sudo docker compose run --rm -T --entrypoint certbot certbot certonly \
            --webroot -w /var/www/certbot -d "$d" \
            --email admin@setag.mx --agree-tos --no-eff-email --non-interactive >/tmp/certout.log 2>&1; then
            ok "Certificado real emitido para $d"
        else
            warn "No se pudo emitir certificado real para $d (ver /tmp/certout.log) — se mantiene autofirmado"
        fi
    done
    for c in $(sudo docker ps -a --filter "name=certbot-run" --format "{{.Names}}"); do sudo docker rm -f "$c" >/dev/null 2>&1; done
    sudo docker compose restart nginx 2>&1 | tail -5
    ok "nginx recargado con certificados actualizados"

    step "FASE 7c — Auto-reload de nginx tras renovación (certbot ya renueva solo cada 12h)"
    CRON_LINE="17 3 * * * root docker compose -f /opt/ades/docker-compose.yml exec nginx nginx -s reload >/var/log/ades-nginx-reload.log 2>&1"
    if [ ! -f /etc/cron.d/ades-nginx-reload ] || ! grep -qF "nginx -s reload" /etc/cron.d/ades-nginx-reload 2>/dev/null; then
        echo "$CRON_LINE" | sudo tee /etc/cron.d/ades-nginx-reload >/dev/null
        sudo chmod 644 /etc/cron.d/ades-nginx-reload
        ok "Cron diario instalado: recarga nginx a las 03:17 (recoge certificados renovados automáticamente)"
    else
        ok "Cron de auto-reload de nginx ya instalado"
    fi
else
    warn "--no-certs: se mantienen certificados autofirmados"
fi

# =============================================================================
# FASE 8 — Frontend en modo producción (nginx lo espera así)
# =============================================================================
step "FASE 8 — Frontend (build de producción)"
sudo docker compose build ades-frontend 2>&1 | tail -10
sudo docker compose up -d --no-deps ades-frontend 2>&1 | tail -5
ok "Frontend reconstruido en modo producción"

# =============================================================================
# FASE 9 — Datos de ejemplo / simulación
# =============================================================================
if [ "$DO_SEEDS" -eq 1 ]; then
    step "FASE 9 — Seeds (backbone + simulación integral)"
    for f in 001_datos_base.sql 002_grupos_profesores.sql 003_alumnos_padres.sql 004_plan_estudios.sql 005_disponibilidad_aulas.sql; do
        if sudo docker compose exec -T postgres psql -U ades_admin -d ades -v ON_ERROR_STOP=1 < "db/seeds/$f" >/tmp/seedout.log 2>&1; then
            ok "Seed aplicado: $f"
        else
            warn "Seed con errores: $f (ver /tmp/seedout.log)"
            tail -5 /tmp/seedout.log
        fi
    done
    for f in 006_simulacion_integral.py 007_modulos_complementarios.py 008_flujo_completo.py; do
        if sudo python3 "db/seeds/$f" >/tmp/seedout.log 2>&1; then
            ok "Seed aplicado: $f"
        else
            warn "Seed con errores: $f (ver /tmp/seedout.log)"
            tail -10 /tmp/seedout.log
        fi
    done
else
    warn "--no-seeds: tablas quedan sin datos de ejemplo"
fi

# =============================================================================
# FASE 10 — Authentik OIDC (apps + admin + usuarios de prueba por rol)
# =============================================================================
step "FASE 10 — Authentik OIDC"
AUTHENTIK_BOOTSTRAP_TOKEN=$(grep "^AUTHENTIK_BOOTSTRAP_TOKEN=" .env | cut -d= -f2)
if [ -n "$AUTHENTIK_BOOTSTRAP_TOKEN" ]; then
    export AUTHENTIK_BOOTSTRAP_TOKEN
    export AUTHENTIK_API_BASE="http://localhost:9010"
    for i in $(seq 1 20); do
        curl -sf -o /dev/null "http://localhost:9010/api/v3/root/config/" && break
        sleep 3
    done
    python3 infrastructure/authentik/setup.py 2>&1 | tail -40
    if [ "$DO_SEEDS" -eq 1 ]; then
        python3 infrastructure/authentik/provision_users.py 2>&1 | tail -60
    else
        warn "--no-seeds: se omite provisión de usuarios (requiere catálogo de roles del seed 001)"
    fi
else
    err "AUTHENTIK_BOOTSTRAP_TOKEN no está en .env — omitiendo configuración OIDC"
fi

step "FASE 10b — Reiniciar servicios con secretos frescos"
sudo docker compose up -d --no-deps ades-api ades-bff ades-frontend superset 2>&1 | tail -20
ok "Servicios reiniciados"

# =============================================================================
# FASE 11 — Validación final
# =============================================================================
step "FASE 11 — Validación final"
assert_ssh_open || exit 1
bash validate-bootstrap.sh

echo -e "\n${GREEN}════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN} BOOTSTRAP COMPLETO${NC}"
echo -e "${GREEN}════════════════════════════════════════════════════════════${NC}"
echo "Panel Authentik : https://auth.ades.setag.mx/if/admin/  (akadmin / ver AUTHENTIK_BOOTSTRAP_PASSWORD en .env)"
echo "App             : https://ades.setag.mx"
if [ "$DO_SEEDS" -eq 1 ]; then
    echo "Admin app       : admin@institutonevadi.edu.mx / Ad3s_N3v4d1!"
    echo "Usuarios prueba : test.<rol>@institutonevadi.edu.mx / Ad3s_N3v4d1!  (uno por cada rol de ades_roles)"
fi
echo "SSH respaldo    : usuario 'ades' (sudo local: Ad3s_N3v4d1! — SSH solo por certificado)"
