package pl.easybot.util;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Przewijany podglad logow z zawijaniem linii. 0 = podglad idzie za najnowszymi liniami. */
public final class LogView {
    private static final int LINE_H = 10;
    private static final int MAX_ENTRIES = 400;

    private int scroll = 0;
    private int lastTotal = -1;

    public void scrollBy(int lines) {
        scroll = Math.max(0, scroll + lines);
    }

    public void toEnd() {
        scroll = 0;
    }

    public void toStart() {
        scroll = Integer.MAX_VALUE / 2;
    }

    public int pageSize(int height) {
        return Math.max(1, (height - 8) / LINE_H - 1);
    }

    public void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, List<Text> entries) {
        ctx.fill(x, y, x + w, y + h, Ui.LOG_BG);
        ctx.drawBorder(x, y, w, h, Ui.BORDER);

        int innerW = Math.max(40, w - 16);
        List<OrderedText> rows = new ArrayList<>();
        int from = Math.max(0, entries.size() - MAX_ENTRIES);
        for (int i = from; i < entries.size(); i++) {
            rows.addAll(tr.wrapLines(entries.get(i), innerW));
        }

        int visible = Math.max(1, (h - 8) / LINE_H);
        int total = rows.size();
        if (lastTotal >= 0 && scroll > 0 && total > lastTotal) {
            scroll += total - lastTotal; // nie "uciekaj" gdy przychodza nowe linie
        }
        lastTotal = total;
        int maxScroll = Math.max(0, total - visible);
        scroll = Math.min(Math.max(scroll, 0), maxScroll);

        int end = total - scroll;
        int start = Math.max(0, end - visible);
        int ty = y + 5;
        for (int i = start; i < end; i++) {
            ctx.drawTextWithShadow(tr, rows.get(i), x + 6, ty, Ui.WHITE);
            ty += LINE_H;
        }

        if (total > visible) {
            int barH = Math.max(12, (int) ((long) (h - 6) * visible / total));
            int track = h - 6 - barH;
            int barY = y + 3 + (maxScroll == 0 ? track : (int) ((long) track * (maxScroll - scroll) / maxScroll));
            ctx.fill(x + w - 5, barY, x + w - 2, barY + barH, scroll > 0 ? Ui.ACCENT : Ui.DIM);
        }
        if (scroll > 0) {
            String hint = "\u2193 " + scroll;
            ctx.drawTextWithShadow(tr, hint, x + w - 12 - tr.getWidth(hint), y + h - 12, Ui.YELLOW);
        }
    }
}
