-- Align copied sales-order A4 remark templates with sales-order fields and coordinates.
UPDATE sys_print_template
SET template_type = 'COORD',
    template_html = $template$
LODOP.PRINT_INITA(0, 20, 2970, 2100, "A4打印模版（带备注）");
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

LODOP.ADD_PRINT_RECT(40,10,732.65625,44,0,1);
LODOP.ADD_PRINT_LINE(40,490,84,490,0,1);
LODOP.ADD_PRINT_TEXT(46,18,464,32,"需方公司：{{customerName}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
LODOP.ADD_PRINT_TEXT(46,498,236.65625,32,"销售订单号：{{orderNoLabel}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);

LODOP.ADD_PRINT_RECT(84,10,732.65625,44,0,1);
LODOP.ADD_PRINT_LINE(84,490,128,490,0,1);
LODOP.ADD_PRINT_TEXT(90,18,464,32,"工程名称：{{projectName}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
LODOP.ADD_PRINT_TEXT(90,498,236.65625,32,"日期：{{deliveryDate}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);

LODOP.ADD_PRINT_RECT(128,10,732.65625,44,0,1);
LODOP.ADD_PRINT_LINE(128,490,172,490,0,1);
LODOP.ADD_PRINT_TEXT(134,18,464,32,"项目地址：{{projectAddress}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
LODOP.ADD_PRINT_TEXT(134,498,236.65625,32,"车号：{{vehiclePlate}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);

var tTop=176,thH=28,rowH=24,maxRows=10;
var col=[78,47.34375,78,72,60,64,66,57];
var colName=["品牌","品名","材质","规格","长度","件数","件重/吨","总重/吨"];
var remarkW=210.3125;
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
  {brand:"{{brand}}",pname:"{{category}}",material:"{{material}}",spec:"{{spec}}",len:"{{length}}",piece:"{{quantity}}",pieceWeight:"{{pieceWeightDisplay}}",weight:"{{weightTonDisplay}}"},
{{/each}}
];

var dataTop=tTop+thH;
for(var r=0;r<maxRows;r++){
  var l=10;
  for(var j=0;j<col.length;j++){
    LODOP.ADD_PRINT_RECT(dataTop+r*rowH,l,col[j],rowH,0,1);
    l+=col[j];
  }
}

var totalPiece=0,totalWeight=0;
for(var k=0;k<DetailList.length&&k<maxRows;k++){
  var d=DetailList[k];
  var n=parseFloat(d.piece),w=parseFloat(d.weight);
  if(!isNaN(n)) totalPiece+=n;
  if(!isNaN(w)) totalWeight+=w;
  var arr=[d.brand,d.pname,d.material,d.spec,d.len||"",d.piece,d.pieceWeight,d.weight];
  var x=10;
  for(var m=0;m<arr.length;m++){
    LODOP.ADD_PRINT_TEXT(dataTop+k*rowH+5,x+2,col[m]-4,16,arr[m]||"");
    LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
    LODOP.SET_PRINT_STYLEA(0,"FontSize",8);
    x+=col[m];
  }
}

var noContentRow=DetailList.length<maxRows?DetailList.length:maxRows;
if(noContentRow<maxRows){
  var ncW=0;
  for(var nci=0;nci<col.length;nci++) ncW+=col[nci];
  LODOP.ADD_PRINT_TEXT(dataTop+noContentRow*rowH+5,12,ncW-4,16,"----------------以下无内容----------------");
  LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
  LODOP.SET_PRINT_STYLEA(0,"FontSize",8);
  LODOP.SET_PRINT_STYLEA(0,"Italic",1);
  LODOP.SET_PRINT_STYLEA(0,"FontColor","#000000");
}

var sumTop=dataTop+maxRows*rowH;
var sumArr=["合计","","","","",totalPiece||"","",totalWeight?totalWeight.toFixed(3):""];
var sx=10;
for(var si=0;si<col.length;si++){
  LODOP.ADD_PRINT_RECT(sumTop,sx,col[si],rowH,0,1);
  LODOP.ADD_PRINT_TEXT(sumTop+5,sx+2,col[si]-4,16,sumArr[si]||"");
  LODOP.SET_PRINT_STYLEA(0,"Bold",1);
  LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
  LODOP.SET_PRINT_STYLEA(0,"FontSize",8);
  sx+=col[si];
}

var remarkLeft=10;
for(var ri=0;ri<col.length;ri++) remarkLeft+=col[ri];
LODOP.ADD_PRINT_RECT(dataTop,remarkLeft,remarkW,maxRows*rowH+rowH,0,1);
LODOP.ADD_PRINT_TEXT(dataTop+6,remarkLeft+6,remarkW-12,230,"1.货物规格、材质、数量及价格在收货时当即点清，并签字生效。    2.对货物必须先行检测合格后使用，如有质量问题需方需在五日内提出书面异议，逾期视为认可，供方负责调换或协助向厂方索赔，否则供方不予处理。需方不得以质量异议为由拒付或少付货款，否则视需方违约且需方向供方支付日息万分之五付违约金。                        3.需方收货后，应当即时或合同约定时间全部付款，否则需按日息万分之五支付违约金，同时承担供方实现债权支出的一切费用。");
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);

LODOP.PREVIEW();
$template$
WHERE deleted_flag = FALSE
  AND bill_type = 'sales-order'
  AND template_name LIKE '颖捷A4打印/_带备注%' ESCAPE '/';
