# Activación de LAChain — Guía de Implementación

**Fecha de preparación:** 2026-07-08  
**Estado:** Código listo, infraestructura MOCK activa  
**Esfuerzo estimado para activar:** 1-2 horas

---

## ✅ Qué ya está hecho (2026-07-08)

### Backend FastAPI
- ✅ Variables renombradas: `POLYGON_*` → `LACCHAIN_*` en `config.py`
- ✅ Servicio blockchain actualizado: `app/services/blockchain.py`
- ✅ Tarea Celery actualizada: `app/worker/tasks/blockchain.py`
- ✅ Modo MOCK funcional (ningún cambio requerido)
- ✅ Web3.py compatible con cualquier blockchain EVM

### Frontend Angular
- ✅ Componente certificados: `blockchainLink()` soporta LAChain
- ✅ Componente verificar: texto y método actualizados
- ✅ Backward compatibility: certificados históricos Polygon_AMOY siguen siendo válidos

### Documentación
- ✅ README.md actualizado
- ✅ .env.example con variables LAChain
- ✅ plan_pruebas_integral.md actualizado
- ✅ PROXIMO_PASOS_ANALISIS_2026_06_16.md actualizado

### Testing
- ✅ Validación Python: sintaxis correcta
- ✅ Validación TypeScript: 0 errores
- ✅ Modo MOCK funciona sin dependencias externas

---

## 🚀 Para Activar LAChain en Producción (cuando esté disponible)

### Paso 1: Obtener credenciales LAChain

Contactar con el proveedor de LAChain y obtener:

```bash
# 1. RPC Endpoint
LACCHAIN_RPC_URL="https://rpc.lacchain.net"  # ← Reemplazar con URL real

# 2. Wallet privada (con fondos en LAChain)
LACCHAIN_PRIVATE_KEY="0x..."  # ← Hexadecimal sin prefijo 0x en el valor
# Generar con: openssl rand -hex 32

# 3. Dirección del contrato inteligente
LACCHAIN_CONTRACT_ADDRESS="0x..."  # ← Dirección del contrato desplegado

# 4. Chain ID de LAChain
LACCHAIN_CHAIN_ID="2020"  # ← Ajustar según red LAChain
```

### Paso 2: Actualizar `.env`

```bash
# En producción, cambiar de:
LACCHAIN_RPC_URL=MOCK

# A:
LACCHAIN_RPC_URL=https://rpc.lacchain.net
LACCHAIN_PRIVATE_KEY=0x...
LACCHAIN_CONTRACT_ADDRESS=0x...
LACCHAIN_CHAIN_ID=2020
```

### Paso 3: Actualizar Explorer URL (opcional)

Si LAChain tiene un explorer público, actualizar `blockchainLink()` en ambos componentes Angular:

**Archivo: `frontend/src/app/features/certificados/certificados.component.ts`**
```typescript
if (network === 'LACCHAIN') {
  return `https://explorer.lacchain.net/tx/${tx}`;  // ← Reemplazar URL
}
```

**Archivo: `frontend/src/app/features/verificar/verificar.component.ts`**
```typescript
if (network === 'LACCHAIN') {
  return `https://explorer.lacchain.net/tx/${tx}`;  // ← Reemplazar URL
}
```

### Paso 4: Reiniciar servicios

```bash
# 1. Actualizar .env
nano .env  # Cambiar LACCHAIN_RPC_URL, etc.

# 2. Reconstruir imagen FastAPI (si .env cambió)
docker compose up -d --build ades-api

# 3. Reconstruir frontend (si explorer URL cambió)
cd frontend && npm run build && cd ..
docker compose up -d --build ades-nginx

# 4. Verificar que los contenedores están healthy
docker compose ps
```

### Paso 5: Testing

```bash
# 1. Verificar configuración
docker compose logs ades-api | grep -i lacchain

# 2. Emitir certificado (vía UI o API)
# Debe generar transacción en LAChain en lugar de simular

# 3. Verificar en explorer LAChain
# Ir a: https://explorer.lacchain.net/tx/{tx_hash}
# Debe mostrar la transacción anclada
```

---

## 📋 Checklist de Activación

- [ ] Obtener credenciales LAChain (RPC, private key, contrato, chain ID)
- [ ] Actualizar `.env` con valores reales
- [ ] Actualizar explorer URL en ambos componentes (si es diferente)
- [ ] Reconstruir y reiniciar contenedores
- [ ] Verificar logs: `docker compose logs ades-api | grep lacchain`
- [ ] Emitir certificado de prueba desde UI
- [ ] Verificar transacción en explorer LAChain
- [ ] Confirmar que certificados históricos (Polygon) siguen siendo válidos

---

## 🔍 Archivos Modificados

**Backend:**
- `backend/app/core/config.py` — Variables LACCHAIN_*
- `backend/app/services/blockchain.py` — Lógica anclaje
- `backend/app/worker/tasks/blockchain.py` — Tarea Celery

**Frontend:**
- `frontend/src/app/features/certificados/certificados.component.ts`
- `frontend/src/app/features/verificar/verificar.component.ts`

**Configuración:**
- `.env.example` — Variables LAChain
- `.env` — Actualizar solo cuando LAChain esté disponible

**Documentación:**
- `README.md`
- `docs/plan_pruebas_integral.md`
- `docs/sprints/PROXIMO_PASOS_ANALISIS_2026_06_16.md`

---

## 🔗 Referencias

- LAChain Docs: https://lacchain.net/
- Web3.py Docs: https://web3py.readthedocs.io/
- EVM Compatibility: https://ethereum.org/en/developers/docs/evm/

---

## 💬 Notas

- **Modo MOCK sigue activo por defecto** en desarrollo
- **Certificados históricos son inmutables** (Polygon_AMOY seguirá siendo válido)
- **Costo:** La integración LAChain reemplaza Polygon PoS (no hay cambios de costo, es elección de blockchain)
- **Rollback:** Para volver a MOCK, solo cambiar `LACCHAIN_RPC_URL="MOCK"` en `.env`

