package pl.easybot.screen;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import pl.easybot.Config;
import pl.easybot.bot.Bot;
import pl.easybot.bot.BotManager;
import pl.easybot.util.Ui;

import java.util.ArrayList;
import java.util.List;

/** Glowne menu (klawisz X): adres, liczba botow, nicki, start/stop i lista botow. */
public class BotScreen extends Screen {
    private static final int ROW_H = 22;

    private final Screen parent;

    private TextFieldWidget addressField;
    private TextFieldWidget countField;
    private TextFieldWidget delayField;
    private TextFieldWidget passwordField;
    private TextFieldWidget postField;
    private TextFieldWidget chatField;
    private final List<TextFieldWidget> nickFields = new ArrayList<>();

    private int rowScroll = 0;
    private String message = "";
    private int messageColor = Ui.GRAY;

    private int px;
    private int pw;
    private int listTop;
    private int listBottom;

    public BotScreen(Screen parent) {
        super(Text.literal("EasyBot"));
        this.parent = parent;
    }

    // ------------------------------------------------------------ budowa

    @Override
    protected void init() {
        Config cfg = Config.get();
        pw = Math.min(width - 16, 560);
        px = (width - pw) / 2;
        int inner = px + 10;
        int innerW = pw - 20;

        nickFields.clear();

        // wiersz 1: adres | ilosc | odstep
        int countW = 44;
        int delayW = 56;
        int addrW = innerW - countW - delayW - 12;
        int y1 = 50;
        addressField = field(inner, y1, addrW, 100, "np. mc.serwer.pl:25565");
        addressField.setText(cfg.address);
        countField = field(inner + addrW + 6, y1, countW, 2, "1");
        countField.setTextPredicate(s -> s.matches("\\d{0,2}"));
        countField.setText(String.valueOf(cfg.count));
        delayField = field(inner + addrW + countW + 12, y1, delayW, 4, "18");
        delayField.setTextPredicate(s -> s.matches("\\d{0,4}"));
        delayField.setText(String.valueOf(cfg.joinDelaySec));

        // wiersz 2: haslo | komenda po zalogowaniu
        int halfW = (innerW - 6) / 2;
        int y2 = 90;
        passwordField = field(inner, y2, halfW, 40, "eloelo");
        passwordField.setText(cfg.password);
        postField = field(inner + halfW + 6, y2, innerW - halfW - 6, 60, "np. /warp afk");
        postField.setText(cfg.postLoginCommand);

        // przyciski
        int by = 118;
        int bw = (innerW - 3 * 6) / 4;
        addDrawableChild(ButtonWidget.builder(Text.literal("Start"), b -> onStart())
                .dimensions(inner, by, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Stop wszystkich"), b -> {
            BotManager.stopAll();
            setMessage("Zatrzymano wszystkie boty.", Ui.YELLOW);
        }).dimensions(inner + (bw + 6), by, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Błędy (B)"), b -> {
            saveFields();
            client.setScreen(new ErrorsScreen(this));
        }).dimensions(inner + 2 * (bw + 6), by, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Wypełnij nicki"), b -> fillNicks())
                .dimensions(inner + 3 * (bw + 6), by, bw, 20).build());

        listTop = 160;
        listBottom = Math.max(listTop + ROW_H * 2 + 4, height - 66);

        // pola nickow (pozycje ustawiane co klatke w layoutRows)
        for (int i = 0; i < Config.MAX_BOTS; i++) {
            final int idx = i;
            TextFieldWidget f = new TextFieldWidget(textRenderer, inner + 32, listTop, 150, 18, Text.empty());
            f.setMaxLength(16);
            f.setPlaceholder(Text.literal("nick bota " + (i + 1)));
            f.setText(cfg.nicks[i]);
            f.setChangedListener(s -> Config.get().nicks[idx] = s);
            f.setVisible(false);
            addDrawableChild(f);
            nickFields.add(f);
        }

        // czat na dole
        chatField = new TextFieldWidget(textRenderer, inner, height - 52, innerW, 18, Text.empty());
        chatField.setMaxLength(256);
        chatField.setPlaceholder(Text.literal("Czat lub /komenda dla wybranego bota (Enter = wyślij)"));
        addDrawableChild(chatField);

        int cw = (innerW - 2 * 6) / 3;
        int cy = height - 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("Wyślij do wybranego"), b -> sendSelected())
                .dimensions(inner, cy, cw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Wyślij do wszystkich"), b -> sendAll())
                .dimensions(inner + cw + 6, cy, cw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Zamknij"), b -> close())
                .dimensions(inner + 2 * (cw + 6), cy, cw, 20).build());
    }

    private TextFieldWidget field(int x, int y, int w, int maxLen, String placeholder) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 18, Text.empty());
        f.setMaxLength(maxLen);
        f.setPlaceholder(Text.literal(placeholder));
        addDrawableChild(f);
        return f;
    }

    // ------------------------------------------------------------ akcje

    private int currentCount() {
        try {
            int n = Integer.parseInt(countField.getText().trim());
            return Math.max(1, Math.min(Config.MAX_BOTS, n));
        } catch (NumberFormatException e) {
            return Config.get().count;
        }
    }

    private void saveFields() {
        Config cfg = Config.get();
        cfg.address = addressField.getText().trim();
        cfg.count = currentCount();
        try {
            cfg.joinDelaySec = Math.max(1, Integer.parseInt(delayField.getText().trim()));
        } catch (NumberFormatException ignored) {
        }
        cfg.password = passwordField.getText().isBlank() ? "eloelo" : passwordField.getText().trim();
        cfg.postLoginCommand = postField.getText().trim();
        Config.save();
    }

    private void onStart() {
        saveFields();
        String err = BotManager.startAll();
        if (err != null) {
            setMessage(err, Ui.RED);
        } else {
            setMessage("Start: " + BotManager.lastStarted + " bot(ów) w kolejce, pierwszy od razu, kolejne co "
                    + Config.get().joinDelaySec + " s.", Ui.GREEN);
        }
    }

    private void fillNicks() {
        String base = nickFields.get(0).getText().trim();
        if (base.isEmpty()) {
            setMessage("Wpisz nick pierwszego bota - reszta zostanie uzupełniona (nick2, nick3...).", Ui.YELLOW);
            return;
        }
        int count = currentCount();
        for (int i = 1; i < count; i++) {
            String suffix = String.valueOf(i + 1);
            String b = base.length() + suffix.length() > 16 ? base.substring(0, 16 - suffix.length()) : base;
            nickFields.get(i).setText(b + suffix);
        }
        setMessage("Uzupełniono nicki dla " + count + " botów.", Ui.GREEN);
    }

    private void sendSelected() {
        String text = chatField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        Bot b = BotManager.get(BotManager.selected);
        if (b == null || b.state() != Bot.State.ONLINE) {
            setMessage("Wybrany bot (#" + (BotManager.selected + 1) + ") nie jest online.", Ui.RED);
            return;
        }
        if (b.send(text)) {
            chatField.setText("");
        }
    }

    private void sendAll() {
        String text = chatField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        BotManager.broadcast(text);
        setMessage("Wysłano do wszystkich botów online (co 0,6 s).", Ui.GREEN);
        chatField.setText("");
    }

    private void setMessage(String text, int color) {
        message = text;
        messageColor = color;
    }

    // ------------------------------------------------------------ wiersze

    private int visibleRows() {
        return Math.max(1, (listBottom - listTop - 4) / ROW_H);
    }

    private void layoutRows() {
        int count = currentCount();
        int visible = visibleRows();
        rowScroll = Math.max(0, Math.min(rowScroll, Math.max(0, count - visible)));
        for (int i = 0; i < nickFields.size(); i++) {
            TextFieldWidget f = nickFields.get(i);
            int row = i - rowScroll;
            boolean show = i < count && row >= 0 && row < visible;
            f.setVisible(show);
            if (show) {
                f.setX(px + 10 + 32);
                f.setY(listTop + 2 + row * ROW_H + 2);
                Bot b = BotManager.get(i);
                f.setEditable(b == null || !b.isActive());
            }
        }
    }

    private int rowAt(double x, double y) {
        if (x < px + 6 || x > px + pw - 6 || y < listTop + 2 || y >= listBottom - 2) {
            return -1;
        }
        int row = (int) ((y - listTop - 2) / ROW_H);
        int idx = rowScroll + row;
        return row < visibleRows() && idx < currentCount() ? idx : -1;
    }

    // ------------------------------------------------------------ rysowanie

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // wlasne tlo rysujemy w render() - tutaj celowo nic (bez rozmycia)
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, Ui.BG);
        layoutRows();

        Ui.panel(ctx, px, 8, pw, height - 12);
        ctx.fill(px + 1, 9, px + pw - 1, 30, Ui.PANEL_LIGHT);
        ctx.drawTextWithShadow(textRenderer, "EasyBot", px + 10, 15, Ui.ACCENT);
        ctx.drawTextWithShadow(textRenderer, "1.21.11  |  X = menu, B = błędy", px + 58, 15, Ui.DIM);

        int online = 0;
        int errors = 0;
        for (int i = 0; i < Config.MAX_BOTS; i++) {
            Bot b = BotManager.get(i);
            if (b != null) {
                if (b.state() == Bot.State.ONLINE) {
                    online++;
                }
                errors += b.errorCount();
            }
        }
        String summary = "online: " + online + "   błędy: " + errors;
        ctx.drawTextWithShadow(textRenderer, summary, px + pw - 10 - textRenderer.getWidth(summary), 15,
                errors > 0 ? Ui.ORANGE : Ui.GRAY);

        int inner = px + 10;
        ctx.drawTextWithShadow(textRenderer, "Adres serwera", inner, 40, Ui.GRAY);
        ctx.drawTextWithShadow(textRenderer, "Ilość", countField.getX(), 40, Ui.GRAY);
        ctx.drawTextWithShadow(textRenderer, "Odstęp s", delayField.getX(), 40, Ui.GRAY);
        ctx.drawTextWithShadow(textRenderer, "Hasło (auto /register /login)", inner, 80, Ui.GRAY);
        ctx.drawTextWithShadow(textRenderer, "Komenda po zalogowaniu", postField.getX(), 80, Ui.GRAY);

        if (!message.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, message, inner, 143, messageColor);
        }

        // lista botow
        ctx.fill(px + 6, listTop, px + pw - 6, listBottom, Ui.LOG_BG);
        ctx.drawBorder(px + 6, listTop, pw - 12, listBottom - listTop, Ui.BORDER);

        int count = currentCount();
        int visible = visibleRows();
        long now = System.currentTimeMillis();
        for (int i = rowScroll; i < Math.min(count, rowScroll + visible); i++) {
            int y = listTop + 2 + (i - rowScroll) * ROW_H;
            boolean hover = rowAt(mouseX, mouseY) == i;
            boolean sel = BotManager.selected == i;
            if (sel) {
                ctx.fill(px + 8, y, px + pw - 8, y + ROW_H - 1, 0x553A4A8A);
            } else if (hover) {
                ctx.fill(px + 8, y, px + pw - 8, y + ROW_H - 1, 0x33FFFFFF);
            }
            ctx.drawTextWithShadow(textRenderer, "#" + (i + 1), inner + 4, y + 7, sel ? Ui.ACCENT : Ui.DIM);

            Bot b = BotManager.get(i);
            int sx = inner + 32 + 150 + 10;
            String status;
            int color;
            if (b == null || b.state() == Bot.State.IDLE) {
                status = "nieaktywny";
                color = Ui.DIM;
            } else {
                switch (b.state()) {
                    case QUEUED -> {
                        status = "w kolejce, start za " + Math.max(0, (b.queuedAt() - now + 999) / 1000) + " s";
                        color = Ui.YELLOW;
                    }
                    case CONNECTING -> {
                        status = "łączenie...";
                        color = Ui.ORANGE;
                    }
                    case ONLINE -> {
                        status = "online  " + Ui.duration(now - b.onlineSince());
                        color = Ui.GREEN;
                    }
                    case OFFLINE -> {
                        status = "rozłączony";
                        color = Ui.RED;
                    }
                    default -> {
                        status = "nie wszedł (zobacz błędy)";
                        color = Ui.RED;
                    }
                }
                if (b.errorCount() > 0) {
                    String e = "! " + b.errorCount();
                    ctx.drawTextWithShadow(textRenderer, e, px + pw - 14 - textRenderer.getWidth(e), y + 7, Ui.RED);
                }
            }
            ctx.drawTextWithShadow(textRenderer, status, sx, y + 7, color);
        }
        if (count > visible) {
            String more = (rowScroll + 1) + "-" + Math.min(count, rowScroll + visible) + " z " + count
                    + "  (kółko myszy = przewijanie)";
            ctx.drawTextWithShadow(textRenderer, more, px + 10, listBottom + 4, Ui.DIM);
        } else {
            ctx.drawTextWithShadow(textRenderer, "Dwuklik w wiersz bota = jego osobna konsola",
                    px + 10, listBottom + 4, Ui.DIM);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    // ------------------------------------------------------------ wejscie

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            int row = rowAt(click.x(), click.y());
            if (row >= 0) {
                BotManager.selected = row;
                if (doubled) {
                    saveFields();
                    client.setScreen(new ConsoleScreen(this, row));
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseY >= listTop && mouseY <= listBottom) {
            rowScroll -= (int) Math.signum(verticalAmount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && chatField.isFocused()) {
            sendSelected();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void removed() {
        saveFields();
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
