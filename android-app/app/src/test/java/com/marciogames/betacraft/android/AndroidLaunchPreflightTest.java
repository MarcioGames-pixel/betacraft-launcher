package com.marciogames.betacraft.android;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AndroidLaunchPreflightTest {
    @Test
    public void recommendsJava17ForModernReleaseLabels() {
        assertEquals(17, AndroidLaunchPreflight.recommendedJavaMajor("Release 1.17.1"));
        assertEquals(17, AndroidLaunchPreflight.recommendedJavaMajor("release-1.20.4"));
        assertEquals(17, AndroidLaunchPreflight.recommendedJavaMajor("Minecraft 1.21"));
    }

    @Test
    public void recommendsJava8ForLegacyVersions() {
        assertEquals(8, AndroidLaunchPreflight.recommendedJavaMajor("Release 1.16.5"));
        assertEquals(8, AndroidLaunchPreflight.recommendedJavaMajor("Beta 1.7.3"));
        assertEquals(8, AndroidLaunchPreflight.recommendedJavaMajor("b1.7.3"));
        assertEquals(8, AndroidLaunchPreflight.recommendedJavaMajor("Indev"));
    }

    @Test
    public void detectsBuiltInLegacyLaunchMethodsWithoutLoadingClasses() throws Exception {
        File jar = File.createTempFile("betacraft-version", ".jar");
        try {
            try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jar))) {
                output.putNextEntry(new JarEntry("net/minecraft/client/MinecraftApplet.class"));
                output.write(new byte[]{0});
                output.closeEntry();
            }
            assertEquals("indev", AndroidLaunchMethodDetector.detect(jar));
            assertTrue(AndroidLaunchMethodDetector.isBuiltIn("indev"));
            assertFalse(AndroidLaunchMethodDetector.isBuiltIn("CustomDesktopMethod"));
        } finally {
            jar.delete();
        }
    }

    @Test
    public void acceptsOnlyAarch64ElfRuntimeLibraries() throws Exception {
        File file = File.createTempFile("betacraft-libjvm", ".so");
        try {
            byte[] header = new byte[20];
            header[0] = 0x7f;
            header[1] = 'E';
            header[2] = 'L';
            header[3] = 'F';
            header[4] = 2;   // ELF64
            header[5] = 1;   // little-endian
            header[18] = (byte) 183; // EM_AARCH64
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(header);
            }
            assertTrue(AndroidRuntimeInstaller.isArm64Elf(file));

            header[18] = 62; // EM_X86_64
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(header);
            }
            assertFalse(AndroidRuntimeInstaller.isArm64Elf(file));
        } finally {
            file.delete();
        }
    }

    @Test
    public void defaultsSafelyForMissingVersion() {
        assertEquals(8, AndroidLaunchPreflight.recommendedJavaMajor(null));
        assertEquals(8, AndroidLaunchPreflight.recommendedJavaMajor(""));
    }
}
