package gerenciadordecontas;

import gerenciadordecontas.config.AiConfig;
import gerenciadordecontas.config.Env;
import gerenciadordecontas.config.MailConfig;
import gerenciadordecontas.config.Settings;
import gerenciadordecontas.config.SheetsConfig;
import gerenciadordecontas.email.Caixa;
import gerenciadordecontas.email.JakartaCaixa;
import gerenciadordecontas.estado.Estado;
import gerenciadordecontas.estado.RegistroMudancas;
import gerenciadordecontas.fluxo.EntregaPorEmail;
import gerenciadordecontas.fluxo.Entregador;
import gerenciadordecontas.fluxo.Orquestrador;
import gerenciadordecontas.fluxo.ProcessadorDeRespostas;
import gerenciadordecontas.ia.ComparadorDeModelos;
import gerenciadordecontas.ia.ComparadorGemini;
import gerenciadordecontas.ia.InterpretadorAnthropic;
import gerenciadordecontas.ia.InterpretadorGemini;
import gerenciadordecontas.ia.InterpretadorDeTexto;
import gerenciadordecontas.ia.InterpretadorIndisponivel;
import gerenciadordecontas.nomes.Apelidos;
import gerenciadordecontas.nomes.ContaMatcher;
import gerenciadordecontas.planilha.GoogleSheetsGateway;
import gerenciadordecontas.planilha.PlanilhaContas;
import gerenciadordecontas.planilha.SheetsGateway;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Consumer;

/** Monta o programa a partir da configuração (variáveis de ambiente e .env). */
final class Composicao {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Composicao() {
    }

    static SheetsGateway planilha() {
        return new GoogleSheetsGateway(SheetsConfig.deAmbiente());
    }

    static Orquestrador orquestrador() {
        SheetsConfig sheetsConfig = SheetsConfig.deAmbiente();
        SheetsGateway sheets = new GoogleSheetsGateway(sheetsConfig);
        Settings settings = Settings.deAmbiente();
        MailConfig mail = MailConfig.deAmbiente();
        Clock relogio = Clock.systemDefaultZone();

        PlanilhaContas contas = new PlanilhaContas(sheets);
        Estado estado = new Estado(sheets, relogio);
        RegistroMudancas registro = new RegistroMudancas(sheets, relogio);
        ContaMatcher matcher = new ContaMatcher(Apelidos.carregar(settings.arquivoApelidos()));

        InterpretadorDeTexto interpretador = interpretador(relogio);

        Caixa caixa = new JakartaCaixa(mail);
        Entregador entregador =
                new EntregaPorEmail(sheets, caixa, mail.destinatario(), sheetsConfig.planilhaId(), relogio);

        ProcessadorDeRespostas processador = new ProcessadorDeRespostas(
                contas, matcher, interpretador, registro, estado, settings, entregador, relogio);

        return new Orquestrador(contas, estado, caixa, processador, settings, relogio, mail.destinatario(), registrarLog());
    }

    /** Sem chave de IA, avisos e "desfazer" continuam funcionando; só o texto livre falha. */
    static InterpretadorDeTexto interpretador(Clock relogio) {
        Optional<AiConfig> ia = AiConfig.deAmbiente();
        if (ia.isEmpty()) {
            return new InterpretadorIndisponivel();
        }
        AiConfig config = ia.get();
        return switch (config.provedor()) {
            case GEMINI -> new InterpretadorGemini(HttpClient.newHttpClient(), config, relogio);
            case ANTHROPIC -> new InterpretadorAnthropic(HttpClient.newHttpClient(), config, relogio);
        };
    }

    /** Só existe para o Gemini: lista os modelos da chave e mede cada um. */
    static ComparadorDeModelos comparador(Clock relogio) {
        AiConfig config = AiConfig.deAmbiente()
                .filter(c -> c.provedor() == AiConfig.Provedor.GEMINI)
                .orElseThrow(() -> new IllegalStateException("Defina GEMINI_API_KEY no .env para medir os modelos."));
        return new ComparadorGemini(HttpClient.newHttpClient(), config, relogio);
    }

    /** Escreve no console e no arquivo gerenciadordecontas.log (útil quando roda sem janela, pelo Agendador). */
    private static Consumer<String> registrarLog() {
        Path arquivo = Paths.get("gerenciadordecontas.log").toAbsolutePath();
        Path alternativo = Env.pastaDoPrograma().resolve("gerenciadordecontas.log");
        return mensagem -> {
            String linha = "[" + LocalDateTime.now().format(HORA) + "] " + mensagem;
            System.out.println(linha);
            try {
                Path destino = Files.isWritable(arquivo.getParent()) ? arquivo : alternativo;
                Files.writeString(destino, linha + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                // Falha ao gravar o log não deve derrubar o programa.
            }
        };
    }
}
