package com.example.spgen;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.spgen.Models.ColumnMeta;

/** Reads column definitions from QSYS2.SYSCOLUMNS; never touches the data of the user's tables. */
@Service
public class ColumnMetadataService implements ColumnMetadataProvider {

    private static final String SQL =
        "SELECT SYSTEM_COLUMN_NAME, DATA_TYPE, LENGTH, NUMERIC_SCALE, CCSID, IS_NULLABLE "
        + "FROM QSYS2.SYSCOLUMNS "
        + "WHERE (TABLE_SCHEMA = ?) "
        + "AND (TABLE_NAME = ?) "
        + "ORDER BY ORDINAL_POSITION";

    private final JdbcTemplate jdbc;

    public ColumnMetadataService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** IBM i pads CHAR values; strip all Unicode whitespace/space separators, not just ASCII. */
    private static String clean(String s) {
        return s == null ? null : s.replaceAll("^[\\s\\p{Z}\\u0000]+|[\\s\\p{Z}\\u0000]+$", "");
    }

    @Override
    public List<ColumnMeta> getColumns(String library, String file) {
        String lib = library.toUpperCase();
        String f = file.toUpperCase();
        return jdbc.query(SQL, ps -> {
            ps.setString(1, lib);
            ps.setString(2, f);
        }, (rs, i) -> {
            ColumnMeta m = new ColumnMeta();
            m.systemName = clean(rs.getString("SYSTEM_COLUMN_NAME"));
            m.dataType = clean(rs.getString("DATA_TYPE"));
            m.length = (Integer) rs.getObject("LENGTH");
            m.scale = (Integer) rs.getObject("NUMERIC_SCALE");
            m.ccsid = (Integer) rs.getObject("CCSID");
            m.nullable = "Y".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
            return m;
        });
    }
}
