package br.com.balaio.community.video.application;

import java.io.InputStream;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.com.balaio.community.shared.exception.NaoAutenticadoException;
import br.com.balaio.community.shared.exception.RecursoNaoEncontradoException;
import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;
import br.com.balaio.community.shared.paginacao.Pagina;
import br.com.balaio.community.shared.security.Papel;
import br.com.balaio.community.shared.security.UsuarioAutenticado;
import br.com.balaio.community.shared.servicos.CatalogoPort;
import br.com.balaio.community.video.application.port.out.ArmazenamentoMidiaPort;
import br.com.balaio.community.video.application.port.out.ComentariosVideoPort;
import br.com.balaio.community.video.application.port.out.InteracoesVideoPort;
import br.com.balaio.community.video.application.port.out.VideosPort;
import br.com.balaio.community.video.domain.ArquivoMidia;
import br.com.balaio.community.video.domain.SituacaoVideo;
import br.com.balaio.community.video.domain.TipoMidia;
import br.com.balaio.community.video.domain.Video;

@Service
public class VideoService {
    private static final Logger LOG = LoggerFactory.getLogger(VideoService.class);

    private final VideosPort videos;
    private final ComentariosVideoPort comentarios;
    private final ArmazenamentoMidiaPort armazenamento;
    private final CatalogoPort catalogo;
    private final InteracoesVideoPort interacoes;
    private final TransactionOperations transacao;
    private final Clock relogio;

    public VideoService(VideosPort videos, ComentariosVideoPort comentarios, ArmazenamentoMidiaPort armazenamento,
            CatalogoPort catalogo, InteracoesVideoPort interacoes, TransactionOperations transacao, Clock relogio) {
        this.videos = videos;
        this.comentarios = comentarios;
        this.armazenamento = armazenamento;
        this.catalogo = catalogo;
        this.interacoes = interacoes;
        this.transacao = transacao;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public List<VideoComNumeros> listar(boolean painel, Optional<UsuarioAutenticado> usuario, Pagina pagina) {
        var lista = painel
                ? videos.listarDoArtesao(exigirArtesao(usuario).id(), pagina)
                : videos.listarPublicados(pagina);
        return comNumeros(lista);
    }

    @Transactional
    public void registrarVisualizacao(UUID id, Optional<UsuarioAutenticado> espectador) {
        var video = visivel(id, espectador);
        if (video.contaVisualizacaoDe(espectador.map(UsuarioAutenticado::id).orElse(null))) {
            videos.registrarVisualizacao(id);
        }
    }

    @Transactional(readOnly = true)
    public VideoComNumeros consultar(UUID id, Optional<UsuarioAutenticado> usuario) {
        return comNumeros(visivelOuEmModeracao(id, usuario));
    }

    public VideoComNumeros criar(DadosVideo dados, UsuarioAutenticado artesao) {
        artesao.exigirPapel(Papel.ARTESAO);
        exigirPecaDoArtesao(dados.peca(), artesao);
        return transacao.execute(status -> {
            var video = Video.rascunho(artesao.id(), vazioComoNulo(dados.peca()), dados.legenda(),
                    dados.etiquetas(), dados.duracao(), agora());
            if (dados.situacao() != null) {
                video.mudarSituacao(SituacaoVideo.deCodigo(dados.situacao()), agora());
            }
            return comNumeros(videos.salvar(video));
        });
    }

    public VideoComNumeros alterar(UUID id, DadosVideo dados, UsuarioAutenticado artesao) {
        doDono(id, artesao);
        exigirPecaDoArtesao(dados.peca(), artesao);
        return transacao.execute(status -> {
            var video = doDono(id, artesao);
            video.alterarDescricao(dados.legenda(), dados.etiquetas(), dados.peca());
            if (dados.situacao() != null) {
                video.mudarSituacao(SituacaoVideo.deCodigo(dados.situacao()), agora());
            }
            return comNumeros(videos.salvar(video));
        });
    }

    @Transactional
    public VideoComNumeros remover(UUID id, UsuarioAutenticado artesao) {
        var video = doDono(id, artesao);
        var removido = comNumeros(video);
        comentarios.removerDoVideo(id);
        interacoes.esquecerVideo(id);
        videos.remover(id);
        removerAposConfirmar(video.arquivo());
        removerAposConfirmar(video.capa());
        return removido;
    }

    public VideoComNumeros enviarMidia(UUID id, EnvioMidia envio, UsuarioAutenticado artesao) {
        doDono(id, artesao);
        var reservado = ArquivoMidia.novo(id, envio.tipo(), envio.tipoConteudo(), envio.tamanhoDeclarado());
        var novo = reservado.comTamanhoReal(
                armazenamento.gravar(reservado.chave(), envio.conteudo(), envio.tipo().tamanhoMaximoBytes()));
        try {
            return transacao.execute(status -> anexarMidia(id, novo, envio, artesao));
        } catch (RuntimeException falha) {
            removerSemInterromper(novo);
            throw falha;
        }
    }

    @Transactional(readOnly = true)
    public MidiaParaEntrega abrirMidia(UUID id, TipoMidia tipo, Optional<UsuarioAutenticado> usuario) {
        var video = visivelOuEmModeracao(id, usuario);
        var arquivo = Optional.ofNullable(tipo == TipoMidia.VIDEO ? video.arquivo() : video.capa())
                .orElseThrow(VideoService::naoEncontrado);
        var conteudo = armazenamento.abrir(arquivo.chave()).orElseThrow(VideoService::naoEncontrado);
        return new MidiaParaEntrega(conteudo, arquivo.tipoConteudo());
    }

    Video visivel(UUID id, Optional<UsuarioAutenticado> usuario) {
        UUID leitor = usuario.map(UsuarioAutenticado::id).orElse(null);
        return videos.buscar(id).filter(video -> video.visivelPara(leitor)).orElseThrow(VideoService::naoEncontrado);
    }

    private Video visivelOuEmModeracao(UUID id, Optional<UsuarioAutenticado> usuario) {
        if (usuario.filter(leitor -> leitor.possuiPapel(Papel.ADMIN)).isPresent()) {
            return videos.buscar(id).orElseThrow(VideoService::naoEncontrado);
        }
        return visivel(id, usuario);
    }

    private VideoComNumeros anexarMidia(UUID id, ArquivoMidia novo, EnvioMidia envio, UsuarioAutenticado artesao) {
        var video = doDono(id, artesao);
        var anterior = envio.tipo() == TipoMidia.VIDEO ? video.arquivo() : video.capa();
        if (envio.tipo() == TipoMidia.VIDEO) {
            video.anexarArquivo(novo, envio.duracao());
        } else {
            video.anexarCapa(novo);
        }
        var salvo = videos.salvar(video);
        removerAposConfirmar(anterior);
        return comNumeros(salvo);
    }

    private Video doDono(UUID id, UsuarioAutenticado artesao) {
        return videos.buscar(id).filter(video -> video.pertenceA(artesao.id()))
                .orElseThrow(VideoService::naoEncontrado);
    }

    private VideoComNumeros comNumeros(Video video) {
        return comNumeros(List.of(video)).getFirst();
    }

    private List<VideoComNumeros> comNumeros(List<Video> lista) {
        var ids = lista.stream().map(Video::id).toList();
        var totalComentarios = comentarios.contarPorVideo(ids);
        var totalCurtidas = interacoes.curtidasPorVideo(ids);
        return lista.stream().map(video -> new VideoComNumeros(video,
                totalCurtidas.getOrDefault(video.id(), 0L),
                totalComentarios.getOrDefault(video.id(), 0L))).toList();
    }

    private void exigirPecaDoArtesao(String pecaSlug, UsuarioAutenticado artesao) {
        if (pecaSlug == null || pecaSlug.isBlank()) {
            return;
        }
        boolean ehDoArtesao = catalogo.buscar(pecaSlug, Optional.of(artesao.token()))
                .filter(peca -> artesao.slugArtesao().equals(peca.artesaoSlug())).isPresent();
        if (!ehDoArtesao) {
            throw new RequisicaoInvalidaException("Vincule uma peça do seu ateliê.");
        }
    }

    private void removerAposConfirmar(ArquivoMidia arquivo) {
        if (arquivo == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            removerSemInterromper(arquivo);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                removerSemInterromper(arquivo);
            }
        });
    }

    private void removerSemInterromper(ArquivoMidia arquivo) {
        try {
            armazenamento.remover(arquivo.chave());
        } catch (RuntimeException falha) {
            LOG.warn("Não foi possível remover a mídia {}; o arquivo ficou órfão no armazenamento.",
                    arquivo.chave(), falha);
        }
    }

    private static UsuarioAutenticado exigirArtesao(Optional<UsuarioAutenticado> usuario) {
        return usuario.orElseThrow(NaoAutenticadoException::new).exigirPapel(Papel.ARTESAO);
    }

    private static String vazioComoNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto;
    }

    static RecursoNaoEncontradoException naoEncontrado() {
        return new RecursoNaoEncontradoException("VIDEO_NAO_ENCONTRADO", "Vídeo não encontrado.");
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(relogio);
    }

    public record DadosVideo(String legenda, List<String> etiquetas, String peca, String duracao, String situacao) {
    }

    public record EnvioMidia(TipoMidia tipo, String tipoConteudo, long tamanhoDeclarado, InputStream conteudo,
            String duracao) {
    }

    public record VideoComNumeros(Video video, long curtidas, long comentarios) {
    }

    public record MidiaParaEntrega(ArmazenamentoMidiaPort.MidiaArmazenada conteudo, String tipoConteudo) {
    }
}
