package com.dex.boxphoban.template;

/** One editable parameter of a trigger / condition / action. */
public record ParamSpec(String key, String label, Kind kind, String def, String hint) {

    public enum Kind { TEXT, INT, DOUBLE, BOOL, MOB, REGION, POINT, STAGE, SOUND, CHOICE, NODE }

    public static ParamSpec p(String key, String label, Kind kind, String def, String hint) {
        return new ParamSpec(key, label, kind, def, hint);
    }

    /** Validates a typed value; returns the normalised value or null when invalid. */
    public String normalise(String in) {
        if (in == null) return null;
        String v = in.trim();
        switch (kind) {
            case INT -> {
                try {
                    return String.valueOf(Integer.parseInt(v));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            case DOUBLE -> {
                try {
                    return String.valueOf(Double.parseDouble(v.replace(',', '.')));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            case BOOL -> {
                String l = v.toLowerCase();
                if (l.equals("true") || l.equals("co") || l.equals("có") || l.equals("bat") || l.equals("bật")) return "true";
                if (l.equals("false") || l.equals("khong") || l.equals("không") || l.equals("tat") || l.equals("tắt")) return "false";
                return null;
            }
            case CHOICE -> {
                for (String opt : hint.split("\\|")) {
                    if (opt.equalsIgnoreCase(v)) return opt;
                }
                return null;
            }
            default -> {
                return v.isEmpty() ? null : v;
            }
        }
    }
}
