/**
 * 重负载专项：导出与计算密集接口。
 *
 * 背景：此前的压测集只有分页查询，单请求 4–9ms，从未触碰真正昂贵的路径。
 * 实测单请求耗时（dev、1 VU、数据量很小）：
 *   POST /material-imports/previews        315ms    ← 实测最重（dry-run，不落库）
 *   POST /sales-orders/{id}/xlsx-exports   47–65ms
 *   GET  /inventory/transactions           8.7ms
 *   GET  /sales-orders                     7.6ms
 * 导出走 Apache POI 生成 XLSX，是 CPU 与堆内存大户。dev 数据量很小，
 * 生产单据明细更多时开销会显著放大，因此本脚本重点观察：
 *   - 导出吞吐随并发的变化（CPU 饱和点）
 *   - 服务端 Hikari 连接占用、JVM 堆增长（配合 collect-metrics.sh）
 *
 * 用法：
 *   source tmp/perf/creds.env && LEO_PERF_COMPANY_ID=<id> k6 run leo/perf/k6/07-heavy.js
 * 强度：
 *   LEO_PERF_HEAVY_MAX_VUS=40 LEO_PERF_HEAVY_HOLD=40s k6 run ...
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import {
  BASE_URL,
  COMPANY_ID,
  ensureToken,
  endpointPath,
  idempotencyKey,
  READ_ENDPOINTS,
  readParams,
  setupToken,
  SUMMARY_TREND_STATS,
  writeParams,
} from './lib/common.js';

const maxVus = Number(__ENV.LEO_PERF_HEAVY_MAX_VUS || 30);
const hold = __ENV.LEO_PERF_HEAVY_HOLD || '30s';
const ramp = __ENV.LEO_PERF_HEAVY_RAMP || '20s';

// 只取重负载读接口（排除 health 与轻量分页）
const HEAVY_READS = READ_ENDPOINTS.filter((e) =>
  /inventory|finance|cash-ledger|customer-statements|dashboard/.test(e.path)
);

const exportTrend = new Trend('heavy_export_duration', true);
const exportFailures = new Counter('heavy_export_failures');
const readTrend = new Trend('heavy_read_duration', true);
const importTrend = new Trend('heavy_import_preview_duration', true);
const importFailures = new Counter('heavy_import_preview_failures');

export const options = {
  scenarios: {
    export_pressure: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: ramp, target: Math.max(1, Math.round(maxVus * 0.25)) },
        { duration: hold, target: Math.max(1, Math.round(maxVus * 0.25)) },
        { duration: ramp, target: Math.max(2, Math.round(maxVus * 0.6)) },
        { duration: hold, target: Math.max(2, Math.round(maxVus * 0.6)) },
        { duration: ramp, target: maxVus },
        { duration: hold, target: maxVus },
        { duration: '15s', target: 0 },
      ],
      gracefulRampDown: '15s',
    },
  },
  summaryTrendStats: SUMMARY_TREND_STATS,
};

export function setup() {
  const base = setupToken();
  const res = http.get(
    `${BASE_URL}/v2.0/sales-orders?page=0&size=1`,
    readParams(base.token, 'setup:GET /v2.0/sales-orders', false)
  );
  let salesOrderId = '';
  if (res.status === 200) {
    const body = res.json();
    const content = body.content || body;
    if (content && content.length > 0) {
      salesOrderId = String(content[0].id);
    }
  }
  if (!salesOrderId) {
    throw new Error('setup 未能取得销售订单 ID，无法压测导出接口');
  }
  // 取商品导入模板作为预览负载：预览是 dry-run（实测物料总数前后不变），
  // 因此可以安全地作为压测靶子；它是目前发现最重的接口（单次约 315ms）。
  let importCsv = '';
  const tpl = http.get(
    `${BASE_URL}/v2.0/materials/template/csv`,
    readParams(base.token, 'setup:GET /v2.0/materials/template/csv', false)
  );
  if (tpl.status === 200 && tpl.body) {
    importCsv = `${tpl.body.replace(/\r?\n$/, '')}\nPTEST-PERF-001,压测品牌,压测材质,实体商品,规格A,12米,吨,件,0,0,0,压测预览行\n`;
  }

  return { token: base.token, issuedAt: base.issuedAt, salesOrderId, importCsv };
}

export default function (data) {
  const token = ensureToken(data);
  if (!token) {
    return;
  }

  // 每 8 次迭代打一次导入预览（最重），每次多一轮
  if (__ITER % 8 === 3 && data.importCsv) {
    const res = http.post(
      `${BASE_URL}/v2.0/material-imports/previews`,
      {
        file: http.file(data.importCsv, 'mat-preview.csv', 'text/csv'),
        format: 'csv',
      },
      {
        headers: {
          Authorization: `Bearer ${token}`,
          'X-Idempotency-Key': idempotencyKey('import-preview'),
        },
        tags: { name: 'POST /v2.0/material-imports/previews', kind: 'write' },
      }
    );
    importTrend.add(res.timings.duration);
    if (!check(res, { '导入预览返回 201': (r) => r.status === 201 })) {
      importFailures.add(1);
    }
    return;
  }

  // 每 4 次迭代打一次导出，其余打重负载读，模拟真实混合
  if (__ITER % 4 === 0 && data.salesOrderId) {
    const res = http.post(
      `${BASE_URL}/v2.0/sales-orders/${data.salesOrderId}/xlsx-exports`,
      '{}',
      writeParams(token, 'POST /v2.0/sales-orders/{id}/xlsx-exports')
    );
    exportTrend.add(res.timings.duration);
    const ok = check(res, { '导出返回 200': (r) => r.status === 200 });
    if (!ok) {
      exportFailures.add(1);
    }
    return;
  }

  const endpoint = HEAVY_READS[Math.floor(Math.random() * HEAVY_READS.length)];
  const res = http.get(
    `${BASE_URL}${endpointPath(endpoint, COMPANY_ID)}`,
    readParams(token, endpoint.name, endpoint.anonymous)
  );
  readTrend.add(res.timings.duration);
  check(res, { '重负载读返回 200': (r) => r.status === 200 });
}
