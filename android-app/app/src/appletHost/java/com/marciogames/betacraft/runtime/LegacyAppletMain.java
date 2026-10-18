package com.marciogames.betacraft.runtime;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Experimental JVM-side host for legacy Minecraft applets.
 *
 * This supplies the old AppletStub/AppletContext contract and calls init/start.
 * It does not turn AWT into an Android view or provide the missing LWJGL
 * SurfaceView renderer; those capabilities must be checked separately.
 */
public final class LegacyAppletMain {
    private LegacyAppletMain() { }

    public static void main(String[] args) throws Exception {
        Map<String, String> values = parse(args);
        String className = required(values, "appletClass");
        URL codeBase = new URL(required(values, "codeBase"));
        URL documentBase = new URL(required(values, "documentBase"));
        int width = positiveInt(values, "width", 854);
        int height = positiveInt(values, "height", 480);
        Map<String, String> parameters = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey().startsWith("param.")) {
                parameters.put(entry.getKey().substring(6), entry.getValue());
            }
        }

        Class<?> appletType = Class.forName(className);
        Class<?> stubType = Class.forName("java.applet.AppletStub");
        Class<?> contextType = Class.forName("java.applet.AppletContext");
        Object applet = appletType.getDeclaredConstructor().newInstance();
        final boolean[] active = { false };
        Object context = Proxy.newProxyInstance(contextType.getClassLoader(),
                new Class<?>[]{contextType}, new ContextHandler());
        Object stub = Proxy.newProxyInstance(stubType.getClassLoader(),
                new Class<?>[]{stubType}, new StubHandler(parameters, codeBase,
                        documentBase, context, active, width, height));
        appletType.getMethod("setStub", stubType).invoke(applet, stub);
        appletType.getMethod("setSize", int.class, int.class).invoke(applet, width, height);
        try {
            appletType.getMethod("init").invoke(applet);
            active[0] = true;
            appletType.getMethod("start").invoke(applet);
        } catch (Throwable failure) {
            active[0] = false;
            try { appletType.getMethod("stop").invoke(applet); } catch (Throwable ignored) { }
            try { appletType.getMethod("destroy").invoke(applet); } catch (Throwable ignored) { }
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            System.err.println("BetaCraft legacy applet failed during init/start: " + cause);
            cause.printStackTrace(System.err);
            throw new IllegalStateException("Legacy applet lifecycle failed. This host does not provide an Android AWT/LWJGL renderer.", cause);
        }
        System.out.println("Legacy applet init/start completed for " + className
                + ". AWT/LWJGL Android rendering still requires a compatible backend.");
    }

    private static final class StubHandler implements InvocationHandler {
        private final Map<String, String> parameters;
        private final URL codeBase, documentBase;
        private final Object context;
        private final boolean[] active;
        private final int width, height;
        StubHandler(Map<String, String> p, URL c, URL d, Object x,
                boolean[] a, int w, int h) {
            parameters = p; codeBase = c; documentBase = d;
            context = x; active = a; width = w; height = h;
        }
        @Override public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("getParameter".equals(name)) return args == null ? null : parameters.get(String.valueOf(args[0]));
            if ("getCodeBase".equals(name)) return codeBase;
            if ("getDocumentBase".equals(name)) return documentBase;
            if ("getAppletContext".equals(name)) return context;
            if ("isActive".equals(name)) return active[0];
            if ("appletResize".equals(name)) {
                System.out.println("Applet requested resize; Android host retains " + width + "x" + height);
                return null;
            }
            return defaultValue(method.getReturnType());
        }
    }

    private static final class ContextHandler implements InvocationHandler {
        @Override public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("getApplets".equals(name)) return Collections.enumeration(Collections.emptyList());
            if ("getStreamKeys".equals(name)) return Collections.emptyIterator();
            if ("toString".equals(name)) return "BetaCraftAndroidAppletContext";
            return defaultValue(method.getReturnType());
        }
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String token = args[i];
            if (!token.startsWith("--") || i + 1 >= args.length) {
                throw new IllegalArgumentException("Expected --name value arguments; invalid token: " + token);
            }
            result.put(token.substring(2), args[++i]);
        }
        return result;
    }
    private static String required(Map<String, String> map, String key) {
        String value = map.get(key);
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Missing --" + key);
        return value;
    }
    private static int positiveInt(Map<String, String> map, String key, int fallback) {
        int value = Integer.parseInt(map.containsKey(key) ? map.get(key) : Integer.toString(fallback));
        if (value < 1 || value > 4096) throw new IllegalArgumentException("Invalid --" + key);
        return value;
    }
    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }
}
