package com.marciogames.betacraft.android;

import android.system.Os;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Installs the published Android ARM64 OpenJDK 17 runtime into app-private storage.
 * This installs only the JVM files; it does not supply Minecraft's LWJGL renderer.
 */
final class AndroidRuntimeInstaller {
    static final String SOURCE_URL =
            "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/"
                    + "jre17-ec28559/jre17-arm64-20210825-release.tar.xz";
    private static final long MAX_ARCHIVE_BYTES = 100L * 1024L * 1024L;
    private static final long MAX_UNPACKED_BYTES = 700L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 50000;

    interface Progress {
        void update(String stage, long current, long total);
    }

    private AndroidRuntimeInstaller() { }

    static void install(File runtimeDirectory, Progress progress) throws Exception {
        if (!runtimeDirectory.exists() && !runtimeDirectory.mkdirs()) {
            throw new IllegalStateException("Não foi possível criar a pasta runtime.");
        }
        File archive = new File(runtimeDirectory, "jre17-arm64.tar.xz.part");
        File staged = new File(runtimeDirectory, "staging-jre17");
        File active = new File(runtimeDirectory, "active");
        File backup = new File(runtimeDirectory, "active-old");

        deleteTree(staged);
        deleteTree(archive);
        download(archive, progress);
        extract(archive, staged, progress);
        if (!hasCompatibleRuntime(staged)) {
            deleteTree(staged);
            deleteTree(archive);
            throw new IllegalStateException("O arquivo baixado não contém um OpenJDK 17 ARM64 válido.");
        }

        deleteTree(backup);
        if (active.exists() && !active.renameTo(backup)) {
            deleteTree(staged);
            throw new IllegalStateException("Não foi possível guardar o runtime anterior.");
        }
        if (!staged.renameTo(active)) {
            if (backup.exists()) backup.renameTo(active);
            deleteTree(staged);
            throw new IllegalStateException("Não foi possível ativar o runtime instalado.");
        }
        deleteTree(backup);
        deleteTree(archive);
        progress.update("Runtime Java 17 instalado e validado", 1, 1);
    }

    private static void download(File destination, Progress progress) throws Exception {
        URL current = new URL(SOURCE_URL);
        HttpURLConnection connection = null;
        try {
            for (int redirects = 0; redirects <= 5; redirects++) {
                if (!"https".equalsIgnoreCase(current.getProtocol())) {
                    throw new IllegalStateException("O download do runtime exige HTTPS.");
                }
                String host = current.getHost().toLowerCase(java.util.Locale.ROOT);
                if (!(host.equals("github.com") || host.equals("release-assets.githubusercontent.com")
                        || host.equals("objects.githubusercontent.com"))) {
                    throw new IllegalStateException("Host de download não autorizado: " + host);
                }
                connection = (HttpURLConnection) current.openConnection();
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", "BetaCraft-Android-Port/0.3");
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    connection.disconnect();
                    connection = null;
                    if (location == null || redirects == 5) {
                        throw new IllegalStateException("Redirecionamento do runtime inválido.");
                    }
                    current = new URL(current, location);
                    continue;
                }
                if (code < 200 || code >= 300) {
                    throw new IllegalStateException("Download do runtime respondeu HTTP " + code);
                }
                long total = connection.getContentLengthLong();
                if (total > MAX_ARCHIVE_BYTES) {
                    throw new IllegalStateException("Arquivo do runtime excede o limite de tamanho.");
                }
                long received = 0;
                byte[] buffer = new byte[32768];
                try (InputStream input = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream output = new FileOutputStream(destination)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        received += count;
                        if (received > MAX_ARCHIVE_BYTES) {
                            throw new IllegalStateException("Download excedeu 100 MiB.");
                        }
                        output.write(buffer, 0, count);
                        progress.update("Baixando OpenJDK 17 ARM64", received, total);
                    }
                    output.getFD().sync();
                }
                if (received < 1024 * 1024) {
                    throw new IllegalStateException("Arquivo do runtime incompleto.");
                }
                return;
            }
            throw new IllegalStateException("Muitos redirecionamentos no download do runtime.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void extract(File archive, File destination, Progress progress) throws Exception {
        if (!destination.mkdirs() && !destination.isDirectory()) {
            throw new IllegalStateException("Não foi possível criar a pasta temporária.");
        }
        long expanded = 0;
        int entries = 0;
        byte[] buffer = new byte[32768];
        try (InputStream file = new BufferedInputStream(new FileInputStream(archive));
             XZCompressorInputStream xz = new XZCompressorInputStream(file);
             TarArchiveInputStream tar = new TarArchiveInputStream(xz)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new IllegalStateException("O arquivo contém entradas demais.");
                }
                String name = entry.getName();
                Path root = destination.getCanonicalFile().toPath();
                Path target = root.resolve(name).normalize();
                if (name.startsWith("/") || !target.startsWith(root)) {
                    throw new IllegalStateException("Caminho inseguro no arquivo do runtime.");
                }
                File output = target.toFile();
                if (entry.isDirectory()) {
                    if (!output.mkdirs() && !output.isDirectory()) {
                        throw new IllegalStateException("Falha ao criar diretório do runtime.");
                    }
                    continue;
                }
                File parent = output.getParentFile();
                if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
                    throw new IllegalStateException("Falha ao criar pasta do runtime.");
                }
                if (entry.isSymbolicLink()) {
                    Path linkTarget = Paths.get(entry.getLinkName());
                    if (linkTarget.isAbsolute() || !target.getParent().resolve(linkTarget).normalize().startsWith(root)) {
                        throw new IllegalStateException("Link simbólico inseguro no runtime.");
                    }
                    if (output.exists() || android.system.Os.readlink(output.getAbsolutePath()) != null) {
                        output.delete();
                    }
                    Os.symlink(entry.getLinkName(), output.getAbsolutePath());
                    continue;
                }
                if (entry.isLink()) {
                    Path hardTarget = root.resolve(entry.getLinkName()).normalize();
                    if (!hardTarget.startsWith(root) || !hardTarget.toFile().isFile()) {
                        throw new IllegalStateException("Link rígido inválido no runtime.");
                    }
                    copy(hardTarget.toFile(), output);
                    continue;
                }
                if (!entry.isFile()) continue;
                long declared = entry.getSize();
                if (declared < 0 || expanded + declared > MAX_UNPACKED_BYTES) {
                    throw new IllegalStateException("O runtime excede o limite de extração.");
                }
                try (FileOutputStream out = new FileOutputStream(output)) {
                    int count;
                    long written = 0;
                    while ((count = tar.read(buffer)) != -1) {
                        out.write(buffer, 0, count);
                        written += count;
                        expanded += count;
                        if (expanded > MAX_UNPACKED_BYTES || written > declared) {
                            throw new IllegalStateException("Entrada do runtime excedeu o tamanho permitido.");
                        }
                    }
                    if (written != declared) throw new IllegalStateException("Entrada truncada no runtime.");
                }
                if (entry.getMode() != 0) {
                    output.setExecutable((entry.getMode() & 0111) != 0, false);
                }
                if ((entries & 127) == 0) progress.update("Extraindo OpenJDK 17", entries, MAX_ENTRIES);
            }
        }
        progress.update("Validando OpenJDK 17", expanded, MAX_UNPACKED_BYTES);
    }

    private static boolean hasCompatibleRuntime(File root) {
        return findFile(root, "libjvm.so") && hasJava17Release(root);
    }

    private static boolean hasJava17Release(File root) {
        File release = findFileObject(root, "release");
        if (release == null) return false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(release), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("JAVA_VERSION=")) {
                    String value = line.substring("JAVA_VERSION=".length()).replace("\"", "").trim();
                    return value.startsWith("17.");
                }
            }
        } catch (Exception ignored) { }
        return false;
    }

    private static boolean findFile(File root, String name) {
        return findFileObject(root, name) != null;
    }

    private static File findFileObject(File directory, String name) {
        if (directory == null || !directory.isDirectory()) return null;
        File[] children = directory.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child.isFile() && name.equals(child.getName())) return child;
            if (child.isDirectory()) {
                File found = findFileObject(child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void copy(File source, File destination) throws Exception {
        try (InputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.getFD().sync();
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        file.delete();
    }
}
