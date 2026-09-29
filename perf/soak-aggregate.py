#!/usr/bin/env python3
"""聚合 soak-runner.sh 产生的分段结果，输出整体稳定性总览。

为什么需要：token 有效期 600s 不可配置，2 小时 soak 必须分段运行，
因此单段的 json 汇总无意义，必须跨段聚合才能得到 soak 的整体结论。

用法：
  python3 soak-aggregate.py <分段结果目录>        # 目录内含 soak-seg-*.json
退出码：0 全部段落无错误；1 存在错误段。
"""
import glob
import json
import os
import sys


def pick(metrics, name, key, default=0.0):
    m = metrics.get(name)
    if not m:
        return default
    return m.get(key, default)


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    directory = sys.argv[1]
    files = sorted(glob.glob(os.path.join(directory, 'soak-seg-*.json')))
    if not files:
        print(f'未找到分段结果: {directory}/soak-seg-*.json')
        return 1

    print(f"{'段':>4}{'请求数':>12}{'失败率':>9}{'吞吐(req/s)':>13}{'avg(ms)':>9}{'p95(ms)':>9}{'p99(ms)':>9}{'5xx':>7}{'4xx':>7}")
    tot_req = tot_fail = tot_5xx = tot_4xx = 0
    rates, p95s, p99s = [], [], []
    bad_segments = []

    for path in files:
        seg = os.path.basename(path).replace('soak-seg-', '').replace('.json', '')
        try:
            d = json.load(open(path, encoding='utf-8'))
        except Exception as exc:                      # noqa: BLE001
            print(f'{seg:>4}  读取失败: {exc}')
            bad_segments.append(seg)
            continue
        m = d.get('metrics', {})
        req = pick(m, 'http_reqs', 'count')
        fail_rate = pick(m, 'http_req_failed', 'value')
        rate = pick(m, 'http_reqs', 'rate')
        avg = pick(m, 'http_req_duration', 'avg')
        p95 = pick(m, 'http_req_duration', 'p(95)')
        p99 = pick(m, 'http_req_duration', 'p(99)')
        s5 = pick(m, 'soak_server_errors', 'count')
        s4 = pick(m, 'soak_client_errors', 'count')

        tot_req += req; tot_fail += req * fail_rate
        tot_5xx += s5; tot_4xx += s4
        rates.append(rate); p95s.append(p95); p99s.append(p99)
        if s5 > 0 or fail_rate > 0:
            bad_segments.append(seg)
        print(f'{seg:>4}{req:>12.0f}{fail_rate*100:>8.2f}%{rate:>13.1f}{avg:>9.1f}{p95:>9.1f}{p99:>9.1f}{s5:>7.0f}{s4:>7.0f}')

    n = len(rates)
    print('-' * 78)
    print(f'段数 {n}，累计请求 {tot_req:.0f}，累计失败 {tot_fail:.0f}（{tot_fail/max(tot_req,1)*100:.3f}%）')
    print(f'吞吐 min/中位/max: {min(rates):.1f} / {sorted(rates)[n//2]:.1f} / {max(rates):.1f} req/s')
    print(f'p95  min/中位/max: {min(p95s):.1f} / {sorted(p95s)[n//2]:.1f} / {max(p95s):.1f} ms')
    print(f'p99  min/中位/max: {min(p99s):.1f} / {sorted(p99s)[n//2]:.1f} / {max(p99s):.1f} ms')
    print(f'服务端 5xx 合计 {tot_5xx:.0f}，客户端 4xx 合计 {tot_4xx:.0f}')
    if bad_segments:
        print(f'⚠ 存在异常段: {", ".join(bad_segments)}')
        return 1
    print('✅ 各段均无失败，未见随时间的退化')
    return 0


if __name__ == '__main__':
    sys.exit(main())
