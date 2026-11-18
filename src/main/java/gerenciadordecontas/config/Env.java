package gerenciadordecontas.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuração vinda de variáveis de ambiente e, se existir, de um arquivo .env
 * (as variáveis de ambiente de verdade têm prioridade). Nenhuma chave fica no código.
 */
public final class Env {

    private static final Map<String, String> ARQUIVO = new HashMap<>();

    private Env() {
    }

    public static String obrigatoria(String nome, String dica) {
        String valor = opcional(nome);
        if (valor == null) {
            throw new IllegalStateException("Defina " + nome + " (" + dica + ").");
        }
        return valor;
    }

    /** Valor da variável, ou null se não existe ou está em branco. */
    public static String opcional(String nome) {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) {
            valor = ARQUIVO.get(nome);
        }
        return (valor == null || valor.isBlank()) ? null : valor.strip();
    }

    /** Procura .env na pasta atual e na pasta do programa; carrega o primeiro que achar. */
    public static void carregarDotEnv() {
        for (Path pasta : new Path[] {Paths.get("").toAbsolutePath(), pastaDoPrograma()}) {
            Path arquivo = pasta.resolve(".env");
            if (Files.isRegularFile(arquivo)) {
                carregarArquivo(arquivo);
                return;
            }
        }
    }

    static void carregarArquivo(Path arquivo) {
        try {
            for (String bruta : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
                interpretarLinha(bruta);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Não consegui ler " + arquivo + ": " + e.getMessage(), e);
        }
    }

    /** Aceita CHAVE=valor, ignora linhas vazias e comentários (#) e tira aspas em volta do valor. */
    static void interpretarLinha(String bruta) {
        String linha = bruta.strip();
        if (linha.startsWith("﻿")) {
            linha = linha.substring(1).strip();
        }
        if (linha.isEmpty() || linha.startsWith("#")) {
            return;
        }
        int igual = linha.indexOf('=');
        if (igual <= 0) {
            return;
        }
        String chave = linha.substring(0, igual).strip();
        String valor = linha.substring(igual + 1).strip();
        if (valor.length() >= 2
                && ((valor.startsWith("\"") && valor.endsWith("\""))
                        || (valor.startsWith("'") && valor.endsWith("'")))) {
            valor = valor.substring(1, valor.length() - 1);
        }
        ARQUIVO.putIfAbsent(chave, valor);
    }

    /** Caminho como está, ou relativo à pasta do programa. */
    public static Path resolverCaminho(String caminho) {
        Path path = Paths.get(caminho);
        if (path.isAbsolute() || Files.exists(path)) {
            return path;
        }
        return pastaDoPrograma().resolve(caminho);
    }

    /** Pasta onde está o gerenciadordecontas.jar (ou as classes, quando roda pela IDE). */
    public static Path pastaDoPrograma() {
        try {
            Path local = Paths.get(Env.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath();
            Path pasta = Files.isDirectory(local) ? local : local.getParent();
            return pasta != null ? pasta : Paths.get("").toAbsolutePath();
        } catch (Exception e) {
            return Paths.get("").toAbsolutePath();
        }
    }
}
