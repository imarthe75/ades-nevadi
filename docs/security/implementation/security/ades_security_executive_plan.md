# 📅 PLAN EJECUTIVO DE SEGURIDAD ADES NEVADI
**Período**: 19 Junio - 30 Agosto 2026 (12 semanas)  
**Objetivo**: Llevar postura de seguridad de 6.5/10 → 9/10  
**Inversión**: ~480-600 horas desarrollo + validación

---

## 🎯 GOAL STATEMENT

**Reducir vulnerabilidades críticas de 13 a 2 en 12 semanas, implementando controles OWASP, STRIDE, NIST y Compliance.**

---

## 📊 ROADMAP DETALLADO

### SEMANA 1-2: CÓDIGO CRÍTICO (Junio 19 - Julio 3)
**Focus**: Vulnerabilidades OWASP A01, STRIDE S/T/E

```
Sprint 1.1 — IDOR Prevention (Days 1-3)
├─ Fix expediente.py IDOR validation      [6h]
├─ Audit todos endpoints POST/PUT/DELETE   [8h]
├─ Create test_idor.py suite              [4h]
├─ Deploy a staging + test                [4h]
└─ Code review + approval                 [2h]
   Total: 24h | Team: 1 Sr Dev + 1 QA

Sprint 1.2 — HTTPS + Headers (Days 3-4)
├─ Add HTTPSRedirectMiddleware             [2h]
├─ Add security headers middleware         [3h]
├─ Configure nginx HSTS headers            [2h]
├─ Test redirect + headers                 [2h]
└─ Deploy to production                    [1h]
   Total: 10h | Team: 1 Sr Dev + DevOps

Sprint 1.3 — Rate Limiting (Days 5-7)
├─ Install slowapi + integration           [4h]
├─ Protect login/expediente endpoints      [4h]
├─ Configure limits per endpoint           [3h]
├─ Test rate limiting behavior             [3h]
├─ Monitor 429 responses in production     [2h]
└─ Tune limits based on real usage         [2h]
   Total: 18h | Team: 1 Sr Dev + 1 DevOps

Sprint 1.4 — Testing & Validation (Days 7-10)
├─ Run STRIDE validation tests             [4h]
├─ OWASP A01 compliance tests              [4h]
├─ Manual penetration test (basic)         [6h]
├─ Document findings                       [3h]
└─ Stakeholder presentation                [2h]
   Total: 19h | Team: 1 Security architect + 2 testers

WEEK 1-2 TOTAL: 71h
DELIVERABLES: 
  ✅ IDOR fix + tests deployed
  ✅ HTTPS enforced in prod
  ✅ Rate limiting active
  ✅ Security report
```

---

### SEMANA 3-4: DATA PROTECTION (Julio 4-17)
**Focus**: OWASP A02, GDPR/LFPDPPP, STRIDE I

```
Sprint 2.1 — PII Encryption (Days 1-5)
├─ Audit PII fields in schema              [6h]
├─ Create encryption module                [6h]
├─ Add encryption property decorators      [8h]
├─ Create migration script                 [6h]
├─ Test encryption/decryption              [4h]
└─ Backup data before migration            [2h]
   Total: 32h | Team: 1 Sr Dev + 1 DBA

Sprint 2.2 — Compliance Framework (Days 5-10)
├─ Draft privacy policy (es/en)            [8h]
├─ Data processing agreement               [6h]
├─ DPIA assessment                         [8h]
├─ Map GDPR/LFPDPPP requirements           [6h]
├─ Legal review (externa)                  [4h]
└─ Update terms of service                 [4h]
   Total: 36h | Team: 1 Legal + 1 PM

Sprint 2.3 — Database Hardening (Days 10-14)
├─ Design RLS policies                     [8h]
├─ Implement RLS for key tables            [10h]
├─ Create audit triggers                   [6h]
├─ Test RLS bypass prevention              [6h]
├─ Document policies per role              [4h]
└─ Deploy to staging                       [2h]
   Total: 36h | Team: 1 DBA + 1 Sr Dev

WEEK 3-4 TOTAL: 104h
DELIVERABLES:
  ✅ PII encrypted in database
  ✅ Privacy policy published
  ✅ RLS policies implemented
  ✅ Compliance roadmap
```

---

### SEMANA 5-6: SDLC SECURITY (Julio 18-31)
**Focus**: NIST PROTECT, SDLC improvements

```
Sprint 3.1 — CI/CD Security (Days 1-5)
├─ Create pre-commit config                [6h]
├─ Integrate Bandit (SAST)                 [4h]
├─ Integrate Safety (dep scanning)         [4h]
├─ Create GitHub Actions workflow          [8h]
├─ Setup SAST/DAST reporting               [6h]
└─ Train team on new checks                [2h]
   Total: 30h | Team: 1 DevOps + 1 Sr Dev

Sprint 3.2 — Audit & Logging (Days 5-10)
├─ Implement before/after snapshots        [8h]
├─ Add PII access audit logs               [6h]
├─ Create log retention policy              [4h]
├─ Setup log aggregation (ELK/Splunk)      [8h]
├─ Create alerting rules                   [6h]
└─ Test alert effectiveness                [3h]
   Total: 35h | Team: 1 DevOps + 1 Sr Dev

Sprint 3.3 — Threat Modeling (Days 10-14)
├─ Create data flow diagrams               [6h]
├─ Update threat model (DFD-based)         [8h]
├─ Security architecture review            [6h]
├─ Document control mappings               [4h]
└─ Risk register update                    [4h]
   Total: 28h | Team: 1 Security Architect

WEEK 5-6 TOTAL: 93h
DELIVERABLES:
  ✅ CI/CD security pipeline
  ✅ Audit logging complete
  ✅ Threat model updated
```

---

### SEMANA 7-9: INFRASTRUCTURE & INCIDENT RESPONSE (Agosto 1-22)
**Focus**: NIST DETECT/RESPOND, infrastructure hardening

```
Sprint 4.1 — Network Security (Days 1-7)
├─ Implement WAF rules                     [10h]
├─ Configure DDoS protection                [6h]
├─ Network segmentation audit              [8h]
├─ Implement network policies              [8h]
├─ Test network isolation                  [6h]
└─ Documentation                           [4h]
   Total: 42h | Team: 1 Network/Security architect

Sprint 4.2 — Monitoring & Detection (Days 7-14)
├─ Setup SIEM (Wazuh/ELK)                  [16h]
├─ Create detection rules                  [12h]
├─ Setup alerting & escalation             [8h]
├─ Dashboard creation                      [8h]
├─ Alert tuning & testing                  [8h]
└─ Team training                           [4h]
   Total: 56h | Team: 1 Sr DevOps + 2 analysts

Sprint 4.3 — Incident Response (Days 14-21)
├─ Draft incident response plan            [12h]
├─ Create runbooks                         [12h]
├─ Communication protocols                 [8h]
├─ Escalation procedures                   [6h]
├─ Tabletop exercise (simulation)          [8h]
└─ Team training                           [6h]
   Total: 52h | Team: 1 Security Lead + team

WEEK 7-9 TOTAL: 150h
DELIVERABLES:
  ✅ Incident response plan
  ✅ SIEM operational
  ✅ Network hardened
```

---

### SEMANA 10-12: TESTING & DEPLOYMENT (Agosto 23-Septiembre 5)
**Focus**: Validation, testing, production readiness

```
Sprint 5.1 — Security Testing (Days 1-5)
├─ External pentesting                     [40h] *external*
├─ DAST full scan                          [8h]
├─ Vulnerability remediation               [20h]
├─ Regression testing                      [12h]
└─ Documentation                           [4h]
   Total: 84h | Team: External + internal

Sprint 5.2 — Compliance Validation (Days 5-10)
├─ GDPR compliance checklist               [8h]
├─ LFPDPPP compliance audit                [8h]
├─ SEP/UAEMEX validation                   [6h]
├─ ISO 27001 readiness assessment          [8h]
├─ Remediation of gaps                     [12h]
└─ Final approval                          [4h]
   Total: 46h | Team: Legal/Compliance + tech

Sprint 5.3 — Production Deployment (Days 10-12)
├─ Production deployment plan              [6h]
├─ Blue-green deployment setup             [8h]
├─ Rollback procedures                     [4h]
├─ Monitoring setup                        [6h]
├─ Health checks                           [4h]
├─ Post-deployment validation              [8h]
└─ Team debriefing                         [2h]
   Total: 38h | Team: Full DevOps/Eng team

WEEK 10-12 TOTAL: 168h
DELIVERABLES:
  ✅ External penetration test results
  ✅ Compliance certifications initiated
  ✅ Production deployment successful
  ✅ Security posture 9/10
```

---

## 👥 ESTRUCTURA DE EQUIPO

### Equipo Principal (Full-time)

| Rol | Count | Responsabilidades |
|-----|-------|---|
| **Security Architect** | 1 | Threat modeling, roadmap, architecture review |
| **Sr Backend Developer** | 2 | Code fixes, IDOR, encryption, logging |
| **Sr DevOps/Infrastructure** | 1 | Nginx, Vault, CI/CD, monitoring |
| **Database Administrator** | 1 | RLS, encryption, audit, performance |
| **QA/Test Engineer** | 2 | Test automation, penetration testing |
| **Project Manager** | 0.5 | Coordination, timeline, stakeholders |

### Soporte Especializado (Contracted)

| Especialidad | Duration | Cost Estimate |
|---|---|---|
| **External Pentesting** | 2 weeks | $8-15k |
| **Legal/Compliance Review** | 4 weeks (part-time) | $2-4k |
| **SIEM Setup & Training** | 2 weeks | $3-5k |
| **External Security Audit** | 1 week | $4-8k |

### Total Team Cost Estimate
```
Full-time team (12 weeks): ~$80-120k
External services: ~$20-35k
Training & tools: ~$5-10k
─────────────────────────
TOTAL: ~$105-165k
```

---

## 📈 GANTT CHART

```
WEEK    1  2  3  4  5  6  7  8  9  10 11 12
─────────────────────────────────────────────
CODE    ███████
DATA        ███████
SDLC            ███████
INFRA               ████████████
TESTING                          ███████
DEPLOY                                 ████

Legend:
███ = Sprint active
─ = Planning/gaps
```

---

## 💰 PRESUPUESTO DESGLOSADO

### Gastos de Personal (480-600 horas)

```
Security Architect (1 x 80h @ $100/h):     $8,000
Sr Backend Dev (2 x 160h @ $80/h):        $25,600
Sr DevOps (1 x 100h @ $90/h):              $9,000
DBA (1 x 80h @ $85/h):                     $6,800
QA Engineers (2 x 100h @ $60/h):          $12,000
PM (0.5 x 40h @ $85/h):                    $3,400
─────────────────────────────────
SUBTOTAL Personal:                        $64,800
```

### Gastos de Servicios & Herramientas

```
External Pentesting:                      $12,000
Legal/Compliance Review:                   $3,000
SIEM Setup (Wazuh/ELK):                    $4,000
Security Audit External:                   $6,000
Training & Certifications:                 $3,000
Software Licenses (Vault, WAF, etc):       $5,000
─────────────────────────────────
SUBTOTAL Servicios:                       $33,000
```

### TOTAL PRESUPUESTO: $97,800

---

## 🎯 HITOS Y ENTREGABLES

### Hito 1: CRÍTICA (Semana 2)
- ✅ IDOR fixed in production
- ✅ HTTPS enforced
- ✅ Rate limiting active
- ✅ Security report

### Hito 2: DATA PROTECTION (Semana 4)
- ✅ PII encrypted
- ✅ Privacy policy published
- ✅ RLS deployed
- ✅ Compliance roadmap

### Hito 3: SDLC SECURITY (Semana 6)
- ✅ CI/CD security checks automated
- ✅ Threat model updated
- ✅ Audit logging complete

### Hito 4: INFRASTRUCTURE (Semana 9)
- ✅ SIEM operational
- ✅ Incident response plan ready
- ✅ Network hardened

### Hito 5: VALIDATION (Semana 12)
- ✅ External pentest complete
- ✅ Compliance certifications
- ✅ Security posture 9/10+

---

## 📊 SUCCESS METRICS

### Baseline → Target

| Métrica | Baseline | Target | Semana |
|---------|----------|--------|--------|
| OWASP A01 Issues | 3 | 0 | 2 |
| OWASP A02 Issues | 5 | 1 | 4 |
| STRIDE Critical | 13 | 2 | 6 |
| Dependency Vulns | ? | 0 | 6 |
| Test Coverage | ~70% | 85%+ | 9 |
| SAST Failures | ? | <5 | 6 |
| DAST Findings | ? | <10 | 12 |
| Compliance Score | 40% | 85% | 12 |
| Incident Response Plan | ❌ | ✅ | 9 |
| RLS Enabled | 0% | 100% | 4 |

---

## 🚀 GO-LIVE CHECKLIST

### Pre-Production (Week 11)
- [ ] All code fixes reviewed & approved
- [ ] Penetration test completed & remediated
- [ ] Compliance audit passed
- [ ] Incident response plan approved
- [ ] Team training completed
- [ ] Rollback procedures documented
- [ ] Monitoring dashboards ready

### Production Deployment (Week 12)
- [ ] Database migration executed (RLS, encryption)
- [ ] Code deployment verified
- [ ] HTTPS certificates valid
- [ ] Rate limiting thresholds tuned
- [ ] Monitoring alerts active
- [ ] Support team briefed

### Post-Deployment (Week 12+)
- [ ] Health checks passing
- [ ] No security alerts
- [ ] Compliance team sign-off
- [ ] Incident response simulation
- [ ] Metrics trending correctly

---

## ⚠️ RIESGOS Y MITIGACIÓN

| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|---|---|---|
| Schedule slippage | MEDIA | ALTA | Weekly standup, prioritization |
| External pentest delays | BAJA | MEDIA | Book early, parallel work |
| Encryption migration issues | BAJA | CRÍTICA | Full backup, test env first |
| Team availability | MEDIA | ALTA | Cross-training, documentation |
| Compliance misinterpretation | BAJA | ALTA | Legal review, external audit |
| Production incident during deployment | BAJA | CRÍTICA | Blue-green, rollback plan |

---

## 📋 PRÓXIMOS PASOS

### HOY (Junio 19)
- [ ] Revisar plan con stakeholders
- [ ] Asignar equipo
- [ ] Crear tickets en Jira/GitHub
- [ ] Schedule kickoff meeting

### MAÑANA (Junio 20)
- [ ] Sprint 1.1 kickoff
- [ ] IDOR fix development starts
- [ ] Pre-commit hooks implementation

### SEMANA 1
- [ ] Sprint 1.1 complete
- [ ] Deploy IDOR fix to staging
- [ ] Start HTTPS implementation

---

## 📞 ESCALATION & GOVERNANCE

### Weekly Status
- Security team + dev leads
- 30 min standup
- Risk updates
- Blockers

### Bi-weekly Reviews
- Stakeholder update
- Budget/timeline review
- Risk assessment
- Deliverable signoff

### Critical Escalation Path
1. **Dev Team** → Security Architect (4h)
2. **Security Architect** → Tech Lead (24h)
3. **Tech Lead** → CTO (1 business day)
4. **CTO** → Director (2 business days)

---

## 📚 DOCUMENTATION REQUIREMENTS

All work must include:

```markdown
## For Each Sprint:
- Sprint plan (scope, effort, team)
- Daily standup notes
- Code review checklist
- Test results
- Known issues/workarounds
- Lessons learned

## At Project End:
- Implementation guide
- Operations manual
- Incident response runbook
- Security architecture document
- Compliance report
- Training materials
- Post-implementation review
```

---

## ✅ APPROVAL & SIGN-OFF

```
Security Team Lead:      ________________    Date: _____
CTO/Technical Lead:      ________________    Date: _____
Compliance Officer:      ________________    Date: _____
Project Sponsor:         ________________    Date: _____
```

---

**Plan Prepared**: 19 Junio 2026  
**Plan Approved**: _________________  
**Target Completion**: 5 Septiembre 2026  
**Security Posture Target**: 9+/10

---

## 📞 CONTACT & SUPPORT

- **Security Lead**: [contact]
- **PM**: [contact]
- **CTO**: [contact]
- **Escalation**: [process]

---

**Este plan es confidencial y debe tratarse como documento estratégico de seguridad.**
