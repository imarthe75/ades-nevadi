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
  vault operator init -format=json > /vault/init/keys.json
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
  
  # Setup default secrets
  vault kv put secret/ades \
    DATABASE_URL="postgresql+asyncpg://ades_admin:El7xr7qt0Nj-N8Rjs9QO1ul4gD-fYpjo@ades-postgres:5432/ades" \
    DATABASE_URL_SYNC="postgresql://ades_admin:El7xr7qt0Nj-N8Rjs9QO1ul4gD-fYpjo@ades-postgres:5432/ades" \
    SECRET_KEY="v0fmzdRZTy22eQ8g0exjYFYLyLsfmthq" \
    DATABASE_ENCRYPTION_KEY="ades_encryption_key_2026" \
    VALKEY_URL="redis://:4cbJ20czDNxBCqJYK3kZiCQj9sK_ZqO1@ades-valkey:6379/0" \
    CELERY_BROKER_URL="redis://:4cbJ20czDNxBCqJYK3kZiCQj9sK_ZqO1@ades-valkey:6379/0" \
    CELERY_RESULT_URL="redis://:4cbJ20czDNxBCqJYK3kZiCQj9sK_ZqO1@ades-valkey:6379/0" \
    MINIO_ENDPOINT="ades-seaweedfs:9000" \
    MINIO_ACCESS_KEY="ades_minio" \
    MINIO_SECRET_KEY="sTA9yP9Zh6LCRc1UFxLkd_nPh6AsJU_b"
    
  echo "=== Vault successfully unsealed and secrets loaded ==="
else
  echo "Error: keys.json not found."
fi
