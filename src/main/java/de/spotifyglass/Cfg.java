package de.spotifyglass;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;

public class Cfg {
    public int x = 12, y = 12;
    public float scale = 1f;
    public int glass = 0x88202228;   // ARGB: Alpha = Glas-Transparenz
    public int accent = 0xFF1ED760;  // ARGB
    public boolean artistIcon = false, visible = true;
    public int volStep = 5;
    public String clientId = "", refreshToken = "";

    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("spotifyglass.json"); }
    public static Cfg I = load();

    static Cfg load() {
        try { if (Files.exists(file())) { Cfg c = GSON.fromJson(Files.readString(file()), Cfg.class); if (c != null) return c; } }
        catch (Exception ignored) { }
        return new Cfg();
    }
    public static void save() {
        try { Files.writeString(file(), GSON.toJson(I)); } catch (Exception ignored) { }
    }
}
