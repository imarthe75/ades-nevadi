-- Migración 050: Añadir nacionalidad a contactos_familiares
-- Permite registrar la nacionalidad de padres/tutores/contactos de emergencia

ALTER TABLE ades_contactos_familiares
  ADD COLUMN IF NOT EXISTS nacionalidad VARCHAR(50) DEFAULT 'Mexicana';

COMMENT ON COLUMN ades_contactos_familiares.nacionalidad
  IS 'Nacionalidad del contacto/padre/tutor, del catálogo ades_paises';

SELECT auditoria.asignar_biu('public.ades_contactos_familiares');
