package br.com.balaio.community.pergunta.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.balaio.community.pergunta.application.port.out.PecasCatalogoPort;
import br.com.balaio.community.pergunta.application.port.out.PecasCatalogoPort.PecaCatalogo;
import br.com.balaio.community.pergunta.application.port.out.PerfisPort;
import br.com.balaio.community.pergunta.application.port.out.PerguntasPort;
import br.com.balaio.community.pergunta.domain.Pergunta;
import br.com.balaio.community.shared.exception.AcessoNegadoException;
import br.com.balaio.community.shared.exception.RecursoNaoEncontradoException;
import br.com.balaio.community.shared.security.Papel;
import br.com.balaio.community.shared.security.UsuarioAutenticado;

class PerguntaServiceTest {
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-14T12:00:00Z"), ZoneOffset.UTC);
    private static final String PECA = "vaso-de-barro";

    private final UUID artesaoDono = UUID.randomUUID();
    private final UsuarioAutenticado comprador = new UsuarioAutenticado(UUID.randomUUID(), Papel.COMPRADOR, "t1");
    private final UsuarioAutenticado dono = new UsuarioAutenticado(artesaoDono, Papel.ARTESAO, "t2");

    private PecasCatalogoPort catalogo;
    private PerfisPort perfis;
    private PerguntaService perguntas;

    @BeforeEach
    void preparar() {
        catalogo = mock(PecasCatalogoPort.class);
        perfis = mock(PerfisPort.class);
        perguntas = new PerguntaService(new PerguntasEmMemoria(), catalogo, perfis, RELOGIO);
        when(catalogo.buscar(any(), any())).thenReturn(Optional.empty());
        when(catalogo.buscar(org.mockito.ArgumentMatchers.eq(PECA), any()))
                .thenReturn(Optional.of(new PecaCatalogo(PECA, "artesao-" + artesaoDono)));
        when(perfis.nomeDoUsuario("t1")).thenReturn("Ana");
    }

    @Test
    void compradorPerguntaComNomeVindoDoPerfil() {
        var criada = perguntas.perguntar(PECA, "Serve para planta?", comprador);

        assertThat(criada.autorNome()).isEqualTo("Ana");
        assertThat(criada.autorId()).isEqualTo(comprador.id());
        assertThat(perguntas.listarDaPeca(PECA)).extracting(Pergunta::id).containsExactly(criada.id());
    }

    @Test
    void pecaInexistenteRetornaNaoEncontradaSemConsultarPerfil() {
        assertThatThrownBy(() -> perguntas.perguntar("nao-existe", "Oi?", comprador))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> perguntas.listarDaPeca("nao-existe"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verifyNoInteractions(perfis);
    }

    @Test
    void artesaoDonoResponde() {
        var criada = perguntas.perguntar(PECA, "Serve para planta?", comprador);

        var respondida = perguntas.responder(PECA, criada.id(), "Sim.", dono);

        assertThat(respondida.resposta()).isEqualTo("Sim.");
    }

    @Test
    void outroArtesaoOuCompradorNaoRespondem() {
        var criada = perguntas.perguntar(PECA, "Serve para planta?", comprador);
        var outroArtesao = new UsuarioAutenticado(UUID.randomUUID(), Papel.ARTESAO, "t3");

        assertThatThrownBy(() -> perguntas.responder(PECA, criada.id(), "Sim.", outroArtesao))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> perguntas.responder(PECA, criada.id(), "Sim.", comprador))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void perguntaDeOutraPecaEhTratadaComoInexistente() {
        var outraPeca = "cesto";
        when(catalogo.buscar(org.mockito.ArgumentMatchers.eq(outraPeca), any()))
                .thenReturn(Optional.of(new PecaCatalogo(outraPeca, "artesao-" + artesaoDono)));
        var criada = perguntas.perguntar(PECA, "Serve para planta?", comprador);

        assertThatThrownBy(() -> perguntas.responder(outraPeca, criada.id(), "Sim.", dono))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    private static final class PerguntasEmMemoria implements PerguntasPort {
        private final List<Pergunta> armazenadas = new ArrayList<>();

        @Override
        public Pergunta salvar(Pergunta pergunta) {
            armazenadas.removeIf(existente -> existente.id().equals(pergunta.id()));
            armazenadas.add(pergunta);
            return pergunta;
        }

        @Override
        public Optional<Pergunta> buscar(UUID id) {
            return armazenadas.stream().filter(pergunta -> pergunta.id().equals(id)).findFirst();
        }

        @Override
        public List<Pergunta> listarDaPeca(String pecaSlug) {
            return armazenadas.stream().filter(pergunta -> pergunta.pertenceAPeca(pecaSlug)).toList();
        }
    }
}
