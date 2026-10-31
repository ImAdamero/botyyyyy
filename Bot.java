package pl.easybot.bot;

import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import pl.easybot.EasyBotMod;
import pl.easybot.util.Texts;

import java.net.InetSocketAddress;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/** Jeden bot (offline, bez konta premium) z wlasna konsola. */
public final class Bot {
    public enum State { IDLE, QUEUED, CONNECTING, ONLINE, OFFLINE, FAILED }

    private static final int MAX_LINES = 1500;

    public final int index;

    private final CopyOnWriteArrayList<LogLine> lines = new CopyOnWriteArrayList<>();
    private final AtomicInteger errorCount = new AtomicInteger();

    private volatile State state = State.IDLE;
    private volatile String nick = "";
    private volatile long queuedAt;
    private volatile long onlineSince;
    private volatile boolean stopped = true;
    private volatile ClientSession session;

    private volatile String password = "eloelo";
    private volatile String postCommand = "";
    private volatile boolean postDone;
    private volatile boolean authDone;
    private volatile long lastRegister;
    private volatile long lastLogin;
    private volatile int registerTries;
    private volatile int loginTries;

    public Bot(int index) {
        this.index = index;
    }

    // ---------------------------------------------------------------- stan

    public State state() {
        return state;
    }

    public String nick() {
        return nick;
    }

    public long queuedAt() {
        return queuedAt;
    }

    public long onlineSince() {
        return onlineSince;
    }

    public int errorCount() {
        return errorCount.get();
    }

    public List<LogLine> lines() {
        return lines;
    }

    /** Czy bot jest w kolejce, laczy sie albo jest online (nie wolno go uruchamiac drugi raz). */
    public boolean isActive() {
        State s = state;
        return s == State.QUEUED || s == State.CONNECTING || s == State.ONLINE;
    }

    // ---------------------------------------------------------------- log

    private void add(LogLine.Kind kind, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        lines.add(new LogLine(System.currentTimeMillis(), kind, text));
        while (lines.size() > MAX_LINES) {
            try {
                lines.remove(0);
            } catch (Throwable ignored) {
                break;
            }
        }
        if (kind == LogLine.Kind.ERROR) {
            errorCount.incrementAndGet();
        }
    }

    public void info(String text) {
        add(LogLine.Kind.INFO, text);
    }

    public void error(String text) {
        add(LogLine.Kind.ERROR, text);
    }

    public void clearErrors() {
        errorCount.set(0);
    }

    // ---------------------------------------------------------------- cykl zycia

    /** Ustawia bota w kolejce; faktyczne laczenie robi BotManager o czasie {@code startAt}. */
    public void queue(String nick, long startAt) {
        this.nick = nick;
        this.queuedAt = startAt;
        this.stopped = false;
        this.state = State.QUEUED;
        long in = Math.max(0, (startAt - System.currentTimeMillis()) / 1000);
        add(LogLine.Kind.INFO, "--- Start w kolejce jako '" + nick + "' (za " + in + " s) ---");
    }

    public void connect(String host, int port, String password, String postCommand) {
        if (stopped) {
            return;
        }
        this.password = password;
        this.postCommand = postCommand == null ? "" : postCommand.trim();
        this.postDone = false;
        this.authDone = false;
        this.registerTries = 0;
        this.loginTries = 0;
        this.lastRegister = 0;
        this.lastLogin = 0;
        this.state = State.CONNECTING;
        info("Lacze z " + host + ":" + port + " jako " + nick + " ...");
        try {
            InetSocketAddress address = new InetSocketAddress(host, port);
            if (address.isUnresolved()) {
                fail("Nie znaleziono serwera '" + host + "' (zly adres albo brak DNS).");
                return;
            }
            ClientSession s = ClientNetworkSessionFactory.factory()
                    .setRemoteSocketAddress(address)
                    .setProtocol(new MinecraftProtocol(nick))
                    .create();
            session = s;
            s.addListener(new SessionAdapter() {
                @Override
                public void packetReceived(Session sess, Packet packet) {
                    onPacket(packet);
                }

                @Override
                public void disconnected(DisconnectedEvent event) {
                    onDisconnected(event);
                }
            });
            s.connect();
        } catch (Throwable t) {
            EasyBotMod.LOGGER.warn("Bot {} - blad polaczenia", nick, t);
            fail("Blad laczenia: " + t);
        }
    }

    private void fail(String message) {
        error(message);
        state = State.FAILED;
    }

    public void stop() {
        stopped = true;
        ClientSession s = session;
        session = null;
        if (s != null) {
            try {
                s.disconnect(Component.text("EasyBot stop"));
            } catch (Throwable ignored) {
            }
        }
        if (state != State.IDLE) {
            add(LogLine.Kind.INFO, "--- Zatrzymano ---");
        }
        state = State.IDLE;
    }

    // ---------------------------------------------------------------- pakiety

    private void onPacket(Packet packet) {
        try {
            if (packet instanceof ClientboundLoginPacket) {
                onJoined();
            } else if (packet instanceof ClientboundSystemChatPacket p) {
                onText(Texts.plain(p.getContent()));
            } else if (packet instanceof ClientboundPlayerChatPacket p) {
                onText("<czat> " + Texts.clean(String.valueOf(p.getContent())));
            }
        } catch (Throwable t) {
            EasyBotMod.LOGGER.warn("Bot {} - blad obslugi pakietu", nick, t);
        }
    }

    private void onJoined() {
        state = State.ONLINE;
        onlineSince = System.currentTimeMillis();
        info("Wszedl na serwer.");
        // jesli serwer nie prosi o logowanie, komenda po zalogowaniu poleci po 25 s
        BotManager.later(this::runPostLogin, 25_000);
    }

    private void onDisconnected(DisconnectedEvent event) {
        String reason = Texts.plain(event.getReason());
        Throwable cause = event.getCause();
        boolean wasOnline = state == State.ONLINE;
        String msg = "Rozlaczono" + (reason.isBlank() ? "" : ": " + reason);
        if (cause != null) {
            msg += " (" + cause.getClass().getSimpleName()
                    + (cause.getMessage() != null ? ": " + cause.getMessage() : "") + ")";
        }
        if (stopped) {
            info(msg);
            state = State.IDLE;
            return;
        }
        error(msg);
        String hint = explain((reason + " " + (cause == null ? "" : cause)).toLowerCase(Locale.ROOT));
        if (hint != null) {
            error("Wskazowka: " + hint);
        }
        state = wasOnline ? State.OFFLINE : State.FAILED;
    }

    /** Krotkie wyjasnienia najczestszych powodow, dla ktorych bot nie moze wejsc. */
    private static String explain(String low) {
        if (low.contains("full") || low.contains("pelny") || low.contains("pełny")) {
            return "serwer jest pelny.";
        }
        if (low.contains("throttl") || low.contains("too fast") || low.contains("zbyt szybko")
                || low.contains("too many") || low.contains("rate")) {
            return "serwer ogranicza szybkosc polaczen - zwieksz odstep miedzy botami.";
        }
        if (low.contains("ban")) {
            return "nick albo IP ma bana.";
        }
        if (low.contains("whitelist") || low.contains("bialej") || low.contains("białej")) {
            return "serwer ma whiteliste.";
        }
        if (low.contains("already") || low.contains("juz jest") || low.contains("już jest")
                || low.contains("duplicate")) {
            return "ten nick jest juz na serwerze.";
        }
        if (low.contains("timed out") || low.contains("timeout") || low.contains("connection refused")
                || low.contains("connectexception")) {
            return "serwer nie odpowiada (zly adres/port, serwer wylaczony albo blokada IP).";
        }
        if (low.contains("outdated") || low.contains("incompatible") || low.contains("version")
                || low.contains("wersj")) {
            return "zla wersja - boty mowia protokolem 1.21.11.";
        }
        if (low.contains("session") || low.contains("online mode") || low.contains("premium")
                || low.contains("authenticat")) {
            return "serwer jest w trybie premium (online-mode) - boty bez konta nie wejda.";
        }
        if (low.contains("flood") || low.contains("spam") || low.contains("kicked")) {
            return "serwer wyrzucil bota (anty-spam / kick).";
        }
        return null;
    }

    // ---------------------------------------------------------------- tekst serwera

    private void onText(String text) {
        String clean = Texts.clean(text);
        if (clean.isEmpty()) {
            return;
        }
        String low = clean.toLowerCase(Locale.ROOT);
        add(looksLikeError(low) ? LogLine.Kind.ERROR : LogLine.Kind.CHAT, clean);
        authReact(low);
    }

    private static boolean looksLikeError(String low) {
        String[] words = {
                "zbanowan", "banned", "nie masz uprawnie", "blad", "błąd", "error", "nieprawidlow",
                "nieprawidłow", "niepoprawn", "invalid", "wrong password", "zle haslo", "złe hasło",
                "wyrzucon", "kicked", "too many", "za duzo", "za dużo", "nie udalo", "nie udało", "failed",
                "captcha", "nie mozesz", "nie możesz", "zablokowan"
        };
        for (String w : words) {
            if (low.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /** Automatyczna rejestracja i logowanie, gdy serwer o to prosi. */
    private void authReact(String low) {
        long now = System.currentTimeMillis();
        if (low.contains("zalogowano") || low.contains("zarejestrowano") || low.contains("logged in")
                || low.contains("successfully") || low.contains("pomyslnie") || low.contains("pomyślnie")) {
            if (!authDone) {
                authDone = true;
                add(LogLine.Kind.AUTH, "Konto gotowe (zalogowano / zarejestrowano).");
                BotManager.later(this::runPostLogin, 2500 + ThreadLocalRandom.current().nextInt(2500));
            }
            return;
        }
        if (low.contains("/register") || low.contains("zarejestruj")) {
            if (now - lastRegister > 3000 && registerTries < 4) {
                lastRegister = now;
                registerTries++;
                String pw = password;
                int args = placeholders(low, "/register");
                String cmd = args >= 2 ? "register " + pw + " " + pw : "register " + pw;
                rawCommand(cmd);
                add(LogLine.Kind.AUTH, "Auto: /register " + (args >= 2 ? "**** ****" : "****"));
            }
        } else if (low.contains("/login") || low.contains("zaloguj")) {
            if (now - lastLogin > 3000 && loginTries < 4) {
                lastLogin = now;
                loginTries++;
                rawCommand("login " + password);
                add(LogLine.Kind.AUTH, "Auto: /login ****");
            }
        }
    }

    /** Liczy argumenty typu <haslo> [haslo] po komendzie w opisie uzycia. */
    private static int placeholders(String low, String command) {
        int at = low.indexOf(command);
        if (at < 0) {
            return 1;
        }
        String tail = low.substring(at + command.length(), Math.min(low.length(), at + command.length() + 80));
        int n = 0;
        for (char c : tail.toCharArray()) {
            if (c == '<' || c == '[') {
                n++;
            }
        }
        return Math.max(1, n);
    }

    private void runPostLogin() {
        if (postDone || state != State.ONLINE || postCommand.isBlank()) {
            return;
        }
        postDone = true;
        String cmd = postCommand.startsWith("/") ? postCommand : "/" + postCommand;
        add(LogLine.Kind.AUTH, "Komenda po zalogowaniu: " + cmd);
        send(cmd);
    }

    // ---------------------------------------------------------------- wysylanie

    private void rawCommand(String command) {
        ClientSession s = session;
        if (s != null && s.isConnected()) {
            s.send(new ServerboundChatCommandPacket(command));
        }
    }

    /** Wysyla czat albo komende (zaczynajaca sie od '/'). */
    public boolean send(String message) {
        String msg = message == null ? "" : message.trim();
        if (msg.isEmpty()) {
            return false;
        }
        ClientSession s = session;
        if (state != State.ONLINE || s == null || !s.isConnected()) {
            error("Nie mozna wyslac - bot nie jest online.");
            return false;
        }
        try {
            if (msg.startsWith("/")) {
                s.send(new ServerboundChatCommandPacket(msg.substring(1)));
            } else {
                s.send(new ServerboundChatPacket(msg, System.currentTimeMillis(), 0L, null, 0, new BitSet(), 0));
            }
            add(LogLine.Kind.SENT, "> " + msg);
            return true;
        } catch (Throwable t) {
            error("Blad wysylania: " + t);
            return false;
        }
    }
}
