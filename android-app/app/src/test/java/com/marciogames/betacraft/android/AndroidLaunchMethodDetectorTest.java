package com.marciogames.betacraft.android;

import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import static org.junit.Assert.*;

public class AndroidLaunchMethodDetectorTest {
    @Test public void caveGameSignatureMatchesOfficialRubyDungRule() throws Exception {
        File temp = Files.createTempDirectory("bc-detect-rd").toFile();
        try {
            File jar = jar(temp, "rd.jar", "com/mojang/rubydung/RubyDung.class");
            assertEquals("rd", AndroidLaunchMethodDetector.detect(jar));
        } finally { deleteTree(temp); }
    }

    @Test public void oldMcRuleRequiresBothRubyDungAndAppletClasses() throws Exception {
        File temp = Files.createTempDirectory("bc-detect-mc").toFile();
        try {
            File incomplete = jar(temp, "ruby-only.jar",
                    "com/mojang/minecraft/RubyDung.class");
            assertNull("Upstream mc rule requires MinecraftApplet as well",
                    AndroidLaunchMethodDetector.detect(incomplete));

            File complete = jar(temp, "mc.jar",
                    "com/mojang/minecraft/RubyDung.class",
                    "com/mojang/minecraft/MinecraftApplet.class");
            assertEquals("mc", AndroidLaunchMethodDetector.detect(complete));
        } finally { deleteTree(temp); }
    }

    @Test public void classic12aRuleHonorsForbiddenUserClass() throws Exception {
        File temp = Files.createTempDirectory("bc-detect-classic12").toFile();
        try {
            File valid = jar(temp, "valid.jar",
                    "com/mojang/minecraft/MinecraftApplet.class",
                    "com/mojang/minecraft/server/MinecraftServer.class");
            assertEquals("classic12a", AndroidLaunchMethodDetector.detect(valid));

            File invalid = jar(temp, "invalid.jar",
                    "com/mojang/minecraft/MinecraftApplet.class",
                    "com/mojang/minecraft/server/MinecraftServer.class",
                    "com/mojang/minecraft/User.class");
            assertEquals("classic", AndroidLaunchMethodDetector.detect(invalid));
        } finally { deleteTree(temp); }
    }

    @Test public void distinguishesClassic15aAndClassicMultiplayerSignatures() throws Exception {
        File temp = Files.createTempDirectory("bc-detect-classic15").toFile();
        try {
            File classic15a = jar(temp, "classic15a.jar",
                    "com/mojang/minecraft/MinecraftApplet.class",
                    "com/mojang/minecraft/net/NetworkPlayer.class");
            assertEquals("classic15a", AndroidLaunchMethodDetector.detect(classic15a));

            File classicmp = jar(temp, "classicmp.jar",
                    "com/mojang/minecraft/MinecraftApplet.class",
                    "com/mojang/minecraft/net/NetworkPlayer.class",
                    "com/mojang/minecraft/e.class");
            assertEquals("classicmp", AndroidLaunchMethodDetector.detect(classicmp));
        } finally { deleteTree(temp); }
    }

    @Test public void recognizesOfficialIndevAndModernMainSignatures() throws Exception {
        File temp = Files.createTempDirectory("bc-detect-indev").toFile();
        try {
            assertEquals("indev", AndroidLaunchMethodDetector.detect(jar(temp, "indev.jar",
                    "net/minecraft/client/MinecraftApplet.class")));
            assertEquals("1.6", AndroidLaunchMethodDetector.detect(jar(temp, "main.jar",
                    "net/minecraft/client/main/Main.class")));
            assertEquals("4k", AndroidLaunchMethodDetector.detect(jar(temp, "4k.jar", "M.class")));
        } finally { deleteTree(temp); }
    }

    private static File jar(File directory, String name, String... entries) throws Exception {
        File file = new File(directory, name);
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(file))) {
            for (String entry : entries) {
                out.putNextEntry(new JarEntry(entry));
                out.write(new byte[]{0});
                out.closeEntry();
            }
        }
        return file;
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
