package com.marciogames.betacraft.android;

import android.system.Os;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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
            throw new IllegalArgumentException("Java 8 must use the patched Android AArch64 artifact resolver.");
        }
        if (major == 17) {
            return base + "jre17-ec28559/jre17-arm64-20210825-release.tar.xz";
        }
        throw new IllegalArgumentException("Only Java 8 and Java 17 are supported.");
    }
    private static final long MAX_ARCHIVE_BYTES = 100L * 1024L * 1024L;
    private static final long MAX_ARTIFACT_UNPACKED_BYTES = 150L * 1024L * 1024L;
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
        File java8Package = new File(runtimeDirectory, "jre8-pojav-package");
        File active = new File(runtimeDirectory, "java" + major);
        File backup = new File(runtimeDirectory, "java" + major + "-old");

        deleteTree(staged);
        deleteTree(archive);
        deleteTree(java8Package);
        download(archive, major, progress);
        if (major == 8) {
            // This artifact includes the AArch64 interpreter fix for the
            // "Field too big for insn" HotSpot startup abort. Do not fall back
            // to the older unpatched Java 8 runtime if the artifact is missing.
            extractJava8Artifact(archive, staged, progress);
            unpackJava8PackedJars(staged);
        } else {
            extract(archive, staged, major, progress);
        }
        if (!hasCompatibleRuntime(staged, major)) {
            deleteTree(staged);
            deleteTree(archive);
            deleteTree(java8Package);
            throw new IllegalStateException("The downloaded Java " + major + " package is incomplete or is not a 64-bit ARM (AArch64) Android runtime. Expected libjvm.so and libjli.so.");
        }

        if (major == 8) {
            progress.update("Testing patched Java 8 ARM64 before activation…", 0, 1);
            // Reject a JVM that crashes during startup instead of replacing a
            // working runtime with an unusable one.
            AndroidJvmBridge.test(staged);
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
        URL current = new URL(major == 8 ? resolveJava8ArtifactUrl() : sourceUrl(major));
        HttpURLConnection connection = null;
        try {
            for (int redirects = 0; redirects <= 5; redirects++) {
                if (!"https".equalsIgnoreCase(current.getProtocol())) {
                    throw new IllegalStateException("Runtime downloads require HTTPS.");
                }
                String host = current.getHost().toLowerCase(java.util.Locale.ROOT);
                if (!(host.equals("api.github.com") || host.equals("github.com")
                        || host.equals("nightly.link")
                        || host.equals("release-assets.githubusercontent.com")
                        || host.equals("objects.githubusercontent.com")
                        || host.endsWith(".blob.core.windows.net")
                        || host.equals("results-receiver.actions.githubusercontent.com"))) {
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

 
    /**
     * Resolve the patched Android AArch64 Java 8 build. The upstream interpreter
     * template can otherwise emit a range-limited direct branch and abort HotSpot
     * with "Field too big for insn" during startup, even with -Xint.
     */
    /**
     * Download the public GitHub Actions artifact through nightly.link.
     *
     * GitHub's Actions artifact API requires authentication for artifact downloads,
     * so calling archive_download_url directly from an Android app returns HTTP 401.
     * nightly.link exposes the named artifact from the selected public workflow/branch
     * without embedding a personal access token in the APK.
     */
    private static String resolveJava8ArtifactUrl() {
        return "https://nightly.link/ModinMobileSTS/angelauramc-openjdk-build"
                + "/workflows/build-aarch64-explicit-null-checks.yml"
                + "/buildjre8/jre8-android-aarch64-explicit-null-checks.zip";
    }

    private static JSONObject readJson(String address) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(address);
            if (!"https".equalsIgnoreCase(url.getProtocol())
                    || !"api.github.com".equalsIgnoreCase(url.getHost())) {
                throw new IllegalStateException("Untrusted runtime metadata URL.");
            }
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            connection.setRequestProperty("User-Agent", "BetaCraft-Android-Port/0.5");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("Runtime metadata request returned HTTP " + code);
            }
            try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = input.read(buffer)) != -1) {
                    if (output.size() + n > 5 * 1024 * 1024) {
                        throw new IllegalStateException("Runtime metadata response is too large.");
                    }
                    output.write(buffer, 0, n);
                }
                return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void extractJava8Artifact(File artifactZip, File destination, Progress progress)
            throws Exception {
        File packageDir = new File(artifactZip.getParentFile(), "jre8-pojav-package");
        deleteTree(packageDir);
        if (!packageDir.mkdirs() && !packageDir.isDirectory()) {
            throw new IllegalStateException("Could not create Java 8 package staging folder.");
        }
        long expanded = 0;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(artifactZip)))) {
            ZipEntry entry;
            byte[] buffer = new byte[32768];
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.startsWith("/") || name.contains("\\\\") || name.contains("../")) {
                    throw new IllegalStateException("Unsafe path in Java 8 artifact.");
                }
                String leaf = name.substring(name.lastIndexOf('/') + 1);
                boolean splitPojavPackage = "universal.tar.xz".equals(leaf)
                        || "bin-arm64.tar.xz".equals(leaf) || "version".equals(leaf);
                boolean patchedRawRuntime = leaf.startsWith("jre8-android-aarch64-")
                        && leaf.endsWith(".tar.xz");
                if (!splitPojavPackage && !patchedRawRuntime) continue;
                File output = new File(packageDir,
                        patchedRawRuntime ? "patched-aarch64-runtime.tar.xz" : leaf);
                try (FileOutputStream out = new FileOutputStream(output)) {
                    int n;
                    while ((n = zip.read(buffer)) != -1) {
                        expanded += n;
                        if (expanded > MAX_ARTIFACT_UNPACKED_BYTES) {
                            throw new IllegalStateException("Java 8 artifact exceeds the extraction size limit.");
                        }
                        out.write(buffer, 0, n);
                    }
                    out.getFD().sync();
                }
                zip.closeEntry();
            }
        }
        File patchedRuntime = new File(packageDir, "patched-aarch64-runtime.tar.xz");
        if (patchedRuntime.isFile() && patchedRuntime.length() >= 1024) {
            if (progress != null) progress.update("Extracting patched Java 8 ARM64 runtime…", 0, 0);
            extract(patchedRuntime, destination, 8, progress);
            deleteTree(packageDir);
            return;
        }

        File universal = new File(packageDir, "universal.tar.xz");
        File arm64 = new File(packageDir, "bin-arm64.tar.xz");
        if (!universal.isFile() || !arm64.isFile()
                || universal.length() < 1024 || arm64.length() < 1024) {
            throw new IllegalStateException("Patched Java 8 artifact is missing its AArch64 runtime archive.");
        }
        if (progress != null) progress.update("Extracting patched Java 8 ARM64 runtime…", 0, 0);
        extract(universal, destination, 8, progress);
        extract(arm64, destination, 8, progress);
        deleteTree(packageDir);
    }


    /**
     * Pojav's Android Java 8 package uses Pack200-compressed runtime JARs.
     * Unpack them before the JVM self-test, using the Android-native unpack200
     * executable shipped inside that same Java 8 runtime.
     */
    private static void unpackJava8PackedJars(File javaHome) throws Exception {
        java.util.List<File> packedFiles = new java.util.ArrayList<>();
        collectPackedFiles(javaHome, packedFiles, 0);
        if (packedFiles.isEmpty()) return;

        File unpacker = new File(new File(javaHome, "bin"), "unpack200");
        if (!unpacker.isFile()) {
            throw new IllegalStateException("Java 8 runtime contains .pack files but no Android unpack200 executable.");
        }
        unpacker.setExecutable(true, false);
        for (File packed : packedFiles) {
            String name = packed.getName();
            File unpacked = new File(packed.getParentFile(),
                    name.substring(0, name.length() - ".pack".length()));
            File log = new File(javaHome, "unpack200.log");
            ProcessBuilder builder = new ProcessBuilder(unpacker.getAbsolutePath(), "-r",
                    packed.getAbsolutePath(), unpacked.getAbsolutePath());
            builder.directory(javaHome);
            builder.redirectErrorStream(true);
            builder.redirectOutput(log);
            java.util.Map<String, String> environment = builder.environment();
            environment.put("JAVA_HOME", javaHome.getAbsolutePath());
            String lib = new File(javaHome, "lib/aarch64").getAbsolutePath()
                    + ":" + new File(javaHome, "lib/aarch64/server").getAbsolutePath()
                    + ":" + new File(javaHome, "lib").getAbsolutePath();
            environment.put("LD_LIBRARY_PATH", lib);
            Process process = builder.start();
            int exit = process.waitFor();
            if (log.exists()) log.delete();
            if (exit != 0 || !unpacked.isFile() || unpacked.length() == 0) {
                throw new IllegalStateException("Could not unpack Java 8 runtime file: " + name
                        + " (unpack200 exit code " + exit + ").");
            }
        }
    }

    private static void collectPackedFiles(File directory, java.util.List<File> result, int depth) {
        if (directory == null || depth > 16 || result.size() > 20000) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isFile() && child.getName().endsWith(".pack")) result.add(child);
            else if (child.isDirectory()) {
                collectPackedFiles(child, result, depth + 1);
            }
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

    /**
     * Legacy applet launch needs the Java 8 Applet/AWT classes, not only libjvm.so.
     * Some Android runtimes intentionally omit these classes, so detect that before
     * trying to start an applet and failing later with ClassNotFoundException.
     */
    static boolean hasLegacyAppletClasses(File root) {
        File rtJar = findFileObject(root, "rt.jar");
        if (rtJar == null) return false;
        String[] required = {
                "java/applet/Applet.class",
                "java/applet/AppletStub.class",
                "java/applet/AppletContext.class",
                "java/awt/Component.class"
        };
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(rtJar)) {
            for (String entry : required) {
                if (jar.getJarEntry(entry) == null) return false;
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
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
