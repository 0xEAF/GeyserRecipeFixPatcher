package net.sideways_sky.geyserrecipefixpatcher.net;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Talks only to Modrinth's public REST API - the author's own official
 * distribution channel for compiled GeyserRecipeFix builds. We never scrape,
 * mirror, or cache anything beyond what's needed for the current patch run.
 */
public final class ModrinthClient {

    private static final String USER_AGENT = "GeyserRecipeFixPatcher/1.0 (+github.com/you/GeyserRecipeFixPatcher)";
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public record ReleaseInfo(String versionNumber, String fileName, String downloadUrl, String sha1) {
    }

    /**
     * Returns the newest listed version for the given Modrinth project slug.
     * Modrinth's /version endpoint is returned newest-first.
     */
    public ReleaseInfo fetchLatest(String projectSlug) throws IOException, InterruptedException {
        List<ReleaseInfo> versions = fetchAllVersions(projectSlug);
        if (versions.isEmpty()) {
            throw new IOException("Modrinth returned no versions for project " + projectSlug);
        }
        return versions.get(0);
    }

    /**
     * Looks up one specific, already-known version by its version_number -
     * used to re-patch a version we already have pinned (e.g. auto-update is
     * off, but GeyserRecipeFixPatcher's own logic changed and needs to be
     * re-applied) without moving to whatever is currently latest.
     */
    public Optional<ReleaseInfo> fetchByVersionNumber(String projectSlug, String versionNumber)
            throws IOException, InterruptedException {
        for (ReleaseInfo info : fetchAllVersions(projectSlug)) {
            if (info.versionNumber().equals(versionNumber)) {
                return Optional.of(info);
            }
        }
        return Optional.empty();
    }

    private List<ReleaseInfo> fetchAllVersions(String projectSlug) throws IOException, InterruptedException {
        String url = "https://api.modrinth.com/v2/project/" + projectSlug + "/version";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("Modrinth API returned HTTP " + resp.statusCode() + " for " + url);
        }
        return parseVersions(resp.body());
    }

    /**
     * Minimal hand-rolled JSON field extraction to avoid pulling in a JSON
     * library dependency for a few fields per entry. Modrinth's version list
     * is a top-level array of objects; for each one we only need
     * version_number and its primary file's url/filename/sha1.
     */
    private List<ReleaseInfo> parseVersions(String json) throws IOException {
        List<ReleaseInfo> result = new ArrayList<>();
        int i = json.indexOf('{');
        while (i != -1) {
            int end = findMatchingBrace(json, i);
            String obj = json.substring(i, end + 1);

            String versionNumber = extractString(obj, "version_number");
            int filesIdx = obj.indexOf("\"files\"");
            if (versionNumber != null && filesIdx != -1) {
                String filesBlock = obj.substring(filesIdx);
                String fileName = extractString(filesBlock, "filename");
                String downloadUrl = extractString(filesBlock, "url");
                String sha1 = extractString(filesBlock, "sha1");
                if (fileName != null && downloadUrl != null) {
                    result.add(new ReleaseInfo(versionNumber, fileName, downloadUrl, sha1));
                }
            }

            i = json.indexOf('{', end + 1);
        }
        if (result.isEmpty()) {
            throw new IOException("Unexpected Modrinth API response shape - could not find any version entries");
        }
        return result;
    }

    private static String extractString(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        if (m.find()) {
            return m.group(1).replace("\\/", "/").replace("\\\\", "\\");
        }
        return null;
    }

    private static int findMatchingBrace(String s, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        throw new IllegalArgumentException("No matching closing brace found");
    }

    public void download(String url, Path destination) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) {
            throw new IOException("Download failed: HTTP " + resp.statusCode() + " for " + url);
        }
        Files.createDirectories(destination.getParent());
        try (InputStream in = resp.body()) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
