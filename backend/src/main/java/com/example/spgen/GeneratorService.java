package com.example.spgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.example.spgen.Models.ColumnInfo;
import com.example.spgen.Models.ColumnMeta;
import com.example.spgen.Models.GenerateRequest;
import com.example.spgen.Models.GenerateResponse;
import com.example.spgen.Models.ParamInfo;
import com.example.spgen.Models.TableRef;
import com.example.spgen.QueryParser.ParamUse;
import com.example.spgen.QueryParser.ParsedQuery;
import com.example.spgen.QueryParser.QueryTable;
import com.example.spgen.QueryParser.SelectColumn;

@Service
public class GeneratorService {

    private static final Pattern IDENT = Pattern.compile("^[A-Za-z][A-Za-z0-9_$#@]{0,127}$");
    private static final Pattern TYPE = Pattern.compile("^[A-Za-z ]+(\\(\\d+(\\s*,\\s*\\d+)?\\))?$");

    private static class Resolved {
        QueryTable q;
        TableRef ref;
        List<ColumnMeta> cols;
    }

    private final ColumnMetadataProvider metadata;
    private final QueryParser parser = new QueryParser();

    public GeneratorService(ColumnMetadataProvider metadata) {
        this.metadata = metadata;
    }

    public GenerateResponse generate(GenerateRequest req) {
        validate(req);
        ParsedQuery pq = parser.parse(req.query);
        GenerateResponse res = new GenerateResponse();
        List<Resolved> tables = resolveTables(req, pq);

        int pos = 0;
        for (SelectColumn sc : pq.columns) {
            pos++;
            if (sc.wildcard) {
                for (Resolved t : tables) {
                    if (sc.qualifier == null || matches(t.q, sc.qualifier)) {
                        for (ColumnMeta m : t.cols) {
                            res.columns.add(fromMeta(m.name, t, m));
                        }
                    }
                }
            } else if (sc.columnName != null) {
                res.columns.add(resolveColumn(sc, tables, res, req));
            } else {
                res.columns.add(expressionColumn(sc, pos, req));
            }
        }

        for (ParamUse p : pq.params.values()) {
            res.parameters.add(resolveParam(p, tables, req));
        }

        res.complete = res.columns.stream().allMatch(c -> c.resolved)
            && res.parameters.stream().allMatch(p -> p.resolved);
        if (res.complete) {
            res.source = ProcedureGenerator.generate(req.procedureLibrary, req.procedureName,
                res.columns, res.parameters, pq.sql);
        } else {
            res.warnings.add("Some types could not be determined from QSYS2.SYSCOLUMNS; please supply them.");
        }
        return res;
    }

    private void validate(GenerateRequest req) {
        if (req.query == null || req.query.isBlank()) {
            throw new IllegalArgumentException("A query is required.");
        }
        if (req.tables == null || req.tables.isEmpty()) {
            throw new IllegalArgumentException("At least one library/file is required.");
        }
        for (TableRef t : req.tables) {
            requireIdent(t.library, "library");
            requireIdent(t.file, "file");
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

    private List<Resolved> resolveTables(GenerateRequest req, ParsedQuery pq) {
        List<Resolved> out = new ArrayList<>();
        for (QueryTable q : pq.tables) {
            TableRef ref = null;
            for (TableRef t : req.tables) {
                boolean nameOk = t.file.equalsIgnoreCase(q.name);
                boolean libOk = q.schema == null || t.library.equalsIgnoreCase(q.schema);
                if (nameOk && libOk) {
                    ref = t;
                    break;
                }
            }
            if (ref == null) {
                throw new IllegalArgumentException("Table " + (q.schema != null ? q.schema + "." : "")
                    + q.name + " is used in the query but not listed in the library/file list.");
            }
            Resolved r = new Resolved();
            r.q = q;
            r.ref = ref;
            r.cols = metadata.getColumns(ref.library, ref.file);
            if (r.cols.isEmpty()) {
                throw new IllegalArgumentException("No columns found in QSYS2.SYSCOLUMNS for "
                    + ref.library + "/" + ref.file + ".");
            }
            out.add(r);
        }
        return out;
    }

    private static boolean matches(QueryTable q, String qualifier) {
        return qualifier.equalsIgnoreCase(q.alias) || (q.alias == null && qualifier.equalsIgnoreCase(q.name));
    }

    private ColumnInfo fromMeta(String name, Resolved t, ColumnMeta m) {
        ColumnInfo c = new ColumnInfo();
        c.name = name;
        c.type = TypeMapper.toSqlType(m);
        c.resolved = true;
        c.nullable = m.nullable;
        c.source = t.ref.library.toUpperCase() + "/" + t.ref.file.toUpperCase() + "." + m.name;
        return c;
    }

    private ColumnMeta[] find(String qualifier, String column, List<Resolved> tables, Resolved[] holder,
                              List<String> warnings) {
        List<Resolved> hits = new ArrayList<>();
        List<ColumnMeta> metas = new ArrayList<>();
        for (Resolved t : tables) {
            if (qualifier != null && !matches(t.q, qualifier)) {
                continue;
            }
            for (ColumnMeta m : t.cols) {
                if (m.name.equalsIgnoreCase(column)) {
                    hits.add(t);
                    metas.add(m);
                }
            }
        }
        if (hits.isEmpty()) {
            return null;
        }
        if (hits.size() > 1) {
            warnings.add("Column " + column + " is ambiguous; using "
                + hits.get(0).ref.library + "/" + hits.get(0).ref.file + ".");
        }
        holder[0] = hits.get(0);
        return new ColumnMeta[] {metas.get(0)};
    }

    private ColumnInfo resolveColumn(SelectColumn sc, List<Resolved> tables, GenerateResponse res,
                                     GenerateRequest req) {
        Resolved[] holder = new Resolved[1];
        ColumnMeta[] m = find(sc.qualifier, sc.columnName, tables, holder, res.warnings);
        String override = override(req.columnTypes, sc.name);
        if (override != null) {
            return userColumn(sc.name, override);
        }
        if (m == null) {
            ColumnInfo c = new ColumnInfo();
            c.name = sc.name;
            c.source = "Column " + sc.columnName + " not found in the listed files";
            return c;
        }
        return fromMeta(sc.name, holder[0], m[0]);
    }

    private ColumnInfo expressionColumn(SelectColumn sc, int pos, GenerateRequest req) {
        String name = sc.name != null ? sc.name : "EXPR_" + pos;
        String override = override(req.columnTypes, name);
        if (override != null) {
            return userColumn(name, override);
        }
        ColumnInfo c = new ColumnInfo();
        c.name = name;
        c.source = sc.expression;
        return c;
    }

    private ColumnInfo userColumn(String name, String type) {
        ColumnInfo c = new ColumnInfo();
        c.name = name;
        c.type = type.trim().toUpperCase();
        c.resolved = true;
        c.nullable = true;
        c.source = "user";
        return c;
    }

    private ParamInfo resolveParam(ParamUse p, List<Resolved> tables, GenerateRequest req) {
        ParamInfo info = new ParamInfo();
        info.name = p.name;
        String override = override(req.parameterTypes, p.name);
        if (override != null) {
            info.type = override.trim().toUpperCase();
            info.resolved = true;
            info.source = "user";
            return info;
        }
        if (p.columnName != null) {
            Resolved[] holder = new Resolved[1];
            ColumnMeta[] m = find(p.qualifier, p.columnName, tables, holder, new ArrayList<>());
            if (m != null) {
                info.type = TypeMapper.toSqlType(m[0]);
                info.resolved = true;
                info.source = holder[0].ref.library.toUpperCase() + "/" + holder[0].ref.file.toUpperCase()
                    + "." + m[0].name;
                return info;
            }
        }
        info.source = "Type could not be inferred";
        return info;
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
