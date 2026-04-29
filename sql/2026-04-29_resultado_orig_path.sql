-- Migration: Add orig_path column to resultado
-- Run this manually in Supabase SQL Editor
-- Date: 2026-04-29
--
-- The Python worker now uploads two volumes per study to MinIO:
--   <estudio_id>_heatmap.nii.gz  -> stored in resultado.heatmap_path
--   <estudio_id>_orig.mgz        -> stored in resultado.orig_path  (NEW)
--
-- The backend serves both as proxy-streamed downloads so the frontend
-- (Niivue) can load them as base + overlay without talking to MinIO directly.

ALTER TABLE public.resultado
  ADD COLUMN IF NOT EXISTS orig_path TEXT;
