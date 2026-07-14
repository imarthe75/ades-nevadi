storage "file" {
  # /vault/file (no /vault/data): el docker-entrypoint.sh de la imagen oficial
  # solo hace chown automático de /vault/config, /vault/logs y /vault/file —
  # una ruta custom como /vault/data nunca se ajusta al usuario "vault" y el
  # server (que corre como ese usuario, no root) no puede escribir el keyring.
  path = "/vault/file"
}

listener "tcp" {
  address     = "0.0.0.0:8200"
  tls_disable = "true"
}

disable_mlock = true
ui = true
