-- Manual migration for existing databases that still use the legacy
-- paper_document table names. This script only renames tables, indexes,
-- and constraints; it does not delete or rewrite document/vector data.

DO $$
DECLARE
    table_pair record;
BEGIN
    FOR table_pair IN
        SELECT *
        FROM (VALUES
            ('paper_document', 'document'),
            ('paper_document_chunk', 'document_chunk'),
            ('paper_document_asset', 'document_asset'),
            ('paper_structured_parse', 'document_structured_parse')
        ) AS v(old_name, new_name)
    LOOP
        IF to_regclass('public.' || table_pair.old_name) IS NOT NULL
           AND to_regclass('public.' || table_pair.new_name) IS NOT NULL THEN
            RAISE EXCEPTION
                'Both public.% and public.% exist. Resolve the duplicate tables manually before running this migration.',
                table_pair.old_name,
                table_pair.new_name;
        END IF;
    END LOOP;
END $$;

ALTER TABLE IF EXISTS public.paper_document RENAME TO document;
ALTER TABLE IF EXISTS public.paper_document_chunk RENAME TO document_chunk;
ALTER TABLE IF EXISTS public.paper_document_asset RENAME TO document_asset;
ALTER TABLE IF EXISTS public.paper_structured_parse RENAME TO document_structured_parse;

DO $$
DECLARE
    constraint_pair record;
BEGIN
    FOR constraint_pair IN
        SELECT *
        FROM (VALUES
            ('public.document', 'paper_document_pkey', 'document_pkey'),
            ('public.document', 'chk_paper_document_status', 'chk_document_status'),
            ('public.document', 'chk_paper_document_publish_year', 'chk_document_publish_year'),
            ('public.document', 'chk_paper_document_chunk_count', 'chk_document_chunk_count'),
            ('public.document_chunk', 'paper_document_chunk_pkey', 'document_chunk_pkey'),
            ('public.document_chunk', 'fk_paper_document_chunk_source', 'fk_document_chunk_source'),
            ('public.document_chunk', 'fk_paper_document_chunk_vector_store', 'fk_document_chunk_vector_store'),
            ('public.document_chunk', 'uq_paper_document_chunk_owner_chunk', 'uq_document_chunk_owner_chunk'),
            ('public.document_chunk', 'uq_paper_document_chunk_source_index', 'uq_document_chunk_source_index'),
            ('public.document_chunk', 'chk_paper_document_chunk_index', 'chk_document_chunk_index'),
            ('public.document_chunk', 'chk_paper_document_chunk_range', 'chk_document_chunk_range'),
            ('public.document_asset', 'paper_document_asset_pkey', 'document_asset_pkey'),
            ('public.document_asset', 'fk_paper_document_asset_source', 'fk_document_asset_source'),
            ('public.document_asset', 'uq_paper_document_asset_owner_asset', 'uq_document_asset_owner_asset'),
            ('public.document_asset', 'uq_paper_document_asset_source_index', 'uq_document_asset_source_index'),
            ('public.document_asset', 'chk_paper_document_asset_index', 'chk_document_asset_index'),
            ('public.document_asset', 'chk_paper_document_asset_size', 'chk_document_asset_size'),
            ('public.document_asset', 'chk_paper_document_asset_type', 'chk_document_asset_type'),
            ('public.document_asset', 'chk_paper_document_asset_text_range', 'chk_document_asset_text_range'),
            ('public.document_structured_parse', 'paper_structured_parse_pkey', 'document_structured_parse_pkey'),
            ('public.document_structured_parse', 'fk_paper_structured_parse_source', 'fk_document_structured_parse_source'),
            ('public.document_structured_parse', 'uq_paper_structured_parse_owner_source', 'uq_document_structured_parse_owner_source'),
            ('public.document_structured_parse', 'chk_paper_structured_parse_status', 'chk_document_structured_parse_status')
        ) AS v(table_name, old_name, new_name)
    LOOP
        IF to_regclass(constraint_pair.table_name) IS NOT NULL
           AND EXISTS (
               SELECT 1
               FROM pg_constraint
               WHERE conrelid = to_regclass(constraint_pair.table_name)
                 AND conname = constraint_pair.old_name
           )
           AND NOT EXISTS (
               SELECT 1
               FROM pg_constraint
               WHERE conrelid = to_regclass(constraint_pair.table_name)
                 AND conname = constraint_pair.new_name
           ) THEN
            EXECUTE format(
                'ALTER TABLE %s RENAME CONSTRAINT %I TO %I',
                constraint_pair.table_name,
                constraint_pair.old_name,
                constraint_pair.new_name
            );
        END IF;
    END LOOP;
END $$;

DO $$
DECLARE
    index_pair record;
BEGIN
    FOR index_pair IN
        SELECT *
        FROM (VALUES
            ('public.uq_paper_document_owner_source', 'uq_document_owner_source'),
            ('public.idx_paper_document_owner_updated_at', 'idx_document_owner_updated_at'),
            ('public.idx_paper_document_title', 'idx_document_title'),
            ('public.idx_paper_document_status', 'idx_document_status'),
            ('public.idx_paper_document_publish_year', 'idx_document_publish_year'),
            ('public.idx_paper_document_created_at', 'idx_document_created_at'),
            ('public.idx_paper_document_metadata', 'idx_document_metadata'),
            ('public.idx_paper_document_authors', 'idx_document_authors'),
            ('public.idx_paper_document_keywords', 'idx_document_keywords'),
            ('public.idx_paper_document_chunk_owner_source_id', 'idx_document_chunk_owner_source_id'),
            ('public.idx_paper_document_chunk_source_id', 'idx_document_chunk_source_id'),
            ('public.idx_paper_document_chunk_vector_store_id', 'idx_document_chunk_vector_store_id'),
            ('public.idx_paper_document_chunk_metadata', 'idx_document_chunk_metadata'),
            ('public.idx_paper_document_asset_owner_source_id', 'idx_document_asset_owner_source_id'),
            ('public.idx_paper_document_asset_source_id', 'idx_document_asset_source_id'),
            ('public.idx_paper_document_asset_type', 'idx_document_asset_type'),
            ('public.idx_paper_document_asset_metadata', 'idx_document_asset_metadata'),
            ('public.idx_paper_structured_parse_owner_source', 'idx_document_structured_parse_owner_source'),
            ('public.idx_paper_structured_parse_document_id', 'idx_document_structured_parse_document_id'),
            ('public.idx_paper_structured_parse_status', 'idx_document_structured_parse_status')
        ) AS v(old_name, new_name)
    LOOP
        IF to_regclass(index_pair.old_name) IS NOT NULL
           AND to_regclass('public.' || index_pair.new_name) IS NULL THEN
            EXECUTE format('ALTER INDEX %s RENAME TO %I', index_pair.old_name, index_pair.new_name);
        END IF;
    END LOOP;
END $$;
