package gerenciadordecontas;

import gerenciadordecontas.config.Settings;
import gerenciadordecontas.estado.Estado;
import gerenciadordecontas.estado.RegistroMudancas;
import gerenciadordecontas.fluxo.Orquestrador;
import gerenciadordecontas.fluxo.ProcessadorDeRespostas;
import gerenciadordecontas.modelo.Acao;
import gerenciadordecontas.modelo.Bloco;
import gerenciadordecontas.modelo.Entrega;
import gerenciadordecontas.modelo.TipoAcao;
import gerenciadordecontas.nomes.ContaMatcher;
import gerenciadordecontas.planilha.PlanilhaContas;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Monta o fluxo completo com planilha, e-mail e IA falsos. */
public final class Cenario {

    public static final String DESTINATARIO = "yas@example.com";

    public final FakeSheets sheets = PlanilhaDeExemplo.novembro();
    public final Fakes.InterpretadorFalso interpretador = new Fakes.InterpretadorFalso();
    public final Fakes.CaixaFalsa caixa = new Fakes.CaixaFalsa();
    public final Fakes.EntregadorFalso entregador = new Fakes.EntregadorFalso();
    public final Fakes.RelogioFalso relogio;
    public final ProcessadorDeRespostas processador;
    public final Orquestrador orquestrador;
    public final List<String> logs = new ArrayList<>();

    public Cenario(LocalDateTime agora) {
        relogio = new Fakes.RelogioFalso(agora);

        PlanilhaContas planilha = new PlanilhaContas(sheets);
        Estado estado = new Estado(sheets, relogio);
        RegistroMudancas registro = new RegistroMudancas(sheets, relogio);
        processador = new ProcessadorDeRespostas(
                planilha, new ContaMatcher(), interpretador, registro, estado, Settings.padrao(), entregador, relogio);
        orquestrador = new Orquestrador(
                planilha, estado, caixa, processador, Settings.padrao(), relogio, DESTINATARIO, logs::add);
    }

    public static Cenario padrao() {
        return new Cenario(LocalDateTime.of(2026, 11, 16, 18, 0));
    }

    public static Acao pagar(String conta, String valor, double confianca) {
        return new Acao(TipoAcao.PAGAR, conta, valor == null ? null : Fakes.v(valor), null, null, confianca);
    }

    public static Acao pagar(String conta, String valor) {
        return pagar(conta, valor, 0.95);
    }

    public static Acao enviar(Entrega entrega, Integer mes) {
        return new Acao(TipoAcao.ENVIAR, null, null, null, null, 0.95, entrega, mes);
    }

    public static Acao cadastrar(String conta, String valor, Bloco bloco, Integer dia) {
        return new Acao(TipoAcao.CADASTRAR, conta, Fakes.v(valor), bloco, dia, 0.95);
    }
}
