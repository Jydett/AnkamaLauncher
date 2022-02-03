package fr.jydet.launcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MutableRequest;
import com.github.mizosoft.methanol.ProgressTracker;
import fr.jydet.launcher.data.Cytrus;
import fr.jydet.launcher.data.GameData;
import fr.jydet.launcher.data.GameJson;
import org.apache.commons.codec.digest.DigestUtils;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.handler.StreamedAsyncHandler;

import java.io.File;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.asynchttpclient.Dsl.*;

public class AsyncLauncher {

    public static final String CYTRUS_URL = "cytrus.json";
    public static final String PLATFORM = "windows";
    public static final String BRANCH = "main";
    public static final String BASE_URL = "https://launcher.cdn.ankama.com/";
    public static final String CACHE = "tmp/";
    public static final AsyncHttpClient client = asyncHttpClient();
    private static final AtomicInteger runningThread = new AtomicInteger(0);
    private static final AtomicInteger numberOfFileDownloaded = new AtomicInteger(0);
    private static long lastUpdate = -1;
    private static final AtomicLong totalDownloadedBytes = new AtomicLong(0);
    private static final ConcurrentHashMap<Long, Long> progresses = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
            .disable(FAIL_ON_UNKNOWN_PROPERTIES, FAIL_ON_IGNORED_PROPERTIES);
        Cytrus cytrusObj = objectMapper.copy()
            .readValue(CachedRemoteJsonObject.wget(BASE_URL, CYTRUS_URL).data(), Cytrus.class);
        Map<Integer, Pair<String, GameData>> choice = new LinkedHashMap<>();
        for (Map.Entry<String, GameData> stringGameDataEntry : cytrusObj.games.entrySet()) {
            GameData gameData = stringGameDataEntry.getValue();
            choice.put(gameData.order, new Pair<>(stringGameDataEntry.getKey(), gameData));
        }
        for (Integer i : new TreeSet<>(choice.keySet())) {
            GameData gameData = choice.get(i).b;
            System.out.println(i + ") " + gameData.name + " version " + gameData.retrieveVersion() + " hash is " + gameData.retrieveHash());
        }
        System.out.println("Choose a game");
        try (var scanner = new Scanner(System.in)) {
            while (true) {
                int i = scanner.nextInt();
                Pair<String, GameData> choosed = choice.get(i);
                if (choosed != null) {
                    System.out.println("Selected game " + choosed.b.name);
                    System.out.println();
                    String gameFile = CachedRemoteJsonObject.wget(BASE_URL, choosed.b.retrieveFileName(
                        choosed.a, BRANCH, PLATFORM, choosed.b.retrieveVersion()
                    )).data();
                    GameJson gameJson = objectMapper.readValue(gameFile, GameJson.class);
                    runningThread.set(1);
                    new Thread(() -> {
                        CompletableFuture.allOf(gameJson.getMain().streamFile(choosed.a)
                            .sorted(Comparator.comparingLong(dlInfo -> dlInfo.getGameFileInfo().getSize()))
                            .map(d -> download(d, false))
                            .toArray(CompletableFuture<?>[]::new)).join();
                        runningThread.set(0);
                    }).start();
                    long start = System.currentTimeMillis();
                    while (runningThread.get() > 0) {
                        var newUpdate = totalDownloadedBytes.get();
                        System.err.println("update main :" + (newUpdate != AsyncLauncher.lastUpdate));
                        if (newUpdate != AsyncLauncher.lastUpdate) {
                            AsyncLauncher.lastUpdate = newUpdate;
                            var v = bytesToMiB(newUpdate);
                            System.out.print(/* "\r" + */"Downloading... " + numberOfFileDownloaded.get() + "/" + gameJson.getMain().files.size() + " (" + v + " MiB transferred) " + (v / ((System.currentTimeMillis() - start) / 1000)) + "MiB/s");
                        }
                        Thread.sleep(1000);
                    }
                    System.out.println("Game is downloaded");
                    return;
                }
            }
        }
    }

    public static final double MiB = 1048576;

    public static double bytesToMiB(long bytes) {
        return Math.rint(bytes * 100 / MiB) / 100;
    }

    public static CompletableFuture<?> download(GameJson.GameDlInfo dlInfo, boolean retry) {
        var finalFileName = CACHE + "games/" + dlInfo.getFilename();
        var file = new File(finalFileName);
        if (! file.exists()) {
            try {
                file.getParentFile().mkdirs();
            } catch (Exception e) {
                new RuntimeException(e);
            }
            var tracker = ProgressTracker.newBuilder()
                .bytesTransferredThreshold(1024 * 50)
                .build();
            var downloadingBodyHandler = HttpResponse.BodyHandlers.ofFile(
                file.toPath(), CREATE, WRITE);
            MutableRequest get = MutableRequest.GET(dlInfo.getUrl());
            try {
                return client.prepareGet(dlInfo.getUrl()).execute(
//                    new StreamedAsyncHandler<Object>() {}
                ).toCompletableFuture();
//                return client.sendAsync(get, tracker.tracking(downloadingBodyHandler, AsyncLauncher ::logProgress));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            if (retry) {
                throw new RuntimeException("Retried the file is still there !");
            }
            try {
                var computedHash = DigestUtils.sha1Hex(Files.readAllBytes(file.toPath()));
                if (! dlInfo.getGameFileInfo().getHash().equals(computedHash)) {
                    System.err.println(finalFileName + " from url " + dlInfo.getUrl());
                    System.err.println("Hash and computed hash are not the same :\nexpected : " + dlInfo.getGameFileInfo().getHash() + "\ncomputed : " + computedHash);
                    file.delete();
                    return download(dlInfo, true);
                } else {
                    numberOfFileDownloaded.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void logProgress(ProgressTracker.Progress progress) {
        var lastUpdate = progresses.get(Thread.currentThread().getId());
        if (lastUpdate == null || lastUpdate > progress.totalBytesTransferred()) {
            lastUpdate = 0L;
        }
        totalDownloadedBytes.addAndGet(progress.totalBytesTransferred() - lastUpdate);
        System.err.println("update worker " + Thread.currentThread().getId() + ")" + progress.totalBytesTransferred() + " " + lastUpdate + " diff " + (progress.totalBytesTransferred() - lastUpdate));
        if (progress.totalBytesTransferred() != lastUpdate) {
            progresses.put(Thread.currentThread().getId(), progress.totalBytesTransferred());
        }

        if (progress.done()) {
            numberOfFileDownloaded.incrementAndGet();
        }
    }
}
