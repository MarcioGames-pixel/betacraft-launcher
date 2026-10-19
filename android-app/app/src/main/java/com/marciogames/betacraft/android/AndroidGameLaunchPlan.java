package com.marciogames.betacraft.android;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Builds a deterministic JLI invocation for versions with a real Java entry
 * point. Main-based releases and the standalone Cave Game/RubyDung clients are
 * handled separately; applet-based Classic/Indev/Infdev versions are rejected
 * until their applet adapters are ported to Android.
 */
final class AndroidGameLaunchPlan {
    static final String LEGACY_APPLET_HOST_MAIN =
            "com.marciogames.betacraft.runtime.LegacyAppletMain";
    final String version;
    final String mainClass;
    final File gameDirectory;
    final File versionJar;
    final File javaHome;
    final List<String> jvmArguments;
    final List<String> gameArguments;
    final String classPath;

    private AndroidGameLaunchPlan(String version, String mainClass, File gameDirectory,
            File versionJar, File javaHome, List<String> jvmArguments,
            List<String> gameArguments, String classPath) {
        this.version = version;
        this.mainClass = mainClass;
        this.gameDirectory = gameDirectory;
        this.versionJar = versionJar;
        this.javaHome = javaHome;
        this.jvmArguments = Collections.unmodifiableList(jvmArguments);
        this.gameArguments = Collections.unmodifiableList(gameArguments);
        this.classPath = classPath;
    }

    static AndroidGameLaunchPlan prepare(File dataRoot, File appNativeLibraryDir,
            String version, String username, String instanceId) throws Exception {
        return prepare(dataRoot, appNativeLibraryDir, version, username, instanceId, 768);
    }

    static AndroidGameLaunchPlan prepare(File dataRoot, File appNativeLibraryDir,
            String version, String username, String instanceId, int maxMemoryMb) throws Exception {
        if (maxMemoryMb < 512 || maxMemoryMb > 4096) {
            throw new IllegalArgumentException("Maximum Java memory must be between 512 and 4096 MB.");
        }
        if (!safeVersion(version)) throw new IllegalArgumentException("Invalid version label.");
        String player = username == null ? "" : username.trim();
        if (!player.matches("[A-Za-z0-9_]{1,16}")) {
            throw new IllegalArgumentException("Offline player name must be 1–16 letters, digits or underscores.");
        }

        File versionJar = new File(new File(dataRoot, "versions"), version + ".jar");
        if (!versionJar.isFile() || versionJar.length() == 0) {
            throw new IllegalStateException("Download the selected version JAR first.");
        }

        String mainClass = findMainClass(versionJar);
        String appletClass = AndroidLaunchMethodDetector.detectAppletClass(versionJar);
        boolean detectedModernMain = "net.minecraft.client.main.Main".equals(mainClass);
        boolean detectedCaveGame = "com.mojang.rubydung.RubyDung".equals(mainClass)
                || "com.mojang.minecraft.RubyDung".equals(mainClass);
        if (appletClass != null && !detectedModernMain && !detectedCaveGame) {
            // Some legacy JARs contain Minecraft.class but no Java main method;
            // that class must not be mistaken for a standalone entry point.
            mainClass = LEGACY_APPLET_HOST_MAIN;
        } else if (mainClass == null) {
            String method = AndroidLaunchMethodDetector.detect(versionJar);
            String category = method == null ? "an unknown legacy format" : "the " + method + " launch method";
            throw new IllegalStateException("This version uses " + category
                    + " without a supported standalone Java entry point. Custom desktop launch methods still need Android-specific adapters; the launcher will not pass them incorrect arguments.");
        }
        boolean modernMain = "net.minecraft.client.main.Main".equals(mainClass);
        boolean caveGame = "com.mojang.rubydung.RubyDung".equals(mainClass)
                || "com.mojang.minecraft.RubyDung".equals(mainClass);
        boolean legacyApplet = LEGACY_APPLET_HOST_MAIN.equals(mainClass);
        if (!modernMain && !caveGame && !legacyApplet) {
            throw new IllegalStateException("Detected entry point " + mainClass
                    + ", which does not yet have an Android launch adapter.");
        }

        InstanceStorage.Instance instance = instanceId == null || instanceId.trim().isEmpty()
                ? null : InstanceStorage.findById(dataRoot, instanceId);
        File gameDir = instance == null
                ? new File(dataRoot, "game/" + version)
                : instance.gameDirectory;
        if (!gameDir.isDirectory() && !gameDir.mkdirs()) {
            throw new IllegalStateException("Could not create the selected instance game directory.");
        }

        int javaMajor = AndroidLaunchPreflight.recommendedJavaMajor(version);
        File javaHome = new File(new File(dataRoot, "runtime"), "java" + javaMajor);
        File release = findFile(javaHome, "release", 0);
        if (release == null) {
            throw new IllegalStateException("Install the recommended Android ARM64 Java runtime first.");
        }
        javaHome = release.getParentFile();
        if (findFile(javaHome, "libjli.so", 0) == null
                || findFile(javaHome, "libjvm.so", 0) == null) {
            throw new IllegalStateException("The installed Java runtime is missing libjli.so or libjvm.so.");
        }

        File libraries = new File(dataRoot, "libraries");
        List<File> jars = new ArrayList<>();
        collectJars(libraries, jars, 0);
        // The Android LWJGL 2 fork replaces org.lwjgl.opengl.Display and must
        // precede the desktop LWJGL jar supplied by the selected game version.
        File compatibility = new File(dataRoot, "launcher/components/lwjgl2-android");
        List<File> compatibilityJars = new ArrayList<>();
        collectJars(compatibility, compatibilityJars, 0);
        Collections.sort(compatibilityJars, Comparator.comparing(File::getAbsolutePath));
        Collections.sort(jars, Comparator.comparing(File::getAbsolutePath));
        StringBuilder cp = new StringBuilder();
        if (legacyApplet) {
            File appletHostJar = new File(dataRoot,
                    "launcher/components/legacy-applet-host/legacy-applet-host.jar");
            if (!appletHostJar.isFile() || appletHostJar.length() == 0) {
                throw new IllegalStateException("The bundled JVM legacy applet host is missing. Rebuild the APK with the legacy applet host task enabled.");
            }
            cp.append(appletHostJar.getAbsolutePath()).append(File.pathSeparator);
        }
        for (File jar : compatibilityJars) {
            cp.append(jar.getAbsolutePath()).append(File.pathSeparator);
        }
        cp.append(versionJar.getAbsolutePath());
        for (File jar : jars) {
            if (jar.getCanonicalFile().equals(versionJar.getCanonicalFile())) continue;
            cp.append(File.pathSeparator).append(jar.getAbsolutePath());
        }

        File natives = new File(new File(dataRoot, "natives"), version);
        if (!natives.isDirectory()) natives = new File(libraries, "natives");
        String nativePath = natives.getAbsolutePath() + File.pathSeparator
                + appNativeLibraryDir.getAbsolutePath();

        List<String> jvm = new ArrayList<>();
        // This Android ARM64 OpenJDK 8 build can abort during HotSpot startup
        // with "assembler_aarch64.hpp:237 / Field too big for insn" when
        // compressed oops are enabled. Disable that mode for Java 8 only.
        // Java 17 uses its own runtime defaults and is left unchanged.
        if (javaMajor == 8) jvm.add("-XX:-UseCompressedOops");
        // Keep the initial heap modest on Android and honour the saved maximum.
        int initialMemoryMb = Math.min(256, Math.max(128, maxMemoryMb / 4));
        jvm.add("-Xms" + initialMemoryMb + "M");
        jvm.add("-Xmx" + maxMemoryMb + "M");
        jvm.add("-Djava.awt.headless=false");
        jvm.add("-Duser.home=" + dataRoot.getAbsolutePath());
        jvm.add("-Djava.io.tmpdir=" + new File(gameDir, "tmp").getAbsolutePath());
        jvm.add("-Duser.language=en");
        jvm.add("-Dfile.encoding=UTF-8");
        jvm.add("-Djava.library.path=" + nativePath);
        jvm.add("-Dorg.lwjgl.librarypath=" + nativePath);
        jvm.add("-Dorg.lwjgl.opengl.libname=libGL.so");
        jvm.add("-Dbetacraft.android=true");
        jvm.add("-Dbetacraft.lwjgl.compat=" + new File(dataRoot,
                "launcher/components/lwjgl3").getAbsolutePath());
        jvm.add("-Dorg.lwjgl.opengl.Display.allowSoftwareOpenGL=true");
        jvm.add("-Dnet.java.games.input.librarypath=" + nativePath);
        jvm.add("-cp");
        jvm.add(cp.toString());

        String uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + player)
                .getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        List<String> args = new ArrayList<>();
        args.add(mainClass);
        if (modernMain) {
            args.add("--username"); args.add(player);
            args.add("--version"); args.add(version);
            args.add("--gameDir"); args.add(gameDir.getAbsolutePath());
            args.add("--assetsDir"); args.add(new File(dataRoot, "assets").getAbsolutePath());
            args.add("--assetIndex"); args.add("legacy");
            args.add("--uuid"); args.add(uuid);
            args.add("--accessToken"); args.add("0");
            args.add("--userType"); args.add("legacy");
            args.add("--versionType"); args.add("BetaCraft Android");
            args.add("--width"); args.add("854");
            args.add("--height"); args.add("480");
        } else if (caveGame) {
            // Cave Game/RubyDung is a standalone LWJGL client and does not
            // accept the modern Minecraft --username/--gameDir argument set.
            jvm.add("-Dbetacraft.gameDirectory=" + gameDir.getAbsolutePath());
        } else {
            AndroidAppletLaunchAdapter.Plan appletPlan = AndroidAppletLaunchAdapter.prepare(
                    versionJar, gameDir, version, player, 854, 480);
            args.add("--appletClass"); args.add(appletPlan.appletClass);
            args.add("--codeBase"); args.add(appletPlan.codeBase.toExternalForm());
            args.add("--documentBase"); args.add(appletPlan.documentBase.toExternalForm());
            args.add("--width"); args.add(Integer.toString(appletPlan.width));
            args.add("--height"); args.add(Integer.toString(appletPlan.height));
            for (java.util.Map.Entry<String, String> parameter : appletPlan.parameters.entrySet()) {
                args.add("--param." + parameter.getKey());
                args.add(parameter.getValue());
            }
        }

        File tmp = new File(gameDir, "tmp");
        if (!tmp.isDirectory() && !tmp.mkdirs()) {
            throw new IllegalStateException("Could not create the game's temporary directory.");
        }
        return new AndroidGameLaunchPlan(version, mainClass, gameDir, versionJar,
                javaHome, jvm, args, cp.toString());
    }

    String[] toJliArguments() {
        List<String> args = new ArrayList<>();
        args.add("java");
        args.addAll(jvmArguments);
        args.addAll(gameArguments);
        return args.toArray(new String[0]);
    }

    String describe() {
        return "Prepared Java launch\n"
                + "Version: " + version + "\n"
                + "Main class: " + mainClass + "\n"
                + "Runtime: " + javaHome.getAbsolutePath() + "\n"
                + "Game directory: " + gameDirectory.getAbsolutePath() + "\n"
                + "Classpath entries: " + (classPath.isEmpty() ? 0 : classPath.split(java.util.regex.Pattern.quote(File.pathSeparator), -1).length)
                + "\n\nClasspath checks for the bundled Android LWJGL 2 compatibility jar first. Real-device validation is still required: this backend must successfully create its GLFW/EGL window and render the selected game.";
    }

    private static String findMainClass(File jar) throws IOException {
        try (JarFile archive = new JarFile(jar)) {
            Manifest manifest = archive.getManifest();
            if (manifest != null) {
                String main = manifest.getMainAttributes().getValue("Main-Class");
                if (main != null && main.matches("[A-Za-z_$][A-Za-z0-9_$.]*")
                        && archive.getJarEntry(main.replace('.', '/') + ".class") != null) return main;
            }
            if (archive.getJarEntry("net/minecraft/client/main/Main.class") != null) {
                return "net.minecraft.client.main.Main";
            }
            if (archive.getJarEntry("com/mojang/rubydung/RubyDung.class") != null) {
                return "com.mojang.rubydung.RubyDung";
            }
            if (archive.getJarEntry("com/mojang/minecraft/RubyDung.class") != null) {
                return "com.mojang.minecraft.RubyDung";
            }
            return null;
        }
    }

    private static void collectJars(File directory, List<File> result, int depth) {
        if (directory == null || depth > 16 || !directory.isDirectory()) return;
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
                result.add(file);
            } else if (file.isDirectory() && !file.getName().equals("natives")) {
                collectJars(file, result, depth + 1);
            }
        }
    }

    private static File findFile(File root, String name, int depth) {
        if (root == null || depth > 12 || !root.isDirectory()) return null;
        File[] files = root.listFiles();
        if (files == null) return null;
        for (File file : files) {
            if (file.isFile() && name.equals(file.getName())) return file;
            if (file.isDirectory()) {
                File found = findFile(file, name, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean safeVersion(String version) {
        if (version == null || version.isEmpty() || version.length() > 100
                || ".".equals(version) || "..".equals(version)) return false;
        for (int i = 0; i < version.length(); i++) {
            char c = version.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-'
                    || c == ' ' || c == '(' || c == ')' || c == '+')) return false;
        }
        return true;
    }
}
