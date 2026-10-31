package pl.easybot.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import pl.easybot.bot.Bot;
import pl.easybot.bot.BotManager;
import pl.easybot.bot.LogLine;
import pl.easybot.util.LogView;
import pl.easybot.util.Ui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Osobna konsola jednego bota: wszystko, co widzi (czat graczy, komunikaty serwera), plus wysylanie. */
public class ConsoleScreen extends Screen {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Screen parent;
    private final int index;
    private final LogView view = new LogView();

    private TextFieldWidget input;
    private ButtonWidget filterButton;
    private boolean errorsOnly = false;
    private long clearedAt = 0;

    public ConsoleScreen(Screen parent, int index) {
        super(Text.literal("Konsola bota " + (index + 1)));
        this.parent = parent;
        this.index = index;
    }

    private Bot bot() {
        return BotManager.get(index);
    }

    @Override
    protected void init() {
        int margin = 10;
        int w = width - margin * 2;

        input = new TextFieldWidget(textRenderer, margin, height - 50, w, 18, Text.empty());
        input.setMaxLength(256);
        input.setPlaceholder(Text.literal("Czat lub /komenda jako ten bot (Enter = wyślij)"));
        addDrawableChild(input);
        setInitialFocus(input);

        int bw = (w - 3 * 6) / 4;
        int by = height - 26;
        addDrawableChild(ButtonWidget.builder(Text.literal("Wstecz"), b -> close())
                .dimensions(margin, by, bw, 20).build());
        filterButton = addDrawableChild(ButtonWidget.builder(filterLabel(), b -> {
            errorsOnly = !errorsOnly;
            filterButton.setMessage(filterLabel());
            view.toEnd();
        }).dimensions(margin + (bw + 6), by, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Wyczyść widok"), b -> {
            clearedAt = System.currentTimeMillis();
            view.toEnd();
        }).dimensions(margin + 2 * (bw + 6), by, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Zatrzymaj bota"), b -> BotManager.stop(index))
                .dimensions(margin + 3 * (bw + 6), by, bw, 20).build());
    }

    private Text filterLabel() {
        return Text.literal(errorsOnly ? "Pokazuj: tylko błędy" : "Pokazuj: wszystko");
    }

    private static int color(LogLine.Kind kind) {
        return switch (kind) {
            case ERROR -> 0xFF5555;
            case SENT -> 0x55FF55;
            case AUTH -> 0xFFD25A;
            case INFO -> 0x8FA3FF;
            default -> 0xFFFFFF;
        };
    }

    static Text format(LogLine l, String prefix) {
        return Text.literal(TIME.format(java.time.Instant.ofEpochMilli(l.time())) + " ").formatted(Formatting.DARK_GRAY)
                .append(Text.literal(prefix + l.text()).styled(s -> s.withColor(color(l.kind()))));
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // tlo rysowane w render()
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, Ui.BG);

        Bot b = bot();
        String nick = b == null || b.nick().isEmpty() ? "bot " + (index + 1) : b.nick();
        ctx.drawTextWithShadow(textRenderer, "Konsola: " + nick, 10, 10, Ui.ACCENT);
        String state = b == null ? "nie uruchomiony" : switch (b.state()) {
            case IDLE -> "nieaktywny";
            case QUEUED -> "w kolejce (start za " + Math.max(0, (b.queuedAt() - System.currentTimeMillis() + 999) / 1000) + " s)";
            case CONNECTING -> "łączenie...";
            case ONLINE -> "online " + Ui.duration(System.currentTimeMillis() - b.onlineSince());
            case OFFLINE -> "rozłączony";
            case FAILED -> "nie wszedł";
        };
        int stateColor = b == null ? Ui.DIM : switch (b.state()) {
            case ONLINE -> Ui.GREEN;
            case QUEUED -> Ui.YELLOW;
            case CONNECTING -> Ui.ORANGE;
            case IDLE -> Ui.DIM;
            default -> Ui.RED;
        };
        ctx.drawTextWithShadow(textRenderer, state, width - 10 - textRenderer.getWidth(state), 10, stateColor);

        List<Text> entries = new ArrayList<>();
        if (b != null) {
            for (LogLine l : new ArrayList<>(b.lines())) {
                if (l.time() <= clearedAt) {
                    continue;
                }
                if (errorsOnly && l.kind() != LogLine.Kind.ERROR) {
                    continue;
                }
                entries.add(format(l, ""));
            }
        }
        if (entries.isEmpty()) {
            entries.add(Text.literal(b == null ? "Ten bot jeszcze nie był uruchomiony - kliknij Start w menu (X)."
                    : "Brak wpisów.").formatted(Formatting.GRAY));
        }
        view.render(ctx, textRenderer, 10, 26, width - 20, height - 26 - 56, entries);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void send() {
        String text = input.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        Bot b = bot();
        if (b == null) {
            return;
        }
        if (b.send(text)) {
            input.setText("");
            view.toEnd();
        }
    }

    @Override
    public boolean keyPressed(KeyInput in) {
        int key = in.key();
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && input.isFocused()) {
            send();
            return true;
        }
        int page = view.pageSize(height - 82);
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
                if (!input.isFocused() || input.getText().isEmpty()) {
                    view.toStart();
                    return true;
                }
            }
            case GLFW.GLFW_KEY_END -> {
                if (!input.isFocused() || input.getText().isEmpty()) {
                    view.toEnd();
                    return true;
                }
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
