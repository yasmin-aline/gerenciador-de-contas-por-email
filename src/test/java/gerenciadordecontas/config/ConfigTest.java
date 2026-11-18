package gerenciadordecontas.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gerenciadordecontas.modelo.Bloco;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigTest {

    @Test
    void dotenv_aceita_aspas_comentarios_e_espacos() {
        Env.interpretarLinha("# um comentário");
        Env.interpretarLinha("");
        Env.interpretarLinha("CONTASBOT_TESTE_A = \"valor com espaço\" ");
        Env.interpretarLinha("CONTASBOT_TESTE_B=simples");
        Env.interpretarLinha("sem igual");

        assertEquals("valor com espaço", Env.opcional("CONTASBOT_TESTE_A"));
        assertEquals("simples", Env.opcional("CONTASBOT_TESTE_B"));
        assertNull(Env.opcional("CONTASBOT_TESTE_NAO_EXISTE"));
    }

    @Test
    void obrigatoria_diz_qual_variavel_falta() {
        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> Env.obrigatoria("CONTASBOT_TESTE_FALTANDO", "para o teste"));

        assertEquals("Defina CONTASBOT_TESTE_FALTANDO (para o teste).", erro.getMessage());
    }

    @Test
    void settings_usa_os_padroes_do_plano() {
        Settings s = Settings.de(nome -> null);

        assertEquals(0, new BigDecimal("2200").compareTo(s.recebe(Bloco.DIA_15)));
        assertEquals(0, new BigDecimal("1800").compareTo(s.recebe(Bloco.FIM_DO_MES)));
        assertEquals(0, new BigDecimal("0.30").compareTo(s.limiteDesvio()));
        assertEquals(0.75, s.confiancaMinima(), 0.0001);
    }

    @Test
    void settings_le_valores_do_ambiente_com_ponto_decimal() {
        Map<String, String> ambiente = Map.of("RECEBE_DIA15", "2500.50", "LIMITE_DESVIO", "0.5");

        Settings s = Settings.de(ambiente::get);

        assertEquals(0, new BigDecimal("2500.50").compareTo(s.recebeDia15()));
        assertEquals(0, new BigDecimal("0.5").compareTo(s.limiteDesvio()));
    }

    @Test
    void settings_reclama_de_numero_invalido() {
        assertThrows(IllegalStateException.class, () -> Settings.de(Map.of("RECEBE_DIA15", "duas mil")::get));
    }

    @Test
    void ia_prefere_o_gemini_e_usa_o_modelo_padrao_ou_o_configurado() {
        AiConfig padrao = AiConfig.de(Map.of("GEMINI_API_KEY", "k1", "ANTHROPIC_API_KEY", "k2")::get).orElseThrow();
        assertEquals(AiConfig.Provedor.GEMINI, padrao.provedor());
        assertEquals("k1", padrao.chaveApi());
        assertEquals(AiConfig.MODELO_GEMINI_PADRAO, padrao.modelo());

        AiConfig escolhido = AiConfig.de(Map.of("GEMINI_API_KEY", "k1", "GEMINI_MODEL", "gemini-x")::get).orElseThrow();
        assertEquals("gemini-x", escolhido.modelo());
        assertNull(escolhido.modeloReserva());

        AiConfig comReserva = AiConfig.de(Map.of("GEMINI_API_KEY", "k1", "GEMINI_MODEL_FALLBACK", "gemini-y")::get).orElseThrow();
        assertEquals("gemini-y", comReserva.modeloReserva());
    }

    @Test
    void ia_cai_para_anthropic_e_fica_vazia_sem_nenhuma_chave() {
        AiConfig anthropic = AiConfig.de(Map.of("ANTHROPIC_API_KEY", "k2")::get).orElseThrow();
        assertEquals(AiConfig.Provedor.ANTHROPIC, anthropic.provedor());
        assertEquals(AiConfig.MODELO_ANTHROPIC_PADRAO, anthropic.modelo());

        assertTrue(AiConfig.de(nome -> null).isEmpty());
    }
}
