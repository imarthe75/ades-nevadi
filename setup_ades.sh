#!/bin/bash

set -e

echo "Creando estructura de directorios..."

# Lista de directorios
DIRS=(
  /opt/ades/infrastructure/nginx/conf.d
  /opt/ades/integrations/superset
  /opt/ades/integrations/asc_horarios
  /opt/ades/integrations/cube/schema
  /opt/ades/backend
  /opt/ades/frontend
)

# Crear directorios si no existen
for dir in "${DIRS[@]}"; do
  if [ ! -d "$dir" ]; then
    echo "Creando $dir"
    mkdir -p "$dir"
  else
    echo "Ya existe $dir"
  fi
done

# Crear archivo nginx.conf
NGINX_CONF="/opt/ades/infrastructure/nginx/nginx.conf"

echo "Creando configuración de nginx en $NGINX_CONF..."

cat <<EOF > "$NGINX_CONF"
events {}

http {
    server {
        listen 80;

        # Frontend
        location / {
            proxy_pass http://ades-frontend:4200;
            proxy_set_header Host \$host;
            proxy_set_header X-Real-IP \$remote_addr;
        }

        # API
        location /api/ {
            proxy_pass http://ades-api:8000/;
            proxy_set_header Host \$host;
            proxy_set_header X-Real-IP \$remote_addr;
        }

        # Authentik
        location /auth/ {
            proxy_pass http://authentik-server:9000/;
            proxy_set_header Host \$host;
            proxy_set_header X-Real-IP \$remote_addr;
        }

        # Superset (BI)
        location /bi/ {
            proxy_pass http://superset:8088/;
            proxy_set_header Host \$host;
            proxy_set_header X-Real-IP \$remote_addr;
        }
    }
}
EOF

echo "✅ Configuración completada correctamente."
