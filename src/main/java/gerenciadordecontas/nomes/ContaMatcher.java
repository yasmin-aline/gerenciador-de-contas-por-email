package gerenciadordecontas.nomes;

import gerenciadordecontas.modelo.Conta;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Casa o nome dito ("luz") com a conta da planilha ("Conta de Luz"). */
public final class ContaMatcher {

    private final Map<String, String> apelidos = new HashMap<>();

    public ContaMatcher() {
        this(Map.of());
    }

    public ContaMatcher(Map<String, String> apelidos) {
        for (Map.Entry<String, String> par : apelidos.entrySet()) {
            String chave = Texto.chaveDeConta(par.getKey());
            if (!chave.isEmpty()) {
                this.apelidos.put(chave, par.getValue());
            }
        }
    }

    public ResultadoBusca buscar(String nomeDito, List<Conta> contas) {
        String chave = Texto.chaveDeConta(nomeDito);
        if (chave.isEmpty()) {
            return nenhuma();
        }

        // Apelido: "energia" vira "luz".
        String alvo = apelidos.get(chave);
        if (alvo != null) {
            chave = Texto.chaveDeConta(alvo);
            if (chave.isEmpty()) {
                return nenhuma();
            }
        }

        // 1) Mesmo nome, ignorando maiúsculas, acento e "conta de"/"fatura".
        String chaveFinal = chave;
        List<Conta> iguais = contas.stream()
                .filter(c -> Texto.chaveDeConta(c.nome()).equals(chaveFinal))
                .toList();
        if (!iguais.isEmpty()) {
            return resultado(iguais);
        }

        // 2) Todas as palavras ditas aparecem no nome da conta ("nubank" em "Cartão Nubank").
        List<String> palavras = Arrays.asList(chave.split(" "));
        List<Conta> contem = contas.stream()
                .filter(c -> Arrays.asList(Texto.tokens(c.nome())).containsAll(palavras))
                .toList();
        return resultado(contem);
    }

    /** Já existe conta com esse mesmo nome (sem aproximação)? */
    public boolean existeIgual(String nome, List<Conta> contas) {
        String chave = Texto.chaveDeConta(nome);
        return !chave.isEmpty() && contas.stream().anyMatch(c -> Texto.chaveDeConta(c.nome()).equals(chave));
    }

    private static ResultadoBusca nenhuma() {
        return new ResultadoBusca(ResultadoBusca.Estado.NAO_ENCONTRADA, null, List.of());
    }

    private static ResultadoBusca resultado(List<Conta> candidatas) {
        if (candidatas.isEmpty()) {
            return nenhuma();
        }
        if (candidatas.size() == 1) {
            return new ResultadoBusca(ResultadoBusca.Estado.ENCONTRADA, candidatas.get(0), candidatas);
        }
        return new ResultadoBusca(ResultadoBusca.Estado.AMBIGUA, null, candidatas);
    }
}
