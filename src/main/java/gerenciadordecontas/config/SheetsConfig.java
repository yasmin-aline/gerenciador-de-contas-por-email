package gerenciadordecontas.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Acesso à planilha: id e chave JSON da conta de serviço. */
public record SheetsConfig(String planilhaId, String chaveJson) {

    public static SheetsConfig deAmbiente() {
        String id = Env.obrigatoria("SPREADSHEET_ID", "o trecho da URL da planilha entre /d/ e /edit");

        // Duas formas de entregar a chave: o conteúdo do JSON ou o caminho do arquivo.
        String json = Env.opcional("GOOGLE_SERVICE_ACCOUNT_JSON");
        String arquivo = Env.opcional("GOOGLE_SERVICE_ACCOUNT_FILE");

        if (json == null && arquivo != null) {
            try {
                json = Files.readString(Env.resolverCaminho(arquivo), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Não consegui ler a chave em " + arquivo + ": " + e.getMessage(), e);
            }
        }
        if (json == null || json.isBlank()) {
            throw new IllegalStateException(
                    "Defina GOOGLE_SERVICE_ACCOUNT_FILE (caminho do .json) ou GOOGLE_SERVICE_ACCOUNT_JSON (conteúdo).");
        }
        return new SheetsConfig(id, json);
    }
}
