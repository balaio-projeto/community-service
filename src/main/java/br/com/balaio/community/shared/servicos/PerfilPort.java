package br.com.balaio.community.shared.servicos;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface PerfilPort {
    String nomeDoUsuario(String token);

    Map<UUID, String> imagensDasContas(Collection<UUID> contas);
}
