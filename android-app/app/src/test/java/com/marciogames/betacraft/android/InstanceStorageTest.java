package com.marciogames.betacraft.android;

import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import static org.junit.Assert.*;

public class InstanceStorageTest {
    @Test public void createsIndependentGameDirectoriesAndPersistsVersion() throws Exception {
        File temp = Files.createTempDirectory("betacraft-instance-test").toFile();
        try {
            File root = new File(temp, "betacraft");
            InstanceStorage.Instance alpha = InstanceStorage.create(root, "Alpha world", "b1.7.3");
            InstanceStorage.Instance beta = InstanceStorage.create(root, "Beta world", "1.5.2");

            assertNotEquals(alpha.id, beta.id);
            assertTrue(new File(alpha.gameDirectory, "saves").isDirectory());
            assertTrue(new File(alpha.gameDirectory, "mods").isDirectory());
            assertTrue(new File(beta.gameDirectory, "config").isDirectory());
            assertNotEquals(alpha.gameDirectory.getCanonicalPath(), beta.gameDirectory.getCanonicalPath());

            InstanceStorage.setVersion(alpha, "a1.2.6");
            List<InstanceStorage.Instance> all = InstanceStorage.list(root);
            assertEquals(2, all.size());
            InstanceStorage.Instance reloaded = InstanceStorage.findById(root, alpha.id);
            assertNotNull(reloaded);
            assertEquals("Alpha world", reloaded.name);
            assertEquals("a1.2.6", reloaded.version);

            InstanceStorage.delete(root, alpha);
            assertFalse(alpha.directory.exists());
            assertEquals(1, InstanceStorage.list(root).size());
            assertTrue(beta.directory.exists());
        } finally {
            deleteRecursively(temp);
        }
    }

    @Test public void rejectsPathTraversalAndDuplicateNames() throws Exception {
        File temp = Files.createTempDirectory("betacraft-instance-security-test").toFile();
        try {
            File root = new File(temp, "betacraft");
            InstanceStorage.create(root, "My Instance", "1.5.2");
            try {
                InstanceStorage.create(root, "../escape", "1.5.2");
                fail("Path traversal should be rejected");
            } catch (IllegalArgumentException expected) { }
            try {
                InstanceStorage.create(root, "my instance", "1.5.2");
                fail("Case-insensitive duplicate names should be rejected");
            } catch (IllegalArgumentException expected) { }
            assertFalse(new File(temp, "escape").exists());
        } finally {
            deleteRecursively(temp);
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }
}
