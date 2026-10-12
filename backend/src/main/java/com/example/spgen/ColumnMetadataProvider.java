package com.example.spgen;

import java.util.List;

import com.example.spgen.Models.ColumnMeta;

public interface ColumnMetadataProvider {
    /** Returns the columns of a table/file in ordinal order; empty if it does not exist. */
    List<ColumnMeta> getColumns(String library, String file);
}
