package com.tss.grc.module.sust.ghg;

import java.util.LinkedHashMap;
import java.util.Map;

/** The GHG Protocol's 15 Scope-3 value-chain categories (roadmap closure — plan §0 item 10). */
public final class GhgScope3Categories {

    private GhgScope3Categories() {}

    public record Cat(int number, String labelEn, String labelAr) {}

    private static final Map<Integer, Cat> CATS = new LinkedHashMap<>();
    static {
        put(1,  "Purchased goods and services",            "السلع والخدمات المشتراة");
        put(2,  "Capital goods",                           "السلع الرأسمالية");
        put(3,  "Fuel- and energy-related activities",     "أنشطة الوقود والطاقة (غير المشمولة في النطاقين 1 و2)");
        put(4,  "Upstream transportation and distribution","النقل والتوزيع في سلسلة التوريد");
        put(5,  "Waste generated in operations",           "النفايات الناتجة عن العمليات");
        put(6,  "Business travel",                         "سفر العمل");
        put(7,  "Employee commuting",                      "تنقلات الموظفين");
        put(8,  "Upstream leased assets",                  "الأصول المستأجرة (مورّدون)");
        put(9,  "Downstream transportation and distribution","النقل والتوزيع بعد البيع");
        put(10, "Processing of sold products",             "معالجة المنتجات المباعة");
        put(11, "Use of sold products",                    "استخدام المنتجات المباعة");
        put(12, "End-of-life treatment of sold products",  "معالجة نهاية عمر المنتجات المباعة");
        put(13, "Downstream leased assets",                "الأصول المؤجَّرة (عملاء)");
        put(14, "Franchises",                              "الامتيازات التجارية");
        put(15, "Investments",                             "الاستثمارات");
    }
    private static void put(int n, String en, String ar) { CATS.put(n, new Cat(n, en, ar)); }

    public static Map<Integer, Cat> all() { return CATS; }

    public static Cat of(int number) { return CATS.get(number); }
}
