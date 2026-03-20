package com.siguha.sigsacademyaddons.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class HudTextUtil {

    public static List<String> wrapText(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (maxWidth <= 0 || text == null || text.isEmpty()) {
            if (text != null && !text.isEmpty()) lines.add(text);
            return lines;
        }

        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            if (current.isEmpty()) {
                current.append(word);
            } else {
                String test = current + " " + word;
                if (font.width(test) > maxWidth) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current.append(" ").append(word);
                }
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    public static int renderWrappedCentered(GuiGraphics graphics, Font font, String text,
                                             int panelWidth, int y, int color, int lineHeight) {
        int maxTextWidth = panelWidth - 8;
        if (font.width(text) <= maxTextWidth) {
            int textWidth = font.width(text);
            graphics.drawString(font, text, (panelWidth - textWidth) / 2, y, color, true);
            return y + lineHeight;
        }
        List<String> lines = wrapText(font, text, maxTextWidth);
        for (String line : lines) {
            int lineWidth = font.width(line);
            graphics.drawString(font, line, (panelWidth - lineWidth) / 2, y, color, true);
            y += lineHeight;
        }
        return y;
    }

    public static int wrappedCenteredHeight(Font font, String text, int panelWidth, int lineHeight) {
        int maxTextWidth = panelWidth - 8;
        if (font.width(text) <= maxTextWidth) {
            return lineHeight;
        }
        return wrapText(font, text, maxTextWidth).size() * lineHeight;
    }

    public static int renderStatLine(GuiGraphics graphics, Font font,
                                      String name, Component value,
                                      int nameColor, int valueColor,
                                      int y, int panelWidth, int padding, int lineHeight) {
        int available = panelWidth - padding * 2 - 2;
        int nameWidth = font.width(name);
        int valueWidth = font.width(value);

        if (nameWidth + 8 + valueWidth <= available) {
            graphics.drawString(font, name, padding + 2, y, nameColor, true);
            graphics.drawString(font, value, panelWidth - padding - valueWidth, y, valueColor, true);
            return y + lineHeight;
        } else {
            graphics.drawString(font, name, padding + 2, y, nameColor, true);
            y += lineHeight;
            graphics.drawString(font, value, panelWidth - padding - valueWidth, y, valueColor, true);
            return y + lineHeight;
        }
    }

    public static int statLineHeight(Font font, String name, Component value,
                                      int panelWidth, int padding, int lineHeight) {
        int available = panelWidth - padding * 2 - 2;
        int nameWidth = font.width(name);
        int valueWidth = font.width(value);
        if (nameWidth + 8 + valueWidth <= available) {
            return lineHeight;
        }
        return lineHeight * 2;
    }
}
