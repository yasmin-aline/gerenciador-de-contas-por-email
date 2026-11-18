package gerenciadordecontas.email;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Tira do e-mail de resposta o texto citado (a mensagem do bot que vem embaixo). */
public final class Citacao {

    private static final Pattern ATRIBUICAO = Pattern.compile("^(Em|On)\\s.+", Pattern.CASE_INSENSITIVE);
    private static final String[] TERMINADORES = {"escreveu:", "wrote:"};

    private Citacao() {
    }

    public static String limpar(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }

        String[] linhas = texto.replace("\r\n", "\n").split("\n", -1);
        List<String> mantidas = new ArrayList<>();

        for (int i = 0; i < linhas.length; i++) {
            if (linhas[i].stripLeading().startsWith(">") || ehAtribuicao(linhas, i)) {
                break;
            }
            mantidas.add(linhas[i]);
        }
        return String.join("\n", mantidas).strip();
    }

    // "Em seg., 16 de nov. de 2026 às 18:00, Fulano <x@y> escreveu:" (o Gmail às vezes quebra em duas linhas)
    private static boolean ehAtribuicao(String[] linhas, int i) {
        if (!ATRIBUICAO.matcher(linhas[i].strip()).matches()) {
            return false;
        }
        for (int j = i; j <= i + 1 && j < linhas.length; j++) {
            String minuscula = linhas[j].toLowerCase();
            for (String terminador : TERMINADORES) {
                if (minuscula.contains(terminador)) {
                    return true;
                }
            }
        }
        return false;
    }
}
