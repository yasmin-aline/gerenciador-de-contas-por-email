package gerenciadordecontas.ia;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import gerenciadordecontas.config.AiConfig;
import gerenciadordecontas.modelo.Acao;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Entende a resposta em texto livre chamando a API da Anthropic com uma "ferramenta" obrigatória,
 * o que força a resposta em JSON no formato esperado. A IA só extrai (conta, valor, ação);
 * casar o nome com a planilha e gravar é feito pelo programa.
 */
public final class InterpretadorAnthropic implements InterpretadorDeTexto {

    private static final String URL = "https://api.anthropic.com/v1/messages";

    private final HttpClient http;
    private final AiConfig config;
    private final Clock relogio;

    public InterpretadorAnthropic(HttpClient http, AiConfig config, Clock relogio) {
        this.http = http;
        this.config = config;
        this.relogio = relogio;
    }

    @Override
    public List<Acao> interpretar(String texto, List<String> contasDoMes) {
        String pergunta = "Hoje é " + LocalDate.now(relogio).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".\n"
                + "Contas já cadastradas neste mês: "
                + (contasDoMes.isEmpty() ? "(nenhuma)" : String.join("; ", contasDoMes)) + "\n\n"
                + "Mensagem da pessoa:\n" + texto + "\n";

        JsonObject ferramenta = new JsonObject();
        ferramenta.addProperty("name", "registrar_acoes");
        ferramenta.addProperty("description", "Registra as ações pedidas na mensagem.");
        ferramenta.add("input_schema", JsonParser.parseString(Instrucoes.SCHEMA));

        JsonArray ferramentas = new JsonArray();
        ferramentas.add(ferramenta);

        JsonObject escolha = new JsonObject();
        escolha.addProperty("type", "tool");
        escolha.addProperty("name", "registrar_acoes");

        JsonObject mensagem = new JsonObject();
        mensagem.addProperty("role", "user");
        mensagem.addProperty("content", pergunta);
        JsonArray mensagens = new JsonArray();
        mensagens.add(mensagem);

        JsonObject corpo = new JsonObject();
        corpo.addProperty("model", config.modelo());
        corpo.addProperty("max_tokens", 1024);
        corpo.addProperty("system", Instrucoes.SISTEMA);
        corpo.add("tools", ferramentas);
        corpo.add("tool_choice", escolha);
        corpo.add("messages", mensagens);

        HttpRequest requisicao = HttpRequest.newBuilder(URI.create(URL))
                .timeout(Duration.ofSeconds(60))
                .header("content-type", "application/json")
                .header("x-api-key", config.chaveApi())
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(corpo.toString(), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> resposta = http.send(requisicao, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resposta.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                        "A API de IA respondeu " + resposta.statusCode() + ": " + cortar(resposta.body()));
            }
            return extrairAcoes(resposta.body());
        } catch (IOException e) {
            throw new IllegalStateException("Não consegui falar com a API de IA: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("A chamada à API de IA foi interrompida.", e);
        }
    }

    /** Lê a resposta da API e devolve as ações. Público para poder ser testado sem rede. */
    public static List<Acao> extrairAcoes(String respostaJson) {
        JsonArray conteudo;
        try {
            JsonElement raiz = JsonParser.parseString(respostaJson);
            conteudo = raiz.isJsonObject() && raiz.getAsJsonObject().has("content")
                    ? raiz.getAsJsonObject().getAsJsonArray("content")
                    : null;
        } catch (JsonParseException | IllegalStateException | ClassCastException e) {
            throw new IllegalStateException("A resposta da IA não é um JSON válido.", e);
        }
        if (conteudo == null) {
            throw new IllegalStateException("A resposta da IA veio sem conteúdo.");
        }

        for (JsonElement item : conteudo) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject bloco = item.getAsJsonObject();
            if (!"tool_use".equals(ExtratorDeAcoes.texto(bloco, "type"))) {
                continue;
            }
            JsonElement entrada = bloco.get("input");
            if (entrada == null || !entrada.isJsonObject()) {
                continue;
            }
            List<Acao> acoes = ExtratorDeAcoes.doObjeto(entrada.getAsJsonObject());
            if (acoes != null) {
                return acoes;
            }
        }
        throw new IllegalStateException("A IA não devolveu as ações no formato esperado.");
    }

    private static String cortar(String texto) {
        return texto.length() <= 300 ? texto : texto.substring(0, 300) + "...";
    }
}
