-- Add the conflict target used by the world-data ingestion function.
ALTER TABLE public.korax_entity_facts
  ADD CONSTRAINT korax_entity_facts_provider_fact_unique
  UNIQUE (entity_id, field_key, source_key, source_external_id);

COMMENT ON CONSTRAINT korax_entity_facts_provider_fact_unique
  ON public.korax_entity_facts
  IS 'Stable provider fact key used for idempotent updates from source sync.';
