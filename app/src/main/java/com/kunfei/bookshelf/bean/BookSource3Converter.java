package com.kunfei.bookshelf.bean;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 阅读3.0书源 → 阅读2.0书源（批量导入用）。
 *
 * 工程里原本只有"书源编辑界面粘贴"这一条转换路径（SourceEditPresenter#mathcSourceBean），
 * 批量导入（BookSourceManager#importBookSourceFromJson）不走它，所以 3.0 书源合集直接导入
 * 会得到一堆空规则。这里补上导入侧转换，并补一条规则语法规范化：
 *
 * 阅读3.0 的规则默认是 CSS 选择器（如 {@code .chapter li a}），而 2.0 的规则解析器只有在
 * 规则带 {@code @CSS:} 前缀时才按 CSS 处理（见 AnalyzeByJSoup.SourceRule），否则会走 2.0
 * 自有的 class./id./tag. 语法，结果取值为空。
 */
public class BookSource3Converter {

    private static final Gson GSON = new Gson();

    /** 这些字段是 URL 模板或正则，不能按选择器处理 */
    private static final Set<String> RULE_EXEMPT = new HashSet<>(Arrays.asList(
            "ruleSearchUrl", "ruleFindUrl", "ruleBookUrlPattern", "ruleBookContentReplace"));

    private BookSource3Converter() {
    }

    /** 3.0 格式的特征：规则是嵌套对象而不是平铺的 ruleXxx 字段 */
    public static boolean isSource3(JsonElement element) {
        if (element == null || !element.isJsonObject()) return false;
        JsonObject obj = element.getAsJsonObject();
        for (String key : new String[]{"ruleSearch", "ruleToc", "ruleBookInfo", "ruleContent", "ruleExplore"}) {
            JsonElement rule = obj.get(key);
            if (rule != null && rule.isJsonObject()) return true;
        }
        return false;
    }

    /**
     * 把含 3.0 书源的 json 转成 2.0 可解析的 json；没有 3.0 内容时原样返回。
     * 支持数组、单对象以及两者混排。
     */
    public static String convertIfNeeded(String json) {
        if (json == null || json.trim().isEmpty()) return json;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root.isJsonArray()) {
                JsonArray result = new JsonArray();
                boolean converted = false;
                for (JsonElement item : root.getAsJsonArray()) {
                    JsonElement convertedItem = isSource3(item) ? convertOne(item) : null;
                    if (convertedItem != null) {
                        result.add(convertedItem);
                        converted = true;
                    } else {
                        result.add(item);
                    }
                }
                return converted ? result.toString() : json;
            }
            if (isSource3(root)) {
                JsonElement converted = convertOne(root);
                if (converted != null) return converted.toString();
            }
        } catch (Exception ignored) {
        }
        return json;
    }

    private static JsonElement convertOne(JsonElement element) {
        try {
            BookSource3Bean bean3 = GSON.fromJson(element, BookSource3Bean.class);
            if (bean3 == null) return null;
            bean3.addGroupTag("阅读3.0书源");
            BookSourceBean bean2 = bean3.toBookSourceBean();
            if (bean2 == null || bean2.getBookSourceUrl() == null) return null;
            normalizeBean(bean2);
            return GSON.toJsonTree(bean2);
        } catch (Exception e) {
            return null;
        }
    }

    /** 规范化一个 2.0 书源里所有"选择器类"规则 */
    public static void normalizeBean(BookSourceBean bean) {
        if (bean == null) return;
        for (Field field : BookSourceBean.class.getDeclaredFields()) {
            String name = field.getName();
            if (!name.startsWith("rule") || field.getType() != String.class) continue;
            if (RULE_EXEMPT.contains(name)) continue;
            try {
                field.setAccessible(true);
                String value = (String) field.get(bean);
                String fixed = normalizeRule(value);
                if (fixed != null && !fixed.equals(value)) {
                    field.set(bean, fixed);
                }
            } catch (Exception ignored) {
            }
        }
    }

    /** 给 3.0 的 CSS 规则补 @CSS: 前缀（## 正则段不参与） */
    public static String normalizeRule(String rule) {
        if (rule == null || rule.trim().isEmpty()) return rule;
        int cut = rule.indexOf("##");
        String head = cut >= 0 ? rule.substring(0, cut) : rule;
        String tail = cut >= 0 ? rule.substring(cut) : "";
        return normalizeHead(head) + tail;
    }

    private static String normalizeHead(String head) {
        if (head.contains("&&")) {
            return joinNormalized(head.split("&&", -1), "&&");
        }
        if (head.contains("||")) {
            return joinNormalized(head.split("\\|\\|", -1), "||");
        }
        return normalizePart(head);
    }

    private static String joinNormalized(String[] parts, String separator) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(separator);
            sb.append(normalizePart(parts[i]));
        }
        return sb.toString();
    }

    private static String normalizePart(String part) {
        if (part == null) return "";
        String trimmed = part.trim();
        if (trimmed.isEmpty()) return part;
        return looksLikeCss(trimmed) ? "@CSS:" + trimmed : trimmed;
    }

    private static boolean looksLikeCss(String rule) {
        String lower = rule.toLowerCase();
        if (lower.startsWith("@css:") || lower.startsWith("@json:") || lower.startsWith("@xpath:")
                || lower.startsWith("@js:") || lower.startsWith("<js>") || lower.startsWith("@header:")) {
            return false;
        }
        // JSONPath / XPath 引擎能自动识别
        if (rule.startsWith("$.") || rule.startsWith("//")) return false;
        // 2.0 自有的规则语法
        if (lower.startsWith("class.") || lower.startsWith("id.") || lower.startsWith("tag.")
                || lower.startsWith("text.") || lower.startsWith("children.")) {
            return false;
        }
        // 纯取值关键字或属性名（text/href/src/div…）交给原解析器
        if (rule.matches("[A-Za-z_][A-Za-z0-9_\\-]*")) return false;
        // 含 CSS 特征字符才认定是选择器
        return rule.matches(".*[.#>\\[\\]: ].*");
    }
}
