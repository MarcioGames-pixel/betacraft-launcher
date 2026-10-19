package com.marciogames.betacraft.android;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import android.util.Log;

/**
 * Thin JNI adapter for OpenJDK's JLI_Launch entry point.
 *
 * This is a JVM bootstrap, not a Minecraft renderer. JLI implementations can
 * still fail on Android when their build or dependent native libraries are not
 * compatible with Android's linker.
 */
final class AndroidJvmBridge {
    static {
        System.loadLibrary("betacraft_runtime");
    }

    private AndroidJvmBridge() { }

    /**
     * Run the runtime self-test in a child process. Calling JLI_Launch for
     * "java -version" inside the Android app can invoke the JVM launcher's
     * process-exit path and terminate the entire launcher. A child process
     * keeps that exit isolated and lets us display the actual diagnostic.
     */
    static int test(File javaHome) throws Exception {
        File release = findNamedFile(javaHome, "release", 0);
        File actualHome = release == null ? javaHome : release.getParentFile();
        File java = new File(actualHome, "bin/java");
        if (!java.isFile()) {
            throw new IllegalStateException("The installed runtime has no bin/java executable; reinstall the Android ARM64 runtime.");
        }
        java.setExecutable(true, false);
        // This ARM64 OpenJDK 8 build can abort in assembler_aarch64.hpp while
        // initializing HotSpot with compressed oops enabled. Disable that mode
        // for the isolated self-test; the game launch plan applies the same
        // workaround to Java 8. Do not rename/replace the runtime binary.
        ProcessBuilder builder = new ProcessBuilder(java.getAbsolutePath(),
                "-XX:-UseCompressedOops", "-version");
        builder.directory(actualHome);
        builder.redirectErrorStream(true);
        Map<String, String> environment = builder.environment();
        environment.put("JAVA_HOME", actualHome.getAbsolutePath());
        // Android OpenJDK builds place libjli.so in different subdirectories
        // (commonly lib/aarch64/jli). The java executable is a separate process,
        // so its ELF loader must be given the directory containing libjli before
        // it starts; merely finding the file during install-time validation is
        // not enough. Put that exact directory first, then the known JVM paths.
        File jli = findNamedFile(actualHome, "libjli.so", 0);
        if (jli == null || !jli.isFile()) {
            throw new IllegalStateException("libjli.so is missing from the selected Java runtime.");
        }
        String jliDirectory = jli.getParentFile().getAbsolutePath();
        String libraries = jliDirectory
                + ":" + new File(actualHome, "lib/aarch64").getAbsolutePath()
                + ":" + new File(actualHome, "lib/aarch64/server").getAbsolutePath()
                + ":" + new File(actualHome, "lib/aarch64/jli").getAbsolutePath()
                + ":" + new File(actualHome, "lib/server").getAbsolutePath()
                + ":" + new File(actualHome, "lib").getAbsolutePath();
        String inherited = environment.get("LD_LIBRARY_PATH");
        if (inherited != null && !inherited.isEmpty()) libraries += ":" + inherited;
        environment.put("LD_LIBRARY_PATH", libraries);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() < 12000) output.append(line).append('\n');
                Log.i("BetaCraftRuntimeTest", line);
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Java runtime child process exited with code "
                    + exitCode + (output.length() == 0 ? "." : ":\n" + output));
        }
        if (output.length() == 0) {
            throw new IllegalStateException("Java runtime exited successfully but produced no version output.");
        }
        return 0;
    }

    /** Launches a JVM entry point on the calling worker thread. Never call from the UI thread. */
    static int launch(File javaHome, String[] arguments) throws Exception {
        File release = findNamedFile(javaHome, "release", 0);
        File actualHome = release == null ? javaHome : release.getParentFile();
        File jli = findNamedFile(actualHome, "libjli.so", 0);
        if (jli == null || !jli.isFile()) {
            throw new IllegalStateException("libjli.so was not found in the installed Java runtime.");
        }
        if (arguments == null || arguments.length == 0) {
            throw new IllegalArgumentException("At least one JVM argument is required.");
        }
        return runNative(actualHome.getCanonicalPath(), jli.getCanonicalPath(), arguments);
    }

    private static File findNamedFile(File root, String name, int depth) {
        if (root == null || depth > 12 || !root.isDirectory()) return null;
        File[] files = root.listFiles();
        if (files == null) return null;
        for (File file : files) {
            if (file.isFile() && name.equals(file.getName())) return file;
            if (file.isDirectory()) {
                File found = findNamedFile(file, name, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static native int runNative(String javaHome, String jliPath, String[] arguments);
}
