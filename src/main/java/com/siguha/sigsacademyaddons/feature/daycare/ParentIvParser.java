package com.siguha.sigsacademyaddons.feature.daycare;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParentIvParser {

    private static final Pattern IV_SUMMARY_PATTERN =
            Pattern.compile("IVs:\\s*(\\d+)/\\d+\\s*\\((\\d+)%\\)");

    // Thai Unicode glyphs used as stat icons: โ=HP, เ=ATK, แ=DEF, ใ=SP.ATK, ไ=SP.DEF, ๅ=SPE
    private static final Pattern IV_DETAIL_PATTERN =
            Pattern.compile("\u0E42\\s+(\\d+)\\s+\u0E40\\s+(\\d+)\\s+\u0E41\\s+(\\d+)\\s+" +
                    "\u0E43\\s+(\\d+)\\s+\u0E44\\s+(\\d+)\\s+\u0E45\\s+(\\d+)");

    private static final Pattern HELD_ITEM_PATTERN =
            Pattern.compile("Held Item:\\s*(.+)");

    public static ParentIvData parse(ItemStack stack, String speciesName) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return null;

        int ivPercent = -1;
        int hp = -1, atk = -1, def = -1, spAtk = -1, spDef = -1, spe = -1;
        String heldItem = null;
        boolean foundIvLine = false;

        for (Component line : lore.lines()) {
            String text = line.getString().replaceAll("\u00A7[0-9a-fk-or]", "").trim();
            if (text.isEmpty()) continue;

            if (!foundIvLine) {
                Matcher detailMatcher = IV_DETAIL_PATTERN.matcher(text);
                if (detailMatcher.find()) {
                    hp = Integer.parseInt(detailMatcher.group(1));
                    atk = Integer.parseInt(detailMatcher.group(2));
                    def = Integer.parseInt(detailMatcher.group(3));
                    spAtk = Integer.parseInt(detailMatcher.group(4));
                    spDef = Integer.parseInt(detailMatcher.group(5));
                    spe = Integer.parseInt(detailMatcher.group(6));
                    foundIvLine = true;
                    continue;
                }
            }

            if (ivPercent < 0) {
                Matcher summaryMatcher = IV_SUMMARY_PATTERN.matcher(text);
                if (summaryMatcher.find()) {
                    ivPercent = Integer.parseInt(summaryMatcher.group(2));
                    continue;
                }
            }

            if (heldItem == null) {
                Matcher heldMatcher = HELD_ITEM_PATTERN.matcher(text);
                if (heldMatcher.find()) {
                    heldItem = heldMatcher.group(1).trim();
                }
            }
        }

        if (!foundIvLine) return null;

        if (ivPercent < 0) {
            int total = hp + atk + def + spAtk + spDef + spe;
            ivPercent = Math.round(total / 186.0f * 100);
        }

        return new ParentIvData(speciesName, hp, atk, def, spAtk, spDef, spe,
                ivPercent, mapPowerItem(heldItem));
    }

    private static ParentIvData.PowerItemStat mapPowerItem(String heldItem) {
        if (heldItem == null) return null;
        return switch (heldItem.trim()) {
            case "Power Weight" -> ParentIvData.PowerItemStat.HP;
            case "Power Bracer" -> ParentIvData.PowerItemStat.ATK;
            case "Power Belt" -> ParentIvData.PowerItemStat.DEF;
            case "Power Lens" -> ParentIvData.PowerItemStat.SP_ATK;
            case "Power Band" -> ParentIvData.PowerItemStat.SP_DEF;
            case "Power Anklet" -> ParentIvData.PowerItemStat.SPE;
            default -> null;
        };
    }
}
