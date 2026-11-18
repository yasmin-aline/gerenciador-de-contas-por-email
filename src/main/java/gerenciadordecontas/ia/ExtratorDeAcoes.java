package gerenciadordecontas.ia;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gerenciadordecontas.modelo.Acao;
import gerenciadordecontas.modelo.Bloco;
import gerenciadordecontas.modelo.Entrega;
import gerenciadordecontas.modelo.TipoAcao;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Converte o JSON {"acoes": [...]} devolvido pela IA em ações do programa. Serve a qualquer provedor. */
final class ExtratorDeAcoes {

    private ExtratorDeAcoes() {
    }

    /** Devolve vazio se o objeto não tem o campo "acoes" como lista. */
    static List<Acao> doObjeto(JsonObject entrada) {
        JsonElement lista = entrada.get("acoes");
        if (lista == null || !lista.isJsonArray()) {
            return null;
        }
        List<Acao> acoes = new ArrayList<>();
        for (JsonElement acao : (JsonArray) lista) {
            if (acao.isJsonObject()) {
                acoes.add(mapear(acao.getAsJsonObject()));
            }
        }
        return acoes;
    }

    static Acao mapear(JsonObject no) {
        String conta = texto(no, "conta");
        return new Acao(
                tipoDe(texto(no, "tipo")),
                conta == null ? "" : conta.strip(),
                decimal(no, "valor"),
                blocoDe(texto(no, "bloco")),
                inteiro(no, "dia_vencimento"),
                numero(no, "confianca", 0.5),
                entregaDe(texto(no, "enviar_o_que")),
                mesDe(inteiro(no, "mes")));
    }

    static TipoAcao tipoDe(String texto) {
        if (texto == null) {
            return TipoAcao.DESCONHECIDO;
        }
        return switch (texto) {
            case "pagar" -> TipoAcao.PAGAR;
            case "cadastrar" -> TipoAcao.CADASTRAR;
            case "desfazer" -> TipoAcao.DESFAZER;
            case "enviar" -> TipoAcao.ENVIAR;
            default -> TipoAcao.DESCONHECIDO;
        };
    }

    static Bloco blocoDe(String texto) {
        if ("dia15".equals(texto)) {
            return Bloco.DIA_15;
        }
        if ("fim_mes".equals(texto)) {
            return Bloco.FIM_DO_MES;
        }
        return null;
    }

    private static Entrega entregaDe(String texto) {
        if (texto == null) {
            return null;
        }
        return switch (texto) {
            case "link" -> Entrega.LINK;
            case "resumo" -> Entrega.RESUMO;
            case "arquivo" -> Entrega.ARQUIVO;
            default -> null;
        };
    }

    private static Integer mesDe(Integer mes) {
        return mes != null && mes >= 1 && mes <= 12 ? mes : null;
    }

    static String texto(JsonObject no, String campo) {
        JsonElement valor = no.get(campo);
        if (valor == null || !valor.isJsonPrimitive()) {
            return null;
        }
        return valor.getAsString();
    }

    static BigDecimal decimal(JsonObject no, String campo) {
        try {
            JsonElement valor = no.get(campo);
            return valor == null || valor.isJsonNull() ? null : valor.getAsBigDecimal();
        } catch (RuntimeException e) {
            return null;
        }
    }

    static Integer inteiro(JsonObject no, String campo) {
        try {
            JsonElement valor = no.get(campo);
            return valor == null || valor.isJsonNull() ? null : valor.getAsInt();
        } catch (RuntimeException e) {
            return null;
        }
    }

    static double numero(JsonObject no, String campo, double padrao) {
        try {
            JsonElement valor = no.get(campo);
            return valor == null || valor.isJsonNull() ? padrao : valor.getAsDouble();
        } catch (RuntimeException e) {
            return padrao;
        }
    }
}
