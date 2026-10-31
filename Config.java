package pl.easybot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Ustawienia zapisywane w config/easybot.json. */
public final class Config {
    public static final int MAX_BOTS = 30;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    public String address = "";
    public int count = 1;
    /** Przerwa miedzy startami kolejnych botow (sekundy). */
    public int joinDelaySec = 18;
    /** Haslo uzywane przy auto /register i /login. */
    public String password = "eloelo";
    /** Komenda wysylana po zalogowaniu, np. "/warp afk" (puste = brak). */
    public String postLoginCommand = "";
    public String[] nicks = new String[MAX_BOTS];

    public static synchronized Config get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("easybot.json");
    }

    private static Config load() {
        Config c = null;
        try {
            Path f = file();
            if (Files.exists(f)) {
                c = GSON.fromJson(Files.readString(f), Config.class);
            }
        } catch (Exception e) {
            EasyBotMod.LOGGER.warn("Nie udalo sie wczytac easybot.json, uzywam domyslnych ustawien", e);
        }
        if (c == null) {
            c = new Config();
        }
        c.normalize();
        return c;
    }

    private void normalize() {
        String[] fixed = new String[MAX_BOTS];
        for (int i = 0; i < MAX_BOTS; i++) {
            fixed[i] = nicks != null && i < nicks.length && nicks[i] != null ? nicks[i] : "";
        }
        nicks = fixed;
        count = Math.max(1, Math.min(MAX_BOTS, count));
        joinDelaySec = Math.max(1, Math.min(3600, joinDelaySec));
        if (address == null) {
            address = "";
        }
        if (password == null || password.isBlank()) {
            password = "eloelo";
        }
        if (postLoginCommand == null) {
            postLoginCommand = "";
        }
    }

    public static synchronized void save() {
        try {
            Config c = get();
            c.normalize();
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(c));
        } catch (Exception e) {
            EasyBotMod.LOGGER.warn("Nie udalo sie zapisac easybot.json", e);
        }
    }
}
