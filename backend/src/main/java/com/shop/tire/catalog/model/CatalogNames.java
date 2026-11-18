package com.shop.tire.catalog.model;

import java.text.Normalizer;
import java.util.Locale;

public final class CatalogNames {
    private CatalogNames() {}

    public static String display(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).replaceAll("(?U)\\s+", " ").strip();
    }

    public static String normalized(String value) { return display(value).toLowerCase(Locale.ROOT); }
}
