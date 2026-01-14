package pro.kaleert.uwubot.util;

public class TextNormalizer {
    public static String normalizeGroup(String input) {
        if (input == null) return null;
        return input.trim().toUpperCase()
                .replace('A', 'А')
                .replace('B', 'В')
                .replace('E', 'Е')
                .replace('K', 'К')
                .replace('M', 'М')
                .replace('H', 'Н')
                .replace('O', 'О')
                .replace('P', 'Р')
                .replace('C', 'С')
                .replace('T', 'Т')
                .replace('X', 'Х')
                .replace('Y', 'У')
                .replace(" ", "-");
    }
}
