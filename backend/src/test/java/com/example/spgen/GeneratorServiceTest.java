package com.example.spgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.spgen.Models.ColumnMeta;
import com.example.spgen.Models.GenerateRequest;
import com.example.spgen.Models.GenerateResponse;
import com.example.spgen.Models.TableRef;

class GeneratorServiceTest {

    private static ColumnMeta col(String n, String t, Integer len, Integer scale) {
        ColumnMeta m = new ColumnMeta();
        m.name = n;
        m.dataType = t;
        m.length = len;
        m.scale = scale;
        return m;
    }

    private final GeneratorService service = new GeneratorService((lib, file) -> {
        List<ColumnMeta> l = new ArrayList<>();
        if (file.equalsIgnoreCase("CUSTOMER")) {
            l.add(col("CUSTID", "DECIMAL", 9, 0));
            l.add(col("NAME", "VARCHAR", 40, null));
        } else if (file.equalsIgnoreCase("ORDERS")) {
            l.add(col("ORDID", "INTEGER", 4, 0));
            l.add(col("CUSTID", "DECIMAL", 9, 0));
            l.add(col("TOTAL", "DECIMAL", 11, 2));
        }
        return l;
    });

    private GenerateRequest req(String query, String... files) {
        GenerateRequest r = new GenerateRequest();
        r.query = query;
        for (String f : files) {
            TableRef t = new TableRef();
            t.library = "MYLIB";
            t.file = f;
            r.tables.add(t);
        }
        r.procedureLibrary = "MYLIB";
        r.procedureName = "GETCUST";
        return r;
    }

    @Test
    void resolvesColumnsAliasesAndNamedParams() {
        GenerateResponse res = service.generate(req(
            "SELECT c.CUSTID, c.NAME AS CUSTNAME, o.TOTAL FROM MYLIB.CUSTOMER c JOIN ORDERS o ON o.CUSTID = c.CUSTID WHERE c.CUSTID = :ID;",
            "CUSTOMER", "ORDERS"));
        assertTrue(res.complete);
        assertEquals("CUSTNAME", res.columns.get(1).name);
        assertEquals("VARCHAR(40)", res.columns.get(1).type);
        assertEquals("DECIMAL(11,2)", res.columns.get(2).type);
        assertEquals("ID", res.parameters.get(0).name);
        assertEquals("DECIMAL(9,0)", res.parameters.get(0).type);
        assertTrue(res.source.contains("IN ID DECIMAL(9,0)"));
        assertTrue(res.source.contains("DYNAMIC RESULT SETS 1"));
        assertFalse(res.source.contains("WHERE c.CUSTID = :ID;;"));
    }

    @Test
    void positionalMarkersBecomeNamedParams() {
        GenerateResponse res = service.generate(req(
            "SELECT * FROM CUSTOMER WHERE NAME = ? AND CUSTID > ?", "CUSTOMER"));
        assertTrue(res.complete);
        assertEquals(2, res.columns.size());
        assertEquals("VARCHAR(40)", res.parameters.get(0).type);
        assertEquals("DECIMAL(9,0)", res.parameters.get(1).type);
        assertTrue(res.source.contains(":P1"));
        assertFalse(res.source.contains("?"));
    }

    @Test
    void expressionsNeedUserTypes() {
        GenerateRequest r = req("SELECT SUM(TOTAL) FROM ORDERS", "ORDERS");
        GenerateResponse res = service.generate(r);
        assertFalse(res.complete);
        assertEquals("EXPR_1", res.columns.get(0).name);

        r.columnTypes = Map.of("EXPR_1", "DECIMAL(15,2)");
        res = service.generate(r);
        assertTrue(res.complete);
        assertTrue(res.source.contains("EXPR_1 DECIMAL(15,2)"));
    }

    @Test
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class,
            () -> service.generate(req("DELETE FROM CUSTOMER", "CUSTOMER")));
        assertThrows(IllegalArgumentException.class,
            () -> service.generate(req("SELECT * FROM OTHER", "CUSTOMER")));
        GenerateRequest r = req("SELECT * FROM CUSTOMER", "CUSTOMER");
        r.procedureName = "X; DROP";
        assertThrows(IllegalArgumentException.class, () -> service.generate(r));
    }
}
