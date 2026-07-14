#!/bin/bash
# =============================================================================
# Init Certbot — Obtener certificados Let's Encrypt para ades.setag.mx
# Requiere: nginx levantado, puerto 80 accesible, DNS resolviendo a nueva IP
# =============================================================================

set -e

DOMAIN="${CERTBOT_DOMAIN:-ades.setag.mx}"
EMAIL="${CERTBOT_EMAIL:-admin@setag.mx}"
CERT_PATH="/etc/letsencrypt/live/$DOMAIN/cert.pem"

echo "=== Certbot Init ==="
echo "Domain: $DOMAIN"
echo "Email: $EMAIL"
echo "New IP: 163.192.138.130"

# Esperar a que nginx esté listo
echo "Esperando a nginx..."
sleep 5

# Verificar si el certificado ya existe
if [ -f "$CERT_PATH" ]; then
    echo "✓ Certificado existente encontrado en $CERT_PATH"
    echo "Renovando certificado..."
    sudo docker-compose run --rm certbot certonly --webroot \
        -w /var/www/certbot \
        -d $DOMAIN \
        --email $EMAIL \
        --agree-tos \
        --no-eff-email \
        --force-renewal
else
    echo "✗ Certificado no existe. Obteniendo uno nuevo..."

    # Pre-verificar que nginx está escuchando en :80
    if ! curl -I http://localhost:80 >/dev/null 2>&1; then
        echo "⚠ nginx aún no está listo. Esperando..."
        sleep 10
    fi

    # Obtener certificado nuevo
    sudo docker-compose run --rm certbot certonly --webroot \
        -w /var/www/certbot \
        -d $DOMAIN \
        --email $EMAIL \
        --agree-tos \
        --no-eff-email
fi

# Reiniciar nginx con los certificados
echo "Reiniciando nginx con certificados..."
sudo docker-compose restart nginx

echo "✓ Certbot configurado exitosamente"
echo ""
echo "Próximos pasos:"
echo "1. Verificar que el dominio $DOMAIN resuelve a 163.192.138.130"
echo "2. Monitorear: docker-compose logs -f nginx certbot"
echo "3. Certificado se renovará automáticamente cada 12h (certbot service)"
