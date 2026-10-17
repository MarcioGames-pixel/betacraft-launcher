package com.marciogames.betacraft.android;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.jar.JarFile;

/**
 * Downloads and validates the BetaCraft launch-method artifact declared by a version's .info.
 * This prepares the desktop launch-method payload; it does not make desktop code Android-safe.
 */
final class AndroidLaunchMethodInstaller {
    private static final long MAX_INFO_BYTES = 1024L * 1024L;
    private static final long MAX_JAR_BYTES = 100L * 1024L * 1024L;
    private AndroidLaunchMethodInstaller() { }

    interface Progress { void update(String message); }

    static String install(File dataRoot, String version, Progress progress) throws Exception {
        File infoFile = new File(new File(new File(dataRoot, "versions"), "jsons"), version + ".info");
        if (!infoFile.isFile() || infoFile.length() == 0) {
            throw new IllegalStateException("Download the version .info metadata first.");
        }
        String infoText = readUtf8(infoFile, MAX_INFO_BYTES);
        String methodName = property(infoText, "launch-method");
        String descriptorUrl = property(infoText, "launch-method-link");
        if (methodName == null || methodName.trim().isEmpty()) {
            throw new IllegalStateException("This version does not declare a launch-method.");
        }
        if (descriptorUrl == null || descriptorUrl.trim().isEmpty()) {
            throw new IllegalStateException("The metadata does not declare launch-method-link.");
        }
        methodName = methodName.trim();
        if (!methodName.matches("[A-Za-z0-9._-]{1,100}") || ".".equals(methodName)
                || "..".equals(methodName)) {
            throw new IllegalStateException("Invalid launch-method name.");
        }

        if (progress != null) progress.update("Reading launch-method descriptor " + methodName + "…");
        String descriptor = readUrl(normalizeUrl(descriptorUrl.trim()), MAX_INFO_BYTES);
        JSONObject json = new JSONObject(descriptor);
        String jarUrl = json.optString("url", "").trim();
        String expectedHash = json.optString("hash", "").trim();
        if (jarUrl.isEmpty() || expectedHash.isEmpty()) {
            throw new IllegalStateException("The launch-method descriptor is missing url or hash.");
        }
        if (!expectedHash.matches("(?i)[0-9a-f]{40}")) {
            throw new IllegalStateException("The launch-method SHA-1 hash is missing or invalid.");
        }

        File methodsDir = new File(new File(dataRoot, "launcher"), "launch-methods");
        if (!methodsDir.isDirectory() && !methodsDir.mkdirs()) {
            throw new IllegalStateException("Could not create the launch-methods directory.");
        }
        File destination = new File(methodsDir, methodName + ".jar");
        if (isValidJar(destination, expectedHash)) {
            return "Method " + methodName + " is already installed and validated.\n"
                    + destination.getAbsolutePath()
                    + "\nNote: this method may depend on desktop APIs and may not run on Android.";
        }

        File temporary = new File(methodsDir, methodName + ".jar.part");
        if (progress != null) progress.update("Downloading launch method " + methodName + "…");
        HttpURLConnection connection = null;
        try {
            connection = openConnection(normalizeUrl(jarUrl));
            long length = connection.getContentLength();
            if (length > MAX_JAR_BYTES) throw new IllegalStateException("Launch-method JAR exceeds 100 MiB.");
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            long countBytes = 0;
            byte[] buffer = new byte[32 * 1024];
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream output = new FileOutputStream(temporary)) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    countBytes += count;
                    if (countBytes > MAX_JAR_BYTES) {
                        throw new IllegalStateException("Download excedeu o limite de 100 MiB.");
                    }
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
            String actual = hex(digest.digest());
            if (!actual.equalsIgnoreCase(expectedHash)) {
                throw new IllegalStateException("Launch-method SHA-1 mismatch; download discarded.");
            }
            if (!isValidJar(temporary, expectedHash)) {
                throw new IllegalStateException("The downloaded file is not a valid JAR.");
            }
            if (destination.exists() && !destination.delete()) {
                throw new IllegalStateException("Could not replace the previous launch method.");
            }
            if (!temporary.renameTo(destination)) {
                copy(temporary, destination);
                if (!temporary.delete()) temporary.deleteOnExit();
            }
            return "Method " + methodName + " downloaded, validated and prepared.\n"
                    + destination.getAbsolutePath()
                    + "\nNote: this does not confirm Android compatibility or launch the game.";
        } catch (Exception e) {
            if (temporary.exists()) temporary.delete();
            throw e;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static boolean isValidJar(File file, String expectedHash) {
        if (file == null || !file.isFile() || file.length() == 0
                || file.length() > MAX_JAR_BYTES) return false;
        try (JarFile jar = new JarFile(file)) {
            if (jar.size() == 0) return false;
        } catch (Exception e) {
            return false;
        }
        if (expectedHash == null || expectedHash.isEmpty()) return true;
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[32 * 1024];
            int n;
            while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n);
            return expectedHash.equalsIgnoreCase(hex(digest.digest()));
        } catch (Exception e) {
            return false;
        }
    }

    private static String readUrl(String address, long maxBytes) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(address);
            long length = connection.getContentLengthLong();
            if (length > maxBytes) throw new IllegalStateException("Descritor excede o limite permitido.");
            try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
                byte[] buffer = new byte[8192];
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                int n;
                while ((n = input.read(buffer)) != -1) {
                    if (output.size() + n > maxBytes) {
                        throw new IllegalStateException("Resposta excedeu o limite permitido.");
                    }
                    output.write(buffer, 0, n);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static HttpURLConnection openConnection(String address) throws Exception {
        URL current = new URL(address);
        for (int redirects = 0; redirects <= 5; redirects++) {
            if (!"https".equalsIgnoreCase(current.getProtocol()) || current.getUserInfo() != null) {
                throw new IllegalStateException("HTTPS is required for launch methods.");
            }
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(25000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "BetaCraft-Android-Port/0.3");
            int code = connection.getResponseCode();
            if (code >= 200 && code < 300) return connection;
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (code < 300 || code >= 400 || location == null || redirects == 5) {
                throw new IllegalStateException("HTTP error while downloading launch method: " + code);
            }
            current = new URL(current, location);
        }
        throw new IllegalStateException("Redirecionamentos demais.");
    }

    private static String normalizeUrl(String value) throws Exception {
        URL url = new URL(value);
        if ("https".equalsIgnoreCase(url.getProtocol())) return url.toString();
        String host = url.getHost().toLowerCase(Locale.ROOT);
        if ("http".equalsIgnoreCase(url.getProtocol())
                && ("files.betacraft.uk".equals(host) || "betacraft.uk".equals(host)
                || host.endsWith(".betacraft.uk"))) {
            return new URL("https", url.getHost(), url.getPort(), url.getFile()).toString();
        }
        throw new IllegalStateException("Unsafe URL or untrusted host in the descriptor.");
    }

    private static String property(String content, String key) {
        for (String line : content.split("\\r?\\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && key.equals(line.substring(0, colon).trim())) {
                return line.substring(colon + 1).trim();
            }
        }
        return null;
    }

    private static String readUtf8(File file, long maxBytes) throws Exception {
        if (file.length() > maxBytes) throw new IllegalStateException("Metadados excedem o limite permitido.");
        try (InputStream input = new FileInputStream(file)) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = input.read(buffer)) != -1) {
                if (output.size() + n > maxBytes) throw new IllegalStateException("Metadados muito grandes.");
                output.write(buffer, 0, n);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void copy(File source, File destination) throws Exception {
        try (InputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
            output.getFD().sync();
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }
}
