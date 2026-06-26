# =============================================================================
# ADES Frontend — Angular 21 LTS + PrimeNG 21
#
# Stages:
#   development  — ng serve con hot-reload (default en docker-compose)
#   builder      — producción: build estático
#   production   — producción: nginx sirve el build estático
#
# Uso en docker-compose:
#   FRONTEND_BUILD_TARGET=development  → ng serve en puerto 4200
#   FRONTEND_BUILD_TARGET=production   → nginx estático en puerto 80
# =============================================================================

# ── Stage: development ──────────────────────────────────────────────────────
# Solo instala node_modules; el código fuente llega por volumen en compose.
FROM node:22-alpine AS development
WORKDIR /app
COPY package*.json ./
RUN npm ci --legacy-peer-deps
EXPOSE 4200
CMD ["node_modules/.bin/ng", "serve"]

# ── Stage: builder ────────────────────────────────────────────────────────
FROM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci --legacy-peer-deps
COPY . .
RUN npm run build -- --configuration=production

# ── Stage: production ────────────────────────────────────────────────────
FROM nginx:alpine AS production
COPY --from=builder /app/dist/ades-frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
