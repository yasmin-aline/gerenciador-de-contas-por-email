package gerenciadordecontas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import gerenciadordecontas.email.Citacao;
import gerenciadordecontas.ia.InterpretadorAnthropic;
import gerenciadordecontas.ia.InterpretadorGemini;
import gerenciadordecontas.modelo.Acao;
import gerenciadordecontas.modelo.Bloco;
import gerenciadordecontas.modelo.TipoAcao;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmailETextoTest {

    @Test
    void citacao_mantem_so_o_que_foi_escrito_acima_da_mensagem_citada() {
        String corpo = "paguei a luz, 210\n\nEm seg., 16 de nov. de 2026 às 18:00, Contas Bot <bot@gmail.com> escreveu:\n"
                + "> Resumo de novembro\n> ...";

        assertEquals("paguei a luz, 210", Citacao.limpar(corpo));
    }

    @Test
    void citacao_trata_linha_de_atribuicao_quebrada_em_duas() {
        String corpo = "desfazer\r\n\r\nEm seg., 16 de nov. de 2026 às 18:00, Contas Bot\r\n<bot@gmail.com> escreveu:\r\n> texto";

        assertEquals("desfazer", Citacao.limpar(corpo));
    }

    @Test
    void citacao_devolve_texto_simples_sem_espacos_nas_pontas() {
        assertEquals("nova conta: IPTU, 400, dia 15", Citacao.limpar("  nova conta: IPTU, 400, dia 15 \n"));
    }

    @Test
    void citacao_de_mensagem_so_com_citacao_fica_vazia() {
        assertEquals("", Citacao.limpar("> só citação"));
        assertEquals("", Citacao.limpar(null));
    }

    @Test
    void citacao_nao_corta_frase_que_so_comeca_com_em() {
        assertEquals("Em casa paguei a luz, 210", Citacao.limpar("Em casa paguei a luz, 210"));
    }

    @Test
    void interpretador_converte_a_chamada_da_ferramenta_em_acoes() {
        String resposta = """
                {
                  "content": [
                    { "type": "tool_use", "name": "registrar_acoes", "input": { "acoes": [
                      { "tipo": "pagar", "conta": "internet", "valor": 130, "bloco": "nenhum", "confianca": 0.95 },
                      { "tipo": "cadastrar", "conta": "IPTU", "valor": 400.5, "bloco": "fim_mes", "dia_vencimento": 20, "confianca": 0.9 }
                    ] } }
                  ]
                }
                """;

        List<Acao> acoes = InterpretadorAnthropic.extrairAcoes(resposta);

        assertEquals(2, acoes.size());

        assertEquals(TipoAcao.PAGAR, acoes.get(0).tipo());
        assertEquals("internet", acoes.get(0).nomeConta());
        Verifica.valor("130", acoes.get(0).valor());
        assertNull(acoes.get(0).bloco());
        assertEquals(0.95, acoes.get(0).confianca(), 0.001);

        assertEquals(TipoAcao.CADASTRAR, acoes.get(1).tipo());
        Verifica.valor("400.5", acoes.get(1).valor());
        assertEquals(Bloco.FIM_DO_MES, acoes.get(1).bloco());
        assertEquals(Integer.valueOf(20), acoes.get(1).diaVencimento());
    }

    @Test
    void interpretador_falha_com_mensagem_clara_se_nao_ha_chamada_de_ferramenta() {
        String resposta = "{ \"content\": [ { \"type\": \"text\", \"text\": \"oi\" } ] }";

        assertThrows(IllegalStateException.class, () -> InterpretadorAnthropic.extrairAcoes(resposta));
        assertThrows(IllegalStateException.class, () -> InterpretadorAnthropic.extrairAcoes("isso não é json {"));
    }

    private static String respostaGemini(String textoDaIa) {
        com.google.gson.JsonObject parte = new com.google.gson.JsonObject();
        parte.addProperty("text", textoDaIa);
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
        return raiz.toString();
    }

    private static final String ACOES_JSON = """
            {"acoes": [
              {"tipo": "pagar", "conta": "internet", "valor": 130, "bloco": "nenhum", "confianca": 0.95},
              {"tipo": "cadastrar", "conta": "IPTU", "valor": 400.5, "bloco": "fim_mes", "dia_vencimento": 20, "confianca": 0.9}
            ]}""";

    @Test
    void gemini_converte_o_json_da_resposta_em_acoes() {
        List<Acao> acoes = InterpretadorGemini.extrairAcoes(respostaGemini(ACOES_JSON));

        assertEquals(2, acoes.size());
        assertEquals(TipoAcao.PAGAR, acoes.get(0).tipo());
        assertEquals("internet", acoes.get(0).nomeConta());
        Verifica.valor("130", acoes.get(0).valor());
        assertNull(acoes.get(0).bloco());
        assertEquals(TipoAcao.CADASTRAR, acoes.get(1).tipo());
        Verifica.valor("400.5", acoes.get(1).valor());
        assertEquals(Bloco.FIM_DO_MES, acoes.get(1).bloco());
        assertEquals(Integer.valueOf(20), acoes.get(1).diaVencimento());
    }

    @Test
    void gemini_aceita_json_embrulhado_em_cercas_de_codigo() {
        String embrulhado = "```json\n" + ACOES_JSON + "\n```";

        assertEquals(2, InterpretadorGemini.extrairAcoes(respostaGemini(embrulhado)).size());
    }

    @Test
    void gemini_ignora_os_pensamentos_do_modelo() {
        String resposta = "{\"candidates\": [{\"content\": {\"parts\": ["
                + "{\"thought\": true, \"text\": \"vou pensar...\"},"
                + "{\"text\": \"{\\\"acoes\\\": []}\"}]}}]}";

        assertEquals(0, InterpretadorGemini.extrairAcoes(resposta).size());
    }

    @Test
    void gemini_explica_quando_a_resposta_foi_bloqueada_ou_veio_vazia() {
        IllegalStateException bloqueada = assertThrows(IllegalStateException.class,
                () -> InterpretadorGemini.extrairAcoes("{\"promptFeedback\": {\"blockReason\": \"SAFETY\"}}"));
        assertEquals("O Gemini não devolveu texto (bloqueado: SAFETY).", bloqueada.getMessage());

        assertThrows(IllegalStateException.class, () -> InterpretadorGemini.extrairAcoes("{\"candidates\": []}"));
    }

    @Test
    void gemini_falha_com_mensagem_clara_se_o_texto_nao_e_json_ou_nao_tem_acoes() {
        assertThrows(IllegalStateException.class, () -> InterpretadorGemini.extrairAcoes(respostaGemini("oi, tudo bem?")));
        assertThrows(IllegalStateException.class, () -> InterpretadorGemini.extrairAcoes(respostaGemini("{\"outra\": 1}")));
        assertThrows(IllegalStateException.class, () -> InterpretadorGemini.extrairAcoes("isso não é json {"));
    }
}
