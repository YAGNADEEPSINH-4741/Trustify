-- SafeCall AI: Core Database Schema Migration
-- Defines verified businesses, user reports, personal whitelists, and dispatch verification events.

-- 1. Table: verified_businesses
-- Stores independently verified enterprise, healthcare, banking, and courier numbers.
CREATE TABLE IF NOT EXISTS public.verified_businesses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_name TEXT NOT NULL,
    normalized_phone_number TEXT UNIQUE NOT NULL,
    verification_status TEXT NOT NULL DEFAULT 'VERIFIED',
    verification_method TEXT NOT NULL,
    verified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    verification_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_verified_businesses_phone 
    ON public.verified_businesses(normalized_phone_number);

-- 2. Table: number_reports
-- Community and user reports for false spam recovery and suspicious impersonation.
CREATE TABLE IF NOT EXISTS public.number_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    normalized_phone_number TEXT NOT NULL,
    report_category TEXT NOT NULL CHECK (report_category IN (
        'INCORRECT_SPAM_CLASSIFICATION',
        'SUSPECTED_SPAM',
        'SUSPECTED_IMPERSONATION',
        'INCORRECT_BUSINESS_IDENTITY',
        'OTHER'
    )),
    report_description TEXT NOT NULL,
    reporter_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    moderation_status TEXT NOT NULL DEFAULT 'PENDING' CHECK (moderation_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_number_reports_phone 
    ON public.number_reports(normalized_phone_number);
CREATE INDEX IF NOT EXISTS idx_number_reports_created 
    ON public.number_reports(created_at DESC);

-- 3. Table: user_trusted_numbers
-- Cloud sync of personal trusted callers for authenticated accounts (strictly private).
CREATE TABLE IF NOT EXISTS public.user_trusted_numbers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    normalized_phone_number TEXT NOT NULL,
    custom_label TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_trusted_number UNIQUE(user_id, normalized_phone_number)
);

CREATE INDEX IF NOT EXISTS idx_user_trusted_user 
    ON public.user_trusted_numbers(user_id);

-- 4. Table: verification_events
-- Audit trail of verification queries and third-party dispatch confirmations.
CREATE TABLE IF NOT EXISTS public.verification_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    normalized_phone_number TEXT NOT NULL,
    verification_source TEXT NOT NULL,
    verification_result TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_verification_events_phone 
    ON public.verification_events(normalized_phone_number);
