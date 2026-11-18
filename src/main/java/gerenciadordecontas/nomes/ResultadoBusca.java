package gerenciadordecontas.nomes;

import gerenciadordecontas.modelo.Conta;
import java.util.List;

/** Resultado de procurar uma conta pelo nome dito. conta só existe quando estado é ENCONTRADA. */
public record ResultadoBusca(Estado estado, Conta conta, List<Conta> candidatas) {

    public enum Estado {
        ENCONTRADA,
        AMBIGUA,
        NAO_ENCONTRADA
    }
}
