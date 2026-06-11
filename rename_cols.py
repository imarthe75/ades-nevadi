import os

def process_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Simple text replacements
    new_content = content.replace('fccreacion', 'fecha_creacion')
    new_content = new_content.replace('fcmodificacion', 'fecha_modificacion')
    
    if new_content != content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f"Updated: {filepath}")

def process_dir(directory):
    for root, dirs, files in os.walk(directory):
        # Exclude hidden directories, node_modules, venv, pycache
        dirs[:] = [d for d in dirs if not d.startswith('.') and d not in ('node_modules', 'venv', '__pycache__')]
        for file in files:
            if file.endswith('.py') or file.endswith('.sql'):
                process_file(os.path.join(root, file))

if __name__ == "__main__":
    process_dir("/opt/ades/backend")
    process_dir("/opt/ades/db/migrations")
    process_dir("/opt/ades/db/seeds")
    process_dir("/opt/ades/docs")
    process_dir("/opt/ades/spec")
    print("Renaming complete.")
