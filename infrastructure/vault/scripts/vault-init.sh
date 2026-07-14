#!/bin/sh
# wait for Vault to be ready
export VAULT_ADDR="http://vault:8200"

echo "=== Waiting for Vault to start at $VAULT_ADDR ==="
until vault status >/dev/null 2>&1 || [ $? -eq 2 ]; do
  echo "Vault is not ready yet, sleeping 2s..."
  sleep 2
done

echo "=== Checking if Vault is initialized ==="
if ! vault operator init -status >/dev/null 2>&1; then
  echo "Vault is not initialized. Initializing..."
  mkdir -p /vault/init
  # Antes no se validaba el código de salida: si esto fallaba (ej. permiso
  # denegado al escribir el keyring), igual se imprimía "successfully" y el
  # script seguía intentando unseal con un keys.json vacío/con el JSON de
  # error de la API, produciendo prompts interactivos rotos en un contenedor
  # no-tty. Ahora se aborta explícitamente si el init real falla.
  if ! vault operator init -format=json > /vault/init/keys.json; then
    echo "ERROR: vault operator init falló — ver mensaje arriba. Abortando."
    rm -f /vault/init/keys.json
    exit 1
  fi
  chmod 600 /vault/init/keys.json
  echo "Vault initialized successfully."
else
  echo "Vault is already initialized."
fi

# Parse keys.json to unseal Vault
if [ -f /vault/init/keys.json ]; then
  echo "Unsealing Vault..."
  
  KEY1=$(grep -A 5 "unseal_keys_b64" /vault/init/keys.json | sed -n '2p' | tr -d '", ')
  KEY2=$(grep -A 5 "unseal_keys_b64" /vault/init/keys.json | sed -n '3p' | tr -d '", ')
  KEY3=$(grep -A 5 "unseal_keys_b64" /vault/init/keys.json | sed -n '4p' | tr -d '", ')
  ROOT_TOKEN=$(grep "root_token" /vault/init/keys.json | sed -E 's/.*"root_token":\s*"([^"]+)".*/\1/')
  
  # Perform unsealing
  vault operator unseal "$KEY1" >/dev/null
  vault operator unseal "$KEY2" >/dev/null
  vault operator unseal "$KEY3" >/dev/null
  
  # Write token to shared file
  echo "$ROOT_TOKEN" > /vault/init/root_token.txt
  chmod 644 /vault/init/root_token.txt
  
  # Login and configure KV v2
  export VAULT_TOKEN="$ROOT_TOKEN"
  
  # Enable kv-v2
  vault secrets enable -path=secret kv-v2 2>/dev/null || echo "KV engine already enabled."
  
  # Setup default secrets — leídos de variables de entorno (pasadas desde .env vía
  # docker-compose), nunca hardcodeados en este script (antes tenía credenciales
  # reales de un setup anterior escritas en texto plano y commiteadas a git).
  vault kv put secret/ades \
    DATABASE_URL="postgresql+asyncpg://${POSTGRES_USER}:${POSTGRES_PASSWORD}@ades-pgbouncer:5432/ades" \
    DATABASE_URL_SYNC="postgresql://${POSTGRES_USER}:${POSTGRES_PASSWORD}@ades-pgbouncer:5432/ades" \
    SECRET_KEY="${API_SECRET_KEY}" \
    DATABASE_ENCRYPTION_KEY="${PII_ENCRYPTION_KEY}" \
    VALKEY_URL="redis://:${VALKEY_PASSWORD}@ades-valkey:6379/0" \
    CELERY_BROKER_URL="redis://:${VALKEY_PASSWORD}@ades-valkey:6379/0" \
    CELERY_RESULT_URL="redis://:${VALKEY_PASSWORD}@ades-valkey:6379/0" \
    MINIO_ENDPOINT="${MINIO_ENDPOINT}" \
    MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY}" \
    MINIO_SECRET_KEY="${MINIO_SECRET_KEY}" \
    OIDC_CLIENT_SECRET="${OIDC_CLIENT_SECRET}" \
    ADES_INTERNAL_API_KEY="${ADES_INTERNAL_API_KEY}"
    
  echo "=== Vault successfully unsealed and secrets loaded ==="
else
  echo "Error: keys.json not found."
fi
