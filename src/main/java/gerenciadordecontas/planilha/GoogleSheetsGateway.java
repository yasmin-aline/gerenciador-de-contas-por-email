package gerenciadordecontas.planilha;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import gerenciadordecontas.config.SheetsConfig;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Acesso à planilha pela Google Sheets API, com conta de serviço. */
public final class GoogleSheetsGateway implements SheetsGateway {

    private static final String ESCOPO_DRIVE = "https://www.googleapis.com/auth/drive.readonly";
    private static final String TIPO_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final Sheets servico;
    private final GoogleCredentials credenciais;
    private final String planilhaId;

    public GoogleSheetsGateway(SheetsConfig config) {
        try {
            // O escopo do Drive só é usado para baixar a planilha como .xlsx (anexo); leitura e escrita vão pela Sheets API.
            this.credenciais = GoogleCredentials
                    .fromStream(new ByteArrayInputStream(config.chaveJson().getBytes(StandardCharsets.UTF_8)))
                    .createScoped(List.of(SheetsScopes.SPREADSHEETS, ESCOPO_DRIVE));

            this.servico = new Sheets.Builder(
                            GoogleNetHttpTransport.newTrustedTransport(),
                            GsonFactory.getDefaultInstance(),
                            new HttpCredentialsAdapter(credenciais))
                    .setApplicationName("GerenciadorDeContas")
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Não consegui conectar ao Google Sheets: " + e.getMessage(), e);
        }
        this.planilhaId = config.planilhaId();
    }

    @Override
    public List<String> listarAbas() {
        try {
            Spreadsheet planilha = servico.spreadsheets().get(planilhaId)
                    .setFields("sheets.properties.title")
                    .execute();
            List<String> abas = new ArrayList<>();
            if (planilha.getSheets() != null) {
                for (Sheet aba : planilha.getSheets()) {
                    abas.add(aba.getProperties().getTitle());
                }
            }
            return abas;
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao listar as abas: " + e.getMessage(), e);
        }
    }

    @Override
    public List<List<String>> lerIntervalo(String aba, String intervalo) {
        try {
            // Aspas simples em volta do nome da aba funcionam para qualquer nome.
            // FORMATTED_VALUE: texto como aparece na tela (ex.: "R$ 130,00"). A planilha precisa estar com localidade Brasil.
            ValueRange resposta = servico.spreadsheets().values()
                    .get(planilhaId, "'" + aba + "'!" + intervalo)
                    .setValueRenderOption("FORMATTED_VALUE")
                    .execute();

            List<List<String>> linhas = new ArrayList<>();
            if (resposta.getValues() != null) {
                for (List<Object> linha : resposta.getValues()) {
                    List<String> colunas = new ArrayList<>();
                    for (Object celula : linha) {
                        colunas.add(celula == null ? "" : celula.toString());
                    }
                    linhas.add(colunas);
                }
            }
            return linhas;
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler " + aba + "!" + intervalo + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void escreverCelulas(String aba, Map<String, Object> celulas) {
        if (celulas.isEmpty()) {
            return;
        }
        try {
            List<ValueRange> dados = new ArrayList<>();
            for (Map.Entry<String, Object> par : celulas.entrySet()) {
                Object valor = par.getValue() == null ? "" : par.getValue();
                List<List<Object>> grade = List.of(List.of(valor));
                dados.add(new ValueRange()
                        .setRange("'" + aba + "'!" + par.getKey())
                        .setValues(grade));
            }

            // RAW: grava exatamente o que mandamos (número como número) e mantém a formatação da célula.
            BatchUpdateValuesRequest corpo = new BatchUpdateValuesRequest()
                    .setValueInputOption("RAW")
                    .setData(dados);
            servico.spreadsheets().values().batchUpdate(planilhaId, corpo).execute();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar em " + aba + ": " + e.getMessage(), e);
        }
    }

    @Override
    public boolean garantirAba(String aba) {
        if (listarAbas().contains(aba)) {
            return false;
        }
        try {
            BatchUpdateSpreadsheetRequest pedido = new BatchUpdateSpreadsheetRequest()
                    .setRequests(List.of(new Request()
                            .setAddSheet(new AddSheetRequest()
                                    .setProperties(new SheetProperties().setTitle(aba)))));
            servico.spreadsheets().batchUpdate(planilhaId, pedido).execute();
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao criar a aba " + aba + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void acrescentarLinha(String aba, List<Object> linha) {
        try {
            List<Object> celulas = new ArrayList<>();
            for (Object valor : linha) {
                celulas.add(valor == null ? "" : valor);
            }
            ValueRange corpo = new ValueRange().setValues(List.of(celulas));
            servico.spreadsheets().values()
                    .append(planilhaId, "'" + aba + "'!A1", corpo)
                    .setValueInputOption("RAW")
                    .setInsertDataOption("INSERT_ROWS")
                    .execute();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao acrescentar linha em " + aba + ": " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] exportarXlsx() {
        try {
            credenciais.refreshIfExpired();
            String token = credenciais.getAccessToken().getTokenValue();
            URI endereco = URI.create("https://www.googleapis.com/drive/v3/files/" + planilhaId
                    + "/export?mimeType=" + URLEncoder.encode(TIPO_XLSX, StandardCharsets.UTF_8));
            HttpResponse<byte[]> resposta = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(20))
                    .build()
                    .send(HttpRequest.newBuilder(endereco)
                                    .header("Authorization", "Bearer " + token)
                                    .timeout(Duration.ofSeconds(90))
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofByteArray());
            if (resposta.statusCode() != 200) {
                String corpo = new String(resposta.body(), StandardCharsets.UTF_8);
                String dica = resposta.statusCode() == 403 && corpo.contains("drive.googleapis.com")
                        ? " Ative a Google Drive API no projeto do Google Cloud (APIs e serviços > Biblioteca > Google Drive API)."
                        : "";
                throw new IllegalStateException("O Google não deixou baixar a planilha (HTTP "
                        + resposta.statusCode() + ")." + dica);
            }
            return resposta.body();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao baixar a planilha: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Download da planilha interrompido.", e);
        }
    }
}
