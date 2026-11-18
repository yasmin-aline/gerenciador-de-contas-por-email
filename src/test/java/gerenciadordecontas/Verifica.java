package gerenciadordecontas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;

/** Comparações que o JUnit não tem pronto. */
public final class Verifica {

    private Verifica() {
    }

    /** Compara valores em reais ignorando a escala: 200 e 200.00 são iguais. */
    public static void valor(String esperado, BigDecimal real) {
        assertNotNull(real, "valor nulo");
        assertEquals(0, new BigDecimal(esperado).compareTo(real), "esperado " + esperado + " mas veio " + real);
    }
}
