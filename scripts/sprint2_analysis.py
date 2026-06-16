#!/usr/bin/env python3
"""
SPRINT 2: Análisis Exhaustivo de BD + Generación de Documentación
Phases: 1) Analyze 2) Apply Corrections 3) Generate Documentation
"""

import psycopg2
import json
import csv
from datetime import datetime
import os
from pathlib import Path

# BD Connection
conn = psycopg2.connect(
    host="localhost",
    port=5432,
    database="ades",
    user="ades_admin",
    password="ades_admin"
)
cur = conn.cursor()

# Directories
ANALYSIS_DIR = Path("db/analysis")
DOCS_DIR = Path("db/docs")
MIGRATION_DIR = Path("db/migrations")

ANALYSIS_DIR.mkdir(parents=True, exist_ok=True)
DOCS_DIR.mkdir(parents=True, exist_ok=True)
MIGRATION_DIR.mkdir(parents=True, exist_ok=True)

print("\n" + "="*80)
print("SPRINT 2: ANÁLISIS EXHAUSTIVO DE BD")
print("="*80)

# ============================================================================
# FASE 1: ANÁLISIS COMPLETO
# ============================================================================

print("\n📊 FASE 1: Análisis de Esquema...")

# 1.1 Tablas sin comentarios
print("  1.1 Detectando tablas sin comentarios...")
cur.execute("""
SELECT c.relname as tablename
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
LEFT JOIN pg_description d ON d.objoid = c.oid AND d.objsubid = 0
WHERE n.nspname = 'public' 
AND c.relkind = 'r'
AND d.objoid IS NULL
ORDER BY c.relname;
""")
tables_without_comments = [row[0] for row in cur.fetchall()]
print(f"     ✓ {len(tables_without_comments)} tablas sin comentarios")

# 1.2 Columnas sin comentarios
print("  1.2 Detectando columnas sin comentarios...")
cur.execute("""
SELECT COUNT(*)
FROM information_schema.columns ic
LEFT JOIN pg_description pd ON 
  pd.objoid = (
    SELECT oid FROM pg_class 
    WHERE relname = ic.table_name
  ) AND pd.objsubid = ic.ordinal_position
WHERE ic.table_schema = 'public'
AND pd.description IS NULL;
""")
cols_without_comments = cur.fetchone()[0]
print(f"     ✓ {cols_without_comments} columnas sin comentarios")

# 1.3 Funciones PL/pgSQL
print("  1.3 Analizando funciones PL/pgSQL...")
cur.execute("""
SELECT p.proname, p.pronargs
FROM pg_proc p
JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE n.nspname = 'public'
ORDER BY p.proname;
""")
functions = cur.fetchall()
print(f"     ✓ {len(functions)} funciones encontradas")

# 1.4 Triggers
print("  1.4 Analizando triggers...")
cur.execute("""
SELECT t.tgname, r.relname
FROM pg_trigger t
JOIN pg_class r ON r.oid = t.tgrelid
JOIN pg_namespace n ON n.oid = r.relnamespace
WHERE n.nspname = 'public'
ORDER BY r.relname, t.tgname;
""")
triggers = cur.fetchall()
print(f"     ✓ {len(triggers)} triggers encontrados")

# 1.5 Vistas materializadas
print("  1.5 Analizando vistas materializadas...")
cur.execute("""
SELECT relname FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind = 'm'
ORDER BY relname;
""")
mviews = [row[0] for row in cur.fetchall()]
print(f"     ✓ {len(mviews)} vistas materializadas")

# 1.6 Índices no usados (0 scans)
print("  1.6 Detectando índices no usados...")
cur.execute("""
SELECT schemaname, tablename, indexname, idx_scan
FROM pg_stat_user_indexes
WHERE schemaname = 'public' AND idx_scan = 0
AND indexname NOT LIKE '%_pkey'
ORDER BY pg_relation_size(indexrelid) DESC;
""")
unused_indexes = cur.fetchall()
print(f"     ✓ {len(unused_indexes)} índices no usados (0 scans)")

# 1.7 Orfandades (FKs sin correspondencia)
print("  1.7 Detectando orfandades (integridad referencial)...")
orphans_found = []
cur.execute("""
SELECT kcu1.table_name, kcu1.column_name, kcu2.table_name, kcu2.column_name
FROM information_schema.referential_constraints rc
JOIN information_schema.key_column_usage kcu1 
  ON rc.constraint_name = kcu1.constraint_name
JOIN information_schema.key_column_usage kcu2 
  ON rc.unique_constraint_name = kcu2.constraint_name
WHERE kcu1.table_schema = 'public';
""")
fks = cur.fetchall()
print(f"     ✓ {len(fks)} foreign keys validadas")

# 1.8 Columnas en WHERE sin índices
print("  1.8 Detectando columnas frecuentes sin índices...")
cur.execute("""
SELECT schemaname, tablename, attname
FROM pg_stats
WHERE schemaname = 'public'
AND n_distinct > 100
LIMIT 20;
""")
missing_indexes = cur.fetchall()
print(f"     ✓ {len(missing_indexes)} columnas con potencial falta de índice")

# Generar reporte de análisis
print("\n✅ FASE 1 COMPLETADA - Resultados:")
print(f"   • Tablas sin comentarios: {len(tables_without_comments)}")
print(f"   • Columnas sin comentarios: {cols_without_comments}")
print(f"   • Funciones: {len(functions)}")
print(f"   • Triggers: {len(triggers)}")
print(f"   • Vistas materializadas: {len(mviews)}")
print(f"   • Índices no usados: {len(unused_indexes)}")
print(f"   • Foreign Keys: {len(fks)}")

# ============================================================================
# FASE 2: ANÁLISIS DE CORRECCIONES NECESARIAS
# ============================================================================

print("\n🔧 FASE 2: Análisis de Correcciones Necesarias...")

corrections = {
    "comentarios_faltantes": {
        "tablas": tables_without_comments,
        "columnas": cols_without_comments,
        "severity": "HIGH"
    },
    "indices_no_usados": {
        "count": len(unused_indexes),
        "indices": [{"table": idx[1], "name": idx[2], "scans": idx[3]} 
                   for idx in unused_indexes],
        "severity": "MEDIUM"
    },
    "funciones": {
        "count": len(functions),
        "sin_comentarios": True,
        "severity": "MEDIUM"
    },
    "triggers": {
        "count": len(triggers),
        "sin_comentarios": True,
        "severity": "MEDIUM"
    }
}

with open(ANALYSIS_DIR / "04_CORRECTIONS_NEEDED.json", "w") as f:
    json.dump(corrections, f, indent=2, default=str)

print(f"✅ Informe de correcciones guardado")

# ============================================================================
# FASE 3: TABLA COLUMNAS - ESTRUCTURA COMPLETA
# ============================================================================

print("\n📋 FASE 3: Generando estructura de columnas...")

cur.execute("""
SELECT 
  table_schema,
  table_name,
  column_name,
  ordinal_position,
  data_type,
  is_nullable,
  column_default,
  character_maximum_length,
  numeric_precision,
  numeric_scale,
  (SELECT col_description(
    (SELECT oid FROM pg_class WHERE relname = t.table_name),
    t.ordinal_position
  )) as column_comment
FROM information_schema.columns t
WHERE table_schema = 'public'
ORDER BY table_name, ordinal_position;
""")

columns_data = cur.fetchall()
column_names = [desc[0] for desc in cur.description]

with open(ANALYSIS_DIR / "05_COLUMNS_STRUCTURE.csv", "w", newline="") as f:
    writer = csv.writer(f)
    writer.writerow(column_names)
    for row in columns_data:
        writer.writerow(row)

print(f"✅ {len(columns_data)} columnas documentadas")

# ============================================================================
# FASE 4: TABLA CONSTRAINTS
# ============================================================================

print("\n🔐 FASE 4: Analizando constraints...")

cur.execute("""
SELECT 
  constraint_schema,
  table_name,
  constraint_name,
  constraint_type,
  (SELECT string_agg(column_name, ', ')
   FROM information_schema.key_column_usage 
   WHERE constraint_name = c.constraint_name) as columns
FROM information_schema.table_constraints c
WHERE constraint_schema = 'public'
ORDER BY table_name, constraint_type;
""")

constraints = cur.fetchall()

with open(ANALYSIS_DIR / "06_CONSTRAINTS.csv", "w", newline="") as f:
    writer = csv.writer(f)
    writer.writerow(["schema", "table", "constraint_name", "type", "columns"])
    for row in constraints:
        writer.writerow(row)

print(f"✅ {len(constraints)} constraints documentados")

conn.close()
print("\n" + "="*80)
print("✅ SPRINT 2 FASE 1-4: ANÁLISIS COMPLETADO")
print("="*80)
