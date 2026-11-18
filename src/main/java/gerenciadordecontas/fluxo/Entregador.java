package gerenciadordecontas.fluxo;

/** Como a planilha chega até você quando você pede: o link e o arquivo em anexo. */
public interface Entregador {

    /** Endereço para abrir a planilha no navegador. */
    String linkDaPlanilha();

    /**
     * Manda um e-mail para você com a planilha inteira (.xlsx) em anexo.
     * Lança exceção se não conseguir baixar ou enviar.
     */
    void enviarPlanilhaAnexada(String assunto, String corpo);
}
