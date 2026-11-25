-- KORAX ten-source registry expansion.
-- Optional providers remain disabled until their credentials and publication rights are confirmed.

INSERT INTO public.korax_source_registry (
  source_key, display_name, source_type, base_url, data_domains, license_code,
  attribution_required, attribution_text, attribution_url, share_alike_required,
  asset_rights_review_required, commercial_use_review_required,
  requests_per_minute, requests_per_day, min_interval_seconds, enabled,
  health_status, quota_metadata
) VALUES
(
  'football_data_org',
  'Football-Data.org',
  'fixtures',
  'https://www.football-data.org/',
  ARRAY['fixtures','results','tables','competitions','teams'],
  'football-data-org-free-tier-terms',
  true,
  'Football data provided by the Football-Data.org API',
  'https://www.football-data.org/',
  false,
  true,
  true,
  10,
  NULL,
  7,
  false,
  'disabled',
  '{"free_tier_competitions":12,"delayed_scores":true,"free_request_limit_per_minute":10,"requires_private_token":true,"lineups_and_squads_included":false,"logos_and_profile_photos_require_separate_rights":true,"credentials_must_not_be_committed":true}'::jsonb
),
(
  'api_football',
  'API-Football',
  'live',
  'https://www.api-football.com/',
  ARRAY['fixtures','results','live_scores','events','lineups','standings','teams','players','coaches','transfers','injuries','statistics'],
  'api-football-terms-rights-review',
  false,
  NULL,
  'https://www.api-football.com/terms',
  false,
  true,
  true,
  10,
  100,
  7,
  false,
  'disabled',
  '{"free_requests_per_day":100,"free_requests_per_minute":10,"free_plan_seasons_limited":true,"public_app_publication_rights_granted_by_provider":false,"requires_rights_holder_permission":true,"enable_only_after_permission_and_private_key":true,"coverage_page_last_checked":"2026-11-23"}'::jsonb
),
(
  'wikipedia',
  'Wikipedia',
  'metadata',
  'https://www.wikipedia.org/',
  ARRAY['club_profiles','national_team_profiles','player_profiles','coach_profiles','competition_profiles','biographies','summaries'],
  'CC-BY-SA-4.0/GFDL',
  true,
  'Wikipedia contributors — link to the article and its applicable license',
  'https://www.wikipedia.org/',
  true,
  true,
  false,
  NULL,
  NULL,
  2,
  true,
  'unknown',
  '{"article_title_must_match_entity_exactly":true,"max_entities_per_daily_run":20,"summary_max_characters":1200,"text_license":"CC-BY-SA-4.0/GFDL","per_article_attribution_required":true,"media_files_not_imported":true}'::jsonb
)
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
  health_status = CASE WHEN public.korax_source_registry.health_status = 'healthy'
    AND EXCLUDED.source_key = 'wikipedia' THEN 'healthy' ELSE EXCLUDED.health_status END,
  quota_metadata = public.korax_source_registry.quota_metadata || EXCLUDED.quota_metadata,
  updated_at = now();

UPDATE public.korax_source_registry
SET enabled = false,
    health_status = 'disabled',
    last_error = 'The free tier does not permit app-store distribution. A paid key and publication permission are required before production synchronization.',
    last_error_at = now(),
    quota_metadata = quota_metadata || '{"production_enabled":false,"paid_key_required_for_public_app":true}'::jsonb,
    updated_at = now()
WHERE source_key = 'thesportsdb';

UPDATE public.korax_source_registry
SET requests_per_minute = 4,
    min_interval_seconds = 15,
    quota_metadata = quota_metadata || jsonb_build_object(
      'country_batch_size', 4,
      'max_requests_per_run', 30,
      'query_service_endpoint', 'https://query.wikidata.org/sparql',
      'query_timeout_seconds', 55,
      'query_service_policy', 'sequential requests; 15-second minimum interval'
    ),
    updated_at = now()
WHERE source_key = 'wikidata';
