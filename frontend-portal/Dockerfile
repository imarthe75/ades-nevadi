# =============================================================================
# ADES Portal Público — Angular 21 LTS + PrimeNG 21
# portalnvd.setag.mx
#
# Stages:
#   development — ng serve con hot-reload (puerto 4201)
#   production  — nginx sirve el build estático (puerto 80)
# =============================================================================

FROM node:22-alpine AS development
WORKDIR /app
COPY package*.json ./
RUN npm install --legacy-peer-deps
EXPOSE 4201
CMD ["node_modules/.bin/ng", "serve", "--host", "0.0.0.0", "--port", "4201", "--disable-host-check"]

FROM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm install --legacy-peer-deps
COPY . .
RUN npm run build -- --configuration=production

FROM nginx:alpine AS production
COPY --from=builder /app/dist/ades-portal/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
