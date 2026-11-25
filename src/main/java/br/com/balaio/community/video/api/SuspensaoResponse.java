package br.com.balaio.community.video.api;

import br.com.balaio.community.video.domain.SuspensaoVideo;

public record SuspensaoResponse(String motivo, String suspensoEm) {
    static SuspensaoResponse de(SuspensaoVideo suspensao) {
        return suspensao == null ? null : new SuspensaoResponse(suspensao.motivo(), suspensao.suspensoEm().toString());
    }
}
