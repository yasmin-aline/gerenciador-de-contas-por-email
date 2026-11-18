package gerenciadordecontas.ia;

import gerenciadordecontas.modelo.Acao;
import java.util.List;

/** Usado quando não há chave de IA configurada: falha com uma mensagem clara só se for preciso. */
public final class InterpretadorIndisponivel implements InterpretadorDeTexto {

    @Override
    public List<Acao> interpretar(String texto, List<String> contasDoMes) {
        throw new IllegalStateException("Defina GEMINI_API_KEY (ou ANTHROPIC_API_KEY) no .env para o bot entender respostas em texto livre.");
    }
}
