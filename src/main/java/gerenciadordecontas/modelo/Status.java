package gerenciadordecontas.modelo;

/** Textos da coluna "Pago?", iguais às opções da lista suspensa da planilha. */
public final class Status {
    public static final String PAGO = "✅ Pago";
    public static final String PENDENTE = "⏳ Pendente";

    private Status() {
    }

    /**
     * Lê "✅ Pago", "Pago" e "pago" como pago. Vazio, "Pendente", "⏳ Pendente" e "❌ Não pago"
     * não são pago.
     */
    public static boolean ehPago(String texto) {
        if (texto == null || texto.isBlank()) {
            return false;
        }
        String limpo = texto.replace("✅", "").strip();
        return limpo.equalsIgnoreCase("Pago");
    }
}
