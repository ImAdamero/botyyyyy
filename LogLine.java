package pl.easybot.bot;

/** Jedna linia w konsoli bota. */
public record LogLine(long time, Kind kind, String text) {
    public enum Kind {
        /** Informacje od EasyBot (laczenie, start, stop). */
        INFO,
        /** Czat i wiadomosci serwera. */
        CHAT,
        /** To, co bot sam wyslal (czat/komendy). */
        SENT,
        /** Automatyczna rejestracja / logowanie. */
        AUTH,
        /** Bledy: rozlaczenia, odrzucenia, bledy serwera. */
        ERROR
    }
}
