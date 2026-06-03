#!/usr/bin/env python3
import os
import shutil
import subprocess

# Directorios de origen
FRAMEWORK_DIR = "/home/ia/ecosistema-casmarts/resident-agent-framework"
PARENT_DIR = "/home/ia/ecosistema-casmarts"

# Archivos y carpetas comunes a copiar
ITEMS_TO_COPY = [
    ".agent",
    "agents",
    "rules",
    "skills",
    "contexts",
    "hooks",
    "resident_agent_genesis.md",
    "HEURISTICAS_MASTER_GUIDE.md",
    "auditoria.sql"
]

# Excepciones que no deben recibir copias
EXCLUDED_DIR_NAMES = {
    "resident-agent-framework",
    ".git",
    ".vscode",
    ".antigravitycli",
    ".agent",
    ".claude",
    "CASmartS",
    "data"
}

def distribute():
    print("🚀 Iniciando distribución del Resident Agent Framework...")
    
    # Listar subdirectorios que sean repositorios Git válidos
    subdirs = [
        d for d in os.listdir(PARENT_DIR)
        if os.path.isdir(os.path.join(PARENT_DIR, d)) 
        and os.path.exists(os.path.join(PARENT_DIR, d, ".git"))
        and d not in EXCLUDED_DIR_NAMES
    ]
    
    print(f"Detectados {len(subdirs)} repositorios Git objetivos.")
    
    for subdir in subdirs:
        target_path = os.path.join(PARENT_DIR, subdir)
        print(f"\n──────────────────────────────────────────────────")
        print(f"📦 Procesando: {subdir} ({target_path})")
        is_git = True
            
        for item in ITEMS_TO_COPY:
            source_item_path = os.path.join(FRAMEWORK_DIR, item)
            target_item_path = os.path.join(target_path, item)
            
            if not os.path.exists(source_item_path):
                continue
                
            # Tratamiento especial para CONTEXT.md y MAP.md para no borrar información de proyectos específicos
            if item == ".agent":
                os.makedirs(target_item_path, exist_ok=True)
                for file_name in os.listdir(source_item_path):
                    src_file = os.path.join(source_item_path, file_name)
                    tgt_file = os.path.join(target_item_path, file_name)
                    
                    if os.path.exists(tgt_file):
                        print(f"   ℹ️  Conservando {file_name} existente en {subdir}")
                    else:
                        shutil.copy2(src_file, tgt_file)
            else:
                # Copia de carpetas y archivos genéricos
                if os.path.isdir(source_item_path):
                    if os.path.exists(target_item_path):
                        shutil.rmtree(target_item_path)
                    shutil.copytree(source_item_path, target_item_path)
                else:
                    shutil.copy2(source_item_path, target_item_path)
                    
        print(f"   ✅ Archivos copiados exitosamente.")
        
        # Ejecutar git add en el destino
        if is_git:
            try:
                subprocess.run(
                    ["git", "add", "."],
                    cwd=target_path,
                    check=True,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL
                )
                print(f"   ✅ Archivos agregados al stage de Git.")
            except Exception as e:
                print(f"   ❌ Error al ejecutar git add en {subdir}: {e}")

    print("\n🎉 Distribución completada de forma exitosa.")

if __name__ == "__main__":
    distribute()
