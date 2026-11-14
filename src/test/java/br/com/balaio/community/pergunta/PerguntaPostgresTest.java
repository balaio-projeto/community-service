package br.com.balaio.community.pergunta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

import br.com.balaio.community.pergunta.application.PerguntaService;
import br.com.balaio.community.shared.servicos.CatalogoPort;
import br.com.balaio.community.shared.servicos.CatalogoPort.PecaCatalogo;
import br.com.balaio.community.shared.servicos.PerfilPort;
import br.com.balaio.community.shared.security.Papel;
import br.com.balaio.community.shared.security.UsuarioAutenticado;

@EnabledIfEnvironmentVariable(named = "COMMUNITY_POSTGRES_TEST", matches = "true")
@SpringBootTest(properties = "balaio.worker.enabled=false")
@Import(PerguntaPostgresTest.Postgres.class)
class PerguntaPostgresTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class Postgres {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:18-alpine");
        }
    }

    @Autowired PerguntaService perguntas;
    @MockitoBean CatalogoPort catalogo;
    @MockitoBean PerfilPort perfis;
    @MockitoBean JwtDecoder decoder;

    @Test
    void persistePerguntaERespostaEmOrdemDeCriacao() {
        UUID artesao = UUID.randomUUID();
        when(catalogo.buscar(eq("vaso"), any())).thenReturn(Optional.of(new PecaCatalogo("vaso", "artesao-" + artesao)));
        when(perfis.nomeDoUsuario(any())).thenReturn("Ana");
        var comprador = new UsuarioAutenticado(UUID.randomUUID(), Papel.COMPRADOR, "t");

        var primeira = perguntas.perguntar("vaso", "Primeira?", comprador);
        perguntas.perguntar("vaso", "Segunda?", comprador);
        perguntas.responder("vaso", primeira.id(), "Resposta.", new UsuarioAutenticado(artesao, Papel.ARTESAO, "t"));

        var lista = perguntas.listarDaPeca("vaso");
        assertThat(lista).extracting(p -> p.texto()).containsExactly("Primeira?", "Segunda?");
        assertThat(lista.getFirst().resposta()).isEqualTo("Resposta.");
        assertThat(lista.getFirst().respondidaEm()).isNotNull();
    }
}
