package com.example.spgen;

import java.util.List;
import java.util.stream.Collectors;

import com.example.spgen.Models.ColumnInfo;
import com.example.spgen.Models.ParamInfo;

public final class ProcedureGenerator {
    private ProcedureGenerator() {}

    public static String generate(String library, String name, List<ColumnInfo> columns,
                                  List<ParamInfo> params, String query) {
        StringBuilder sb = new StringBuilder();
        if (!columns.isEmpty()) {
            sb.append("-- Result set columns:\n");
            for (ColumnInfo c : columns) {
                sb.append("--   ").append(c.name).append(" ").append(c.type).append("\n");
            }
        }
        sb.append("CREATE OR REPLACE PROCEDURE ").append(library.toUpperCase()).append('.')
            .append(name.toUpperCase());
        if (params.isEmpty()) {
            sb.append(" ()\n");
        } else {
            sb.append(" (\n");
            sb.append(params.stream().map(p -> "    IN " + p.name + " " + p.type)
                .collect(Collectors.joining(",\n")));
            sb.append("\n)\n");
        }
        sb.append("    LANGUAGE SQL\n");
        sb.append("    DYNAMIC RESULT SETS 1\n");
        sb.append("    READS SQL DATA\n");
        sb.append("BEGIN\n");
        sb.append("    DECLARE C1 CURSOR WITH RETURN TO CLIENT FOR\n");
        sb.append("        ").append(query.replaceAll("\\R", "\n        ")).append(";\n");
        sb.append("    OPEN C1;\n");
        sb.append("END;\n");
        return sb.toString();
    }
}
