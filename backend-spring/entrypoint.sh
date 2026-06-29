#!/bin/sh
# Reads the Vault root token from the shared init volume before starting Spring Boot.
# The vault-init container writes the token to /vault/init/root_token.txt on first boot.
TOKEN_FILE="/vault/init/root_token.txt"
if [ -z "$VAULT_TOKEN" ] && [ -f "$TOKEN_FILE" ]; then
    export VAULT_TOKEN=$(cat "$TOKEN_FILE")
fi
exec java -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv4Addresses=true -jar /app/app.jar "$@"
