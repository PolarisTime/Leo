/**
 * 附件专项（第 3 项缺口的一部分）：上传与内容下载。
 *
 * 背景：附件是唯一一个此前**完全没能压测**的路径。原因是环境限制：
 *   - `.env.local` 里的 COS/S3 凭据已失效，上传直接报
 *     `S3 上传失败: HTTP 403 The Access Key Id you provided does not exist in our records`；
 *   - 附件没有删除接口（只有 upload / access-url / content），即使传上去也无法清理。
 *
 * 本脚本改用 `leo.attachment.storage.type=local` 运行后端（通过 SPRING_APPLICATION_JSON 覆盖，
 * 因为 `scripts/env/dev.sh` 会把 `.env.local` 里的值再导出一次，OS 环境变量压不住它），
 * 从而可以压测**上传/下载接口本身**的 CPU、IO 与 multipart 解析路径。
 *
 * 口径说明（必须写在结论旁边）：本脚本测的是 **local 存储**下的附件接口，
 * S3 客户端、签名 URL、网络往返**不在测量范围内**。生产走 S3 时这部分开销只会更高。
 *
 * 清理：`sourceType` 是服务端白名单（只允许 PAGE_UPLOAD / CLIPBOARD），不能当标记用，
 * 因此所有附件的**文件名**都带运行标记 `perf-attach-<RUN_ID>.txt`，
 * 可用 `cleanup-perf-data.sh` 按该前缀删除数据库记录与本地文件。
 *
 * 用法：
 *   source tmp/perf/creds.env && LEO_PERF_TOKEN=... LEO_PERF_COMPANY_ID=<id> k6 run leo/perf/k6/10-attachments.js
 * 强度：
 *   LEO_PERF_ATTACH_VUS=10 LEO_PERF_ATTACH_UPLOADS=200 LEO_PERF_ATTACH_KB=64 k6 run ...
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import {
  BASE_URL,
  DATA_MARKER,
  ensureToken,
  idempotencyKey,
  readParams,
  setupToken,
  SUMMARY_TREND_STATS,
} from './lib/common.js';

const uploadVus = Number(__ENV.LEO_PERF_ATTACH_VUS || 10);
const uploadIterations = Number(__ENV.LEO_PERF_ATTACH_UPLOADS || 200);
const seedCount = Number(__ENV.LEO_PERF_ATTACH_SEED || 20);
const downloadVus = Number(__ENV.LEO_PERF_ATTACH_DOWNLOAD_VUS || 10);
const downloadDuration = __ENV.LEO_PERF_ATTACH_DOWNLOAD || '30s';
const moduleKey = __ENV.LEO_PERF_ATTACH_MODULE_KEY || 'sales-order';
// sourceType 是服务端白名单（仅 PAGE_UPLOAD / CLIPBOARD），不能当清理标记用。
// 因此改用**文件名**带运行标记：清理脚本按 original_file_name 前缀删除记录与本地文件。
const runId = __ENV.LEO_PERF_RUN_ID || String(Date.now());
const markerFile = `perf-attach-${runId}.txt`;
const payloadKb = Number(__ENV.LEO_PERF_ATTACH_KB || 64);

const uploadTrend = new Trend('attachment_upload_duration', true);
const downloadTrend = new Trend('attachment_download_duration', true);
const uploadStatus = new Counter('attachment_upload_status');
const downloadStatus = new Counter('attachment_download_status');

// 允许的状态码：上传 201、下载 200。其它一律视为「非预期」并让脚本失败——
// 401（会话失效）与 5xx（存储故障）都会让这轮附件结论不可用，不能混在里面当数据。
const EXPECTED = [200, 201];
const unexpectedStatus = new Counter('attachment_unexpected_status');
const recordUnexpected = (res) => {
  if (!EXPECTED.includes(res.status)) {
    unexpectedStatus.add(1, { http_status: String(res.status) });
  }
};

export const options = {
  scenarios: {
    // 上传是写路径，会落库并落盘，因此用**有界**的 shared-iterations 而不是持续加压：
    // 无界上传会在几分钟内产生数万条不可删除的附件记录（没有 DELETE 接口）。
    attachment_upload: {
      executor: 'shared-iterations',
      vus: uploadVus,
      iterations: uploadIterations,
      exec: 'uploadAttachment',
    },
    // 下载是读路径，用恒定并发观察吞吐与延迟
    attachment_download: {
      executor: 'constant-vus',
      vus: downloadVus,
      duration: downloadDuration,
      exec: 'downloadAttachment',
      startTime: '3s',
    },
  },
  summaryTrendStats: SUMMARY_TREND_STATS,
  thresholds: {
    'http_req_failed{kind:read}': ['rate<0.01'],
    'http_req_failed{kind:write}': ['rate<0.01'],
    attachment_unexpected_status: ['count==0'],
  },
};

export function setup() {
  const base = setupToken();
  const token = base.token;
  // 载荷内容刻意可压缩/可校验：写入运行标记，便于事后确认下载到的确实是本次上传的文件
  const line = `${DATA_MARKER} attachment payload line\n`;
  const payload = line.repeat(Math.max(1, Math.ceil((payloadKb * 1024) / line.length)));

  // 先串行上传 seedCount 个附件，作为并发下载的靶子
  const urls = [];
  for (let i = 0; i < seedCount; i += 1) {
    const res = http.post(
      `${BASE_URL}/v2.0/attachments/upload`,
      { moduleKey, file: http.file(payload, markerFile, 'text/plain') },
      {
        headers: {
          Authorization: `Bearer ${token}`,
          'X-Idempotency-Key': idempotencyKey(`attach-seed-${i}`),
        },
        tags: { name: 'setup:POST /v2.0/attachments/upload', kind: 'write' },
      }
    );
    if (res.status !== 201) {
      throw new Error(`setup 上传附件失败（status=${res.status}）：请确认后端以 local 附件存储启动`);
    }
    const url = res.json().downloadUrl;
    if (!url) {
      throw new Error('setup 未取得 downloadUrl');
    }
    urls.push(url);
  }
  console.log(`附件下载靶子 ${urls.length} 个，载荷 ${payload.length} 字节，清理标记文件名=${markerFile}`);
  // 必须整体展开 base：漏传 expiresAt 会让 ensureToken 认为共享 token 恒失效，
  // 每个 VU 各自重登 → 触发服务端会话上限（3）的吊销级联 → 大面积 401。
  return Object.assign({}, base, { urls, payload });
}

/** 上传：multipart 表单，字段 moduleKey + sourceType + file */
export function uploadAttachment(data) {
  const token = ensureToken(data);
  if (!token) {
    return;
  }
  const res = http.post(
    `${BASE_URL}/v2.0/attachments/upload`,
    { moduleKey, file: http.file(data.payload, markerFile, 'text/plain') },
    {
      headers: {
        Authorization: `Bearer ${token}`,
        'X-Idempotency-Key': idempotencyKey('attach-upload'),
      },
      tags: { name: 'POST /v2.0/attachments/upload', kind: 'write' },
    }
  );
  uploadTrend.add(res.timings.duration);
  uploadStatus.add(1, { http_status: String(res.status) });
  recordUnexpected(res);
  check(res, { '附件上传返回 201': (r) => r.status === 201 });
}

/** 下载：GET /v2.0/attachments/{id}/content?moduleKey=..&accessKey=.. */
export function downloadAttachment(data) {
  const token = ensureToken(data);
  if (!token || !data.urls || data.urls.length === 0) {
    return;
  }
  const path = data.urls[Math.floor(Math.random() * data.urls.length)];
  const res = http.get(`${BASE_URL.replace(/\/api$/, '')}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'GET /v2.0/attachments/{id}/content', kind: 'read' },
  });
  downloadTrend.add(res.timings.duration);
  downloadStatus.add(1, { http_status: String(res.status) });
  recordUnexpected(res);
  check(res, { '附件下载返回 200': (r) => r.status === 200 });
}

// readParams 在本脚本中未使用（下载需要自定义 tags 与 URL），显式引用避免静态检查误报
void readParams;
