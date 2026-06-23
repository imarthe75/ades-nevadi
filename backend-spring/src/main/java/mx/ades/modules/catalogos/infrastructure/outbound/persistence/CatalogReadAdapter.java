package mx.ades.modules.catalogos.infrastructure.outbound.persistence;

import mx.ades.modules.catalogos.*;
import mx.ades.modules.catalogos.domain.port.out.CatalogReadPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class CatalogReadAdapter implements CatalogReadPort {

    private final NivelEducativoRepository nivelRepo;
    private final GradoRepository gradoRepo;
    private final CicloEscolarRepository cicloRepo;
    private final CatalogsQueryService queryService;

    public CatalogReadAdapter(
            NivelEducativoRepository nivelRepo,
            GradoRepository gradoRepo,
            CicloEscolarRepository cicloRepo,
            CatalogsQueryService queryService) {
        this.nivelRepo    = nivelRepo;
        this.gradoRepo    = gradoRepo;
        this.cicloRepo    = cicloRepo;
        this.queryService = queryService;
    }

    @Override public List<NivelEducativo> findAllNiveles()               { return nivelRepo.findAll(); }
    @Override public List<Grado> findAllGrados()                         { return gradoRepo.findAll(); }
    @Override
    public List<Grado> findGrados(UUID nivelId, UUID plantelId) {
        List<Grado> list;
        if (nivelId != null && plantelId != null) {
            list = gradoRepo.findByNivelEducativoIdAndPlantelId(nivelId, plantelId);
        } else if (nivelId != null) {
            list = gradoRepo.findByNivelEducativoId(nivelId);
        } else if (plantelId != null) {
            list = gradoRepo.findByPlantelId(plantelId);
        } else {
            list = gradoRepo.findAll();
        }

        if (plantelId == null) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            List<Grado> uniqueList = new java.util.ArrayList<>();
            for (Grado g : list) {
                UUID nId = g.getNivelEducativo() != null ? g.getNivelEducativo().getId() : null;
                String key = g.getNumeroGrado() + "_" + nId;
                if (seen.add(key)) {
                    uniqueList.add(g);
                }
            }
            return uniqueList;
        }
        return list;
    }
    @Override public List<CicloEscolar> findAllCiclos()                  { return cicloRepo.findAll(); }
    @Override public List<CicloEscolar> findCiclosVigentes()             { return cicloRepo.findByEsVigenteTrue(); }
    @Override public List<Map<String, Object>> roles()                   { return queryService.roles(); }
    @Override public List<Map<String, Object>> periodos(UUID c, UUID g)  { return queryService.periodos(c, g); }
    @Override public List<Map<String, Object>> paises()                  { return queryService.paises(); }
    @Override public List<Map<String, Object>> nacionalidades()          { return queryService.nacionalidades(); }
    @Override public List<Map<String, Object>> lenguasIndigenas(String f){ return queryService.lenguasIndigenas(f); }
    @Override public List<Map<String, Object>> familiasLinguisticas()    { return queryService.familiasLinguisticas(); }
    @Override public List<Map<String, Object>> nivelesIngles()           { return queryService.nivelesIngles(); }
}
