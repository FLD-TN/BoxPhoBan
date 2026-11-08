package com.dex.boxphoban.template;

import org.bukkit.Material;

import java.util.List;

import static com.dex.boxphoban.template.ParamSpec.Kind.*;
import static com.dex.boxphoban.template.ParamSpec.p;

/** What makes a node run. */
public enum TriggerType {

    DUNGEON_START("Bắt đầu phó bản", Material.LIME_BANNER, "Chạy khi phó bản vừa bắt đầu"),
    STAGE_START("Bắt đầu ải", Material.WHITE_BANNER, "Chạy khi một ải bắt đầu",
            p("stage", "Ải", STAGE, "", "id ải")),
    AREA("Vào vùng", Material.TRIPWIRE_HOOK, "Có người bước vào vùng hoặc bán kính",
            p("region", "Vùng", REGION, "", "tên vùng, để trống = dùng bán kính"),
            p("radius", "Bán kính", DOUBLE, "4", "block quanh node"),
            p("players", "Số người", TEXT, "1", "số người cần có, all = cả phòng")),
    KILL_MOB("Giết mob", Material.IRON_SWORD, "Giết đủ số mob MythicMobs",
            p("mob", "Mob", MOB, "*", "id MythicMobs, * = bất kỳ"),
            p("amount", "Số lượng", INT, "1", "")),
    WAVE_CLEARED("Dọn sạch đợt", Material.BONE, "Mọi mob do một node spawn đã chết",
            p("node", "Node spawn", NODE, "", "id node có hành động spawn")),
    INTERACT("Tương tác", Material.LEVER, "Bấm nút, gạt cần, chuột phải vào block của node"),
    KEY_USE("Dùng chìa", Material.TRIAL_KEY, "Chuột phải vào block của node bằng chìa",
            p("key", "Chìa", TEXT, "chia", "id chìa (hành động Cho chìa)"),
            p("consume", "Mất chìa", BOOL, "true", "true/false")),
    PLAYER_DEATH("Người chơi chết", Material.SKELETON_SKULL, "Có người trong phòng chết"),
    TIMER("Đếm giờ", Material.CLOCK, "Sau một số giây",
            p("seconds", "Số giây", INT, "30", ""),
            p("from", "Tính từ", CHOICE, "dungeon", "dungeon|stage"),
            p("stage", "Ải", STAGE, "", "chỉ khi tính từ ải, để trống = ải bất kỳ")),
    SIGNAL("Tín hiệu", Material.REDSTONE_TORCH, "Node khác gửi tín hiệu",
            p("name", "Tên tín hiệu", TEXT, "", "")),
    ROOM_SEALED("Phòng khoá kín", Material.IRON_BARS, "Mọi người còn sống đã vào phòng và cửa vào đóng",
            p("room", "Phòng", REGION, "", "vùng phòng của hành động Khoá phòng")),
    STAGE_CLEARED("Dọn sạch ải", Material.TOTEM_OF_UNDYING, "Mọi quái spawn trong ải hiện tại đã chết",
            p("stage", "Ải", STAGE, "", "để trống = ải bất kỳ"));

    public final String display;
    public final Material icon;
    public final String description;
    public final List<ParamSpec> params;

    TriggerType(String display, Material icon, String description, ParamSpec... params) {
        this.display = display;
        this.icon = icon;
        this.description = description;
        this.params = List.of(params);
    }

    public static TriggerType parse(String s) {
        try {
            return valueOf(s.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
}
