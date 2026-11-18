package gerenciadordecontas;

import gerenciadordecontas.config.Env;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Ponto de entrada único. O Agendador de Tarefas só precisa chamar "gerenciadordecontas tick";
 * a lógica (hoje é dia de aviso? tem resposta nova?) fica nas outras classes.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        // UTF-8 explícito: o console do Windows costuma usar outra codificação e estragaria os acentos.
        PrintStream saida = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        PrintStream erros = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
        System.setOut(saida);
        System.setErr(erros);

        Env.carregarDotEnv();

        Cli cli = new Cli(saida, erros, Composicao::planilha, Composicao::orquestrador,
                () -> Composicao.interpretador(java.time.Clock.systemDefaultZone()),
                () -> Composicao.comparador(java.time.Clock.systemDefaultZone()), LocalDate::now);
        System.exit(cli.executar(args));
    }
}
