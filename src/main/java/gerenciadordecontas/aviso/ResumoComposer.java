package gerenciadordecontas.aviso;

import gerenciadordecontas.config.Settings;
import gerenciadordecontas.modelo.Bloco;
import gerenciadordecontas.modelo.Conta;
import gerenciadordecontas.planilha.Dinheiro;
import gerenciadordecontas.planilha.Meses;
import java.math.BigDecimal;
import java.util.List;

/** Texto com a situação de um mês: o que já foi pago, o que falta e o saldo previsto. */
public final class ResumoComposer {

    private ResumoComposer() {
    }

    public static String montar(int mes, int ano, List<Conta> contas, Settings settings) {
        StringBuilder sb = new StringBuilder();
        sb.append("Resumo de ").append(Meses.nomeCompleto(mes)).append('/').append(ano).append(":\n");

        for (Bloco bloco : Bloco.values()) {
            sb.append('\n').append(bloco.titulo()).append(":\n");
            List<Conta> doBloco = contas.stream().filter(c -> c.bloco() == bloco).toList();
            if (doBloco.isEmpty()) {
                sb.append("  (nenhuma conta)\n");
            }
            for (Conta c : doBloco) {
                sb.append("  ").append(c.pago() ? "✅" : "⏳").append(' ').append(c.nome()).append(" — ")
                        .append(c.valor() != null ? Dinheiro.formatar(c.valor()) : "sem valor").append('\n');
            }
        }

        List<Conta> pagas = contas.stream().filter(Conta::pago).toList();
        List<Conta> pendentes = contas.stream().filter(c -> !c.pago()).toList();
        BigDecimal entradas = settings.recebeDia15().add(settings.recebeFimMes());
        BigDecimal totalContas = total(contas);

        sb.append("\nPago: ").append(Dinheiro.formatar(total(pagas))).append(" (").append(pagas.size())
                .append(" conta(s))\n");
        sb.append("Falta pagar: ").append(Dinheiro.formatar(total(pendentes))).append(" (")
                .append(pendentes.size()).append(" conta(s))\n");
        sb.append("Saldo previsto do mês: ").append(Dinheiro.formatar(entradas.subtract(totalContas)))
                .append("\n  (entradas ").append(Dinheiro.formatar(entradas)).append(" − contas ")
                .append(Dinheiro.formatar(totalContas)).append(")\n");
        return sb.toString();
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
}
