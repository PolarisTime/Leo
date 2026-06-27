package com.leo.erp.system.printtemplate.service;

import java.util.List;

public record PrintXlsxExportLayout(
        String moduleKey,
        String templateResource,
        String sheetName,
        String filenameSuffix,
        int rowsPerPage,
        int detailStartRow,
        int detailEndColumn,
        List<HeaderCell> headerCells,
        List<DetailColumn> detailColumns,
        Summary summary,
        PieceWeight pieceWeight
) {

    public PrintXlsxExportLayout {
        headerCells = headerCells == null ? List.of() : List.copyOf(headerCells);
        detailColumns = detailColumns == null ? List.of() : List.copyOf(detailColumns);
    }

    public record HeaderCell(String field, String cell) {
    }

    public record DetailColumn(String field, int column, String type) {
    }

    public record Summary(int row, List<SummaryCell> cells) {
        public Summary {
            cells = cells == null ? List.of() : List.copyOf(cells);
        }
    }

    public record SummaryCell(
            String field,
            int column,
            String type,
            String text,
            int scale,
            String suffix
    ) {
    }

    public record PieceWeight(String replacement, int scale, List<SuppressRule> suppressWhen) {
        public PieceWeight {
            suppressWhen = suppressWhen == null ? List.of() : List.copyOf(suppressWhen);
        }
    }

    public record SuppressRule(String field, List<String> values) {
        public SuppressRule {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }
}
