package mx.ades.modules.certificados.application.service;

import org.springframework.stereotype.Service;
import mx.ades.modules.certificados.domain.port.in.EmitirCertificadoUseCase;
import mx.ades.modules.certificados.domain.port.out.CertificadoFastApiPort;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

@Service
public class CertificadoApplicationService implements EmitirCertificadoUseCase {

    private final CertificadoFastApiPort fastApiPort;

    public CertificadoApplicationService(CertificadoFastApiPort fastApiPort) {
        this.fastApiPort = fastApiPort;
    }

    @Override
    public ResponseEntity<byte[]> emitir(Map<String, Object> body, String authHeader) {
        return fastApiPort.emitir(body, authHeader);
    }

    @Override
    public ResponseEntity<Map<String, Object>> firmar(UUID certId, String authHeader) {
        return fastApiPort.firmar(certId, authHeader);
    }

    @Override
    public ResponseEntity<Map<String, Object>> generarLlave(String authHeader) {
        return fastApiPort.generarLlave(authHeader);
    }

    @Override
    public ResponseEntity<Map<String, Object>> registrarLlave(Map<String, Object> body, String authHeader) {
        return fastApiPort.registrarLlave(body, authHeader);
    }

    @Override
    public ResponseEntity<Map<String, Object>> obtenerLlaveActiva(String authHeader) {
        return fastApiPort.obtenerLlaveActiva(authHeader);
    }
}
