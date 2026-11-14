package br.senac.qs.recursos.application.recurso;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import br.senac.qs.recursos.application.UsuarioDoToken;
import br.senac.qs.recursos.domain.exception.CadastroDuplicadoException;
import br.senac.qs.recursos.domain.exception.RecursoNaoEncontradoException;
import br.senac.qs.recursos.domain.exception.RegraNegocioException;
import br.senac.qs.recursos.domain.model.Competencia;
import br.senac.qs.recursos.domain.model.Material;
import br.senac.qs.recursos.domain.model.Professor;
import br.senac.qs.recursos.domain.model.Recurso;
import br.senac.qs.recursos.domain.model.RegistroAuditoria;
import br.senac.qs.recursos.domain.model.Sala;
import br.senac.qs.recursos.domain.model.Usuario;
import br.senac.qs.recursos.domain.repository.CompetenciaRepository;
import br.senac.qs.recursos.domain.repository.MaterialRepository;
import br.senac.qs.recursos.domain.repository.OcupacaoRepository;
import br.senac.qs.recursos.domain.repository.ProfessorRepository;
import br.senac.qs.recursos.domain.repository.RecursoRepository;
import br.senac.qs.recursos.domain.repository.RegistroAuditoriaRepository;
import br.senac.qs.recursos.domain.repository.SalaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro de salas, professores e materiais (RF02), com auditoria de cada mudança (RF10).
 *
 * <p>Duplicidade (nome de sala, matrícula, patrimônio) é conferida ANTES de alterar a entidade, para
 * a consulta não disparar a gravação antecipada da mudança. A conferência dá a mensagem clara; os
 * índices únicos do banco são a garantia contra dois cadastros simultâneos (viram 409 genérico).
 */
@Service
public class GerenciarRecursos {

    private final UsuarioDoToken usuarioDoToken;
    private final RecursoRepository recursos;
    private final SalaRepository salas;
    private final ProfessorRepository professores;
    private final MaterialRepository materiais;
    private final CompetenciaRepository competencias;
    private final OcupacaoRepository ocupacoes;
    private final RegistroAuditoriaRepository auditoria;
    private final Clock clock;

    @SuppressWarnings("java:S107") // um repositório por tipo de recurso: dividir só espalharia o caso de uso
    public GerenciarRecursos(UsuarioDoToken usuarioDoToken, RecursoRepository recursos, SalaRepository salas,
                             ProfessorRepository professores, MaterialRepository materiais,
                             CompetenciaRepository competencias, OcupacaoRepository ocupacoes,
                             RegistroAuditoriaRepository auditoria, Clock clock) {
        this.usuarioDoToken = usuarioDoToken;
        this.recursos = recursos;
        this.salas = salas;
        this.professores = professores;
        this.materiais = materiais;
        this.competencias = competencias;
        this.ocupacoes = ocupacoes;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- salas

    @Transactional
    public RecursoResumo cadastrarSala(String emailAutor, DadosSala dados) {
        var autor = usuarioDoToken.ativo(emailAutor);
        exigirNomeDeSalaLivre(dados.nome(), salas.existsByNomeIgnoreCase(semEspacos(dados.nome())));
        var sala = salas.save(Sala.cadastrar(autor, dados.nome(), dados.restrito(), dados.capacidade(),
                dados.bloco(), dados.andar(), dados.tipoSala()));
        return registrar(sala, "CRIACAO", autor);
    }

    @Transactional
    public RecursoResumo alterarSala(String emailAutor, Long id, DadosSala dados) {
        var autor = usuarioDoToken.ativo(emailAutor);
        var sala = salas.findById(id).orElseThrow(() -> naoEncontrado("Sala " + id + " não encontrada."));
        exigirNomeDeSalaLivre(dados.nome(), salas.existsByNomeIgnoreCaseAndIdNot(semEspacos(dados.nome()), id));
        sala.alterar(autor, dados.nome(), dados.restrito(), dados.capacidade(), dados.bloco(), dados.andar(),
                dados.tipoSala());
        return registrar(sala, "ALTERACAO", autor);
    }

    private static void exigirNomeDeSalaLivre(String nome, boolean emUso) {
        if (emUso) {
            throw new CadastroDuplicadoException("Já existe uma sala com o nome " + semEspacos(nome) + ".");
        }
    }

    // ---------------------------------------------------------------- professores

    @Transactional
    public RecursoResumo cadastrarProfessor(String emailAutor, DadosProfessor dados) {
        var autor = usuarioDoToken.ativo(emailAutor);
        exigirMatriculaLivre(dados.matricula(), professores.existsByMatricula(semEspacos(dados.matricula())));
        var professor = professores.save(Professor.cadastrar(autor, dados.nome(), dados.restrito(),
                dados.matricula(), dados.email(), competencias(dados.competencias())));
        return registrar(professor, "CRIACAO", autor);
    }

    @Transactional
    public RecursoResumo alterarProfessor(String emailAutor, Long id, DadosProfessor dados) {
        var autor = usuarioDoToken.ativo(emailAutor);
        var professor = professores.findById(id)
                .orElseThrow(() -> naoEncontrado("Professor " + id + " não encontrado."));
        exigirMatriculaLivre(dados.matricula(),
                professores.existsByMatriculaAndIdNot(semEspacos(dados.matricula()), id));
        var novasCompetencias = competencias(dados.competencias());
        professor.alterar(autor, dados.nome(), dados.restrito(), dados.matricula(), dados.email(), novasCompetencias);
        return registrar(professor, "ALTERACAO", autor);
    }

    private static void exigirMatriculaLivre(String matricula, boolean emUso) {
        if (emUso) {
            throw new CadastroDuplicadoException(
                    "Já existe um professor com a matrícula " + semEspacos(matricula) + ".");
        }
    }

    /**
     * Converte os nomes em competências, reaproveitando as já cadastradas. "Java" e " java " são a
     * mesma (fica o primeiro nome informado); uma competência nova é criada na hora.
     */
    private Set<Competencia> competencias(List<String> nomes) {
        var porChave = new LinkedHashMap<String, String>();
        nomes.forEach(nome -> porChave.putIfAbsent(Competencia.chave(nome), nome));
        var resultado = new LinkedHashSet<Competencia>();
        for (var nome : porChave.values()) {
            var nova = Competencia.nova(nome);
            resultado.add(competencias.findByNomeIgnoreCase(nova.getNome()).orElseGet(() -> competencias.save(nova)));
        }
        return resultado;
    }

    // ---------------------------------------------------------------- materiais

    @Transactional
    public RecursoResumo cadastrarMaterial(String emailAutor, DadosMaterial dados) {
        var autor = usuarioDoToken.ativo(emailAutor);
        exigirPatrimonioLivre(dados.patrimonio(), materiais.existsByPatrimonio(semEspacos(dados.patrimonio())));
        var material = materiais.save(Material.cadastrar(autor, dados.nome(), dados.restrito(), dados.patrimonio(),
                dados.tipoMaterial(), dados.estadoConservacao()));
        return registrar(material, "CRIACAO", autor);
    }

    @Transactional
    public RecursoResumo alterarMaterial(String emailAutor, Long id, DadosMaterial dados) {
        var autor = usuarioDoToken.ativo(emailAutor);
        var material = materiais.findById(id).orElseThrow(() -> naoEncontrado("Material " + id + " não encontrado."));
        exigirPatrimonioLivre(dados.patrimonio(),
                materiais.existsByPatrimonioAndIdNot(semEspacos(dados.patrimonio()), id));
        material.alterar(autor, dados.nome(), dados.restrito(), dados.patrimonio(), dados.tipoMaterial(),
                dados.estadoConservacao());
        return registrar(material, "ALTERACAO", autor);
    }

    private static void exigirPatrimonioLivre(String patrimonio, boolean emUso) {
        if (emUso) {
            throw new CadastroDuplicadoException(
                    "Já existe um material com o patrimônio " + semEspacos(patrimonio) + ".");
        }
    }

    // ---------------------------------------------------------------- ciclo de vida

    /**
     * Desativa o recurso, que deixa de aceitar reservas e de aparecer na pesquisa. Recusado enquanto
     * houver reserva ativa que ainda não terminou: quem reservou ficaria sem o recurso e sem aviso.
     *
     * <p>Trava o recurso como a criação de reserva faz: uma reserva simultânea espera a desativação
     * terminar (e então encontra o recurso inativo) ou termina antes (e então é contada aqui).
     */
    @Transactional
    public RecursoResumo desativar(String emailAutor, Long id) {
        var autor = usuarioDoToken.ativo(emailAutor);
        recursos.travar(id);
        var recurso = recursos.findById(id).orElseThrow(() -> naoEncontrado("Recurso " + id + " não encontrado."));
        recurso.desativar(autor);
        long reservasAtivas = ocupacoes.contarReservasAtivasNaoTerminadas(id, clock.instant());
        if (reservasAtivas > 0) {
            throw new RegraNegocioException("Não é possível desativar o recurso: há " + reservasAtivas
                    + " reserva(s) ativa(s) que ainda não terminaram. Cancele-as antes.");
        }
        return registrar(recurso, "DESATIVACAO", autor);
    }

    @Transactional
    public RecursoResumo reativar(String emailAutor, Long id) {
        var autor = usuarioDoToken.ativo(emailAutor);
        var recurso = recursos.findById(id).orElseThrow(() -> naoEncontrado("Recurso " + id + " não encontrado."));
        recurso.reativar(autor);
        return registrar(recurso, "REATIVACAO", autor);
    }

    // ---------------------------------------------------------------- utilitários

    private RecursoResumo registrar(Recurso recurso, String acao, Usuario autor) {
        auditoria.save(RegistroAuditoria.doRecurso(recurso.getId(), acao, autor, clock.instant()));
        return RecursoResumo.de(recurso);
    }

    private static RecursoNaoEncontradoException naoEncontrado(String mensagem) {
        return new RecursoNaoEncontradoException(mensagem);
    }

    private static String semEspacos(String texto) {
        return texto == null ? null : texto.strip();
    }
}
