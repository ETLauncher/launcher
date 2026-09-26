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
package com.atlauncher.managers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.atlauncher.Network;
import com.atlauncher.data.AbstractNews;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class NewsManager {
    private static final String RELEASES_URL = "https://github.com/ETLauncher/launcher/releases";
    private static final String API_URL = "https://api.github.com/repos/ETLauncher/launcher/releases?per_page=10";

    static final BehaviorSubject<List<AbstractNews>> NEWS = BehaviorSubject.createDefault(Collections.emptyList());

    /**
     * Get the News for the Launcher
     *
     * @return The News items
     */
    public static Observable<List<AbstractNews>> getNews() {
        return NEWS;
    }

    /** Fetch published ETLauncher release descriptions without blocking the UI. */
    public static void loadNews() {
        Request request = new Request.Builder().url(API_URL)
                .header("Accept", "application/vnd.github+json")
                .build();
        Network.CACHED_CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) {
                try (Response result = response) {
                    ResponseBody body = result.body();
                    if (!result.isSuccessful() || body == null) {
                        throw new IOException("GitHub releases API returned HTTP " + result.code());
                    }
                    List<AbstractNews> releases = parseReleases(body.string());
                    NEWS.onNext(releases.isEmpty() ? unavailable("No published releases yet.") : releases);
                } catch (Exception e) {
                    onFailure(call, e instanceof IOException ? (IOException) e : new IOException(e));
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                LogManager.logStackTrace("Error fetching ETLauncher releases", e);
                if (NEWS.getValue() == null || NEWS.getValue().isEmpty()) {
                    NEWS.onNext(unavailable("Could not load releases. Open GitHub to try again."));
                }
            }
        });
    }

    static List<AbstractNews> parseReleases(String json) {
        JsonArray items = JsonParser.parseString(json).getAsJsonArray();
        List<AbstractNews> releases = new ArrayList<>();
        for (JsonElement item : items) {
            JsonObject release = item.getAsJsonObject();
            if (release.has("draft") && release.get("draft").getAsBoolean()) {
                continue;
            }
            String url = stringValue(release, "html_url");
            if (!url.startsWith(RELEASES_URL + "/tag/")) {
                continue;
            }
            String title = stringValue(release, "name");
            if (title.isEmpty()) {
                title = stringValue(release, "tag_name");
            }
            releases.add(new AbstractNews(title, stringValue(release, "body"), url,
                    stringValue(release, "published_at")));
        }
        return releases;
    }

    private static String stringValue(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : "";
    }

    private static List<AbstractNews> unavailable(String message) {
        return Collections.singletonList(new AbstractNews("Releases", message, RELEASES_URL, ""));
    }
}
