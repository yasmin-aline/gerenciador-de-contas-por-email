package gerenciadordecontas.planilha;

import java.util.List;

/** Nomes das abas de mês: três letras em português (Jan, Fev, ... Dez). */
public final class Meses {

    private static final List<String> ABAS =
            List.of("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez");

    private static final List<String> COMPLETOS = List.of(
            "janeiro", "fevereiro", "março", "abril", "maio", "junho",
            "julho", "agosto", "setembro", "outubro", "novembro", "dezembro");

    private Meses() {
    }

    public static List<String> abas() {
        return ABAS;
    }

    public static String aba(int mes) {
        validar(mes);
        return ABAS.get(mes - 1);
    }

    public static String nomeCompleto(int mes) {
        validar(mes);
        return COMPLETOS.get(mes - 1);
    }

    private static void validar(int mes) {
        if (mes < 1 || mes > 12) {
            throw new IllegalArgumentException("O mês deve estar entre 1 e 12, mas veio " + mes + ".");
        }
    }
}
