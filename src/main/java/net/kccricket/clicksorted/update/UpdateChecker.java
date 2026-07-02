package net.kccricket.clicksorted.update;

/*
 * This file is part of ClickSorted
 *
 * ClickSorted is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * ClickSorted is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with ClickSorted. If not, see <http://www.gnu.org/licenses/>.
 */

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kccricket.clicksorted.ClickSortedPlugin;
import net.kccricket.clicksorted.logging.Log;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Best-effort check against the Modrinth API for a newer published release.
 * <p>
 * The lookup runs entirely off the main thread (Folia-safe via the async scheduler) and never
 * throws into its caller: any network or parse failure degrades to a quiet debug log, since a
 * missed update check must never disrupt startup or a reload. Beyond the plugin handle, the only
 * state held is the handle to the recurring check task ({@link #reschedule()}), mirroring the
 * other small helpers in this codebase.
 */
public class UpdateChecker {

    private static final String API_URL = "https://api.modrinth.com/v2/project/clicksorted/version";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final ClickSortedPlugin plugin;
    private ScheduledTask scheduledTask;

    public UpdateChecker(ClickSortedPlugin plugin) {
        this.plugin = plugin;
    }

    /** Fire the check asynchronously and log the outcome. Returns immediately. */
    public void check() {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> run());
    }

    /**
     * (Re)schedules the recurring update check per {@code check_for_updates}/
     * {@code check_for_updates_interval_hours}, cancelling any previously scheduled task first.
     * Safe to call repeatedly (on enable and after every {@code admin reload}). Does not itself
     * fire an immediate check — callers that want one should call {@link #check()} separately.
     */
    public void reschedule() {
        stop();
        var main = plugin.getConfigManager().main();
        if (!main.getCheckForUpdates()) {
            return;
        }
        long periodHours = main.getUpdateCheckIntervalHours();
        scheduledTask = plugin.getServer().getAsyncScheduler().runAtFixedRate(
                plugin, task -> run(), periodHours, periodHours, TimeUnit.HOURS);
    }

    /** Cancels the recurring check task, if one is scheduled. Safe to call when none is running. */
    public void stop() {
        if (scheduledTask != null) {
            try {
                scheduledTask.cancel();
            } catch (Exception e) {
                // Best-effort: some scheduler implementations (e.g. test harnesses) don't support
                // cancellation. Never let plugin shutdown fail over a missed update check.
                Log.debug("Failed to cancel update check task: " + e.getClass().getSimpleName());
            } finally {
                scheduledTask = null;
            }
        }
    }

    private void run() {
        String current = plugin.getPluginMeta().getVersion();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "kccricket/ClickSorted/" + current + " (github.com/kccricket/ClickSorted)")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                Log.debug("Update check skipped: Modrinth returned HTTP " + response.statusCode());
                return;
            }

            String latest = latestRelease(response.body());
            if (latest == null) {
                Log.debug("Update check: no release versions found on Modrinth.");
                return;
            }

            if (compareVersions(latest, current) > 0) {
                Log.log(java.util.logging.Level.INFO, "A new version of ClickSorted is available: "
                        + latest + " (you are running " + current + ").");
                Log.log(java.util.logging.Level.INFO, "Download: "
                        + "https://modrinth.com/plugin/clicksorted | "
                        + "https://hangar.papermc.io/kccricket/ClickSorted | "
                        + "https://github.com/kccricket/ClickSorted/releases");
            } else {
                Log.debug("Update check: ClickSorted is up to date (" + current + ").");
            }
        } catch (Exception e) {
            // Best-effort only — never surface a stack trace for a failed update check.
            Log.debug("Update check failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Parse the Modrinth version-list JSON and return the highest {@code release} version number,
     * or {@code null} if none is present. Modrinth returns versions roughly newest-first, but we do
     * not rely on ordering.
     */
    private String latestRelease(String body) {
        JsonArray versions = JsonParser.parseString(body).getAsJsonArray();
        String best = null;
        for (var element : versions) {
            JsonObject version = element.getAsJsonObject();
            if (!version.has("version_number") || !version.has("version_type")) continue;
            if (!"release".equals(version.get("version_type").getAsString())) continue;
            String number = version.get("version_number").getAsString();
            if (best == null || compareVersions(number, best) > 0) {
                best = number;
            }
        }
        return best;
    }

    /**
     * Compare two dotted version strings numerically. A pre-release suffix (anything after a
     * {@code -}) sorts before the same base version. Returns &gt;0 if {@code a} is newer than
     * {@code b}, &lt;0 if older, 0 if equal. Tolerates a leading {@code v}.
     */
    static int compareVersions(String a, String b) {
        String[] aSplit = splitVersion(a);
        String[] bSplit = splitVersion(b);

        int cmp = compareNumeric(aSplit[0], bSplit[0]);
        if (cmp != 0) return cmp;

        // Equal base versions: a release (no suffix) is newer than a pre-release.
        boolean aPre = !aSplit[1].isEmpty();
        boolean bPre = !bSplit[1].isEmpty();
        if (aPre != bPre) return aPre ? -1 : 1;
        return aSplit[1].compareTo(bSplit[1]);
    }

    /** Returns [base, preReleaseSuffix]; suffix is "" when there is none. */
    private static String[] splitVersion(String v) {
        String stripped = v.startsWith("v") || v.startsWith("V") ? v.substring(1) : v;
        int dash = stripped.indexOf('-');
        if (dash < 0) return new String[]{stripped, ""};
        return new String[]{stripped.substring(0, dash), stripped.substring(dash + 1)};
    }

    private static int compareNumeric(String a, String b) {
        String[] aParts = a.split("\\.");
        String[] bParts = b.split("\\.");
        int len = Math.max(aParts.length, bParts.length);
        for (int i = 0; i < len; i++) {
            int aVal = i < aParts.length ? parse(aParts[i]) : 0;
            int bVal = i < bParts.length ? parse(bParts[i]) : 0;
            if (aVal != bVal) return Integer.compare(aVal, bVal);
        }
        return 0;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
