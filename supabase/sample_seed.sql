-- SafeCall AI: Sample Seed Data for Development & Testing

INSERT INTO public.verified_businesses (business_name, normalized_phone_number, verification_status, verification_method, verified_at, verification_expires_at)
VALUES 
    ('HDFC Bank Customer Support', '+9118002026161', 'VERIFIED', 'RBI Corporate Telecom Registry', now() - interval '30 days', now() + interval '365 days'),
    ('Flipkart Customer Helpdesk', '+9118001088888', 'VERIFIED', 'TRAI Commercial Registry', now() - interval '60 days', now() + interval '365 days'),
    ('Amazon Logistics Dispatch Hub', '+91180030009009', 'VERIFIED', 'Official E-Commerce Transport Registry', now() - interval '10 days', now() + interval '365 days')
ON CONFLICT (normalized_phone_number) DO NOTHING;

INSERT INTO public.verification_events (normalized_phone_number, verification_source, verification_result, created_at, expires_at)
VALUES
    ('+919876543210', 'Amazon Logistics Dispatch API', 'ACTIVE_DELIVERY_CONFIRMED', now(), now() + interval '8 hours'),
    ('+919988776655', 'Uber Mobility Driver API', 'ACTIVE_RIDE_CONFIRMED', now(), now() + interval '2 hours')
ON CONFLICT DO NOTHING;
