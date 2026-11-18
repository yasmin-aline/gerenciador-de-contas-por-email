package gerenciadordecontas.ia;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import gerenciadordecontas.config.AiConfig;
import gerenciadordecontas.modelo.Acao;
import gerenciadordecontas.modelo.TipoAcao;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/** Lista os modelos que a chave do Gemini pode usar e mede o tempo de uma chamada pequena em cada um. */
public final class ComparadorGemini implements ComparadorDeModelos {

    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/";
    private static final String FRASE = "paguei o nubank, 600, e a luz, 310";
    private static final List<String> CONTAS = List.of("Cartão Nubank", "Conta de Luz");
    // Modelos que não servem para entender texto: voz, imagem, vídeo, embeddings etc.
    private static final List<String> IGNORAR = List.of(
            "tts", "image", "embedding", "live", "audio", "robotics", "computer-use", "aqa", "imagen", "veo", "lyria");
    private static final long PAUSA_ENTRE_CHAMADAS_MS = 1500;

    private final HttpClient http;
    private final AiConfig config;
    private final Clock relogio;
    private final String base;
    private final LongConsumer dormir;

    public ComparadorGemini(HttpClient http, AiConfig config, Clock relogio) {
        this(http, config, relogio, BASE, ComparadorGemini::dormirDe);
    }

    ComparadorGemini(HttpClient http, AiConfig config, Clock relogio, String base, LongConsumer dormir) {
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
            throw new IllegalStateException("A medição foi interrompida.", e);
        }
    }

    @Override
    public List<Resultado> comparar(String filtro, Consumer<Resultado> aoMedir) {
        List<Resultado> resultados = new ArrayList<>();
        List<String> modelos = listar(filtro);
        for (int i = 0; i < modelos.size(); i++) {
            if (i > 0) {
                dormir.accept(PAUSA_ENTRE_CHAMADAS_MS); // evita estourar o limite por minuto da camada gratuita
            }
            Resultado resultado = medir(modelos.get(i));
            resultados.add(resultado);
            aoMedir.accept(resultado);
        }
        resultados.sort(Comparator.comparing((Resultado r) -> !r.ok()).thenComparingLong(Resultado::milissegundos));
        return resultados;
    }

    /** Nomes dos modelos (sem o prefixo "models/") que aceitam generateContent e passam no filtro. */
    List<String> listar(String filtro) {
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create(base + "models?pageSize=1000"))
                .timeout(Duration.ofSeconds(30))
                .header("x-goog-api-key", config.chaveApi())
                .GET()
                .build();
        String corpo;
        try {
            HttpResponse<String> resposta = http.send(requisicao, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resposta.statusCode() / 100 != 2) {
                throw new IllegalStateException("Não consegui listar os modelos (HTTP " + resposta.statusCode() + "): "
                        + cortar(resposta.body()));
            }
            corpo = resposta.body();
        } catch (IOException e) {
            throw new IllegalStateException("Não consegui falar com a API do Gemini: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("A listagem foi interrompida.", e);
        }

        String busca = filtro == null || filtro.isBlank() ? "flash" : filtro.strip().toLowerCase(Locale.ROOT);
        List<String> nomes = new ArrayList<>();
        try {
            JsonElement raiz = JsonParser.parseString(corpo);
            JsonElement lista = raiz.isJsonObject() ? raiz.getAsJsonObject().get("models") : null;
            if (lista == null || !lista.isJsonArray()) {
                return nomes;
            }
            for (JsonElement item : lista.getAsJsonArray()) {
                if (!item.isJsonObject()) {
                    continue;
                }
                JsonObject modelo = item.getAsJsonObject();
                String nome = texto(modelo, "name");
                if (nome == null || !aceitaGenerateContent(modelo)) {
                    continue;
                }
                nome = nome.startsWith("models/") ? nome.substring("models/".length()) : nome;
                String minusculo = nome.toLowerCase(Locale.ROOT);
                if (minusculo.startsWith("gemini") && minusculo.contains(busca)
                        && IGNORAR.stream().noneMatch(minusculo::contains)) {
                    nomes.add(nome);
                }
            }
        } catch (JsonParseException e) {
            throw new IllegalStateException("A lista de modelos veio num formato inesperado.", e);
        }
        nomes.sort(Comparator.naturalOrder());
        return nomes;
    }

    private static boolean aceitaGenerateContent(JsonObject modelo) {
        JsonElement metodos = modelo.get("supportedGenerationMethods");
        if (metodos == null || !metodos.isJsonArray()) {
            return false;
        }
        for (JsonElement metodo : (JsonArray) metodos) {
            if (metodo.isJsonPrimitive() && "generateContent".equals(metodo.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private Resultado medir(String modelo) {
        AiConfig doModelo = new AiConfig(AiConfig.Provedor.GEMINI, config.chaveApi(), modelo, null);
        // Uma tentativa só: o objetivo é ver como o modelo se comporta, inclusive quando está sobrecarregado.
        InterpretadorGemini interpretador =
                new InterpretadorGemini(http, doModelo, relogio, base + "models/", dormir, 1);

        long inicio = System.nanoTime();
        try {
            List<Acao> acoes = interpretador.interpretar(FRASE, CONTAS);
            long ms = (System.nanoTime() - inicio) / 1_000_000;
            boolean certo = acoes.size() == 2 && acoes.stream().allMatch(a -> a.tipo() == TipoAcao.PAGAR);
            return new Resultado(modelo, certo, ms,
                    certo ? "entendeu as 2 ações" : "entendeu " + acoes.size() + " ação(ões), esperava 2 de pagar");
        } catch (RuntimeException e) {
            long ms = (System.nanoTime() - inicio) / 1_000_000;
            return new Resultado(modelo, false, ms, cortar(primeiraLinha(e.getMessage())));
        }
    }

    private static String texto(JsonObject no, String campo) {
        JsonElement valor = no.get(campo);
        return valor != null && valor.isJsonPrimitive() ? valor.getAsString() : null;
    }

    private static String primeiraLinha(String texto) {
        if (texto == null) {
            return "erro sem mensagem";
        }
        int quebra = texto.indexOf('\n');
        return quebra < 0 ? texto : texto.substring(0, quebra);
    }

    private static String cortar(String texto) {
        return texto.length() <= 110 ? texto : texto.substring(0, 110) + "...";
    }
}
