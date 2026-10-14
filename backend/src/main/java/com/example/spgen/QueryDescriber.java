package com.example.spgen;

import java.util.ArrayList;
import java.util.List;

import com.example.spgen.Models.ColumnMeta;

/** Asks the database to describe a statement (result columns and parameter types) without running it. */
public interface QueryDescriber {

    class Description {
        /** Result columns in order; {@code systemName} is null when the database gave no usable name. */
        public List<ColumnMeta> columns = new ArrayList<>();
        /** One entry per parameter marker, in marker order; {@code dataType} is null when not described. */
        public List<ColumnMeta> params = new ArrayList<>();
    }

    Description describe(String sql, int paramCount);
}
