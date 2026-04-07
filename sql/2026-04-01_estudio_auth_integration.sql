-- Migration: Add columns needed by the SWADE backend API
-- Run this manually in Supabase SQL Editor
-- Date: 2026-04-01

-- Add original_filename to estudio (stores the uploaded filename)
ALTER TABLE public.estudio
  ADD COLUMN IF NOT EXISTS original_filename VARCHAR(255);

-- Add error_message to estudio (stores processing error details)
ALTER TABLE public.estudio
  ADD COLUMN IF NOT EXISTS error_message TEXT;

-- Enforce one resultado per estudio
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'uq_resultado_estudio_id'
  ) THEN
    ALTER TABLE public.resultado
      ADD CONSTRAINT uq_resultado_estudio_id UNIQUE (estudio_id);
  END IF;
END
$$;
