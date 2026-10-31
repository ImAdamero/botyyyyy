package pl.easybot.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import pl.easybot.Config;
import pl.easybot.bot.Bot;
import pl.easybot.bot.BotManager;
import pl.easybot.bot.LogLine;
import pl.easybot.util.LogView;
import pl.easybot.util.Ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Ogolna konsola bledow (klawisz B): bledy wszystkich botow razem - czemu nie moga wejsc itd. */
public class ErrorsScreen extends Screen {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static long clearedAt = 0;

    private final Screen parent;
    private final LogView view = new LogView();

    public ErrorsScreen(Screen parent) {
        super(Text.literal("Błędy botów"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int margin = 10;
        int w = width - margin * 2;
        int bw = (w - 2 * 6) / 3;
        int by = height - 26;
        addDrawableChild(ButtonWidget.builder(Text.literal("Wstecz"), b -> close())
                .dimensions(margin, by, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Wyczyść błędy"), b -> {
            clearedAt = System.currentTimeMillis();
            for (int i = 0; i < Config.MAX_BOTS; i++) {
                Bot bot = BotManager.get(i);
                if (bot != null) {
                    bot.clearErrors();
                }
            }
            view.toEnd();
        }).dimensions(margin + bw + 6, by, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Menu botów (X)"), b -> client.setScreen(new BotScreen(parent)))
                .dimensions(margin + 2 * (bw + 6), by, bw, 20).build());
    }

    private record Entry(long time, String nick, String text) {
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // tlo rysowane w render()
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, Ui.BG);

        List<Entry> all = new ArrayList<>();
        int online = 0;
        int queued = 0;
        int failed = 0;
        for (int i = 0; i < Config.MAX_BOTS; i++) {
            Bot b = BotManager.get(i);
            if (b == null) {
                continue;
            }
            switch (b.state()) {
                case ONLINE -> online++;
                case QUEUED, CONNECTING -> queued++;
                case OFFLINE, FAILED -> failed++;
                default -> {
                }
            }
            String nick = b.nick().isEmpty() ? "bot " + (i + 1) : b.nick();
            for (LogLine l : new ArrayList<>(b.lines())) {
                if (l.kind() == LogLine.Kind.ERROR && l.time() > clearedAt) {
                    all.add(new Entry(l.time(), nick, l.text()));
                }
            }
        }
        all.sort(Comparator.comparingLong(Entry::time));

        ctx.drawTextWithShadow(textRenderer, "Błędy wszystkich botów", 10, 10, Ui.RED);
        String summary = "online: " + online + "   w kolejce/łączy: " + queued + "   nie działa: " + failed
                + "   błędów: " + all.size();
        ctx.drawTextWithShadow(textRenderer, summary, width - 10 - textRenderer.getWidth(summary), 10, Ui.GRAY);

        List<Text> entries = new ArrayList<>();
        for (Entry e : all) {
            entries.add(Text.literal(TIME.format(Instant.ofEpochMilli(e.time())) + " ").formatted(Formatting.DARK_GRAY)
                    .append(Text.literal("[" + e.nick() + "] ").formatted(Formatting.AQUA))
                    .append(Text.literal(e.text()).formatted(Formatting.RED)));
        }
        if (entries.isEmpty()) {
            entries.add(Text.literal("Brak błędów - żaden bot nie zgłosił problemu.").formatted(Formatting.GREEN));
        }
        view.render(ctx, textRenderer, 10, 26, width - 20, height - 26 - 34, entries);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyInput in) {
        int key = in.key();
        int page = view.pageSize(height - 60);
        switch (key) {
            case GLFW.GLFW_KEY_PAGE_UP -> {
                view.scrollBy(page);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                view.scrollBy(-page);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                view.toStart();
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                view.toEnd();
                return true;
            }
            default -> {
            }
        }
        return super.keyPressed(in);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        view.scrollBy((int) Math.signum(verticalAmount) * 3);
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
