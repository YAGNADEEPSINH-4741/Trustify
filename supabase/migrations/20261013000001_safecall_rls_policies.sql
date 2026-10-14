-- SafeCall AI: Row Level Security (RLS) Policies Migration
-- Protects sensitive user reports, enforces personal data isolation,
-- and prevents unauthorized modification of verified enterprise records.

-- Enable RLS on all SafeCall tables
ALTER TABLE public.verified_businesses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.number_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_trusted_numbers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.verification_events ENABLE ROW LEVEL SECURITY;

-- 1. verified_businesses Policies
-- Anyone (anon or authenticated app users) can search verified enterprise records
CREATE POLICY "Public read access to verified businesses"
    ON public.verified_businesses
    FOR SELECT
    TO anon, authenticated
    USING (true);

-- Only administrative service_role can create, update, or revoke business verification
CREATE POLICY "Admin only write to verified businesses"
    ON public.verified_businesses
    FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

-- 2. number_reports Policies
-- App users (anon or authenticated) can submit reports
CREATE POLICY "Allow users to submit number reports"
    ON public.number_reports
    FOR INSERT
    TO anon, authenticated
    WITH CHECK (true);

-- Ordinary users cannot browse reports to protect reporter privacy and prevent scraping
CREATE POLICY "Admin only read for moderation on number reports"
    ON public.number_reports
    FOR SELECT
    TO service_role
    USING (true);

-- 3. user_trusted_numbers Policies
-- Authenticated users can only read their own private trusted numbers
CREATE POLICY "Users can view their own trusted numbers"
    ON public.user_trusted_numbers
    FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

-- Authenticated users can only insert their own trusted numbers
CREATE POLICY "Users can insert their own trusted numbers"
    ON public.user_trusted_numbers
    FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

-- Authenticated users can only update their own trusted numbers
CREATE POLICY "Users can update their own trusted numbers"
    ON public.user_trusted_numbers
    FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- Authenticated users can only delete their own trusted numbers
CREATE POLICY "Users can delete their own trusted numbers"
    ON public.user_trusted_numbers
    FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id);

-- 4. verification_events Policies
-- Public read access for active dispatch verification checks
CREATE POLICY "Public read access to verification events"
    ON public.verification_events
    FOR SELECT
    TO anon, authenticated
    USING (expires_at IS NULL OR expires_at > now());

-- Only authorized backend providers can log verification events
CREATE POLICY "Admin only insert to verification events"
    ON public.verification_events
    FOR INSERT
    TO service_role
    WITH CHECK (true);
