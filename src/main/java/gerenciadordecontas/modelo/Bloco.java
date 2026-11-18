package gerenciadordecontas.modelo;

/** Os dois blocos de cada aba de mês: o que se paga no dia 15 e o que se paga no fim do mês. */
public enum Bloco {
    DIA_15("Dia 15", "dia 15"),
    FIM_DO_MES("Fim do mês", "fim do mês");

    private final String titulo;
    private final String nome;

    Bloco(String titulo, String nome) {
        this.titulo = titulo;
        this.nome = nome;
    }

    /** Para começo de frase: "Dia 15". */
    public String titulo() {
        return titulo;
    }

    /** Para meio de frase: "dia 15". */
    public String nome() {
        return nome;
    }
}
