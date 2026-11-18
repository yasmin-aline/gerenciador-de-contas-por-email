package gerenciadordecontas.email;

import java.time.Instant;

/**
 * @param id     identificador estável da mensagem (Message-ID), usado para nunca processar duas vezes
 * @param handle identificador técnico da caixa de entrada, usado para marcar como lida
 */
public record EmailRecebido(String id, String assunto, String texto, String de, Instant data, String handle) {
}
