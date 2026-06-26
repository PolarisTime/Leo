-- Render the sales-order A5 summary row as labels plus current-record totals.
UPDATE sys_print_template
SET template_html = replace(
        template_html,
        E'LODOP.ADD_PRINT_TEXT({{sumTop}},295,41,24,"{{totalQuantity}}");\nLODOP.SET_PRINT_STYLEA(0,"Alignment",2);\nLODOP.SET_PRINT_STYLEA(0,"FontSize",12);\nLODOP.SET_PRINT_STYLEA(0,"Bold",1);\nLODOP.ADD_PRINT_TEXT({{sumTop}},415,71,24,"{{totalWeight}}");\nLODOP.SET_PRINT_STYLEA(0,"Alignment",2);\nLODOP.SET_PRINT_STYLEA(0,"FontSize",12);\nLODOP.SET_PRINT_STYLEA(0,"Bold",1);',
        E'LODOP.ADD_PRINT_TEXT({{sumTop}},240,61,24,"合计件数");\nLODOP.SET_PRINT_STYLEA(0,"Alignment",2);\nLODOP.SET_PRINT_STYLEA(0,"FontSize",12);\nLODOP.SET_PRINT_STYLEA(0,"Bold",1);\nLODOP.ADD_PRINT_TEXT({{sumTop}},295,41,24,"{{totalQuantity}}");\nLODOP.SET_PRINT_STYLEA(0,"Alignment",2);\nLODOP.SET_PRINT_STYLEA(0,"FontSize",12);\nLODOP.SET_PRINT_STYLEA(0,"Bold",1);\nLODOP.ADD_PRINT_TEXT({{sumTop}},345,61,24,"合计吨位");\nLODOP.SET_PRINT_STYLEA(0,"Alignment",2);\nLODOP.SET_PRINT_STYLEA(0,"FontSize",12);\nLODOP.SET_PRINT_STYLEA(0,"Bold",1);\nLODOP.ADD_PRINT_TEXT({{sumTop}},415,71,24,"{{totalWeight}}T");\nLODOP.SET_PRINT_STYLEA(0,"Alignment",2);\nLODOP.SET_PRINT_STYLEA(0,"FontSize",12);\nLODOP.SET_PRINT_STYLEA(0,"Bold",1);'
    ),
    version_no = GREATEST(version_no, 3) + 1,
    updated_at = now()
WHERE id = 700540000000000023
  AND bill_type = 'sales-order'
  AND template_html LIKE '%LODOP.ADD_PRINT_TEXT({{sumTop}},295,41,24,"{{totalQuantity}}");%'
  AND template_html NOT LIKE '%LODOP.ADD_PRINT_TEXT({{sumTop}},240,61,24,"合计件数");%';
