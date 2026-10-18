package com.marciogames.betacraft.android;

import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import static org.junit.Assert.*;

public class AndroidGameLaunchPlanTest {
    @Test public void assemblesClasspathAndOfflineArgumentsForMainBasedVersion() throws Exception {
        File temp = Files.createTempDirectory("betacraft-launch-plan").toFile();
        try {
            File data = new File(temp, "data");
            File versions = new File(data, "versions");
            assertTrue(versions.mkdirs());
            File jar = new File(versions, "1.6.4.jar");
            createJar(jar, "net.minecraft.client.main.Main",
                    "net/minecraft/client/main/Main.class");
            File dependency = new File(data, "libraries/example/dependency.jar");
            dependency.getParentFile().mkdirs();
            createJar(dependency, null, "example/Dependency.class");
            File lwjglx = new File(data, "launcher/components/lwjgl3/lwjgl-lwjglx.jar");
            lwjglx.getParentFile().mkdirs();
            createJar(lwjglx, null, "org/lwjgl/opengl/Display.class");

            File runtime = new File(data, "runtime/java8");
            runtime.mkdirs();
            write(new File(runtime, "release"), "JAVA_VERSION=\"1.8.0\"\n");
            write(new File(runtime, "libjli.so"), "test");
            write(new File(runtime, "libjvm.so"), "test");
            File nativeDir = new File(temp, "native");
            nativeDir.mkdirs();

            AndroidGameLaunchPlan plan = AndroidGameLaunchPlan.prepare(
                    data, nativeDir, "1.6.4", "Marcio_1", "");
            assertEquals("net.minecraft.client.main.Main", plan.mainClass);
            assertEquals(new File(data, "game/1.6.4").getCanonicalFile(),
                    plan.gameDirectory.getCanonicalFile());
            assertTrue(plan.classPath.contains(jar.getAbsolutePath()));
            assertTrue(plan.classPath.contains(dependency.getAbsolutePath()));
            assertTrue("Compatibility jars must be visible to the launched JVM",
                    plan.classPath.contains(lwjglx.getAbsolutePath()));
            assertTrue(Arrays.asList(plan.toJliArguments()).contains("-cp"));
            assertTrue(Arrays.asList(plan.toJliArguments()).contains("--username"));
            assertTrue(Arrays.asList(plan.toJliArguments()).contains("Marcio_1"));
            assertTrue(Arrays.asList(plan.toJliArguments()).contains("--gameDir"));
            assertEquals(runtime.getCanonicalFile(), plan.javaHome.getCanonicalFile());
        } finally {
            deleteTree(temp);
        }
    }

    @Test public void rejectsAppletOnlyVersionInsteadOfPassingWrongArguments() throws Exception {
        File temp = Files.createTempDirectory("betacraft-applet-launch").toFile();
        try {
            File data = new File(temp, "data");
            File versions = new File(data, "versions");
            assertTrue(versions.mkdirs());
            createJar(new File(versions, "1.5.2.jar"), null,
                    "net/minecraft/client/MinecraftApplet.class");
            try {
                AndroidGameLaunchPlan.prepare(data, new File(temp, "native"),
                        "1.5.2", "Player", "");
                fail("Applet-only versions must not be launched as a modern Main entry point.");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("applet"));
            }
        } finally {
            deleteTree(temp);
        }
    }

    @Test public void rejectsUnsafeVersionAndUsername() throws Exception {
        File temp = Files.createTempDirectory("betacraft-invalid-launch").toFile();
        try {
            try {
                AndroidGameLaunchPlan.prepare(temp, temp, "../outside", "Player", "");
                fail("Unsafe version labels must be rejected.");
            } catch (IllegalArgumentException expected) { }
            try {
                AndroidGameLaunchPlan.prepare(temp, temp, "1.6.4", "player name", "");
                fail("Invalid offline usernames must be rejected.");
            } catch (IllegalArgumentException expected) { }
        } finally {
            deleteTree(temp);
        }
    }

    private static void createJar(File file, String mainClass, String entryName) throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        if (mainClass != null) manifest.getMainAttributes().putValue("Main-Class", mainClass);
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(file), manifest)) {
            out.putNextEntry(new JarEntry(entryName));
            out.write(new byte[]{0});
            out.closeEntry();
        }
    }

    private static void write(File file, String value) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
