package gerenciadordecontas.planilha;

import gerenciadordecontas.modelo.Bloco;
import gerenciadordecontas.modelo.CelulaMudanca;
import gerenciadordecontas.modelo.Conta;
import gerenciadordecontas.modelo.Status;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Lê e grava as contas nas abas de mês. Procura contas pelo nome, só nas colunas
 * A-C (Dia 15) e E-G (Fim do mês), e nunca toca nas linhas de total nem nas anotações.
 */
public final class PlanilhaContas {

    private final SheetsGateway gateway;

    public PlanilhaContas(SheetsGateway gateway) {
        this.gateway = gateway;
    }

    public List<Conta> lerMes(int mes) {
        String aba = Meses.aba(mes);
        MesLayout.Vagas vagas = MesLayout.vagas(mes);
        List<List<String>> linhas = gateway.lerIntervalo(aba, MesLayout.intervaloDeLeitura(mes));

        List<Conta> contas = new ArrayList<>();
        for (MesLayout.BlocoLayout bloco : MesLayout.blocos()) {
            for (int linha = vagas.primeira(); linha <= vagas.ultima(); linha++) {
                int i = linha - vagas.primeira();
                String nome = Grade.celula(linhas, i, bloco.colNome()).strip();
                if (nome.isEmpty()) {
                    continue; // vaga livre
                }

                String textoValor = Grade.celula(linhas, i, bloco.colValor());
                BigDecimal valor = null;
                if (!textoValor.isBlank()) {
                    Optional<BigDecimal> lido = Dinheiro.ler(textoValor);
                    if (lido.isEmpty()) {
                        throw new IllegalStateException("Valor inválido em " + aba + "!"
                                + MesLayout.celula(bloco.colValor(), linha) + ": \"" + textoValor
                                + "\". A planilha está com localidade Brasil?");
                    }
                    valor = lido.get();
                }

                String status = Grade.celula(linhas, i, bloco.colPago());
                contas.add(new Conta(nome, valor, status, bloco.bloco(), linha));
            }
        }
        return contas;
    }

    /** Primeira vaga livre (nome em branco) do bloco, ou vazio se não houver. */
    public OptionalInt acharVaga(int mes, Bloco bloco) {
        String aba = Meses.aba(mes);
        MesLayout.Vagas vagas = MesLayout.vagas(mes);
        MesLayout.BlocoLayout layout = MesLayout.de(bloco);
        List<List<String>> linhas = gateway.lerIntervalo(aba, MesLayout.intervaloDeLeitura(mes));

        for (int linha = vagas.primeira(); linha <= vagas.ultima(); linha++) {
            if (Grade.celula(linhas, linha - vagas.primeira(), layout.colNome()).strip().isEmpty()) {
                return OptionalInt.of(linha);
            }
        }
        return OptionalInt.empty();
    }

    /** Marca a conta como "Pago" e, se informado e diferente, troca o valor pelo valor realmente pago. */
    public List<CelulaMudanca> marcarPago(int mes, Conta conta, BigDecimal valorPago) {
        String aba = Meses.aba(mes);
        MesLayout.BlocoLayout layout = MesLayout.de(conta.bloco());
        List<CelulaMudanca> mudancas = new ArrayList<>();
        Map<String, Object> escritas = new LinkedHashMap<>();

        String celulaStatus = MesLayout.celula(layout.colPago(), conta.linha());
        mudancas.add(new CelulaMudanca(aba, celulaStatus, conta.statusBruto(), Status.PAGO, false));
        escritas.put(celulaStatus, Status.PAGO);

        if (valorPago != null && (conta.valor() == null || valorPago.compareTo(conta.valor()) != 0)) {
            String celulaValor = MesLayout.celula(layout.colValor(), conta.linha());
            String antes = conta.valor() != null ? Dinheiro.simples(conta.valor()) : "";
            mudancas.add(new CelulaMudanca(aba, celulaValor, antes, Dinheiro.simples(valorPago), true));
            escritas.put(celulaValor, valorPago);
        }

        gateway.escreverCelulas(aba, escritas);
        return mudancas;
    }

    /** Cadastra uma conta nova numa vaga livre (já escolhida com acharVaga), com status Pendente. */
    public List<CelulaMudanca> cadastrar(int mes, Bloco bloco, int linha, String nome, BigDecimal valor) {
        String aba = Meses.aba(mes);
        MesLayout.BlocoLayout layout = MesLayout.de(bloco);

        // Guarda o que havia nas três células, para o "desfazer" devolver exatamente igual.
        List<List<String>> atual = gateway.lerIntervalo(
                aba,
                MesLayout.celula(layout.colNome(), linha) + ":" + MesLayout.celula(layout.colPago(), linha));

        String celulaNome = MesLayout.celula(layout.colNome(), linha);
        String celulaValor = MesLayout.celula(layout.colValor(), linha);
        String celulaStatus = MesLayout.celula(layout.colPago(), linha);

        String antesValorTexto = Grade.celula(atual, 0, layout.colValor() - layout.colNome());
        Optional<BigDecimal> antesValor = Dinheiro.ler(antesValorTexto);

        List<CelulaMudanca> mudancas = List.of(
                new CelulaMudanca(aba, celulaNome, Grade.celula(atual, 0, 0), nome, false),
                new CelulaMudanca(aba, celulaValor,
                        antesValor.map(Dinheiro::simples).orElse(antesValorTexto), Dinheiro.simples(valor), true),
                new CelulaMudanca(aba, celulaStatus,
                        Grade.celula(atual, 0, layout.colPago() - layout.colNome()), Status.PENDENTE, false));

        Map<String, Object> escritas = new LinkedHashMap<>();
        escritas.put(celulaNome, nome);
        escritas.put(celulaValor, valor);
        escritas.put(celulaStatus, Status.PENDENTE);
        gateway.escreverCelulas(aba, escritas);
        return mudancas;
    }
}
