package gerenciadordecontas.config;

import java.util.Optional;
import java.util.function.Function;

/** Provedor, chave e modelo da IA que entende o texto livre. O modelo reserva (opcional) só é usado se o principal estiver sobrecarregado. */
public record AiConfig(Provedor provedor, String chaveApi, String modelo, String modeloReserva) {

    public enum Provedor { GEMINI, ANTHROPIC }

    public static final String MODELO_GEMINI_PADRAO = "gemini-flash-latest";
    public static final String MODELO_ANTHROPIC_PADRAO = "claude-haiku-5-5";

    /**
     * GEMINI_API_KEY tem prioridade; sem ela, usa ANTHROPIC_API_KEY. Vazio quando não há nenhuma:
     * avisos funcionam, texto livre não.
     */
    public static Optional<AiConfig> deAmbiente() {
        return de(Env::opcional);
    }

    /** Versão testável: {@code variavel} devolve o valor de uma variável ou null. */
    public static Optional<AiConfig> de(Function<String, String> variavel) {
        String gemini = variavel.apply("GEMINI_API_KEY");
        if (gemini != null) {
            String modelo = variavel.apply("GEMINI_MODEL");
            return Optional.of(new AiConfig(Provedor.GEMINI, gemini, modelo != null ? modelo : MODELO_GEMINI_PADRAO,
                    variavel.apply("GEMINI_MODEL_FALLBACK")));
        }
        String anthropic = variavel.apply("ANTHROPIC_API_KEY");
        if (anthropic != null) {
            String modelo = variavel.apply("ANTHROPIC_MODEL");
            return Optional.of(
                    new AiConfig(Provedor.ANTHROPIC, anthropic, modelo != null ? modelo : MODELO_ANTHROPIC_PADRAO, null));
        }
        return Optional.empty();
    }
}
