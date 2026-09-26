package com.atlauncher.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.atlauncher.data.AbstractNews;

class NewsManagerTest {
    @Test
    void displaysPublishedReleaseNotesSafely() {
        String json = "["
                + "{\"name\":\"ETLauncher <patch>\",\"body\":\"**Fixed** login\\n\\n<script>alert(1)</script>\","
                + "\"html_url\":\"https://github.com/ETLauncher/launcher/releases/tag/v1\","
                + "\"published_at\":\"2026-09-26T12:00:00Z\",\"draft\":false},"
                + "{\"name\":\"Unpublished\",\"html_url\":\"https://github.com/ETLauncher/launcher/releases/tag/v2\",\"draft\":true},"
                + "{\"name\":\"Wrong source\",\"html_url\":\"https://example.com/releases/tag/v3\"}"
                + "]";

        List<AbstractNews> releases = NewsManager.parseReleases(json);

        assertEquals(1, releases.size());
        String html = releases.get(0).htmlEntry;
        assertTrue(html.contains("ETLauncher &lt;patch&gt;"));
        assertTrue(html.contains("<strong>Fixed</strong> login"));
        assertTrue(html.contains("2026-09-26"));
        assertTrue(html.contains("https://github.com/ETLauncher/launcher/releases/tag/v1"));
        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("Unpublished"));
    }
}
