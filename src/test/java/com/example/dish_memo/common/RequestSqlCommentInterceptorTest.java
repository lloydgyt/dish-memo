package com.example.dish_memo.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestSqlCommentInterceptorTest {

    @Test
    void appendsRequestIdCommentToCoreCrudSql() {
        assertThat(RequestSqlCommentInterceptor.appendRequestIdComment("SELECT * FROM dish_record", "req_1"))
                .isEqualTo("SELECT * FROM dish_record /* request_id: req_1 */");
        assertThat(RequestSqlCommentInterceptor.appendRequestIdComment("INSERT INTO dish_record (id) VALUES (?)", "req_1"))
                .isEqualTo("INSERT INTO dish_record (id) VALUES (?) /* request_id: req_1 */");
        assertThat(RequestSqlCommentInterceptor.appendRequestIdComment("UPDATE dish_record SET name = ?", "req_1"))
                .isEqualTo("UPDATE dish_record SET name = ? /* request_id: req_1 */");
        assertThat(RequestSqlCommentInterceptor.appendRequestIdComment("DELETE FROM dish_record WHERE id = ?", "req_1"))
                .isEqualTo("DELETE FROM dish_record WHERE id = ? /* request_id: req_1 */");
    }

    @Test
    void preservesTrailingSemicolonAndAvoidsDuplicateTraceComment() {
        String sql = "SELECT * FROM dish_record;";

        String commented = RequestSqlCommentInterceptor.appendRequestIdComment(sql, "req_1");
        String secondPass = RequestSqlCommentInterceptor.appendRequestIdComment(commented, "req_2");

        assertThat(commented).isEqualTo("SELECT * FROM dish_record /* request_id: req_1 */;");
        assertThat(secondPass).isEqualTo(commented);
    }

    @Test
    void sanitizesRequestIdForSqlComment() {
        String commented = RequestSqlCommentInterceptor.appendRequestIdComment("SELECT 1", "req_1 */ DROP TABLE dish_record");

        assertThat(commented).isEqualTo("SELECT 1 /* request_id: req_1____DROP_TABLE_dish_record */");
    }

    @Test
    void fingerprintRemovesRequestIdComment() {
        String fingerprint = RequestSqlCommentInterceptor.fingerprint(
                "SELECT *\nFROM dish_record WHERE id = ? /* request_id: req_1 */"
        );

        assertThat(fingerprint).isEqualTo("SELECT * FROM dish_record WHERE id = ?");
    }

    @Test
    void extractsDocumentedDatabaseTableLabel() {
        assertThat(RequestSqlCommentInterceptor.dbTable("SELECT * FROM dish_record WHERE id = ?"))
                .isEqualTo("dish_memo:dish_record");
        assertThat(RequestSqlCommentInterceptor.dbTable("UPDATE `dish_memo`.`dish_record` SET name = ?"))
                .isEqualTo("dish_memo:dish_record");
    }
}
