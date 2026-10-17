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
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Installs published Android ARM64 OpenJDK 8 or 17 runtimes into app-private storage.
 * This installs only the JVM files; it does not supply Minecraft's LWJGL renderer.
 */
final class AndroidRuntimeInstaller {
    private static String sourceUrl(int major) {
        String base = "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/";
        if (major == 8) {
            return base + "jre8-40df388/jre8-arm64-20220811-release.tar.xz";
        }
        if (major == 17) {
            return base + "jre17-ca01427/jre17-arm64-20220817-release.tar.xz";
        }
        throw new IllegalArgumentException("Only Java 8 and Java 17 are supported.");
    }
    private static final long MAX_ARCHIVE_BYTES = 100L * 1024L * 1024L;
    private static final long MAX_UNPACKED_BYTES = 700L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 50000;

    interface Progress {
        void update(String stage, long current, long total);
    }

    private AndroidRuntimeInstaller() { }

    static void install(File runtimeDirectory, int major, Progress progress) throws Exception {
        if (major != 8 && major != 17) {
            throw new IllegalArgumentException("Choose Java 8 or Java 17.");
        }
        if (!runtimeDirectory.exists() && !runtimeDirectory.mkdirs()) {
            throw new IllegalStateException("Could not create the runtime directory.");
        }
        String label = "jre" + major;
        File archive = new File(runtimeDirectory, label + "-arm64.tar.xz.part");
        File staged = new File(runtimeDirectory, "staging-" + label);
        File active = new File(runtimeDirectory, "java" + major);
        File backup = new File(runtimeDirectory, "java" + major + "-old");

        deleteTree(staged);
        deleteTree(archive);
        download(archive, major, progress);
        extract(archive, staged, major, progress);
        if (!hasCompatibleRuntime(staged, major)) {
            deleteTree(staged);
            deleteTree(archive);
            throw new IllegalStateException("The downloaded Java " + major + " package is incomplete or is not a 64-bit ARM (AArch64) Android runtime. Expected libjvm.so and libjli.so.");
        }

        deleteTree(backup);
        if (active.exists() && !active.renameTo(backup)) {
            deleteTree(staged);
            throw new IllegalStateException("Could not back up the previous runtime.");
        }
        if (!staged.renameTo(active)) {
            if (backup.exists()) backup.renameTo(active);
            deleteTree(staged);
            throw new IllegalStateException("Could not activate the installed runtime.");
        }
        deleteTree(backup);
        deleteTree(archive);
        progress.update("Java " + major + " runtime installed and validated", 1, 1);
    }

    private static void download(File destination, int major, Progress progress) throws Exception {
        URL current = new URL(sourceUrl(major));
        HttpURLConnection connection = null;
        try {
            for (int redirects = 0; redirects <= 5; redirects++) {
                if (!"https".equalsIgnoreCase(current.getProtocol())) {
                    throw new IllegalStateException("Runtime downloads require HTTPS.");
                }
                String host = current.getHost().toLowerCase(java.util.Locale.ROOT);
                if (!(host.equals("github.com") || host.equals("release-assets.githubusercontent.com")
                        || host.equals("objects.githubusercontent.com"))) {
                    throw new IllegalStateException("Untrusted download host: " + host);
                }
                connection = (HttpURLConnection) current.openConnection();
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", "BetaCraft-Android-Port/0.4");
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    connection.disconnect();
                    connection = null;
                    if (location == null || redirects == 5) {
                        throw new IllegalStateException("Invalid runtime download redirect.");
                    }
                    current = new URL(current, location);
                    continue;
                }
                if (code < 200 || code >= 300) {
                    throw new IllegalStateException("Runtime download returned HTTP " + code);
                }
                long total = connection.getContentLength();
                if (total > MAX_ARCHIVE_BYTES) {
                    throw new IllegalStateException("Runtime archive exceeds the size limit.");
                }
                long received = 0;
                byte[] buffer = new byte[32768];
                try (InputStream input = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream output = new FileOutputStream(destination)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        received += count;
                        if (received > MAX_ARCHIVE_BYTES) {
                            throw new IllegalStateException("Download exceeded 100 MiB.");
                        }
                        output.write(buffer, 0, count);
                        progress.update("Downloading OpenJDK " + major + " ARM64", received, total);
                    }
                    output.getFD().sync();
                }
                if (received < 1024 * 1024) {
                    throw new IllegalStateException("Runtime archive is incomplete.");
                }
                return;
            }
            throw new IllegalStateException("Too many runtime download redirects.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void extract(File archive, File destination, int major, Progress progress) throws Exception {
        if (!destination.mkdirs() && !destination.isDirectory()) {
            throw new IllegalStateException("Could not create the temporary extraction directory.");
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
                    throw new IllegalStateException("The archive contains too many entries.");
                }
                String name = entry.getName();
                File root = destination.getCanonicalFile();
                File output = new File(destination, name);
                String rootPath = root.getPath() + File.separator;
                String targetPath = output.getCanonicalPath();
                if (name.startsWith("/") || !(targetPath.equals(root.getPath())
                        || targetPath.startsWith(rootPath))) {
                    throw new IllegalStateException("Unsafe path in runtime archive.");
                }
                if (entry.isDirectory()) {
                    if (!output.mkdirs() && !output.isDirectory()) {
                        throw new IllegalStateException("Could not create a runtime directory.");
                    }
                    continue;
                }
                File parent = output.getParentFile();
                if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
                    throw new IllegalStateException("Could not create a runtime folder.");
                }
                if (entry.isSymbolicLink()) {
                    File linkTarget = new File(output.getParentFile(), entry.getLinkName());
                    String resolvedLink = linkTarget.getCanonicalPath();
                    if (new File(entry.getLinkName()).isAbsolute()
                            || !(resolvedLink.equals(root.getPath()) || resolvedLink.startsWith(rootPath))) {
                        throw new IllegalStateException("Unsafe symbolic link in runtime archive.");
                    }
                    if (output.exists()) {
                        output.delete();
                    }
                    Os.symlink(entry.getLinkName(), output.getAbsolutePath());
                    continue;
                }
                if (entry.isLink()) {
                    File hardTarget = new File(root, entry.getLinkName());
                    String hardTargetPath = hardTarget.getCanonicalPath();
                    if (!(hardTargetPath.equals(root.getPath()) || hardTargetPath.startsWith(rootPath))
                            || !hardTarget.isFile()) {
                        throw new IllegalStateException("Invalid hard link in runtime archive.");
                    }
                    copy(hardTarget, output);
                    continue;
                }
                if (!entry.isFile()) continue;
                long declared = entry.getSize();
                if (declared < 0 || expanded + declared > MAX_UNPACKED_BYTES) {
                    throw new IllegalStateException("Runtime exceeds the extraction size limit.");
                }
                try (FileOutputStream out = new FileOutputStream(output)) {
                    int count;
                    long written = 0;
                    while ((count = tar.read(buffer)) != -1) {
                        out.write(buffer, 0, count);
                        written += count;
                        expanded += count;
                        if (expanded > MAX_UNPACKED_BYTES || written > declared) {
                            throw new IllegalStateException("Runtime entry exceeded its declared size.");
                        }
                    }
                    if (written != declared) throw new IllegalStateException("Truncated runtime archive entry.");
                }
                if (entry.getMode() != 0) {
                    output.setExecutable((entry.getMode() & 0111) != 0, false);
                }
                if ((entries & 127) == 0) progress.update("Extracting OpenJDK " + major, entries, MAX_ENTRIES);
            }
        }
        progress.update("Validating OpenJDK " + major, expanded, MAX_UNPACKED_BYTES);
    }

    static boolean hasCompatibleRuntime(File root, int expectedMajor) {
        File jvm = findFileObject(root, "libjvm.so");
        File jli = findFileObject(root, "libjli.so");
        return jvm != null && jli != null && isArm64Elf(jvm) && isArm64Elf(jli)
                && hasJavaMajorRelease(root, expectedMajor);
    }

    static boolean isArm64Elf(File file) {
        if (file == null || !file.isFile()) return false;
        try (InputStream input = new FileInputStream(file)) {
            byte[] header = new byte[20];
            int offset = 0;
            while (offset < header.length) {
                int count = input.read(header, offset, header.length - offset);
                if (count < 0) return false;
                offset += count;
            }
            // ELF64, little-endian, e_machine = EM_AARCH64 (183).
            return (header[0] & 0xff) == 0x7f && header[1] == 'E'
                    && header[2] == 'L' && header[3] == 'F'
                    && (header[4] & 0xff) == 2 && (header[5] & 0xff) == 1
                    && (header[18] & 0xff) == 183 && (header[19] & 0xff) == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean hasJavaMajorRelease(File root, int expectedMajor) {
        File release = findFileObject(root, "release");
        if (release == null) return false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(release), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("JAVA_VERSION=")) {
                    String value = line.substring("JAVA_VERSION=".length()).replace("\"", "").trim();
                    java.util.regex.Matcher matcher = java.util.regex.Pattern
                            .compile("^(?:1\\.)?(\\d+)")
                            .matcher(value);
                    return matcher.find() && Integer.parseInt(matcher.group(1)) == expectedMajor;
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
