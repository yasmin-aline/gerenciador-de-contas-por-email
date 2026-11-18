package gerenciadordecontas;

/** Planilha de novembro de exemplo, no layout descrito no plano. */
public final class PlanilhaDeExemplo {

    private PlanilhaDeExemplo() {
    }

    public static FakeSheets novembro() {
        FakeSheets s = new FakeSheets().cria("Nov");

        // Bloco Dia 15 (A, B, C) e Fim do mês (E, F, G), linhas 4 a 8.
        s.set("Nov", "A4", "Conta de Luz").set("Nov", "B4", "R$ 200,00").set("Nov", "C4", "Pendente");
        s.set("Nov", "A5", "Internet").set("Nov", "B5", "R$ 120,00").set("Nov", "C5", " ✅ Pago");
        s.set("Nov", "A6", "Inglês").set("Nov", "B6", "R$ 300,00").set("Nov", "C6", "Pendente");

        s.set("Nov", "E4", "Cartão Inter").set("Nov", "F4", "R$ 900,00").set("Nov", "G4", "Pendente");
        s.set("Nov", "E5", "Fatura Nubank").set("Nov", "F5", "R$ 450,00").set("Nov", "G5", "Pago");

        // Fora das vagas: totais e anotações. O programa nunca pode ler nem mexer.
        s.set("Nov", "A9", "Total").set("Nov", "B9", "R$ 620,00");
        s.set("Nov", "E9", "Total").set("Nov", "F9", "R$ 1.350,00");
        s.set("Nov", "A11", "Pendentes 29/05");
        return s;
    }
}
