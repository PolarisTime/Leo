-- Print the sales-order A5 totals before each generated page break.
UPDATE sys_print_template
SET template_html = replace(
        template_html,
        E'{{#if needsNewPage}}\nLODOP.NewPage();',
        E'{{#if needsNewPage}}\nLODOP.ADD_PRINT_TEXT({{sumTop}},295,41,24,"{{totalQuantity}}");\nLODOP.SET_PRINT_STYLEA(0,"Alignment",2);\nLODOP.SET_PRINT_STYLEA(0,"FontSize",12);\nLODOP.SET_PRINT_STYLEA(0,"Bold",1);\nLODOP.ADD_PRINT_TEXT({{sumTop}},415,71,24,"{{totalWeight}}");\nLODOP.SET_PRINT_STYLEA(0,"Alignment",2);\nLODOP.SET_PRINT_STYLEA(0,"FontSize",12);\nLODOP.SET_PRINT_STYLEA(0,"Bold",1);\nLODOP.NewPage();'
    ),
    version_no = GREATEST(version_no, 2) + 1,
    updated_at = now()
WHERE id = 700540000000000023
  AND bill_type = 'sales-order'
  AND template_html LIKE '%{{#if needsNewPage}}%'
  AND template_html LIKE '%LODOP.NewPage();%'
  AND template_html NOT LIKE '%LODOP.ADD_PRINT_TEXT({{sumTop}},295,41,24,"{{totalQuantity}}");%LODOP.NewPage();%';
