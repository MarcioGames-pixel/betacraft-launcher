package com.marciogames.betacraft.android;

import java.io.File;

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

    static int test(File javaHome) throws Exception {
        return launch(javaHome, new String[]{"java", "-version"});
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
