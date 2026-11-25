package br.com.balaio.community.video.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionOperations;

import br.com.balaio.community.shared.exception.RecursoNaoEncontradoException;
import br.com.balaio.community.shared.paginacao.Pagina;
import br.com.balaio.community.shared.security.Papel;
import br.com.balaio.community.shared.security.UsuarioAutenticado;
import br.com.balaio.community.shared.servicos.CatalogoPort;
import br.com.balaio.community.video.application.VideoService.DadosVideo;
import br.com.balaio.community.video.application.VideoService.EnvioMidia;
import br.com.balaio.community.video.application.port.out.ArmazenamentoMidiaPort;
import br.com.balaio.community.video.application.port.out.ComentariosVideoPort;
import br.com.balaio.community.video.application.port.out.InteracoesVideoPort;
import br.com.balaio.community.video.application.port.out.VideosPort;
import br.com.balaio.community.video.domain.FiltroModeracaoVideo;
import br.com.balaio.community.video.domain.TipoMidia;
import br.com.balaio.community.video.domain.Video;

class VideoServiceTest {
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-14T12:00:00Z"), ZoneOffset.UTC);

    private final UsuarioAutenticado artesao = new UsuarioAutenticado(UUID.randomUUID(), Papel.ARTESAO, "a");
    private final UsuarioAutenticado visitante = new UsuarioAutenticado(UUID.randomUUID(), Papel.COMPRADOR, "c");

    private final UsuarioAutenticado administrador = new UsuarioAutenticado(UUID.randomUUID(), Papel.ADMIN, "adm");

    private VideosEmMemoria repositorio;
    private MidiasEmMemoria armazenamento;
    private VideoService videos;

    @BeforeEach
    void preparar() {
        repositorio = new VideosEmMemoria();
        armazenamento = new MidiasEmMemoria();
        videos = new VideoService(repositorio, mock(ComentariosVideoPort.class), armazenamento,
                mock(CatalogoPort.class), new SemInteracoes(), TransactionOperations.withoutTransaction(), RELOGIO);
    }

    @Test
    void novoUploadSubstituiReferenciaERemoveMidiaAnterior() {
        UUID id = rascunho();
        var primeira = enviarVideo(id).video().arquivo();

        var segunda = enviarVideo(id).video().arquivo();

        assertThat(repositorio.buscar(id).orElseThrow().arquivo()).isEqualTo(segunda);
        assertThat(armazenamento.chaves()).containsExactly(segunda.chave());
        assertThat(primeira.chave()).isNotEqualTo(segunda.chave());
    }

    @Test
    void uploadQuePerdeADisputaRemoveAMidiaGravadaEPreservaAVencedora() {
        UUID id = rascunho();
        var vencedora = enviarVideo(id).video().arquivo();
        repositorio.proximoSalvarEncontraVersaoMaisNova();

        assertThatThrownBy(() -> enviarVideo(id)).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(repositorio.buscar(id).orElseThrow().arquivo()).isEqualTo(vencedora);
        assertThat(armazenamento.chaves()).containsExactly(vencedora.chave());
    }

    @Test
    void videoRemovidoDuranteOUploadNaoDeixaMidiaOrfa() {
        UUID id = rascunho();
        repositorio.removerAposAProximaBusca(id);

        assertThatThrownBy(() -> enviarVideo(id)).isInstanceOf(RecursoNaoEncontradoException.class);

        assertThat(armazenamento.chaves()).isEmpty();
    }

    @Test
    void falhaAoGravarNoArmazenamentoNaoAlteraOVideo() {
        UUID id = rascunho();
        armazenamento.falharProximaGravacao();

        assertThatThrownBy(() -> enviarVideo(id)).isInstanceOf(UncheckedIOException.class);

        assertThat(repositorio.buscar(id).orElseThrow().arquivo()).isNull();
    }

    @Test
    void visualizacaoContaParaVisitanteMasNaoParaODono() {
        UUID id = rascunho();
        enviarVideo(id);
        videos.alterar(id, new DadosVideo(null, null, null, null, "publicada"), artesao);

        videos.registrarVisualizacao(id, Optional.empty());
        videos.registrarVisualizacao(id, Optional.of(visitante));
        videos.registrarVisualizacao(id, Optional.of(artesao));

        assertThat(repositorio.visualizacoes(id)).isEqualTo(2);
    }

    @Test
    void rascunhoNaoRecebeVisualizacaoDeTerceiros() {
        UUID id = rascunho();

        assertThatThrownBy(() -> videos.registrarVisualizacao(id, Optional.of(visitante)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        videos.registrarVisualizacao(id, Optional.of(artesao));
        assertThat(repositorio.visualizacoes(id)).isZero();
    }

    @Test
    void videoSuspensoSaiDoPublicoMasFicaNoPainelDoDonoENoDetalheDoAdmin() {
        UUID id = publicado();
        suspender(id);

        assertThat(videos.listar(false, Optional.empty(), Pagina.primeira())).isEmpty();
        assertThatThrownBy(() -> videos.consultar(id, Optional.of(visitante)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> videos.consultar(id, Optional.empty()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> videos.registrarVisualizacao(id, Optional.of(visitante)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(videos.listar(true, Optional.of(artesao), Pagina.primeira()))
                .extracting(v -> v.video().suspenso()).containsExactly(true);
        assertThat(videos.consultar(id, Optional.of(artesao)).video().suspensao().motivo())
                .isEqualTo("Conteúdo de terceiros");
        assertThat(videos.consultar(id, Optional.of(administrador)).video().id()).isEqualTo(id);
    }

    @Test
    void donoEditaVideoSuspensoEEleContinuaForaDoPublico() {
        UUID id = publicado();
        suspender(id);

        var alterado = videos.alterar(id, new DadosVideo("Legenda corrigida", List.of("barro"), null, null,
                "publicada"), artesao);

        assertThat(alterado.video().legenda()).isEqualTo("Legenda corrigida");
        assertThat(alterado.video().suspenso()).isTrue();
        assertThat(videos.listar(false, Optional.empty(), Pagina.primeira())).isEmpty();
    }

    @Test
    void listagemDoPainelRespeitaAPagina() {
        rascunho();
        rascunho();
        rascunho();

        assertThat(videos.listar(true, Optional.of(artesao), Pagina.de(1, 2))).hasSize(1);
    }

    private UUID rascunho() {
        return videos.criar(new DadosVideo("Asa do vaso", List.of(), null, null, null), artesao).video().id();
    }

    private UUID publicado() {
        UUID id = rascunho();
        enviarVideo(id);
        videos.alterar(id, new DadosVideo(null, null, null, null, "publicada"), artesao);
        return id;
    }

    private void suspender(UUID id) {
        var video = repositorio.buscar(id).orElseThrow();
        video.suspender("Conteúdo de terceiros", administrador.id(), OffsetDateTime.now(RELOGIO));
        repositorio.salvar(video);
    }

    private VideoService.VideoComNumeros enviarVideo(UUID id) {
        return videos.enviarMidia(id, new EnvioMidia(TipoMidia.VIDEO, "video/mp4", 4,
                new ByteArrayInputStream(new byte[] { 0, 0, 0, 24 }), null), artesao);
    }

    private static final class VideosEmMemoria implements VideosPort {
        private final Map<UUID, Video> armazenados = new LinkedHashMap<>();
        private final Map<UUID, Long> visualizacoes = new HashMap<>();
        private boolean conflitoNoProximoSalvar;
        private UUID removerAposBusca;

        void proximoSalvarEncontraVersaoMaisNova() {
            conflitoNoProximoSalvar = true;
        }

        void removerAposAProximaBusca(UUID id) {
            removerAposBusca = id;
        }

        long visualizacoes(UUID id) {
            return visualizacoes.getOrDefault(id, 0L);
        }

        @Override
        public Video salvar(Video video) {
            if (conflitoNoProximoSalvar) {
                conflitoNoProximoSalvar = false;
                throw new ObjectOptimisticLockingFailureException(Video.class, video.id());
            }
            armazenados.put(video.id(), video);
            return video;
        }

        @Override
        public Optional<Video> buscar(UUID id) {
            if (id.equals(removerAposBusca) && armazenados.containsKey(id)) {
                var encontrado = armazenados.get(id);
                removerAposBusca = null;
                armazenados.remove(id);
                return Optional.of(encontrado);
            }
            return Optional.ofNullable(armazenados.get(id)).map(VideosEmMemoria::copia);
        }

        @Override
        public List<Video> listarPublicados(Pagina pagina) {
            return paginar(armazenados.values().stream().filter(v -> v.visivelPara(null)).toList(), pagina);
        }

        @Override
        public List<Video> listarDoArtesao(UUID artesaoId, Pagina pagina) {
            return paginar(armazenados.values().stream().filter(v -> v.pertenceA(artesaoId)).toList(), pagina);
        }

        @Override
        public List<Video> listarParaModeracao(FiltroModeracaoVideo filtro, Pagina pagina) {
            return paginar(armazenados.values().stream().filter(filtro::aceita).toList(), pagina);
        }

        @Override
        public void remover(UUID id) {
            armazenados.remove(id);
        }

        @Override
        public void registrarVisualizacao(UUID id) {
            visualizacoes.merge(id, 1L, Long::sum);
        }

        private static List<Video> paginar(List<Video> lista, Pagina pagina) {
            return lista.stream().skip(pagina.deslocamento()).limit(pagina.tamanho()).toList();
        }

        private static Video copia(Video video) {
            return Video.reconstituir(new Video.Dados(video.id(), video.artesaoId(), video.criadoEm(),
                    video.pecaSlug(), video.legenda(), video.etiquetas(), video.duracao(), video.situacao(),
                    video.arquivo(), video.capa(), video.publicadoEm(), video.visualizacoes(), video.suspensao()));
        }
    }

    private static final class MidiasEmMemoria implements ArmazenamentoMidiaPort {
        private final Map<String, byte[]> arquivos = new LinkedHashMap<>();
        private boolean falharGravacao;

        void falharProximaGravacao() {
            falharGravacao = true;
        }

        List<String> chaves() {
            return List.copyOf(arquivos.keySet());
        }

        @Override
        public long gravar(String chave, InputStream conteudo, long tamanhoMaximoBytes) {
            if (falharGravacao) {
                falharGravacao = false;
                throw new UncheckedIOException(new IOException("disco cheio"));
            }
            try {
                byte[] bytes = conteudo.readAllBytes();
                arquivos.put(chave, bytes);
                return bytes.length;
            } catch (IOException falha) {
                throw new UncheckedIOException(falha);
            }
        }

        @Override
        public Optional<MidiaArmazenada> abrir(String chave) {
            return Optional.empty();
        }

        @Override
        public void remover(String chave) {
            arquivos.remove(chave);
        }
    }

    private static final class SemInteracoes implements InteracoesVideoPort {
        @Override
        public Map<UUID, Long> curtidasPorVideo(List<UUID> videoIds) {
            return Map.of();
        }

        @Override
        public void esquecerVideo(UUID videoId) {
        }
    }
}
