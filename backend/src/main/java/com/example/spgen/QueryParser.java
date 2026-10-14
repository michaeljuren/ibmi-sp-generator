package com.example.spgen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates that the text is a single read-only statement and finds its parameter markers.
 * Column and parameter types are not inferred here; the database describes the statement.
 */
public class QueryParser {

    public static class ParsedQuery {
        /** Parameter names in order of first appearance. */
        public Set<String> paramNames = new LinkedHashSet<>();
        /** Parameter name of every marker in the statement, in order of appearance. */
        public List<String> markers = new ArrayList<>();
        /** Query text with ? markers replaced by :Pn and no trailing semicolon. */
        public String sql;
        /** The same query with every marker as ?, for preparing against the database. */
        public String jdbcSql;
    }

    private static final Pattern NAMED = Pattern.compile(":([A-Za-z_][A-Za-z0-9_$#@]*)");
    private static final Pattern READ_ONLY = Pattern.compile("^[\\s(]*(SELECT|WITH|VALUES)\\b",
        Pattern.CASE_INSENSITIVE);

    public ParsedQuery parse(String query) {
        String sql = query.trim().replaceAll("[;\\s]+$", "");
        ParsedQuery pq = new ParsedQuery();
        StringBuilder named = new StringBuilder();
        StringBuilder jdbc = new StringBuilder();
        StringBuilder code = new StringBuilder();
        char quote = 0;
        int n = 0;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (quote != 0) {
                named.append(ch);
                jdbc.append(ch);
                if (ch == quote) {
                    quote = 0;
                }
            } else if (ch == '\'' || ch == '"') {
                quote = ch;
                named.append(ch);
                jdbc.append(ch);
                code.append(' ');
            } else if (sql.startsWith("--", i)) {
                int end = lineEnd(sql, i);
                named.append(sql, i, end);
                jdbc.append(sql, i, end);
                i = end - 1;
            } else if (sql.startsWith("/*", i)) {
                int close = sql.indexOf("*/", i + 2);
                int end = close < 0 ? sql.length() : close + 2;
                named.append(sql, i, end);
                jdbc.append(sql, i, end);
                i = end - 1;
            } else if (ch == ';') {
                throw new IllegalArgumentException("Only a single SQL statement is supported.");
            } else if (ch == '?') {
                String name = "P" + (++n);
                addMarker(pq, name);
                named.append(':').append(name);
                jdbc.append('?');
                code.append(' ');
            } else if (ch == ':') {
                Matcher m = NAMED.matcher(sql);
                if (m.find(i) && m.start() == i) {
                    addMarker(pq, m.group(1).toUpperCase());
                    named.append(m.group());
                    jdbc.append('?');
                    code.append(' ');
                    i = m.end() - 1;
                } else {
                    named.append(ch);
                    jdbc.append(ch);
                    code.append(ch);
                }
            } else {
                named.append(ch);
                jdbc.append(ch);
                code.append(ch);
            }
        }
        if (!READ_ONLY.matcher(code).find()) {
            throw new IllegalArgumentException(
                "Only a read-only SELECT, WITH or VALUES statement is supported.");
        }
        pq.sql = named.toString();
        pq.jdbcSql = jdbc.toString();
        return pq;
    }

    private static int lineEnd(String s, int from) {
        int e = from;
        while (e < s.length() && s.charAt(e) != '\n' && s.charAt(e) != '\r') {
            e++;
        }
        return e;
    }

    private static void addMarker(ParsedQuery pq, String name) {
        pq.markers.add(name);
        pq.paramNames.add(name);
    }
}
