FROM node:20-alpine

# Dependencias de sistema para LibreOffice (conversión DOCX→PDF)
RUN apk add --no-cache \
    libreoffice \
    ttf-dejavu \
    fontconfig \
    && fc-cache -fv

WORKDIR /app

COPY package.json .
RUN npm install --production

COPY server.js .

# Directorio de plantillas persistente
RUN mkdir -p /app/templates

EXPOSE 3000

CMD ["node", "server.js"]
