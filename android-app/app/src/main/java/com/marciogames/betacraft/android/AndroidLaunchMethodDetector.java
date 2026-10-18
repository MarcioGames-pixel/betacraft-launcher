package com.marciogames.betacraft.android;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Android-safe port of BetaCraft v1's WrapperDetector.classRules.
 *
 * The desktop detector uses Class.forName() to test class presence. We instead
 * inspect JAR entries so detection cannot initialize legacy game classes.
 * The ordered signatures and required/forbidden classes mirror upstream.
 */
final class AndroidLaunchMethodDetector {
    private AndroidLaunchMethodDetector() { }

    private static final class Rule {
        final String method;
        final String[] signatures;
        Rule(String method, String... signatures) {
            this.method = method;
            this.signatures = signatures;
        }
    }

    // Keep the order from org.betacraft.WrapperDetector in the official v1 source.
    private static final Rule[] OFFICIAL_RULES = {
        new Rule("rd", "com.mojang.rubydung.RubyDung"),
        new Rule("mc", "com.mojang.minecraft.RubyDung",
                "com.mojang.minecraft.MinecraftApplet"),
        new Rule("classic12a", "com.mojang.minecraft.MinecraftApplet",
                "com.mojang.minecraft.server.MinecraftServer",
                "!com.mojang.minecraft.User"),
        new Rule("classic", "com.mojang.minecraft.MinecraftApplet",
                "!com.mojang.minecraft.net.NetworkPlayer"),
        new Rule("classic15a", "com.mojang.minecraft.MinecraftApplet",
                "com.mojang.minecraft.net.NetworkPlayer",
                "!com.mojang.minecraft.e"),
        new Rule("classicmp", "com.mojang.minecraft.MinecraftApplet",
                "com.mojang.minecraft.net.NetworkPlayer"),
        new Rule("4k", "M"),
        new Rule("indev", "net.minecraft.client.MinecraftApplet"),
        new Rule("1.6", "net.minecraft.client.main.Main")
    };

    private static final String[] BUILT_IN = {
        "rd", "mc", "classic12a", "classic", "classic15a",
        "classicmp", "4k", "indev", "1.6"
    };

    static boolean isBuiltIn(String method) {
        if (method == null) return false;
        for (String value : BUILT_IN) {
            if (value.equalsIgnoreCase(method.trim())) return true;
        }
        return false;
    }

    static String detect(File jar) throws IOException {
        if (jar == null || !jar.isFile()) return null;
        Set<String> classes = readClasses(jar);
        for (Rule rule : OFFICIAL_RULES) {
            boolean matches = true;
            for (String signature : rule.signatures) {
                boolean forbidden = signature.startsWith("!");
                String className = forbidden ? signature.substring(1) : signature;
                boolean present = classes.contains(className);
                if (forbidden ? present : !present) {
                    matches = false;
                    break;
                }
            }
            if (matches) return rule.method;
        }

        // The official launcher relies on metadata/custom launch methods for
        // other layouts. Preserve the Android port's extra recognition of
        // repackaged applets, but never treat that recognition as playable.
        return findAppletClass(classes) == null ? null : "classic";
    }

    /**
     * Finds an applet class by simple name for repackaged legacy JARs.
     * This is detection only; Android has no desktop AWT Applet container.
     */
    static String detectAppletClass(File jar) throws IOException {
        if (jar == null || !jar.isFile()) return null;
        return findAppletClass(readClasses(jar));
    }

    private static Set<String> readClasses(File file) throws IOException {
        Set<String> classes = new HashSet<>();
        try (JarFile archive = new JarFile(file)) {
            Enumeration<JarEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                classes.add(entry.getName().substring(0, entry.getName().length() - 6)
                        .replace('/', '.').replace('\\', '.'));
            }
        }
        return classes;
    }

    private static String findAppletClass(Set<String> classes) {
        for (String className : classes) {
            int dot = className.lastIndexOf('.');
            String simpleName = dot < 0 ? className : className.substring(dot + 1);
            if ("MinecraftApplet".equals(simpleName)) return className;
        }
        return null;
    }
}
