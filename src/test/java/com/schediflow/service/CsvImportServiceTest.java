package com.schediflow.service;

import com.schediflow.exception.BadRequestException;
import com.schediflow.security.TenantContext;
import com.schediflow.service.csv.CsvRow;
import com.schediflow.service.csv.CsvRowException;
import com.schediflow.service.csv.CsvRowWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CsvImportServiceTest {

    @Mock CsvRowWriter rowWriter;

    CsvImportService service;

    private static final Long TENANT_ID = 1L;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        service = new CsvImportService(rowWriter);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void importsEveryValidRow() {
        when(rowWriter.upsertRoom(eq(TENANT_ID), any(CsvRow.class))).thenReturn(CsvRowWriter.Outcome.IMPORTED);

        var response = service.importCsv("rooms", csv("name,type\nA1,CLASSROOM\nA2,LAB\n"));

        assertThat(response.entityType()).isEqualTo("rooms");
        assertThat(response.totalRows()).isEqualTo(2);
        assertThat(response.imported()).isEqualTo(2);
        assertThat(response.updated()).isZero();
        assertThat(response.skipped()).isZero();
        assertThat(response.errors()).isEmpty();
    }

    @Test
    void countsUpdatesSeparatelyFromInserts() {
        when(rowWriter.upsertRoom(eq(TENANT_ID), any(CsvRow.class)))
                .thenReturn(CsvRowWriter.Outcome.IMPORTED, CsvRowWriter.Outcome.UPDATED);

        var response = service.importCsv("rooms", csv("name,type\nA1,CLASSROOM\nA2,LAB\n"));

        assertThat(response.imported()).isEqualTo(1);
        assertThat(response.updated()).isEqualTo(1);
    }

    @Test
    void failingRowIsReportedAndOthersStillApply() {
        when(rowWriter.upsertRoom(eq(TENANT_ID), any(CsvRow.class)))
                .thenReturn(CsvRowWriter.Outcome.IMPORTED)
                .thenThrow(new CsvRowException("type", "Invalid type"))
                .thenReturn(CsvRowWriter.Outcome.IMPORTED);

        var response = service.importCsv("rooms", csv("name,type\nA1,CLASSROOM\nA2,POOL\nA3,LAB\n"));

        assertThat(response.totalRows()).isEqualTo(3);
        assertThat(response.imported()).isEqualTo(2);
        assertThat(response.skipped()).isEqualTo(1);
        assertThat(response.errors()).hasSize(1);
        assertThat(response.errors().get(0).row()).isEqualTo(3);
        assertThat(response.errors().get(0).field()).isEqualTo("type");
        assertThat(response.errors().get(0).error()).isEqualTo("Invalid type");
    }

    @Test
    void unexpectedRowFailure_isReportedWithoutField() {
        when(rowWriter.upsertRoom(eq(TENANT_ID), any(CsvRow.class)))
                .thenThrow(new IllegalStateException("boom"));

        var response = service.importCsv("rooms", csv("name,type\nA1,CLASSROOM\n"));

        assertThat(response.skipped()).isEqualTo(1);
        assertThat(response.errors().get(0).field()).isNull();
        assertThat(response.errors().get(0).error()).isEqualTo("Row could not be saved");
    }

    @Test
    void headersAreCaseInsensitive() {
        when(rowWriter.upsertTeacher(eq(TENANT_ID), any(CsvRow.class))).thenReturn(CsvRowWriter.Outcome.IMPORTED);

        var response = service.importCsv("teachers", csv("EMAIL,DisplayName\na@b.edu,Ann\n"));

        assertThat(response.imported()).isEqualTo(1);
    }

    @Test
    void blankLinesAreNotCountedAsRows() {
        when(rowWriter.upsertRoom(eq(TENANT_ID), any(CsvRow.class))).thenReturn(CsvRowWriter.Outcome.IMPORTED);

        var response = service.importCsv("rooms", csv("name,type\nA1,CLASSROOM\n,\n"));

        assertThat(response.totalRows()).isEqualTo(1);
    }

    @Test
    void unknownEntityType_throwsBadRequest() {
        assertThatThrownBy(() -> service.importCsv("widgets", csv("name\nA1\n")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("widgets");
        verifyNoInteractions(rowWriter);
    }

    @Test
    void missingRequiredColumn_throwsBadRequest() {
        assertThatThrownBy(() -> service.importCsv("rooms", csv("name,capacity\nA1,30\n")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("type");
    }

    @Test
    void emptyFile_throwsBadRequest() {
        assertThatThrownBy(() -> service.importCsv("rooms", csv("")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void malformedCsv_throwsBadRequest() {
        assertThatThrownBy(() -> service.importCsv("rooms", csv("name,type\n\"unclosed,CLASSROOM\n")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void duplicateHeaders_throwBadRequest() {
        assertThatThrownBy(() -> service.importCsv("rooms", csv("name,type,name\nA1,LAB,A2\n")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void tooManyRows_throwsBadRequest() {
        StringBuilder body = new StringBuilder("name,type\n");
        for (int i = 0; i <= CsvImportService.MAX_ROWS; i++) {
            body.append("Room").append(i).append(",CLASSROOM\n");
        }

        assertThatThrownBy(() -> service.importCsv("rooms", csv(body.toString())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("1000");
    }

    @Test
    void fileOverSizeLimit_throwsBadRequest() {
        MultipartFile oversized = new MockMultipartFile(
                "file", "big.csv", "text/csv", new byte[(int) CsvImportService.MAX_FILE_BYTES + 1]);

        assertThatThrownBy(() -> service.importCsv("rooms", oversized))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("5MB");
    }

    private static MultipartFile csv(String content) {
        return new MockMultipartFile("file", "data.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
}
