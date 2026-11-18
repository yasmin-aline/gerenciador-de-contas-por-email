package gerenciadordecontas.aviso;

import gerenciadordecontas.config.Settings;
import gerenciadordecontas.modelo.Bloco;
import gerenciadordecontas.modelo.Conta;
import gerenciadordecontas.planilha.Dinheiro;
import gerenciadordecontas.planilha.Meses;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Monta o e-mail de aviso: o que vence, o que entra e o saldo previsto do mês. */
public final class AvisoComposer {

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    private AvisoComposer() {
    }

    public static AvisoEmail montar(
            LocalDate hoje, LocalDate ancora, Bloco bloco, List<Conta> contas, Settings settings) {
        long dias = ChronoUnit.DAYS.between(hoje, ancora);
        String quando = dias <= 0 ? "hoje" : dias == 1 ? "amanhã" : "em " + dias + " dias";

        List<Conta> doBloco = contas.stream().filter(c -> c.bloco() == bloco && !c.pago()).toList();
        Bloco outroBloco = bloco == Bloco.DIA_15 ? Bloco.FIM_DO_MES : Bloco.DIA_15;
        List<Conta> doOutro = contas.stream().filter(c -> c.bloco() == outroBloco && !c.pago()).toList();

        BigDecimal entradas = settings.recebeDia15().add(settings.recebeFimMes());
        BigDecimal totalContas = total(contas);
        BigDecimal saldo = entradas.subtract(totalContas);

        String assunto = "[Contas] " + bloco.titulo() + " vence " + quando + " — " + doBloco.size() + " pendente(s)";

        StringBuilder sb = new StringBuilder();
        sb.append("Resumo de ").append(Meses.nomeCompleto(ancora.getMonthValue())).append('/').append(ancora.getYear())
                .append(": ").append(bloco.titulo()).append(" (").append(ancora.format(DIA_MES)).append(", ")
                .append(quando).append(").\n\n");

        if (doBloco.isEmpty()) {
            sb.append("Nada pendente neste bloco.\n");
        } else {
            sb.append("A pagar neste bloco (").append(doBloco.size()).append("):\n");
            for (Conta c : doBloco) {
                sb.append("  • ").append(linha(c)).append('\n');
            }
            sb.append("Total pendente do bloco: ").append(Dinheiro.formatar(total(doBloco))).append('\n');
        }

        sb.append("\nEntra em ").append(ancora.format(DIA_MES)).append(": ")
                .append(Dinheiro.formatar(settings.recebe(bloco))).append('\n');

        if (!doOutro.isEmpty()) {
            sb.append("\nAinda pendente no bloco ").append(outroBloco.nome()).append(" (").append(doOutro.size())
                    .append("):\n");
            for (Conta c : doOutro) {
                sb.append("  • ").append(linha(c)).append('\n');
            }
        }

        sb.append("\nSaldo previsto do mês: ").append(Dinheiro.formatar(saldo)).append('\n');
        sb.append("  (entradas ").append(Dinheiro.formatar(entradas)).append(" − contas ")
                .append(Dinheiro.formatar(totalContas)).append(")\n");

        sb.append("\nPara dar baixa, responda este e-mail, por exemplo:\n");
        sb.append("  ").append(exemploDeBaixa(doBloco.isEmpty() ? doOutro : doBloco)).append('\n');
        sb.append("Outros comandos:\n");
        sb.append("  nova conta: IPTU, 400, fim do mês\n");
        sb.append("  me manda a planilha  (também: o link, ou o resumo do mês)\n");
        sb.append("  desfazer\n");

        return new AvisoEmail(assunto, sb.toString());
    }

    /** Exemplo com contas que realmente estão pendentes; sem nenhuma, usa um exemplo genérico. */
    private static String exemploDeBaixa(List<Conta> pendentes) {
        if (pendentes.isEmpty()) {
            return "paguei a internet, deu 130, e a luz, 210";
        }
        Conta primeira = pendentes.get(0);
        String exemplo = "paguei " + primeira.nome()
                + (primeira.valor() != null ? ", deu " + Dinheiro.formatar(primeira.valor()) : "");
        if (pendentes.size() > 1) {
            exemplo += ", e " + pendentes.get(1).nome();
        }
        return exemplo;
    }

    private static BigDecimal total(List<Conta> contas) {
        BigDecimal soma = BigDecimal.ZERO;
        for (Conta c : contas) {
            if (c.valor() != null) {
                soma = soma.add(c.valor());
            }
        }
        return soma;
    }

    private static String linha(Conta conta) {
        return conta.nome() + " — " + (conta.valor() != null ? Dinheiro.formatar(conta.valor()) : "sem valor");
    }
}
