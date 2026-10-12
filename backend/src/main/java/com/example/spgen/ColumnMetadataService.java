package com.example.spgen;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.spgen.Models.ColumnMeta;

/** Reads column definitions from QSYS2.SYSCOLUMNS; never touches the data of the user's tables. */
@Service
public class ColumnMetadataService implements ColumnMetadataProvider {

    private static final String SQL =
        "SELECT COLUMN_NAME, DATA_TYPE, LENGTH, NUMERIC_SCALE, CCSID, IS_NULLABLE "
        + "FROM QSYS2.SYSCOLUMNS "
        + "WHERE (TABLE_SCHEMA = ? OR SYSTEM_TABLE_SCHEMA = ?) "
        + "AND (TABLE_NAME = ? OR SYSTEM_TABLE_NAME = ?) "
        + "ORDER BY ORDINAL_POSITION";

    private final JdbcTemplate jdbc;

    public ColumnMetadataService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ColumnMeta> getColumns(String library, String file) {
        String lib = library.toUpperCase();
        String f = file.toUpperCase();
        return jdbc.query(SQL, ps -> {
            ps.setString(1, lib);
            ps.setString(2, lib);
            ps.setString(3, f);
            ps.setString(4, f);
        }, (rs, i) -> {
            ColumnMeta m = new ColumnMeta();
            m.name = rs.getString("COLUMN_NAME");
            m.dataType = rs.getString("DATA_TYPE");
            m.length = (Integer) rs.getObject("LENGTH");
            m.scale = (Integer) rs.getObject("NUMERIC_SCALE");
            m.ccsid = (Integer) rs.getObject("CCSID");
            m.nullable = "Y".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
            return m;
        });
    }
}
