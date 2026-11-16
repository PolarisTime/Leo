-- 修复用户设置里残留的「未来时间戳」，并说明为什么必须修。
--
-- 症状（2026-11-16 用户反馈）：改个人设置或比价页视图时弹
-- 「设置已在其他设备更新，请采用服务端值：supplierQuote」，而且反复弹、设置再也同步不上。
--
-- 根因：sys_user.preferences_json->sections 里某个分片的 clientUpdatedAt 落在**未来**
-- （dev 库实测 admin_prod.ui = 2030-01-01T00:00:00Z，早期联调写入）。服务端 LWW 的判定是
-- 「提交时间戳严格早于已存时间戳 → 409」，于是客户端用 now() 提交永远比 2030 早 → 每次 409 →
-- 每次「采用服务端值 + 提示」，该分片永久写不回去（同一次请求里其它分片也跟着不落库）。
--
-- 修法：把落在未来的 clientUpdatedAt 回退成该分片的 serverUpdatedAt（真实入库时间）；
-- 缺失时用当前时间兜底。幂等：没有未来时间戳时一行都不改，可在任意环境重复执行。
-- 客户端的自愈逻辑（提交时间戳取「现在」与「本地镜像 + 1ms」的较大者）见
-- aries `services/user-settings-sync.ts` 的 nextClientUpdatedAt。
UPDATE sys_user u
SET preferences_json = jsonb_set(
        u.preferences_json,
        '{sections}',
        (
            SELECT jsonb_object_agg(
                       s.key,
                       CASE
                           WHEN NULLIF(s.value ->> 'clientUpdatedAt', '')::timestamptz > now()
                               THEN jsonb_set(
                                        s.value,
                                        '{clientUpdatedAt}',
                                        to_jsonb(COALESCE(
                                            NULLIF(s.value ->> 'serverUpdatedAt', ''),
                                            to_char(now() AT TIME ZONE 'UTC',
                                                    'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')
                                        ))
                                    )
                           ELSE s.value
                       END
                   )
            FROM jsonb_each(u.preferences_json -> 'sections') AS s
        )
    )
WHERE u.preferences_json -> 'sections' IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM jsonb_each(u.preferences_json -> 'sections') AS s
      WHERE NULLIF(s.value ->> 'clientUpdatedAt', '')::timestamptz > now()
  );
