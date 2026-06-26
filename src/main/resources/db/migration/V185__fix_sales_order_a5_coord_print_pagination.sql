-- Fix sales-order A5 coordinate template wrapping and pagination.
UPDATE sys_print_template
SET template_html = $template$
LODOP.PRINT_INIT("A5套打模版");
LODOP.SET_PRINT_PAGESIZE(1,2100,1500,"A5");
LODOP.SET_PRINT_STYLE("FontName","宋体");
LODOP.SET_PRINT_STYLE("FontSize",12);

LODOP.ADD_PRINT_TEXT(5,10,500,18,"{{remark}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);

LODOP.ADD_PRINT_TEXT(45,110,400,20,"{{customerName}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);

LODOP.ADD_PRINT_TEXT(65,570,150,20,"{{orderNo}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.SET_PRINT_STYLEA(0,"FontColor","#000000");

LODOP.ADD_PRINT_TEXT({{projectNameTop}},110,420,{{projectNameHeight}},"{{projectName}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",{{projectNameFontSize}});
LODOP.SET_PRINT_STYLEA(0,"LineSpacing",0);
LODOP.SET_PRINT_STYLEA(0,"WordBreak",{{projectNameWordBreak}});

LODOP.ADD_PRINT_TEXT(85,565,60,20,"{{dateYear}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.ADD_PRINT_TEXT(85,625,60,20,"{{dateMonth}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.ADD_PRINT_TEXT(85,665,60,20,"{{dateDay}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);

{{#each details}}
{{#if needsNewPage}}
LODOP.NewPage();
LODOP.ADD_PRINT_TEXT(5,10,500,18,"{{remark}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
LODOP.ADD_PRINT_TEXT(45,110,400,20,"{{customerName}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.ADD_PRINT_TEXT(65,570,150,20,"{{orderNo}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.SET_PRINT_STYLEA(0,"FontColor","#000000");
LODOP.ADD_PRINT_TEXT({{projectNameTop}},110,420,{{projectNameHeight}},"{{projectName}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",{{projectNameFontSize}});
LODOP.SET_PRINT_STYLEA(0,"LineSpacing",0);
LODOP.SET_PRINT_STYLEA(0,"WordBreak",{{projectNameWordBreak}});
LODOP.ADD_PRINT_TEXT(85,565,60,20,"{{dateYear}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.ADD_PRINT_TEXT(85,625,60,20,"{{dateMonth}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.ADD_PRINT_TEXT(85,665,60,20,"{{dateDay}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
{{/if}}
LODOP.ADD_PRINT_TEXT({{rowTop}},45,46,24,"{{brand}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.ADD_PRINT_TEXT({{rowTop}},95,56,24,"{{category}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.ADD_PRINT_TEXT({{rowTop}},163,71,24,"{{material}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.ADD_PRINT_TEXT({{rowTop}},240,61,24,"{{spec}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.ADD_PRINT_TEXT({{rowTop}},295,41,24,"{{quantity}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.ADD_PRINT_TEXT({{rowTop}},345,51,24,"{{pieceWeightTon}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.ADD_PRINT_TEXT({{rowTop}},415,71,24,"{{weightTon}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.ADD_PRINT_TEXT({{rowTop}},485,76,24,"{{unitPrice}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.ADD_PRINT_TEXT({{rowTop}},528,76,24,"");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
{{/each}}

LODOP.ADD_PRINT_TEXT({{sumTop}},295,41,24,"{{totalQuantity}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);
LODOP.ADD_PRINT_TEXT({{sumTop}},415,71,24,"{{totalWeight}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);
$template$,
    version_no = GREATEST(version_no, 1) + 1,
    updated_at = now()
WHERE id = 700540000000000023
  AND bill_type = 'sales-order';
