package gerenciadordecontas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CliTest {

    private final ByteArrayOutputStream saida = new ByteArrayOutputStream();
    private final ByteArrayOutputStream erros = new ByteArrayOutputStream();

    private final Fakes.InterpretadorFalso interpretador = new Fakes.InterpretadorFalso();
    private final java.util.List<gerenciadordecontas.ia.ComparadorDeModelos.Resultado> medidos =
            new java.util.ArrayList<>();
    private final gerenciadordecontas.ia.ComparadorDeModelos comparador = (filtro, aoMedir) -> {
        medidos.forEach(aoMedir);
        return medidos.stream()
                .sorted(java.util.Comparator
                        .comparing((gerenciadordecontas.ia.ComparadorDeModelos.Resultado r) -> !r.ok())
                        .thenComparingLong(gerenciadordecontas.ia.ComparadorDeModelos.Resultado::milissegundos))
                .toList();
    };

    private Cli cli(FakeSheets sheets) {
        return new Cli(
                new PrintStream(saida, true, StandardCharsets.UTF_8),
                new PrintStream(erros, true, StandardCharsets.UTF_8),
                () -> sheets,
                () -> {
                    throw new IllegalStateException("não deveria montar o orquestrador");
                },
                () -> interpretador,
                () -> comparador,
                () -> LocalDate.of(2026, 11, 16));
    }

    private String saida() {
        return saida.toString(StandardCharsets.UTF_8);
    }

    private String erros() {
        return erros.toString(StandardCharsets.UTF_8);
    }

    @Test
    void sem_argumentos_mostra_a_ajuda() {
        assertEquals(0, cli(PlanilhaDeExemplo.novembro()).executar(new String[0]));
        assertTrue(saida().contains("tick"));
    }

    @Test
    void comando_desconhecido_devolve_codigo_2() {
        assertEquals(2, cli(PlanilhaDeExemplo.novembro()).executar(new String[] {"voar"}));
        assertTrue(erros().contains("Comando desconhecido: voar"));
    }

    @Test
    void ler_celula_mostra_o_conteudo() {
        int codigo = cli(PlanilhaDeExemplo.novembro()).executar(new String[] {"ler-celula", "Nov", "A4"});

        assertEquals(0, codigo);
        assertTrue(saida().contains("Nov!A4 = \"Conta de Luz\""));
    }

    @Test
    void diagnostico_mostra_contas_pendentes_e_totais_por_bloco() {
        int codigo = cli(PlanilhaDeExemplo.novembro()).executar(new String[] {"diagnostico", "Nov"});

        assertEquals(0, codigo);
        String texto = saida();
        assertTrue(texto.contains("5 conta(s) lida(s) nas linhas 4 a 8"));
        assertTrue(texto.contains("Bloco Dia 15:"));
        assertTrue(texto.contains("2 pendente(s), total R$ 500,00")); // Luz 200 + Inglês 300
        assertTrue(texto.contains("1 pendente(s), total R$ 900,00")); // Cartão Inter
    }

    @Test
    void diagnostico_sem_argumento_usa_o_mes_de_hoje() {
        int codigo = cli(PlanilhaDeExemplo.novembro()).executar(new String[] {"diagnostico"});

        assertEquals(0, codigo);
        assertTrue(saida().contains("Aba Nov:"));
    }

    @Test
    void diagnostico_com_mes_invalido_devolve_codigo_2() {
        assertEquals(2, cli(PlanilhaDeExemplo.novembro()).executar(new String[] {"diagnostico", "xyz"}));
    }

    @Test
    void abas_avisa_quais_abas_de_mes_faltam() {
        int codigo = cli(PlanilhaDeExemplo.novembro()).executar(new String[] {"abas"});

        assertEquals(1, codigo);
        assertTrue(erros().contains("Faltam abas de mês"));
    }

    @Test
    void erro_inesperado_vira_mensagem_e_codigo_1() {
        FakeSheets semAba = new FakeSheets(); // sem a aba Nov: a leitura falha

        int codigo = cli(semAba).executar(new String[] {"diagnostico", "Nov"});

        assertEquals(1, codigo);
        assertTrue(erros().startsWith("Erro:"));
    }

    @Test
    void ia_mostra_o_que_foi_entendido_sem_tocar_na_planilha() {
        interpretador.acoes.add(Cenario.pagar("luz", "210"));

        int codigo = cli(PlanilhaDeExemplo.novembro()).executar(new String[] {"ia", "paguei", "a", "luz,", "210"});

        assertEquals(0, codigo);
        assertEquals("paguei a luz, 210", interpretador.ultimoTexto);
        assertTrue(saida().contains("1 ação(ões) entendida(s):"));
        assertTrue(saida().contains("PAGAR | conta: \"luz\" | valor: R$ 210,00"));
    }

    @Test
    void ia_sem_texto_devolve_codigo_2() {
        assertEquals(2, cli(PlanilhaDeExemplo.novembro()).executar(new String[] {"ia"}));
    }

    @Test
    void ia_modelos_mostra_cada_tempo_e_sugere_o_mais_rapido() {
        medidos.add(new gerenciadordecontas.ia.ComparadorDeModelos.Resultado("gemini-lento", true, 4200, "entendeu as 2 ações"));
        medidos.add(new gerenciadordecontas.ia.ComparadorDeModelos.Resultado("gemini-ocupado", false, 900, "503"));
        medidos.add(new gerenciadordecontas.ia.ComparadorDeModelos.Resultado("gemini-rapido", true, 800, "entendeu as 2 ações"));

        int codigo = cli(PlanilhaDeExemplo.novembro()).executar(new String[] {"ia-modelos"});

        assertEquals(0, codigo);
        String texto = saida();
        assertTrue(texto.contains("gemini-ocupado"));
        assertTrue(texto.contains("FALHOU: 503"));
        assertTrue(texto.contains("1. gemini-rapido"));
        assertTrue(texto.contains("2. gemini-lento"));
        assertTrue(texto.contains("GEMINI_MODEL=gemini-rapido"));
        assertTrue(texto.contains("GEMINI_MODEL_FALLBACK=gemini-lento"));
    }

    @Test
    void ia_modelos_sem_nenhum_que_funcione_devolve_codigo_1() {
        medidos.add(new gerenciadordecontas.ia.ComparadorDeModelos.Resultado("gemini-ocupado", false, 900, "503"));

        assertEquals(1, cli(PlanilhaDeExemplo.novembro()).executar(new String[] {"ia-modelos"}));
        assertTrue(saida().contains("tente de novo"));
    }
}
