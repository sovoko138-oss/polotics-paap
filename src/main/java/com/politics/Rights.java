package com.politics;

/** Rights a trusted player can have inside a country. Combined as bit flags. */
public class Rights {
    public static final int BREAK = 1;
    public static final int PLACE = 2;
    public static final int DOORS = 4;
    public static final int BUTTONS = 8;
    public static final int SUMMON = 16;
    public static final int FULL = BREAK | PLACE | DOORS | BUTTONS | SUMMON;

    public static String actionText(int right) {
        return switch (right) {
            case BREAK -> "ломать блоки";
            case PLACE -> "ставить блоки";
            case DOORS -> "открывать двери и люки";
            case BUTTONS -> "нажимать на кнопки и плиты";
            case SUMMON -> "призывать сущностей";
            default -> "делать это";
        };
    }
}
