package com.zifang.mill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 极简 JSON 解析：对象→Map、数组→List、字符串/数字/布尔/null 原样。零依赖，转义遵循 RFC 8259。 */
public final class MillMiniJson {

    private MillMiniJson() {
    }

    public static Object parse(String json) {
        P p = new P(json);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.pos < p.src.length()) {
            throw new IllegalArgumentException("JSON 尾部有多余内容 @" + p.pos);
        }
        return v;
    }

    private static final class P {
        final String src;
        int pos;

        P(String src) {
            this.src = src;
        }

        void ws() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        char peek() {
            if (pos >= src.length()) {
                throw new IllegalArgumentException("JSON 提前结束");
            }
            return src.charAt(pos);
        }

        void expect(char c) {
            if (peek() != c) {
                throw new IllegalArgumentException("期望 '" + c + "' @" + pos);
            }
            pos++;
        }

        Object value() {
            return switch (peek()) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        Map<String, Object> object() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            ws();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                ws();
                String key = string();
                ws();
                expect(':');
                ws();
                map.put(key, value());
                ws();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == '}') {
                    pos++;
                    return map;
                } else {
                    throw new IllegalArgumentException("期望 ',' 或 '}' @" + pos);
                }
            }
        }

        List<Object> array() {
            expect('[');
            List<Object> list = new ArrayList<>();
            ws();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                ws();
                list.add(value());
                ws();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == ']') {
                    pos++;
                    return list;
                } else {
                    throw new IllegalArgumentException("期望 ',' 或 ']' @" + pos);
                }
            }
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= src.length()) {
                    throw new IllegalArgumentException("字符串未闭合");
                }
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char e = src.charAt(pos++);
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > src.length()) {
                                throw new IllegalArgumentException("\\u 转义不完整");
                            }
                            sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        default -> throw new IllegalArgumentException("未知转义 \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        Object literal(String word, Object v) {
            if (!src.startsWith(word, pos)) {
                throw new IllegalArgumentException("非法字面量 @" + pos);
            }
            pos += word.length();
            return v;
        }

        Double number() {
            int start = pos;
            while (pos < src.length() && "-+.eE0123456789".indexOf(src.charAt(pos)) >= 0) {
                pos++;
            }
            if (pos == start) {
                throw new IllegalArgumentException("非法数值 @" + pos);
            }
            return Double.parseDouble(src.substring(start, pos));
        }
    }
}
