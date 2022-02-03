package fr.jydet.launcher;

import lombok.Getter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;

import static fr.jydet.launcher.Launcher.CACHE;

public record CachedRemoteJsonObject(File file, String data) {


    public static CachedRemoteJsonObject wget(String url, String name) {
        var file = new File(CACHE + name.replaceAll("[^A-za-z0-9.]", "_"));
        String data;
        if (! file.exists()) {
            try {
                file.getParentFile().mkdirs();
            } catch (Exception e) {
                new RuntimeException(e);
            }
            try (var out = new BufferedWriter(new FileWriter(file))) {
                data = getJson(url + name);
                out.write(data);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                data = Files.readString(file.toPath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (data.startsWith("<")) {
            try {
                file.delete();
            } catch (Exception e) {
                e.printStackTrace();
            }
            throw new RuntimeException("Error : " + data);
        }
        return new CachedRemoteJsonObject(file, data);
    }


    private static String getJson(String url) throws Exception {
        System.out.println("Trying to get Url : " + url);
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder(URI.create(url))
            .header("accept", "application/json")
            .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }
}
