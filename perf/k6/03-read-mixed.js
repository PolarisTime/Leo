/**
 * 读混合爬坡压测（默认档）：按权重随机访问各读接口，阶梯提升并发找饱和点。
 *
 * 用法：
 *   source tmp/perf/creds.env && k6 run leo/perf/k6/03-read-mixed.js
 * 覆盖并发：
 *   LEO_PERF_MAX_VUS=150 k6 run leo/perf/k6/03-read-mixed.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import {
  BASE_URL,
  endpointPath,
  ensureToken,
  setupToken,
  pickReadEndpoint,
  readParams,
  readThresholds,
  READ_ENDPOINTS,
  safeMetricName,
  SUMMARY_TREND_STATS,
} from './lib/common.js';

// 逐接口单独记 Trend/Counter：k6 摘要默认不展开 name 标签维度，
// 只有自定义指标才能给出「哪个接口在并发下退化最明显」。
const endpointTrends = {};
const endpointErrors = {};
for (const endpoint of READ_ENDPOINTS) {
  endpointTrends[endpoint.name] = new Trend(safeMetricName('read', endpoint.name), true);
  endpointErrors[endpoint.name] = new Counter(safeMetricName('readfail', endpoint.name));
}

const maxVus = Number(__ENV.LEO_PERF_MAX_VUS || 300);
const hold = __ENV.LEO_PERF_HOLD || '45s';
const ramp = __ENV.LEO_PERF_RAMP || '20s';

// 单调递增的阶梯：每档先爬升再保持，避免出现「下一档低于上一档」的回退。
// ramping-vus 的 target 是阶段结束时的 VU 数，因此「爬升到本档 + 保持本档」才能形成纯阶梯。
const tiers = [
  Math.max(1, Math.round(maxVus * 0.17)),
  Math.max(2, Math.round(maxVus * 0.5)),
  maxVus,
];
const staircase = [];
for (const target of tiers) {
  staircase.push({ duration: ramp, target });
  staircase.push({ duration: hold, target });
}
staircase.push({ duration: '20s', target: 0 });

export const options = {
  scenarios: {
    read_ramp: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: staircase,
      gracefulRampDown: '20s',
    },
  },
  thresholds: readThresholds(),
  summaryTrendStats: SUMMARY_TREND_STATS,
};

/** 串行登录一次并共享 token：避免各 VU 启动时的并发登录争用污染测量结果。 */
export function setup() {
  return setupToken();
}

export default function (data) {
  const token = ensureToken(data);
  if (!token) {
    return;
  }

  const endpoint = pickReadEndpoint();
  const res = http.get(
    `${BASE_URL}${endpointPath(endpoint, data && data.companyId)}`,
    readParams(token, endpoint.name, endpoint.anonymous)
  );
  endpointTrends[endpoint.name].add(res.timings.duration);
  if (res.status !== 200) {
    endpointErrors[endpoint.name].add(1);
  }
  check(res, { '读接口返回 200': (r) => r.status === 200 });

  // 无 sleep：模拟前端并发请求，压满连接与线程池
}
