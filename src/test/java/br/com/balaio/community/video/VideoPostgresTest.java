package br.com.balaio.community.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

import br.com.balaio.community.colecao.application.ColecaoService;
import br.com.balaio.community.colecao.domain.TipoColecao;
import br.com.balaio.community.shared.exception.ConflitoException;
import br.com.balaio.community.shared.exception.RecursoNaoEncontradoException;
import br.com.balaio.community.shared.security.Papel;
import br.com.balaio.community.shared.security.UsuarioAutenticado;
import br.com.balaio.community.shared.servicos.CatalogoPort;
import br.com.balaio.community.shared.servicos.PerfilPort;
import br.com.balaio.community.video.application.ComentarioVideoService;
import br.com.balaio.community.video.application.VideoService;
import br.com.balaio.community.video.application.VideoService.DadosVideo;
import br.com.balaio.community.video.application.VideoService.EnvioMidia;
import br.com.balaio.community.video.domain.TipoMidia;

@EnabledIfEnvironmentVariable(named = "COMMUNITY_POSTGRES_TEST", matches = "true")
@SpringBootTest(properties = "balaio.worker.enabled=false")
@Import(VideoPostgresTest.Postgres.class)
class VideoPostgresTest {
    @TempDir static Path midia;

    @TestConfiguration(proxyBeanMethods = false)
    static class Postgres {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:18-alpine");
        }
    }

    @DynamicPropertySource
    static void diretorioDeMidia(DynamicPropertyRegistry registro) {
        registro.add("balaio.midia.diretorio", () -> midia.toString());
    }

    @Autowired VideoService videos;
    @Autowired ComentarioVideoService comentarios;
    @Autowired ColecaoService colecoes;
    @MockitoBean CatalogoPort catalogo;
    @MockitoBean PerfilPort perfis;
    @MockitoBean JwtDecoder decoder;

    @Test
    void fluxoCompletoDeRascunhoUploadPublicacaoComentarioERemocao() throws Exception {
        var artesao = new UsuarioAutenticado(UUID.randomUUID(), Papel.ARTESAO, "a");
        var fa = new UsuarioAutenticado(UUID.randomUUID(), Papel.COMPRADOR, "c");
        when(perfis.nomeDoUsuario(any())).thenReturn("Clarissa");
        when(catalogo.buscar(any(), any())).thenReturn(Optional.empty());

        var criado = videos.criar(new DadosVideo("Asa do vaso", List.of("ceramica"), null, null, null), artesao);
        UUID id = criado.video().id();
        assertThatThrownBy(() -> videos.consultar(id, Optional.of(fa)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> videos.alterar(id, new DadosVideo(null, null, null, null, "publicada"), artesao))
                .isInstanceOf(ConflitoException.class);

        videos.enviarMidia(id, new EnvioMidia(TipoMidia.VIDEO, "video/mp4", 4,
                new ByteArrayInputStream(new byte[] { 0, 0, 0, 24 }), "0:48"), artesao);
        videos.alterar(id, new DadosVideo(null, null, null, null, "publicada"), artesao);
        assertThat(videos.listar(false, Optional.empty())).extracting(v -> v.video().id()).contains(id);
        assertThat(videos.abrirMidia(id, TipoMidia.VIDEO, Optional.empty()).conteudo().tamanho()).isEqualTo(4);

        comentarios.comentar(id, "Que lindo!", fa);
        colecoes.substituir(fa.id(), TipoColecao.VIDEOS_CURTIDOS, List.of(id.toString()));
        var detalhe = videos.consultar(id, Optional.empty());
        assertThat(detalhe.comentarios()).isEqualTo(1);
        assertThat(detalhe.curtidas()).isEqualTo(1);
        assertThat(comentarios.listar(id, Optional.empty())).extracting(c -> c.autorNome()).containsExactly("Clarissa");

        videos.remover(id, artesao);
        assertThat(colecoes.consultar(fa.id(), TipoColecao.VIDEOS_CURTIDOS)).isEmpty();
        assertThatThrownBy(() -> videos.consultar(id, Optional.of(artesao)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
