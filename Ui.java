package pl.easybot.util;

import net.minecraft.client.gui.DrawContext;

/** Wspolne kolory i male pomocnicze funkcje rysowania. */
public final class Ui {
    private Ui() {
    }

    public static final int BG = 0xF010111A;
    public static final int PANEL = 0xFF181A26;
    public static final int PANEL_LIGHT = 0xFF21243A;
    public static final int BORDER = 0xFF343A5C;
    public static final int ACCENT = 0xFF6C8CFF;
    public static final int LOG_BG = 0xFF0C0D14;

    public static final int WHITE = 0xFFFFFFFF;
    public static final int GRAY = 0xFFA0A4B8;
    public static final int DIM = 0xFF6B6F85;
    public static final int GREEN = 0xFF58E07A;
    public static final int YELLOW = 0xFFFFD25A;
    public static final int ORANGE = 0xFFFF9F43;
    public static final int RED = 0xFFFF5C5C;

    public static void panel(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, PANEL);
        ctx.drawBorder(x, y, w, h, BORDER);
    }

    public static String duration(long millis) {
        long s = Math.max(0, millis / 1000);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%02d:%02d", m, sec);
    }
}
