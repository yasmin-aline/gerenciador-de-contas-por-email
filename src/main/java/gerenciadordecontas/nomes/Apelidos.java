package gerenciadordecontas.nomes;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import gerenciadordecontas.config.Env;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public final class Apelidos {

    private static final Type TIPO = new TypeToken<Map<String, String>>() { }.getType();

    private Apelidos() {
    }

    /**
     * Lê o apelidos.json ({"energia": "luz"}). Procura no caminho informado, na pasta atual e na pasta
     * do programa. Sem arquivo, não há apelidos.
     */
    public static Map<String, String> carregar(String caminho) {
        Path arquivo = localizar(caminho);
        if (arquivo == null) {
            return Map.of();
        }
        try {
            return ler(Files.readString(arquivo, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Não consegui ler " + arquivo + ": " + e.getMessage(), e);
        }
    }

    static Map<String, String> ler(String json) {
        try {
            Map<String, String> mapa = new Gson().fromJson(json, TIPO);
            return mapa != null ? mapa : Map.of();
        } catch (JsonParseException e) {
            throw new IllegalStateException("apelidos.json inválido: " + e.getMessage(), e);
        }
    }

    private static Path localizar(String caminho) {
        if (caminho != null && !caminho.isBlank()) {
            Path informado = Env.resolverCaminho(caminho);
            return Files.isRegularFile(informado) ? informado : null;
        }
        for (Path candidato : new Path[] {
            Paths.get("apelidos.json").toAbsolutePath(), Env.pastaDoPrograma().resolve("apelidos.json")
        }) {
            if (Files.isRegularFile(candidato)) {
                return candidato;
            }
        }
        return null;
    }
}
