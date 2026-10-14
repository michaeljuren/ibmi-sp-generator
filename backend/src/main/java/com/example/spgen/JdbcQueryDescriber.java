package com.example.spgen;

import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.spgen.Models.ColumnMeta;

/** Prepares the statement and reads its metadata; the statement is never executed. */
@Service
public class JdbcQueryDescriber implements QueryDescriber {

    private final JdbcTemplate jdbc;

    public JdbcQueryDescriber(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Description describe(String sql, int paramCount) {
        return jdbc.execute((ConnectionCallback<Description>) con -> {
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                Description d = new Description();
                ResultSetMetaData rm = ps.getMetaData();
                if (rm != null) {
                    for (int i = 1; i <= rm.getColumnCount(); i++) {
                        ColumnMeta m = new ColumnMeta();
                        String label = rm.getColumnLabel(i);
                        m.systemName = label == null || label.isBlank() || label.trim().matches("\\d+")
                            ? null : label.trim();
                        fill(m, rm.getColumnTypeName(i), rm.getPrecision(i), rm.getScale(i));
                        m.nullable = rm.isNullable(i) != ResultSetMetaData.columnNoNulls;
                        d.columns.add(m);
                    }
                }
                ParameterMetaData pm = null;
                try {
                    pm = ps.getParameterMetaData();
                } catch (SQLException e) {
                    // parameter types stay unresolved and can be supplied by the user
                }
                for (int i = 1; i <= paramCount; i++) {
                    ColumnMeta m = new ColumnMeta();
                    try {
                        if (pm != null) {
                            fill(m, pm.getParameterTypeName(i), pm.getPrecision(i), pm.getScale(i));
                        }
                    } catch (SQLException e) {
                        m.dataType = null;
                    }
                    d.params.add(m);
                }
                return d;
            } catch (SQLException e) {
                String state = e.getSQLState();
                if (state != null && (state.startsWith("42") || state.startsWith("07")
                    || state.startsWith("37") || state.startsWith("56"))) {
                    throw new IllegalArgumentException("Db2 could not prepare the query: "
                        + e.getMessage().split("\\R")[0]);
                }
                throw e;
            }
        });
    }

    private static void fill(ColumnMeta m, String typeName, int precision, int scale) {
        if (typeName == null || typeName.isBlank()) {
            return;
        }
        m.dataType = typeName.trim().toUpperCase();
        m.length = precision;
        m.scale = scale;
    }
}
