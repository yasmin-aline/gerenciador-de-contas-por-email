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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * Entende a resposta em texto livre chamando a API do Gemini (generateContent) em modo JSON.
 * O formato esperado vai nas instruções e o programa valida o que volta; a IA só extrai
 * (conta, valor, ação), e casar o nome com a planilha e gravar é feito pelo programa.
 */
public final class InterpretadorGemini implements InterpretadorDeTexto {

    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final int TENTATIVAS_PADRAO = 3;
    private static final long ESPERA_INICIAL_MS = 3000;

    /** O servidor do Gemini não conseguiu atender (sobrecarga, limite de uso, rede), mesmo após tentar de novo. */
    static final class Indisponivel extends IllegalStateException {
        Indisponivel(String mensagem) {
            super(mensagem);
        }
    }

    private final HttpClient http;
    private final AiConfig config;
    private final Clock relogio;
    private final String base;
    private final LongConsumer dormir;
    private final int tentativas;

    public InterpretadorGemini(HttpClient http, AiConfig config, Clock relogio) {
        this(http, config, relogio, BASE, InterpretadorGemini::dormirDe);
    }

    /** Para testes: endereço e espera trocáveis. */
    InterpretadorGemini(HttpClient http, AiConfig config, Clock relogio, String base, LongConsumer dormir) {
        this(http, config, relogio, base, dormir, TENTATIVAS_PADRAO);
    }

    /** Também troca o número de tentativas (1 = sem repetir, usado para medir o tempo de cada modelo). */
    InterpretadorGemini(HttpClient http, AiConfig config, Clock relogio, String base, LongConsumer dormir,
            int tentativas) {
        this.tentativas = tentativas;
        this.http = http;
        this.config = config;
        this.relogio = relogio;
        this.base = base;
        this.dormir = dormir;
    }

    private static void dormirDe(long milissegundos) {
        try {
            Thread.sleep(milissegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("A chamada à API do Gemini foi interrompida.", e);
        }
    }

    @Override
    public List<Acao> interpretar(String texto, List<String> contasDoMes) {
        String instrucoes = Instrucoes.SISTEMA
                + "\nSua resposta deve ser somente um objeto JSON, sem texto fora dele, neste formato"
                + " (JSON Schema):\n" + Instrucoes.SCHEMA;

        String pergunta = "Hoje é " + LocalDate.now(relogio).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".\n"
                + "Contas já cadastradas neste mês: "
                + (contasDoMes.isEmpty() ? "(nenhuma)" : String.join("; ", contasDoMes)) + "\n\n"
                + "Mensagem da pessoa:\n" + texto + "\n";

        JsonObject corpo = new JsonObject();
        corpo.add("systemInstruction", conteudo(null, instrucoes));
        JsonArray conteudos = new JsonArray();
        conteudos.add(conteudo("user", pergunta));
        corpo.add("contents", conteudos);

        JsonObject geracao = new JsonObject();
        geracao.addProperty("responseMimeType", "application/json");
        geracao.addProperty("temperature", 0);
        corpo.add("generationConfig", geracao);

        // Se o modelo principal estiver sobrecarregado mesmo depois de tentar de novo, usa o reserva.
        List<String> modelos = new ArrayList<>();
        modelos.add(config.modelo());
        if (config.modeloReserva() != null && !config.modeloReserva().equals(config.modelo())) {
            modelos.add(config.modeloReserva());
        }
        Indisponivel ultima = null;
        for (String modelo : modelos) {
            try {
                return extrairAcoes(chamar(modelo, corpo.toString()));
            } catch (Indisponivel e) {
                ultima = e;
            }
        }
        throw ultima;
    }

    /** Uma chamada ao modelo, repetida se o servidor estiver sobrecarregado. Devolve o JSON da resposta. */
    private String chamar(String modelo, String corpoJson) {
        String url = base + URLEncoder.encode(modelo, StandardCharsets.UTF_8) + ":generateContent";
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("content-type", "application/json")
                .header("x-goog-api-key", config.chaveApi())
                .POST(HttpRequest.BodyPublishers.ofString(corpoJson, StandardCharsets.UTF_8))
                .build();

        try {
            ComTentativas.Resposta resposta = ComTentativas.executar(() -> {
                HttpResponse<String> enviada = http.send(requisicao, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                return new ComTentativas.Resposta(enviada.statusCode(), enviada.body());
            }, tentativas, ESPERA_INICIAL_MS, dormir);

            if (!resposta.ok()) {
                String mensagem = "A API do Gemini respondeu " + resposta.status() + " (modelo " + modelo + "): "
                        + cortar(resposta.corpo());
                throw resposta.transitoria() ? new Indisponivel(mensagem) : new IllegalStateException(mensagem);
            }
            return resposta.corpo();
        } catch (IOException e) {
            throw new Indisponivel("Não consegui falar com a API do Gemini: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("A chamada à API do Gemini foi interrompida.", e);
        }
    }

    private static JsonObject conteudo(String papel, String texto) {
        JsonObject parte = new JsonObject();
        parte.addProperty("text", texto);
        JsonArray partes = new JsonArray();
        partes.add(parte);
        JsonObject conteudo = new JsonObject();
        if (papel != null) {
            conteudo.addProperty("role", papel);
        }
        conteudo.add("parts", partes);
        return conteudo;
    }

    /** Lê a resposta da API e devolve as ações. Público para poder ser testado sem rede. */
    public static List<Acao> extrairAcoes(String respostaJson) {
        JsonObject raiz;
        try {
            JsonElement elemento = JsonParser.parseString(respostaJson);
            if (!elemento.isJsonObject()) {
                throw new IllegalStateException("A resposta do Gemini não é um objeto JSON.");
            }
            raiz = elemento.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new IllegalStateException("A resposta do Gemini não é um JSON válido.", e);
        }

        String texto = textoDaPrimeiraResposta(raiz);
        if (texto == null) {
            throw new IllegalStateException("O Gemini não devolveu texto" + motivoDoBloqueio(raiz) + ".");
        }

        JsonElement dados;
        try {
            dados = JsonParser.parseString(semCercas(texto));
        } catch (JsonParseException e) {
            throw new IllegalStateException("O Gemini devolveu algo que não é JSON: " + cortar(texto), e);
        }

        List<Acao> acoes = null;
        if (dados.isJsonObject()) {
            acoes = ExtratorDeAcoes.doObjeto(dados.getAsJsonObject());
        }
        if (acoes == null) {
            throw new IllegalStateException("O Gemini não devolveu as ações no formato esperado: " + cortar(texto));
        }
        return acoes;
    }

    /** Junta os trechos de texto da primeira resposta, ignorando os "pensamentos" do modelo. */
    private static String textoDaPrimeiraResposta(JsonObject raiz) {
        JsonElement candidatas = raiz.get("candidates");
        if (candidatas == null || !candidatas.isJsonArray() || candidatas.getAsJsonArray().isEmpty()) {
            return null;
        }
        JsonElement primeira = candidatas.getAsJsonArray().get(0);
        if (!primeira.isJsonObject() || !primeira.getAsJsonObject().has("content")
                || !primeira.getAsJsonObject().get("content").isJsonObject()) {
            return null;
        }
        JsonElement partes = primeira.getAsJsonObject().getAsJsonObject("content").get("parts");
        if (partes == null || !partes.isJsonArray()) {
            return null;
        }
        StringBuilder texto = new StringBuilder();
        for (JsonElement parte : partes.getAsJsonArray()) {
            if (!parte.isJsonObject()) {
                continue;
            }
            JsonObject objeto = parte.getAsJsonObject();
            boolean pensamento = objeto.has("thought") && objeto.get("thought").isJsonPrimitive()
                    && objeto.get("thought").getAsBoolean();
            String trecho = ExtratorDeAcoes.texto(objeto, "text");
            if (!pensamento && trecho != null) {
                texto.append(trecho);
            }
        }
        return texto.length() == 0 ? null : texto.toString();
    }

    private static String motivoDoBloqueio(JsonObject raiz) {
        JsonElement feedback = raiz.get("promptFeedback");
        if (feedback != null && feedback.isJsonObject()) {
            String motivo = ExtratorDeAcoes.texto(feedback.getAsJsonObject(), "blockReason");
            if (motivo != null) {
                return " (bloqueado: " + motivo + ")";
            }
        }
        return "";
    }

    /** Alguns modelos embrulham o JSON em ```json ... ``` mesmo no modo JSON. */
    static String semCercas(String texto) {
        String limpo = texto.strip();
        if (limpo.startsWith("```")) {
            int fimDaPrimeiraLinha = limpo.indexOf('\n');
            limpo = fimDaPrimeiraLinha < 0 ? "" : limpo.substring(fimDaPrimeiraLinha + 1);
            if (limpo.stripTrailing().endsWith("```")) {
                limpo = limpo.stripTrailing();
                limpo = limpo.substring(0, limpo.length() - 3);
            }
        }
        return limpo.strip();
    }

    private static String cortar(String texto) {
        return texto.length() <= 300 ? texto : texto.substring(0, 300) + "...";
    }
}
