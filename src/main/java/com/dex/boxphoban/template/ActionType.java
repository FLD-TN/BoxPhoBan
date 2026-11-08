package com.dex.boxphoban.template;

import org.bukkit.Material;

import java.util.List;

import static com.dex.boxphoban.template.ParamSpec.Kind.*;
import static com.dex.boxphoban.template.ParamSpec.p;

/** What a node does when it runs. */
public enum ActionType {

    SPAWN_MOB("Spawn mob", Material.ZOMBIE_HEAD, "Spawn mob MythicMobs quanh node",
            p("mob", "Mob", MOB, "", "id MythicMobs"),
            p("amount", "Số lượng", INT, "3", ""),
            p("radius", "Bán kính rải", DOUBLE, "3", "block"),
            p("level", "Cộng level", INT, "0", "cộng thêm vào level của độ khó")),
    SPAWN_BOSS("Spawn boss", Material.WITHER_SKELETON_SKULL, "Spawn boss + thanh máu",
            p("mob", "Mob", MOB, "", "id MythicMobs"),
            p("level", "Cộng level", INT, "0", ""),
            p("bar-color", "Màu thanh máu", CHOICE, "RED", "RED|PINK|BLUE|GREEN|YELLOW|PURPLE|WHITE")),
    DOOR_OPEN("Mở cửa", Material.OAK_DOOR, "Đổi vùng cửa thành không khí",
            p("region", "Vùng cửa", REGION, "", "")),
    DOOR_CLOSE("Đóng cửa", Material.IRON_DOOR, "Khôi phục vùng cửa",
            p("region", "Vùng cửa", REGION, "", ""),
            p("material", "Block", TEXT, "", "để trống = block cũ, có thì lấp bằng block này")),
    CHECKPOINT("Đặt checkpoint", Material.RESPAWN_ANCHOR, "Checkpoint chung của phòng",
            p("point", "Điểm", POINT, "", "tên điểm, để trống = ngay trên node")),
    SET_STAGE("Chuyển ải", Material.WHITE_BANNER, "Sang ải khác",
            p("stage", "Ải", STAGE, "", "id ải, next = ải kế tiếp")),
    MESSAGE("Thông báo", Material.PAPER, "Chat, title hoặc actionbar cho cả phòng",
            p("mode", "Kiểu", CHOICE, "chat", "chat|title|actionbar"),
            p("text", "Nội dung", TEXT, "", "có màu &, &#RRGGBB"),
            p("subtitle", "Dòng phụ", TEXT, "", "chỉ dùng với title")),
    SOUND("Âm thanh", Material.NOTE_BLOCK, "Phát âm thanh cho cả phòng",
            p("sound", "Âm thanh", ParamSpec.Kind.SOUND, "entity.experience_orb.pickup", "key âm thanh"),
            p("volume", "Âm lượng", DOUBLE, "1", ""),
            p("pitch", "Cao độ", DOUBLE, "1", "")),
    TELEPORT("Teleport", Material.ENDER_PEARL, "Đưa người tới một điểm",
            p("point", "Điểm", POINT, "", "tên điểm"),
            p("who", "Ai", CHOICE, "all", "all|trigger")),
    GIVE_KEY("Cho chìa", Material.TRIAL_KEY, "Cho chìa chỉ dùng trong phó bản này",
            p("key", "Id chìa", TEXT, "chia", ""),
            p("name", "Tên hiển thị", TEXT, "&#FFD166Chìa khoá", ""),
            p("who", "Ai", CHOICE, "trigger", "all|trigger")),
    LIVES("Cộng / trừ mạng", Material.TOTEM_OF_UNDYING, "Thêm hoặc bớt mạng",
            p("amount", "Số mạng", INT, "1", "số âm = trừ")),
    SIGNAL("Gửi tín hiệu", Material.REDSTONE, "Kích các node có trigger Tín hiệu",
            p("name", "Tên tín hiệu", TEXT, "", "")),
    COMMAND("Chạy lệnh", Material.COMMAND_BLOCK, "Lệnh console, %player% = người kích hoạt",
            p("command", "Lệnh", TEXT, "", "không có dấu /"),
            p("who", "Chạy cho", CHOICE, "trigger", "trigger|all|once")),
    LOOT("Rương thưởng", Material.CHEST, "Mở loot riêng cho người kích hoạt",
            p("table", "Bảng loot", TEXT, "", "id trong loot.yml")),
    FINISH("Hoàn thành", Material.EMERALD_BLOCK, "Kết thúc thắng"),
    FAIL("Thất bại", Material.REDSTONE_BLOCK, "Kết thúc thua");

    public final String display;
    public final Material icon;
    public final String description;
    public final List<ParamSpec> params;

    ActionType(String display, Material icon, String description, ParamSpec... params) {
        this.display = display;
        this.icon = icon;
        this.description = description;
        this.params = List.of(params);
    }

    public static ActionType parse(String s) {
        try {
            return valueOf(s.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
}
