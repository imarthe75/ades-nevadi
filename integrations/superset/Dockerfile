FROM apache/superset:6.1.0
USER root
RUN . /app/.venv/bin/activate && \
    uv pip install \
    psycopg2-binary==2.9.10 \
    redis==5.2.1 \
    authlib==1.3.2
# Custom security manager y config disponibles en /app/pythonpath/
COPY custom_sso_security_manager.py /app/pythonpath/custom_sso_security_manager.py
COPY superset_config.py /app/pythonpath/superset_config.py

# Aprovisionar script de inicialización
COPY docker-init.sh /app/docker-init.sh
USER root
RUN chmod +x /app/docker-init.sh
USER superset

ENTRYPOINT ["/app/docker-init.sh"]
