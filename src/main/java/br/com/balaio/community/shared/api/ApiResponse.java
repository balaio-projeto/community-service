package br.com.balaio.community.shared.api;

public record ApiResponse<T>(T dados, ApiError erro) {
    public static <T> ApiResponse<T> sucesso(T dados) {
        return new ApiResponse<>(dados, null);
    }

    public static ApiResponse<Void> falha(String codigo, String mensagem) {
        return new ApiResponse<>(null, new ApiError(codigo, mensagem));
    }
}
