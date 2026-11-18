package gerenciadordecontas.planilha;

import java.util.List;

public final class Grade {

    private Grade() {
    }

    /** Célula de um resultado de leitura, ou texto vazio se estiver fora (o Google corta células vazias do fim). */
    public static String celula(List<List<String>> linhas, int linha, int coluna) {
        if (linha < 0 || linha >= linhas.size()) {
            return "";
        }
        List<String> colunas = linhas.get(linha);
        if (coluna < 0 || coluna >= colunas.size()) {
            return "";
        }
        return colunas.get(coluna);
    }
}
