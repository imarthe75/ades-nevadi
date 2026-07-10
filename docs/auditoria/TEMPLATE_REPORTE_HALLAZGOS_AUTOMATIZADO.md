# 📋 TEMPLATE: REPORTE DE HALLAZGOS AUTOMATIZADO

**Instrucciones:** Duplica esta plantilla para cada hallazgo. Llena todas las secciones. El auditor NO escribe código, solo documenta.

---

## [HALLAZGO-ID] [Título Conciso]

**Fecha Detección:** YYYY-MM-DD  
**Auditor:** [Nombre]  
**Sistema Auditado:** [Nombre sistema]  

---

### 📌 Clasificación

| Atributo | Valor |
|----------|-------|
| **Severidad** | 🔴 Crítica / 🟠 Alta / 🟡 Media / 🟢 Baja |
| **Categoría** | Seguridad / Rendimiento / Lógica / Mantenibilidad |
| **Componente Afectado** | [Archivo/Endpoint/Tabla específico] |
| **CVE/CWE** | [CVE-YYYY-XXXXX / CWE-XXXX] si aplica |
| **Normativa Aplicable** | [OWASP A01 / GDPR Art. 32 / ISO27001 A.10] |

---

### 1. 📝 Descripción Detallada

#### 1.1 ¿Qué es el problema?
[2-3 líneas explicando qué está mal de forma objetiva, sin alarmismo]

**Ejemplo Malo:** "El sistema es inseguro"  
**Ejemplo Bueno:** "Las contraseñas se almacenan en texto plano en la tabla `users.password`, permitiendo que cualquiera con acceso a BD lea las contraseñas en texto sin cifrar"

#### 1.2 ¿Por qué es un problema?
[Explica la cadena de causalidad: Problema → Riesgo → Consecuencia]

**Estructura:**
- **Causa Raíz:** [Qué código/configuración causa esto]
- **Escenario de Riesgo:** [Cómo se explota o falla]
- **Consecuencia:** [Qué sucede si no se arregla]

#### 1.3 ¿A quién afecta?
[Usuarios, datos, operaciones específicamente impactadas]

---

### 2. 🧪 Evidencia (Reproducibilidad Garantizada)

#### 2.1 Código/Configuración Problemática

```[lenguaje]
// Ubicación exacta: archivo.ext:linea

// Código que causa el problema
function guardarUsuario(usuario) {
  const query = "INSERT INTO users (password) VALUES ('" + usuario.password + "')";
  // ^ PROBLEMA: Concatenación SQL + contraseña en texto plano
}
```

#### 2.2 Pasos para Reproducir

```
1. Abrir navegador y acceder a https://system.local/admin
2. Inspeccionar Network tab
3. Observar: Sin header "Strict-Transport-Security" en respuesta
4. Conclusión: HSTS no configurado
```

#### 2.3 Comando Exacto para Verificar

```bash
# Ejemplo 1: Verificar header faltante
curl -I https://api.example.com/endpoint | grep "Strict-Transport-Security"
# Resultado esperado: (vacío = vulnerable)

# Ejemplo 2: Detectar N+1 queries
SELECT query, calls FROM pg_stat_statements WHERE calls > 100 ORDER BY calls DESC;
# Resultado esperado: 50+ queries similares con diferentes IDs

# Ejemplo 3: Probar IDOR
curl -H "Authorization: Bearer TOKEN_USUARIO_A" \
  https://api.example.com/api/usuarios/ID_USUARIO_B
# Resultado esperado: 403 Forbidden (correcto) vs 200 OK (vulnerable)
```

#### 2.4 Output/Respuesta Observada

```json
{
  "status": 200,
  "data": {
    "userId": "uuid-usuario-b",
    "email": "usuario-b@example.com",
    "phone": "+34611223344"
  },
  "problema": "Usuario A puede acceder a datos de Usuario B sin autorización"
}
```

#### 2.5 Captura de Pantalla / Log (si aplica)

[Pegar screenshot de DevTools, stack trace, o log relevante]

---

### 3. 💥 Impacto

#### 3.1 Impacto Técnico

| Aspecto | Descripción | Magnitud |
|---------|-------------|----------|
| **Confidencialidad** | Acceso no autorizado a datos PII | ❌ Crítica |
| **Integridad** | Modificación sin autorización | ✅ No aplica |
| **Disponibilidad** | Riesgo de DoS | ⚠️ Potencial |
| **Performance** | API timeout tras 30s | ❌ Crítica |

#### 3.2 Impacto de Negocio

| Aspecto | Consecuencia |
|---------|-------------|
| **Cumplimiento Normativo** | Violación GDPR Art. 32 - Pérdida de datos |
| **Reputación** | Breach = pérdida de confianza de usuarios |
| **Usuarios Impactados** | 3,483 usuarios activos × acceso no autorizado |
| **Costo Potencial** | GDPR multa: hasta €20M o 4% de facturación anual |
| **Tiempo de Detección** | Desconocido - sin logs de acceso |
| **Tiempo de Remediación Esperado** | 2-4 semanas de implementación |

#### 3.3 CVSS Score (si aplica)

```
CVSS v3.1: 7.5 (High)
Vector: CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N
Interpretación: Network accessible, sin autenticación, acceso sin límite a datos confidenciales
```

---

### 4. ✅ Recomendación de Solución (Sin Código)

#### 4.1 Estrategia Recomendada

**Opción A: [Nombre Short]**
- Ventaja: [+]
- Desventaja: [-]
- Esfuerzo: [Bajo/Medio/Alto]

**Opción B: [Nombre Short]** (Recomendada)
- Ventaja: [+]
- Desventaja: [-]
- Esfuerzo: [Bajo/Medio/Alto]

#### 4.2 Pasos Generales (NO es código)

1. **Análisis:** Identificar todos los endpoints que retornan datos de usuario
2. **Implementación:** Agregar validación de autorización en cada endpoint
3. **Validación:** Verificar que usuario A NO puede acceder a Usuario B
4. **Testing:** Crear test automatizado que falla si se reintroduce la vuln
5. **Documentación:** Actualizar política de seguridad en API docs

#### 4.3 Tecnologías/Patterns Sugeridos

- **Para IDOR:** Patrón "resource-based access control" o Spring `@PreAuthorize`
- **Para N+1:** EntityGraph (JPA), eager loading, batching
- **Para Missing Index:** Análisis EXPLAIN ANALYZE + índices compuestos
- **Para Secrets:** Vault de HashiCorp, rotación automática de credentials

#### 4.4 Testing & Verificación (Cómo validar la corrección)

```
✓ Usuario A intenta acceder a Usuario B → 403 Forbidden
✓ Usuario A accede a su propio Usuario A → 200 OK con datos
✓ Admin puede acceder a cualquier Usuario → 200 OK
✓ Audit log registra intentos no autorizados
✓ SonarQube: 0 vulnerabilidades IDOR detectadas
```

---

### 5. 📊 Estimación

| Aspecto | Valor |
|---------|-------|
| **Esfuerzo de Corrección** | Bajo (<4h) / Medio (4-16h) / Alto (>16h) |
| **Complejidad Técnica** | Baja / Media / Alta |
| **Riesgo de Regresión** | Bajo / Medio / Alto |
| **Testing Requerido** | Unit / Integration / E2E |
| **Cambios en API** | Sí / No |
| **Cambios en DB Schema** | Sí / No |

---

### 6. 🎯 Criterio de Aceptación

La solución se considera **TERMINADA** cuando:

- [ ] Código revisado por 2 reviewers
- [ ] Test automatizado creado y pasando
- [ ] Validación manual en staging completa
- [ ] No hay regresiones en suite de tests existente
- [ ] Documentación actualizada
- [ ] Auditor verifica la corrección

---

### 7. 📚 Referencias & Contexto

**OWASP/CWE:**
- [OWASP A01 - BOLA](https://owasp.org/Top10/A01_2021-Broken_Access_Control/)
- [CWE-639: Authorization Bypass](https://cwe.mitre.org/data/definitions/639.html)

**Estándares Aplicables:**
- GDPR Art. 32 (Seguridad de datos personales)
- ISO 27001 A.9 (Control de acceso)

**Documentación Interna:**
- [Guía de Seguridad ADES](link-to-internal-doc)
- [API Security Best Practices](link)

---

### 8. 📝 Notas Adicionales

[Cualquier contexto, limitaciones, o consideraciones especiales]

**Ejemplo:**
- "Este hallazgo solo aplica en modo de producción; en desarrollo puede ser aceptable"
- "La solución depende de la migración a Spring 6.0 (TASK-2024-001)"
- "Requiere coordinación con equipo de DevOps para rotación de secrets"

---

### 9. ✍️ Seguimiento

| Fecha | Estado | Responsable | Notas |
|-------|--------|-------------|-------|
| 2026-07-08 | 🔴 Abierto | [Nombre] | Hallazgo detectado |
| 2026-07-10 | 🟡 En Progreso | [Dev 1] | PR #234 abierto |
| 2026-07-15 | 🟢 Corregido | [Dev 1] | PR #234 merged |
| 2026-07-16 | ✅ Verificado | [Auditor] | Verificado en staging |

---

## 📋 CHECKLIST DEL AUDITOR (Llenar antes de enviar)

- [ ] Descripción es objetiva (sin adjetivos alarmistas)
- [ ] Evidencia es reproducible (comandos exactos incluidos)
- [ ] Impacto está cuantificado (no vago)
- [ ] Recomendación es implementable (sin código)
- [ ] Severidad es consistente con impacto
- [ ] Referencias a estándares son correctas
- [ ] No hay información sensible expuesta en el reporte
- [ ] Revisor técnico ha validado el hallazgo

---

**Versión del Template:** 2.0  
**Última actualización:** 2026-07-08  
**Creador:** [Tu nombre/equipo]
