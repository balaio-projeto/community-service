package br.com.balaio.community.pergunta.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.balaio.community.pergunta.application.PerguntaService;
import br.com.balaio.community.pergunta.domain.Pergunta;
import br.com.balaio.community.shared.servicos.PerfilPort;

class PerguntasControllerFotoTest {
    @Test
    void listaTrazAFotoAtualDeCadaAutorESemFotoQuandoOPerfilNaoTem() {
        UUID ana = UUID.randomUUID();
        UUID bia = UUID.randomUUID();
        var servico = mock(PerguntaService.class);
        var perfis = mock(PerfilPort.class);
        when(servico.listarDaPeca(eq("vaso"), any())).thenReturn(List.of(
                Pergunta.fazer("vaso", ana, "Ana", "Tem azul?", OffsetDateTime.now()),
                Pergunta.fazer("vaso", bia, "Bia", "Tem verde?", OffsetDateTime.now())));
        when(perfis.imagensDasContas(any())).thenReturn(Map.of(ana, "/api/v1/arquivos/perfis/foto-da-ana"));

        var resposta = new PerguntasController(servico, perfis).listar("vaso", 0, 20).dados();

        assertThat(resposta).extracting(PerguntaResponse::autor, PerguntaResponse::autorImagem).containsExactly(
                org.assertj.core.groups.Tuple.tuple("Ana", "/api/v1/arquivos/perfis/foto-da-ana"),
                org.assertj.core.groups.Tuple.tuple("Bia", null));
    }
}
