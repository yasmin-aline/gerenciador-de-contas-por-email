package gerenciadordecontas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gerenciadordecontas.modelo.Bloco;
import gerenciadordecontas.modelo.Conta;
import gerenciadordecontas.modelo.Status;
import gerenciadordecontas.nomes.ContaMatcher;
import gerenciadordecontas.nomes.ResultadoBusca;
import gerenciadordecontas.planilha.Dinheiro;
import gerenciadordecontas.planilha.MesLayout;
import gerenciadordecontas.planilha.Meses;
import gerenciadordecontas.planilha.PlanilhaContas;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class LeituraTest {

    // ---- Dinheiro ----

    @Test
    void dinheiro_le_valores_no_formato_brasileiro() {
        String[][] casos = {
            {"R$ 1.234,56", "1234.56"}, {"R$ 130,00", "130.00"}, {"130", "130"},
            {"130,5", "130.5"}, {"  R$ 2.200,00 ", "2200.00"}, {"R$ 1.000,00", "1000.00"},
        };
        for (String[] caso : casos) {
            assertTrue(Dinheiro.ler(caso[0]).isPresent(), "deveria ler: " + caso[0]);
            Verifica.valor(caso[1], Dinheiro.ler(caso[0]).get());
        }
    }

    @Test
    void dinheiro_rejeita_o_que_nao_e_numero() {
        for (String texto : new String[] {"", "abc", "Pendente", "12abc", "1234.56", null}) {
            assertTrue(Dinheiro.ler(texto).isEmpty(), "não deveria ler: " + texto);
        }
    }

    @Test
    void dinheiro_formata_com_separadores_brasileiros() {
        assertEquals("R$ 1.234,56", Dinheiro.formatar(new BigDecimal("1234.56")));
        assertEquals("R$ 200,00", Dinheiro.formatar(new BigDecimal("200")));
        assertEquals("R$ 0,50", Dinheiro.formatar(new BigDecimal("0.5")));
    }

    // ---- Status ----

    @Test
    void status_trata_as_tres_formas_como_equivalentes() {
        assertTrue(Status.ehPago("Pago"));
        assertTrue(Status.ehPago(" ✅ Pago"));
        assertTrue(Status.ehPago("pago"));
        assertFalse(Status.ehPago("Pendente"));
        assertFalse(Status.ehPago("⏳ Pendente"));
        assertFalse(Status.ehPago("❌ Não pago"));
        assertEquals("✅ Pago", Status.PAGO);
        assertFalse(Status.ehPago(""));
        assertFalse(Status.ehPago(null));
    }

    // ---- Meses e layout ----

    @Test
    void meses_dao_o_nome_de_tres_letras_da_aba() {
        assertEquals("Jan", Meses.aba(1));
        assertEquals("Fev", Meses.aba(2));
        assertEquals("Mai", Meses.aba(5));
        assertEquals("Ago", Meses.aba(8));
        assertEquals("Dez", Meses.aba(12));
        assertEquals(12, Meses.abas().size());
        assertEquals("novembro", Meses.nomeCompleto(11));
        assertThrows(IllegalArgumentException.class, () -> Meses.aba(0));
        assertThrows(IllegalArgumentException.class, () -> Meses.aba(13));
    }

    @Test
    void vagas_seguem_o_layout_de_cada_epoca_do_ano() {
        assertEquals(new MesLayout.Vagas(5, 9), MesLayout.vagas(1));
        assertEquals(new MesLayout.Vagas(5, 9), MesLayout.vagas(4));
        assertEquals(new MesLayout.Vagas(4, 7), MesLayout.vagas(5));
        assertEquals(new MesLayout.Vagas(4, 8), MesLayout.vagas(6));
        assertEquals(new MesLayout.Vagas(4, 8), MesLayout.vagas(12));
        assertEquals("A4:G8", MesLayout.intervaloDeLeitura(11));
        assertEquals("E6", MesLayout.celula(MesLayout.FIM_DO_MES.colNome(), 6));
    }

    // ---- ContaMatcher ----

    private static Conta conta(String nome) {
        return new Conta(nome, new BigDecimal("100"), "Pendente", Bloco.DIA_15, 4);
    }

    @Test
    void matcher_ignora_maiusculas_acentos_e_conta_de() {
        ContaMatcher matcher = new ContaMatcher();
        List<Conta> contas = List.of(conta("Conta de Luz"), conta("Inglês"));

        assertEquals("Conta de Luz", matcher.buscar("luz", contas).conta().nome());
        assertEquals("Inglês", matcher.buscar("INGLES", contas).conta().nome());
        assertEquals("Inglês", matcher.buscar("conta de inglês", contas).conta().nome());
    }

    @Test
    void matcher_casa_quando_todas_as_palavras_ditas_estao_no_nome() {
        ContaMatcher matcher = new ContaMatcher();
        List<Conta> contas = List.of(conta("Cartão Nubank"), conta("Cartão Inter"));

        assertEquals("Cartão Nubank", matcher.buscar("nubank", contas).conta().nome());
    }

    @Test
    void matcher_avisa_da_ambiguidade_em_vez_de_chutar() {
        ContaMatcher matcher = new ContaMatcher();
        List<Conta> contas = List.of(conta("Cartão Nubank"), conta("Cartão Inter"));

        ResultadoBusca busca = matcher.buscar("cartão", contas);

        assertEquals(ResultadoBusca.Estado.AMBIGUA, busca.estado());
        assertEquals(2, busca.candidatas().size());
    }

    @Test
    void matcher_usa_apelidos() {
        ContaMatcher matcher = new ContaMatcher(Map.of("energia", "luz"));

        assertEquals("Conta de Luz", matcher.buscar("energia", List.of(conta("Conta de Luz"))).conta().nome());
    }

    @Test
    void matcher_nao_acha_conta_desconhecida() {
        assertEquals(ResultadoBusca.Estado.NAO_ENCONTRADA,
                new ContaMatcher().buscar("padaria", List.of(conta("Conta de Luz"))).estado());
    }

    // ---- PlanilhaContas ----

    @Test
    void lerMes_le_so_as_vagas_e_acha_cada_conta_pelo_nome() {
        PlanilhaContas planilha = new PlanilhaContas(PlanilhaDeExemplo.novembro());

        List<Conta> contas = planilha.lerMes(11);

        assertEquals(5, contas.size()); // totais e anotações ficam de fora
        assertTrue(contas.stream().noneMatch(c -> c.nome().equals("Total")));

        Conta luz = contas.stream().filter(c -> c.nome().equals("Conta de Luz")).findFirst().orElseThrow();
        Verifica.valor("200", luz.valor());
        assertFalse(luz.pago());
        assertEquals(Bloco.DIA_15, luz.bloco());
        assertEquals(4, luz.linha());

        assertTrue(contas.stream().filter(c -> c.nome().equals("Internet")).findFirst().orElseThrow().pago());
        assertTrue(contas.stream().filter(c -> c.nome().equals("Fatura Nubank")).findFirst().orElseThrow().pago());
        assertEquals(Bloco.FIM_DO_MES,
                contas.stream().filter(c -> c.nome().equals("Cartão Inter")).findFirst().orElseThrow().bloco());
    }

    @Test
    void lerMes_reclama_de_valor_ilegivel_dizendo_onde_esta() {
        FakeSheets sheets = PlanilhaDeExemplo.novembro();
        sheets.set("Nov", "B4", "duzentos");

        IllegalStateException erro =
                assertThrows(IllegalStateException.class, () -> new PlanilhaContas(sheets).lerMes(11));

        assertTrue(erro.getMessage().contains("Nov!B4"));
    }

    @Test
    void acharVaga_devolve_a_primeira_linha_em_branco_de_cada_bloco() {
        PlanilhaContas planilha = new PlanilhaContas(PlanilhaDeExemplo.novembro());

        assertEquals(7, planilha.acharVaga(11, Bloco.DIA_15).getAsInt());
        assertEquals(6, planilha.acharVaga(11, Bloco.FIM_DO_MES).getAsInt());
    }

    @Test
    void acharVaga_nao_devolve_nada_quando_o_bloco_esta_cheio() {
        FakeSheets sheets = PlanilhaDeExemplo.novembro();
        sheets.set("Nov", "A7", "Água").set("Nov", "A8", "Gás");

        OptionalInt vaga = new PlanilhaContas(sheets).acharVaga(11, Bloco.DIA_15);

        assertTrue(vaga.isEmpty());
    }

    @Test
    void marcarPago_grava_status_e_valor_pago_sem_tocar_nos_totais() {
        FakeSheets sheets = PlanilhaDeExemplo.novembro();
        PlanilhaContas planilha = new PlanilhaContas(sheets);
        Conta luz = planilha.lerMes(11).stream().filter(c -> c.nome().equals("Conta de Luz")).findFirst().orElseThrow();

        planilha.marcarPago(11, luz, new BigDecimal("210"));

        assertEquals("✅ Pago", sheets.get("Nov", "C4"));
        assertEquals("R$ 210,00", sheets.get("Nov", "B4"));
        assertEquals("R$ 620,00", sheets.get("Nov", "B9"));
        assertEquals("Total", sheets.get("Nov", "A9"));
    }

    @Test
    void cadastrar_preenche_a_vaga_livre_como_pendente() {
        FakeSheets sheets = PlanilhaDeExemplo.novembro();
        PlanilhaContas planilha = new PlanilhaContas(sheets);

        assertNotNull(planilha.cadastrar(11, Bloco.FIM_DO_MES, 6, "IPTU", new BigDecimal("400")));

        assertEquals("IPTU", sheets.get("Nov", "E6"));
        assertEquals("R$ 400,00", sheets.get("Nov", "F6"));
        assertEquals("⏳ Pendente", sheets.get("Nov", "G6"));
    }
}
