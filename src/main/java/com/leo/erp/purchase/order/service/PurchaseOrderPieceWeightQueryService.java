package com.leo.erp.purchase.order.service;

import com.leo.erp.purchase.order.web.dto.PieceWeightResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PurchaseOrderPieceWeightQueryService {

    private final JdbcTemplate jdbc;

    public PurchaseOrderPieceWeightQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PieceWeightResponse> getPieceWeights(Long itemId) {
        return jdbc.query("""
                SELECT pw.piece_no, pw.weight_ton,
                       COALESCE(so.order_no, '') AS sales_order_no
                FROM po_purchase_order_item_piece_weight pw
                LEFT JOIN so_sales_order_item soi ON soi.id = pw.sales_order_item_id
                LEFT JOIN so_sales_order so ON so.id = soi.order_id
                WHERE pw.purchase_order_item_id = ?
                ORDER BY pw.piece_no
                """,
                (rs, rowNum) -> new PieceWeightResponse(
                        rs.getInt("piece_no"),
                        rs.getBigDecimal("weight_ton"),
                        rs.getString("sales_order_no")
                ),
                itemId);
    }

    public List<PieceWeightResponse> getPieceWeightsBySalesOrderItemId(Long salesOrderItemId) {
        return jdbc.query("""
                SELECT pw.piece_no, pw.weight_ton,
                       COALESCE(so.order_no, '') AS sales_order_no
                FROM po_purchase_order_item_piece_weight pw
                LEFT JOIN so_sales_order_item soi ON soi.id = pw.sales_order_item_id
                LEFT JOIN so_sales_order so ON so.id = soi.order_id
                WHERE pw.sales_order_item_id = ?
                ORDER BY pw.piece_no
                """,
                (rs, rowNum) -> new PieceWeightResponse(
                        rs.getInt("piece_no"),
                        rs.getBigDecimal("weight_ton"),
                        rs.getString("sales_order_no")
                ),
                salesOrderItemId);
    }
}
