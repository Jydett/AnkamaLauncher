package fr.jydet.launcher.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.jydet.launcher.CachedRemoteJsonObject;
import fr.jydet.launcher.Launcher;
import fr.jydet.launcher.Pair;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;
import java.util.stream.Stream;

@Data
public class GameJson {
    public GameFile main;

    @Data
    public static class GameFile {
        public Map<String, GameFileInfo> files;

        /**
         * @return Stream<URL, FILENAME>
         */
        public Stream<GameDlInfo> streamFile(String game) {
            return files.entrySet().stream().map(e -> new GameDlInfo(Launcher.BASE_URL + game + "/hashes/" + e.getValue().getHash().substring(0, 2) + "/" + e.getValue().getHash(), e.getKey(), e.getValue()));
        }
    }

    @Data
    public static class GameFileInfo {
        public String hash;
        public long size;
    }

    @Data
    @AllArgsConstructor
    public static class GameDlInfo {
        private String url;
        private String filename;
        public GameFileInfo gameFileInfo;
    }
}
