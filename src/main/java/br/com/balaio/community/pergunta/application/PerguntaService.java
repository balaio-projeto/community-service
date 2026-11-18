package br.com.balaio.community.pergunta.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import br.com.balaio.community.shared.servicos.CatalogoPort;
import br.com.balaio.community.shared.servicos.CatalogoPort.PecaCatalogo;
import br.com.balaio.community.shared.servicos.PerfilPort;
import br.com.balaio.community.pergunta.application.port.out.PerguntasPort;
import br.com.balaio.community.pergunta.domain.Pergunta;
import br.com.balaio.community.shared.exception.AcessoNegadoException;
import br.com.balaio.community.shared.exception.RecursoNaoEncontradoException;
import br.com.balaio.community.shared.paginacao.Pagina;
import br.com.balaio.community.shared.security.Papel;
import br.com.balaio.community.shared.security.UsuarioAutenticado;

@Service
public class PerguntaService {
    private final PerguntasPort perguntas;
    private final CatalogoPort pecas;
    private final PerfilPort perfis;
    private final TransactionOperations transacao;
    private final Clock relogio;

    public PerguntaService(PerguntasPort perguntas, CatalogoPort pecas, PerfilPort perfis,
            TransactionOperations transacao, Clock relogio) {
        this.perguntas = perguntas;
        this.pecas = pecas;
        this.perfis = perfis;
        this.transacao = transacao;
        this.relogio = relogio;
    }

    public List<Pergunta> listarDaPeca(String pecaSlug, Pagina pagina) {
        exigirPeca(pecaSlug, Optional.empty());
        return perguntas.listarDaPeca(pecaSlug, pagina);
    }

    public Pergunta perguntar(String pecaSlug, String texto, UsuarioAutenticado autor) {
        exigirPeca(pecaSlug, Optional.of(autor.token()));
        String nomeAutor = perfis.nomeDoUsuario(autor.token());
        return perguntas.salvar(Pergunta.fazer(pecaSlug, autor.id(), nomeAutor, texto, agora()));
    }

    public Pergunta responder(String pecaSlug, UUID perguntaId, String texto, UsuarioAutenticado artesao) {
        PecaCatalogo peca = exigirPeca(pecaSlug, Optional.of(artesao.token()));
        return transacao.execute(status -> {
            var pergunta = perguntas.buscar(perguntaId).filter(encontrada -> encontrada.pertenceAPeca(pecaSlug))
                    .orElseThrow(() -> new RecursoNaoEncontradoException("PERGUNTA_NAO_ENCONTRADA",
                            "Pergunta não encontrada."));
            if (!ehArtesaoResponsavel(artesao, peca)) {
                throw new AcessoNegadoException("Somente o artesão responsável pode responder.");
            }
            pergunta.responder(texto, agora());
            return perguntas.salvar(pergunta);
        });
    }

    private PecaCatalogo exigirPeca(String pecaSlug, Optional<String> tokenVisitante) {
        return pecas.buscar(pecaSlug, tokenVisitante)
                .orElseThrow(() -> new RecursoNaoEncontradoException("PECA_NAO_ENCONTRADA", "Peça não encontrada."));
    }

    private static boolean ehArtesaoResponsavel(UsuarioAutenticado usuario, PecaCatalogo peca) {
        return usuario.possuiPapel(Papel.ARTESAO) && usuario.slugArtesao().equals(peca.artesaoSlug());
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(relogio);
    }
}
