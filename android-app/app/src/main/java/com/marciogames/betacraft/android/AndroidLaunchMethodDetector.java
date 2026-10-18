package com.marciogames.betacraft.android;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;

/**
 * Mirrors BetaCraft v1's legacy class-presence launch-method detection without
 * loading game classes (which can execute static initializers).
 */
final class AndroidLaunchMethodDetector {
    private static final String[] BUILT_IN = {
            "rd", "mc", "classic12a", "classic", "classic15a", "classicmp", "4k", "indev", "1.6"
    };

    private AndroidLaunchMethodDetector() { }

    static boolean isBuiltIn(String method) {
        if (method == null) return false;
        for (String value : BUILT_IN) if (value.equalsIgnoreCase(method.trim())) return true;
        return false;
    }

    static String detect(File jar) throws IOException {
        if (jar == null || !jar.isFile()) return null;
        try (JarFile archive = new JarFile(jar)) {
            boolean rubyDung = has(archive, "com/mojang/rubydung/RubyDung.class");
            boolean oldRubyDung = has(archive, "com/mojang/minecraft/RubyDung.class");
            boolean applet = has(archive, "com/mojang/minecraft/MinecraftApplet.class");
            boolean server = has(archive, "com/mojang/minecraft/server/MinecraftServer.class");
            boolean user = has(archive, "com/mojang/minecraft/User.class");
            boolean networkPlayer = has(archive, "com/mojang/minecraft/net/NetworkPlayer.class");
            boolean classicE = has(archive, "com/mojang/minecraft/e.class");

            if (rubyDung) return "rd";
            if (oldRubyDung) return "mc";
            if (applet && server && !user) return "classic12a";
            if (applet && networkPlayer && !classicE) return "classic15a";
            if (applet && networkPlayer) return "classicmp";
            if (applet) return "classic";
            if (has(archive, "M.class")) return "4k";
            if (has(archive, "net/minecraft/client/MinecraftApplet.class")) return "indev";
            if (has(archive, "net/minecraft/client/main/Main.class")) return "1.6";
        }

        // BetaCraft's classic wrapper hosts legacy Applets. Keep specific
        // signatures above, but recognize repackaged clients too.
        // Classification does not mean Android rendering is implemented.
        return detectAppletClass(jar) == null ? null : "classic";
    }

    /**
     * Scans the entire JAR for an applet class by simple name, independent of package.
     * This is detection only: Android does not provide the desktop Applet
     * container, so callers must route it through an Android applet adapter.
     */
    static String detectAppletClass(File jar) throws IOException {
        if (jar == null || !jar.isFile()) return null;
        try (JarFile archive = new JarFile(jar)) {
            java.util.Enumeration<java.util.jar.JarEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String path = entry.getName();
                if (!path.endsWith(".class")) continue;
                String simpleName = path.substring(path.lastIndexOf('/') + 1);
                if ("MinecraftApplet.class".equals(simpleName)) {
                    return path.substring(0, path.length() - ".class".length())
                            .replace('/', '.');
                }
            }
        }
        return null;
    }

    private static boolean has(JarFile jar, String path) {
        return jar.getJarEntry(path) != null;
    }
}
