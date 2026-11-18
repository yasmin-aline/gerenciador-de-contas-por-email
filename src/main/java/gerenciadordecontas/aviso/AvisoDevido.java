package gerenciadordecontas.aviso;

import gerenciadordecontas.modelo.Bloco;
import java.time.LocalDate;

public record AvisoDevido(LocalDate ancora, Bloco bloco, int diasAntes, String chave) {
}
