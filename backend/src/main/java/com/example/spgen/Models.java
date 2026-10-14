package com.example.spgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Models {
    private Models() {}

    public static class GenerateRequest {
        public String query;
        /** "PROCEDURE" (default) or "FUNCTION" (SQL table function / UDF). */
        public String routineType;
        public String procedureName;
        public String procedureLibrary;
        /** Output column name (alias / column name / EXPR_n) -> SQL type, for unresolved columns. */
        public Map<String, String> columnTypes;
        /** Parameter name -> SQL type, for unresolved parameters. */
        public Map<String, String> parameterTypes;
    }

    public static class ColumnInfo {
        public String name;
        public String type;
        public boolean resolved;
        public boolean nullable;
        /** Where the type came from: "Library/File.COLUMN", "user" or the expression text when unresolved. */
        public String source;
    }

    public static class ParamInfo {
        public String name;
        public String type;
        public boolean resolved;
        public String source;
    }

    public static class GenerateResponse {
        public boolean complete;
        public String source;
        public List<ColumnInfo> columns = new ArrayList<>();
        public List<ParamInfo> parameters = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();
    }

    /** Column metadata as read from QSYS2.SYSCOLUMNS. */
    public static class ColumnMeta {
        public String systemName;
        public String dataType;
        public Integer length;
        public Integer scale;
        public Integer ccsid;
        public boolean nullable;
    }
}
