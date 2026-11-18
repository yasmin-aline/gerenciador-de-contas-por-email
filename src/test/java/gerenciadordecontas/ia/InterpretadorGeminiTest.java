package gerenciadordecontas.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import gerenciadordecontas.config.AiConfig;
import gerenciadordecontas.modelo.Acao;
import gerenciadordecontas.modelo.TipoAcao;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Servidor HTTP local que imita o Gemini: confere o pedido enviado e o que acontece com 503/400. */
class InterpretadorGeminiTest {

    private static final String RESPOSTA_OK =
            "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"acoes\\\":[{\\\"tipo\\\":\\\"pagar\\\","
                    + "\\\"conta\\\":\\\"luz\\\",\\\"valor\\\":310,\\\"bloco\\\":\\\"nenhum\\\",\\\"confianca\\\":0.9}]}\"}]}}]}";

    /** Servidor com respostas roteirizadas por caminho; o que acabar no roteiro responde 200. */
    private static final class Falso implements AutoCloseable {
        final HttpServer servidor;
        final Map<String, Deque<Integer>> roteiro = new HashMap<>();
        final List<String> caminhos = new ArrayList<>();
        final List<String> chaves = new ArrayList<>();
        final List<String> corpos = new ArrayList<>();

        Falso() throws IOException {
            servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            servidor.createContext("/", troca -> {
                caminhos.add(troca.getRequestURI().getPath());
                chaves.add(troca.getRequestHeaders().getFirst("x-goog-api-key"));
                corpos.add(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                Deque<Integer> fila = roteiro.get(troca.getRequestURI().getPath());
                int status = fila == null || fila.isEmpty() ? 200 : fila.poll();
                String corpo = status == 200 ? RESPOSTA_OK : "{\"error\":{\"code\":" + status + ",\"status\":\"X\"}}";
                byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
                troca.getResponseHeaders().add("content-type", "application/json");
                troca.sendResponseHeaders(status, bytes.length);
                try (OutputStream saida = troca.getResponseBody()) {
                    saida.write(bytes);
                }
            });
            servidor.start();
        }

        void responder(String modelo, Integer... status) {
            roteiro.put("/v1beta/models/" + modelo + ":generateContent", new ArrayDeque<>(List.of(status)));
        }

        String base() {
            return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1beta/models/";
        }

        @Override
        public void close() {
            servidor.stop(0);
        }
    }

    private static InterpretadorGemini interpretador(Falso falso, String modelo, String reserva, List<Long> esperas) {
        AiConfig config = new AiConfig(AiConfig.Provedor.GEMINI, "chave-de-teste", modelo, reserva);
        Clock relogio = Clock.fixed(Instant.parse("2026-11-16T21:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        return new InterpretadorGemini(HttpClient.newHttpClient(), config, relogio, falso.base(), esperas::add);
    }

    @Test
    void envia_o_pedido_no_formato_do_gemini_e_le_a_resposta() throws Exception {
        try (Falso falso = new Falso()) {
            List<Acao> acoes = interpretador(falso, "modelo-a", null, new ArrayList<>())
                    .interpretar("paguei a luz, 310", List.of("Conta de Luz"));

            assertEquals(1, acoes.size());
            assertEquals(TipoAcao.PAGAR, acoes.get(0).tipo());
            assertEquals("luz", acoes.get(0).nomeConta());

            assertEquals("/v1beta/models/modelo-a:generateContent", falso.caminhos.get(0));
            assertEquals("chave-de-teste", falso.chaves.get(0));
            String corpo = falso.corpos.get(0);
            assertTrue(corpo.contains("\"systemInstruction\""));
            assertTrue(corpo.contains("\"responseMimeType\":\"application/json\""));
            assertTrue(corpo.contains("paguei a luz, 310"));
            assertTrue(corpo.contains("Conta de Luz"));
        }
    }

    @Test
    void tenta_de_novo_quando_o_servidor_esta_sobrecarregado() throws Exception {
        try (Falso falso = new Falso()) {
            falso.responder("modelo-a", 503, 503); // falha duas vezes e na terceira dá certo
            List<Long> esperas = new ArrayList<>();

            List<Acao> acoes = interpretador(falso, "modelo-a", null, esperas).interpretar("paguei a luz, 310", List.of());

            assertEquals(1, acoes.size());
            assertEquals(3, falso.caminhos.size());
            assertEquals(List.of(3000L, 6000L), esperas); // espera dobra a cada tentativa
        }
    }

    @Test
    void usa_o_modelo_reserva_se_o_principal_continua_sobrecarregado() throws Exception {
        try (Falso falso = new Falso()) {
            falso.responder("modelo-a", 503, 503, 503);

            List<Acao> acoes = interpretador(falso, "modelo-a", "modelo-b", new ArrayList<>())
                    .interpretar("paguei a luz, 310", List.of());

            assertEquals(1, acoes.size());
            assertEquals(4, falso.caminhos.size());
            assertEquals("/v1beta/models/modelo-b:generateContent", falso.caminhos.get(3));
        }
    }

    @Test
    void sem_reserva_desiste_depois_de_tres_tentativas_com_mensagem_clara() throws Exception {
        try (Falso falso = new Falso()) {
            falso.responder("modelo-a", 503, 503, 503, 503);

            IllegalStateException erro = assertThrows(IllegalStateException.class,
                    () -> interpretador(falso, "modelo-a", null, new ArrayList<>()).interpretar("oi", List.of()));

            assertTrue(erro.getMessage().contains("503"));
            assertTrue(erro.getMessage().contains("modelo-a"));
            assertEquals(3, falso.caminhos.size());
        }
    }

    @Test
    void erro_que_nao_passa_sozinho_nao_e_repetido_nem_vai_para_o_reserva() throws Exception {
        try (Falso falso = new Falso()) {
            falso.responder("modelo-a", 400);

            IllegalStateException erro = assertThrows(IllegalStateException.class,
                    () -> interpretador(falso, "modelo-a", "modelo-b", new ArrayList<>()).interpretar("oi", List.of()));

            assertTrue(erro.getMessage().contains("400"));
            assertFalse(erro instanceof InterpretadorGemini.Indisponivel);
            assertEquals(1, falso.caminhos.size());
        }
    }
}
