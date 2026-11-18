package gerenciadordecontas.planilha;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Valores em reais no formato brasileiro ("R$ 1.234,56"), sem depender do idioma do computador. */
public final class Dinheiro {

    // 1.234,56 | 1234,56 | 130 | -10,5
    private static final Pattern PT_BR = Pattern.compile("^-?(\\d{1,3}(\\.\\d{3})+|\\d+)(,\\d+)?$");

    private Dinheiro() {
    }

    public static Optional<BigDecimal> ler(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        String limpo = texto.replace("R$", "").replace(" ", " ").replaceAll("\\s+", "");
        if (!PT_BR.matcher(limpo).matches()) {
            return Optional.empty();
        }
        return Optional.of(new BigDecimal(limpo.replace(".", "").replace(",", ".")));
    }

    public static String formatar(BigDecimal valor) {
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols(Locale.ROOT);
        simbolos.setGroupingSeparator('.');
        simbolos.setDecimalSeparator(',');
        DecimalFormat formato = new DecimalFormat("#,##0.00", simbolos);
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return "R$ " + formato.format(valor);
    }

    /** Número em texto simples, com ponto decimal (para guardar no log). */
    public static String simples(BigDecimal valor) {
        return valor.toPlainString();
    }
}
