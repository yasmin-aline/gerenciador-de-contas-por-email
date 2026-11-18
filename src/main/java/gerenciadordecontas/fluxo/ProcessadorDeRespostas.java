package gerenciadordecontas.fluxo;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import gerenciadordecontas.aviso.ResumoComposer;
import gerenciadordecontas.config.Settings;
import gerenciadordecontas.estado.Estado;
import gerenciadordecontas.estado.EntradaEstado;
import gerenciadordecontas.estado.RegistroMudancas;
import gerenciadordecontas.ia.InterpretadorDeTexto;
import gerenciadordecontas.modelo.Acao;
import gerenciadordecontas.modelo.Bloco;
import gerenciadordecontas.modelo.CelulaMudanca;
import gerenciadordecontas.modelo.Conta;
import gerenciadordecontas.modelo.Entrega;
import gerenciadordecontas.modelo.TipoAcao;
import gerenciadordecontas.nomes.ContaMatcher;
import gerenciadordecontas.nomes.ResultadoBusca;
import gerenciadordecontas.nomes.Texto;
import gerenciadordecontas.planilha.Dinheiro;
import gerenciadordecontas.planilha.Meses;
import gerenciadordecontas.planilha.PlanilhaContas;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Processa uma resposta sua: entende (IA), casa com as contas, confere se é seguro, grava na planilha,
 * registra no log e devolve o texto do e-mail de confirmação.
 */
public final class ProcessadorDeRespostas {

    private static final String PREFIXO_PENDENTE = "pendente:";
    private static final Duration VALIDADE_PENDENTE = Duration.ofDays(3);
    private static final Set<String> SIM = Set.of("sim", "s", "confirmo", "confirmar", "ok", "pode", "pode sim", "isso");
    private static final Set<String> NAO = Set.of("nao", "n", "cancela", "cancelar", "nao quero");

    private final Gson gson = new Gson();
    private final PlanilhaContas planilha;
    private final ContaMatcher matcher;
    private final InterpretadorDeTexto interpretador;
    private final RegistroMudancas registro;
    private final Estado estado;
    private final Settings settings;
    private final Entregador entregador;
    private final Clock relogio;

    public ProcessadorDeRespostas(
            PlanilhaContas planilha,
            ContaMatcher matcher,
            InterpretadorDeTexto interpretador,
            RegistroMudancas registro,
            Estado estado,
            Settings settings,
            Entregador entregador,
            Clock relogio) {
        this.planilha = planilha;
        this.matcher = matcher;
        this.interpretador = interpretador;
        this.registro = registro;
        this.estado = estado;
        this.settings = settings;
        this.entregador = entregador;
        this.relogio = relogio;
    }

    /** Recebe o texto já sem citações e devolve o texto do e-mail de confirmação. */
    public String processar(String texto) {
        String comando = Texto.normalizar(texto);

        if (comando.equals("desfazer") || comando.startsWith("desfazer ")) {
            return desfazer();
        }

        Pendente pendente = ultimoPendente();
        if (pendente != null) {
            if (SIM.contains(comando)) {
                fechar(pendente);
                return executar(pendente.acao(), pendente.mes(), true);
            }
            if (NAO.contains(comando)) {
                fechar(pendente);
                return "Ok, cancelei: " + descrever(pendente.acao()) + ". Nada foi alterado.";
            }
            if (pendente.tipo() == TipoPendente.BLOCO) {
                Bloco bloco = tentarBloco(comando);
                if (bloco != null) {
                    fechar(pendente);
                    return executar(pendente.acao().comBloco(bloco), pendente.mes(), false);
                }
            }
        }

        int mes = agora().getMonthValue();
        List<Conta> contas = planilha.lerMes(mes);
        List<Acao> acoes = interpretador.interpretar(texto, contas.stream().map(Conta::nome).toList());

        if (acoes.isEmpty()) {
            return "Não entendi nenhuma ação nessa mensagem. Exemplos:\n  paguei a internet, deu 130\n"
                    + "  nova conta: IPTU, 400, fim do mês\n  desfazer";
        }

        List<String> linhas = new ArrayList<>();
        for (Acao acao : acoes) {
            linhas.add(executar(acao, mes, false));
        }
        linhas.add("Se algo ficou errado, responda \"desfazer\" para reverter a última mudança.");
        return String.join("\n\n", linhas);
    }

    private String executar(Acao acao, int mes, boolean confirmada) {
        return switch (acao.tipo()) {
            case DESFAZER -> desfazer();
            case PAGAR -> pagar(acao, mes, confirmada);
            case CADASTRAR -> cadastrar(acao, mes, confirmada);
            case ENVIAR -> enviar(acao, mes);
            case DESCONHECIDO -> acao.nomeConta() == null || acao.nomeConta().isBlank()
                    ? "✗ Não entendi essa parte da mensagem. Nada foi alterado."
                    : "✗ Não entendi o que fazer com \"" + acao.nomeConta() + "\". Nada foi alterado.";
        };
    }

    private String pagar(Acao acao, int mes, boolean confirmada) {
        String aba = Meses.aba(mes);
        List<Conta> contas = planilha.lerMes(mes);
        ResultadoBusca busca = matcher.buscar(acao.nomeConta(), contas);

        if (busca.estado() == ResultadoBusca.Estado.NAO_ENCONTRADA) {
            String nomes = contas.isEmpty() ? "(nenhuma)" : nomesDe(contas);
            return "✗ Não achei \"" + acao.nomeConta() + "\" na aba " + aba + ". Contas do mês: " + nomes
                    + ". Nada foi alterado.";
        }
        Conta conta;
        boolean desempatada = false;
        if (busca.estado() == ResultadoBusca.Estado.AMBIGUA) {
            conta = desempatar(busca.candidatas(), acao);
            if (conta == null) {
                return "✗ \"" + acao.nomeConta() + "\" pode ser mais de uma conta ("
                        + nomesComBloco(busca.candidatas()) + "). Responda de novo dizendo o bloco, "
                        + "por exemplo: \"paguei " + acao.nomeConta() + " do dia 15\". Nada foi alterado.";
            }
            desempatada = true;
        } else {
            conta = busca.conta();
        }

        if (acao.valor() != null && acao.valor().signum() <= 0) {
            return "✗ O valor informado para " + conta.nome() + " não é válido. Nada foi alterado.";
        }

        if (!confirmada) {
            String motivo = motivoParaConfirmar(acao, conta);
            if (motivo != null) {
                return pedirConfirmacao(acao.comNomeConta(conta.nome()), mes, motivo);
            }
        }

        boolean mesmoValor = acao.valor() == null || iguais(acao.valor(), conta.valor());
        if (conta.pago() && mesmoValor) {
            return "ℹ " + conta.nome() + " já estava como Pago (" + valorOuVazio(conta.valor())
                    + "). Nada foi alterado.";
        }

        List<CelulaMudanca> mudancas = planilha.marcarPago(mes, conta, acao.valor());
        registro.registrar(novoGrupo(), "Pagar " + conta.nome() + " (" + aba + ")", mudancas);

        BigDecimal valorFinal = acao.valor() != null ? acao.valor() : conta.valor();
        String texto = "✓ " + conta.nome() + " (" + aba + ", bloco " + conta.bloco().nome() + "): "
                + (conta.pago() ? "Pago" : "Pendente") + " → Pago, " + valorOuVazio(valorFinal);
        if (acao.valor() != null && conta.valor() != null && !iguais(acao.valor(), conta.valor())) {
            texto += " (previsto " + Dinheiro.formatar(conta.valor()) + ")";
        }
        texto += ".";
        if (desempatada) {
            texto += " (Havia mais de uma conta com esse nome; usei esta. Se não era ela, responda \"desfazer\".)";
        }
        return texto;
    }

    /**
     * Duas contas com o mesmo nome (ex.: "Cartão Inter" nos dois blocos). Usa o bloco, se a pessoa
     * disse qual, e depois a única que ainda está pendente. Devolve null se continuar sem saber.
     */
    private static Conta desempatar(List<Conta> candidatas, Acao acao) {
        List<Conta> doBloco = candidatas;
        if (acao.bloco() != null) {
            List<Conta> filtradas = candidatas.stream().filter(c -> c.bloco() == acao.bloco()).toList();
            if (!filtradas.isEmpty()) {
                doBloco = filtradas;
            }
        }
        if (doBloco.size() == 1) {
            return doBloco.get(0);
        }
        List<Conta> pendentes = doBloco.stream().filter(c -> !c.pago()).toList();
        return pendentes.size() == 1 ? pendentes.get(0) : null;
    }

    private String cadastrar(Acao acao, int mes, boolean confirmada) {
        String aba = Meses.aba(mes);

        if (acao.nomeConta() == null || acao.nomeConta().isBlank()) {
            return "✗ Faltou o nome da conta nova. Nada foi alterado.";
        }
        if (acao.valor() == null || acao.valor().signum() <= 0) {
            return "✗ Para cadastrar \"" + acao.nomeConta() + "\" preciso do valor. Exemplo: nova conta: "
                    + acao.nomeConta() + ", 400, fim do mês. Nada foi alterado.";
        }
        if (acao.bloco() == null) {
            return pedirBloco(acao, mes);
        }

        Bloco bloco = acao.bloco();
        List<Conta> contas = planilha.lerMes(mes);

        if (matcher.existeIgual(acao.nomeConta(), contas)) {
            return "ℹ Já existe uma conta \"" + acao.nomeConta() + "\" na aba " + aba + ". Nada foi alterado.";
        }

        if (!confirmada && acao.confianca() < settings.confiancaMinima()) {
            return pedirConfirmacao(acao, mes, "não tenho certeza de que entendi essa conta nova direito");
        }

        OptionalInt linha = planilha.acharVaga(mes, bloco);
        if (linha.isEmpty()) {
            return "✗ O bloco " + bloco.nome() + " de " + aba + " está sem vaga livre. Não inseri linha nova para "
                    + "não quebrar as somas do Resumo. Libere uma linha na planilha e envie de novo. "
                    + "Nada foi alterado.";
        }

        List<CelulaMudanca> mudancas = planilha.cadastrar(mes, bloco, linha.getAsInt(), acao.nomeConta(), acao.valor());
        registro.registrar(novoGrupo(), "Cadastrar " + acao.nomeConta() + " (" + aba + ")", mudancas);

        String texto = "✓ Cadastrei " + acao.nomeConta() + ": " + Dinheiro.formatar(acao.valor()) + ", bloco "
                + bloco.nome() + ", linha " + linha.getAsInt() + " de " + aba + ", Pendente.";
        if (acao.diaVencimento() != null) {
            texto += " (O vencimento dia " + acao.diaVencimento()
                    + " não foi gravado: a planilha não tem coluna de vencimento.)";
        }
        return texto;
    }

    /** Pedido para receber a planilha: link, resumo em texto ou o arquivo em anexo. Não altera nada na planilha. */
    private String enviar(Acao acao, int mesAtual) {
        Entrega entrega = acao.entrega() != null ? acao.entrega() : Entrega.RESUMO;
        String link = entregador.linkDaPlanilha();

        switch (entrega) {
            case LINK:
                return "Aqui está a planilha:\n" + link;
            case ARQUIVO:
                try {
                    String data = agora().toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                    entregador.enviarPlanilhaAnexada("[Contas] Sua planilha de " + data,
                            "Segue a planilha em anexo (.xlsx), como estava em " + data + ".\n\nPara abrir online:\n"
                                    + link + "\n");
                    return "✓ Mandei a planilha em anexo, em um e-mail separado.\nPara abrir online:\n" + link;
                } catch (RuntimeException e) {
                    return "✗ Não consegui mandar a planilha em anexo: " + e.getMessage() + "\nO link é:\n" + link;
                }
            default:
                int mes = acao.mes() != null ? acao.mes() : mesAtual;
                try {
                    List<Conta> contas = planilha.lerMes(mes);
                    return ResumoComposer.montar(mes, agora().getYear(), contas, settings)
                            + "\nPlanilha:\n" + link;
                } catch (RuntimeException e) {
                    return "✗ Não consegui ler a aba " + Meses.aba(mes) + ": " + e.getMessage() + "\nO link é:\n"
                            + link;
                }
        }
    }

    private String desfazer() {
        return registro.desfazerUltimo()
                .map(d -> "✓ Desfeito: " + d.descricao() + " (" + d.celulas() + " célula(s) restaurada(s)).")
                .orElse("Não há nenhuma mudança para desfazer.");
    }

    private String motivoParaConfirmar(Acao acao, Conta conta) {
        if (acao.confianca() < settings.confiancaMinima()) {
            return "não tenho certeza de que entendi direito";
        }
        if (acao.valor() != null && conta.valor() != null && conta.valor().signum() > 0) {
            BigDecimal desvio = acao.valor().subtract(conta.valor()).abs()
                    .divide(conta.valor(), 4, RoundingMode.HALF_UP);
            if (desvio.compareTo(settings.limiteDesvio()) > 0) {
                return "o valor informado (" + Dinheiro.formatar(acao.valor()) + ") foge bastante do previsto ("
                        + Dinheiro.formatar(conta.valor()) + ")";
            }
        }
        return null;
    }

    private String pedirConfirmacao(Acao acao, int mes, String motivo) {
        guardar(new Pendente(novoId(), TipoPendente.CONFIRMAR, agoraIso(), acao, mes, motivo));
        return "⚠ Antes de mexer na planilha, preciso da sua confirmação: " + descrever(acao) + ", porque " + motivo
                + ".\nResponda \"sim\" para confirmar ou \"não\" para cancelar.";
    }

    private String pedirBloco(Acao acao, int mes) {
        guardar(new Pendente(novoId(), TipoPendente.BLOCO, agoraIso(), acao, mes, "faltou dizer o bloco"));
        return "⚠ Em qual bloco entra a conta " + acao.nomeConta() + " ("
                + Dinheiro.formatar(acao.valor() != null ? acao.valor() : BigDecimal.ZERO)
                + ")?\nResponda \"dia 15\" ou \"fim do mês\".";
    }

    private void guardar(Pendente pendente) {
        estado.gravar(PREFIXO_PENDENTE + pendente.id(), gson.toJson(pendente));
    }

    private void fechar(Pendente pendente) {
        estado.gravar(PREFIXO_PENDENTE + pendente.id(), "");
    }

    private Pendente ultimoPendente() {
        Pendente maisRecente = null;
        LocalDateTime maisRecenteEm = null;

        for (EntradaEstado entrada : estado.listar(PREFIXO_PENDENTE)) {
            if (entrada.valor().isBlank()) {
                continue; // já resolvida
            }
            Pendente candidato;
            LocalDateTime criado;
            try {
                candidato = gson.fromJson(entrada.valor(), Pendente.class);
                criado = LocalDateTime.parse(candidato.criado());
            } catch (JsonParseException | NullPointerException | java.time.format.DateTimeParseException e) {
                continue;
            }
            if (Duration.between(criado, agora()).compareTo(VALIDADE_PENDENTE) > 0) {
                continue;
            }
            if (maisRecente == null || criado.isAfter(maisRecenteEm)) {
                maisRecente = candidato;
                maisRecenteEm = criado;
            }
        }
        return maisRecente;
    }

    private static Bloco tentarBloco(String comandoNormalizado) {
        if (comandoNormalizado.contains("fim")) {
            return Bloco.FIM_DO_MES;
        }
        if (comandoNormalizado.contains("15")) {
            return Bloco.DIA_15;
        }
        return null;
    }

    private static String descrever(Acao acao) {
        String valor = acao.valor() != null ? " por " + Dinheiro.formatar(acao.valor()) : "";
        return switch (acao.tipo()) {
            case PAGAR -> "marcar " + acao.nomeConta() + " como pago" + valor;
            case CADASTRAR -> "cadastrar a conta " + acao.nomeConta() + valor
                    + (acao.bloco() != null ? ", bloco " + acao.bloco().nome() : "");
            default -> acao.nomeConta();
        };
    }

    private static String nomesDe(List<Conta> contas) {
        return contas.stream().map(Conta::nome).collect(Collectors.joining(", "));
    }

    private static String nomesComBloco(List<Conta> contas) {
        return contas.stream()
                .map(c -> c.nome() + " [" + c.bloco().nome() + "]")
                .collect(Collectors.joining(", "));
    }

    private static boolean iguais(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }

    private static String valorOuVazio(BigDecimal valor) {
        return valor != null ? Dinheiro.formatar(valor) : "sem valor";
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(relogio);
    }

    private String agoraIso() {
        return agora().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private static String novoId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static String novoGrupo() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
