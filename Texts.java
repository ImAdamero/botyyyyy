package pl.easybot.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.minecraft.client.resource.language.I18n;

/** Zamiana komponentow tekstu serwera na zwykly tekst. */
public final class Texts {
    private Texts() {
    }

    public static String plain(Component component) {
        if (component == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        append(sb, component);
        return clean(sb.toString());
    }

    private static void append(StringBuilder sb, Component c) {
        if (c instanceof TextComponent t) {
            sb.append(t.content());
        } else if (c instanceof TranslatableComponent tr) {
            String key = tr.key();
            String translated;
            try {
                translated = I18n.translate(key);
            } catch (Throwable e) {
                translated = key;
            }
            sb.append(translated);
        }
        for (Component child : c.children()) {
            append(sb, child);
        }
    }

    public static String clean(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "").replace('\n', ' ').replace('\r', ' ').trim();
    }
}
