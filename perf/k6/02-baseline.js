/**
 * 基线压测：单 VU 顺序轮询各读接口，测无竞争条件下的真实响应耗时。
 * 这是后续爬坡压测的对照基线，用来区分「接口本身慢」与「并发下变慢」。
 *
 * 用法：
 *   source tmp/perf/creds.env && k6 run leo/perf/k6/02-baseline.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import {
  BASE_URL,
  ensureToken,
  setupToken,
  READ_ENDPOINTS,
  readParams,
  SUMMARY_TREND_STATS,
} from './lib/common.js';

// 每个端点单独记一条 Trend，便于逐接口对比基线。
// k6 自定义指标名只允许字母/数字/下划线，因此把端点名规范化为安全标识。
function metricName(endpointName) {
  return `baseline_${endpointName.replace(/[^A-Za-z0-9]+/g, '_').replace(/^_+|_+$/g, '')}`;
}

const endpointTrends = {};
for (const endpoint of READ_ENDPOINTS) {
  endpointTrends[endpoint.name] = new Trend(metricName(endpoint.name), true);
}

export const options = {
  scenarios: {
    baseline: {
      executor: 'constant-vus',
      vus: 1,
      duration: __ENV.LEO_PERF_BASELINE_DURATION || '60s',
    },
  },
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

  for (const endpoint of READ_ENDPOINTS) {
    const res = http.get(
      `${BASE_URL}${endpoint.path}`,
      readParams(token, endpoint.name, endpoint.anonymous)
    );
    endpointTrends[endpoint.name].add(res.timings.duration);
    check(res, { [`${endpoint.name} 返回 200`]: (r) => r.status === 200 });
  }

  sleep(0.5);
}
