"""
Celery Task: Automatic Weekly SEPOMEX updates.
Downloads the latest ZIP from Correos de México, extracts CPdescarga.txt,
and performs an idempotent batch insert/update of geographic catalogs.
"""
from __future__ import annotations

import io
import os
import re
import logging
import zipfile
import psycopg2
import psycopg2.extras
import requests
import uuid
from celery import shared_task

from app.core.config import settings

log = logging.getLogger(__name__)

@shared_task(name="app.worker.tasks.sepomex.sync_sepomex_weekly")
def sync_sepomex_weekly() -> dict:
    """
    Downloads, extracts, and seeds the latest SEPOMEX ZIP database.
    Runs weekly via celery-beat on Sundays.
    """
    url = 'https://www.correosdemexico.gob.mx/SSLServicios/ConsultaCP/CodigoPostal_Exportar.aspx'
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
    }
    
    log.info("Starting weekly SEPOMEX sync from Correos de México...")
    
    # 1. Fetch ASP.NET view state variables using regex
    try:
        session = requests.Session()
        r_get = session.get(url, headers=headers, verify=False, timeout=30)
        r_get.raise_for_status()
        
        # Regex search for ASP.NET fields
        vs_match = re.search(r'id="__VIEWSTATE"\s+value="([^"]+)"', r_get.text)
        vsg_match = re.search(r'id="__VIEWSTATEGENERATOR"\s+value="([^"]+)"', r_get.text)
        ev_match = re.search(r'id="__EVENTVALIDATION"\s+value="([^"]+)"', r_get.text)
        
        if not vs_match or not vsg_match or not ev_match:
            # Try alternate input layout
            vs_match = re.search(r'name="__VIEWSTATE"\s+id="__VIEWSTATE"\s+value="([^"]+)"', r_get.text)
            vsg_match = re.search(r'name="__VIEWSTATEGENERATOR"\s+id="__VIEWSTATEGENERATOR"\s+value="([^"]+)"', r_get.text)
            ev_match = re.search(r'name="__EVENTVALIDATION"\s+id="__EVENTVALIDATION"\s+value="([^"]+)"', r_get.text)
            
        if not vs_match or not vsg_match or not ev_match:
            raise ValueError("Could not extract ASP.NET view state variables from download page.")
            
        vs = vs_match.group(1)
        vsg = vsg_match.group(1)
        ev = ev_match.group(1)
        
        payload = {
            '__EVENTTARGET': '',
            '__EVENTARGUMENT': '',
            '__LASTFOCUS': '',
            '__VIEWSTATE': vs,
            '__VIEWSTATEGENERATOR': vsg,
            '__EVENTVALIDATION': ev,
            'cboEdo': '00',          # All states
            'rblTipo': 'txt',        # TXT format
            'btnDescarga.x': '15',   # ASP.NET image coordinates
            'btnDescarga.y': '15'
        }
        
        log.info("Submitting POST request to download SEPOMEX ZIP...")
        r_post = session.post(url, data=payload, headers=headers, verify=False, timeout=60)
        r_post.raise_for_status()
        
        if "CPdescargatxt" not in r_post.headers.get('Content-Disposition', ''):
            raise ValueError("Response headers do not contain CPdescargatxt ZIP file.")
            
        # 2. Extract ZIP contents
        zip_file = zipfile.ZipFile(io.BytesIO(r_post.content))
        txt_filename = None
        for name in zip_file.namelist():
            if name.endswith('.txt'):
                txt_filename = name
                break
                
        if not txt_filename:
            raise ValueError("No .txt file found inside SEPOMEX ZIP archive.")
            
        txt_content = zip_file.read(txt_filename).decode('latin-1')
        log.info("SEPOMEX file extracted successfully. Processing lines...")
        
        # 3. Parse and seed into database
        conn = psycopg2.connect(settings.DATABASE_URL_SYNC)
        cur = conn.cursor()
        
        # Load existing states and municipalities maps
        cur.execute("SELECT id, clave_estado FROM public.ades_estados;")
        estados_map = {row[1]: row[0] for row in cur.fetchall()}
        
        cur.execute("SELECT id, estado_id, clave_municipio FROM public.ades_municipios;")
        municipios_map = {(row[1], row[2]): row[0] for row in cur.fetchall()}
        
        lines = txt_content.splitlines()
        if len(lines) < 3:
            raise ValueError("SEPOMEX text file is empty or corrupted.")
            
        # Detect copyright notice (ignore first line if it doesn't contain headers)
        start_idx = 2 if 'd_codigo' not in lines[0] else 1
        
        tipos_asentamiento_set = set()
        
        for line in lines[start_idx:]:
            parts = line.strip().split('|')
            if len(parts) < 12:
                continue
            c_tipo_asenta = parts[10].strip()
            d_tipo_asenta = parts[2].strip()
            if c_tipo_asenta and d_tipo_asenta:
                tipos_asentamiento_set.add((c_tipo_asenta, d_tipo_asenta))
                
        # Insert types of settlement
        for clave, nombre in tipos_asentamiento_set:
            cur.execute("""
                INSERT INTO public.ades_tipos_asentamiento (clave_tipo, nombre_tipo)
                VALUES (%s, %s)
                ON CONFLICT (clave_tipo) DO UPDATE SET nombre_tipo = EXCLUDED.nombre_tipo;
            """, (clave, nombre))
        conn.commit()
        
        cur.execute("SELECT id, clave_tipo FROM public.ades_tipos_asentamiento;")
        tipos_map = {row[1]: row[0] for row in cur.fetchall()}
        
        # Track existing localities
        localidades_cache = {}
        cur.execute("SELECT id, municipio_id, UPPER(nombre_localidad) FROM public.ades_localidades;")
        for row in cur.fetchall():
            localidades_cache[(row[1], row[2])] = row[0]
            
        localidades_to_insert = []
        cps_to_insert = []
        
        for line in lines[start_idx:]:
            parts = line.strip().split('|')
            if len(parts) < 13:
                continue
                
            codigo_postal = parts[0].strip()
            d_asenta = parts[1].strip()
            c_estado = parts[7].strip()
            c_mnpio = parts[11].strip()
            c_tipo_asenta = parts[10].strip()
            
            estado_uuid = estados_map.get(c_estado)
            if not estado_uuid:
                continue
                
            municipio_uuid = municipios_map.get((estado_uuid, c_mnpio))
            if not municipio_uuid:
                continue
                
            tipo_asent_uuid = tipos_map.get(c_tipo_asenta)
            
            loc_key = (municipio_uuid, d_asenta.upper())
            loc_uuid = localidades_cache.get(loc_key)
            if not loc_uuid:
                loc_uuid = str(uuid.uuid4())
                localidades_cache[loc_key] = loc_uuid
                localidades_to_insert.append((loc_uuid, d_asenta, municipio_uuid, tipo_asent_uuid))
                
            cps_to_insert.append((str(uuid.uuid4()), codigo_postal, loc_uuid, municipio_uuid, estado_uuid, tipo_asent_uuid))
            
        # Bulk inserts
        if localidades_to_insert:
            psycopg2.extras.execute_values(
                cur,
                """
                INSERT INTO public.ades_localidades (id, nombre_localidad, municipio_id, tipo_asentamiento_id)
                VALUES %s ON CONFLICT DO NOTHING;
                """,
                localidades_to_insert,
                page_size=1000
            )
            conn.commit()
            
        if cps_to_insert:
            psycopg2.extras.execute_values(
                cur,
                """
                INSERT INTO public.ades_codigos_postales (id, codigo_postal, localidad_id, municipio_id, estado_id, tipo_asentamiento_id)
                VALUES %s ON CONFLICT (codigo_postal, localidad_id) DO NOTHING;
                """,
                cps_to_insert,
                page_size=2000
            )
            conn.commit()
            
        cur.close()
        conn.close()
        
        log.info("SEPOMEX sync completed. Localities loaded: %d, ZIP codes loaded: %d", len(localidades_to_insert), len(cps_to_insert))
        return {
            "estado": "ok",
            "localidades_nuevas": len(localidades_to_insert),
            "codigos_postales_nuevos": len(cps_to_insert)
        }
    except Exception as e:
        log.error("Failed to run weekly SEPOMEX sync: %s", str(e))
        return {"estado": "error", "message": str(e)}
