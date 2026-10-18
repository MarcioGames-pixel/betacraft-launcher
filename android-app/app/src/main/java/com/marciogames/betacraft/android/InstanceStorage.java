package com.marciogames.betacraft.android;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * On-disk storage for named Minecraft instances.
 *
 * Each instance has an independent game directory (saves, mods, config,
 * resource packs and logs). Downloaded version JARs and shared libraries stay
 * in the launcher-wide cache until the actual launch pipeline selects them.
 */
final class InstanceStorage {
    static final class Instance {
        final String id;
        final String name;
        final String version;
        final long createdAt;
        final File directory;
        final File gameDirectory;

        Instance(String id, String name, String version, long createdAt, File directory) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.createdAt = createdAt;
            this.directory = directory;
            this.gameDirectory = new File(directory, "game");
        }
    }

    private static final String METADATA = "instance.properties";
    private InstanceStorage() { }

    static File root(File dataRoot) {
        return new File(new File(dataRoot, "launcher"), "instances");
    }

    static List<Instance> list(File dataRoot) throws Exception {
        File root = root(dataRoot);
        if (!root.isDirectory() && !root.mkdirs()) {
            throw new IllegalStateException("Could not create the instances directory.");
        }
        File[] children = root.listFiles();
        List<Instance> result = new ArrayList<>();
        if (children == null) return result;
        for (File child : children) {
            if (!child.isDirectory() || !safeId(child.getName())) continue;
            File metadata = new File(child, METADATA);
            if (!metadata.isFile()) continue;
            Properties p = read(metadata);
            String name = p.getProperty("name", "").trim();
            String version = p.getProperty("version", "").trim();
            long createdAt;
            try { createdAt = Long.parseLong(p.getProperty("createdAt", "0")); }
            catch (NumberFormatException e) { createdAt = 0L; }
            if (name.isEmpty()) continue;
            result.add(new Instance(child.getName(), name, version, createdAt, child));
        }
        Collections.sort(result, Comparator.comparing(i -> i.name.toLowerCase(Locale.ROOT)));
        return result;
    }

    static Instance create(File dataRoot, String rawName, String version) throws Exception {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty() || name.length() > 48 || name.contains("/") || name.contains("\\")
                || name.contains("..") || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Use 1–48 characters; path separators and '..' are not allowed.");
        }
        for (Instance instance : list(dataRoot)) {
            if (instance.name.equalsIgnoreCase(name)) {
                throw new IllegalArgumentException("An instance with that name already exists.");
            }
        }
        String id = slug(name);
        File root = root(dataRoot);
        File destination = new File(root, id);
        int suffix = 2;
        while (destination.exists()) {
            destination = new File(root, id + "-" + suffix++);
        }
        if (!destination.mkdirs()) {
            throw new IllegalStateException("Could not create the instance directory.");
        }
        boolean committed = false;
        try {
            String selectedVersion = version == null ? "" : version.trim();
            String[] directories = {
                    "game", "game/mods", "game/config", "game/saves",
                    "game/resourcepacks", "game/screenshots", "game/logs",
                    "logs", "natives", "libraries"
            };
            for (String relative : directories) {
                File dir = new File(destination, relative);
                if (!dir.mkdirs() && !dir.isDirectory()) {
                    throw new IllegalStateException("Could not create " + relative);
                }
            }
            Properties metadata = new Properties();
            metadata.setProperty("id", destination.getName());
            metadata.setProperty("name", name);
            metadata.setProperty("version", selectedVersion);
            metadata.setProperty("createdAt", Long.toString(System.currentTimeMillis()));
            metadata.setProperty("formatVersion", "1");
            writeAtomically(new File(destination, METADATA), metadata);
            committed = true;
            return new Instance(destination.getName(), name, selectedVersion,
                    System.currentTimeMillis(), destination);
        } finally {
            if (!committed) deleteTree(destination);
        }
    }

    static void setVersion(Instance instance, String version) throws Exception {
        Properties metadata = read(new File(instance.directory, METADATA));
        metadata.setProperty("version", version == null ? "" : version.trim());
        writeAtomically(new File(instance.directory, METADATA), metadata);
    }

    static void delete(File dataRoot, Instance instance) throws Exception {
        File root = root(dataRoot).getCanonicalFile();
        File target = instance.directory.getCanonicalFile();
        if (!target.getParentFile().equals(root) || !safeId(target.getName())) {
            throw new IllegalArgumentException("Refusing to delete a directory outside the instances folder.");
        }
        if (!deleteTree(target)) {
            throw new IllegalStateException("Could not completely delete the instance files.");
        }
    }

    static Instance findById(File dataRoot, String id) throws Exception {
        if (id == null || !safeId(id)) return null;
        for (Instance instance : list(dataRoot)) {
            if (instance.id.equals(id)) return instance;
        }
        return null;
    }

    private static Properties read(File file) throws Exception {
        Properties p = new Properties();
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            p.load(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
        }
        return p;
    }

    private static void writeAtomically(File destination, Properties properties) throws Exception {
        File temporary = new File(destination.getParentFile(), destination.getName() + ".tmp");
        try (OutputStream output = new FileOutputStream(temporary)) {
            java.io.Writer writer = new java.io.OutputStreamWriter(output, StandardCharsets.UTF_8);
            properties.store(writer, "BetaCraft Android instance metadata");
            writer.flush();
            output.flush();
            ((FileOutputStream) output).getFD().sync();
        }
        if (destination.exists() && !destination.delete()) {
            throw new IllegalStateException("Could not replace instance metadata.");
        }
        if (!temporary.renameTo(destination)) {
            throw new IllegalStateException("Could not commit instance metadata.");
        }
    }

    private static String slug(String value) {
        String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^[.-]+|[.-]+$", "");
        if (slug.isEmpty()) slug = "instance";
        return slug.length() > 40 ? slug.substring(0, 40) : slug;
    }

    private static boolean safeId(String value) {
        return value != null && value.matches("[a-zA-Z0-9._-]{1,64}")
                && !value.equals(".") && !value.equals("..");
    }

    private static boolean deleteTree(File file) {
        if (file == null || !file.exists()) return true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) if (!deleteTree(child)) return false;
            }
        }
        return file.delete();
    }
}
