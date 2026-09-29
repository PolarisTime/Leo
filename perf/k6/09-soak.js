/**
 * Soak 长时稳定性压测（第 5 项缺口）。
 *
 * 目标：在接近饱和的恒定负载下长时间运行，找出只有长跑才会暴露的问题——
 *   - JVM 堆持续增长（内存泄漏）
 *   - Hikari 活跃连接不归还（连接泄漏）
 *   - 错误率随时间漂移、延迟持续爬升
 *   - Redis key / 会话 / 幂等键膨胀
 *
 * 负载设计：恒定 40 VU（实测该实例吞吐天花板约在 50 VU 左右，
 * 40 VU 是「持续接近饱和但不至于自我压垮」的合适档位），
 * 覆盖全部 21 个读接口（含库存流水/财务/对账/字典等重负载路径）。
 * 只读，不产生业务数据，因此不污染环境、无需清理。
 *
 * 配合采集器使用（必须共用同一个 token，否则会因会话数上限互相吊销）：
 *   export LEO_PERF_TOKEN=<由 run.sh 或手工登录一次获得>
 *   bash leo/perf/collect-metrics.sh watch <csv> 7200 30 &
 *   k6 run leo/perf/k6/09-soak.js
 *
 * 强度：LEO_PERF_SOAK_VUS=40 LEO_PERF_SOAK_DURATION=2h
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import {
  BASE_URL,
  COMPANY_ID,
  ensureToken,
  endpointPath,
  pickReadEndpoint,
  readParams,
  setupToken,
  SUMMARY_TREND_STATS,
} from './lib/common.js';

const soakVus = Number(__ENV.LEO_PERF_SOAK_VUS || 40);
const soakDuration = __ENV.LEO_PERF_SOAK_DURATION || '2h';

const serverErrors = new Counter('soak_server_errors');
const clientErrors = new Counter('soak_client_errors');

export const options = {
  scenarios: {
    soak: {
      executor: 'constant-vus',
      vus: soakVus,
      duration: soakDuration,
    },
  },
  // 长跑不设硬阈值：目标是观察趋势，而不是让中途的超标提前终止跑测
  summaryTrendStats: SUMMARY_TREND_STATS,
};

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
    `${BASE_URL}${endpointPath(endpoint, COMPANY_ID)}`,
    readParams(token, endpoint.name, endpoint.anonymous)
  );

  if (res.status >= 500) {
    serverErrors.add(1);
  } else if (res.status >= 400) {
    clientErrors.add(1);
  }
  check(res, { '读接口返回 200': (r) => r.status === 200 });
}
