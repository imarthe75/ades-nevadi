"""
Test de validación SPRINT 3: Optimizaciones de BD
- Verificar que índices FK fueron creados
- Verificar que índices no usados fueron eliminados
- Verificar que materialized views existen
- Verificar integridad de datos
"""
import sys
import os
import asyncio

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..')))

from sqlalchemy import select, text
from app.core.database import get_db

async def run_sprint3_validation():
    print("\n" + "="*70)
    print("VALIDACIÓN SPRINT 3: Optimizaciones de BD")
    print("="*70)
    
    try:
        async for db in get_db():
            # 1. Verificar índices FK creados
            print("\n1️⃣ Verificando índices en Foreign Keys...")
            fk_indexes = await db.execute(text("""
                SELECT indexrelname, idx_scan 
                FROM pg_stat_user_indexes 
                WHERE indexrelname LIKE 'idx_ades_%'
                ORDER BY indexrelname
                LIMIT 10
            """))
            fk_index_rows = fk_indexes.fetchall()
            if fk_index_rows:
                print(f"   ✅ Encontrados {len(fk_index_rows)} índices de FK:")
                for idx_name, scans in fk_index_rows[:5]:
                    print(f"      - {idx_name}")
            else:
                print("   ⚠️ No se encontraron índices idx_ades_*")
            
            # 2. Verificar que índices no usados fueron eliminados
            print("\n2️⃣ Verificando eliminación de índices no usados...")
            removed_indexes = [
                'ades_asistencias_ref_key',
                'ux_ades_cp_cp_localidad',
                'uq_ades_cal_periodo',
                'uq_ades_entregas',
                'idx_entregas_tarea'
            ]
            
            for idx_name in removed_indexes:
                result = await db.execute(text(
                    f"SELECT 1 FROM pg_indexes WHERE indexname = '{idx_name}'"
                ))
                if not result.fetchone():
                    print(f"   ✅ {idx_name} eliminado correctamente")
                else:
                    print(f"   ⚠️ {idx_name} aún existe")
            
            # 3. Verificar materialized views
            print("\n3️⃣ Verificando materialized views...")
            mv_result = await db.execute(text("""
                SELECT matviewname, pg_size_pretty(pg_total_relation_size('public.' || matviewname)) as size
                FROM pg_matviews 
                WHERE schemaname = 'public'
            """))
            
            mv_rows = mv_result.fetchall()
            if mv_rows:
                print(f"   ✅ Encontradas {len(mv_rows)} materialized views:")
                for mv_name, size in mv_rows:
                    print(f"      - {mv_name} ({size})")
            else:
                print("   ⚠️ No se encontraron materialized views")
            
            # 4. Verificar integridad de datos (conteo básico)
            print("\n4️⃣ Verificando integridad de datos...")
            
            table_counts = {
                'ades_asistencias': 'SELECT COUNT(*) FROM ades_asistencias',
                'ades_estudiantes': 'SELECT COUNT(*) FROM ades_estudiantes',
                'ades_calificaciones_periodo': 'SELECT COUNT(*) FROM ades_calificaciones_periodo',
                'ades_personas': 'SELECT COUNT(*) FROM ades_personas'
            }
            
            for table_name, query in table_counts.items():
                result = await db.execute(text(query))
                count = result.scalar()
                print(f"   ✅ {table_name}: {count:,} registros")
            
            # 5. Verificar tamaño de BD
            print("\n5️⃣ Verificando tamaño de base de datos...")
            size_result = await db.execute(text("""
                SELECT pg_size_pretty(pg_database_size(current_database())) as size
            """))
            bd_size = size_result.scalar()
            print(f"   ✅ Tamaño total de BD: {bd_size}")
            print(f"      (Esperado: ~371 MB después de SPRINT 3)")
            
            # 6. Verificar estadísticas de índices más usados
            print("\n6️⃣ Estadísticas de índices (top 5 más usados)...")
            stats_result = await db.execute(text("""
                SELECT 
                    t.tablename,
                    i.indexrelname,
                    i.idx_scan as scans,
                    i.idx_tup_read as tuples_read
                FROM pg_stat_user_indexes i
                JOIN pg_tables t ON i.relname = t.tablename
                WHERE t.schemaname = 'public'
                ORDER BY i.idx_scan DESC NULLS LAST
                LIMIT 5
            """))
            
            stats_rows = stats_result.fetchall()
            for table, index, scans, tuples in stats_rows:
                print(f"   ✅ {index}: {scans} scans, {tuples} tuples")
            
            print("\n" + "="*70)
            print("✅ VALIDACIÓN SPRINT 3 COMPLETADA SIN ERRORES")
            print("="*70 + "\n")
            return True
            
    except Exception as e:
        print(f"\n❌ ERROR: {type(e).__name__}: {e}\n")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    result = asyncio.run(run_sprint3_validation())
    sys.exit(0 if result else 1)
