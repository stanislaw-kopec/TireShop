package com.shop.tire.catalog;

import java.text.Normalizer;
import java.util.Locale;

final class CatalogNames {
    private CatalogNames() {}

    static String display(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).replaceAll("(?U)\\s+", " ").strip();
    }

    static String normalized(String value) { return display(value).toLowerCase(Locale.ROOT); }
}
