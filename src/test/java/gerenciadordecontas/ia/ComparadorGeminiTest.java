package gerenciadordecontas.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import gerenciadordecontas.config.AiConfig;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComparadorGeminiTest {

    private static final String LISTA = """
            {"models": [
              {"name": "models/gemini-a-flash", "supportedGenerationMethods": ["generateContent"]},
              {"name": "models/gemini-b-flash-lite", "supportedGenerationMethods": ["generateContent", "countTokens"]},
              {"name": "models/gemini-a-flash-tts", "supportedGenerationMethods": ["generateContent"]},
              {"name": "models/gemini-embedding-flash", "supportedGenerationMethods": ["embedContent"]},
              {"name": "models/gemini-c-pro", "supportedGenerationMethods": ["generateContent"]},
              {"name": "models/gemma-flash", "supportedGenerationMethods": ["generateContent"]}
            ]}""";

    private static final String RESPOSTA_OK =
            "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"acoes\\\":["
                    + "{\\\"tipo\\\":\\\"pagar\\\",\\\"conta\\\":\\\"nubank\\\",\\\"valor\\\":600,\\\"bloco\\\":\\\"nenhum\\\",\\\"confianca\\\":0.9},"
                    + "{\\\"tipo\\\":\\\"pagar\\\",\\\"conta\\\":\\\"luz\\\",\\\"valor\\\":310,\\\"bloco\\\":\\\"nenhum\\\",\\\"confianca\\\":0.9}]}\"}]}}]}";

    private static HttpServer servidor(List<String> chamados) throws IOException {
        HttpServer servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.createContext("/", troca -> {
            String caminho = troca.getRequestURI().getPath();
            chamados.add(caminho);
            int status = 200;
            String corpo;
            if (caminho.equals("/v1beta/models")) {
                corpo = LISTA;
            } else if (caminho.contains("gemini-b-flash-lite")) {
                status = 503;
                corpo = "{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\"}}";
            } else {
                corpo = RESPOSTA_OK;
            }
            byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
            troca.sendResponseHeaders(status, bytes.length);
            try (OutputStream saida = troca.getResponseBody()) {
                saida.write(bytes);
            }
        });
        servidor.start();
        return servidor;
    }

    private static ComparadorGemini comparador(HttpServer servidor, List<Long> pausas) {
        AiConfig config = new AiConfig(AiConfig.Provedor.GEMINI, "chave-de-teste", "qualquer", null);
        Clock relogio = Clock.fixed(Instant.parse("2026-11-16T21:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        String base = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1beta/";
        return new ComparadorGemini(HttpClient.newHttpClient(), config, relogio, base, pausas::add);
    }

    @Test
    void lista_so_modelos_gemini_de_texto_com_generateContent_e_o_filtro() throws Exception {
        List<String> chamados = new ArrayList<>();
        HttpServer servidor = servidor(chamados);
        try {
            ComparadorGemini comparador = comparador(servidor, new ArrayList<>());

            // Padrão: "flash". Fora ficam voz (tts), embedding, o que não é gemini e o que não casa com o filtro.
            assertEquals(List.of("gemini-a-flash", "gemini-b-flash-lite"), comparador.listar(""));
            assertEquals(List.of("gemini-b-flash-lite"), comparador.listar("lite"));
            assertEquals(List.of("gemini-c-pro"), comparador.listar("PRO"));
        } finally {
            servidor.stop(0);
        }
    }

    @Test
    void mede_cada_modelo_uma_vez_e_poe_os_que_deram_certo_primeiro() throws Exception {
        List<String> chamados = new ArrayList<>();
        HttpServer servidor = servidor(chamados);
        try {
            List<Long> pausas = new ArrayList<>();
            List<String> avisados = new ArrayList<>();

            List<ComparadorDeModelos.Resultado> resultados =
                    comparador(servidor, pausas).comparar("", r -> avisados.add(r.modelo()));

            assertEquals(List.of("gemini-a-flash", "gemini-b-flash-lite"), avisados);
            assertEquals("gemini-a-flash", resultados.get(0).modelo());
            assertTrue(resultados.get(0).ok());
            assertEquals("gemini-b-flash-lite", resultados.get(1).modelo());
            assertFalse(resultados.get(1).ok());
            assertTrue(resultados.get(1).detalhe().contains("503"));

            // 503 não foi repetido (a medição usa uma tentativa) e houve pausa entre os modelos.
            assertEquals(1L, chamados.stream().filter(c -> c.contains("gemini-b-flash-lite")).count());
            assertEquals(List.of(1500L), pausas);
        } finally {
            servidor.stop(0);
        }
    }
}
