package com.atlauncher.workers;

import static org.junit.jupiter.api.Assertions.assertNull;

import javax.swing.JLabel;

import org.junit.jupiter.api.Test;

public class BackgroundImageWorkerTest {
    @Test
    public void missingOrInvalidImageUrlDoesNotOpenCacheDirectory() throws Exception {
        for (String url : new String[] { null, "", "   ", ":" }) {
            BackgroundImageWorker worker = new BackgroundImageWorker(new JLabel(), url, 60, 60);
            assertNull(worker.doInBackground());
        }
    }
}
