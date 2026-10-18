package com.marciogames.betacraft.android;

/**
 * Explicit capability registry for the Java BetaCraft v1 version family.
 *
 * This prevents the UI/preflight from equating "recognized" with "playable".
 * The official desktop launch-method names are retained, while Android support
 * is stated separately for each family.
 */
final class AndroidV1Compatibility {
    enum Mode { STANDALONE_MAIN, STANDALONE_RUBYDUNG, LEGACY_APPLET, CUSTOM_DESKTOP, UNKNOWN }

    static final class Entry {
        final String method;
        final String label;
        final Mode mode;
        final boolean androidLaunchImplemented;
        final String note;

        Entry(String method, String label, Mode mode, boolean implemented, String note) {
            this.method = method;
            this.label = label;
            this.mode = mode;
            this.androidLaunchImplemented = implemented;
            this.note = note;
        }
    }

    private static final Entry[] ENTRIES = {
        new Entry("rd", "Cave Game / RubyDung", Mode.STANDALONE_RUBYDUNG, true,
                "Standalone RubyDung entry point; graphics/input still require the Android LWJGL backend."),
        new Entry("mc", "Minecraft pre-classic", Mode.LEGACY_APPLET, false,
                "Experimental JVM applet lifecycle helper exists; Android rendering, AWT compatibility and input integration remain incomplete."),
        new Entry("classic12a", "Classic 0.0.12a", Mode.LEGACY_APPLET, false,
                "Experimental applet lifecycle helper exists; the Android game renderer and input bridge are still missing."),
        new Entry("classic", "Minecraft Classic", Mode.LEGACY_APPLET, false,
                "Needs an Android replacement for the desktop applet wrapper."),
        new Entry("classic15a", "Classic 0.0.15a", Mode.LEGACY_APPLET, false,
                "Needs an Android replacement for the desktop applet wrapper."),
        new Entry("classicmp", "Classic multiplayer", Mode.LEGACY_APPLET, false,
                "Experimental applet lifecycle helper exists; renderer and legacy multiplayer integration remain missing."),
        new Entry("4k", "Minecraft 4K", Mode.CUSTOM_DESKTOP, false,
                "Requires its own launch adapter; desktop wrapper is not Android-safe."),
        new Entry("indev", "Minecraft Indev", Mode.LEGACY_APPLET, false,
                "Needs an Android replacement for the desktop applet wrapper."),
        new Entry("1.6", "Minecraft 1.6+ main entry", Mode.STANDALONE_MAIN, true,
                "Main-class invocation is implemented; complete libraries/assets and renderer are still required.")
    };

    private AndroidV1Compatibility() { }

    static Entry forMethod(String method) {
        if (method == null) return null;
        for (Entry entry : ENTRIES) {
            if (entry.method.equalsIgnoreCase(method.trim())) return entry;
        }
        return null;
    }

    static String describe(String method) {
        Entry entry = forMethod(method);
        if (entry == null) {
            return "Unrecognized BetaCraft v1 launch method. A custom Android adapter may be required.";
        }
        return entry.label + " — " + (entry.androidLaunchImplemented
                ? "launch-plan support exists" : "not yet playable on Android")
                + ". " + entry.note;
    }

    static boolean canBuildLaunchPlan(String method) {
        Entry entry = forMethod(method);
        return entry != null && entry.androidLaunchImplemented;
    }
}
