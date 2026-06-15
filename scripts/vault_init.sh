#!/bin/bash
# scripts/vault_init.sh
# Inicializa y siembra secretos de .env en HashiCorp Vault.

set -euo pipefail

VAULT_CONTAINER="ades-vault"
VAULT_TOKEN="${VAULT_TOKEN:-root_token_desarrollo}"
VAULT_ENV_ADDR="http://127.0.0.1:8200"

echo "=== [1/3] Esperando que Vault esté listo ==="
until docker exec -e VAULT_ADDR="$VAULT_ENV_ADDR" "$VAULT_CONTAINER" vault status > /dev/null 2>&1; do
    echo "Esperando a Vault..."
    sleep 2
done

echo "✅ Vault está listo."

echo "=== [2/3] Habilitando motor de secretos KV v2 ==="
# Intentamos habilitar secret/ si no está ya montado
docker exec -e VAULT_ADDR="$VAULT_ENV_ADDR" -e VAULT_TOKEN="$VAULT_TOKEN" "$VAULT_CONTAINER" vault secrets enable -path=secret kv-v2 2>/dev/null || echo "Motor KV v2 ya habilitado en 'secret/'"

echo "=== [3/3] Sembrando secretos desde .env ==="
if [ ! -f .env ]; then
    echo "❌ Archivo .env no encontrado en el directorio raíz."
    exit 1
fi

# Creamos una lista de parámetros KEY=VALUE a partir de las variables que no tengan comentarios ni estén vacías
SECRET_ARGS=()
while IFS= read -r line || [ -n "$line" ]; do
    # Omitir líneas vacías o comentarios
    if [[ -z "$line" || "$line" =~ ^# ]]; then
        continue
    fi
    # Extraer llave y valor
    if [[ "$line" =~ ^([^=]+)=(.*)$ ]]; then
        key="${BASH_REMATCH[1]}"
        val="${BASH_REMATCH[2]}"
        # Limpiar comillas si existen
        val="${val#\"}"
        val="${val%\"}"
        val="${val#\'}"
        val="${val%\'}"
        
        # Filtramos claves vacías
        if [ -n "$val" ]; then
            # Agregar a los argumentos del comando vault
            SECRET_ARGS+=("$key=$val")
        fi
    fi
done < .env

if [ ${#SECRET_ARGS[@]} -eq 0 ]; then
    echo "No se encontraron secretos para sembrar."
else
    echo "Sembrando ${#SECRET_ARGS[@]} secretos en secret/data/ades..."
    docker exec -e VAULT_ADDR="$VAULT_ENV_ADDR" -e VAULT_TOKEN="$VAULT_TOKEN" "$VAULT_CONTAINER" vault kv put secret/ades "${SECRET_ARGS[@]}" > /dev/null
    echo "✅ Sembrado completado con éxito."
fi
