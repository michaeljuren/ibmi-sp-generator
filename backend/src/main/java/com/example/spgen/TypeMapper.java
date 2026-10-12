package com.example.spgen;

import java.util.Set;

import com.example.spgen.Models.ColumnMeta;

public final class TypeMapper {
    private TypeMapper() {}

    private static final Set<String> LENGTH_ONLY = Set.of(
        "CHAR", "VARCHAR", "GRAPHIC", "VARGRAPHIC", "BINARY", "VARBINARY",
        "CLOB", "DBCLOB", "BLOB", "DECFLOAT");

    public static String toSqlType(ColumnMeta c) {
        String t = c.dataType == null ? "" : c.dataType.trim().toUpperCase();
        Integer len = c.length;
        switch (t) {
            case "DECIMAL":
            case "NUMERIC":
                return t + "(" + len + "," + (c.scale == null ? 0 : c.scale) + ")";
            case "TIMESTAMP":
                return c.scale != null && c.scale != 6 ? "TIMESTAMP(" + c.scale + ")" : "TIMESTAMP";
            case "FLOAT":
                return len != null && len <= 24 ? "REAL" : "DOUBLE";
            case "CHAR":
            case "VARCHAR": {
                String base = t + "(" + len + ")";
                return c.ccsid != null && c.ccsid == 65535 ? base + " FOR BIT DATA" : base;
            }
            default:
                if (LENGTH_ONLY.contains(t) && len != null) {
                    return t + "(" + len + ")";
                }
                return t;
        }
    }
}
