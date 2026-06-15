ADES BFF (Spring Boot)

Scaffold inicial para el Backend-for-Frontend de Instituto Nevadi.

Run (dev):

mvn spring-boot:run

Construir imagen Docker:

docker build -t ades-bff:local .

Notas:
- Configurar variables en `src/main/resources/application.yml` o en variables de entorno.
- Este scaffold incluye seguridad OIDC como resource-server (JWT) apuntando a Authentik.
