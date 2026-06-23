'use strict';

const express = require('express');
const fileUpload = require('express-fileupload');
const fs = require('fs-extra');
const path = require('path');
const { v4: uuidv4 } = require('uuid');
const H5P = require('@lumieducation/h5p-server');

const PORT = process.env.PORT || 8091;
const DATA_DIR = process.env.H5P_DATA_DIR || '/data';
const ADES_API_URL = process.env.ADES_API_URL || 'http://ades-api:8000';

// ---------------------------------------------------------------------------
// Directorios de trabajo
// ---------------------------------------------------------------------------
const dirs = {
    libraries:     path.join(DATA_DIR, 'libraries'),
    content:       path.join(DATA_DIR, 'content'),
    temporaryFiles: path.join(DATA_DIR, 'tmp'),
    core:          path.join(DATA_DIR, 'h5p-core'),
    editor:        path.join(DATA_DIR, 'h5p-editor'),
};
Object.values(dirs).forEach(d => fs.ensureDirSync(d));

// ---------------------------------------------------------------------------
// Adaptadores de almacenamiento (filesystem local montado en volumen)
// ---------------------------------------------------------------------------
let h5pEditor;
let h5pPlayer;

async function initH5P() {
    // JsonStorage requiere que el archivo exista antes de leerlo
    const configPath = path.join(DATA_DIR, 'h5p-config.json');
    if (!fs.existsSync(configPath)) {
        fs.writeJsonSync(configPath, {});
    }

    const config = await new H5P.H5PConfig(
        new H5P.fsImplementations.JsonStorage(configPath)
    ).load();

    // nginx proxea /h5p/ → este servicio.
    // baseUrl='/h5p' se prepone a los paths relativos:
    //   /h5p + /core      → /h5p/core      (coincide con app.use('/h5p/core', ...))
    //   /h5p + /libraries → /h5p/libraries
    //   /h5p + /content   → /h5p/content
    config.baseUrl = '/h5p';
    config.librariesUrl = '/libraries';
    config.coreUrl = '/core';
    config.editorLibraryUrl = '/editor';
    config.contentFilesUrlPath = '/content';

    const libraryStorage   = new H5P.fsImplementations.FileLibraryStorage(dirs.libraries);
    const contentStorage   = new H5P.fsImplementations.FileContentStorage(dirs.content);
    const temporaryStorage = new H5P.fsImplementations.DirectoryTemporaryFileStorage(dirs.temporaryFiles);
    const keyValueStorage  = new H5P.fsImplementations.InMemoryStorage();

    // Translation function es opcional en v9.x — se omite para simplificar
    h5pEditor = new H5P.H5PEditor(
        keyValueStorage,
        config,
        libraryStorage,
        contentStorage,
        temporaryStorage,
    );

    h5pPlayer = new H5P.H5PPlayer(
        libraryStorage,
        contentStorage,
        config,
    );

    console.log('[H5P] Editor e Player inicializados');
}

// ---------------------------------------------------------------------------
// Express app
// ---------------------------------------------------------------------------
const app = express();
app.use(express.json({ limit: '50mb' }));
app.use(fileUpload({ limits: { fileSize: 100 * 1024 * 1024 }, useTempFiles: true, tempFileDir: dirs.temporaryFiles }));

// CORS para Angular
app.use((req, res, next) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET,POST,PUT,DELETE,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type,Authorization');
    if (req.method === 'OPTIONS') return res.sendStatus(204);
    next();
});

// Servir archivos core/editor de H5P como estáticos
app.use('/h5p/core', express.static(dirs.core));
app.use('/h5p/editor', express.static(dirs.editor));
app.use('/h5p/libraries', express.static(dirs.libraries));

// ---------------------------------------------------------------------------
// Health
// ---------------------------------------------------------------------------
app.get('/health', (req, res) => res.json({ status: 'ok', service: 'ades-h5p' }));
app.get('/h5p/health', (req, res) => res.json({ status: 'ok', service: 'ades-h5p' }));

// ---------------------------------------------------------------------------
// Listar contenidos
// ---------------------------------------------------------------------------
app.get('/h5p/api/contenidos', async (req, res) => {
    try {
        const ids = await h5pEditor.contentManager.listContent();
        const list = await Promise.all(ids.map(async id => {
            try {
                const meta = await h5pEditor.contentManager.getContentMetadata(id);
                const params = await h5pEditor.contentManager.getContentParameters(id);
                return { id, titulo: meta.title, library: meta.mainLibrary, metadatos: meta };
            } catch {
                return { id, titulo: 'Sin título', library: 'unknown' };
            }
        }));
        res.json({ contenidos: list, total: list.length });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ---------------------------------------------------------------------------
// Subir paquete .h5p
// ---------------------------------------------------------------------------
app.post('/h5p/api/upload', async (req, res) => {
    if (!req.files || !req.files.h5p_file) {
        return res.status(400).json({ error: 'Campo h5p_file requerido' });
    }
    try {
        const file = req.files.h5p_file;
        const user = { id: req.body.usuario_id || 'sistema', name: req.body.usuario_nombre || 'Sistema', type: 'local', email: '' };
        const { id, metadata, parameters } = await h5pEditor.uploadPackage(
            file.tempFilePath,
            user,
        );
        await fs.remove(file.tempFilePath);
        res.json({
            h5p_content_id: id,
            titulo: metadata.title,
            library: metadata.mainLibrary,
            metadatos: metadata,
        });
    } catch (err) {
        res.status(422).json({ error: err.message });
    }
});

// ---------------------------------------------------------------------------
// Obtener HTML del player para embedding
// ---------------------------------------------------------------------------
app.get('/h5p/api/player/:contentId', async (req, res) => {
    try {
        const user = {
            id:    req.query.usuario_id    || 'alumno',
            name:  req.query.usuario_nombre || 'Alumno',
            type:  'local',
            email: '',
        };
        // render(contentId, actingUser, language, options) — el HTML ya viene listo
        const html = await h5pPlayer.render(req.params.contentId, user, 'es');
        if (typeof html === 'string') {
            res.setHeader('Content-Type', 'text/html; charset=utf-8');
            res.send(html);
        } else {
            // v9+ puede devolver un objeto modelo — serializamos a HTML básico
            res.setHeader('Content-Type', 'text/html; charset=utf-8');
            res.send(`<!DOCTYPE html><html><head>
<meta charset="UTF-8">
<script>window.H5PIntegration = ${JSON.stringify(html)};</script>
</head><body><div class="h5p-content" data-content-id="${req.params.contentId}"></div></body></html>`);
        }
    } catch (err) {
        res.status(404).json({ error: err.message });
    }
});

// ---------------------------------------------------------------------------
// Metadatos de un contenido
// ---------------------------------------------------------------------------
app.get('/h5p/api/contenidos/:contentId', async (req, res) => {
    try {
        const meta = await h5pEditor.contentManager.getContentMetadata(req.params.contentId);
        res.json({ id: req.params.contentId, titulo: meta.title, library: meta.mainLibrary, metadatos: meta });
    } catch (err) {
        res.status(404).json({ error: err.message });
    }
});

// ---------------------------------------------------------------------------
// Eliminar contenido
// ---------------------------------------------------------------------------
app.delete('/h5p/api/contenidos/:contentId', async (req, res) => {
    try {
        const user = { id: 'admin', name: 'Admin', type: 'local', email: '' };
        await h5pEditor.deleteContent(req.params.contentId, user);
        res.json({ eliminado: true });
    } catch (err) {
        res.status(404).json({ error: err.message });
    }
});

// ---------------------------------------------------------------------------
// Recibir resultado xAPI (postMessage desde el player iframe)
// ---------------------------------------------------------------------------
app.post('/h5p/api/xapi/:contentId', async (req, res) => {
    const { contentId } = req.params;
    const { statement, usuario_id, asignacion_id } = req.body;
    try {
        // Parsear score y completion del statement xAPI
        const result = statement?.result || {};
        const score = result.score || {};
        const payload = {
            h5p_content_id: contentId,
            usuario_id,
            asignacion_id: asignacion_id || null,
            score_raw: score.raw ?? null,
            score_max: score.max ?? null,
            score_escalado: score.scaled ?? null,
            completado: result.completion ?? false,
            aprobado: result.success ?? null,
            tiempo_segundos: result.duration
                ? _isoDurationToSeconds(result.duration)
                : null,
            xapi_statement: statement,
        };
        // Reenviar al ADES API para persistencia
        const axios = require('axios');
        await axios.post(`${ADES_API_URL}/api/v1/h5p/xapi-resultado`, payload, { timeout: 5000 }).catch(() => {});
        res.json({ recibido: true });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

function _isoDurationToSeconds(iso) {
    const m = iso.match(/PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?/);
    if (!m) return null;
    return ((+m[1] || 0) * 3600) + ((+m[2] || 0) * 60) + (+m[3] || 0);
}

// ---------------------------------------------------------------------------
// Arranque
// ---------------------------------------------------------------------------
(async () => {
    try {
        await initH5P();
        app.listen(PORT, '0.0.0.0', () => {
            console.log(`[ADES-H5P] Servicio en http://0.0.0.0:${PORT}`);
        });
    } catch (err) {
        console.error('[ADES-H5P] Error al inicializar:', err);
        process.exit(1);
    }
})();
