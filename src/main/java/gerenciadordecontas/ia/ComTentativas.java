package gerenciadordecontas.ia;

import java.io.IOException;
import java.util.function.LongConsumer;

/** Repete uma chamada HTTP que falhou por sobrecarga temporária do servidor (429, 5xx) ou de rede. */
final class ComTentativas {

    record Resposta(int status, String corpo) {
        boolean ok() {
            return status / 100 == 2;
        }

        /** Erros que costumam passar sozinhos: limite de uso e indisponibilidade do servidor. */
        boolean transitoria() {
            return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
        }
    }

    @FunctionalInterface
    interface Chamada {
        Resposta executar() throws IOException, InterruptedException;
    }

    private ComTentativas() {
    }

    /**
     * Tenta até {@code maximo} vezes, esperando {@code esperaInicialMs}, o dobro, e assim por diante
     * entre as tentativas. Devolve a última resposta (que pode ser um erro) ou relança a falha de rede.
     */
    static Resposta executar(Chamada chamada, int maximo, long esperaInicialMs, LongConsumer dormir)
            throws IOException, InterruptedException {
        long espera = esperaInicialMs;
        for (int tentativa = 1; ; tentativa++) {
            Resposta resposta;
            try {
                resposta = chamada.executar();
            } catch (IOException e) {
                if (tentativa >= maximo) {
                    throw e;
                }
                dormir.accept(espera);
                espera *= 2;
                continue;
            }
            if (resposta.ok() || !resposta.transitoria() || tentativa >= maximo) {
                return resposta;
            }
            dormir.accept(espera);
            espera *= 2;
        }
    }
}
