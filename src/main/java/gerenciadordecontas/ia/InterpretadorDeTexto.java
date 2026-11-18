package gerenciadordecontas.ia;

import gerenciadordecontas.modelo.Acao;
import java.util.List;

/** Transforma texto livre em ações estruturadas. Só extrai; quem decide e grava é o programa. */
public interface InterpretadorDeTexto {

    List<Acao> interpretar(String texto, List<String> contasDoMes);
}
