package com.siguha.sigsacademyaddons.feature.daycare;

public class ParentIvData {

    public enum PowerItemStat {
        HP, ATK, DEF, SP_ATK, SP_DEF, SPE
    }

    private final String species;
    private final int hp, atk, def, spAtk, spDef, spe;
    private final int ivPercent;
    private final PowerItemStat powerItemStat;

    public ParentIvData(String species, int hp, int atk, int def, int spAtk, int spDef, int spe,
                        int ivPercent, PowerItemStat powerItemStat) {
        this.species = species;
        this.hp = hp;
        this.atk = atk;
        this.def = def;
        this.spAtk = spAtk;
        this.spDef = spDef;
        this.spe = spe;
        this.ivPercent = ivPercent;
        this.powerItemStat = powerItemStat;
    }

    public String getSpecies() { return species; }
    public int getIvPercent() { return ivPercent; }
    public PowerItemStat getPowerItemStat() { return powerItemStat; }

    public int getIvForStatRow(int row) {
        return switch (row) {
            case 0 -> hp;
            case 1 -> atk;
            case 2 -> def;
            case 3 -> spe;
            case 4 -> spDef;
            case 5 -> spAtk;
            default -> 0;
        };
    }

    public static PowerItemStat statForRow(int row) {
        return switch (row) {
            case 0 -> PowerItemStat.HP;
            case 1 -> PowerItemStat.ATK;
            case 2 -> PowerItemStat.DEF;
            case 3 -> PowerItemStat.SPE;
            case 4 -> PowerItemStat.SP_DEF;
            case 5 -> PowerItemStat.SP_ATK;
            default -> null;
        };
    }
}
