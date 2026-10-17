ALTER TABLE webhook_retry_jobs
    ADD COLUMN IF NOT EXISTS job_key VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS uq_webhook_retry_jobs_job_key
    ON webhook_retry_jobs (job_key);
