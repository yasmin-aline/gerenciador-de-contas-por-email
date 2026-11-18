package gerenciadordecontas.ia;

import java.util.List;
import java.util.function.Consumer;

/** Mede quanto cada modelo de IA demora e se entende uma frase de teste. */
public interface ComparadorDeModelos {

    record Resultado(String modelo, boolean ok, long milissegundos, String detalhe) {
    }

    /**
     * Testa os modelos cujo nome contém o filtro (vazio = os "flash"). Chama {@code aoMedir} a cada
     * modelo terminado e devolve tudo, com os que deram certo primeiro, do mais rápido ao mais lento.
     */
    List<Resultado> comparar(String filtro, Consumer<Resultado> aoMedir);
}
