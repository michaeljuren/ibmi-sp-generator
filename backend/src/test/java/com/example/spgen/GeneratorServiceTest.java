package com.example.spgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.spgen.Models.ColumnMeta;
import com.example.spgen.Models.GenerateRequest;
import com.example.spgen.Models.GenerateResponse;
import com.example.spgen.QueryDescriber.Description;

class GeneratorServiceTest {

    private static ColumnMeta col(String n, String t, Integer len, Integer scale) {
        ColumnMeta m = new ColumnMeta();
        m.systemName = n;
        m.dataType = t;
        m.length = len;
        m.scale = scale;
        return m;
    }

    private Description described = new Description();
    private String preparedSql;

    private final GeneratorService service = new GeneratorService((sql, count) -> {
        preparedSql = sql;
        return described;
    });

    private GenerateRequest req(String query) {
        GenerateRequest r = new GenerateRequest();
        r.query = query;
        r.procedureLibrary = "MYLIB";
        r.procedureName = "GETCUST";
        return r;
    }

    private GenerateRequest fn(GenerateRequest r) {
        r.routineType = "FUNCTION";
        return r;
    }

    @Test
    void functionColumnsAndParamsComeFromDb2() {
        described.columns.add(col("CUSTID", "DECIMAL", 9, 0));
        described.columns.add(col("CUSTNAME", "VARCHAR", 40, null));
        described.columns.add(col(null, "DECIMAL", 11, 2));
        described.params.add(col(null, "DECIMAL", 9, 0));
        GenerateResponse res = service.generate(fn(req(
            "WITH T AS (SELECT * FROM CUSTOMER) SELECT CUSTID, NAME AS CUSTNAME, 1.5 FROM T WHERE CUSTID = :ID;")));
        assertTrue(res.complete);
        assertEquals("VARCHAR(40)", res.columns.get(1).type);
        assertEquals("EXPR_3", res.columns.get(2).name);
        assertEquals("DECIMAL(11,2)", res.columns.get(2).type);
        assertEquals("DECIMAL(9,0)", res.parameters.get(0).type);
        assertTrue(preparedSql.contains("CUSTID = ?"));
        assertTrue(res.source.contains("RETURNS TABLE"));
        assertTrue(res.source.contains("CUSTID = ID"));
        assertFalse(res.source.contains(":ID"));
    }

    @Test
    void procedureIgnoresColumnsButTypesParams() {
        described.columns.add(col("X", "DECIMAL", 15, 2));
        described.params.add(col(null, "DECIMAL", 9, 0));
        GenerateResponse res = service.generate(req(
            "SELECT SUM(TOTAL) FROM ORDERS UNION ALL SELECT 1 FROM ORDERS WHERE CUSTID = :ID"));
        assertTrue(res.complete);
        assertTrue(res.columns.isEmpty());
        assertTrue(res.source.contains("CREATE OR REPLACE PROCEDURE"));
        assertTrue(res.source.contains("IN ID DECIMAL(9,0)"));
        assertFalse(res.source.contains("RETURNS TABLE"));
    }

    @Test
    void positionalMarkersBecomeNamedParams() {
        described.params.add(col(null, "VARCHAR", 40, null));
        described.params.add(col(null, "DECIMAL", 9, 0));
        GenerateResponse res = service.generate(req(
            "SELECT * FROM CUSTOMER WHERE NAME = ? AND CUSTID > ?"));
        assertTrue(res.complete);
        assertEquals("VARCHAR(40)", res.parameters.get(0).type);
        assertEquals("DECIMAL(9,0)", res.parameters.get(1).type);
        assertTrue(res.source.contains("NAME = :P1"));
        assertEquals("SELECT * FROM CUSTOMER WHERE NAME = ? AND CUSTID > ?", preparedSql);
    }

    @Test
    void repeatedNamedParamUsesFirstOccurrence() {
        described.params.add(col(null, "INTEGER", 4, 0));
        described.params.add(col(null, "VARCHAR", 10, null));
        described.params.add(col(null, "INTEGER", 4, 0));
        GenerateResponse res = service.generate(req(
            "SELECT 1 FROM ORDERS WHERE ORDID = :A AND CUSTID = :B OR ORDID = :A"));
        assertEquals(2, res.parameters.size());
        assertEquals("INTEGER", res.parameters.get(0).type);
    }

    @Test
    void undescribedTypesNeedUserTypes() {
        described.columns.add(col(null, null, null, null));
        described.params.add(col(null, null, null, null));
        GenerateRequest r = fn(req("SELECT ? FROM ORDERS"));
        GenerateResponse res = service.generate(r);
        assertFalse(res.complete);
        assertEquals("EXPR_1", res.columns.get(0).name);

        r.columnTypes = Map.of("EXPR_1", "DECIMAL(15,2)");
        r.parameterTypes = Map.of("P1", "INTEGER");
        res = service.generate(r);
        assertTrue(res.complete);
        assertTrue(res.source.contains("EXPR_1 DECIMAL(15,2)"));
    }

    @Test
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class,
            () -> service.generate(req("DELETE FROM CUSTOMER")));
        assertThrows(IllegalArgumentException.class,
            () -> service.generate(req("SELECT 1 FROM CUSTOMER; DROP TABLE CUSTOMER")));
        GenerateRequest r = req("SELECT * FROM CUSTOMER");
        r.procedureName = "X; DROP";
        assertThrows(IllegalArgumentException.class, () -> service.generate(r));
    }
}
