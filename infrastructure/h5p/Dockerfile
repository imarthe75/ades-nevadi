FROM node:20-slim

WORKDIR /app

# Dependencias del sistema para H5P (canvas/svg si se necesitan)
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY package.json ./
RUN npm install --production --no-audit --no-fund

COPY server.js ./

# Volumen para contenido y librerías H5P
VOLUME ["/data"]

ENV PORT=8091
ENV H5P_DATA_DIR=/data
ENV NODE_ENV=production

EXPOSE 8091

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8091/health || exit 1

CMD ["node", "server.js"]
