ALTER TABLE md_material ADD COLUMN IF NOT EXISTS length_sort NUMERIC
    GENERATED ALWAYS AS (CAST(NULLIF(regexp_replace(COALESCE(length, '0'), '[^0-9.]', '', 'g'), '') AS numeric)) STORED;

ALTER TABLE md_material ADD COLUMN IF NOT EXISTS spec_sort INTEGER
    GENERATED ALWAYS AS (CAST(NULLIF(regexp_replace(spec, '[^0-9]', '', 'g'), '') AS integer)) STORED;

CREATE INDEX IF NOT EXISTS idx_md_material_length_sort ON md_material (length_sort);
CREATE INDEX IF NOT EXISTS idx_md_material_spec_sort ON md_material (spec_sort);
