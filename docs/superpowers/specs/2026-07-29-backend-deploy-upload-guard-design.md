# Backend Deployment Upload Guard Design

## Goal

Prevent simultaneous backend deployment runs from stalling or corrupting a shared temporary JAR upload.

## Cause

Two GitHub Actions backend jobs uploaded to the same `/tmp/GameSaves.jar.new` path at once. Their concurrent SFTP sessions stopped progressing while the application server, disk capacity, and JAR artifact remained healthy.

## Design

- Apply GitHub Actions concurrency only to `deploy-backend`, under the fixed `deploy-backend-production` group.
- Set `cancel-in-progress: true` so a newer backend deployment cancels an older one.
- Define a per-run `REMOTE_JAR` path using `github.run_id` and `github.run_attempt`.
- Upload and atomically move only that run's file, so a cancelled upload cannot be consumed by another run.
- Give the upload step a ten-minute limit, batch-mode authentication, a 20-second connection timeout, and SSH keepalives.

## Verification

- Parse the workflow YAML after editing.
- Confirm the upload and replacement commands both reference `${REMOTE_JAR}`.
- Confirm the backend job concurrency group and cancellation setting are present.
