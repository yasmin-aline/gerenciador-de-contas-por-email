package gerenciadordecontas.nomes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ApelidosTest {

    @Test
    void le_o_json_de_apelidos() {
        Map<String, String> apelidos = Apelidos.ler("{\"energia\": \"luz\", \"net\": \"internet\"}");

        assertEquals("luz", apelidos.get("energia"));
        assertEquals(2, apelidos.size());
    }

    @Test
    void arquivo_vazio_nao_tem_apelidos() {
        assertTrue(Apelidos.ler("").isEmpty());
    }

    @Test
    void json_invalido_da_erro_claro() {
        assertThrows(IllegalStateException.class, () -> Apelidos.ler("{ isso não é json"));
    }

    @Test
    void normalizar_tira_acento_e_pontuacao() {
        assertEquals("cartao inter", Texto.normalizar("  Cartão   Inter!! "));
        assertEquals("ingles", Texto.chaveDeConta("Fatura Inglês"));
        assertEquals("", Texto.chaveDeConta("Conta de"));
    }
}
