package gerenciadordecontas.planilha;

import java.util.List;
import java.util.Map;

/**
 * Porta de acesso à planilha. O resto do programa só conhece esta interface,
 * então dá para testar com uma planilha falsa em memória.
 */
public interface SheetsGateway {

    /** Títulos de todas as abas da planilha. */
    List<String> listarAbas();

    /** Lê um intervalo (ex.: "A4:C8" ou "A:C") como texto, do jeito que aparece na planilha. */
    List<List<String>> lerIntervalo(String aba, String intervalo);

    /** Grava células soltas (endereço como "C5"). BigDecimal entra como número; texto vazio limpa a célula. */
    void escreverCelulas(String aba, Map<String, Object> celulas);

    /** Cria a aba se não existir. Devolve true se criou agora. */
    boolean garantirAba(String aba);

    /** Acrescenta uma linha no fim da aba. */
    void acrescentarLinha(String aba, List<Object> linha);

    /** Baixa a planilha inteira como arquivo Excel (.xlsx). */
    default byte[] exportarXlsx() {
        throw new UnsupportedOperationException("Esta planilha não sabe exportar para .xlsx.");
    }

    /** Lê uma célula só. Célula vazia volta como texto vazio. */
    default String lerCelula(String aba, String celula) {
        return Grade.celula(lerIntervalo(aba, celula), 0, 0);
    }
}
