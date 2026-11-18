package gerenciadordecontas.estado;

/** Uma linha da aba "_estado". linha é a linha da planilha (começa em 1). */
public record EntradaEstado(String chave, String valor, int linha) {
}
