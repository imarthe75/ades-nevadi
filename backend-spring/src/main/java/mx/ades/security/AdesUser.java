package mx.ades.security;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AdesUser {
    private final UUID id;
    private final String username;
    private final String email;
    private final UUID personaId;
    private final UUID plantelId;
    private final UUID nivelEducativoId;
    private final UUID rolPrincipalId;
    private final List<String> roles;
    private final Integer nivelAcceso;
}
