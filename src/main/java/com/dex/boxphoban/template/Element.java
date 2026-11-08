package com.dex.boxphoban.template;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A condition or action of a node: a type name plus string parameters. */
public final class Element {

    public String type;
    public final Map<String, String> params = new LinkedHashMap<>();

    public Element(String type) {
        this.type = type;
    }

    public static Element withDefaults(String type, List<ParamSpec> specs) {
        Element e = new Element(type);
        for (ParamSpec s : specs) e.params.put(s.key(), s.def());
        return e;
    }

    public String s(String key, String def) {
        String v = params.get(key);
        return v == null || v.isEmpty() ? def : v;
    }

    public int i(String key, int def) {
        try {
            return Integer.parseInt(params.get(key));
        } catch (Exception e) {
            return def;
        }
    }

    public double d(String key, double def) {
        try {
            return Double.parseDouble(params.get(key));
        } catch (Exception e) {
            return def;
        }
    }

    public boolean b(String key, boolean def) {
        String v = params.get(key);
        return v == null || v.isEmpty() ? def : Boolean.parseBoolean(v);
    }

    public Element copy() {
        Element e = new Element(type);
        e.params.putAll(params);
        return e;
    }
}
