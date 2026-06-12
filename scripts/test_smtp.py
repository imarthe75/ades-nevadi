#!/usr/bin/env python3
# =============================================================================
# ADES - Script de Prueba de SMTP
# Carga las credenciales del archivo .env y envía un correo electrónico de prueba.
# Diagnostica la conexión a Zoho Mail o cualquier servidor SMTP configurado.
# =============================================================================

import os
import smtplib
import ssl
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart

# Directorio base y carga de .env
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
env_path = os.path.join(BASE_DIR, '.env')

def cargar_env():
    if not os.path.exists(env_path):
        print(f"ERROR: No se encontró el archivo .env en {env_path}")
        return False
    
    with open(env_path, 'r') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            key_val = line.split('=', 1)
            if len(key_val) == 2:
                key, val = key_val
                os.environ[key.strip()] = val.strip()
    return True

def enviar_prueba():
    if not cargar_env():
        return

    # Obtener configuración
    host = os.getenv("SMTP_HOST", "smtp.zoho.com")
    port_str = os.getenv("SMTP_PORT", "465")
    username = os.getenv("SMTP_USERNAME")
    password = os.getenv("SMTP_PASSWORD")
    sender = os.getenv("SMTP_FROM", username)
    dest = username  # Enviar a uno mismo para la prueba

    print("--- Configuración detectada ---")
    print(f"SMTP Host: {host}")
    print(f"SMTP Port: {port_str}")
    print(f"Sender: {sender}")
    print(f"User: {username}")
    print("-------------------------------")

    if not username or not password:
        print("ERROR: SMTP_USERNAME o SMTP_PASSWORD no están configurados en el .env")
        return

    port = int(port_str)

    # Crear el mensaje
    msg = MIMEMultipart()
    msg['From'] = sender
    msg['To'] = dest
    msg['Subject'] = "ADES - Prueba de Conexión SMTP"
    
    body = """
    Hola,
    
    Este es un correo automático de prueba generado por el script de diagnóstico de ADES.
    Si has recibido este mensaje, significa que la configuración SMTP (Zoho Mail) en el 
    archivo .env es completamente funcional.
    
    Saludos,
    Equipo de Desarrollo ADES
    """
    msg.attach(MIMEText(body, 'plain'))

    # Configuración de seguridad SSL vs TLS
    use_ssl = os.getenv("SMTP_USE_SSL", "true").lower() == "true"
    
    try:
        print("Iniciando conexión con el servidor SMTP...")
        
        if use_ssl or port == 465:
            # SSL Directo
            context = ssl.create_default_context()
            print(f"Conectando vía SSL directo a {host}:{port}...")
            with smtplib.SMTP_SSL(host, port, context=context) as server:
                print("Autenticándose...")
                server.login(username, password)
                print("Enviando correo de prueba...")
                server.sendmail(sender, dest, msg.as_string())
        else:
            # STARTTLS
            print(f"Conectando vía STARTTLS a {host}:{port}...")
            with smtplib.SMTP(host, port) as server:
                server.ehlo()
                if port == 587 or os.getenv("SMTP_USE_TLS", "false").lower() == "true":
                    print("Iniciando STARTTLS...")
                    server.starttls()
                    server.ehlo()
                print("Autenticándose...")
                server.login(username, password)
                print("Enviando correo de prueba...")
                server.sendmail(sender, dest, msg.as_string())
                
        print("✓ ¡Correo de prueba enviado con éxito!")
        print(f"Destinatario: {dest}")
        
    except Exception as e:
        print(f"❌ ERROR al enviar correo: {e}")

if __name__ == "__main__":
    enviar_prueba()
