package com.example.spgen;

import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.example.spgen.Models.ColumnInfo;
import com.example.spgen.Models.ColumnMeta;
import com.example.spgen.Models.GenerateRequest;
import com.example.spgen.Models.GenerateResponse;
import com.example.spgen.Models.ParamInfo;
import com.example.spgen.QueryDescriber.Description;
import com.example.spgen.QueryParser.ParsedQuery;

@Service
public class GeneratorService {

    private static final Pattern IDENT = Pattern.compile("^[A-Za-z][A-Za-z0-9_$#@]{0,127}$");
    private static final Pattern TYPE = Pattern.compile("^[A-Za-z ]+(\\(\\d+(\\s*,\\s*\\d+)?\\))?$");
    private static final String DB_SOURCE = "Db2 (prepared, not executed)";

    private final QueryDescriber describer;
    private final QueryParser parser = new QueryParser();

    public GeneratorService(QueryDescriber describer) {
        this.describer = describer;
    }

    public GenerateResponse generate(GenerateRequest req) {
        validate(req);
        ParsedQuery pq = parser.parse(req.query);
        GenerateResponse res = new GenerateResponse();
        boolean function = isFunction(req);

        Description d = describer.describe(pq.jdbcSql, pq.markers.size());

        // A stored procedure returns its cursor directly, so output columns are only needed for functions.
        if (function) {
            int pos = 0;
            for (ColumnMeta m : d.columns) {
                pos++;
                String name = m.systemName != null ? m.systemName : "EXPR_" + pos;
                res.columns.add(column(name, m, req));
            }
        }

        for (String name : pq.paramNames) {
            ColumnMeta m = d.params.size() > pq.markers.indexOf(name)
                ? d.params.get(pq.markers.indexOf(name)) : null;
            res.parameters.add(param(name, m, req));
        }

        res.complete = res.columns.stream().allMatch(c -> c.resolved)
            && res.parameters.stream().allMatch(p -> p.resolved);
        if (res.complete) {
            res.source = function
                ? FunctionGenerator.generate(req.procedureLibrary, req.procedureName,
                    res.columns, res.parameters, pq.sql)
                : ProcedureGenerator.generate(req.procedureLibrary, req.procedureName,
                    res.columns, res.parameters, pq.sql);
        } else {
            res.warnings.add("Some types could not be determined by Db2; please supply them.");
        }
        return res;
    }

    private ColumnInfo column(String name, ColumnMeta m, GenerateRequest req) {
        ColumnInfo c = new ColumnInfo();
        c.name = name;
        String override = override(req.columnTypes, name);
        if (override != null) {
            c.type = override.trim().toUpperCase();
            c.resolved = true;
            c.nullable = true;
            c.source = "user";
        } else if (m.dataType != null) {
            c.type = TypeMapper.toSqlType(m);
            c.resolved = true;
            c.nullable = m.nullable;
            c.source = DB_SOURCE;
        } else {
            c.source = "Type could not be determined";
        }
        return c;
    }

    private ParamInfo param(String name, ColumnMeta m, GenerateRequest req) {
        ParamInfo p = new ParamInfo();
        p.name = name;
        String override = override(req.parameterTypes, name);
        if (override != null) {
            p.type = override.trim().toUpperCase();
            p.resolved = true;
            p.source = "user";
        } else if (m != null && m.dataType != null) {
            p.type = TypeMapper.toSqlType(m);
            p.resolved = true;
            p.source = DB_SOURCE;
        } else {
            p.source = "Type could not be inferred";
        }
        return p;
    }

    private static boolean isFunction(GenerateRequest req) {
        return "FUNCTION".equalsIgnoreCase(req.routineType);
    }

    private void validate(GenerateRequest req) {
        if (req.routineType != null && !req.routineType.isBlank()
            && !"FUNCTION".equalsIgnoreCase(req.routineType)
            && !"PROCEDURE".equalsIgnoreCase(req.routineType)) {
            throw new IllegalArgumentException("Invalid routine type: " + req.routineType);
        }
        if (req.query == null || req.query.isBlank()) {
            throw new IllegalArgumentException("A query is required.");
        }
        requireIdent(req.procedureLibrary, "procedure library");
        requireIdent(req.procedureName, "procedure name");
        for (Map<String, String> m : new Map[] {req.columnTypes, req.parameterTypes}) {
            if (m != null) {
                for (String type : m.values()) {
                    if (type == null || !TYPE.matcher(type.trim()).matches()) {
                        throw new IllegalArgumentException("Invalid type: " + type);
                    }
                }
            }
        }
    }

    private static void requireIdent(String v, String what) {
        if (v == null || !IDENT.matcher(v).matches()) {
            throw new IllegalArgumentException("Invalid " + what + ": " + v);
        }
    }

    private static String override(Map<String, String> map, String name) {
        if (map == null || name == null) {
            return null;
        }
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name) && e.getValue() != null && !e.getValue().isBlank()) {
                return e.getValue();
            }
        }
        return null;
    }
}
