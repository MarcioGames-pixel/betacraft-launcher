package com.marciogames.betacraft.android;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Describes the applet entry point and standard parameters needed by a future
 * JVM-side applet host. This class deliberately does not pretend an Android
 * Activity is an AWT Container: an actual JVM-side host and renderer are still
 * required before an applet can be launched as a playable game.
 */
final class AndroidAppletLaunchAdapter {
    static final class Plan {
        final String appletClass;
        final File gameDirectory;
        final URL codeBase;
        final URL documentBase;
        final int width;
        final int height;
        final Map<String, String> parameters;

        private Plan(String appletClass, File gameDirectory, URL codeBase,
                URL documentBase, int width, int height, Map<String, String> parameters) {
            this.appletClass = appletClass;
            this.gameDirectory = gameDirectory;
            this.codeBase = codeBase;
            this.documentBase = documentBase;
            this.width = width;
            this.height = height;
            this.parameters = Collections.unmodifiableMap(parameters);
        }
    }

    private AndroidAppletLaunchAdapter() { }

    static Plan prepare(File jar, File gameDirectory, String version,
            String username, int width, int height) throws IOException {
        String appletClass = AndroidLaunchMethodDetector.detectAppletClass(jar);
        if (appletClass == null) {
            throw new IllegalArgumentException("The selected JAR does not contain MinecraftApplet.class.");
        }
        if (gameDirectory == null) throw new IllegalArgumentException("Game directory is required.");
        if (version == null || version.trim().isEmpty()) throw new IllegalArgumentException("Version is required.");
        String player = username == null ? "Player" : username.trim();
        if (!player.matches("[A-Za-z0-9_]{1,16}")) {
            throw new IllegalArgumentException("Offline player name must be 1–16 letters, digits or underscores.");
        }
        if (width < 320 || width > 4096 || height < 240 || height > 4096) {
            throw new IllegalArgumentException("Applet dimensions are outside supported bounds.");
        }
        File absoluteJar = jar.getCanonicalFile();
        URL codeBase = absoluteJar.getParentFile().toURI().toURL();
        URL documentBase = gameDirectory.getCanonicalFile().toURI().toURL();
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("username", player);
        parameters.put("sessionid", "0");
        parameters.put("haspaid", "false");
        parameters.put("standalone", "true");
        parameters.put("demo", "false");
        parameters.put("fullscreen", "false");
        parameters.put("version", version);
        parameters.put("gameDir", gameDirectory.getCanonicalPath());
        parameters.put("width", Integer.toString(width));
        parameters.put("height", Integer.toString(height));
        return new Plan(appletClass, gameDirectory, codeBase, documentBase,
                width, height, parameters);
    }

    /** Check class presence without defining or initializing any game classes. */
    static boolean containsApplet(File jar) throws IOException {
        if (jar == null || !jar.isFile()) return false;
        return AndroidLaunchMethodDetector.detectAppletClass(jar) != null;
    }
}
