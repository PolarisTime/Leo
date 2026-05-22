-- 从旧系统(jshERP)导入并适配 LODOP 打印模板，字段名映射到新系统
-- 旧字段 → 新字段: organName→customerName, freightBillNo→outboundNo, sendDate→outboundDate,
--   displayName→brand, categoryName→category, model→material, standard→spec,
--   operNumber→quantity, weight/itemWeight→weightTon, unitWeight→pieceWeightTon,
--   allPrice→amount, beginTimeStr→startDate, endTimeStr→endDate, carNo→vehiclePlate

-- ============================
-- 销售出库 (3 个模板)
-- ============================

-- 颖捷A4打印
INSERT INTO sys_print_template (id, bill_type, template_name, template_html, is_default, deleted_flag)
VALUES (700540000000000021, 'sales-outbound', '颖捷A4打印',
'LODOP.PRINT_INITA(0, 20, 2970, 2100, "A4打印模版");
  LODOP.SET_PRINT_PAGESIZE(1,2970,2100,"");
  LODOP.SET_PRINT_STYLE("FontName","微软雅黑");
  LODOP.SET_PRINT_STYLE("FontSize",9);
  LODOP.SET_PRINT_STYLE("Italic",0);

  LODOP.ADD_PRINT_TEXT(8,10,732.65625,28,"嘉兴颖捷建材有限公司（供货单）");
  LODOP.SET_PRINT_STYLEA(0,"FontSize",16);
  LODOP.SET_PRINT_STYLEA(0,"Bold",1);
  LODOP.SET_PRINT_STYLEA(0,"Alignment",2);

  var hL=10,hW=732.65625,hSplit=480,hRowH=22;
  var hTop=40;
  LODOP.ADD_PRINT_RECT(hTop,hL,hW,hRowH,0,1);
  LODOP.ADD_PRINT_LINE(hTop,hL+hSplit,hTop+hRowH,hL+hSplit,0,1);
  LODOP.ADD_PRINT_TEXT(hTop+4,hL+8,hSplit-16,16,"需方公司：{{customerName}}");
  LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
  var billNo="{{outboundNo}}";
  LODOP.ADD_PRINT_TEXT(hTop+4,hL+hSplit+8,hW-hSplit-16,16,billNo?"单据号:"+billNo:"单据号：");
  LODOP.SET_PRINT_STYLEA(0,"FontSize",10);

  hTop+=hRowH;
  LODOP.ADD_PRINT_RECT(hTop,hL,hW,hRowH,0,1);
  LODOP.ADD_PRINT_LINE(hTop,hL+hSplit,hTop+hRowH,hL+hSplit,0,1);
  LODOP.ADD_PRINT_TEXT(hTop+4,hL+8,hSplit-16,16,"工程名称：{{projectName}}");
  LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
  LODOP.ADD_PRINT_TEXT(hTop+4,hL+hSplit+8,hW-hSplit-16,16,"日期：{{outboundDate}}");
  LODOP.SET_PRINT_STYLEA(0,"FontSize",10);

  hTop+=hRowH;
  LODOP.ADD_PRINT_RECT(hTop,hL,hW,hRowH,0,1);
  LODOP.ADD_PRINT_LINE(hTop,hL+hSplit,hTop+hRowH,hL+hSplit,0,1);
  LODOP.ADD_PRINT_TEXT(hTop+4,hL+8,hSplit-16,16,"地址：{{customerCity}}");
  LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
  LODOP.ADD_PRINT_TEXT(hTop+4,hL+hSplit+8,hW-hSplit-16,16,"车号：{{vehiclePlate}}");
  LODOP.SET_PRINT_STYLEA(0,"FontSize",10);

  var tTop=hTop+hRowH+4;
  var thH=28;
  var rowH=24;
  var col=[78,47.34375,78,72,60,64,66,57];
  var remarkW=210.3125;
  var colName=["品牌","品名","材质","规格","长度","件数","件重/吨","总重/吨"];

  var left=10;
  for(var i=0;i<col.length;i++){
    LODOP.ADD_PRINT_RECT(tTop,left,col[i],thH,0,1);
    LODOP.ADD_PRINT_TEXT(tTop+7,left+2,col[i]-4,16,colName[i]);
    LODOP.SET_PRINT_STYLEA(0,"Bold",1);
    LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
    LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
    left+=col[i];
  }
  LODOP.ADD_PRINT_RECT(tTop,left,remarkW,thH,0,1);
  LODOP.ADD_PRINT_TEXT(tTop+7,left+2,remarkW-4,16,"备  注");
  LODOP.SET_PRINT_STYLEA(0,"Bold",1);
  LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
  LODOP.SET_PRINT_STYLEA(0,"FontSize",9);

  var DetailList = [
  {{#each details}}
    {brand:"{{brand}}",pname:"{{category}}",material:"{{material}}",spec:"{{spec}}",len:"{{length}}",piece:"{{quantity}}",weight:"{{weightTon}}",isCoil:{{#if category}}"{{category}}"==="盘螺"||"{{category}}"==="线材"{{/if}}},
  {{/each}}
  ];

  var maxRows=10;
  var dataTop=tTop+thH;
  for(var r=0;r<maxRows;r++){
    var l=10;
    for(var i=0;i<col.length;i++){
      LODOP.ADD_PRINT_RECT(dataTop+r*rowH,l,col[i],rowH,0,1);
      l+=col[i];
    }
  }

  var totalPiece=0,totalWeight=0;
  for(var k=0;k<DetailList.length&&k<maxRows;k++){
    var d=DetailList[k];
    var pw="";
    var w=parseFloat(d.weight),n=parseFloat(d.piece);
    if(d.isCoil) pw="-";
    else if(!isNaN(w)&&!isNaN(n)&&n>0) pw=(w/n).toFixed(3);
    if(!isNaN(n)) totalPiece+=n;
    if(!isNaN(w)) totalWeight+=w;
    var arr=[d.brand,d.pname,d.material,d.spec,d.len||"",d.piece,pw,d.weight];
    var l=10;
    for(var i=0;i<arr.length;i++){
      LODOP.ADD_PRINT_TEXT(dataTop+k*rowH+5,l+2,col[i]-4,16,arr[i]||"");
      LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
      LODOP.SET_PRINT_STYLEA(0,"FontSize",8);
      l+=col[i];
    }
  }

  var noContentRow=DetailList.length<maxRows?DetailList.length:maxRows;
  if(noContentRow<maxRows){
    var ncLeft=10,ncW=0;
    for(var i=0;i<col.length;i++) ncW+=col[i];
    LODOP.ADD_PRINT_TEXT(dataTop+noContentRow*rowH+5,ncLeft+2,ncW-4,16,"----------------以下无内容----------------");
    LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
    LODOP.SET_PRINT_STYLEA(0,"FontSize",8);
    LODOP.SET_PRINT_STYLEA(0,"Italic",1);
    LODOP.SET_PRINT_STYLEA(0,"FontColor","#000000");
  }

  var sumTop=dataTop+maxRows*rowH;
  var sumArr=["合计","","","","",totalPiece||"","",totalWeight?totalWeight.toFixed(3):""];
  var l=10;
  for(var i=0;i<col.length;i++){
    LODOP.ADD_PRINT_RECT(sumTop,l,col[i],rowH,0,1);
    LODOP.ADD_PRINT_TEXT(sumTop+5,l+2,col[i]-4,16,sumArr[i]||"");
    LODOP.SET_PRINT_STYLEA(0,"Bold",1);
    LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
    LODOP.SET_PRINT_STYLEA(0,"FontSize",8);
    l+=col[i];
  }

  var remarkLeft=10;
  for(var i=0;i<col.length;i++) remarkLeft+=col[i];
  var remarkH=maxRows*rowH+rowH;
  LODOP.ADD_PRINT_RECT(dataTop,remarkLeft,remarkW,remarkH,0,1);
  var cY=dataTop+6,cX=remarkLeft+6,cW=remarkW-12;
  LODOP.ADD_PRINT_TEXT(cY,cX,cW,230,"1.货物规格、材质、数量及价格在收货时当即点清，并签字生效。    2.对货物必须先行检测合格后使用，如有质量问题需方需在五日内提出书面异议，逾期视为认可，供方负责调换或协助向厂方索赔，否则供方不予处理。需方不得以质量异议为由拒付或少付货款，否则视需方违约且需方向供方支付日息万分之五付违约金。                        3.需方收货后，应当即时或合同约定时间全部付款，否则需按日息万分之五支付违约金，同时承担供方实现债权支出的一切费用。");
  LODOP.SET_PRINT_STYLEA(0,"FontSize",9);

  LODOP.PREVIEW();',
'0', false);

-- 颖捷A4打印_带备注
INSERT INTO sys_print_template (id, bill_type, template_name, template_html, is_default, deleted_flag)
VALUES (700540000000000022, 'sales-outbound', '颖捷A4打印_带备注',
'LODOP.PRINT_INITA(0, 20, 2970, 2100, "A4打印模版（带备注）");
  LODOP.SET_PRINT_PAGESIZE(1,2970,2100,"");
  LODOP.SET_PRINT_STYLE("FontName","微软雅黑");
  LODOP.SET_PRINT_STYLE("FontSize",9);
  LODOP.SET_PRINT_STYLE("Italic",0);

  LODOP.ADD_PRINT_TEXT(8,10,732.65625,28,"嘉兴颖捷建材有限公司（供货单）");
  LODOP.SET_PRINT_STYLEA(0,"FontSize",16);
  LODOP.SET_PRINT_STYLEA(0,"Bold",1);
  LODOP.SET_PRINT_STYLEA(0,"Alignment",2);

  LODOP.ADD_PRINT_TEXT(8,10,230,20,"单据备注：{{remark}}");
  LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
  LODOP.SET_PRINT_STYLEA(0,"Alignment",0);

  var hL=10,hW=732.65625,hSplit=480,hRowH=44;
  var hTop=40;
  LODOP.ADD_PRINT_RECT(hTop,hL,hW,hRowH,0,1);
  LODOP.ADD_PRINT_LINE(hTop,hL+hSplit,hTop+hRowH,hL+hSplit,0,1);
  LODOP.ADD_PRINT_TEXT(hTop+6,hL+8,hSplit-16,32,"需方公司：{{customerName}}");
  LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
  var billNo="{{outboundNo}}";
  LODOP.ADD_PRINT_TEXT(hTop+6,hL+hSplit+8,hW-hSplit-16,32,billNo?"单据号:"+billNo:"单据号：");
  LODOP.SET_PRINT_STYLEA(0,"FontSize",10);

  hTop+=hRowH;
  LODOP.ADD_PRINT_RECT(hTop,hL,hW,hRowH,0,1);
  LODOP.ADD_PRINT_LINE(hTop,hL+hSplit,hTop+hRowH,hL+hSplit,0,1);
  LODOP.ADD_PRINT_TEXT(hTop+6,hL+8,hSplit-16,32,"工程名称：{{projectName}}");
  LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
  LODOP.ADD_PRINT_TEXT(hTop+6,hL+hSplit+8,hW-hSplit-16,32,"日期：{{outboundDate}}");
  LODOP.SET_PRINT_STYLEA(0,"FontSize",10);

  hTop+=hRowH;
  LODOP.ADD_PRINT_RECT(hTop,hL,hW,hRowH,0,1);
  LODOP.ADD_PRINT_LINE(hTop,hL+hSplit,hTop+hRowH,hL+hSplit,0,1);
  LODOP.ADD_PRINT_TEXT(hTop+6,hL+8,hSplit-16,32,"地址：{{customerCity}}");
  LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
  LODOP.ADD_PRINT_TEXT(hTop+6,hL+hSplit+8,hW-hSplit-16,32,"车号：{{vehiclePlate}}");
  LODOP.SET_PRINT_STYLEA(0,"FontSize",10);

  var tTop=hTop+hRowH+4;
  var thH=28;
  var rowH=24;
  var col=[78,47.34375,78,72,60,64,66,57];
  var remarkW=210.3125;
  var colName=["品牌","品名","材质","规格","长度","件数","件重/吨","总重/吨"];

  var left=10;
  for(var i=0;i<col.length;i++){
    LODOP.ADD_PRINT_RECT(tTop,left,col[i],thH,0,1);
    LODOP.ADD_PRINT_TEXT(tTop+7,left+2,col[i]-4,16,colName[i]);
    LODOP.SET_PRINT_STYLEA(0,"Bold",1);
    LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
    LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
    left+=col[i];
  }
  LODOP.ADD_PRINT_RECT(tTop,left,remarkW,thH,0,1);
  LODOP.ADD_PRINT_TEXT(tTop+7,left+2,remarkW-4,16,"备  注");
  LODOP.SET_PRINT_STYLEA(0,"Bold",1);
  LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
  LODOP.SET_PRINT_STYLEA(0,"FontSize",9);

  var DetailList = [
  {{#each details}}
    {brand:"{{brand}}",pname:"{{category}}",material:"{{material}}",spec:"{{spec}}",len:"{{length}}",piece:"{{quantity}}",weight:"{{weightTon}}",isCoil:{{#if category}}"{{category}}"==="盘螺"||"{{category}}"==="线材"{{/if}}},
  {{/each}}
  ];

  var maxRows=10;
  var dataTop=tTop+thH;
  for(var r=0;r<maxRows;r++){
    var l=10;
    for(var i=0;i<col.length;i++){
      LODOP.ADD_PRINT_RECT(dataTop+r*rowH,l,col[i],rowH,0,1);
      l+=col[i];
    }
  }

  var totalPiece=0,totalWeight=0;
  for(var k=0;k<DetailList.length&&k<maxRows;k++){
    var d=DetailList[k];
    var pw="";
    var w=parseFloat(d.weight),n=parseFloat(d.piece);
    if(d.isCoil) pw="-";
    else if(!isNaN(w)&&!isNaN(n)&&n>0) pw=(w/n).toFixed(3);
    if(!isNaN(n)) totalPiece+=n;
    if(!isNaN(w)) totalWeight+=w;
    var arr=[d.brand,d.pname,d.material,d.spec,d.len||"",d.piece,pw,d.weight];
    var l=10;
    for(var i=0;i<arr.length;i++){
      LODOP.ADD_PRINT_TEXT(dataTop+k*rowH+5,l+2,col[i]-4,16,arr[i]||"");
      LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
      LODOP.SET_PRINT_STYLEA(0,"FontSize",8);
      l+=col[i];
    }
  }

  var noContentRow=DetailList.length<maxRows?DetailList.length:maxRows;
  if(noContentRow<maxRows){
    var ncLeft=10,ncW=0;
    for(var i=0;i<col.length;i++) ncW+=col[i];
    LODOP.ADD_PRINT_TEXT(dataTop+noContentRow*rowH+5,ncLeft+2,ncW-4,16,"----------------以下无内容----------------");
    LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
    LODOP.SET_PRINT_STYLEA(0,"FontSize",8);
    LODOP.SET_PRINT_STYLEA(0,"Italic",1);
    LODOP.SET_PRINT_STYLEA(0,"FontColor","#000000");
  }

  var sumTop=dataTop+maxRows*rowH;
  var sumArr=["合计","","","","",totalPiece||"","",totalWeight?totalWeight.toFixed(3):""];
  var l=10;
  for(var i=0;i<col.length;i++){
    LODOP.ADD_PRINT_RECT(sumTop,l,col[i],rowH,0,1);
    LODOP.ADD_PRINT_TEXT(sumTop+5,l+2,col[i]-4,16,sumArr[i]||"");
    LODOP.SET_PRINT_STYLEA(0,"Bold",1);
    LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
    LODOP.SET_PRINT_STYLEA(0,"FontSize",8);
    l+=col[i];
  }

  var remarkLeft=10;
  for(var i=0;i<col.length;i++) remarkLeft+=col[i];
  var remarkH=maxRows*rowH+rowH;
  LODOP.ADD_PRINT_RECT(dataTop,remarkLeft,remarkW,remarkH,0,1);
  var cY=dataTop+6,cX=remarkLeft+6,cW=remarkW-12;
  LODOP.ADD_PRINT_TEXT(cY,cX,cW,230,"1.货物规格、材质、数量及价格在收货时当即点清，并签字生效。    2.对货物必须先行检测合格后使用，如有质量问题需方需在五日内提出书面异议，逾期视为认可，供方负责调换或协助向厂方索赔，否则供方不予处理。需方不得以质量异议为由拒付或少付货款，否则视需方违约且需方向供方支付日息万分之五付违约金。                        3.需方收货后，应当即时或合同约定时间全部付款，否则需按日息万分之五支付违约金，同时承担供方实现债权支出的一切费用。");
  LODOP.SET_PRINT_STYLEA(0,"FontSize",9);

  LODOP.PREVIEW();',
'0', false);

-- A5套打模版
INSERT INTO sys_print_template (id, bill_type, template_name, template_html, is_default, deleted_flag)
VALUES (700540000000000023, 'sales-outbound', 'A5套打模版',
'LODOP.PRINT_INIT("A5套打模版");
  LODOP.SET_PRINT_PAGESIZE(1,2100,1500,"A5");
  LODOP.SET_PRINT_STYLE("FontName","宋体");
  LODOP.SET_PRINT_STYLE("FontSize",12);

  LODOP.ADD_PRINT_TEXT(5,10,500,18,"{{remark}}");
  LODOP.SET_PRINT_STYLEA(0,"FontSize",9);

  LODOP.ADD_PRINT_TEXT(45,110,400,20,"{{customerName}}");
  LODOP.SET_PRINT_STYLEA(0,"FontSize",12);

  var billNo="{{outboundNo}}";
  LODOP.ADD_PRINT_TEXT(65,570,150,20,billNo||"");
  LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
  LODOP.SET_PRINT_STYLEA(0,"FontColor","#000000");

  LODOP.ADD_PRINT_TEXT(90,110,520,20,"{{projectName}}");
  LODOP.SET_PRINT_STYLEA(0,"FontSize",12);

  var sendDateStr="{{outboundDate}}";
  var dateYear="2026",dateMonth="04",dateDay="04";
  var m=sendDateStr.match(/(\d{4})年(\d{2})月(\d{2})日/);
  if(m){dateYear=m[1];dateMonth=m[2];dateDay=m[3];}
  LODOP.ADD_PRINT_TEXT(85,565,60,20,dateYear);
  LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
  LODOP.ADD_PRINT_TEXT(85,625,60,20,dateMonth);
  LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
  LODOP.ADD_PRINT_TEXT(85,665,60,20,dateDay);
  LODOP.SET_PRINT_STYLEA(0,"FontSize",12);

  var DetailList=[
  {{#each details}}
    {brand:"{{brand}}",pname:"{{category}}",material:"{{material}}",spec:"{{spec}}",piece:"{{quantity}}",weight:"{{weightTon}}",unitPrice:"{{unitPrice}}",isCoil:{{#if category}}"{{category}}"==="盘螺"||"{{category}}"==="线材"{{/if}}},
  {{/each}}
  ];

  var maxRows=8;
  var tableTop=155;
  var rowH=41;
  var cols=[
    {x:45,w:50},{x:95,w:60},{x:163,w:75},{x:240,w:65},
    {x:295,w:45},{x:345,w:55},{x:415,w:75},{x:485,w:80},{x:528,w:80}
  ];

  var totalPiece=0,totalWeight=0;
  for(var k=0;k<DetailList.length&&k<maxRows;k++){
    var d=DetailList[k];
    var n=parseFloat(d.piece),w=parseFloat(d.weight);
    if(!isNaN(n)) totalPiece+=n;
    if(!isNaN(w)) totalWeight+=w;
    var pw="";
    if(d.isCoil) pw="-";
    else if(!isNaN(w)&&!isNaN(n)&&n>0) pw=(w/n).toFixed(3);
    var fmtWeight="",fmtUnitPrice="";
    if(!isNaN(w)) fmtWeight=w.toFixed(3);
    if(!isNaN(parseFloat(d.unitPrice))) fmtUnitPrice=parseFloat(d.unitPrice).toFixed(2);
    var brandName=String(d.brand||"").slice(-2);
    var values=[brandName,d.pname,d.material,d.spec,d.piece,pw,fmtWeight||d.weight,fmtUnitPrice||d.unitPrice,""];
    for(var i=0;i<values.length;i++){
      LODOP.ADD_PRINT_TEXT(tableTop+k*rowH+6,cols[i].x,cols[i].w-4,24,String(values[i]||""));
      LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
      LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
    }
  }

  var sumY=tableTop+maxRows*rowH-30;
  var sumValues=["","","","",String(totalPiece||""),"",String(totalWeight?totalWeight.toFixed(3):""),"",""];
  for(var i=0;i<sumValues.length;i++){
    if(!sumValues[i]) continue;
    LODOP.ADD_PRINT_TEXT(sumY,cols[i].x,cols[i].w-4,24,sumValues[i]);
    LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
    LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
    LODOP.SET_PRINT_STYLEA(0,"Bold",1);
  }

  LODOP.PREVIEW();',
'0', false);

-- ============================
-- 物流单 (2 个模板)
-- ============================

-- 物流单A版
INSERT INTO sys_print_template (id, bill_type, template_name, template_html, is_default, deleted_flag)
VALUES (700540000000000024, 'freight-bill', '物流单A版',
'LODOP.PRINT_INIT("物流单A版");
LODOP.SET_PRINT_PAGESIZE(1,2100,2970,"A4");

LODOP.ADD_PRINT_TEXT(18,15,770,22,"物流运费单");
LODOP.SET_PRINT_STYLEA(0,"FontSize",20);
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);

LODOP.ADD_PRINT_TEXT(30,20,300,12,"打印日期：{{_printDate}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
LODOP.SET_PRINT_STYLEA(0,"FontColor","#999999");

LODOP.ADD_PRINT_LINE(42,15,42,785,0,2);

LODOP.ADD_PRINT_TEXT(50,20,350,14,"单据日期：{{billTime}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
LODOP.ADD_PRINT_TEXT(68,20,350,14,"单据编号：{{billNo}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",11);

LODOP.ADD_PRINT_TEXT(50,450,335,14,"结算方：{{carrierName}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
LODOP.ADD_PRINT_TEXT(68,450,335,14,"单价(元/吨)：{{unitPrice}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
LODOP.ADD_PRINT_TEXT(86,450,335,14,"总重量(吨)：{{totalWeight}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
LODOP.ADD_PRINT_TEXT(104,450,335,14,"总运费(元)：{{totalFreight}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);

LODOP.ADD_PRINT_TEXT(122,20,760,14,"备注：{{remark}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
LODOP.SET_PRINT_STYLEA(0,"FontColor","#666666");

LODOP.ADD_PRINT_LINE(138,15,138,785,0,1);

var col     = [30, 125, 115, 90, 110, 70, 65, 90];
var colName = ["序号","出库单号","材料名称","材质","规格","件重","数量","重量(吨)"];
var colLeft = [15,  47,  174, 291, 383, 495, 567, 634];
for(var i=0;i<colName.length;i++){
  LODOP.ADD_PRINT_TEXT(144,colLeft[i],col[i],14,colName[i]);
  LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
  LODOP.SET_PRINT_STYLEA(0,"Bold",1);
}
LODOP.ADD_PRINT_LINE(160,15,160,785,0,1);

var DetailList = [
{{#each details}}
  {idx:"{{_index}}",billNo:"{{sourceNo}}",projectName:"{{../projectName}}",customerName:"{{../customerName}}",category:"{{category}}",materialName:"{{materialName}}",material:"{{material}}",spec:"{{spec}}",quantity:"{{quantity}}",weightTon:"{{weightTon}}",pieceWeightTon:"{{pieceWeightTon}}"},
{{/each}}
];

var projects=[],projectMap={};
for(var k=0;k<DetailList.length;k++){
  var pn=DetailList[k].projectName||"";
  if(!projectMap[pn]){projectMap[pn]=[];projects.push(pn);}
  projectMap[pn].push(DetailList[k]);
}

var rowTop=164;
var rowH=20;
var seq=1;
for(var p=0;p<projects.length;p++){
  var pName=projects[p];
  var pRows=projectMap[pName];
  for(var k=0;k<pRows.length;k++){
    var d=pRows[k];
    var isCoil=d.category&&(d.category==="盘螺"||d.category==="线材");
    var pw=isCoil?"-":"";
    if(!isCoil&&d.weightTon&&d.quantity){
      var w=parseFloat(d.weightTon),n=parseFloat(d.quantity);
      if(!isNaN(w)&&!isNaN(n)&&n>0) pw=(w/n).toFixed(3);
    }
    var arr=[seq,d.billNo,d.materialName,d.material,d.spec,pw,d.quantity,d.weightTon];
    for(var i=0;i<arr.length;i++){
      LODOP.ADD_PRINT_TEXT(rowTop,colLeft[i],col[i],14,arr[i]||"");
      LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
    }
    seq++;
    rowTop+=rowH;
  }
  if(pName){
    LODOP.ADD_PRINT_TEXT(rowTop,colLeft[0],770,14,pName);
    LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
    LODOP.SET_PRINT_STYLEA(0,"FontColor","#666666");
    LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
    rowTop+=rowH;
  }
}

LODOP.PREVIEW();',
'0', false);

-- 物流单 copy
INSERT INTO sys_print_template (id, bill_type, template_name, template_html, is_default, deleted_flag)
VALUES (700540000000000025, 'freight-bill', '物流单 copy（旧版）',
'LODOP.PRINT_INIT("物流单A版");
LODOP.SET_PRINT_PAGESIZE(1,2100,2970,"A4");

LODOP.ADD_PRINT_TEXT(18,15,770,22,"物流运费单");
LODOP.SET_PRINT_STYLEA(0,"FontSize",20);
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);

LODOP.ADD_PRINT_TEXT(30,20,300,12,"打印日期：{{_printDate}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
LODOP.SET_PRINT_STYLEA(0,"FontColor","#999999");

LODOP.ADD_PRINT_LINE(42,15,42,785,0,2);

LODOP.ADD_PRINT_TEXT(50,20,350,14,"单据日期：{{billTime}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
LODOP.ADD_PRINT_TEXT(68,20,350,14,"单据编号：{{billNo}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",11);

LODOP.ADD_PRINT_TEXT(50,450,335,14,"结算方：{{carrierName}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
LODOP.ADD_PRINT_TEXT(68,450,335,14,"单价(元/吨)：{{unitPrice}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
LODOP.ADD_PRINT_TEXT(86,450,335,14,"总重量(吨)：{{totalWeight}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
LODOP.ADD_PRINT_TEXT(104,450,335,14,"总运费(元)：{{totalFreight}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);

LODOP.ADD_PRINT_TEXT(122,20,760,14,"备注：{{remark}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
LODOP.SET_PRINT_STYLEA(0,"FontColor","#666666");

LODOP.ADD_PRINT_LINE(138,15,138,785,0,1);

var col     = [30, 125, 115, 90, 110, 70, 65, 90];
var colName = ["序号","出库单号","材料名称","材质","规格","件重","数量","重量(吨)"];
var colLeft = [15,  47,  174, 291, 383, 495, 567, 634];
for(var i=0;i<colName.length;i++){
  LODOP.ADD_PRINT_TEXT(144,colLeft[i],col[i],14,colName[i]);
  LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
  LODOP.SET_PRINT_STYLEA(0,"Bold",1);
}
LODOP.ADD_PRINT_LINE(160,15,160,785,0,1);

var DetailList = [
{{#each details}}
  {idx:"{{_index}}",billNo:"{{sourceNo}}",projectName:"{{../projectName}}",customerName:"{{../customerName}}",category:"{{category}}",materialName:"{{materialName}}",material:"{{material}}",spec:"{{spec}}",quantity:"{{quantity}}",weightTon:"{{weightTon}}",pieceWeightTon:"{{pieceWeightTon}}"},
{{/each}}
];

var projects=[],projectMap={};
for(var k=0;k<DetailList.length;k++){
  var pn=DetailList[k].projectName||"";
  if(!projectMap[pn]){projectMap[pn]=[];projects.push(pn);}
  projectMap[pn].push(DetailList[k]);
}

var rowTop=164;
var rowH=20;
var seq=1;
for(var p=0;p<projects.length;p++){
  var pName=projects[p];
  var pRows=projectMap[pName];
  for(var k=0;k<pRows.length;k++){
    var d=pRows[k];
    var isCoil=d.category&&(d.category==="盘螺"||d.category==="线材");
    var pw=isCoil?"-":"";
    if(!isCoil&&d.weightTon&&d.quantity){
      var w=parseFloat(d.weightTon),n=parseFloat(d.quantity);
      if(!isNaN(w)&&!isNaN(n)&&n>0) pw=(w/n).toFixed(3);
    }
    var arr=[seq,d.billNo,d.materialName,d.material,d.spec,pw,d.quantity,d.weightTon];
    for(var i=0;i<arr.length;i++){
      LODOP.ADD_PRINT_TEXT(rowTop,colLeft[i],col[i],14,arr[i]||"");
      LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
    }
    seq++;
    rowTop+=rowH;
  }
  if(pName){
    LODOP.ADD_PRINT_TEXT(rowTop,colLeft[0],770,14,pName);
    LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
    LODOP.SET_PRINT_STYLEA(0,"FontColor","#666666");
    LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
    rowTop+=rowH;
  }
}

LODOP.PREVIEW();',
'0', false);

-- ============================
-- 物流对账单 (2 个模板)
-- ============================

-- 物流对账单-汇总
INSERT INTO sys_print_template (id, bill_type, template_name, template_html, is_default, deleted_flag)
VALUES (700540000000000026, 'freight-statement', '物流对账单-汇总',
'LODOP.PRINT_INIT("物流对账单");
LODOP.SET_PRINT_PAGESIZE(1,2970,2100,"A4");

LODOP.ADD_PRINT_TEXT(18,15,940,24,"物流运费对账单");
LODOP.SET_PRINT_STYLEA(0,"FontSize",22);
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);

LODOP.ADD_PRINT_TEXT(10,680,265,18,"No.{{statementNo}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.SET_PRINT_STYLEA(0,"FontColor","#333333");
LODOP.SET_PRINT_STYLEA(0,"Alignment",3);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);

LODOP.ADD_PRINT_TEXT(30,20,300,12,"打印日期：{{_printDate}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
LODOP.SET_PRINT_STYLEA(0,"FontColor","#999999");

LODOP.ADD_PRINT_LINE(44,15,44,950,0,2);

LODOP.ADD_PRINT_TEXT(52,20,400,14,"物流方：{{carrierName}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
LODOP.ADD_PRINT_TEXT(70,20,400,14,"账期：{{startDate}} ~ {{endDate}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
LODOP.ADD_PRINT_TEXT(52,570,380,14,"总重量(吨)：{{totalWeight}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
LODOP.ADD_PRINT_TEXT(70,570,380,14,"总运费(元)：{{totalFreight}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);

LODOP.ADD_PRINT_TEXT(88,20,920,14,"备注：{{remark}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
LODOP.SET_PRINT_STYLEA(0,"FontColor","#666666");

LODOP.ADD_PRINT_LINE(104,15,104,950,0,1);

var colLeft=[15,55,200,340,480,610,740,860];
var col=[38,143,138,138,128,128,118,100];
var colName=["序号","物流单号","日期","物流方","总重量(吨)","单价(元/吨)","运费(元)","备注"];
for(var i=0;i<colName.length;i++){
  LODOP.ADD_PRINT_TEXT(110,colLeft[i],col[i],14,colName[i]);
  LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
  LODOP.SET_PRINT_STYLEA(0,"Bold",1);
}
LODOP.ADD_PRINT_LINE(126,15,126,950,0,1);

var DetailList=[
{{#each details}}
  {idx:"{{_index}}",billNo:"{{sourceNo}}",billTime:"{{billTime}}",carrierName:"{{carrierName}}",totalWeight:"{{weightTon}}",unitPrice:"{{unitPrice}}",totalFreight:"{{amount}}",remark:"{{remark}}"},
{{/each}}
];

var rowTop=130;
var rowH=20;
for(var k=0;k<DetailList.length;k++){
  var d=DetailList[k];
  var arr=[d.idx,d.billNo,d.billTime,d.carrierName,d.totalWeight,d.unitPrice,d.totalFreight,d.remark];
  for(var i=0;i<arr.length;i++){
    LODOP.ADD_PRINT_TEXT(rowTop,colLeft[i],col[i],14,arr[i]||"");
    LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
  }
  rowTop+=rowH;
}

LODOP.ADD_PRINT_LINE(rowTop,15,rowTop,950,0,1);
rowTop+=2;
LODOP.ADD_PRINT_TEXT(rowTop,colLeft[0],500,14,"合计");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);
LODOP.ADD_PRINT_TEXT(rowTop,colLeft[4],col[4],14,"{{totalWeight}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);
LODOP.ADD_PRINT_TEXT(rowTop,colLeft[6],col[6],14,"{{totalFreight}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);

rowTop+=40;
LODOP.ADD_PRINT_TEXT(rowTop,20,300,14,"委托方（盖章）：");
LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
LODOP.ADD_PRINT_TEXT(rowTop,550,300,14,"承运方（盖章）：");
LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
rowTop+=30;
LODOP.ADD_PRINT_LINE(rowTop,20,rowTop,280,0,1);
LODOP.ADD_PRINT_LINE(rowTop,550,rowTop,810,0,1);
rowTop+=8;
LODOP.ADD_PRINT_TEXT(rowTop,20,300,14,"日期：　　年　　月　　日");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
LODOP.ADD_PRINT_TEXT(rowTop,550,300,14,"日期：　　年　　月　　日");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);

LODOP.PREVIEW();',
'0', false);

-- ============================
-- 客户对账单 (2 个模板)
-- ============================

-- 客户对账单
INSERT INTO sys_print_template (id, bill_type, template_name, template_html, is_default, deleted_flag)
VALUES (700540000000000027, 'customer-statement', '客户对账单（旧版）',
'LODOP.PRINT_INIT("客户对账单");
LODOP.SET_PRINT_PAGESIZE(1,2970,2100,"A4");

LODOP.ADD_PRINT_TEXT(18,15,940,24,"客户对账单");
LODOP.SET_PRINT_STYLEA(0,"FontSize",22);
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);

LODOP.ADD_PRINT_TEXT(10,680,265,18,"No.{{statementNo}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",12);
LODOP.SET_PRINT_STYLEA(0,"FontColor","#333333");
LODOP.SET_PRINT_STYLEA(0,"Alignment",3);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);

LODOP.ADD_PRINT_TEXT(30,20,300,12,"打印日期：{{_printDate}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
LODOP.SET_PRINT_STYLEA(0,"FontColor","#999999");

LODOP.ADD_PRINT_LINE(44,15,44,950,0,2);

LODOP.ADD_PRINT_TEXT(52,20,400,14,"客户：{{customerName}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
LODOP.ADD_PRINT_TEXT(70,20,400,14,"账期：{{startDate}} ~ {{endDate}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
LODOP.ADD_PRINT_TEXT(52,570,380,14,"总重量(吨)：{{totalWeight}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
LODOP.ADD_PRINT_TEXT(70,570,380,14,"总金额(元)：{{totalAmount}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);

LODOP.ADD_PRINT_TEXT(88,20,920,14,"备注：{{remark}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
LODOP.SET_PRINT_STYLEA(0,"FontColor","#666666");

LODOP.ADD_PRINT_LINE(104,15,104,950,0,1);

var colLeft=[15,52,144,281,373,445,527,609,676,743,843,935];
var col=[35,90,135,90,70,80,80,67,65,98,90,140];
var colName=["序号","日期","出库单号","商品类别","品牌","材质","规格","件重","件数","重量小计(吨)","单价(元/吨)","总金额(元)"];
for(var i=0;i<colName.length;i++){
  LODOP.ADD_PRINT_TEXT(110,colLeft[i],col[i],14,colName[i]);
  LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
  LODOP.SET_PRINT_STYLEA(0,"Bold",1);
}
LODOP.ADD_PRINT_LINE(126,15,126,950,0,1);

var DetailList=[
{{#each details}}
  {idx:"{{_index}}",billTime:"{{billTime}}",billNo:"{{sourceNo}}",category:"{{category}}",brand:"{{brand}}",material:"{{material}}",spec:"{{spec}}",pieceWeightTon:"{{pieceWeightTon}}",quantity:"{{quantity}}",weightTon:"{{weightTon}}",unitPrice:"{{unitPrice}}",amount:"{{amount}}",billRemark:"{{remark}}"},
{{/each}}
];

var rowTop=130;
var rowH=20;
for(var k=0;k<DetailList.length;k++){
  var d=DetailList[k];
  var isCoil=d.category&&(d.category==="盘螺"||d.category==="线材");
  var uw=isCoil?"-":(d.pieceWeightTon?parseFloat(d.pieceWeightTon).toFixed(3):"");
  var arr=[d.idx,d.billTime,d.billNo,d.category,d.brand,d.material,d.spec,uw,d.quantity,d.weightTon,d.unitPrice,d.amount];
  for(var i=0;i<arr.length;i++){
    LODOP.ADD_PRINT_TEXT(rowTop,colLeft[i],col[i],14,arr[i]||"");
    LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
  }
  rowTop+=rowH;
}

LODOP.ADD_PRINT_LINE(rowTop,15,rowTop,950,0,1);
rowTop+=2;
LODOP.ADD_PRINT_TEXT(rowTop,colLeft[0],600,14,"合计");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);
LODOP.ADD_PRINT_TEXT(rowTop,colLeft[9],col[9],14,"{{totalWeight}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);
LODOP.ADD_PRINT_TEXT(rowTop,colLeft[11],col[11],14,"{{totalAmount}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);

LODOP.PREVIEW();',
'0', false);

-- 客户对账单-A4（含分页+分隔线）
INSERT INTO sys_print_template (id, bill_type, template_name, template_html, is_default, deleted_flag)
VALUES (700540000000000028, 'customer-statement', '客户对账单-A4',
'LODOP.PRINT_INIT("客户对账单-A4");
LODOP.SET_PRINT_PAGESIZE(1,2100,2970,"A4");

LODOP.ADD_PRINT_TEXT(20,15,750,26,"客户对账单");
LODOP.SET_PRINT_STYLEA(0,"FontSize",20);
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);

LODOP.ADD_PRINT_TEXT(46,15,750,14,"CUSTOMER RECONCILIATION STATEMENT");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontColor","#666666");

LODOP.ADD_PRINT_TEXT(20,520,240,16,"No.{{statementNo}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",11);
LODOP.SET_PRINT_STYLEA(0,"Alignment",3);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);

LODOP.ADD_PRINT_LINE(64,15,64,750,0,2);

LODOP.ADD_PRINT_TEXT(72,20,350,14,"对账单号：{{statementNo}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
LODOP.ADD_PRINT_TEXT(72,420,340,14,"客户名称：{{customerName}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);

LODOP.ADD_PRINT_TEXT(90,20,350,14,"项目名称：{{projectName}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
LODOP.ADD_PRINT_TEXT(90,420,340,14,"账期：{{startDate}} 至 {{endDate}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);

LODOP.ADD_PRINT_TEXT(110,20,720,14,"备注：{{remark}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
LODOP.SET_PRINT_STYLEA(0,"FontColor","#666666");

LODOP.ADD_PRINT_LINE(126,15,126,750,0,1);

var colLeft=[15,38,98,198,270,325,378,428,472,516,586,650];
var col=[21,58,98,70,53,51,48,42,42,68,62,100];
var colName=["#","日期","出库单号","商品类别","品牌","材质","规格","件重","件数","重量(吨)","单价","金额(元)"];
for(var i=0;i<colName.length;i++){
  LODOP.ADD_PRINT_TEXT(130,colLeft[i],col[i],13,colName[i]);
  LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
  LODOP.SET_PRINT_STYLEA(0,"Bold",1);
}
LODOP.ADD_PRINT_LINE(144,15,144,750,0,1);

var DetailList=[
{{#each details}}
  {idx:"{{_index}}",billTime:"{{billTime}}",billNo:"{{sourceNo}}",category:"{{category}}",brand:"{{brand}}",material:"{{material}}",spec:"{{spec}}",pieceWeightTon:"{{pieceWeightTon}}",quantity:"{{quantity}}",weightTon:"{{weightTon}}",unitPrice:"{{unitPrice}}",amount:"{{amount}}"},
{{/each}}
];

var rowTop=148;
var rowH=18;
var pageH=1050;
for(var k=0;k<DetailList.length;k++){
  if(rowTop+rowH*2>pageH){LODOP.NEWPAGE();rowTop=20;}
  var d=DetailList[k];
  var isCoil=d.category&&(d.category==="盘螺"||d.category==="线材");
  var uw=isCoil?"-":(d.pieceWeightTon?parseFloat(d.pieceWeightTon).toFixed(3):"");
  var iw=d.weightTon?parseFloat(d.weightTon).toFixed(3):"";
  var ap=d.amount?parseFloat(d.amount).toFixed(2):"";
  var arr=[d.idx,d.billTime,d.billNo,d.category,d.brand,d.material,d.spec,uw,d.quantity,iw,d.unitPrice,ap];
  for(var i=0;i<arr.length;i++){
    LODOP.ADD_PRINT_TEXT(rowTop,colLeft[i],col[i],13,arr[i]||"");
    LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
  }
  rowTop+=rowH;
  var nextBillNo=(k+1<DetailList.length)?DetailList[k+1].billNo:"";
  if(d.billNo!==nextBillNo){
    LODOP.ADD_PRINT_LINE(rowTop-2,15,rowTop-2,750,2,1);
    LODOP.SET_PRINT_STYLEA(0,"LineColor","#CCCCCC");
  }
}

LODOP.ADD_PRINT_LINE(rowTop,15,rowTop,750,0,1);
rowTop+=3;
LODOP.ADD_PRINT_TEXT(rowTop,colLeft[0],500,14,"合　计");
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);
LODOP.ADD_PRINT_TEXT(rowTop,colLeft[8],col[8],14,"{{totalQuantity}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);
LODOP.ADD_PRINT_TEXT(rowTop,colLeft[9],col[9],14,"{{totalWeight}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);
LODOP.ADD_PRINT_TEXT(rowTop,colLeft[11],col[11],14,"{{totalAmount}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);

rowTop+=35;
LODOP.ADD_PRINT_TEXT(rowTop,20,250,14,"供方（盖章）：");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
LODOP.ADD_PRINT_TEXT(rowTop,420,250,14,"需方（盖章）：");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
rowTop+=28;
LODOP.ADD_PRINT_LINE(rowTop,20,rowTop,260,0,1);
LODOP.ADD_PRINT_LINE(rowTop,420,rowTop,660,0,1);
rowTop+=6;
LODOP.ADD_PRINT_TEXT(rowTop,20,250,14,"日期：　　年　　月　　日");
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
LODOP.ADD_PRINT_TEXT(rowTop,420,250,14,"日期：　　年　　月　　日");
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);

LODOP.PREVIEW();',
'0', false);
