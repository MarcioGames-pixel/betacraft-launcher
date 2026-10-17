package com.marciogames.betacraft.android;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/**
 * Honest preflight checks for the future Android game runtime.
 *
 * This class deliberately does not start a JVM. A desktop JAR alone is not an
 * Android game runtime; a compatible JVM, native launch bridge and renderer
 * must be packaged and tested before launching can be enabled.
 */
final class AndroidLaunchPreflight {
    private AndroidLaunchPreflight() { }

    static String inspect(File dataRoot, String version, String[] supportedAbis,
                          File nativeLibraryDir) {
        List<String> lines = new ArrayList<>();
        boolean jarReady = checkJar(dataRoot, version, lines);
        boolean metadataReady = checkMetadata(dataRoot, version, lines);
        boolean abiReady = checkAbi(supportedAbis, lines);
        int requiredJava = requiredJavaMajor(version);
        lines.add("Runtime Java recomendado para " + version + ": Java " + requiredJava);
        File runtimeRoot = new File(dataRoot, "runtime/active");
        boolean jvmReady = containsNamedFile(runtimeRoot, "libjvm.so");
        boolean runtimeVersionReady = runtimeHasMajor(runtimeRoot, requiredJava);
        lines.add((jvmReady && runtimeVersionReady ? "OK" : "FALTA")
                + " — runtime Java Android " + requiredJava
                + (jvmReady && runtimeVersionReady ? "" : " (libjvm.so + release compatível)"));
        boolean launchBridgeReady = new File(nativeLibraryDir, "libbetacraft_runtime.so").isFile();
        lines.add((launchBridgeReady ? "OK" : "FALTA")
                + " — ponte nativa de execução (libbetacraft_runtime.so)");
        boolean rendererReady = new File(nativeLibraryDir, "libbetacraft_renderer.so").isFile();
        lines.add((rendererReady ? "OK" : "FALTA")
                + " — ponte de renderização/input (libbetacraft_renderer.so)");

        boolean ready = jarReady && metadataReady && abiReady && jvmReady
                && runtimeVersionReady && launchBridgeReady && rendererReady;
        lines.add("");
        lines.add(ready
                ? "Pré-verificação concluída. Ainda é necessário testar o lançamento real."
                : "NÃO PRONTO PARA EXECUTAR: o launcher ainda não contém todos os componentes do motor Android.");
        return join(lines);
    }

    private static boolean checkJar(File dataRoot, String version, List<String> lines) {
        File jar = new File(new File(dataRoot, "versions"), version + ".jar");
        if (!jar.isFile() || jar.length() == 0) {
            lines.add("FALTA — JAR da versão selecionada");
            return false;
        }
        try (JarFile archive = new JarFile(jar)) {
            if (archive.size() == 0) {
                lines.add("INVÁLIDO — JAR sem entradas");
                return false;
            }
            lines.add("OK — JAR legível (" + archive.size() + " entradas)");
            return true;
        } catch (Exception e) {
            lines.add("INVÁLIDO — JAR não pôde ser aberto: " + e.getClass().getSimpleName());
            return false;
        }
    }

    private static boolean checkMetadata(File dataRoot, String version, List<String> lines) {
        File info = new File(new File(new File(dataRoot, "versions"), "jsons"), version + ".info");
        if (!info.isFile() || info.length() == 0) {
            lines.add("FALTA — metadados .info da versão");
            return false;
        }
        String launchMethod = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(info), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int separator = line.indexOf(':');
                if (separator > 0 && "launch-method".equals(line.substring(0, separator).trim())) {
                    launchMethod = line.substring(separator + 1).trim();
                    break;
                }
            }
        } catch (Exception e) {
            lines.add("ERRO — leitura dos metadados: " + e.getClass().getSimpleName());
            return false;
        }
        if (launchMethod == null || launchMethod.isEmpty()) {
            lines.add("AVISO — metadados sem launch-method; o método ainda precisaria ser detectado");
            return true;
        }
        lines.add("OK — launch-method declarado: " + launchMethod);
        return true;
    }

    private static boolean checkAbi(String[] supportedAbis, List<String> lines) {
        if (supportedAbis == null || supportedAbis.length == 0) {
            lines.add("FALTA — arquitetura Android não identificada");
            return false;
        }
        StringBuilder value = new StringBuilder();
        boolean arm = false;
        for (String abi : supportedAbis) {
            if (value.length() > 0) value.append(", ");
            value.append(abi);
            if ("arm64-v8a".equals(abi) || "armeabi-v7a".equals(abi)) arm = true;
        }
        lines.add((arm ? "OK" : "AVISO") + " — ABI do dispositivo: " + value);
        return arm;
    }

    private static int requiredJavaMajor(String version) {
        // Legacy BetaCraft versions generally use Java 8; Minecraft 1.17+ requires Java 16+
        // and Java 17 is the practical baseline for those versions.
        String value = version == null ? "" : version.trim();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^(?:release[- ]?)?(\\d+)\\.(\\d+)(?:\\.|$)")
                .matcher(value);
        if (matcher.find()) {
            try {
                int major = Integer.parseInt(matcher.group(1));
                int minor = Integer.parseInt(matcher.group(2));
                if (major == 1 && minor >= 17) return 17;
                if (major >= 2) return 17;
            } catch (NumberFormatException ignored) { }
        }
        return 8;
    }

    private static boolean runtimeHasMajor(File runtimeRoot, int expectedMajor) {
        File[] releases = findNamedFiles(runtimeRoot, "release");
        for (File release : releases) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(release), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("JAVA_VERSION=")) continue;
                    String value = line.substring("JAVA_VERSION=".length())
                            .replace("\\\"", "").trim();
                    java.util.regex.Matcher matcher = java.util.regex.Pattern
                            .compile("^(?:1\\.)?(\\d+)")
                            .matcher(value);
                    if (matcher.find() && Integer.parseInt(matcher.group(1)) == expectedMajor) {
                        return true;
                    }
                }
            } catch (Exception ignored) { }
        }
        return false;
    }

    private static File[] findNamedFiles(File directory, String name) {
        List<File> matches = new ArrayList<>();
        collectNamedFiles(directory, name, matches, 0);
        return matches.toArray(new File[0]);
    }

    private static void collectNamedFiles(File directory, String name, List<File> matches, int depth) {
        if (directory == null || depth > 12 || !directory.isDirectory()) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isFile() && name.equals(child.getName())) matches.add(child);
            else if (child.isDirectory()) collectNamedFiles(child, name, matches, depth + 1);
        }
    }

    private static boolean containsNamedFile(File directory, String name) {
        if (!directory.isDirectory()) return false;
        File[] children = directory.listFiles();
        if (children == null) return false;
        for (File child : children) {
            if (child.isFile() && name.equals(child.getName())) return true;
            if (child.isDirectory() && containsNamedFile(child, name)) return true;
        }
        return false;
    }

    private static String join(List<String> lines) {
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            if (result.length() > 0) result.append('\n');
            result.append(line);
        }
        return result.toString();
    }
}
