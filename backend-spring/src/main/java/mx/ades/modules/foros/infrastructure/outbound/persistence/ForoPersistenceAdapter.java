package mx.ades.modules.foros.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.foros.Anuncio;
import mx.ades.modules.foros.AnuncioRepository;
import mx.ades.modules.foros.Foro;
import mx.ades.modules.foros.ForoRepository;
import mx.ades.modules.foros.MensajeForo;
import mx.ades.modules.foros.MensajeForoRepository;
import mx.ades.modules.foros.RespuestaForo;
import mx.ades.modules.foros.RespuestaForoRepository;
import mx.ades.modules.foros.domain.port.out.ForoRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JPA que implementa {@link ForoRepositoryPort}.
 * Delega en los repositorios Spring Data de Foro, MensajeForo, RespuestaForo y Anuncio.
 *
 * @author ADES
 * @since 2026
 */
@Component
@RequiredArgsConstructor
public class ForoPersistenceAdapter implements ForoRepositoryPort {

    private final ForoRepository foroRepository;
    private final MensajeForoRepository mensajeRepository;
    private final RespuestaForoRepository respuestaRepository;
    private final AnuncioRepository anuncioRepository;

    @Override public Foro saveForo(Foro foro)                              { return foroRepository.save(foro); }
    @Override public Optional<Foro> findForoById(UUID id)                  { return foroRepository.findById(id); }
    @Override public List<Foro> findAllForos()                             { return foroRepository.findAll(); }

    @Override public MensajeForo saveMensaje(MensajeForo m)                { return mensajeRepository.save(m); }
    @Override public Optional<MensajeForo> findMensajeById(UUID id)        { return mensajeRepository.findById(id); }
    @Override public List<MensajeForo> findMensajesByForo(UUID foroId)     { return mensajeRepository.findByForoId(foroId); }

    @Override public RespuestaForo saveRespuesta(RespuestaForo rf)         { return respuestaRepository.save(rf); }

    @Override public Anuncio saveAnuncio(Anuncio a)                        { return anuncioRepository.save(a); }
    @Override public List<Anuncio> findAllAnuncios()                       { return anuncioRepository.findAll(); }
}
