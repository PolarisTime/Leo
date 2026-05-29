-- Align the sales-order A5 overlay with the production LODOP coordinates.
UPDATE sys_print_template
SET bill_type = 'sales-order',
    template_type = 'COORD',
    template_html = $template$
LODOP.PRINT_INIT("A5套打模版");
LODOP.SET_PRINT_PAGESIZE(1,2100,1500,"A5");
LODOP.SET_PRINT_STYLE("FontName","宋体");
LODOP.SET_PRINT_STYLE("FontSize",12);

LODOP.ADD_PRINT_TEXT(5,10,500,18,"{{remark}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);

LODOP.ADD_PRINT_TEXT(45,110,400,20,"{{customerName}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);

LODOP.ADD_PRINT_TEXT(65,570,150,20,"{{orderNoLabel}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.SET_PRINT_STYLEA(0,"FontColor","#000000");

LODOP.ADD_PRINT_TEXT(90,110,520,20,"{{projectName}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);

LODOP.ADD_PRINT_TEXT(85,565,60,20,"{{_dateYear}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.ADD_PRINT_TEXT(85,625,60,20,"{{_dateMonth}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.ADD_PRINT_TEXT(85,665,60,20,"{{_dateDay}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);

{{#each details}}
LODOP.ADD_PRINT_TEXT({{_rowTop}},45,46,24,"{{brandDisplay}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.ADD_PRINT_TEXT({{_rowTop}},95,56,24,"{{category}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.ADD_PRINT_TEXT({{_rowTop}},163,71,24,"{{material}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.ADD_PRINT_TEXT({{_rowTop}},240,61,24,"{{spec}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.ADD_PRINT_TEXT({{_rowTop}},295,41,24,"{{quantity}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.ADD_PRINT_TEXT({{_rowTop}},345,51,24,"{{pieceWeightDisplay}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.ADD_PRINT_TEXT({{_rowTop}},415,71,24,"{{weightTonDisplay}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.ADD_PRINT_TEXT({{_rowTop}},485,76,24,"{{unitPriceDisplay}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.ADD_PRINT_TEXT({{_rowTop}},528,76,24,"");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
{{/each}}

LODOP.ADD_PRINT_TEXT({{_sumTop}},295,41,24,"{{totalQuantity}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);
LODOP.ADD_PRINT_TEXT({{_sumTop}},415,71,24,"{{totalWeightDisplay}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);

LODOP.PREVIEW();
$template$
WHERE id = 700540000000000023;
