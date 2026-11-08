package com.dex.boxphoban.template;

import org.bukkit.Material;

import java.util.List;

import static com.dex.boxphoban.template.ParamSpec.Kind.*;
import static com.dex.boxphoban.template.ParamSpec.p;

/** Extra checks a node needs before it runs. */
public enum ConditionType {

    DIFFICULTY("Độ khó", Material.NETHER_STAR, "Chỉ chạy ở các độ khó này",
            p("ids", "Độ khó", TEXT, "", "id cách nhau dấu phẩy"),
            p("negate", "Đảo ngược", BOOL, "false", "true = mọi độ khó trừ các id trên")),
    PLAYERS_IN_REGION("Số người trong vùng", Material.PLAYER_HEAD, "Cần ít nhất số người trong vùng",
            p("region", "Vùng", REGION, "", ""),
            p("min", "Tối thiểu", INT, "1", "")),
    CHANCE("Xác suất", Material.RABBIT_FOOT, "Chạy theo tỉ lệ phần trăm",
            p("percent", "Phần trăm", DOUBLE, "50", "0-100")),
    TIME_ELAPSED("Thời gian đã chơi", Material.CLOCK, "Phó bản đã chạy ít nhất số giây",
            p("seconds", "Số giây", INT, "60", "")),
    STAGE_IS("Ải hiện tại", Material.WHITE_BANNER, "Chỉ chạy khi đang ở ải này",
            p("stage", "Ải", STAGE, "", ""));

    public final String display;
    public final Material icon;
    public final String description;
    public final List<ParamSpec> params;

    ConditionType(String display, Material icon, String description, ParamSpec... params) {
        this.display = display;
        this.icon = icon;
        this.description = description;
        this.params = List.of(params);
    }

    public static ConditionType parse(String s) {
        try {
            return valueOf(s.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
}
