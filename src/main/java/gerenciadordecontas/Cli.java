package gerenciadordecontas;

import gerenciadordecontas.fluxo.Orquestrador;
import gerenciadordecontas.ia.ComparadorDeModelos;
import gerenciadordecontas.ia.InterpretadorDeTexto;
import gerenciadordecontas.modelo.Acao;
import gerenciadordecontas.modelo.Bloco;
import gerenciadordecontas.modelo.Conta;
import gerenciadordecontas.planilha.Dinheiro;
import gerenciadordecontas.planilha.MesLayout;
import gerenciadordecontas.planilha.Meses;
import gerenciadordecontas.planilha.PlanilhaContas;
import gerenciadordecontas.planilha.SheetsGateway;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Interpreta os comandos de linha de comando. Recebe fábricas, para só montar o que o comando precisa. */
public final class Cli {

    private static final String AJUDA = """
            gerenciadordecontas - bot de contas por e-mail

            Comandos:
              tick                       envia os avisos que estão na hora e processa respostas novas (o que o agendador chama)
              aviso                      envia agora o aviso do próximo vencimento
              processar                  só lê e processa respostas novas
              diagnostico [mes]          mostra o que o programa leu do mês (ex.: diagnostico Nov)
              abas                       lista as abas e confere se Jan a Dez existem
              ler-celula <aba> <celula>  lê uma célula (ex.: ler-celula Nov A4)
              ia-modelos [filtro]        mede o tempo de cada modelo Gemini que a sua chave pode usar (padrão: os "flash")
              ia "<texto>"               testa a chave e o modelo de IA sem mexer na planilha (ex.: ia "paguei a luz, 210")
              ajuda                      mostra esta ajuda

            Configuração: arquivo .env ao lado do programa (veja .env.example) ou variáveis de ambiente.
            """;

    private final PrintStream saida;
    private final PrintStream erros;
    private final Supplier<SheetsGateway> planilha;
    private final Supplier<Orquestrador> orquestrador;
    private final Supplier<InterpretadorDeTexto> interpretador;
    private final Supplier<ComparadorDeModelos> comparador;
    private final Supplier<LocalDate> hoje;

    public Cli(
            PrintStream saida,
            PrintStream erros,
            Supplier<SheetsGateway> planilha,
            Supplier<Orquestrador> orquestrador,
            Supplier<InterpretadorDeTexto> interpretador,
            Supplier<ComparadorDeModelos> comparador,
            Supplier<LocalDate> hoje) {
        this.saida = saida;
        this.erros = erros;
        this.planilha = planilha;
        this.orquestrador = orquestrador;
        this.interpretador = interpretador;
        this.comparador = comparador;
        this.hoje = hoje;
    }

    /** Executa o comando e devolve o código de saída (0 = ok). */
    public int executar(String[] args) {
        if (args.length == 0 || List.of("ajuda", "--help", "-h").contains(args[0])) {
            saida.println(AJUDA);
            return 0;
        }

        String[] resto = Arrays.copyOfRange(args, 1, args.length);
        try {
            switch (args[0]) {
                case "abas":
                    return abas();
                case "ler-celula":
                    return lerCelula(resto);
                case "diagnostico":
                    return diagnostico(resto);
                case "ia-modelos":
                    return medirModelos(resto);
                case "ia":
                    return testarIa(resto);
                case "aviso":
                    orquestrador.get().enviarAvisoAgora();
                    return 0;
                case "processar":
                    orquestrador.get().processarCaixa();
                    return 0;
                case "tick":
                    return orquestrador.get().tick() == 0 ? 0 : 1;
                default:
                    erros.println("Comando desconhecido: " + args[0] + "\n");
                    erros.println(AJUDA);
                    return 2;
            }
        } catch (RuntimeException e) {
            erros.println("Erro: " + e.getMessage());
            return 1;
        }
    }

    private int medirModelos(String[] args) {
        String filtro = args.length > 0 ? args[0] : "";
        saida.println("Medindo os modelos" + (filtro.isBlank() ? " \"flash\"" : " com \"" + filtro + "\"")
                + " que a sua chave pode usar (uma chamada pequena em cada; leva alguns segundos por modelo)...");
        List<ComparadorDeModelos.Resultado> todos = comparador.get().comparar(filtro, r -> saida.println(
                String.format("  %-34s %8s  %s", r.modelo(), tempo(r.milissegundos()),
                        (r.ok() ? "ok: " : "FALHOU: ") + r.detalhe())));

        List<ComparadorDeModelos.Resultado> bons = todos.stream().filter(ComparadorDeModelos.Resultado::ok).toList();
        saida.println();
        if (todos.isEmpty()) {
            saida.println("Nenhum modelo encontrado com esse filtro.");
            return 1;
        }
        if (bons.isEmpty()) {
            saida.println("Nenhum modelo respondeu direito agora. Pode ser sobrecarga passageira: tente de novo em alguns minutos.");
            return 1;
        }
        saida.println("Ranking dos que deram certo (mais rápido primeiro):");
        for (int i = 0; i < bons.size(); i++) {
            saida.println("  " + (i + 1) + ". " + bons.get(i).modelo() + "  (" + tempo(bons.get(i).milissegundos()) + ")");
        }
        saida.println();
        saida.println("Para usar o mais rápido, ponha no .env:  GEMINI_MODEL=" + bons.get(0).modelo());
        if (bons.size() > 1) {
            saida.println("E como reserva, se quiser:                GEMINI_MODEL_FALLBACK=" + bons.get(1).modelo());
        }
        saida.println("Meça de novo algumas vezes: o tempo varia de um minuto para outro.");
        return 0;
    }

    private static String tempo(long milissegundos) {
        return String.format(Locale.forLanguageTag("pt-BR"), "%.1f s", milissegundos / 1000.0);
    }

    private int testarIa(String[] args) {
        if (args.length == 0) {
            erros.println("Uso: gerenciadordecontas ia \"<texto>\"   ex.: gerenciadordecontas ia \"paguei a luz, 210\"");
            return 2;
        }
        String texto = String.join(" ", args);
        List<Acao> acoes = interpretador.get().interpretar(texto, List.of());
        saida.println(acoes.size() + " ação(ões) entendida(s):");
        for (Acao acao : acoes) {
            saida.println("  " + acao.tipo() + " | conta: \"" + acao.nomeConta() + "\" | valor: "
                    + (acao.valor() == null ? "-" : Dinheiro.formatar(acao.valor())) + " | bloco: "
                    + (acao.bloco() == null ? "-" : acao.bloco().titulo()) + " | vencimento: "
                    + (acao.diaVencimento() == null ? "-" : acao.diaVencimento()) + " | confiança: "
                    + acao.confianca());
        }
        return 0;
    }

    private int abas() {
        List<String> abas = planilha.get().listarAbas();
        saida.println(abas.size() + " abas encontradas: " + String.join(", ", abas));

        List<String> faltando = Meses.abas().stream().filter(m -> !abas.contains(m)).toList();
        if (!faltando.isEmpty()) {
            erros.println("Faltam abas de mês: " + String.join(", ", faltando));
            return 1;
        }
        saida.println("As 12 abas de mês (Jan a Dez) estão presentes.");
        return 0;
    }

    private int lerCelula(String[] args) {
        if (args.length != 2) {
            erros.println("Uso: gerenciadordecontas ler-celula <aba> <celula>   ex.: gerenciadordecontas ler-celula Nov A4");
            return 2;
        }
        String valor = planilha.get().lerCelula(args[0], args[1]);
        saida.println(args[0] + "!" + args[1] + " = \"" + valor + "\"");
        return 0;
    }

    // Mostra o que o programa leu de um mês, para você conferir com a planilha.
    private int diagnostico(String[] args) {
        int mes = hoje.get().getMonthValue();
        if (args.length > 0) {
            Integer lido = lerMes(args[0]);
            if (lido == null) {
                erros.println("Mês inválido. Use um número (1 a 12) ou o nome da aba (ex.: Nov).");
                return 2;
            }
            mes = lido;
        }

        List<Conta> contas = new PlanilhaContas(planilha.get()).lerMes(mes);
        MesLayout.Vagas vagas = MesLayout.vagas(mes);

        saida.println("Aba " + Meses.aba(mes) + ": " + contas.size() + " conta(s) lida(s) nas linhas "
                + vagas.primeira() + " a " + vagas.ultima() + ".");
        for (Bloco bloco : Bloco.values()) {
            List<Conta> doBloco = contas.stream().filter(c -> c.bloco() == bloco).toList();
            saida.println();
            saida.println("Bloco " + bloco.titulo() + ":");
            for (Conta c : doBloco) {
                String valor = c.valor() != null ? Dinheiro.formatar(c.valor()) : "(sem valor)";
                saida.println(String.format("  linha %2d  %-28s %14s  %-8s [status na planilha: \"%s\"]",
                        c.linha(), c.nome(), valor, c.pago() ? "pago" : "pendente", c.statusBruto()));
            }

            List<Conta> pendentes = doBloco.stream().filter(c -> !c.pago()).toList();
            BigDecimal total = pendentes.stream()
                    .map(c -> c.valor() != null ? c.valor() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            saida.println("  -> " + pendentes.size() + " pendente(s), total " + Dinheiro.formatar(total));
        }
        return 0;
    }

    private static Integer lerMes(String texto) {
        try {
            int numero = Integer.parseInt(texto.strip());
            return numero >= 1 && numero <= 12 ? numero : null;
        } catch (NumberFormatException e) {
            List<String> abas = Meses.abas();
            for (int i = 0; i < abas.size(); i++) {
                if (abas.get(i).equalsIgnoreCase(texto.strip())) {
                    return i + 1;
                }
            }
            return null;
        }
    }
}
