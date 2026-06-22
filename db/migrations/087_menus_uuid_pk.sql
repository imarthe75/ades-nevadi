-- ============================================================================
-- 087_menus_uuid_pk.sql
-- Migra ades_menus.id (y la auto-referencia parent_id, más ades_menu_roles.menu_id)
-- de INTEGER/serial -> UUID, eliminando el último IDENTITY/serial del esquema.
--
-- Corrige además un bug latente: el BFF (MenusController.MenuNode.id/parentId son
-- UUID y MenusQueryService hace (UUID) row.get("id"/"parent_id")) ya asume UUID;
-- con la columna INTEGER el endpoint /mi-menu lanzaría ClassCastException en cuanto
-- ades_menu_roles tuviera filas. Esta migración alinea la BD con el código.
--
-- Estrategia (pocas filas, FK auto-ref + menu_roles): columnas UUID puente, mapeo
-- del padre por self-join, recrear PK y FKs, eliminar secuencia. Transaccional.
-- ============================================================================
BEGIN;

-- 1. Columnas UUID puente
ALTER TABLE ades_menus       ADD COLUMN id_uuid      uuid NOT NULL DEFAULT uuidv7();
ALTER TABLE ades_menus       ADD COLUMN parent_uuid  uuid;
ALTER TABLE ades_menu_roles  ADD COLUMN menu_uuid    uuid;

-- 2. Mapear auto-referencia padre->hijo y la FK de menu_roles
UPDATE ades_menus c
   SET parent_uuid = p.id_uuid
  FROM ades_menus p
 WHERE c.parent_id = p.id;

UPDATE ades_menu_roles mr
   SET menu_uuid = m.id_uuid
  FROM ades_menus m
 WHERE mr.menu_id = m.id;

-- 3. Soltar FKs, PK y defaults viejos
ALTER TABLE ades_menu_roles DROP CONSTRAINT ades_menu_roles_menu_id_fkey;
ALTER TABLE ades_menus      DROP CONSTRAINT ades_menus_parent_id_fkey;
ALTER TABLE ades_menus      DROP CONSTRAINT ades_menus_pkey;
ALTER TABLE ades_menus      ALTER COLUMN id DROP DEFAULT;

-- 4. Eliminar columnas INTEGER y renombrar las UUID a su nombre canónico
ALTER TABLE ades_menu_roles DROP COLUMN menu_id;
ALTER TABLE ades_menus      DROP COLUMN parent_id;
ALTER TABLE ades_menus      DROP COLUMN id;

ALTER TABLE ades_menus      RENAME COLUMN id_uuid     TO id;
ALTER TABLE ades_menus      RENAME COLUMN parent_uuid TO parent_id;
ALTER TABLE ades_menu_roles RENAME COLUMN menu_uuid   TO menu_id;

-- 5. Recrear PK + FKs + NOT NULL
ALTER TABLE ades_menus      ADD CONSTRAINT ades_menus_pkey PRIMARY KEY (id);
ALTER TABLE ades_menus      ADD CONSTRAINT ades_menus_parent_id_fkey
      FOREIGN KEY (parent_id) REFERENCES ades_menus(id);
ALTER TABLE ades_menu_roles ALTER COLUMN menu_id SET NOT NULL;
ALTER TABLE ades_menu_roles ADD CONSTRAINT ades_menu_roles_menu_id_fkey
      FOREIGN KEY (menu_id) REFERENCES ades_menus(id) ON DELETE CASCADE;

-- 6. Eliminar la secuencia huérfana (último serial del esquema)
DROP SEQUENCE IF EXISTS ades_menus_id_seq;

COMMENT ON COLUMN ades_menus.id IS 'PK UUID v7 (migrado desde serial en 087)';

COMMIT;
