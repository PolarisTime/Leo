UPDATE sys_upload_rule
SET rename_pattern = REPLACE(REPLACE(rename_pattern, '{yyyyMMddHHmmss}', '{年月日时分秒}'), '{yyyyMMdd}', '{年月日}'),
    updated_at = CURRENT_TIMESTAMP
WHERE deleted_flag = FALSE
  AND (
      rename_pattern LIKE '%{yyyyMMddHHmmss}%'
      OR rename_pattern LIKE '%{yyyyMMdd}%'
  );
