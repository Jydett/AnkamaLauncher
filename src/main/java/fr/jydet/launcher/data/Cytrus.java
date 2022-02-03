package fr.jydet.launcher.data;

import lombok.Data;

import java.util.Map;

@Data
public class Cytrus {
    public Map<String, GameData> games;
    public int version;
    public String name;

}
