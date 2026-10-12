package com.example.spgen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.JdbcNamedParameter;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;

/** Parses the query text only; the query is never executed. */
public class QueryParser {

    public static class QueryTable {
        public String schema;
        public String name;
        public String alias;
    }

    public static class SelectColumn {
        /** Output name (alias or column name); null for unaliased expressions and wildcards. */
        public String name;
        /** Qualifier used in the query (alias/table), may be null. */
        public String qualifier;
        public String columnName;
        public String expression;
        public boolean wildcard;
    }

    public static class ParamUse {
        public String name;
        public String qualifier;
        public String columnName;
    }

    public static class ParsedQuery {
        public List<SelectColumn> columns = new ArrayList<>();
        public List<QueryTable> tables = new ArrayList<>();
        public Map<String, ParamUse> params = new LinkedHashMap<>();
        /** Query text with ? markers replaced by :Pn and no trailing semicolon. */
        public String sql;
    }

    private static final Pattern NAMED = Pattern.compile(":([A-Za-z_][A-Za-z0-9_$#@]*)");

    public ParsedQuery parse(String query) {
        String sql = query.trim().replaceAll(";+\\s*$", "");
        Statement st;
        try {
            st = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            throw new IllegalArgumentException("Could not parse the query: " + rootMessage(e));
        }
        if (!(st instanceof PlainSelect) || ((PlainSelect) st).getWithItemsList() != null) {
            throw new IllegalArgumentException(
                "Only a single plain SELECT statement is supported (no UNION, WITH or DML/DDL).");
        }
        PlainSelect ps = (PlainSelect) st;
        ParsedQuery pq = new ParsedQuery();

        collectTable(ps.getFromItem(), pq);
        if (ps.getJoins() != null) {
            for (Join j : ps.getJoins()) {
                collectTable(j.getRightItem(), pq);
            }
        }
        if (pq.tables.isEmpty()) {
            throw new IllegalArgumentException("The query must select from at least one table.");
        }

        for (SelectItem<?> item : ps.getSelectItems()) {
            pq.columns.add(toColumn(item));
        }

        pq.sql = registerParams(sql, pq);
        inferParamColumns(ps, pq);
        return pq;
    }

    private static String rootMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null) {
            r = r.getCause();
        }
        String m = r.getMessage() == null ? r.toString() : r.getMessage();
        return m.split("\\R")[0];
    }

    private void collectTable(Object fromItem, ParsedQuery pq) {
        if (fromItem instanceof Table) {
            Table t = (Table) fromItem;
            QueryTable q = new QueryTable();
            q.schema = t.getSchemaName() == null ? null : unquote(t.getSchemaName());
            q.name = unquote(t.getName());
            Alias a = t.getAlias();
            q.alias = a == null ? null : unquote(a.getName());
            pq.tables.add(q);
        } else if (fromItem != null) {
            throw new IllegalArgumentException(
                "Subselects/derived tables in FROM are not supported: " + fromItem);
        }
    }

    private SelectColumn toColumn(SelectItem<?> item) {
        SelectColumn c = new SelectColumn();
        Expression e = item.getExpression();
        if (e instanceof AllTableColumns) {
            c.wildcard = true;
            c.qualifier = unquote(((AllTableColumns) e).getTable().getName());
        } else if (e instanceof AllColumns) {
            c.wildcard = true;
        } else {
            String alias = item.getAlias() == null ? null : unquote(item.getAlias().getName());
            if (e instanceof Column) {
                Column col = (Column) e;
                c.columnName = unquote(col.getColumnName());
                c.qualifier = col.getTable() == null || col.getTable().getName() == null
                    ? null : unquote(col.getTable().getName());
                c.name = alias != null ? alias : c.columnName;
            } else {
                c.expression = e.toString();
                c.name = alias;
            }
        }
        return c;
    }

    /** Finds ? and :name markers outside string literals, registering them and rewriting ? to :Pn. */
    private String registerParams(String sql, ParsedQuery pq) {
        StringBuilder out = new StringBuilder();
        char quote = 0;
        int n = 0;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (quote != 0) {
                out.append(ch);
                if (ch == quote) {
                    quote = 0;
                }
            } else if (ch == '\'' || ch == '"') {
                quote = ch;
                out.append(ch);
            } else if (ch == '?') {
                String name = "P" + (++n);
                addParam(pq, name);
                out.append(':').append(name);
            } else if (ch == ':') {
                Matcher m = NAMED.matcher(sql);
                if (m.find(i) && m.start() == i) {
                    addParam(pq, m.group(1).toUpperCase());
                    out.append(m.group());
                    i = m.end() - 1;
                } else {
                    out.append(ch);
                }
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private void addParam(ParsedQuery pq, String name) {
        if (!pq.params.containsKey(name)) {
            ParamUse p = new ParamUse();
            p.name = name;
            pq.params.put(name, p);
        }
    }

    private void inferParamColumns(PlainSelect ps, ParsedQuery pq) {
        ExpressionVisitorAdapter v = new ExpressionVisitorAdapter() {
            @Override
            protected void visitBinaryExpression(BinaryExpression e) {
                link(e.getLeftExpression(), e.getRightExpression());
                link(e.getRightExpression(), e.getLeftExpression());
                super.visitBinaryExpression(e);
            }

            @Override
            public void visit(Between b) {
                link(b.getLeftExpression(), b.getBetweenExpressionStart());
                link(b.getLeftExpression(), b.getBetweenExpressionEnd());
                super.visit(b);
            }

            @Override
            public void visit(InExpression in) {
                if (in.getRightExpression() instanceof ExpressionList) {
                    for (Object x : (ExpressionList<?>) in.getRightExpression()) {
                        link(in.getLeftExpression(), (Expression) x);
                    }
                }
                super.visit(in);
            }

            private void link(Expression colSide, Expression paramSide) {
                if (!(colSide instanceof Column)) {
                    return;
                }
                String pname = null;
                if (paramSide instanceof JdbcNamedParameter) {
                    pname = ((JdbcNamedParameter) paramSide).getName().toUpperCase();
                } else if (paramSide instanceof JdbcParameter) {
                    pname = "P" + ((JdbcParameter) paramSide).getIndex();
                }
                ParamUse p = pname == null ? null : pq.params.get(pname);
                if (p != null && p.columnName == null) {
                    Column c = (Column) colSide;
                    p.columnName = unquote(c.getColumnName());
                    p.qualifier = c.getTable() == null || c.getTable().getName() == null
                        ? null : unquote(c.getTable().getName());
                }
            }
        };
        if (ps.getWhere() != null) {
            ps.getWhere().accept(v);
        }
        if (ps.getHaving() != null) {
            ps.getHaving().accept(v);
        }
        if (ps.getJoins() != null) {
            for (Join j : ps.getJoins()) {
                if (j.getOnExpressions() != null) {
                    j.getOnExpressions().forEach(x -> x.accept(v));
                }
            }
        }
    }

    static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s.toUpperCase();
    }
}
