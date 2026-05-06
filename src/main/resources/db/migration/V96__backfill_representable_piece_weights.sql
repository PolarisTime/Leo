update so_sales_order_item
set piece_weight_ton = round((weight_ton / quantity::numeric), 3)
where quantity > 0
  and round((round((weight_ton / quantity::numeric), 3) * quantity::numeric), 3) = weight_ton
  and piece_weight_ton <> round((weight_ton / quantity::numeric), 3);

update so_sales_outbound_item
set piece_weight_ton = round((weight_ton / quantity::numeric), 3)
where quantity > 0
  and round((round((weight_ton / quantity::numeric), 3) * quantity::numeric), 3) = weight_ton
  and piece_weight_ton <> round((weight_ton / quantity::numeric), 3);
