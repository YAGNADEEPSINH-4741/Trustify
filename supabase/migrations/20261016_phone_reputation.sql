-- Trustify: Community-Powered Phone Number Reputation Schema
-- Migration: 20261016_phone_reputation.sql

-- Enable UUID extension if not enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================================
-- 1. Table: phone_reputation (Aggregated Community Counts)
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.phone_reputation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_hash TEXT NOT NULL UNIQUE,
    spam_count INTEGER NOT NULL DEFAULT 0 CHECK (spam_count >= 0),
    not_spam_count INTEGER NOT NULL DEFAULT 0 CHECK (not_spam_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_phone_reputation_hash 
    ON public.phone_reputation(phone_hash);

-- ============================================================================
-- 2. Table: phone_reputation_votes (Individual User Votes)
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.phone_reputation_votes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_hash TEXT NOT NULL,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    vote TEXT NOT NULL CHECK (vote IN ('spam', 'not_spam')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_phone_user_vote UNIQUE(phone_hash, user_id)
);

CREATE INDEX IF NOT EXISTS idx_phone_reputation_votes_hash 
    ON public.phone_reputation_votes(phone_hash);
CREATE INDEX IF NOT EXISTS idx_phone_reputation_votes_user 
    ON public.phone_reputation_votes(user_id);

-- ============================================================================
-- 3. Row Level Security (RLS) Policies
-- ============================================================================
ALTER TABLE public.phone_reputation ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.phone_reputation_votes ENABLE ROW LEVEL SECURITY;

-- phone_reputation: Public read-only for looking up aggregated counts
DROP POLICY IF EXISTS "Public can view phone reputation" ON public.phone_reputation;
CREATE POLICY "Public can view phone reputation" 
    ON public.phone_reputation 
    FOR SELECT 
    USING (true);

-- Prevent direct client writes to phone_reputation (only modified via SECURITY DEFINER functions)
DROP POLICY IF EXISTS "Deny direct inserts to phone_reputation" ON public.phone_reputation;
CREATE POLICY "Deny direct inserts to phone_reputation" 
    ON public.phone_reputation 
    FOR INSERT 
    WITH CHECK (false);

DROP POLICY IF EXISTS "Deny direct updates to phone_reputation" ON public.phone_reputation;
CREATE POLICY "Deny direct updates to phone_reputation" 
    ON public.phone_reputation 
    FOR UPDATE 
    USING (false);

-- phone_reputation_votes: Users can only see and manage their own vote records
DROP POLICY IF EXISTS "Users can view own votes" ON public.phone_reputation_votes;
CREATE POLICY "Users can view own votes" 
    ON public.phone_reputation_votes 
    FOR SELECT 
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can insert own votes" ON public.phone_reputation_votes;
CREATE POLICY "Users can insert own votes" 
    ON public.phone_reputation_votes 
    FOR INSERT 
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can update own votes" ON public.phone_reputation_votes;
CREATE POLICY "Users can update own votes" 
    ON public.phone_reputation_votes 
    FOR UPDATE 
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- ============================================================================
-- 4. Transactional Voting Function: submit_phone_vote
-- Atomically handles new votes, vote flips (spam <-> not_spam), and idempotent duplicate votes
-- ============================================================================
CREATE OR REPLACE FUNCTION public.submit_phone_vote(
    p_phone_hash TEXT,
    p_vote TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
    v_existing_vote TEXT;
    v_spam_delta INT := 0;
    v_not_spam_delta INT := 0;
    v_record public.phone_reputation%ROWTYPE;
    v_classification TEXT;
    c_spam_threshold CONSTANT INT := 10;
BEGIN
    -- Authenticate caller
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required to submit caller feedback';
    END IF;

    -- Validate vote input
    IF p_vote NOT IN ('spam', 'not_spam') THEN
        RAISE EXCEPTION 'Invalid vote classification: %', p_vote;
    END IF;

    IF p_phone_hash IS NULL OR length(trim(p_phone_hash)) = 0 THEN
        RAISE EXCEPTION 'Invalid phone hash';
    END IF;

    -- Check if user has already voted for this phone_hash
    SELECT vote INTO v_existing_vote
    FROM public.phone_reputation_votes
    WHERE phone_hash = p_phone_hash AND user_id = v_user_id
    FOR UPDATE;

    IF v_existing_vote IS NULL THEN
        -- Brand new vote
        INSERT INTO public.phone_reputation_votes (phone_hash, user_id, vote, created_at, updated_at)
        VALUES (p_phone_hash, v_user_id, p_vote, now(), now());

        IF p_vote = 'spam' THEN
            v_spam_delta := 1;
        ELSE
            v_not_spam_delta := 1;
        END IF;

    ELSIF v_existing_vote = p_vote THEN
        -- Duplicate idempotent vote: No count changes
        v_spam_delta := 0;
        v_not_spam_delta := 0;
    ELSE
        -- Enforce one-final-vote rule: disallow changing an already recorded final vote
        RAISE EXCEPTION 'A final vote has already been recorded for this number by this user and cannot be modified';
    END IF;

    -- Upsert aggregate phone_reputation atomically
    INSERT INTO public.phone_reputation (phone_hash, spam_count, not_spam_count, created_at, updated_at)
    VALUES (
        p_phone_hash, 
        GREATEST(0, v_spam_delta), 
        GREATEST(0, v_not_spam_delta), 
        now(), 
        now()
    )
    ON CONFLICT (phone_hash) DO UPDATE
    SET 
        spam_count = GREATEST(0, public.phone_reputation.spam_count + v_spam_delta),
        not_spam_count = GREATEST(0, public.phone_reputation.not_spam_count + v_not_spam_delta),
        updated_at = now()
    RETURNING * INTO v_record;

    -- Determine classification
    IF v_record.spam_count >= c_spam_threshold THEN
        v_classification := 'SPAM';
    ELSIF v_record.not_spam_count >= 10 AND v_record.spam_count <= 2 THEN
        v_classification := 'COMMUNITY_TRUSTED';
    ELSE
        v_classification := 'UNVERIFIED';
    END IF;

    RETURN jsonb_build_object(
        'phone_hash', v_record.phone_hash,
        'classification', v_classification,
        'spam_count', v_record.spam_count,
        'not_spam_count', v_record.not_spam_count,
        'threshold', c_spam_threshold,
        'user_vote', p_vote,
        'updated_at', v_record.updated_at
    );
END;
$$;

-- ============================================================================
-- 5. Reputation Lookup Function: get_phone_reputation
-- Returns current classification and counts without leaking user identities
-- ============================================================================
CREATE OR REPLACE FUNCTION public.get_phone_reputation(
    p_phone_hash TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_record public.phone_reputation%ROWTYPE;
    v_classification TEXT;
    c_spam_threshold CONSTANT INT := 10;
BEGIN
    SELECT * INTO v_record
    FROM public.phone_reputation
    WHERE phone_hash = p_phone_hash;

    IF NOT FOUND THEN
        RETURN jsonb_build_object(
            'phone_hash', p_phone_hash,
            'classification', 'UNVERIFIED',
            'spam_count', 0,
            'not_spam_count', 0,
            'threshold', c_spam_threshold
        );
    END IF;

    IF v_record.spam_count >= c_spam_threshold THEN
        v_classification := 'SPAM';
    ELSIF v_record.not_spam_count >= 10 AND v_record.spam_count <= 2 THEN
        v_classification := 'COMMUNITY_TRUSTED';
    ELSE
        v_classification := 'UNVERIFIED';
    END IF;

    RETURN jsonb_build_object(
        'phone_hash', v_record.phone_hash,
        'classification', v_classification,
        'spam_count', v_record.spam_count,
        'not_spam_count', v_record.not_spam_count,
        'threshold', c_spam_threshold,
        'updated_at', v_record.updated_at
    );
END;
$$;

-- ============================================================================
-- 6. Explicit Function Execution Permissions
-- ============================================================================
REVOKE ALL ON FUNCTION public.submit_phone_vote(TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.submit_phone_vote(TEXT, TEXT) TO authenticated;

REVOKE ALL ON FUNCTION public.get_phone_reputation(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_phone_reputation(TEXT) TO anon, authenticated;

