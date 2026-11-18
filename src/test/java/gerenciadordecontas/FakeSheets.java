package gerenciadordecontas;

import gerenciadordecontas.planilha.Dinheiro;
import gerenciadordecontas.planilha.SheetsGateway;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Planilha em memória. Números gravados voltam formatados como o Google devolveria ("R$ 130,00"). */
public final class FakeSheets implements SheetsGateway {

    private record Pos(int linha, int coluna) {
    }

    private static final Pattern A1 = Pattern.compile("^([A-Z]+)(\\d*)(?::([A-Z]+)(\\d*))?$");

    private final Map<String, Map<Pos, Object>> abas = new LinkedHashMap<>();

    public FakeSheets cria(String aba) {
        abas.computeIfAbsent(aba, a -> new HashMap<>());
        return this;
    }

    public FakeSheets set(String aba, String celula, Object valor) {
        cria(aba);
        abas.get(aba).put(posicao(celula), valor);
        return this;
    }

    public String get(String aba, String celula) {
        Pos p = posicao(celula);
        return texto(aba, p.linha(), p.coluna());
    }

    @Override
    public List<String> listarAbas() {
        return new ArrayList<>(abas.keySet());
    }

    @Override
    public List<List<String>> lerIntervalo(String aba, String intervalo) {
        if (!abas.containsKey(aba)) {
            throw new IllegalStateException("Aba inexistente: " + aba);
        }
        int maxLinha = abas.get(aba).keySet().stream().mapToInt(Pos::linha).max().orElse(0);
        int[] r = intervaloA1(intervalo, maxLinha);

        List<List<String>> linhas = new ArrayList<>();
        for (int l = r[0]; l <= r[2]; l++) {
            List<String> linha = new ArrayList<>();
            for (int c = r[1]; c <= r[3]; c++) {
                linha.add(texto(aba, l, c));
            }
            while (!linha.isEmpty() && linha.get(linha.size() - 1).isEmpty()) {
                linha.remove(linha.size() - 1); // o Google corta células vazias do fim
            }
            linhas.add(linha);
        }
        while (!linhas.isEmpty() && linhas.get(linhas.size() - 1).isEmpty()) {
            linhas.remove(linhas.size() - 1);
        }
        return linhas;
    }

    @Override
    public void escreverCelulas(String aba, Map<String, Object> celulas) {
        cria(aba);
        for (Map.Entry<String, Object> par : celulas.entrySet()) {
            Pos chave = posicao(par.getKey());
            Object valor = par.getValue() == null ? "" : par.getValue();
            if (valor instanceof String s && s.isEmpty()) {
                abas.get(aba).remove(chave);
            } else {
                abas.get(aba).put(chave, valor);
            }
        }
    }

    @Override
    public boolean garantirAba(String aba) {
        boolean existe = abas.containsKey(aba);
        cria(aba);
        return !existe;
    }

    @Override
    public void acrescentarLinha(String aba, List<Object> linha) {
        cria(aba);
        int proxima = abas.get(aba).keySet().stream().mapToInt(Pos::linha).max().orElse(0) + 1;
        for (int c = 0; c < linha.size(); c++) {
            Object valor = linha.get(c) == null ? "" : linha.get(c);
            if (valor instanceof String s && s.isEmpty()) {
                continue;
            }
            abas.get(aba).put(new Pos(proxima, c), valor);
        }
    }

    private String texto(String aba, int linha, int coluna) {
        Map<Pos, Object> celulas = abas.get(aba);
        if (celulas == null) {
            return "";
        }
        Object valor = celulas.get(new Pos(linha, coluna));
        if (valor == null) {
            return "";
        }
        return valor instanceof BigDecimal d ? Dinheiro.formatar(d) : valor.toString();
    }

    private static Pos posicao(String celula) {
        int[] r = intervaloA1(celula, 0);
        return new Pos(r[0], r[1]);
    }

    /** Devolve {linha1, coluna1, linha2, coluna2}. Linhas começam em 1; colunas em 0. Linha omitida vira 1 até maxLinha. */
    private static int[] intervaloA1(String intervalo, int maxLinha) {
        Matcher m = A1.matcher(intervalo);
        if (!m.matches()) {
            throw new IllegalArgumentException("Intervalo inválido: " + intervalo);
        }
        int c1 = coluna(m.group(1));
        int r1 = m.group(2).isEmpty() ? 1 : Integer.parseInt(m.group(2));
        if (m.group(3) == null) {
            return new int[] {r1, c1, r1, c1};
        }
        int c2 = coluna(m.group(3));
        int r2 = m.group(4).isEmpty() ? maxLinha : Integer.parseInt(m.group(4));
        return new int[] {r1, c1, r2, c2};
    }

    private static int coluna(String letras) {
        int n = 0;
        for (char ch : letras.toCharArray()) {
            n = n * 26 + (ch - 'A' + 1);
        }
        return n - 1;
    }
}
