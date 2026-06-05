/**
 * Carbone Server ADES — microservicio de generación de reportes.
 *
 * API:
 *   POST /templates                — sube una plantilla DOCX/XLSX/ODS
 *   GET  /templates                — lista plantillas disponibles
 *   DELETE /templates/:id          — elimina plantilla
 *   POST /render/:templateId       — renderiza con JSON data → PDF
 *   GET  /health                   — liveness probe
 *
 * Plantillas guardadas en /app/templates/{id}.{ext}
 * Metadatos en memoria (reiniciables; el volumen persiste los archivos)
 */

const express = require('express');
const multer  = require('multer');
const carbone = require('carbone');
const path    = require('path');
const fs      = require('fs');
const { v4: uuidv4 } = require('uuid');

const app  = express();
const PORT = process.env.PORT || 3000;
const TEMPLATES_DIR = process.env.TEMPLATES_DIR || '/app/templates';

// Asegurar que el directorio de plantillas existe
fs.mkdirSync(TEMPLATES_DIR, { recursive: true });

app.use(express.json({ limit: '10mb' }));

// ── Multer — upload de plantillas ─────────────────────────────────────────────
const storage = multer.diskStorage({
  destination: TEMPLATES_DIR,
  filename: (req, file, cb) => {
    const id  = uuidv4();
    const ext = path.extname(file.originalname);
    req.templateId = id;
    req.templateExt = ext;
    cb(null, `${id}${ext}`);
  },
});
const upload = multer({
  storage,
  limits: { fileSize: 20 * 1024 * 1024 },  // 20 MB
  fileFilter: (req, file, cb) => {
    const allowed = ['.docx', '.xlsx', '.odt', '.ods', '.pptx'];
    const ext = path.extname(file.originalname).toLowerCase();
    if (allowed.includes(ext)) {
      cb(null, true);
    } else {
      cb(new Error(`Tipo de archivo no soportado: ${ext}. Permitidos: ${allowed.join(', ')}`));
    }
  },
});

// ── Metadatos en memoria (reconstruidos desde disco al arrancar) ───────────────
const templates = new Map();

function loadTemplatesFromDisk() {
  if (!fs.existsSync(TEMPLATES_DIR)) return;
  const files = fs.readdirSync(TEMPLATES_DIR);
  files.forEach(f => {
    const ext = path.extname(f);
    const id  = path.basename(f, ext);
    if (!templates.has(id)) {
      templates.set(id, {
        id,
        nombre: f,
        tipo_documento: 'GENERICO',
        extension: ext,
        fccreacion: new Date().toISOString(),
      });
    }
  });
}
loadTemplatesFromDisk();

// ── GET /health ───────────────────────────────────────────────────────────────
app.get('/health', (req, res) => {
  res.json({ status: 'ok', templates: templates.size });
});

// ── POST /templates ───────────────────────────────────────────────────────────
app.post('/templates', upload.single('template'), (req, res) => {
  if (!req.file) return res.status(400).json({ error: 'No se recibió archivo' });

  const id  = req.templateId;
  const ext = req.templateExt;
  const meta = {
    id,
    nombre:          req.body.nombre          || req.file.originalname,
    tipo_documento:  req.body.tipo_documento  || 'GENERICO',
    descripcion:     req.body.descripcion     || '',
    extension:       ext,
    tamano_bytes:    req.file.size,
    fccreacion:      new Date().toISOString(),
  };
  templates.set(id, meta);
  res.status(201).json(meta);
});

// ── GET /templates ────────────────────────────────────────────────────────────
app.get('/templates', (req, res) => {
  res.json(Array.from(templates.values()));
});

// ── DELETE /templates/:id ─────────────────────────────────────────────────────
app.delete('/templates/:id', (req, res) => {
  const { id } = req.params;
  const meta = templates.get(id);
  if (!meta) return res.status(404).json({ error: 'Plantilla no encontrada' });

  const filePath = path.join(TEMPLATES_DIR, `${id}${meta.extension}`);
  if (fs.existsSync(filePath)) fs.unlinkSync(filePath);
  templates.delete(id);
  res.json({ ok: true });
});

// ── POST /render/:templateId ──────────────────────────────────────────────────
app.post('/render/:templateId', (req, res) => {
  const { templateId } = req.params;
  const meta = templates.get(templateId);
  if (!meta) return res.status(404).json({ error: 'Plantilla no encontrada' });

  const filePath = path.join(TEMPLATES_DIR, `${templateId}${meta.extension}`);
  if (!fs.existsSync(filePath)) {
    templates.delete(templateId);
    return res.status(404).json({ error: 'Archivo de plantilla no encontrado en disco' });
  }

  const data    = req.body.data    || req.body || {};
  const options = req.body.options || {};
  const outputFormat = options.outputFormat || 'pdf';

  carbone.render(filePath, data, { convertTo: outputFormat, lang: 'es-MX' }, (err, result) => {
    if (err) {
      console.error('[carbone render error]', err.message);
      return res.status(500).json({ error: err.message });
    }
    const mimeTypes = {
      pdf:  'application/pdf',
      docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    };
    const mime = mimeTypes[outputFormat] || 'application/octet-stream';
    const filename = `${meta.tipo_documento}_${Date.now()}.${outputFormat}`;
    res.setHeader('Content-Type', mime);
    res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
    res.send(result);
  });
});

// ── Error handler ─────────────────────────────────────────────────────────────
app.use((err, req, res, next) => {
  console.error('[error]', err.message);
  res.status(500).json({ error: err.message });
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`[carbone-server] Listo en puerto ${PORT} | Plantillas: ${TEMPLATES_DIR}`);
});
