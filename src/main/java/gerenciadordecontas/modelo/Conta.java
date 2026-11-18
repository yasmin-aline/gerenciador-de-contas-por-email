package gerenciadordecontas.modelo;

import java.math.BigDecimal;

/**
 * Uma conta como está na planilha.
 *
 * @param valor       valor previsto ou pago; null se a célula está vazia
 * @param statusBruto texto original da coluna "Pago?"
 * @param linha       linha da planilha (começa em 1)
 */
public record Conta(String nome, BigDecimal valor, String statusBruto, Bloco bloco, int linha) {

    public boolean pago() {
        return Status.ehPago(statusBruto);
    }
}
