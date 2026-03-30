package com.siguha.sigsacademyaddons.gui;

import com.siguha.sigsacademyaddons.feature.dex.DexDataManager;
import com.siguha.sigsacademyaddons.feature.dex.DexDataManager.DexDetail;
import com.siguha.sigsacademyaddons.feature.dex.DexDataManager.DexEntry;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FormattedCharSequence;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DexScreen extends Screen {

    private static final int LIST_LIMIT = 0;
    private static final int ROW_HEIGHT = 14;
    private static final int LIST_HEADER_HEIGHT = 12;
    private static final int DETAIL_SCROLL_STEP = 12;
    private static final int DETAIL_MAX_SCROLL = 2400;

    private static final int COLOR_BG = 0xCC111111;
    private static final int COLOR_PANEL = 0xAA0E0E0E;
    private static final int COLOR_PANEL_HEADER = 0xFF202020;
    private static final int COLOR_PANEL_BORDER = 0xFF5A5A5A;
    private static final int COLOR_TITLE = 0xFFFFAA00;
    private static final int COLOR_SECTION = 0xFF7FD6FF;
    private static final int COLOR_LABEL = 0xFFCCCCCC;
    private static final int COLOR_VALUE = 0xFFFFFFFF;
    private static final int COLOR_HINT = 0xFFAAAAAA;
    private static final int COLOR_ACCENT_OK = 0xFF55FF55;
    private static final int COLOR_ACCENT_WARN = 0xFFFF6666;
    private static final int COLOR_ROW_HOVER = 0x3355AAFF;
    private static final int COLOR_ROW_SELECTED = 0x6644CC44;

    private final DexDataManager dexDataManager;

    private EditBox searchBox;
    private List<DexEntry> searchResults = new ArrayList<>();
    private DexEntry selected;
    private DexDetail selectedDetail;
    private final Map<String, ResourceLocation> spriteCache = new HashMap<>();
    private final Set<String> spriteMissing = new HashSet<>();

    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int infoX;
    private int infoY;
    private int infoW;
    private int infoH;

    private int scrollOffset;
    private int detailScrollOffset;
    private int currentSpriteX = -1;
    private int currentSpriteY = -1;
    private int currentSpriteSize = 0;

    public DexScreen(DexDataManager dexDataManager) {
        super(Component.literal("SAA Dex"));
        this.dexDataManager = dexDataManager;
    }

    @Override
    protected void init() {
        super.init();

        int margin = 12;
        int top = 36;
        int contentH = this.height - top - margin;
        int gap = 8;

        listW = Math.max(210, (this.width - margin * 2 - gap) / 3);
        listH = contentH;
        listX = margin;
        listY = top;

        infoX = listX + listW + gap;
        infoY = top;
        infoW = this.width - infoX - margin;
        infoH = contentH;

        searchBox = new EditBox(this.font, margin + 70, 14, Math.min(300, this.width - (margin + 80)), 16,
                Component.literal("Search Pokemon"));
        searchBox.setMaxLength(64);
        searchBox.setResponder(this::onSearchChanged);
        searchBox.setValue("");
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        dexDataManager.ensureLoaded();
        refreshResults();
        detailScrollOffset = 0;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, this.width, this.height, COLOR_BG);

        graphics.drawString(this.font, "SAA Dex Explorer", 12, 6, COLOR_TITLE, true);
        graphics.drawString(this.font, "Search:", 12, 18, COLOR_LABEL, false);

        super.render(graphics, mouseX, mouseY, partialTick);

        drawPanel(graphics, listX, listY, listW, listH);
        drawPanel(graphics, infoX, infoY, infoW, infoH);

        renderResults(graphics, mouseX, mouseY);
        graphics.enableScissor(infoX + 2, infoY + 13, infoX + infoW - 2, infoY + infoH - 2);
        renderDetails(graphics);
        graphics.disableScissor();
        graphics.drawString(this.font, "Wheel: scroll details", infoX + infoW - 110, infoY + infoH - 11, COLOR_HINT, false);

        if (!dexDataManager.isReady()) {
            String err = dexDataManager.getLoadError();
            String msg = err == null ? "Loading Dex data..." : "Dex load failed: " + err;
            graphics.drawString(this.font, msg, listX + 6, listY + 6, 0xFFFF5555, false);
        }
    }

    private void renderResults(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = listY + 3;
        int availableRows = Math.max(1, (listH - 12 - LIST_HEADER_HEIGHT) / ROW_HEIGHT);
        int end = Math.min(searchResults.size(), scrollOffset + availableRows);

        graphics.drawString(this.font, "Pokemon", listX + 8, y, COLOR_SECTION, false);
        y += LIST_HEADER_HEIGHT;

        for (int i = scrollOffset; i < end; i++) {
            DexEntry entry = searchResults.get(i);
            int rowY = y + (i - scrollOffset) * ROW_HEIGHT;
            boolean hovered = isInside(mouseX, mouseY, listX + 4, rowY, listW - 8, ROW_HEIGHT);
            boolean isSelected = selected != null && selected.id().equals(entry.id());

            if (hovered) {
                graphics.fill(listX + 4, rowY, listX + listW - 4, rowY + ROW_HEIGHT, COLOR_ROW_HOVER);
            }
            if (isSelected) {
                graphics.fill(listX + 4, rowY, listX + listW - 4, rowY + ROW_HEIGHT, COLOR_ROW_SELECTED);
            }

            String right = entry.dexnum() > 0 ? "#" + entry.dexnum() : "";
            String left = entry.name();
            int rightW = this.font.width(right);
            String typeText = formatTypes(entry.primaryType(), entry.secondaryType()).toUpperCase(Locale.ROOT);
            int typeW = this.font.width(typeText);

            graphics.drawString(this.font, left, listX + 8, rowY + 3, COLOR_VALUE, false);
            graphics.drawString(this.font, typeText, listX + listW - rightW - typeW - 16, rowY + 3, 0xFF6EC2FF, false);
            graphics.drawString(this.font, right, listX + listW - 8 - rightW, rowY + 3, COLOR_HINT, false);
        }

        if (searchResults.isEmpty() && dexDataManager.isReady()) {
            graphics.drawString(this.font, "No Pokemon found.", listX + 8, listY + 8, COLOR_HINT, false);
        }

        String footer = searchResults.size() + " result(s)";
        int fw = this.font.width(footer);
        graphics.drawString(this.font, footer, listX + listW - fw - 6, listY + listH - 12, COLOR_HINT, false);
    }

    private void renderDetails(GuiGraphics graphics) {
        int x = infoX + 8;
        int y = infoY + 16 - detailScrollOffset;

        if (selected == null) {
            graphics.drawString(this.font, "Select a Pokemon from the list.", x, y, COLOR_HINT, false);
            y += 14;
            graphics.drawString(this.font, "Tip: search by name, id, or dex number.", x, y, COLOR_HINT, false);
            return;
        }

        DexDetail detail = selectedDetail;
        if (detail == null) {
            graphics.drawString(this.font, "No detailed data available for " + selected.name(), x, y, 0xFFFF5555, false);
            return;
        }

        int spriteSize = 96;
        int spriteX = infoX + infoW - spriteSize - 8;
        int spriteY = y;
        currentSpriteX = spriteX;
        currentSpriteY = spriteY;
        currentSpriteSize = spriteSize;
        renderSpriteFrame(graphics, spriteX, spriteY, spriteSize);
        renderPokemonSprite(graphics, detail, spriteX, spriteY, spriteSize);

        graphics.drawString(this.font, detail.name(), x, y, COLOR_TITLE, true);
        y += 12;
        y = drawLine(graphics, "Dex", detail.dexnum() > 0 ? "#" + detail.dexnum() : "Unknown", x, y);
        y = drawLine(graphics, "ID", detail.id(), x, y);
        y = drawLine(graphics, "Types", formatTypes(detail.primaryType(), detail.secondaryType()), x, y);

        if (detail.maleRatio() != null) {
            double malePct = detail.maleRatio() * 100.0;
            double femalePct = 100.0 - malePct;
            y = drawLine(graphics, "Gender", String.format(Locale.ROOT, "M %.1f%% / F %.1f%%", malePct, femalePct), x, y);
        }

        y += 4;
        y = drawSectionTitle(graphics, "Core", x, y);
        y = drawLine(graphics, "Abilities", joinLimited(detail.abilities(), 5), x, y);
        y = drawLine(graphics, "Egg Groups", joinLimited(detail.eggGroups(), 4), x, y);
        y = drawLine(graphics, "Catch Rate", detail.catchRate() >= 0 ? String.valueOf(detail.catchRate()) : "Unknown", x, y);
        y = drawLine(graphics, "XP Group", detail.experienceGroup().isEmpty() ? "Unknown" : detail.experienceGroup(), x, y);
        y = drawLine(graphics, "Moves", String.valueOf(detail.moveCount()), x, y);
        y = drawLine(graphics, "Spawn Entries", String.valueOf(detail.spawnPoolCount()), x, y);
        y = drawLine(graphics, "Implemented", detail.implemented() ? "Yes" : "No", x, y,
                detail.implemented() ? COLOR_ACCENT_OK : COLOR_ACCENT_WARN);

        y += 4;
        y = drawSectionTitle(graphics, "Base Stats", x, y);

        int total = 0;
        for (Map.Entry<String, Integer> stat : detail.baseStats().entrySet()) {
            total += stat.getValue();
            y = drawLine(graphics, stat.getKey(), String.valueOf(stat.getValue()), x + 4, y);
        }
        y = drawLine(graphics, "BST", String.valueOf(total), x + 4, y);

        y += 4;
        y = drawSectionTitle(graphics, "EV Yield", x, y);
        if (detail.evYield().isEmpty()) {
            y = drawLine(graphics, "EV", "None", x + 4, y, COLOR_HINT);
        } else {
            for (Map.Entry<String, Integer> ev : detail.evYield().entrySet()) {
                y = drawLine(graphics, ev.getKey(), "+" + ev.getValue(), x + 4, y, COLOR_ACCENT_OK);
            }
        }

        y += 4;
        y = drawSectionTitle(graphics, "Evolutions", x, y);
        if (detail.evolutions().isEmpty()) {
            graphics.drawString(this.font, "None", x + 4, y, COLOR_HINT, false);
            y += 10;
        } else {
            for (String evo : detail.evolutions()) {
                List<FormattedCharSequence> wrapped = this.font.split(Component.literal("- " + evo), infoW - 20);
                for (FormattedCharSequence line : wrapped) {
                    graphics.drawString(this.font, line, x + 4, y, COLOR_VALUE, false);
                    y += 10;
                    if (y > infoY + infoH - 14) {
                        return;
                    }
                }
            }
        }

        y += 4;
        y = drawSectionTitle(graphics, "Spawn Samples", x, y);
        if (detail.spawnSummaries().isEmpty()) {
            graphics.drawString(this.font, "No spawn data", x + 4, y, COLOR_HINT, false);
            return;
        }

        for (String spawn : detail.spawnSummaries()) {
            List<FormattedCharSequence> wrapped = this.font.split(Component.literal("- " + spawn), infoW - 20);
            for (FormattedCharSequence line : wrapped) {
                graphics.drawString(this.font, line, x + 4, y, 0xFFAEDBFF, false);
                y += 10;
                if (y > infoY + infoH - 14) {
                    return;
                }
            }
        }
    }

    private void renderSpriteFrame(GuiGraphics graphics, int x, int y, int size) {
        graphics.fill(x - 2, y - 2, x + size + 2, y + size + 2, 0xFF3E3E3E);
        graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF111111);
        graphics.fill(x, y, x + size, y + size, 0x66000000);
    }

    private void renderPokemonSprite(GuiGraphics graphics, DexDetail detail, int x, int y, int size) {
        ResourceLocation texture = getSpriteTexture(detail);
        if (texture == null) {
            String label = "No Sprite";
            int lw = this.font.width(label);
            graphics.drawString(this.font, label, x + (size - lw) / 2, y + size / 2 - 4, COLOR_HINT, false);
            return;
        }

        graphics.blit(texture,
                x, y, 0,
                0.0F, 0.0F,
                size, size,
                size, size);
    }

    private ResourceLocation getSpriteTexture(DexDetail detail) {
        if (detail == null || detail.id().isBlank()) {
            return null;
        }

        if (spriteCache.containsKey(detail.id())) {
            return spriteCache.get(detail.id());
        }
        if (spriteMissing.contains(detail.id())) {
            return null;
        }

        Minecraft mc = Minecraft.getInstance();
        for (String imagePath : detail.normalImagePaths()) {
            if (imagePath == null || imagePath.isBlank()) {
                continue;
            }

            ResourceLocation source = ResourceLocation.fromNamespaceAndPath("sigsacademyaddons", imagePath);
            Optional<Resource> resource = mc.getResourceManager().getResource(source);
            if (resource.isEmpty()) {
                continue;
            }

            try (InputStream stream = resource.get().open()) {
                NativeImage image = NativeImage.read(stream);
                DynamicTexture dynamicTexture = new DynamicTexture(image);
                ResourceLocation dynamicId = ResourceLocation.fromNamespaceAndPath(
                        "sigsacademyaddons", "dex_runtime/" + detail.id());
                mc.getTextureManager().register(dynamicId, dynamicTexture);
                spriteCache.put(detail.id(), dynamicId);
                return dynamicId;
            } catch (Exception ignored) {
                // Try next candidate path.
            }
        }

        spriteMissing.add(detail.id());
        return null;
    }

    private int drawLine(GuiGraphics graphics, String label, String value, int x, int y) {
        return drawLine(graphics, label, value, x, y, COLOR_VALUE);
    }

    private int drawLine(GuiGraphics graphics, String label, String value, int x, int y, int color) {
        int maxWidth = infoW - 16;
        String text = label + ": " + value;
        List<FormattedCharSequence> wrapped = this.font.split(Component.literal(text), maxWidth);
        for (FormattedCharSequence line : wrapped) {
            graphics.drawString(this.font, line, x, y, color, false);
            y += 10;
            if (y > infoY + infoH - 14) {
                break;
            }
        }
        return y;
    }

    private int drawSectionTitle(GuiGraphics graphics, String title, int x, int y) {
        graphics.drawString(this.font, title, x, y, COLOR_SECTION, false);
        int lineX = x + this.font.width(title) + 6;
        int lineEndX = infoX + infoW - 8;
        if (currentSpriteSize > 0) {
            int spriteTop = currentSpriteY - 2;
            int spriteBottom = currentSpriteY + currentSpriteSize + 2;
            if (y >= spriteTop && y <= spriteBottom) {
                lineEndX = Math.min(lineEndX, currentSpriteX - 8);
            }
        }
        if (lineEndX > lineX) {
            graphics.fill(lineX, y + 4, lineEndX, y + 5, 0xFF3A3A3A);
        }
        return y + 12;
    }

    private void onSearchChanged(String text) {
        refreshResults();
    }

    private void refreshResults() {
        searchResults = dexDataManager.search(searchBox == null ? "" : searchBox.getValue(), LIST_LIMIT);
        scrollOffset = 0;
        detailScrollOffset = 0;

        if (searchResults.isEmpty()) {
            selected = null;
            selectedDetail = null;
            return;
        }

        if (selected == null || searchResults.stream().noneMatch(e -> e.id().equals(selected.id()))) {
            select(searchResults.getFirst());
        } else {
            selectedDetail = dexDataManager.getDetail(selected.id());
        }
    }

    private void select(DexEntry entry) {
        selected = entry;
        selectedDetail = dexDataManager.getDetail(entry.id());
        detailScrollOffset = 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button != 0) {
            return false;
        }

        int rowsStartY = listY + 3 + LIST_HEADER_HEIGHT;
        if (!isInside(mouseX, mouseY, listX + 4, rowsStartY, listW - 8, listH - (rowsStartY - listY) - 4)) {
            return false;
        }

        int relY = (int) mouseY - rowsStartY;
        if (relY < 0) {
            return false;
        }

        int row = relY / ROW_HEIGHT;
        int idx = scrollOffset + row;
        if (idx >= 0 && idx < searchResults.size()) {
            select(searchResults.get(idx));
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isInside(mouseX, mouseY, infoX, infoY, infoW, infoH)) {
            if (verticalAmount < 0) {
                detailScrollOffset = Math.min(DETAIL_MAX_SCROLL, detailScrollOffset + DETAIL_SCROLL_STEP);
            } else if (verticalAmount > 0) {
                detailScrollOffset = Math.max(0, detailScrollOffset - DETAIL_SCROLL_STEP);
            }
            return true;
        }

        if (!isInside(mouseX, mouseY, listX, listY, listW, listH)) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        int availableRows = Math.max(1, (listH - 12 - LIST_HEADER_HEIGHT) / ROW_HEIGHT);
        int maxOffset = Math.max(0, searchResults.size() - availableRows);

        if (verticalAmount < 0) {
            scrollOffset = Math.min(maxOffset, scrollOffset + 1);
        } else if (verticalAmount > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        }

        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, COLOR_PANEL);
        graphics.fill(x, y, x + w, y + 12, COLOR_PANEL_HEADER);
        graphics.fill(x, y, x + w, y + 1, COLOR_PANEL_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_PANEL_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_PANEL_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_PANEL_BORDER);
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
    }

    private static String formatTypes(String primary, String secondary) {
        if (secondary == null || secondary.isBlank()) {
            return primary;
        }
        return primary + " / " + secondary;
    }

    private static String joinLimited(List<String> values, int maxCount) {
        if (values == null || values.isEmpty()) {
            return "None";
        }

        List<String> formatted = new ArrayList<>();
        int limit = Math.min(values.size(), maxCount);
        for (int i = 0; i < limit; i++) {
            formatted.add(values.get(i));
        }

        String joined = String.join(", ", formatted);
        if (values.size() > maxCount) {
            return joined + " ...";
        }
        return joined;
    }
}
