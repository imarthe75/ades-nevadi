# Stage 1: build
FROM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci --legacy-peer-deps
COPY . .
RUN npm run build -- --configuration=production 2>&1 | tail -20

# Stage 2: serve
FROM nginx:alpine
COPY --from=builder /app/dist/ades-frontend/browser /usr/share/nginx/html
COPY /opt/ades/frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
