-- 052_catalogos_lenguas_ingles.sql
-- Catálogo INALI: 68 agrupaciones lingüísticas indígenas de México
-- Catálogo CEFR: niveles de inglés A1-C2 (Marco Común Europeo de Referencia)
-- Agrega FK en ades_estudiantes

-- ============================================================
-- LENGUAS INDÍGENAS (fuente: INALI, DOF 2008 y actualizaciones)
-- ============================================================
CREATE TABLE public.ades_lenguas_indigenas (
    id                  UUID        PRIMARY KEY DEFAULT uuidv7(),
    familia_linguistica TEXT        NOT NULL,
    agrupacion          TEXT        NOT NULL,
    autonym             TEXT,           -- nombre en la propia lengua
    activa              BOOLEAN     NOT NULL DEFAULT TRUE,
    ref                 UUID        NOT NULL DEFAULT uuidv7(),
    row_version         INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion      TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion  TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion    TEXT        NOT NULL DEFAULT CURRENT_USER,
    usuario_modificacion TEXT       NOT NULL DEFAULT CURRENT_USER,
    CONSTRAINT uq_lengua_agrupacion UNIQUE (agrupacion)
);

COMMENT ON TABLE public.ades_lenguas_indigenas IS 'Catálogo INALI — 68 agrupaciones lingüísticas indígenas nacionales';
COMMENT ON COLUMN public.ades_lenguas_indigenas.autonym IS 'Nombre que usa la propia comunidad para designar su lengua';

-- Datos INALI por familia lingüística

-- Álgica (1)
INSERT INTO ades_lenguas_indigenas (familia_linguistica, agrupacion, autonym) VALUES
  ('Álgica', 'Kickapoo', 'Kikapu');

-- Yuto-Nahua (11)
INSERT INTO ades_lenguas_indigenas (familia_linguistica, agrupacion, autonym) VALUES
  ('Yuto-Nahua', 'Cora',                   'Nayeri'),
  ('Yuto-Nahua', 'Guarijío',               'Macurawe'),
  ('Yuto-Nahua', 'Huichol',                'Wixáritari'),
  ('Yuto-Nahua', 'Mayo',                   'Yoreme'),
  ('Yuto-Nahua', 'Náhuatl',                NULL),
  ('Yuto-Nahua', 'Pápago',                 'Tohono O''odham'),
  ('Yuto-Nahua', 'Pima',                   'O''ob'),
  ('Yuto-Nahua', 'Tarahumara',             'Rarámuri'),
  ('Yuto-Nahua', 'Tepehuano del Norte',    'O''dam'),
  ('Yuto-Nahua', 'Tepehuano del Sur',      'Ódami'),
  ('Yuto-Nahua', 'Yaqui',                  'Yoeme');

-- Cochimí-Yumana (5)
INSERT INTO ades_lenguas_indigenas (familia_linguistica, agrupacion, autonym) VALUES
  ('Cochimí-Yumana', 'Cochimí',  NULL),
  ('Cochimí-Yumana', 'Cucapá',   NULL),
  ('Cochimí-Yumana', 'Kiliwa',   'Ko''lew'),
  ('Cochimí-Yumana', 'Kumiai',   'Ti''pai'),
  ('Cochimí-Yumana', 'Paipai',   'Akwa''ala');

-- Seri (aislante) (1)
INSERT INTO ades_lenguas_indigenas (familia_linguistica, agrupacion, autonym) VALUES
  ('Seri', 'Seri', 'Comcáac');

-- Oto-Mangue (18)
INSERT INTO ades_lenguas_indigenas (familia_linguistica, agrupacion, autonym) VALUES
  ('Oto-Mangue', 'Amuzgo',           'Ñomndaa'),
  ('Oto-Mangue', 'Chatino',          NULL),
  ('Oto-Mangue', 'Chichimeco Jonaz', 'Úza'),
  ('Oto-Mangue', 'Chinanteco',       NULL),
  ('Oto-Mangue', 'Chocholteco',      'Chochon'),
  ('Oto-Mangue', 'Cuicateco',        NULL),
  ('Oto-Mangue', 'Ixcateco',         NULL),
  ('Oto-Mangue', 'Matlatzinca',      NULL),
  ('Oto-Mangue', 'Mazahua',          'Jñatjo'),
  ('Oto-Mangue', 'Mazateco',         NULL),
  ('Oto-Mangue', 'Mixteco',          'Tu''un Savi'),
  ('Oto-Mangue', 'Ocuilteco',        'Tlahuica'),
  ('Oto-Mangue', 'Otomí',            'Hñähñu'),
  ('Oto-Mangue', 'Pame',             NULL),
  ('Oto-Mangue', 'Popoloca',         NULL),
  ('Oto-Mangue', 'Tlapaneco',        'Me''phaa'),
  ('Oto-Mangue', 'Triqui',           NULL),
  ('Oto-Mangue', 'Zapoteco',         NULL);

-- Maya (18)
INSERT INTO ades_lenguas_indigenas (familia_linguistica, agrupacion, autonym) VALUES
  ('Maya', 'Akateko',          NULL),
  ('Maya', 'Awakateko',        NULL),
  ('Maya', 'Chol',             NULL),
  ('Maya', 'Chontal de Tabasco', NULL),
  ('Maya', 'Chuj',             NULL),
  ('Maya', 'Huasteco',         'Téenek'),
  ('Maya', 'Ixil',             NULL),
  ('Maya', 'Jakalteko',        'Popti'''),
  ('Maya', 'Kaqchikel',        NULL),
  ('Maya', 'Lacandón',         'Hach Winik'),
  ('Maya', 'Mam',              NULL),
  ('Maya', 'Maya',             NULL),
  ('Maya', 'Motozintleco',     'Mochó'),
  ('Maya', 'Q''anjob''al',     NULL),
  ('Maya', 'Q''eqchi''',       NULL),
  ('Maya', 'Tojolabal',        NULL),
  ('Maya', 'Tzeltal',          'Bats''il k''op'),
  ('Maya', 'Tzotzil',          'Bats''il k''op');

-- Totonaco-Tepehua (2)
INSERT INTO ades_lenguas_indigenas (familia_linguistica, agrupacion, autonym) VALUES
  ('Totonaco-Tepehua', 'Tepehua',  NULL),
  ('Totonaco-Tepehua', 'Totonaco', 'Totonakú');

-- Tarasca (1)
INSERT INTO ades_lenguas_indigenas (familia_linguistica, agrupacion, autonym) VALUES
  ('Tarasca', 'Purépecha', 'P''urhépecha');

-- Mixe-Zoque (4)
INSERT INTO ades_lenguas_indigenas (familia_linguistica, agrupacion, autonym) VALUES
  ('Mixe-Zoque', 'Ayapaneco',            'Numte Oote'),
  ('Mixe-Zoque', 'Mixe',                 'Ayuujk'),
  ('Mixe-Zoque', 'Popoluca de la Sierra', NULL),
  ('Mixe-Zoque', 'Zoque',               NULL);

-- Tequistlateca (aislante) (1)
INSERT INTO ades_lenguas_indigenas (familia_linguistica, agrupacion, autonym) VALUES
  ('Tequistlateca', 'Chontal de Oaxaca', NULL);

-- Huave (aislante) (1)
INSERT INTO ades_lenguas_indigenas (familia_linguistica, agrupacion, autonym) VALUES
  ('Huave', 'Huave', 'Ikoots');

-- Tlapaneco-Subtiaba (1) — ya incluido en Oto-Mangue como Tlapaneco arriba

-- Otras lenguas con presencia en México (comunidades transfronterizas)
INSERT INTO ades_lenguas_indigenas (familia_linguistica, agrupacion, autonym) VALUES
  ('Oto-Mangue',     'Amuzgo del Norte',  'Ñomndaa'),
  ('Cochimí-Yumana', 'Diegueño',          'Kumeyaay');

-- Índices
CREATE INDEX idx_lengua_familia ON ades_lenguas_indigenas (familia_linguistica);
CREATE INDEX idx_lengua_agrupacion ON ades_lenguas_indigenas (agrupacion);

-- Trigger auditoría
SELECT auditoria.asignar_biu('public.ades_lenguas_indigenas');


-- ============================================================
-- NIVELES DE INGLÉS — Marco Común Europeo de Referencia (CEFR)
-- ============================================================
CREATE TABLE public.ades_niveles_ingles (
    id                  UUID        PRIMARY KEY DEFAULT uuidv7(),
    nivel               VARCHAR(2)  NOT NULL,   -- A1, A2, B1, B2, C1, C2
    nombre              TEXT        NOT NULL,
    descripcion         TEXT,
    equivalencia_cambridge TEXT,                -- KET, PET, FCE, CAE, CPE
    rango_toefl_ibt     VARCHAR(20),
    rango_ielts         VARCHAR(10),
    orden               SMALLINT    NOT NULL,
    activo              BOOLEAN     NOT NULL DEFAULT TRUE,
    ref                 UUID        NOT NULL DEFAULT uuidv7(),
    row_version         INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion      TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion  TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion    TEXT        NOT NULL DEFAULT CURRENT_USER,
    usuario_modificacion TEXT       NOT NULL DEFAULT CURRENT_USER,
    CONSTRAINT uq_nivel_cefr UNIQUE (nivel)
);

COMMENT ON TABLE public.ades_niveles_ingles IS 'Marco Común Europeo de Referencia para las Lenguas (CEFR)';

INSERT INTO ades_niveles_ingles (nivel, nombre, descripcion, equivalencia_cambridge, rango_toefl_ibt, rango_ielts, orden) VALUES
  ('A1', 'Principiante',
   'Comprende y usa expresiones cotidianas básicas. Puede presentarse y dar información personal.',
   'Pre A1 Starters', '0–31', '1.0–2.0', 1),

  ('A2', 'Básico',
   'Comprende oraciones sobre áreas de relevancia inmediata (información personal, familia, compras). Comunica en tareas simples.',
   'A2 Key (KET)', '32–42', '2.5–3.5', 2),

  ('B1', 'Intermedio',
   'Comprende los puntos principales en textos claros sobre temas cotidianos. Se desenvuelve en situaciones habituales de viaje.',
   'B1 Preliminary (PET)', '43–60', '4.0–5.0', 3),

  ('B2', 'Intermedio alto',
   'Comprende ideas principales de textos complejos, incluyendo discusiones técnicas en su área. Produce textos claros y detallados.',
   'B2 First (FCE)', '61–88', '5.5–6.5', 4),

  ('C1', 'Avanzado',
   'Comprende una amplia gama de textos exigentes. Se expresa de forma fluida y espontánea sin buscar palabras con frecuencia.',
   'C1 Advanced (CAE)', '89–109', '7.0–8.0', 5),

  ('C2', 'Maestría',
   'Comprende con facilidad todo lo que escucha o lee. Se expresa espontáneamente, con precisión y fluidez.',
   'C2 Proficiency (CPE)', '110–120', '8.5–9.0', 6);

CREATE INDEX idx_nivel_ingles_orden ON ades_niveles_ingles (orden);

SELECT auditoria.asignar_biu('public.ades_niveles_ingles');


-- ============================================================
-- FK en ades_estudiantes
-- ============================================================
ALTER TABLE public.ades_estudiantes
    ADD COLUMN lengua_indigena_id UUID REFERENCES ades_lenguas_indigenas(id),
    ADD COLUMN nivel_ingles_id     UUID REFERENCES ades_niveles_ingles(id);

COMMENT ON COLUMN public.ades_estudiantes.lengua_indigena_id IS 'Agrupación lingüística indígena (catálogo INALI)';
COMMENT ON COLUMN public.ades_estudiantes.nivel_ingles_id    IS 'Nivel de inglés CEFR certificado o evaluado';
COMMENT ON COLUMN public.ades_estudiantes.etnia              IS 'Autoidentificación étnica (campo libre, complementa lengua_indigena_id)';

CREATE INDEX idx_est_lengua_indigena ON ades_estudiantes (lengua_indigena_id) WHERE lengua_indigena_id IS NOT NULL;
CREATE INDEX idx_est_nivel_ingles    ON ades_estudiantes (nivel_ingles_id)    WHERE nivel_ingles_id    IS NOT NULL;
