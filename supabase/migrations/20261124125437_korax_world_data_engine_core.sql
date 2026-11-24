-- KORAX World Data Engine core schema.
-- Internal source/entity tables are RLS-protected and service_role-only.
CREATE EXTENSION IF NOT EXISTS pgroonga WITH SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA extensions;

CREATE TABLE public.korax_source_registry (
  source_key text PRIMARY KEY,
  display_name text NOT NULL,
  source_type text NOT NULL CHECK (source_type IN ('live','fixtures','historical','metadata','images','research')),
  base_url text NOT NULL,
  data_domains text[] NOT NULL DEFAULT '{}',
  license_code text NOT NULL,
  attribution_required boolean NOT NULL DEFAULT false,
  attribution_text text,
  attribution_url text,
  share_alike_required boolean NOT NULL DEFAULT false,
  asset_rights_review_required boolean NOT NULL DEFAULT false,
  commercial_use_review_required boolean NOT NULL DEFAULT false,
  requests_per_minute integer CHECK (requests_per_minute IS NULL OR requests_per_minute > 0),
  requests_per_day integer CHECK (requests_per_day IS NULL OR requests_per_day > 0),
  min_interval_seconds integer CHECK (min_interval_seconds IS NULL OR min_interval_seconds >= 0),
  enabled boolean NOT NULL DEFAULT true,
  health_status text NOT NULL DEFAULT 'unknown' CHECK (health_status IN ('unknown','healthy','degraded','offline','disabled')),
  last_success_at timestamptz,
  last_error_at timestamptz,
  last_error text,
  quota_metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE public.korax_source_sync_runs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  source_key text NOT NULL REFERENCES public.korax_source_registry(source_key),
  workflow_run_id text,
  run_key text,
  status text NOT NULL DEFAULT 'running' CHECK (status IN ('running','success','partial','failed','skipped')),
  started_at timestamptz NOT NULL DEFAULT now(),
  finished_at timestamptz,
  request_count integer NOT NULL DEFAULT 0 CHECK (request_count >= 0),
  records_seen integer NOT NULL DEFAULT 0 CHECK (records_seen >= 0),
  records_inserted integer NOT NULL DEFAULT 0 CHECK (records_inserted >= 0),
  records_updated integer NOT NULL DEFAULT 0 CHECK (records_updated >= 0),
  records_skipped integer NOT NULL DEFAULT 0 CHECK (records_skipped >= 0),
  error_count integer NOT NULL DEFAULT 0 CHECK (error_count >= 0),
  error_summary text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (source_key, run_key)
);
CREATE INDEX korax_source_sync_runs_source_started_idx
  ON public.korax_source_sync_runs (source_key, started_at DESC);

CREATE TABLE public.korax_entities (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  entity_type text NOT NULL CHECK (entity_type IN ('club','national','player','coach','league','tournament','stadium','country')),
  canonical_name text NOT NULL,
  name_ar text,
  name_en text,
  aliases text[] NOT NULL DEFAULT '{}',
  country_code text,
  country_name text,
  gender text,
  age_group text,
  founded_on date,
  birth_date date,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  search_text text NOT NULL DEFAULT '',
  completeness smallint NOT NULL DEFAULT 0 CHECK (completeness BETWEEN 0 AND 100),
  verification_status text NOT NULL DEFAULT 'unverified' CHECK (verification_status IN ('unverified','partial','verified','needs_review')),
  is_active boolean NOT NULL DEFAULT true,
  last_verified_at timestamptz,
  last_seen_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX korax_entities_type_country_idx
  ON public.korax_entities (entity_type, country_code) WHERE is_active;
CREATE INDEX korax_entities_search_pgroonga_idx
  ON public.korax_entities USING pgroonga (search_text);
CREATE INDEX korax_entities_search_trgm_idx
  ON public.korax_entities USING gin (search_text extensions.gin_trgm_ops);

CREATE TABLE public.korax_entity_sources (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  entity_id uuid NOT NULL REFERENCES public.korax_entities(id) ON DELETE CASCADE,
  source_key text NOT NULL REFERENCES public.korax_source_registry(source_key),
  external_id text NOT NULL,
  external_url text,
  match_method text NOT NULL DEFAULT 'provider_id' CHECK (match_method IN ('provider_id','wikidata_id','verified_link','manual','fuzzy_candidate')),
  confidence numeric(4,3) NOT NULL DEFAULT 1.000 CHECK (confidence BETWEEN 0 AND 1),
  is_primary boolean NOT NULL DEFAULT false,
  raw_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  last_seen_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (source_key, external_id)
);
CREATE INDEX korax_entity_sources_entity_idx
  ON public.korax_entity_sources (entity_id, source_key);

CREATE TABLE public.korax_entity_assets (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  entity_id uuid NOT NULL REFERENCES public.korax_entities(id) ON DELETE CASCADE,
  asset_type text NOT NULL CHECK (asset_type IN ('logo','portrait','stadium','banner','flag','other')),
  asset_url text NOT NULL,
  source_key text NOT NULL REFERENCES public.korax_source_registry(source_key),
  source_page_url text,
  license_code text,
  license_url text,
  creator text,
  attribution_text text,
  license_status text NOT NULL DEFAULT 'unknown' CHECK (license_status IN ('unknown','unverified','verified','blocked')),
  is_primary boolean NOT NULL DEFAULT false,
  width integer,
  height integer,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  last_checked_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (entity_id, asset_type, asset_url)
);
CREATE INDEX korax_entity_assets_verified_idx
  ON public.korax_entity_assets (entity_id, asset_type, is_primary DESC)
  WHERE license_status = 'verified';

CREATE TABLE public.korax_entity_facts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  entity_id uuid NOT NULL REFERENCES public.korax_entities(id) ON DELETE CASCADE,
  field_key text NOT NULL,
  value jsonb NOT NULL,
  source_key text NOT NULL REFERENCES public.korax_source_registry(source_key),
  source_external_id text,
  confidence numeric(4,3) NOT NULL DEFAULT 1.000 CHECK (confidence BETWEEN 0 AND 1),
  observed_at timestamptz NOT NULL DEFAULT now(),
  valid_from timestamptz,
  valid_to timestamptz,
  is_current boolean NOT NULL DEFAULT true,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX korax_entity_facts_current_idx
  ON public.korax_entity_facts (entity_id, field_key, observed_at DESC) WHERE is_current;

CREATE TABLE public.korax_external_matches (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  source_key text NOT NULL REFERENCES public.korax_source_registry(source_key),
  external_id text NOT NULL,
  competition_name text,
  season text,
  match_date date,
  kickoff_at timestamptz,
  home_team_name text NOT NULL,
  away_team_name text NOT NULL,
  home_entity_id uuid REFERENCES public.korax_entities(id),
  away_entity_id uuid REFERENCES public.korax_entities(id),
  home_score integer,
  away_score integer,
  status text,
  is_live boolean NOT NULL DEFAULT false,
  is_finished boolean NOT NULL DEFAULT false,
  fingerprint text NOT NULL,
  canonical_match_slug text REFERENCES public.korax_sportscore_matches(match_slug),
  match_confidence numeric(4,3) CHECK (match_confidence IS NULL OR match_confidence BETWEEN 0 AND 1),
  raw_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  last_seen_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (source_key, external_id)
);
CREATE INDEX korax_external_matches_fingerprint_idx
  ON public.korax_external_matches (fingerprint);
CREATE INDEX korax_external_matches_date_idx
  ON public.korax_external_matches (match_date DESC, competition_name);

CREATE TABLE public.korax_match_source_links (
  source_key text NOT NULL REFERENCES public.korax_source_registry(source_key),
  external_id text NOT NULL,
  match_slug text NOT NULL REFERENCES public.korax_sportscore_matches(match_slug) ON DELETE CASCADE,
  confidence numeric(4,3) NOT NULL CHECK (confidence BETWEEN 0 AND 1),
  match_method text NOT NULL CHECK (match_method IN ('provider_id','fingerprint_verified','manual')),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (source_key, external_id),
  UNIQUE (match_slug, source_key)
);

CREATE TABLE public.korax_source_coverage (
  source_key text NOT NULL REFERENCES public.korax_source_registry(source_key),
  entity_type text NOT NULL,
  country_code text NOT NULL DEFAULT '',
  competition_name text NOT NULL DEFAULT '',
  season text NOT NULL DEFAULT '',
  coverage_status text NOT NULL DEFAULT 'unknown' CHECK (coverage_status IN ('unknown','available','partial','unavailable','stale')),
  record_count integer NOT NULL DEFAULT 0 CHECK (record_count >= 0),
  last_checked_at timestamptz,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  PRIMARY KEY (source_key, entity_type, country_code, competition_name, season)
);

CREATE TABLE public.korax_entity_merge_candidates (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  left_entity_id uuid NOT NULL REFERENCES public.korax_entities(id) ON DELETE CASCADE,
  right_entity_id uuid NOT NULL REFERENCES public.korax_entities(id) ON DELETE CASCADE,
  confidence numeric(4,3) NOT NULL CHECK (confidence BETWEEN 0 AND 1),
  reason text NOT NULL,
  status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','approved','rejected')),
  created_at timestamptz NOT NULL DEFAULT now(),
  reviewed_at timestamptz,
  CHECK (left_entity_id < right_entity_id),
  UNIQUE (left_entity_id, right_entity_id)
);
CREATE INDEX korax_entity_merge_candidates_pending_idx
  ON public.korax_entity_merge_candidates (confidence DESC) WHERE status = 'pending';

CREATE OR REPLACE FUNCTION public.korax_normalize_search(p_value text)
RETURNS text
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
SET search_path = pg_catalog, public, extensions
AS $$
  SELECT btrim(regexp_replace(
    translate(lower(extensions.unaccent(coalesce(p_value, ''))), 'أإآٱىؤئـًٌٍَُِّْٰ', 'اااايويي'),
    '[[:space:][:punct:]]+', ' ', 'g'
  ));
$$;

CREATE OR REPLACE FUNCTION public.korax_entities_refresh_search_text()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, public
AS $$
BEGIN
  NEW.search_text := public.korax_normalize_search(
    concat_ws(' ', NEW.canonical_name, NEW.name_ar, NEW.name_en, array_to_string(NEW.aliases, ' '))
  );
  NEW.updated_at := now();
  RETURN NEW;
END;
$$;
CREATE TRIGGER korax_entities_search_text_trigger
BEFORE INSERT OR UPDATE ON public.korax_entities
FOR EACH ROW EXECUTE FUNCTION public.korax_entities_refresh_search_text();

CREATE OR REPLACE FUNCTION public.search_korax_entities(
  p_query text,
  p_types text[] DEFAULT NULL,
  p_limit integer DEFAULT 40
)
RETURNS TABLE (
  id uuid, entity_type text, canonical_name text, name_ar text, name_en text,
  aliases text[], country_code text, country_name text, metadata jsonb,
  completeness smallint, verification_status text, score real
)
LANGUAGE sql
STABLE
SET search_path = pg_catalog, public, extensions
SET pg_trgm.similarity_threshold = 0.15
AS $$
  WITH query_value AS (SELECT public.korax_normalize_search(p_query) AS value)
  SELECT e.id, e.entity_type, e.canonical_name, e.name_ar, e.name_en, e.aliases,
         e.country_code, e.country_name, e.metadata, e.completeness, e.verification_status,
         CASE
           WHEN e.search_text = q.value THEN 1.0::real
           WHEN e.search_text LIKE q.value || '%' THEN 0.95::real
           WHEN e.search_text &@~ q.value THEN 0.85::real
           ELSE extensions.similarity(e.search_text, q.value)::real
         END AS score
  FROM public.korax_entities e
  CROSS JOIN query_value q
  WHERE length(q.value) >= 2
    AND e.is_active
    AND (p_types IS NULL OR e.entity_type = ANY(p_types))
    AND (e.search_text &@~ q.value OR e.search_text LIKE '%' || q.value || '%' OR
         (length(q.value) >= 4 AND e.search_text % q.value))
  ORDER BY score DESC, e.completeness DESC, e.canonical_name ASC
  LIMIT LEAST(GREATEST(coalesce(p_limit, 40), 1), 100);
$$;

INSERT INTO public.korax_source_registry
(source_key, display_name, source_type, base_url, data_domains, license_code,
 attribution_required, attribution_text, attribution_url, share_alike_required,
 asset_rights_review_required, commercial_use_review_required,
 requests_per_minute, requests_per_day, min_interval_seconds, enabled, health_status, quota_metadata)
VALUES
('sportscore','SportScore','live','https://sportscore.com',ARRAY['matches','live_scores','events','lineups','standings'],'sportscore_free_attribution',true,'Powered by SportScore','https://sportscore.com',false,false,false,NULL,10000,60,true,'healthy','{"cache_seconds":60,"notes":"Current primary source; daily quota is per IP."}'::jsonb),
('thesportsdb','TheSportsDB','metadata','https://www.thesportsdb.com',ARRAY['teams','players','leagues','venues'],'thesportsdb_free_v1_terms_review',false,NULL,'https://www.thesportsdb.com',false,true,true,30,NULL,3,true,'unknown','{"free_key":"123","free_search_team_results":1,"free_search_player_results":1,"free_list_limit":10,"free_v2":"premium_only","images_mostly_fan_created":"asset use blocked until license verified"}'::jsonb),
('openligadb','OpenLigaDB','fixtures','https://api.openligadb.de',ARRAY['fixtures','results','tables','goals'],'ODbL-1.0',true,'OpenLigaDB','https://www.openligadb.de',true,false,true,60,NULL,60,true,'unknown','{"live_poll_interval_seconds_per_league":60,"use_getlastchangedate":true}'::jsonb),
('openfootball','OpenFootball','historical','https://github.com/openfootball/football.json',ARRAY['historical_fixtures','results'],'CC0-1.0',false,NULL,'https://github.com/openfootball/football.json',false,false,false,NULL,NULL,86400,true,'unknown','{"update_cadence":"daily","live_data":false}'::jsonb),
('wikidata','Wikidata','metadata','https://www.wikidata.org',ARRAY['entity_identity','aliases','founded','country','stadium','external_ids'],'CC0-1.0',false,NULL,'https://www.wikidata.org',false,false,false,NULL,NULL,0,true,'unknown','{"max_processing_seconds_per_minute":60,"max_concurrent_queries":5,"batch_queries":true}'::jsonb),
('wikimedia_commons','Wikimedia Commons','images','https://commons.wikimedia.org',ARRAY['licensed_images','logos','portraits','stadiums'],'per_file_license',true,'Wikimedia Commons','https://commons.wikimedia.org',false,false,true,NULL,NULL,1,true,'unknown','{"verify_license_per_asset":true,"allowed_licenses":["CC0","CC-BY","CC-BY-SA","Public domain"]}'::jsonb),
('statsbomb_open_data','StatsBomb Open Data','research','https://github.com/hudl/open-data',ARRAY['historical_events','lineups','advanced_analysis'],'research_terms_pending',true,'StatsBomb','https://github.com/hudl/open-data',false,true,true,NULL,NULL,NULL,false,'disabled','{"production_enabled":false,"reason":"commercial/public app rights not yet confirmed"}'::jsonb)
ON CONFLICT (source_key) DO UPDATE SET
  display_name = EXCLUDED.display_name,
  source_type = EXCLUDED.source_type,
  base_url = EXCLUDED.base_url,
  data_domains = EXCLUDED.data_domains,
  license_code = EXCLUDED.license_code,
  attribution_required = EXCLUDED.attribution_required,
  attribution_text = EXCLUDED.attribution_text,
  attribution_url = EXCLUDED.attribution_url,
  share_alike_required = EXCLUDED.share_alike_required,
  asset_rights_review_required = EXCLUDED.asset_rights_review_required,
  commercial_use_review_required = EXCLUDED.commercial_use_review_required,
  requests_per_minute = EXCLUDED.requests_per_minute,
  requests_per_day = EXCLUDED.requests_per_day,
  min_interval_seconds = EXCLUDED.min_interval_seconds,
  enabled = EXCLUDED.enabled,
  quota_metadata = EXCLUDED.quota_metadata,
  updated_at = now();

ALTER TABLE public.korax_source_registry ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.korax_source_sync_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.korax_entities ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.korax_entity_sources ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.korax_entity_assets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.korax_entity_facts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.korax_external_matches ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.korax_match_source_links ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.korax_source_coverage ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.korax_entity_merge_candidates ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE
  public.korax_source_registry, public.korax_source_sync_runs, public.korax_entities,
  public.korax_entity_sources, public.korax_entity_assets, public.korax_entity_facts,
  public.korax_external_matches, public.korax_match_source_links, public.korax_source_coverage,
  public.korax_entity_merge_candidates
FROM PUBLIC, anon, authenticated;
GRANT USAGE ON SCHEMA public TO service_role;
GRANT USAGE ON SCHEMA extensions TO service_role;
GRANT ALL ON TABLE
  public.korax_source_registry, public.korax_source_sync_runs, public.korax_entities,
  public.korax_entity_sources, public.korax_entity_assets, public.korax_entity_facts,
  public.korax_external_matches, public.korax_match_source_links, public.korax_source_coverage,
  public.korax_entity_merge_candidates
TO service_role;
REVOKE ALL ON FUNCTION public.search_korax_entities(text, text[], integer) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.search_korax_entities(text, text[], integer) TO service_role;
REVOKE ALL ON FUNCTION public.korax_normalize_search(text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.korax_normalize_search(text) TO service_role;
