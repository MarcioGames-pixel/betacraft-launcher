package com.marciogames.betacraft.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class AndroidV1CompatibilityTest {
    @Test public void appletVersionsAreRecognizedButNotAdvertisedAsPlayable() {
        String[] appletMethods = {"mc", "classic12a", "classic", "classic15a", "classicmp", "indev"};
        for (String method : appletMethods) {
            AndroidV1Compatibility.Entry entry = AndroidV1Compatibility.forMethod(method);
            assertNotNull("Expected v1 method " + method, entry);
            assertFalse("Applet method must remain blocked until its Android host exists: " + method,
                    entry.androidLaunchImplemented);
            assertTrue(AndroidV1Compatibility.describe(method).contains("not yet playable on Android"));
        }
    }

    @Test public void standaloneFamiliesHaveExplicitPartialSupport() {
        assertTrue(AndroidV1Compatibility.canBuildLaunchPlan("rd"));
        assertTrue(AndroidV1Compatibility.canBuildLaunchPlan("1.6"));
        assertFalse(AndroidV1Compatibility.canBuildLaunchPlan("4k"));
        assertFalse(AndroidV1Compatibility.canBuildLaunchPlan("unknown-method"));
    }

    @Test public void lookupIsCaseInsensitiveAndWhitespaceTolerant() {
        assertEquals("rd", AndroidV1Compatibility.forMethod(" RD ").method);
        assertNull(AndroidV1Compatibility.forMethod(null));
    }
}
