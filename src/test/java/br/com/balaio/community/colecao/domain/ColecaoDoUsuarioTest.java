package br.com.balaio.community.colecao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import br.com.balaio.community.shared.exception.RequisicaoInvalidaException;

class ColecaoDoUsuarioTest {
    private static final UUID USUARIO = UUID.randomUUID();

    @Test
    void removeRepeticoesEBrancosMantendoAOrdem() {
        var colecao = ColecaoDoUsuario.comItens(USUARIO, TipoColecao.FAVORITOS,
                List.of("vaso-de-barro", " cesto ", "vaso-de-barro", " "));

        assertThat(colecao.itens()).containsExactly("vaso-de-barro", "cesto");
    }

    @Test
    void favoritoPrecisaSerSlugValido() {
        assertThatThrownBy(() -> ColecaoDoUsuario.comItens(USUARIO, TipoColecao.FAVORITOS, List.of("Vaso de Barro!")))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }

    @Test
    void historicoGuardaSomenteAsBuscasMaisRecentes() {
        var buscas = IntStream.range(0, 30).mapToObj(i -> "busca " + i).toList();

        var colecao = ColecaoDoUsuario.comItens(USUARIO, TipoColecao.HISTORICO_BUSCA, buscas);

        assertThat(colecao.itens()).hasSize(TipoColecao.HISTORICO_BUSCA.quantidadeMaxima())
                .startsWith("busca 0");
    }

    @Test
    void favoritosAcimaDoLimiteSaoRecusados() {
        var muitos = IntStream.range(0, TipoColecao.FAVORITOS.quantidadeMaxima() + 1)
                .mapToObj(i -> "peca-" + i).toList();

        assertThatThrownBy(() -> ColecaoDoUsuario.comItens(USUARIO, TipoColecao.FAVORITOS, muitos))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }

    @Test
    void listaComNuloEhInvalida() {
        var comNulo = new ArrayList<>(Arrays.asList("cesto", null));

        assertThatThrownBy(() -> ColecaoDoUsuario.comItens(USUARIO, TipoColecao.VIDEOS_SALVOS, comNulo))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }

    @Test
    void codigoDesconhecidoNaoEhColecaoDoCommunity() {
        assertThat(TipoColecao.deCodigo("sacola")).isEmpty();
        assertThat(TipoColecao.deCodigo("videosCurtidos")).contains(TipoColecao.VIDEOS_CURTIDOS);
    }
}
