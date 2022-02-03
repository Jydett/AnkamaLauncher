package fr.jydet.launcher.data;

import lombok.Data;

import java.util.Map;

import static fr.jydet.launcher.Launcher.BRANCH;
import static fr.jydet.launcher.Launcher.PLATFORM;

@Data
public class GameData {
    public int order;
    public int gameId;
    public GameAssets assets;
    public String name;
    public Map<String, Map<String, String>> platforms;

    public String retrieveVersion() {
        return platforms.get(PLATFORM).get(BRANCH);
    }

    public String retrieveHash() {
        return assets.meta.get(BRANCH);
    }

    public String retrieveFileName(String game, String build, String platform, String version) {
        return String.format("%s/releases/%s/%s/%s.json", game, build, platform, version);
    }
}
