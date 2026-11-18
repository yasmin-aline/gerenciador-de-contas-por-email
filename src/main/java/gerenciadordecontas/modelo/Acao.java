package gerenciadordecontas.modelo;

import java.math.BigDecimal;

/**
 * O que a IA entendeu de uma frase.
 *
 * @param nomeConta     nome como a pessoa escreveu
 * @param valor         valor dito, ou null
 * @param bloco         bloco dito explicitamente, ou null
 * @param diaVencimento dia do mês dito para o vencimento, ou null
 * @param confianca     de 0 a 1
 * @param entrega       só para ENVIAR: o que mandar (link, resumo ou planilha), ou null
 * @param mes           só para ENVIAR um resumo: número do mês pedido (1 a 12), ou null para o mês atual
 */
public record Acao(
        TipoAcao tipo,
        String nomeConta,
        BigDecimal valor,
        Bloco bloco,
        Integer diaVencimento,
        double confianca,
        Entrega entrega,
        Integer mes) {

    /** Ação que não é de enviar: sem entrega nem mês. */
    public Acao(TipoAcao tipo, String nomeConta, BigDecimal valor, Bloco bloco, Integer diaVencimento,
            double confianca) {
        this(tipo, nomeConta, valor, bloco, diaVencimento, confianca, null, null);
    }

    public Acao comBloco(Bloco novoBloco) {
        return new Acao(tipo, nomeConta, valor, novoBloco, diaVencimento, confianca, entrega, mes);
    }

    public Acao comNomeConta(String novoNome) {
        return new Acao(tipo, novoNome, valor, bloco, diaVencimento, confianca, entrega, mes);
    }
}
