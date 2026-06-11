# 📘 **README — Instalación y Configuración de Pgpool-II 4.7.1 con PostgreSQL 18 + SCRAM-SHA-256 (sin `pool_passwd`)**

Este documento describe cómo instalar **Pgpool-II 4.7.1** y **PostgreSQL 18.x**, configurando autenticación **SCRAM-SHA-256**, con soporte para producción y sin usar `pool_passwd`.  
Toda la autenticación se delega en PostgreSQL de manera segura.

***

# 🏷️ **Versiones Confirmadas (marzo 2026)**

*   **PostgreSQL:** versión estable más reciente → **18.3** (26‑feb‑2026)    [\[pgpedia.info\]](https://pgpedia.info/postgresql-versions/postgresql-18.html)

*   **Pgpool-II:** versión estable más reciente → **4.7.1** (26‑feb‑2026)    [\[postgresql.org\]](https://www.postgresql.org/about/news/pgpool-ii-471-466-4511-4416-and-4319-are-now-officially-released-3247/)

*   **Repositorio oficial apt soporta PostgreSQL 18**    [\[postgresql.org\]](https://www.postgresql.org/download/linux/ubuntu/)

***

# 🛠️ 1. Instalar PostgreSQL 18 y Pgpool-II 4.7.1

## 1.1. Habilitar repositorio oficial de PostgreSQL (siempre actual)

```bash
sudo apt install -y postgresql-common
sudo /usr/share/postgresql-common/pgdg/apt.postgresql.org.sh
sudo apt update
```

> Este repositorio provee siempre las últimas versiones estables, incluyendo PostgreSQL 18.    [\[postgresql.org\]](https://www.postgresql.org/download/linux/ubuntu/)

***

## 1.2. Instalar PostgreSQL 18

```bash
sudo apt install -y postgresql-18
```

***

## 1.3. Instalar Pgpool-II

```bash
sudo apt install -y pgpool2
```

> La versión disponible actualmente es Pgpool-II 4.7.1 (26‑feb‑2026).    [\[postgresql.org\]](https://www.postgresql.org/about/news/pgpool-ii-471-466-4511-4416-and-4319-are-now-officially-released-3247/)

***

# 📁 2. Rutas de archivos relevantes

| Archivo                  | Ruta                                      | Notas                    |
| ------------------------ | ----------------------------------------- | ------------------------ |
| `postgresql.conf`        | `/etc/postgresql/18/main/postgresql.conf` | PG18                     |
| `pg_hba.conf`            | `/etc/postgresql/18/main/pg_hba.conf`     | SCRAM                    |
| `pgpool.conf`            | `/etc/pgpool2/pgpool.conf`                | Config principal         |
| `pool_hba.conf`          | `/etc/pgpool2/pool_hba.conf`              | Autorización clientes    |
| `pcp.conf`               | `/etc/pgpool2/pcp.conf`                   | Acceso PCP               |
| `backend_data_directory` | `/var/lib/postgresql/18/main`             | Directorio de datos PG18 |

***

# 🧩 3. Configuración de PostgreSQL 18 para SCRAM

## 3.1. Habilitar SCRAM

Editar:

```bash
sudo nano /etc/postgresql/18/main/postgresql.conf
```

Agregar:

```ini
password_encryption = scram-sha-256
```

Reiniciar PostgreSQL:

```bash
sudo systemctl restart postgresql
```

***

## 3.2. Configurar `pg_hba.conf`

Editar:

```bash
sudo nano /etc/postgresql/18/main/pg_hba.conf
```

Añadir:

```ini
host    all    all    127.0.0.1/32    scram-sha-256
host    all    all    ::1/128         scram-sha-256
```

Recargar:

```bash
sudo systemctl reload postgresql
```

***

## 3.3. Crear roles necesarios

```sql
CREATE ROLE admin_user   WITH LOGIN PASSWORD 'TuPassword';
CREATE ROLE pgpool_user  WITH LOGIN PASSWORD 'TuPassword';
ALTER ROLE postgres      WITH PASSWORD 'TuPassword';
```

***

# ⚙️ 4. Configuración de Pgpool-II 4.7.1

## 4.1. Archivo **pgpool.conf** actualizado para PostgreSQL 18

```ini
###########################################################################
## pgpool.conf actualizado para PostgreSQL 18 y Pgpool-II 4.7.1
###########################################################################

# ==========================
# CONEXIÓN DE CLIENTES
# ==========================
listen_addresses = '*'
port = 9999
socket_dir = '/var/run/postgresql'
pcp_port = 9898
pcp_socket_dir = '/var/run/postgresql'

# ==========================
# BACKEND POSTGRES 18
# ==========================
backend_hostname0 = '127.0.0.1'
backend_port0 = 5432
backend_weight0 = 1
backend_data_directory0 = '/var/lib/postgresql/18/main'
backend_flag0 = 'ALWAYS_MASTER'

# ==========================
# POOLING
# ==========================
num_init_children = 150
max_pool = 4
connection_cache = on
child_life_time = 300
connection_life_time = 300
client_idle_limit = 600

# ==========================
# AUTENTICACIÓN
# ==========================
enable_pool_hba = off
backend_user0 = 'pgpool_user'
backend_password0 = 'TuPassword'

# Seguridad reforzada: solo SCRAM desde el cliente
allow_clear_text_frontend_auth = off

# ==========================
# BALANCEO Y REPLICACIÓN
# ==========================
load_balance_mode = off
replication_mode = off
replication_stop_on_mismatch = off

# ==========================
# FAILOVER
# ==========================
failover_on_backend_error = off
auto_failback = off

# ==========================
# HEALTH CHECK
# ==========================
health_check_period = 5
health_check_timeout = 10
health_check_user = 'pgpool_user'
health_check_password = 'G3$t10n.S3gur0-PGP00!'
health_check_database = 'postgres'
health_check_max_retries = 3
health_check_retry_delay = 1

# ==========================
# LOGGING
# ==========================
log_destination = 'stderr'
logging_collector = on
log_directory = '/var/log/pgpool'
log_filename = 'pgpool-%Y-%m-%d.log'
log_rotation_age = 1d
log_rotation_size = 100MB
log_connections = on
log_disconnections = on
log_statement = on
log_per_node_statement = on

log_line_prefix = '%t: pid %p: %u@%d: '

# ==========================
# PCP
# ==========================
pcp_user = 'pgpool_user'
pcp_password = 'TuPassword'

# ==========================
# RENDIMIENTO
# ==========================
child_max_connections = 0
reserve_pool_size = 10
reserve_pool_timeout = 60
memory_cache_enabled = on
connect_timeout = 10000
```

***

## 4.2. Configurar `pool_hba.conf`

```ini
host    all    all    127.0.0.1/32    scram-sha-256
host    all    all    ::1/128         scram-sha-256
```

***

# 📜 5. Permisos correctos

```bash
sudo chown pgpool:pgpool /etc/pgpool2/*.conf
sudo chmod 600 /etc/pgpool2/*.conf
```

***

# 🚀 6. Reiniciar Pgpool-II

```bash
sudo systemctl enable pgpool2
sudo systemctl restart pgpool2
sudo systemctl status pgpool2
```

***

# 🧪 7. Pruebas

```bash
psql -h 127.0.0.1 -p 9999 -U admin_user   -d postgres
psql -h 127.0.0.1 -p 9999 -U postgres     -d postgres
psql -h 127.0.0.1 -p 9999 -U pgpool_user  -d postgres

# Verificación
SHOW pool_nodes;
```

***

# 🛡️ 8. Recomendaciones finales

*   Mantener `pool_passwd` deshabilitado (SCRAM centralizado en PostgreSQL).
*   Usar siempre contraseñas SCRAM-SHA-256 (PostgreSQL 18 depreca MD5).
*   Monitorear logs:
    ```bash
    tail -f /var/log/pgpool/pgpool.log
    journalctl -u postgresql@18-main.service -f
    ```

***

# 📚 9. Referencias verificadas

*   Release PostgreSQL 18.3 (26‑feb‑2026)    [\[pgpedia.info\]](https://pgpedia.info/postgresql-versions/postgresql-18.html)
*   Pgpool-II 4.7.1 (26‑feb‑2026)    [\[postgresql.org\]](https://www.postgresql.org/about/news/pgpool-ii-471-466-4511-4416-and-4319-are-now-officially-released-3247/)
*   Instalación PostgreSQL 18 con repos oficiales    [\[postgresql.org\]](https://www.postgresql.org/download/linux/ubuntu/)
