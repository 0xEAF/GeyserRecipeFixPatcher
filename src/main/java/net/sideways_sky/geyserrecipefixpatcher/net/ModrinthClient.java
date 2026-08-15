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
import java.util.List;
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
        return parseFirstVersion(resp.body());
    }

    /**
     * Minimal hand-rolled JSON field extraction to avoid pulling in a JSON
     * library dependency for a couple of fields. Modrinth's version list is
     * an array of objects; we only need the first element's version_number
     * and its primary file's url/filename/sha1.
     */
    private ReleaseInfo parseFirstVersion(String json) throws IOException {
        int firstObjEnd = findMatchingBrace(json, json.indexOf('{'));
        String first = json.substring(json.indexOf('{'), firstObjEnd + 1);

        String versionNumber = extractString(first, "version_number");
        // files: [ { ..., "url": "...", "filename": "...", "hashes": {"sha1": "..."}, "primary": true, ... }, ... ]
        int filesIdx = first.indexOf("\"files\"");
        String filesBlock = first.substring(filesIdx);
        String fileName = extractString(filesBlock, "filename");
        String downloadUrl = extractString(filesBlock, "url");
        String sha1 = extractString(filesBlock, "sha1");

        if (versionNumber == null || downloadUrl == null || fileName == null) {
            throw new IOException("Unexpected Modrinth API response shape - could not find version/url/filename");
        }
        return new ReleaseInfo(versionNumber, fileName, downloadUrl, sha1);
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
