package gerenciadordecontas.nomes;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Set;

public final class Texto {

    // Palavras que não ajudam a distinguir contas: "Conta de Luz", "Fatura Nubank"...
    private static final Set<String> IGNORADAS = Set.of(
            "conta", "contas", "de", "da", "do", "das", "dos", "fatura", "a", "o", "as", "os");

    private Texto() {
    }

    /** Minúsculas, sem acento, só letras e números separados por um espaço. */
    public static String normalizar(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String decomposto = Normalizer.normalize(texto, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(decomposto.length());
        for (int i = 0; i < decomposto.length(); i++) {
            char ch = decomposto.charAt(i);
            if (Character.getType(ch) == Character.NON_SPACING_MARK) {
                continue;
            }
            sb.append(Character.isLetterOrDigit(ch) ? Character.toLowerCase(ch) : ' ');
        }
        return String.join(" ", Arrays.stream(sb.toString().split(" ")).filter(p -> !p.isEmpty()).toList());
    }

    /** Palavras significativas: normalizadas, sem "conta", "de", "fatura"... */
    public static String[] tokens(String texto) {
        return Arrays.stream(normalizar(texto).split(" "))
                .filter(p -> !p.isEmpty() && !IGNORADAS.contains(p))
                .toArray(String[]::new);
    }

    public static String chaveDeConta(String texto) {
        return String.join(" ", tokens(texto));
    }
}
