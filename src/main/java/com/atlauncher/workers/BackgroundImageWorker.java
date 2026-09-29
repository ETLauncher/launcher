/*
 * ATLauncher - https://github.com/ATLauncher/ATLauncher
 * Copyright (C) 2013-2022 ATLauncher
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.atlauncher.workers;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingWorker;

import com.atlauncher.FileSystem;
import com.atlauncher.network.Download;
import com.google.common.hash.Hashing;

import okhttp3.HttpUrl;

public class BackgroundImageWorker extends SwingWorker<ImageIcon, Object> {
    private final JLabel label;
    private final String url;
    private final int width;
    private final int height;

    public BackgroundImageWorker(JLabel label, String url, int width, int height) {
        this.label = label;
        this.url = url;
        this.width = width;
        this.height = height;
    }

    @Override
    protected ImageIcon doInBackground() throws Exception {
        if (this.url == null || this.url.isBlank() || HttpUrl.parse(this.url) == null) {
            return null;
        }

        Path path = FileSystem.REMOTE_IMAGE_CACHE.resolve(
                Hashing.sha256().hashString(this.url, StandardCharsets.UTF_8).toString());

        if (Files.isDirectory(path)) {
            return null;
        }

        Download download = Download.build().setUrl(this.url).ignoreFailures().downloadTo(path);

        if (!Files.isRegularFile(path)) {
            try {
                download.downloadFile();
            } catch (IOException ignored) {
                // ignored
            }
        }

        if (Files.isRegularFile(path)) {
            try (BufferedInputStream inputStream = new BufferedInputStream(Files.newInputStream(path))) {
                BufferedImage sourceImage = ImageIO.read(inputStream);
                if (sourceImage != null) {
                    int newWidth = width;
                    int newHeight = height;

                    // Compute scales to maintain the aspect ratio
                    if (sourceImage.getWidth() > sourceImage.getHeight()) {
                        newHeight = Math.max(1, (sourceImage.getHeight() * width) / sourceImage.getWidth());
                    } else {
                        newWidth = Math.max(1, (sourceImage.getWidth() * height) / sourceImage.getHeight());
                    }

                    BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2d = scaledImage.createGraphics();
                    g2d.drawImage(sourceImage, 0, 0, newWidth, newHeight, null);
                    g2d.dispose();
                    sourceImage.flush(); // Immediately discard large source image buffer
                    return new ImageIcon(scaledImage);
                }
            } catch (IOException ignored) {
                // A missing or corrupt cached image should leave the placeholder visible.
            }
        }

        return null;
    }

    @Override
    protected void done() {
        try {
            ImageIcon icon = get();
            if (icon != null) {
                label.setIcon(icon);
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        } finally {
            label.setVisible(true);
        }
    }
}
