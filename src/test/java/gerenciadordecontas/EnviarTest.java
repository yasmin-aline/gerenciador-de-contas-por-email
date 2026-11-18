package gerenciadordecontas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gerenciadordecontas.aviso.AvisoComposer;
import gerenciadordecontas.aviso.ResumoComposer;
import gerenciadordecontas.config.Settings;
import gerenciadordecontas.ia.InterpretadorGemini;
import gerenciadordecontas.modelo.Acao;
import gerenciadordecontas.modelo.Bloco;
import gerenciadordecontas.modelo.Conta;
import gerenciadordecontas.modelo.Entrega;
import gerenciadordecontas.modelo.TipoAcao;
import gerenciadordecontas.planilha.Dinheiro;
import gerenciadordecontas.planilha.PlanilhaContas;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** "Me manda a planilha": link, resumo em texto e arquivo em anexo. */
class EnviarTest {

    private static String dinheiro(String valor) {
        return Dinheiro.formatar(new BigDecimal(valor));
    }

    @Test
    void link_devolve_o_endereco_e_nao_mexe_na_planilha() {
        Cenario c = Cenario.padrao();
        c.interpretador.acoes.add(Cenario.enviar(Entrega.LINK, null));

        String resposta = c.processador.processar("me manda o link");

        assertTrue(resposta.contains(Fakes.EntregadorFalso.LINK));
        assertEquals("Pendente", c.sheets.get("Nov", "C4"));
        assertTrue(c.entregador.anexadas.isEmpty());
    }

    @Test
    void resumo_do_mes_atual_lista_pagas_pendentes_e_saldo() {
        Cenario c = Cenario.padrao(); // 16/11/2026
        c.interpretador.acoes.add(Cenario.enviar(Entrega.RESUMO, null));

        String resposta = c.processador.processar("como estão as contas?");

        assertTrue(resposta.contains("Resumo de novembro/2026"));
        assertTrue(resposta.contains("⏳ Conta de Luz"));
        assertTrue(resposta.contains("✅ Internet"));
        assertTrue(resposta.contains("Pago: " + dinheiro("570")));
        assertTrue(resposta.contains("Falta pagar: " + dinheiro("1400")));
        assertTrue(resposta.contains("Saldo previsto do mês: " + dinheiro("2030")));
        assertTrue(resposta.contains(Fakes.EntregadorFalso.LINK));
    }

    @Test
    void enviar_sem_dizer_o_que_vira_resumo() {
        Cenario c = Cenario.padrao();
        c.interpretador.acoes.add(Cenario.enviar(null, null));

        String resposta = c.processador.processar("manda aí");

        assertTrue(resposta.contains("Resumo de novembro/2026"));
    }

    @Test
    void resumo_de_mes_que_nao_tem_aba_explica_e_da_o_link() {
        Cenario c = Cenario.padrao();
        c.interpretador.acoes.add(Cenario.enviar(Entrega.RESUMO, 12));

        String resposta = c.processador.processar("resumo de dezembro");

        assertTrue(resposta.contains("✗ Não consegui ler a aba Dez"));
        assertTrue(resposta.contains(Fakes.EntregadorFalso.LINK));
    }

    @Test
    void arquivo_manda_um_email_com_anexo_e_confirma() {
        Cenario c = Cenario.padrao();
        c.interpretador.acoes.add(Cenario.enviar(Entrega.ARQUIVO, null));

        String resposta = c.processador.processar("me manda a planilha");

        assertEquals(1, c.entregador.anexadas.size());
        assertTrue(c.entregador.anexadas.get(0)[0].startsWith("[Contas] Sua planilha"));
        assertTrue(resposta.contains("✓ Mandei a planilha em anexo"));
        assertTrue(resposta.contains(Fakes.EntregadorFalso.LINK));
    }

    @Test
    void arquivo_que_falha_avisa_e_ainda_da_o_link() {
        Cenario c = Cenario.padrao();
        c.entregador.falha = new IllegalStateException("Ative a Google Drive API");
        c.interpretador.acoes.add(Cenario.enviar(Entrega.ARQUIVO, null));

        String resposta = c.processador.processar("me manda a planilha");

        assertTrue(resposta.contains("✗ Não consegui mandar a planilha em anexo"));
        assertTrue(resposta.contains("Ative a Google Drive API"));
        assertTrue(resposta.contains(Fakes.EntregadorFalso.LINK));
    }

    @Test
    void pagar_e_pedir_a_planilha_na_mesma_mensagem_faz_as_duas_coisas_em_ordem() {
        Cenario c = Cenario.padrao();
        c.interpretador.acoes.add(Cenario.pagar("luz", "200"));
        c.interpretador.acoes.add(Cenario.enviar(Entrega.RESUMO, null));

        String resposta = c.processador.processar("paguei a luz, 200, e me manda o resumo");

        assertEquals("✅ Pago", c.sheets.get("Nov", "C4"));
        assertTrue(resposta.indexOf("✓ Conta de Luz") < resposta.indexOf("Resumo de novembro"));
        assertTrue(resposta.contains("✅ Conta de Luz")); // o resumo já enxerga a baixa
    }

    @Test
    void gemini_entende_os_campos_de_enviar() {
        String json = """
                {"acoes": [
                  {"tipo": "enviar", "conta": "", "enviar_o_que": "arquivo", "confianca": 0.9},
                  {"tipo": "enviar", "conta": "", "enviar_o_que": "resumo", "mes": 10, "confianca": 0.9},
                  {"tipo": "enviar", "conta": "", "enviar_o_que": "resumo", "mes": 13, "confianca": 0.9},
                  {"tipo": "enviar", "conta": "", "enviar_o_que": "nenhum", "confianca": 0.9}
                ]}""";
        com.google.gson.JsonObject parte = new com.google.gson.JsonObject();
        parte.addProperty("text", json);
        com.google.gson.JsonArray partes = new com.google.gson.JsonArray();
        partes.add(parte);
        com.google.gson.JsonObject conteudo = new com.google.gson.JsonObject();
        conteudo.add("parts", partes);
        com.google.gson.JsonObject candidata = new com.google.gson.JsonObject();
        candidata.add("content", conteudo);
        com.google.gson.JsonArray candidatas = new com.google.gson.JsonArray();
        candidatas.add(candidata);
        com.google.gson.JsonObject raiz = new com.google.gson.JsonObject();
        raiz.add("candidates", candidatas);

        List<Acao> acoes = InterpretadorGemini.extrairAcoes(raiz.toString());

        assertEquals(4, acoes.size());
        assertEquals(TipoAcao.ENVIAR, acoes.get(0).tipo());
        assertEquals(Entrega.ARQUIVO, acoes.get(0).entrega());
        assertNull(acoes.get(0).mes());
        assertEquals(Entrega.RESUMO, acoes.get(1).entrega());
        assertEquals(Integer.valueOf(10), acoes.get(1).mes());
        assertNull(acoes.get(2).mes()); // mês 13 não existe
        assertNull(acoes.get(3).entrega()); // "nenhum"
    }

    @Test
    void resumo_composer_mostra_bloco_vazio_e_conta_sem_valor() {
        List<Conta> contas = List.of(
                new Conta("Aluguel", null, "Pendente", Bloco.DIA_15, 4),
                new Conta("Internet", new BigDecimal("120"), "✅ Pago", Bloco.DIA_15, 5));

        String texto = ResumoComposer.montar(11, 2026, contas, Settings.padrao());

        assertTrue(texto.contains("⏳ Aluguel — sem valor"));
        assertTrue(texto.contains("Fim do mês:\n  (nenhuma conta)"));
        assertTrue(texto.contains("Pago: " + dinheiro("120") + " (1 conta(s))"));
        assertTrue(texto.contains("Falta pagar: " + dinheiro("0") + " (1 conta(s))"));
    }

    @Test
    void rodape_do_aviso_usa_contas_pendentes_de_verdade() {
        List<Conta> contas = new PlanilhaContas(PlanilhaDeExemplo.novembro()).lerMes(11);

        String corpo = AvisoComposer.montar(
                LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 15), Bloco.DIA_15, contas, Settings.padrao())
                .corpo();

        assertTrue(corpo.contains("paguei Conta de Luz, deu " + dinheiro("200") + ", e Inglês"));
        assertTrue(corpo.contains("me manda a planilha"));
        assertFalse(corpo.contains("paguei a internet, deu 130"));
    }
}
