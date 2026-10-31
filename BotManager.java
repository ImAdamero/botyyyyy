package pl.easybot.bot;

import pl.easybot.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Zarzadza botami: kolejka startow ze stalym odstepem, stop, wysylanie do wszystkich. */
public final class BotManager {
    private static final Pattern NICK = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private static final Bot[] BOTS = new Bot[Config.MAX_BOTS];
    private static final ScheduledFuture<?>[] FUTURES = new ScheduledFuture<?>[Config.MAX_BOTS];
    private static final ScheduledThreadPoolExecutor EXEC = new ScheduledThreadPoolExecutor(2, r -> {
        Thread t = new Thread(r, "EasyBot-scheduler");
        t.setDaemon(true);
        return t;
    });

    /** Wybrany bot w menu. */
    public static volatile int selected = 0;
    /** Ile botow dodano do kolejki ostatnim startem. */
    public static volatile int lastStarted = 0;

    static {
        EXEC.setRemoveOnCancelPolicy(true);
    }

    private BotManager() {
    }

    public static Bot get(int i) {
        return i >= 0 && i < BOTS.length ? BOTS[i] : null;
    }

    public static Bot getOrCreate(int i) {
        if (BOTS[i] == null) {
            BOTS[i] = new Bot(i);
        }
        return BOTS[i];
    }

    public static void later(Runnable r, long delayMs) {
        try {
            EXEC.schedule(r, delayMs, TimeUnit.MILLISECONDS);
        } catch (Throwable ignored) {
        }
    }

    /** @return komunikat bledu albo null, gdy ustawienia sa poprawne. */
    public static String validate(Config c) {
        if (c.address.isBlank()) {
            return "Wpisz adres serwera.";
        }
        List<String> seen = new ArrayList<>();
        for (int i = 0; i < c.count; i++) {
            String nick = c.nicks[i] == null ? "" : c.nicks[i].trim();
            if (!NICK.matcher(nick).matches()) {
                return "Bot " + (i + 1) + ": nick musi miec 3-16 znakow (litery, cyfry, _).";
            }
            if (seen.contains(nick.toLowerCase())) {
                return "Bot " + (i + 1) + ": nick '" + nick + "' sie powtarza.";
            }
            seen.add(nick.toLowerCase());
        }
        return null;
    }

    /**
     * Ustawia boty w kolejce: pierwszy startuje od razu, kazdy nastepny po {@code joinDelaySec} sekundach
     * od poprzedniego (stala przerwa, bez losowosci).
     *
     * @return komunikat bledu albo null
     */
    public static synchronized String startAll() {
        Config c = Config.get();
        String err = validate(c);
        if (err != null) {
            return err;
        }
        String address = c.address.trim();
        String host = address;
        int port = 25565;
        int colon = address.lastIndexOf(':');
        if (colon > 0 && address.indexOf(':') == colon) {
            host = address.substring(0, colon).trim();
            try {
                port = Integer.parseInt(address.substring(colon + 1).trim());
            } catch (NumberFormatException e) {
                return "Zly port w adresie.";
            }
        }
        final String fHost = host;
        final int fPort = port;
        final String password = c.password;
        final String post = c.postLoginCommand;
        long delayMs = c.joinDelaySec * 1000L;

        int k = 0;
        for (int i = 0; i < c.count; i++) {
            Bot bot = getOrCreate(i);
            if (bot.isActive()) {
                continue;
            }
            long delay = k * delayMs;
            bot.queue(c.nicks[i].trim(), System.currentTimeMillis() + delay);
            FUTURES[i] = EXEC.schedule(() -> bot.connect(fHost, fPort, password, post), delay, TimeUnit.MILLISECONDS);
            k++;
        }
        lastStarted = k;
        return k == 0 ? "Wszystkie boty juz dzialaja." : null;
    }

    public static synchronized void stopAll() {
        for (int i = 0; i < BOTS.length; i++) {
            ScheduledFuture<?> f = FUTURES[i];
            if (f != null) {
                f.cancel(false);
                FUTURES[i] = null;
            }
            if (BOTS[i] != null) {
                BOTS[i].stop();
            }
        }
    }

    public static synchronized void stop(int i) {
        if (i < 0 || i >= BOTS.length) {
            return;
        }
        ScheduledFuture<?> f = FUTURES[i];
        if (f != null) {
            f.cancel(false);
            FUTURES[i] = null;
        }
        if (BOTS[i] != null) {
            BOTS[i].stop();
        }
    }

    /** Wysyla wiadomosc kazdym botem online, z przerwa 600 ms miedzy botami (anty-spam). */
    public static void broadcast(String message) {
        long delay = 0;
        for (Bot b : BOTS) {
            if (b != null && b.state() == Bot.State.ONLINE) {
                final Bot bot = b;
                later(() -> bot.send(message), delay);
                delay += 600;
            }
        }
    }

    public static void shutdown() {
        stopAll();
        EXEC.shutdownNow();
    }
}
