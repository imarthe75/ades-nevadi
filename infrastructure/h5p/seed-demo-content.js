'use strict';
/**
 * seed-demo-content.js
 * Instala librerías H5P desde el Hub y crea 5 contenidos demo.
 * Ejecutar dentro del contenedor: node /app/seed-demo-content.js
 */

const H5P = require('@lumieducation/h5p-server');
const fs = require('fs-extra');
const path = require('path');

const DATA_DIR = process.env.H5P_DATA_DIR || '/data';

const dirs = {
    libraries:      path.join(DATA_DIR, 'libraries'),
    content:        path.join(DATA_DIR, 'content'),
    temporaryFiles: path.join(DATA_DIR, 'tmp'),
};
Object.values(dirs).forEach(d => fs.ensureDirSync(d));

// ── Contenidos demo ──────────────────────────────────────────────────────────
// machineName + majorVersion + minorVersion según lo que queda instalado.
// Se resuelve dinámicamente desde listInstalledLibraries().

const DEMO_CONTENT = [
    {
        adesTitulo:      'Ejercicio de Arrastrar: Partes de la Célula',
        adesDescripcion: 'Arrastra las etiquetas al lugar correcto en el diagrama de la célula',
        machineName:     'H5P.DragQuestion',
        content: {
            scoreShow: 10,
            question: {
                settings: {
                    size: { width: 620, height: 310 },
                },
                task: {
                    elements: [
                        { id: 0, type: { params: { contentName: 'Núcleo', background: { path: '' } }, library: 'H5P.AdvancedText 1.1', metadata: { contentType: 'Text', license: 'U', title: 'Núcleo' }, subContentId: 'unused-1' }, x: 5, y: 10, width: 20, height: 15, dropZones: ['0'] },
                        { id: 1, type: { params: { contentName: 'Mitocondria', background: { path: '' } }, library: 'H5P.AdvancedText 1.1', metadata: { contentType: 'Text', license: 'U', title: 'Mitocondria' }, subContentId: 'unused-2' }, x: 5, y: 30, width: 20, height: 15, dropZones: ['1'] },
                        { id: 2, type: { params: { contentName: 'Membrana celular', background: { path: '' } }, library: 'H5P.AdvancedText 1.1', metadata: { contentType: 'Text', license: 'U', title: 'Membrana' }, subContentId: 'unused-3' }, x: 5, y: 50, width: 20, height: 15, dropZones: ['2'] },
                    ],
                    dropZones: [
                        { id: '0', x: 60, y: 10, width: 30, height: 15, showLabel: true, tipsAndFeedback: { tip: '' }, label: 'Zona: Núcleo', acceptedElements: ['0'] },
                        { id: '1', x: 60, y: 30, width: 30, height: 15, showLabel: true, tipsAndFeedback: { tip: '' }, label: 'Zona: Mitocondria', acceptedElements: ['1'] },
                        { id: '2', x: 60, y: 50, width: 30, height: 15, showLabel: true, tipsAndFeedback: { tip: '' }, label: 'Zona: Membrana celular', acceptedElements: ['2'] },
                    ],
                },
            },
            overallFeedback: [
                { from: 0,  to: 49,  feedback: 'Sigue estudiando las partes de la célula.' },
                { from: 50, to: 79,  feedback: '¡Buen intento! Repasa los organelos.' },
                { from: 80, to: 100, feedback: '¡Excelente! Dominas la estructura celular.' },
            ],
            behaviour: { enableRetry: true, enableSolutionsButton: true, preventResize: false, singlePoint: false, showSolutionsRequiresInput: true, applyPenalties: true, enableFullScreen: false, showScorePoints: true, showTitle: true },
            l10n: { checkAnswer: 'Verificar', tryAgain: 'Reintentar', showSolution: 'Ver solución', score: 'Obtuviste :num de :total puntos.', noInput: 'Por favor ubica los elementos antes de verificar.', oneToOne: 'Correcto', alignmentAid: 'Ayuda de alineación' },
        },
    },
    {
        adesTitulo:      'Verdadero o Falso: Célula Animal',
        adesDescripcion: 'Pregunta rápida sobre la estructura de la célula animal',
        machineName:     'H5P.TrueFalse',
        content: {
            question: 'La mitocondria es el organelo responsable de producir energía en la célula animal.',
            correct: 'True',
            behaviour: { enableRetry: true, enableSolutionsButton: true, confirmCheckDialog: false, confirmRetryDialog: false, autoCheck: false },
            l10n: { trueText: 'Verdadero', falseText: 'Falso', score: 'Obtuviste @score de @total puntos', checkAnswer: 'Verificar respuesta', showSolutionButton: 'Mostrar solución', tryAgain: 'Reintentar', wrongAnswerMessage: 'Respuesta incorrecta', correctAnswerMessage: '¡Respuesta correcta!', falseAnswerMessage: 'Respuesta incorrecta', scoreBarLabel: 'Puntos' },
        },
    },
    {
        adesTitulo:      'Completa el texto: Revolución Francesa',
        adesDescripcion: 'Ejercicio de completar los conceptos clave de la Revolución Francesa',
        machineName:     'H5P.Blanks',
        content: {
            text: '<p>La Revolución Francesa comenzó en el año *1789*. El rey *Luis XVI* fue guillotinado en *1793*. El lema de la revolución fue *Libertad, Igualdad y Fraternidad*.</p>',
            overallFeedback: [
                { from: 0,  to: 49,  feedback: 'Estudia más sobre la Revolución Francesa.' },
                { from: 50, to: 100, feedback: '¡Bien hecho!' },
            ],
            showSolutions: 'Mostrar solución',
            tryAgain: 'Reintentar',
            checkAnswer: 'Verificar',
            notFilledOut: 'Por favor completa todos los espacios',
            answerIsCorrect: '":ans" es correcto',
            answerIsWrong: '":ans" es incorrecto',
            answeredCorrectly: 'Respondido correctamente',
            answeredIncorrectly: 'Respondido incorrectamente',
            solutionLabel: 'Solución correcta:',
            inputLabel: 'Respuesta @num de @total',
            inputHasTipLabel: 'Pista disponible',
            tipLabel: 'Pista',
            behaviour: { enableRetry: true, enableSolutionsButton: true, autoCheck: false, caseSensitive: false, showSolutionsRequiresInput: true, separateLines: false, confirmCheckDialog: false, confirmRetryDialog: false, acceptSpellingErrors: false },
            scoreBarLabel: 'Puntos',
        },
    },
    {
        adesTitulo:      'Tarjetas: Vocabulario de Ciencias',
        adesDescripcion: 'Repaso de vocabulario científico con tarjetas interactivas',
        machineName:     'H5P.Flashcards',
        content: {
            description: 'Estudia el vocabulario de ciencias naturales con estas tarjetas.',
            cards: [
                { text: 'Proceso por el cual las plantas producen su alimento usando luz solar', answer: 'Fotosíntesis' },
                { text: 'Unidad básica de la vida', answer: 'Célula' },
                { text: 'Organelo que contiene el ADN en células eucariotas', answer: 'Núcleo' },
                { text: 'Proceso de división celular en células somáticas', answer: 'Mitosis' },
                { text: 'Molécula que almacena la información genética', answer: 'ADN' },
            ],
            progressText: 'Tarjeta @card de @total',
            next: 'Siguiente',
            previous: 'Anterior',
            checkAnswerText: 'Verificar respuesta',
            showSolutionsRequiresInput: true,
            defaultAnswerText: 'Tu respuesta',
            correctAnswerText: '¡Correcto!',
            incorrectAnswerText: 'Incorrecto. La respuesta es:',
            autocomplete: 'off',
        },
    },
    {
        adesTitulo:      'Acordeón: Sistema Solar',
        adesDescripcion: 'Información sobre los planetas del sistema solar en formato acordeón',
        machineName:     'H5P.Accordion',
        content: {
            panels: [
                { title: 'Mercurio', content: '<p>Mercurio es el planeta más pequeño del sistema solar y el más cercano al Sol. Su superficie está cubierta de cráteres y no tiene atmósfera significativa.</p>' },
                { title: 'Venus',   content: '<p>Venus es el segundo planeta y el más caliente, con temperaturas que superan los 450°C. Su densa atmósfera de CO₂ genera un efecto invernadero extremo.</p>' },
                { title: 'Tierra',  content: '<p>La Tierra es el único planeta conocido con vida. El 71% de su superficie está cubierta de agua y tiene una luna natural.</p>' },
                { title: 'Marte',   content: '<p>Marte es el planeta rojo, con el volcán más alto del sistema solar: el Olimpo Mons (21 km). Tiene dos lunas: Fobos y Deimos.</p>' },
                { title: 'Júpiter', content: '<p>Júpiter es el planeta más grande. Es un gigante gaseoso con 95 lunas conocidas. Su Gran Mancha Roja es una tormenta que lleva siglos activa.</p>' },
            ],
        },
    },
];

// ── Main ─────────────────────────────────────────────────────────────────────

(async () => {
    console.log('🚀 ADES H5P — Seed de contenido demo\n');

    const configPath = path.join(DATA_DIR, 'h5p-config.json');
    if (!fs.existsSync(configPath)) fs.writeJsonSync(configPath, {});
    const config = await new H5P.H5PConfig(new H5P.fsImplementations.JsonStorage(configPath)).load();
    config.baseUrl = '/h5p';
    config.librariesUrl = '/h5p/libraries';
    config.coreUrl = '/h5p/core';
    config.contentFilesUrlPath = '/h5p/content';

    const libraryStorage   = new H5P.fsImplementations.FileLibraryStorage(dirs.libraries);
    const contentStorage   = new H5P.fsImplementations.FileContentStorage(dirs.content);
    const tmpStorage       = new H5P.fsImplementations.DirectoryTemporaryFileStorage(dirs.temporaryFiles);
    const kv               = new H5P.fsImplementations.InMemoryStorage();
    const editor           = new H5P.H5PEditor(kv, config, libraryStorage, contentStorage, tmpStorage);

    const user = { id: 'sistema', name: 'Sistema ADES', type: 'local', email: '' };

    // Obtener librerías instaladas
    const installedMap = await editor.libraryManager.listInstalledLibraries();

    const results = [];

    for (const demo of DEMO_CONTENT) {
        console.log(`\n→ ${demo.adesTitulo} (${demo.machineName})`);
        try {
            const versions = installedMap[demo.machineName];
            if (!versions || versions.length === 0) {
                throw new Error(`Librería ${demo.machineName} no instalada`);
            }
            // Usar la versión más reciente instalada
            const lib = versions[versions.length - 1];
            const uberName = `${lib.machineName} ${lib.majorVersion}.${lib.minorVersion}`;

            const metadata = {
                title: demo.adesTitulo,
                language: 'es',
                license: 'U',
                mainLibrary: lib.machineName,
                embedTypes: ['div'],
                preloadedDependencies: [{ machineName: lib.machineName, majorVersion: lib.majorVersion, minorVersion: lib.minorVersion }],
            };

            const contentId = await editor.saveOrUpdateContent(
                undefined,          // contentId = nuevo
                demo.content,       // parameters
                metadata,           // metadata
                uberName,           // mainLibraryUbername e.g. "H5P.TrueFalse 1.8"
                user,
            );

            console.log(`  ✅ Creado con contentId: ${contentId}`);
            results.push({ titulo: demo.adesTitulo, contentId: String(contentId), machineName: demo.machineName, uberName });
        } catch (e) {
            console.error(`  ❌ Error: ${e.message}`);
            results.push({ titulo: demo.adesTitulo, error: e.message });
        }
    }

    console.log('\n══════════════════════════════════════════');
    console.log('SQL para actualizar ades_h5p_contenidos:');
    console.log('══════════════════════════════════════════');
    results.filter(r => !r.error).forEach(r => {
        console.log(`UPDATE ades_h5p_contenidos SET h5p_content_id='${r.contentId}', h5p_library='${r.machineName}' WHERE titulo='${r.titulo}';`);
    });
    console.log('══════════════════════════════════════════\n');
    process.exit(0);
})();
