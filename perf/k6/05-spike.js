/**
 * 激进档冲击压测：并发一路拉到设定上限（默认 800 VU）并保持，直到出现错误。
 * 目标是找到吞吐拐点与报错阈值，而不是通过质量门禁，因此只记录、不设失败阈值。
 *
 * 用法：
 *   source tmp/perf/creds.env
 *   LEO_PERF_SPIKE_MAX=800 k6 run leo/perf/k6/05-spike.js
 * 建议同时用 --out csv 记录逐秒数据，便于定位拐点：
 *   k6 run --out csv=spike.csv leo/perf/k6/05-spike.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import {
  BASE_URL,
  endpointPath,
  ensureToken,
  setupToken,
  pickReadEndpoint,
  readParams,
  SUMMARY_TREND_STATS,
  writeParams,
} from './lib/common.js';

const maxVus = Number(__ENV.LEO_PERF_SPIKE_MAX || 800);
const rampStep = __ENV.LEO_PERF_SPIKE_STEP || '30s';
const hold = __ENV.LEO_PERF_SPIKE_HOLD || '60s';

const serverErrors = new Counter('spike_server_errors');
const clientErrors = new Counter('spike_client_errors');

export const options = {
  scenarios: {
    spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: rampStep, target: Math.round(maxVus * 0.25) },
        { duration: rampStep, target: Math.round(maxVus * 0.5) },
        { duration: rampStep, target: maxVus },
        { duration: hold, target: maxVus },
        { duration: '20s', target: 0 },
      ],
      gracefulRampDown: '20s',
    },
  },
  // 激进档故意不设阈值：需要观察错误持续到多高并发才出现
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

  // 90% 读 + 10% 轻量写，模拟真实混合流量下的冲击
  if (__ITER % 10 === 0 && __ENV.LEO_PERF_COMPANY_ID) {
    const res = http.post(
      `${BASE_URL}/v2.0/master-data/code-issuances/customer`,
      null,
      writeParams(token, 'POST /v2.0/master-data/code-issuances/customer')
    );
    if (res.status >= 500) {
      serverErrors.add(1);
    } else if (res.status >= 400) {
      clientErrors.add(1);
    }
    check(res, { '冲击期写请求成功': (r) => r.status === 201 });
    return;
  }

  const endpoint = pickReadEndpoint();
  const res = http.get(
    `${BASE_URL}${endpointPath(endpoint, data && data.companyId)}`,
    readParams(token, endpoint.name, endpoint.anonymous)
  );
  if (res.status >= 500) {
    serverErrors.add(1);
  } else if (res.status >= 400) {
    clientErrors.add(1);
  }
  check(res, { '冲击期读请求成功': (r) => r.status === 200 });
}
