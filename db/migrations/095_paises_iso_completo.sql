-- =============================================================================
-- Migración 095: Catálogo de países ISO 3166-1 completo
--               + ISO 3166-2 para estados de México
--
-- Agrega a ades_paises:
--   iso_alpha_3, iso_numeric, nombre_en, nombre_fr, nombre_oficial_es,
--   codigo_telefono, region, emoji_bandera
--
-- Agrega a ades_estados:
--   abreviatura (clave INEGI 3-4 letras), iso_3166_2 (MX-AGU, etc.)
--
-- Nota: clave_pais ya contiene ISO 3166-1 alpha-2 (MX, US, FR...)
-- =============================================================================

BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. NUEVAS COLUMNAS EN ades_paises
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE ades_paises
  ADD COLUMN IF NOT EXISTS iso_alpha_3       CHAR(3),
  ADD COLUMN IF NOT EXISTS iso_numeric       CHAR(3),
  ADD COLUMN IF NOT EXISTS nombre_en         VARCHAR(120),
  ADD COLUMN IF NOT EXISTS nombre_fr         VARCHAR(120),
  ADD COLUMN IF NOT EXISTS nombre_oficial_es VARCHAR(255),
  ADD COLUMN IF NOT EXISTS codigo_telefono   VARCHAR(12),
  ADD COLUMN IF NOT EXISTS region            VARCHAR(50),
  ADD COLUMN IF NOT EXISTS emoji_bandera     VARCHAR(10),
  ADD COLUMN IF NOT EXISTS nacionalidad      VARCHAR(60);

-- Constraints únicos (nullable: XK-Kosovo no tiene código ISO oficial)
DO $$ BEGIN
  ALTER TABLE ades_paises ADD CONSTRAINT uq_paises_iso_alpha3 UNIQUE (iso_alpha_3);
EXCEPTION WHEN duplicate_table THEN NULL; END $$;
DO $$ BEGIN
  ALTER TABLE ades_paises ADD CONSTRAINT uq_paises_iso_numeric UNIQUE (iso_numeric);
EXCEPTION WHEN duplicate_table THEN NULL; END $$;

COMMENT ON COLUMN ades_paises.clave_pais        IS 'ISO 3166-1 alpha-2 (2 letras): MX, US, FR';
COMMENT ON COLUMN ades_paises.iso_alpha_3        IS 'ISO 3166-1 alpha-3 (3 letras): MEX, USA, FRA';
COMMENT ON COLUMN ades_paises.iso_numeric        IS 'ISO 3166-1 numérico (3 dígitos): 484, 840, 250';
COMMENT ON COLUMN ades_paises.nombre_pais        IS 'Nombre común del país en español';
COMMENT ON COLUMN ades_paises.nombre_oficial_es  IS 'Nombre oficial completo en español según ISO 3166-1';
COMMENT ON COLUMN ades_paises.nombre_en          IS 'Nombre en inglés (ISO 3166-1 English short name)';
COMMENT ON COLUMN ades_paises.nombre_fr          IS 'Nombre en francés (ISO 3166-1 French short name)';
COMMENT ON COLUMN ades_paises.nacionalidad       IS 'Gentilicio en español (mexicano/a, estadounidense...)';
COMMENT ON COLUMN ades_paises.codigo_telefono    IS 'Prefijo telefónico internacional (ej: +52)';
COMMENT ON COLUMN ades_paises.region             IS 'Región geográfica macro';
COMMENT ON COLUMN ades_paises.emoji_bandera      IS 'Emoji de bandera del país';

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. NUEVAS COLUMNAS EN ades_estados
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE ades_estados
  ADD COLUMN IF NOT EXISTS abreviatura VARCHAR(6),
  ADD COLUMN IF NOT EXISTS iso_3166_2  VARCHAR(10);

DO $$ BEGIN
  ALTER TABLE ades_estados ADD CONSTRAINT uq_estados_iso_3166_2 UNIQUE (iso_3166_2);
EXCEPTION WHEN duplicate_table THEN NULL; END $$;

COMMENT ON COLUMN ades_estados.abreviatura IS 'Abreviatura oficial INEGI (AGS, CDMX, MICH...)';
COMMENT ON COLUMN ades_estados.iso_3166_2  IS 'Código ISO 3166-2 (MX-AGU, MX-CMX, MX-MIC...)';

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. POBLAR DATOS ISO DE TODOS LOS PAÍSES
--    Columnas: alpha2, alpha3, numeric, en, fr, oficial_es, tel, region, emoji
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE ades_paises AS p SET
  iso_alpha_3       = d.alpha3,
  iso_numeric       = d.num,
  nombre_en         = d.en,
  nombre_fr         = d.fr,
  nombre_oficial_es = d.oficial,
  codigo_telefono   = d.tel,
  region            = d.region,
  emoji_bandera     = d.emoji
FROM (VALUES
  ('AD','AND','020','Andorra','Andorre','Principado de Andorra','+376','Europa','🇦🇩'),
  ('AE','ARE','784','United Arab Emirates','Émirats arabes unis','Emiratos Árabes Unidos','+971','Oriente Medio','🇦🇪'),
  ('AF','AFG','004','Afghanistan','Afghanistan','República Islámica de Afganistán','+93','Asia Central','🇦🇫'),
  ('AG','ATG','028','Antigua and Barbuda','Antigua-et-Barbuda','Antigua y Barbuda','+1-268','El Caribe','🇦🇬'),
  ('AI','AIA','660','Anguilla','Anguilla','Anguilla','+1-264','El Caribe','🇦🇮'),
  ('AL','ALB','008','Albania','Albanie','República de Albania','+355','Europa','🇦🇱'),
  ('AM','ARM','051','Armenia','Arménie','República de Armenia','+374','Asia Occidental','🇦🇲'),
  ('AO','AGO','024','Angola','Angola','República de Angola','+244','África','🇦🇴'),
  ('AQ','ATA','010','Antarctica','Antarctique','Antártida',NULL,'Antártida y Territorios','🇦🇶'),
  ('AR','ARG','032','Argentina','Argentine','República Argentina','+54','América del Sur','🇦🇷'),
  ('AS','ASM','016','American Samoa','Samoa américaines','Samoa Americana','+1-684','Oceanía','🇦🇸'),
  ('AT','AUT','040','Austria','Autriche','República de Austria','+43','Europa','🇦🇹'),
  ('AU','AUS','036','Australia','Australie','Commonwealth de Australia','+61','Oceanía','🇦🇺'),
  ('AW','ABW','533','Aruba','Aruba','Aruba','+297','El Caribe','🇦🇼'),
  ('AX','ALA','248','Åland Islands','Îles Åland','Islas Åland','+358','Europa','🇦🇽'),
  ('AZ','AZE','031','Azerbaijan','Azerbaïdjan','República de Azerbaiyán','+994','Asia Occidental','🇦🇿'),
  ('BA','BIH','070','Bosnia and Herzegovina','Bosnie-Herzégovine','Bosnia y Herzegovina','+387','Europa','🇧🇦'),
  ('BB','BRB','052','Barbados','Barbade','Barbados','+1-246','El Caribe','🇧🇧'),
  ('BD','BGD','050','Bangladesh','Bangladesh','República Popular de Bangladesh','+880','Asia Meridional','🇧🇩'),
  ('BE','BEL','056','Belgium','Belgique','Reino de Bélgica','+32','Europa','🇧🇪'),
  ('BF','BFA','854','Burkina Faso','Burkina Faso','Burkina Faso','+226','África','🇧🇫'),
  ('BG','BGR','100','Bulgaria','Bulgarie','República de Bulgaria','+359','Europa','🇧🇬'),
  ('BH','BHR','048','Bahrain','Bahreïn','Reino de Baréin','+973','Oriente Medio','🇧🇭'),
  ('BI','BDI','108','Burundi','Burundi','República de Burundi','+257','África','🇧🇮'),
  ('BJ','BEN','204','Benin','Bénin','República de Benín','+229','África','🇧🇯'),
  ('BL','BLM','652','Saint Barthélemy','Saint-Barthélemy','San Bartolomé','+590','El Caribe','🇧🇱'),
  ('BM','BMU','060','Bermuda','Bermudes','Bermudas','+1-441','América del Norte','🇧🇲'),
  ('BN','BRN','096','Brunei','Brunéi Darussalam','Brunéi Darussalam','+673','Asia Sudoriental','🇧🇳'),
  ('BO','BOL','068','Bolivia','Bolivie','Estado Plurinacional de Bolivia','+591','América del Sur','🇧🇴'),
  ('BQ','BES','535','Caribbean Netherlands','Pays-Bas caribéens','Caribe Neerlandés','+599','El Caribe','🇧🇶'),
  ('BR','BRA','076','Brazil','Brésil','República Federativa del Brasil','+55','América del Sur','🇧🇷'),
  ('BS','BHS','044','Bahamas','Bahamas','Commonwealth de las Bahamas','+1-242','El Caribe','🇧🇸'),
  ('BT','BTN','064','Bhutan','Bhoutan','Reino de Bután','+975','Asia Meridional','🇧🇹'),
  ('BV','BVT','074','Bouvet Island','Île Bouvet','Isla Bouvet',NULL,'Antártida y Territorios','🇧🇻'),
  ('BW','BWA','072','Botswana','Botswana','República de Botsuana','+267','África','🇧🇼'),
  ('BY','BLR','112','Belarus','Bélarus','República de Bielorrusia','+375','Europa','🇧🇾'),
  ('BZ','BLZ','084','Belize','Belize','Belice','+501','América Central','🇧🇿'),
  ('CA','CAN','124','Canada','Canada','Canadá','+1','América del Norte','🇨🇦'),
  ('CC','CCK','166','Cocos Islands','Îles Cocos','Territorio de las Islas Cocos','+61','Oceanía','🇨🇨'),
  ('CD','COD','180','DR Congo','Congo (Rép. dém.)','República Democrática del Congo','+243','África','🇨🇩'),
  ('CF','CAF','140','Central African Republic','République centrafricaine','República Centroafricana','+236','África','🇨🇫'),
  ('CG','COG','178','Republic of the Congo','République du Congo','República del Congo','+242','África','🇨🇬'),
  ('CH','CHE','756','Switzerland','Suisse','Confederación Suiza','+41','Europa','🇨🇭'),
  ('CI','CIV','384','Ivory Coast','Côte d''Ivoire','República de Côte d''Ivoire','+225','África','🇨🇮'),
  ('CK','COK','184','Cook Islands','Îles Cook','Islas Cook','+682','Oceanía','🇨🇰'),
  ('CL','CHL','152','Chile','Chili','República de Chile','+56','América del Sur','🇨🇱'),
  ('CM','CMR','120','Cameroon','Cameroun','República de Camerún','+237','África','🇨🇲'),
  ('CN','CHN','156','China','Chine','República Popular China','+86','Asia Oriental','🇨🇳'),
  ('CO','COL','170','Colombia','Colombie','República de Colombia','+57','América del Sur','🇨🇴'),
  ('CR','CRI','188','Costa Rica','Costa Rica','República de Costa Rica','+506','América Central','🇨🇷'),
  ('CU','CUB','192','Cuba','Cuba','República de Cuba','+53','El Caribe','🇨🇺'),
  ('CV','CPV','132','Cape Verde','Cabo Verde','República de Cabo Verde','+238','África','🇨🇻'),
  ('CW','CUW','531','Curaçao','Curaçao','Curazao','+599','El Caribe','🇨🇼'),
  ('CX','CXR','162','Christmas Island','Île Christmas','Isla de Navidad','+61','Oceanía','🇨🇽'),
  ('CY','CYP','196','Cyprus','Chypre','República de Chipre','+357','Europa','🇨🇾'),
  ('CZ','CZE','203','Czech Republic','Tchéquie','República Checa','+420','Europa','🇨🇿'),
  ('DE','DEU','276','Germany','Allemagne','República Federal de Alemania','+49','Europa','🇩🇪'),
  ('DJ','DJI','262','Djibouti','Djibouti','República de Yibuti','+253','África','🇩🇯'),
  ('DK','DNK','208','Denmark','Danemark','Reino de Dinamarca','+45','Europa','🇩🇰'),
  ('DM','DMA','212','Dominica','Dominique','Commonwealth de Dominica','+1-767','El Caribe','🇩🇲'),
  ('DO','DOM','214','Dominican Republic','République dominicaine','República Dominicana','+1-809','El Caribe','🇩🇴'),
  ('DZ','DZA','012','Algeria','Algérie','República Argelina Democrática y Popular','+213','África','🇩🇿'),
  ('EC','ECU','218','Ecuador','Équateur','República del Ecuador','+593','América del Sur','🇪🇨'),
  ('EE','EST','233','Estonia','Estonie','República de Estonia','+372','Europa','🇪🇪'),
  ('EG','EGY','818','Egypt','Égypte','República Árabe de Egipto','+20','África','🇪🇬'),
  ('EH','ESH','732','Western Sahara','Sahara occidental','Sahara Occidental','+212','África','🇪🇭'),
  ('ER','ERI','232','Eritrea','Érythrée','Estado de Eritrea','+291','África','🇪🇷'),
  ('ES','ESP','724','Spain','Espagne','Reino de España','+34','Europa','🇪🇸'),
  ('ET','ETH','231','Ethiopia','Éthiopie','República Democrática Federal de Etiopía','+251','África','🇪🇹'),
  ('FI','FIN','246','Finland','Finlande','República de Finlandia','+358','Europa','🇫🇮'),
  ('FJ','FJI','242','Fiji','Fidji','República de Fiyi','+679','Oceanía','🇫🇯'),
  ('FK','FLK','238','Falkland Islands','Îles Malouines','Islas Malvinas','+500','América del Sur','🇫🇰'),
  ('FM','FSM','583','Micronesia','Micronésie','Estados Federados de Micronesia','+691','Oceanía','🇫🇲'),
  ('FO','FRO','234','Faroe Islands','Îles Féroé','Islas Feroe','+298','Europa','🇫🇴'),
  ('FR','FRA','250','France','France','República Francesa','+33','Europa','🇫🇷'),
  ('GA','GAB','266','Gabon','Gabon','República Gabonesa','+241','África','🇬🇦'),
  ('GB','GBR','826','United Kingdom','Royaume-Uni','Reino Unido de Gran Bretaña e Irlanda del Norte','+44','Europa','🇬🇧'),
  ('GD','GRD','308','Grenada','Grenade','Granada','+1-473','El Caribe','🇬🇩'),
  ('GE','GEO','268','Georgia','Géorgie','Georgia','+995','Asia Occidental','🇬🇪'),
  ('GF','GUF','254','French Guiana','Guyane française','Guayana Francesa','+594','América del Sur','🇬🇫'),
  ('GG','GGY','831','Guernsey','Guernesey','Guernsey','+44','Europa','🇬🇬'),
  ('GH','GHA','288','Ghana','Ghana','República de Ghana','+233','África','🇬🇭'),
  ('GI','GIB','292','Gibraltar','Gibraltar','Gibraltar','+350','Europa','🇬🇮'),
  ('GL','GRL','304','Greenland','Groenland','Groenlandia','+299','América del Norte','🇬🇱'),
  ('GM','GMB','270','Gambia','Gambie','República de Gambia','+220','África','🇬🇲'),
  ('GN','GIN','324','Guinea','Guinée','República de Guinea','+224','África','🇬🇳'),
  ('GP','GLP','312','Guadeloupe','Guadeloupe','Guadalupe','+590','El Caribe','🇬🇵'),
  ('GQ','GNQ','226','Equatorial Guinea','Guinée équatoriale','República de Guinea Ecuatorial','+240','África','🇬🇶'),
  ('GR','GRC','300','Greece','Grèce','República Helénica','+30','Europa','🇬🇷'),
  ('GS','SGS','239','South Georgia','Géorgie du Sud','Islas Georgias del Sur y Sandwich del Sur','+500','Antártida y Territorios','🇬🇸'),
  ('GT','GTM','320','Guatemala','Guatemala','República de Guatemala','+502','América Central','🇬🇹'),
  ('GU','GUM','316','Guam','Guam','Guam','+1-671','Oceanía','🇬🇺'),
  ('GW','GNB','624','Guinea-Bissau','Guinée-Bissau','República de Guinea-Bisáu','+245','África','🇬🇼'),
  ('GY','GUY','328','Guyana','Guyana','República Cooperativa de Guyana','+592','América del Sur','🇬🇾'),
  ('HK','HKG','344','Hong Kong','Hong Kong','Región Administrativa Especial de Hong Kong','+852','Asia Oriental','🇭🇰'),
  ('HM','HMD','334','Heard Island','Île Heard','Islas Heard y McDonald',NULL,'Antártida y Territorios','🇭🇲'),
  ('HN','HND','340','Honduras','Honduras','República de Honduras','+504','América Central','🇭🇳'),
  ('HR','HRV','191','Croatia','Croatie','República de Croacia','+385','Europa','🇭🇷'),
  ('HT','HTI','332','Haiti','Haïti','República de Haití','+509','El Caribe','🇭🇹'),
  ('HU','HUN','348','Hungary','Hongrie','Hungría','+36','Europa','🇭🇺'),
  ('ID','IDN','360','Indonesia','Indonésie','República de Indonesia','+62','Asia Sudoriental','🇮🇩'),
  ('IE','IRL','372','Ireland','Irlande','Irlanda','+353','Europa','🇮🇪'),
  ('IL','ISR','376','Israel','Israël','Estado de Israel','+972','Oriente Medio','🇮🇱'),
  ('IM','IMN','833','Isle of Man','Île de Man','Isla de Man','+44','Europa','🇮🇲'),
  ('IN','IND','356','India','Inde','República de India','+91','Asia Meridional','🇮🇳'),
  ('IO','IOT','086','British Indian Ocean Territory','Territoire britannique de l''océan Indien','Territorio Británico del Océano Índico','+246','Asia Meridional','🇮🇴'),
  ('IQ','IRQ','368','Iraq','Irak','República de Irak','+964','Oriente Medio','🇮🇶'),
  ('IR','IRN','364','Iran','Iran','República Islámica de Irán','+98','Oriente Medio','🇮🇷'),
  ('IS','ISL','352','Iceland','Islande','Islandia','+354','Europa','🇮🇸'),
  ('IT','ITA','380','Italy','Italie','República Italiana','+39','Europa','🇮🇹'),
  ('JE','JEY','832','Jersey','Jersey','Jersey','+44','Europa','🇯🇪'),
  ('JM','JAM','388','Jamaica','Jamaïque','Jamaica','+1-876','El Caribe','🇯🇲'),
  ('JO','JOR','400','Jordan','Jordanie','Reino Hachemita de Jordania','+962','Oriente Medio','🇯🇴'),
  ('JP','JPN','392','Japan','Japon','Japón','+81','Asia Oriental','🇯🇵'),
  ('KE','KEN','404','Kenya','Kenya','República de Kenia','+254','África','🇰🇪'),
  ('KG','KGZ','417','Kyrgyzstan','Kirghizistan','República Kirguís','+996','Asia Central','🇰🇬'),
  ('KH','KHM','116','Cambodia','Cambodge','Reino de Camboya','+855','Asia Sudoriental','🇰🇭'),
  ('KI','KIR','296','Kiribati','Kiribati','República de Kiribati','+686','Oceanía','🇰🇮'),
  ('KM','COM','174','Comoros','Comores','Unión de las Comoras','+269','África','🇰🇲'),
  ('KN','KNA','659','Saint Kitts and Nevis','Saint-Kitts-et-Nevis','Federación de Saint Kitts y Nevis','+1-869','El Caribe','🇰🇳'),
  ('KP','PRK','408','North Korea','Corée du Nord','República Popular Democrática de Corea','+850','Asia Oriental','🇰🇵'),
  ('KR','KOR','410','South Korea','Corée du Sud','República de Corea','+82','Asia Oriental','🇰🇷'),
  ('KW','KWT','414','Kuwait','Koweït','Estado de Kuwait','+965','Oriente Medio','🇰🇼'),
  ('KY','CYM','136','Cayman Islands','Îles Caïmans','Islas Caimán','+1-345','El Caribe','🇰🇾'),
  ('KZ','KAZ','398','Kazakhstan','Kazakhstan','República de Kazajistán','+7','Asia Central','🇰🇿'),
  ('LA','LAO','418','Laos','Laos','República Democrática Popular Lao','+856','Asia Sudoriental','🇱🇦'),
  ('LB','LBN','422','Lebanon','Liban','República Libanesa','+961','Oriente Medio','🇱🇧'),
  ('LC','LCA','662','Saint Lucia','Sainte-Lucie','Santa Lucía','+1-758','El Caribe','🇱🇨'),
  ('LI','LIE','438','Liechtenstein','Liechtenstein','Principado de Liechtenstein','+423','Europa','🇱🇮'),
  ('LK','LKA','144','Sri Lanka','Sri Lanka','República Democrática Socialista de Sri Lanka','+94','Asia Meridional','🇱🇰'),
  ('LR','LBR','430','Liberia','Libéria','República de Liberia','+231','África','🇱🇷'),
  ('LS','LSO','426','Lesotho','Lesotho','Reino de Lesotho','+266','África','🇱🇸'),
  ('LT','LTU','440','Lithuania','Lituanie','República de Lituania','+370','Europa','🇱🇹'),
  ('LU','LUX','442','Luxembourg','Luxembourg','Gran Ducado de Luxemburgo','+352','Europa','🇱🇺'),
  ('LV','LVA','428','Latvia','Lettonie','República de Letonia','+371','Europa','🇱🇻'),
  ('LY','LBY','434','Libya','Libye','Estado de Libia','+218','África','🇱🇾'),
  ('MA','MAR','504','Morocco','Maroc','Reino de Marruecos','+212','África','🇲🇦'),
  ('MC','MCO','492','Monaco','Monaco','Principado de Mónaco','+377','Europa','🇲🇨'),
  ('MD','MDA','498','Moldova','Moldavie','República de Moldova','+373','Europa','🇲🇩'),
  ('ME','MNE','499','Montenegro','Monténégro','Montenegro','+382','Europa','🇲🇪'),
  ('MF','MAF','663','Saint Martin','Saint-Martin','Colectividad de San Martín','+590','El Caribe','🇲🇫'),
  ('MG','MDG','450','Madagascar','Madagascar','República de Madagascar','+261','África','🇲🇬'),
  ('MH','MHL','584','Marshall Islands','Îles Marshall','República de las Islas Marshall','+692','Oceanía','🇲🇭'),
  ('MK','MKD','807','North Macedonia','Macédoine du Nord','República de Macedonia del Norte','+389','Europa','🇲🇰'),
  ('ML','MLI','466','Mali','Mali','República de Malí','+223','África','🇲🇱'),
  ('MM','MMR','104','Myanmar','Myanmar','República de la Unión de Myanmar','+95','Asia Sudoriental','🇲🇲'),
  ('MN','MNG','496','Mongolia','Mongolie','Mongolia','+976','Asia Oriental','🇲🇳'),
  ('MO','MAC','446','Macao','Macao','Región Administrativa Especial de Macao','+853','Asia Oriental','🇲🇴'),
  ('MP','MNP','580','Northern Mariana Islands','Îles Mariannes du Nord','Islas Marianas del Norte','+1-670','Oceanía','🇲🇵'),
  ('MQ','MTQ','474','Martinique','Martinique','Martinica','+596','El Caribe','🇲🇶'),
  ('MR','MRT','478','Mauritania','Mauritanie','República Islámica de Mauritania','+222','África','🇲🇷'),
  ('MS','MSR','500','Montserrat','Montserrat','Montserrat','+1-664','El Caribe','🇲🇸'),
  ('MT','MLT','470','Malta','Malte','República de Malta','+356','Europa','🇲🇹'),
  ('MU','MUS','480','Mauritius','Maurice','República de Mauricio','+230','África','🇲🇺'),
  ('MV','MDV','462','Maldives','Maldives','República de Maldivas','+960','Asia Meridional','🇲🇻'),
  ('MW','MWI','454','Malawi','Malawi','República de Malaui','+265','África','🇲🇼'),
  ('MX','MEX','484','Mexico','Mexique','Estados Unidos Mexicanos','+52','América del Norte','🇲🇽'),
  ('MY','MYS','458','Malaysia','Malaisie','Malasia','+60','Asia Sudoriental','🇲🇾'),
  ('MZ','MOZ','508','Mozambique','Mozambique','República de Mozambique','+258','África','🇲🇿'),
  ('NA','NAM','516','Namibia','Namibie','República de Namibia','+264','África','🇳🇦'),
  ('NC','NCL','540','New Caledonia','Nouvelle-Calédonie','Nueva Caledonia','+687','Oceanía','🇳🇨'),
  ('NE','NER','562','Niger','Niger','República del Níger','+227','África','🇳🇪'),
  ('NF','NFK','574','Norfolk Island','Île Norfolk','Isla Norfolk','+672','Oceanía','🇳🇫'),
  ('NG','NGA','566','Nigeria','Nigéria','República Federal de Nigeria','+234','África','🇳🇬'),
  ('NI','NIC','558','Nicaragua','Nicaragua','República de Nicaragua','+505','América Central','🇳🇮'),
  ('NL','NLD','528','Netherlands','Pays-Bas','Reino de los Países Bajos','+31','Europa','🇳🇱'),
  ('NO','NOR','578','Norway','Norvège','Reino de Noruega','+47','Europa','🇳🇴'),
  ('NP','NPL','524','Nepal','Népal','República Democrática Federal de Nepal','+977','Asia Meridional','🇳🇵'),
  ('NR','NRU','520','Nauru','Nauru','República de Nauru','+674','Oceanía','🇳🇷'),
  ('NU','NIU','570','Niue','Niue','Niue','+683','Oceanía','🇳🇺'),
  ('NZ','NZL','554','New Zealand','Nouvelle-Zélande','Nueva Zelanda','+64','Oceanía','🇳🇿'),
  ('OM','OMN','512','Oman','Oman','Sultanato de Omán','+968','Oriente Medio','🇴🇲'),
  ('PA','PAN','591','Panama','Panama','República de Panamá','+507','América Central','🇵🇦'),
  ('PE','PER','604','Peru','Pérou','República del Perú','+51','América del Sur','🇵🇪'),
  ('PF','PYF','258','French Polynesia','Polynésie française','Polinesia Francesa','+689','Oceanía','🇵🇫'),
  ('PG','PNG','598','Papua New Guinea','Papouasie-Nouvelle-Guinée','Estado Independiente de Papúa Nueva Guinea','+675','Oceanía','🇵🇬'),
  ('PH','PHL','608','Philippines','Philippines','República de Filipinas','+63','Asia Sudoriental','🇵🇭'),
  ('PK','PAK','586','Pakistan','Pakistan','República Islámica de Pakistán','+92','Asia Meridional','🇵🇰'),
  ('PL','POL','616','Poland','Pologne','República de Polonia','+48','Europa','🇵🇱'),
  ('PM','SPM','666','Saint Pierre and Miquelon','Saint-Pierre-et-Miquelon','San Pedro y Miquelón','+508','América del Norte','🇵🇲'),
  ('PN','PCN','612','Pitcairn Islands','Îles Pitcairn','Islas Pitcairn','+64','Oceanía','🇵🇳'),
  ('PR','PRI','630','Puerto Rico','Porto Rico','Estado Libre Asociado de Puerto Rico','+1-787','El Caribe','🇵🇷'),
  ('PS','PSE','275','Palestine','Territoire palestinien','Estado de Palestina','+970','Oriente Medio','🇵🇸'),
  ('PT','PRT','620','Portugal','Portugal','República Portuguesa','+351','Europa','🇵🇹'),
  ('PW','PLW','585','Palau','Palaos','República de Palaos','+680','Oceanía','🇵🇼'),
  ('PY','PRY','600','Paraguay','Paraguay','República del Paraguay','+595','América del Sur','🇵🇾'),
  ('QA','QAT','634','Qatar','Qatar','Estado de Catar','+974','Oriente Medio','🇶🇦'),
  ('RE','REU','638','Réunion','La Réunion','Reunión','+262','África','🇷🇪'),
  ('RO','ROU','642','Romania','Roumanie','Rumanía','+40','Europa','🇷🇴'),
  ('RS','SRB','688','Serbia','Serbie','República de Serbia','+381','Europa','🇷🇸'),
  ('RU','RUS','643','Russia','Russie','Federación de Rusia','+7','Europa','🇷🇺'),
  ('RW','RWA','646','Rwanda','Rwanda','República de Ruanda','+250','África','🇷🇼'),
  ('SA','SAU','682','Saudi Arabia','Arabie saoudite','Reino de Arabia Saudita','+966','Oriente Medio','🇸🇦'),
  ('SB','SLB','090','Solomon Islands','Îles Salomon','Islas Salomón','+677','Oceanía','🇸🇧'),
  ('SC','SYC','690','Seychelles','Seychelles','República de Seychelles','+248','África','🇸🇨'),
  ('SD','SDN','729','Sudan','Soudan','República del Sudán','+249','África','🇸🇩'),
  ('SE','SWE','752','Sweden','Suède','Reino de Suecia','+46','Europa','🇸🇪'),
  ('SG','SGP','702','Singapore','Singapour','República de Singapur','+65','Asia Sudoriental','🇸🇬'),
  ('SH','SHN','654','Saint Helena','Sainte-Hélène','Santa Elena, Ascensión y Tristán de Acuña','+290','África','🇸🇭'),
  ('SI','SVN','705','Slovenia','Slovénie','República de Eslovenia','+386','Europa','🇸🇮'),
  ('SJ','SJM','744','Svalbard and Jan Mayen','Svalbard et Jan Mayen','Svalbard y Jan Mayen','+47','Europa','🇸🇯'),
  ('SK','SVK','703','Slovakia','Slovaquie','República Eslovaca','+421','Europa','🇸🇰'),
  ('SL','SLE','694','Sierra Leone','Sierra Leone','República de Sierra Leona','+232','África','🇸🇱'),
  ('SM','SMR','674','San Marino','Saint-Marin','República de San Marino','+378','Europa','🇸🇲'),
  ('SN','SEN','686','Senegal','Sénégal','República de Senegal','+221','África','🇸🇳'),
  ('SO','SOM','706','Somalia','Somalie','República Federal de Somalia','+252','África','🇸🇴'),
  ('SR','SUR','740','Suriname','Suriname','República de Surinam','+597','América del Sur','🇸🇷'),
  ('SS','SSD','728','South Sudan','Soudan du Sud','República de Sudán del Sur','+211','África','🇸🇸'),
  ('ST','STP','678','São Tomé and Príncipe','Sao Tomé-et-Principe','República Democrática de Santo Tomé y Príncipe','+239','África','🇸🇹'),
  ('SV','SLV','222','El Salvador','El Salvador','República de El Salvador','+503','América Central','🇸🇻'),
  ('SX','SXM','534','Sint Maarten','Saint-Martin','Sint Maarten','+1-721','El Caribe','🇸🇽'),
  ('SY','SYR','760','Syria','Syrie','República Árabe Siria','+963','Oriente Medio','🇸🇾'),
  ('SZ','SWZ','748','Eswatini','Eswatini','Reino de Esuatini','+268','África','🇸🇿'),
  ('TC','TCA','796','Turks and Caicos Islands','Îles Turks-et-Caïcos','Islas Turcas y Caicos','+1-649','El Caribe','🇹🇨'),
  ('TD','TCD','148','Chad','Tchad','República del Chad','+235','África','🇹🇩'),
  ('TF','ATF','260','French Southern Territories','Terres australes françaises','Tierras Australes y Antárticas Francesas',NULL,'Antártida y Territorios','🇹🇫'),
  ('TG','TGO','768','Togo','Togo','República Togolesa','+228','África','🇹🇬'),
  ('TH','THA','764','Thailand','Thaïlande','Reino de Tailandia','+66','Asia Sudoriental','🇹🇭'),
  ('TJ','TJK','762','Tajikistan','Tadjikistan','República de Tayikistán','+992','Asia Central','🇹🇯'),
  ('TK','TKL','772','Tokelau','Tokelau','Tokelau','+690','Oceanía','🇹🇰'),
  ('TL','TLS','626','Timor-Leste','Timor oriental','República Democrática de Timor Oriental','+670','Asia Sudoriental','🇹🇱'),
  ('TM','TKM','795','Turkmenistan','Turkménistan','Turkmenistán','+993','Asia Central','🇹🇲'),
  ('TN','TUN','788','Tunisia','Tunisie','República de Túnez','+216','África','🇹🇳'),
  ('TO','TON','776','Tonga','Tonga','Reino de Tonga','+676','Oceanía','🇹🇴'),
  ('TR','TUR','792','Türkiye','Turquie','República de Turquía','+90','Europa','🇹🇷'),
  ('TT','TTO','780','Trinidad and Tobago','Trinité-et-Tobago','República de Trinidad y Tobago','+1-868','El Caribe','🇹🇹'),
  ('TV','TUV','798','Tuvalu','Tuvalu','Tuvalu','+688','Oceanía','🇹🇻'),
  ('TW','TWN','158','Taiwan','Taïwan','Taiwán','+886','Asia Oriental','🇹🇼'),
  ('TZ','TZA','834','Tanzania','Tanzanie','República Unida de Tanzania','+255','África','🇹🇿'),
  ('UA','UKR','804','Ukraine','Ukraine','Ucrania','+380','Europa','🇺🇦'),
  ('UG','UGA','800','Uganda','Ouganda','República de Uganda','+256','África','🇺🇬'),
  ('UM','UMI','581','U.S. Minor Outlying Islands','Îles mineures des États-Unis','Islas Ultramarinas Menores de los Estados Unidos','+1','Oceanía','🇺🇲'),
  ('US','USA','840','United States','États-Unis','Estados Unidos de América','+1','América del Norte','🇺🇸'),
  ('UY','URY','858','Uruguay','Uruguay','República Oriental del Uruguay','+598','América del Sur','🇺🇾'),
  ('UZ','UZB','860','Uzbekistan','Ouzbékistan','República de Uzbekistán','+998','Asia Central','🇺🇿'),
  ('VA','VAT','336','Vatican City','Vatican','Estado de la Ciudad del Vaticano','+379','Europa','🇻🇦'),
  ('VC','VCT','670','Saint Vincent and the Grenadines','Saint-Vincent-et-les-Grenadines','San Vicente y las Granadinas','+1-784','El Caribe','🇻🇨'),
  ('VE','VEN','862','Venezuela','Venezuela','República Bolivariana de Venezuela','+58','América del Sur','🇻🇪'),
  ('VG','VGB','092','British Virgin Islands','Îles Vierges britanniques','Islas Vírgenes Británicas','+1-284','El Caribe','🇻🇬'),
  ('VI','VIR','850','U.S. Virgin Islands','Îles Vierges américaines','Islas Vírgenes de los Estados Unidos','+1-340','El Caribe','🇻🇮'),
  ('VN','VNM','704','Vietnam','Viêt Nam','República Socialista de Vietnam','+84','Asia Sudoriental','🇻🇳'),
  ('VU','VUT','548','Vanuatu','Vanuatu','República de Vanuatu','+678','Oceanía','🇻🇺'),
  ('WF','WLF','876','Wallis and Futuna','Wallis-et-Futuna','Wallis y Futuna','+681','Oceanía','🇼🇫'),
  ('WS','WSM','882','Samoa','Samoa','Estado Independiente de Samoa','+685','Oceanía','🇼🇸'),
  ('XK',NULL,NULL,'Kosovo','Kosovo','República de Kosovo','+383','Europa','🇽🇰'),
  ('YE','YEM','887','Yemen','Yémen','República de Yemen','+967','Oriente Medio','🇾🇪'),
  ('YT','MYT','175','Mayotte','Mayotte','Mayotte','+262','África','🇾🇹'),
  ('ZA','ZAF','710','South Africa','Afrique du Sud','República de Sudáfrica','+27','África','🇿🇦'),
  ('ZM','ZMB','894','Zambia','Zambie','República de Zambia','+260','África','🇿🇲'),
  ('ZW','ZWE','716','Zimbabwe','Zimbabwe','República de Zimbabue','+263','África','🇿🇼')
) AS d(alpha2, alpha3, num, en, fr, oficial, tel, region, emoji)
WHERE p.clave_pais = d.alpha2;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. POBLAR ISO 3166-2 + ABREVIATURA DE LOS 32 ESTADOS DE MÉXICO
--    clave_estado = código INEGI (01-32)
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE ades_estados AS e SET
  abreviatura = d.abr,
  iso_3166_2  = d.iso2
FROM (VALUES
  ('01','AGS', 'MX-AGU'),
  ('02','BCN', 'MX-BCN'),
  ('03','BCS', 'MX-BCS'),
  ('04','CAM', 'MX-CAM'),
  ('05','COAH','MX-COA'),
  ('06','COL', 'MX-COL'),
  ('07','CHIS','MX-CHP'),
  ('08','CHIH','MX-CHH'),
  ('09','CDMX','MX-CMX'),
  ('10','DGO', 'MX-DUR'),
  ('11','GTO', 'MX-GUA'),
  ('12','GRO', 'MX-GRO'),
  ('13','HGO', 'MX-HID'),
  ('14','JAL', 'MX-JAL'),
  ('15','MEX', 'MX-MEX'),
  ('16','MICH','MX-MIC'),
  ('17','MOR', 'MX-MOR'),
  ('18','NAY', 'MX-NAY'),
  ('19','NL',  'MX-NLE'),
  ('20','OAX', 'MX-OAX'),
  ('21','PUE', 'MX-PUE'),
  ('22','QRO', 'MX-QUE'),
  ('23','QROO','MX-ROO'),
  ('24','SLP', 'MX-SLP'),
  ('25','SIN', 'MX-SIN'),
  ('26','SON', 'MX-SON'),
  ('27','TAB', 'MX-TAB'),
  ('28','TAMPS','MX-TAM'),
  ('29','TLAX','MX-TLA'),
  ('30','VER', 'MX-VER'),
  ('31','YUC', 'MX-YUC'),
  ('32','ZAC', 'MX-ZAC')
) AS d(clave, abr, iso2)
WHERE e.clave_estado = d.clave;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. ÍNDICES
-- ─────────────────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_paises_iso_alpha3  ON ades_paises (iso_alpha_3);
CREATE INDEX IF NOT EXISTS idx_paises_iso_numeric ON ades_paises (iso_numeric);
CREATE INDEX IF NOT EXISTS idx_paises_nombre_en   ON ades_paises (nombre_en);
CREATE INDEX IF NOT EXISTS idx_paises_region      ON ades_paises (region);

CREATE INDEX IF NOT EXISTS idx_estados_iso_3166_2 ON ades_estados (iso_3166_2);

COMMIT;
