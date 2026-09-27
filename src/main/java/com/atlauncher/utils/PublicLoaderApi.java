/*
 * ETLauncher - https://github.com/ETLauncher/launcher
 * Copyright (C) 2026 ETLauncher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.atlauncher.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.atlauncher.data.minecraft.loaders.LoaderType;
import com.atlauncher.data.minecraft.loaders.LoaderVersion;
import com.atlauncher.data.minecraft.loaders.forge.ForgeLoader;
import com.atlauncher.network.Download;
import com.atlauncher.network.NetworkClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Loader metadata from the projects that publish the loaders. */
public final class PublicLoaderApi {
    private PublicLoaderApi() {}

    public static List<LoaderVersion> versions(LoaderType type, String minecraft) {
        switch (type) {
            case FABRIC:
                return metaVersions("https://meta.fabricmc.net/v2", minecraft, "Fabric");
            case LEGACY_FABRIC:
                return metaVersions("https://meta.legacyfabric.net/v2", minecraft, "LegacyFabric");
            case QUILT:
                return metaVersions("https://meta.quiltmc.org/v3", minecraft, "Quilt");
            case FORGE:
                return mavenVersions("https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml",
                        minecraft + "-", "Forge", minecraft);
            case NEOFORGE:
                if (minecraft.equals("1.20.1")) {
                    return mavenVersions("https://maven.neoforged.net/releases/net/neoforged/forge/maven-metadata.xml",
                            minecraft + "-", "NeoForge", minecraft);
                }
                String[] parts = minecraft.split("\\.");
                if (parts.length < 2) return Collections.emptyList();
                String prefix = parts[0].equals("1") ? parts[1] + "." + (parts.length > 2 ? parts[2] : "0") + "." : "";
                return mavenVersions("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml",
                        prefix, "NeoForge", minecraft);
            case PAPER:
                return paperVersions(minecraft);
            case PURPUR:
                return purpurVersions(minecraft);
            default:
                return Collections.emptyList();
        }
    }

    private static List<LoaderVersion> metaVersions(String base, String minecraft, String type) {
        JsonArray entries = NetworkClient.get(base + "/versions/loader/" + minecraft, JsonArray.class);
        if (entries == null) return Collections.emptyList();
        List<LoaderVersion> result = new ArrayList<>();
        for (JsonElement entry : entries) {
            if (entry.isJsonObject() && entry.getAsJsonObject().has("loader")) {
                JsonObject loader = entry.getAsJsonObject().getAsJsonObject("loader");
                if (loader.has("version")) result.add(new LoaderVersion(loader.get("version").getAsString(), false, type));
            }
        }
        return result;
    }

    private static List<LoaderVersion> mavenVersions(String url, String prefix, String type, String minecraft) {
        String xml = Download.build().setUrl(url).asString();
        if (xml == null) return Collections.emptyList();
        List<LoaderVersion> result = new ArrayList<>();
        String recommendedVersion = type.equals("Forge") ? ForgeLoader.getRecommendedVersion(minecraft) : null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("<version>([^<]+)</version>").matcher(xml);
        while (matcher.find()) {
            String raw = matcher.group(1);
            if (!raw.startsWith(prefix)) continue;
            String version = raw.substring(prefix.length());
            boolean recommended = version.equals(recommendedVersion);
            result.add(new LoaderVersion(version, raw, recommended, type));
        }
        Collections.reverse(result);
        return result;
    }

    private static List<LoaderVersion> paperVersions(String minecraft) {
        JsonArray builds = NetworkClient.get("https://fill.papermc.io/v3/projects/paper/versions/" + minecraft + "/builds", JsonArray.class);
        if (builds == null) return Collections.emptyList();
        List<LoaderVersion> result = new ArrayList<>();
        for (JsonElement build : builds) {
            JsonObject object = build.getAsJsonObject();
            result.add(new LoaderVersion(object.get("id").getAsString(),
                    "STABLE".equals(object.get("channel").getAsString()), "Paper"));
        }
        return result;
    }

    private static List<LoaderVersion> purpurVersions(String minecraft) {
        JsonObject data = NetworkClient.get("https://api.purpurmc.org/v2/purpur/" + minecraft, JsonObject.class);
        if (data == null || !data.has("builds")) return Collections.emptyList();
        List<LoaderVersion> result = new ArrayList<>();
        for (JsonElement build : data.getAsJsonObject("builds").getAsJsonArray("all")) {
            result.add(new LoaderVersion(build.getAsString(), false, "Purpur"));
        }
        Collections.reverse(result);
        return result;
    }

    public static Map<String, Object> paperBuild(String minecraft, String build) {
        JsonArray builds = NetworkClient.get("https://fill.papermc.io/v3/projects/paper/versions/" + minecraft + "/builds", JsonArray.class);
        if (builds == null) return null;
        for (JsonElement entry : builds) {
            JsonObject data = entry.getAsJsonObject();
            if (!build.equals(data.get("id").getAsString())) continue;
            JsonObject download = data.getAsJsonObject("downloads").getAsJsonObject("server:default");
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("minecraft", minecraft);
            metadata.put("build", build);
            metadata.put("filename", download.get("name").getAsString());
            metadata.put("sha256", download.getAsJsonObject("checksums").get("sha256").getAsString());
            metadata.put("downloadUrl", download.get("url").getAsString());
            return metadata;
        }
        return null;
    }

    public static Map<String, Object> purpurBuild(String minecraft, String build) {
        JsonObject data = NetworkClient.get("https://api.purpurmc.org/v2/purpur/" + minecraft + "/" + build, JsonObject.class);
        if (data == null || !data.has("md5")) return null;
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("minecraft", minecraft);
        metadata.put("build", build);
        metadata.put("filename", "purpur-" + minecraft + "-" + build + ".jar");
        metadata.put("md5", data.get("md5").getAsString());
        metadata.put("downloadUrl", "https://api.purpurmc.org/v2/purpur/" + minecraft + "/" + build + "/download");
        return metadata;
    }

    public static String fabricMetaBase(LoaderType type) {
        if (type == LoaderType.LEGACY_FABRIC) return "https://meta.legacyfabric.net/v2";
        if (type == LoaderType.QUILT) return "https://meta.quiltmc.org/v3";
        return "https://meta.fabricmc.net/v2";
    }

    public static JsonObject profile(LoaderType type, String minecraft, String version, boolean server) {
        return NetworkClient.get(fabricMetaBase(type) + "/versions/loader/" + minecraft + "/" + version
                + (server ? "/server/json" : "/profile/json"), JsonObject.class);
    }

    public static LoaderVersion forgeVersion(String minecraft, String version) {
        if (version == null) return null;
        String raw = version.startsWith(minecraft + "-") ? version : minecraft + "-" + version;
        String shortVersion = raw.substring(minecraft.length() + 1);
        return new LoaderVersion(shortVersion, raw, false, "Forge");
    }

    public static LoaderVersion neoForgeVersion(String minecraft, String version) {
        if (version == null) return null;
        String raw = minecraft.equals("1.20.1") && !version.startsWith(minecraft + "-")
                ? minecraft + "-" + version : version;
        String shortVersion = raw.startsWith(minecraft + "-") ? raw.substring(minecraft.length() + 1) : raw;
        return new LoaderVersion(shortVersion, raw, false, "NeoForge");
    }
}
