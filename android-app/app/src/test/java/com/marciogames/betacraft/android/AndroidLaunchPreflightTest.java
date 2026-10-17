package com.marciogames.betacraft.android;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
    public void defaultsSafelyForMissingVersion() {
        assertEquals(8, AndroidLaunchPreflight.recommendedJavaMajor(null));
        assertEquals(8, AndroidLaunchPreflight.recommendedJavaMajor(""));
    }
}
