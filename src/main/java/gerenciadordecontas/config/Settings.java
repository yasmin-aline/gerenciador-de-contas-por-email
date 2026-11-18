package gerenciadordecontas.config;

import gerenciadordecontas.modelo.Bloco;
import java.math.BigDecimal;
import java.util.function.Function;

/**
 * Regras ajustáveis. Todas têm padrão; só mude se precisar.
 *
 * @param recebeDia15     quanto entra no dia 15
 * @param recebeFimMes    quanto entra no fim do mês
 * @param limiteDesvio    pede confirmação se o valor pago diferir mais que isso do previsto (0.30 = 30%)
 * @param confiancaMinima pede confirmação se a IA tiver menos confiança que isso
 * @param arquivoApelidos caminho do apelidos.json, ou null para o padrão
 */
public record Settings(
        BigDecimal recebeDia15,
        BigDecimal recebeFimMes,
        BigDecimal limiteDesvio,
        double confiancaMinima,
        String arquivoApelidos) {

    public static Settings padrao() {
        return new Settings(new BigDecimal("2200"), new BigDecimal("1800"), new BigDecimal("0.30"), 0.75, null);
    }

    public static Settings deAmbiente() {
        return de(Env::opcional);
    }

    /** Lê as configurações de qualquer fonte de variáveis (facilita testar). Use ponto decimal: 2200.50. */
    public static Settings de(Function<String, String> variavel) {
        Settings padrao = padrao();
        return new Settings(
                ler(variavel, "RECEBE_DIA15", padrao.recebeDia15),
                ler(variavel, "RECEBE_FIM_MES", padrao.recebeFimMes),
                ler(variavel, "LIMITE_DESVIO", padrao.limiteDesvio),
                ler(variavel, "CONFIANCA_MINIMA", BigDecimal.valueOf(padrao.confiancaMinima)).doubleValue(),
                variavel.apply("APELIDOS_FILE"));
    }

    public BigDecimal recebe(Bloco bloco) {
        return bloco == Bloco.DIA_15 ? recebeDia15 : recebeFimMes;
    }

    private static BigDecimal ler(Function<String, String> variavel, String nome, BigDecimal padrao) {
        String texto = variavel.apply(nome);
        if (texto == null || texto.isBlank()) {
            return padrao;
        }
        try {
            return new BigDecimal(texto.strip());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(nome + " inválido: \"" + texto + "\" (use ponto decimal, ex.: 2200.50).");
        }
    }
}
