package com.siguha.sigsacademyaddons.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public class HudTextUtil {

    public static List<String> wrapText(Font font, Component text, int maxWidth) {
        List<String> lines = new ArrayList<>();

        if (font == null || text == null || maxWidth <= 0 || text.getString().isEmpty()) {
            return lines;
        }

        List<FormattedCharSequence> wrapped = font.split(text, maxWidth);

        for (FormattedCharSequence seq : wrapped) {
            lines.add(seq.toString());
        }

        return lines;
    }

    public static int renderWrappedCentered(GuiGraphics graphics, Font font, Component text, int panelWidth, int y, int color, int lineHeight) {
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

    public static int wrappedCenteredHeight(Font font, Component text, int panelWidth, int lineHeight) {
        int maxTextWidth = panelWidth - 8;
        if (font.width(text) <= maxTextWidth) {
            return lineHeight;
        }
        return wrapText(font, text, maxTextWidth).size() * lineHeight;
    }

    public static int renderStatLine(GuiGraphics graphics, Font font,
                                      Component name, Component value,
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

    public static int statLineHeight(Font font, Component name, Component value,
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
