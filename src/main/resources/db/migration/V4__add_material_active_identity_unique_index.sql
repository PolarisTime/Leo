DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM (
            SELECT
                btrim(brand) AS brand,
                btrim(material) AS material,
                btrim(spec) AS spec,
                btrim(COALESCE(length, '')) AS length
            FROM public.md_material
            WHERE deleted_flag = false
            GROUP BY 1, 2, 3, 4
            HAVING COUNT(*) > 1
        ) duplicate_material_identity
    ) THEN
        RAISE EXCEPTION 'duplicate active md_material identity exists: brand, material, spec, length';
    END IF;
END $$;

CREATE UNIQUE INDEX uk_md_material_identity_active
ON public.md_material USING btree (
    btrim(brand),
    btrim(material),
    btrim(spec),
    btrim(COALESCE(length, ''))
)
WHERE deleted_flag = false;
