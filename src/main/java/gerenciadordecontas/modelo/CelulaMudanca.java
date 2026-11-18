package gerenciadordecontas.modelo;

/**
 * Uma célula que mudou. Quando ehNumero, antes/depois guardam o número em formato simples (ex.: "130.50").
 */
public record CelulaMudanca(String aba, String celula, String antes, String depois, boolean ehNumero) {
}
