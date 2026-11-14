package br.com.balaio.community.video.application;

import java.io.InputStream;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.balaio.community.colecao.application.ColecaoService;
import br.com.balaio.community.colecao.domain.TipoColecao;
import br.com.balaio.community.shared.exception.NaoAutenticadoException;
import br.com.balaio.community.shared.exception.RecursoNaoEncontradoException;
import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;
import br.com.balaio.community.shared.security.Papel;
import br.com.balaio.community.shared.security.UsuarioAutenticado;
import br.com.balaio.community.shared.servicos.CatalogoPort;
import br.com.balaio.community.video.application.port.out.ArmazenamentoMidiaPort;
import br.com.balaio.community.video.application.port.out.ComentariosVideoPort;
import br.com.balaio.community.video.application.port.out.VideosPort;
import br.com.balaio.community.video.domain.ArquivoMidia;
import br.com.balaio.community.video.domain.SituacaoVideo;
import br.com.balaio.community.video.domain.TipoMidia;
import br.com.balaio.community.video.domain.Video;

@Service
public class VideoService {
    private final VideosPort videos;
    private final ComentariosVideoPort comentarios;
    private final ArmazenamentoMidiaPort armazenamento;
    private final CatalogoPort catalogo;
    private final ColecaoService colecoes;
    private final Clock relogio;

    public VideoService(VideosPort videos, ComentariosVideoPort comentarios, ArmazenamentoMidiaPort armazenamento,
            CatalogoPort catalogo, ColecaoService colecoes, Clock relogio) {
        this.videos = videos;
        this.comentarios = comentarios;
        this.armazenamento = armazenamento;
        this.catalogo = catalogo;
        this.colecoes = colecoes;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public List<VideoComNumeros> listar(boolean painel, Optional<UsuarioAutenticado> usuario) {
        var lista = painel
                ? videos.listarDoArtesao(exigirArtesao(usuario).id())
                : videos.listarPublicados();
        return comNumeros(lista);
    }

    @Transactional(readOnly = true)
    public VideoComNumeros consultar(UUID id, Optional<UsuarioAutenticado> usuario) {
        return comNumeros(List.of(visivel(id, usuario))).getFirst();
    }

    @Transactional
    public VideoComNumeros criar(DadosVideo dados, UsuarioAutenticado artesao) {
        artesao.exigirPapel(Papel.ARTESAO);
        exigirPecaDoArtesao(dados.peca(), artesao);
        var video = Video.rascunho(artesao.id(), vazioComoNulo(dados.peca()), dados.legenda(), dados.etiquetas(),
                dados.duracao(), agora());
        if (dados.situacao() != null) {
            video.mudarSituacao(SituacaoVideo.deCodigo(dados.situacao()), agora());
        }
        return comNumeros(List.of(videos.salvar(video))).getFirst();
    }

    @Transactional
    public VideoComNumeros alterar(UUID id, DadosVideo dados, UsuarioAutenticado artesao) {
        var video = doDono(id, artesao);
        exigirPecaDoArtesao(dados.peca(), artesao);
        video.alterarDescricao(dados.legenda(), dados.etiquetas(), dados.peca());
        if (dados.situacao() != null) {
            video.mudarSituacao(SituacaoVideo.deCodigo(dados.situacao()), agora());
        }
        return comNumeros(List.of(videos.salvar(video))).getFirst();
    }

    @Transactional
    public VideoComNumeros remover(UUID id, UsuarioAutenticado artesao) {
        var video = doDono(id, artesao);
        var removido = comNumeros(List.of(video)).getFirst();
        comentarios.removerDoVideo(id);
        colecoes.esquecerItem(id.toString(), TipoColecao.VIDEOS_CURTIDOS, TipoColecao.VIDEOS_SALVOS);
        videos.remover(id);
        apagarSeExistir(video.arquivo());
        apagarSeExistir(video.capa());
        return removido;
    }

    @Transactional
    public VideoComNumeros enviarMidia(UUID id, EnvioMidia envio, UsuarioAutenticado artesao) {
        var video = doDono(id, artesao);
        var reservado = ArquivoMidia.novo(id, envio.tipo(), envio.tipoConteudo(), envio.tamanhoDeclarado());
        var novo = reservado.comTamanhoReal(
                armazenamento.gravar(reservado.chave(), envio.conteudo(), envio.tipo().tamanhoMaximoBytes()));
        var anterior = envio.tipo() == TipoMidia.VIDEO ? video.arquivo() : video.capa();
        try {
            if (envio.tipo() == TipoMidia.VIDEO) {
                video.anexarArquivo(novo, envio.duracao());
            } else {
                video.anexarCapa(novo);
            }
            var salvo = videos.salvar(video);
            apagarSeExistir(anterior);
            return comNumeros(List.of(salvo)).getFirst();
        } catch (RuntimeException falha) {
            armazenamento.remover(novo.chave());
            throw falha;
        }
    }

    @Transactional(readOnly = true)
    public MidiaParaEntrega abrirMidia(UUID id, TipoMidia tipo, Optional<UsuarioAutenticado> usuario) {
        var video = visivel(id, usuario);
        var arquivo = Optional.ofNullable(tipo == TipoMidia.VIDEO ? video.arquivo() : video.capa())
                .orElseThrow(VideoService::naoEncontrado);
        var conteudo = armazenamento.abrir(arquivo.chave()).orElseThrow(VideoService::naoEncontrado);
        return new MidiaParaEntrega(conteudo, arquivo.tipoConteudo());
    }

    Video visivel(UUID id, Optional<UsuarioAutenticado> usuario) {
        UUID leitor = usuario.map(UsuarioAutenticado::id).orElse(null);
        return videos.buscar(id).filter(video -> video.visivelPara(leitor)).orElseThrow(VideoService::naoEncontrado);
    }

    private Video doDono(UUID id, UsuarioAutenticado artesao) {
        return videos.buscar(id).filter(video -> video.pertenceA(artesao.id()))
                .orElseThrow(VideoService::naoEncontrado);
    }

    private List<VideoComNumeros> comNumeros(List<Video> lista) {
        var ids = lista.stream().map(Video::id).toList();
        var totalComentarios = comentarios.contarPorVideo(ids);
        var totalCurtidas = colecoes.quantosUsuariosTem(TipoColecao.VIDEOS_CURTIDOS,
                ids.stream().map(UUID::toString).toList());
        return lista.stream().map(video -> new VideoComNumeros(video,
                totalCurtidas.getOrDefault(video.id().toString(), 0L),
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

    private void apagarSeExistir(ArquivoMidia arquivo) {
        if (arquivo != null) {
            armazenamento.remover(arquivo.chave());
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
