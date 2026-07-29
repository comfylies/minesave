# Main Branch Migration Design

## Goal

Rename the repository's primary branch from `master` to `main` locally and on GitHub, while keeping automated deployment working.

## Scope

- Create and push `main` from the current `master` commit, preserving all history.
- Change GitHub's default branch to `main`.
- Update the deployment workflow to run on pushes to `main`.
- Remove the old remote `master` branch after `main` is the default branch.

## Safety

- Existing untracked workspace files are out of scope and will not be staged or changed.
- The workflow update is committed before the branch migration is finalized, so future pushes to `main` retain deployment behavior.
- The old remote branch is deleted only after GitHub accepts `main` as the default branch.

## Verification

- Local `main` tracks `origin/main`.
- GitHub reports `main` as the repository default branch.
- The deployment workflow's push filter names `main`.
- `origin/master` no longer exists after the migration.
