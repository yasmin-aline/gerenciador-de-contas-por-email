package gerenciadordecontas.fluxo;

import gerenciadordecontas.modelo.Acao;

/**
 * Uma ação que espera a sua resposta (confirmar um valor estranho ou dizer o bloco de uma conta nova).
 *
 * @param criado data e hora de criação em ISO (ex.: 2026-11-16T18:00:00), como texto para o JSON ficar simples
 */
public record Pendente(String id, TipoPendente tipo, String criado, Acao acao, int mes, String motivo) {
}
