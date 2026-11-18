package gerenciadordecontas.planilha;

import gerenciadordecontas.modelo.Bloco;
import java.util.List;

/**
 * Onde ficam as vagas nas abas de mês. O programa procura cada conta pelo NOME, dentro destas vagas;
 * os limites existem só para nunca ler os totais e as anotações que ficam abaixo dos blocos.
 */
public final class MesLayout {

    /** Colunas de um bloco (0 = coluna A). */
    public record BlocoLayout(Bloco bloco, int colNome, int colValor, int colPago) {
    }

    /** Primeira e última linha das vagas (1 = primeira linha da planilha). */
    public record Vagas(int primeira, int ultima) {
    }

    public static final BlocoLayout DIA_15 = new BlocoLayout(Bloco.DIA_15, 0, 1, 2); // A, B, C
    public static final BlocoLayout FIM_DO_MES = new BlocoLayout(Bloco.FIM_DO_MES, 4, 5, 6); // E, F, G

    private MesLayout() {
    }

    public static List<BlocoLayout> blocos() {
        return List.of(DIA_15, FIM_DO_MES);
    }

    public static BlocoLayout de(Bloco bloco) {
        return bloco == Bloco.DIA_15 ? DIA_15 : FIM_DO_MES;
    }

    /** Jan a Abr: linhas 5-9. Mai: 4-7. Jun a Dez: 4-8. */
    public static Vagas vagas(int mes) {
        if (mes >= 1 && mes <= 4) {
            return new Vagas(5, 9);
        }
        if (mes == 5) {
            return new Vagas(4, 7);
        }
        if (mes >= 6 && mes <= 12) {
            return new Vagas(4, 8);
        }
        throw new IllegalArgumentException("O mês deve estar entre 1 e 12, mas veio " + mes + ".");
    }

    public static String coluna(int coluna) {
        return String.valueOf((char) ('A' + coluna));
    }

    public static String celula(int coluna, int linha) {
        return coluna(coluna) + linha;
    }

    /** Intervalo lido de uma aba de mês: colunas A a G, só nas linhas das vagas. */
    public static String intervaloDeLeitura(int mes) {
        Vagas vagas = vagas(mes);
        return "A" + vagas.primeira() + ":G" + vagas.ultima();
    }
}
