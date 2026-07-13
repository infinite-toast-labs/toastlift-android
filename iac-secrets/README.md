# ToastLift signing material and GitHub OIDC

This CDK app creates two retained AWS Secrets Manager placeholders and the
GitHub Actions identity that can read them:

| Purpose | Secret | GitHub environment / IAM role |
| --- | --- | --- |
| Production Play upload signer | `/toastlift/android/release-signing` | `production` / production signing role |
| Dedicated staging signer | `/toastlift/android/staging-signing` | `staging` / staging signing role |

Each role can perform only `secretsmanager:GetSecretValue` on its own secret.
Its OIDC trust policy permits only
`repo:infinite-toast-labs/toastlift-android:environment:<environment>`. CDK
uses `RETAIN`, so a stack teardown cannot delete a signer.

## Prerequisites

Configure AWS credentials using the normal AWS SDK credential chain and set a
region, for example:

```bash
export AWS_REGION=us-east-1
export AWS_PROFILE=your-profile
```

The identity needs permission to bootstrap/deploy CDK resources and update the
created Secrets Manager secret.

## Deploy the placeholder

```bash
cd iac-secrets
./scripts/deploy.sh
```

Override a name or the repository only when necessary:

```bash
TOASTLIFT_AWS_RELEASE_SECRET_NAME=/toastlift/android/release-signing \
TOASTLIFT_AWS_STAGING_SECRET_NAME=/toastlift/android/staging-signing \
TOASTLIFT_GITHUB_REPOSITORY=infinite-toast-labs/toastlift-android \
./scripts/deploy.sh
```

## Create the staging signer once

After deploy, create the staging key exactly once:

```bash
cd iac-secrets
AWS_REGION=us-east-1 ./scripts/create-staging-signing.sh
```

The script refuses to overwrite either a local staging JKS or a non-placeholder
staging secret. It generates a new 4096-bit JKS whose passwords never appear in
arguments or output, stores the backup directly in Secrets Manager, and keeps
the ignored local JKS under `keystore/`. It never uses the Play upload key.

## Back up existing signing material

After the placeholder exists, run this from `iac-secrets/`:

```bash
AWS_REGION=us-east-1 ./scripts/backup-release-signing.sh
```

The release script reads the ignored Android-root `.env` values prefixed
`TOASTLIFT_RELEASE_` and the referenced keystore. It writes a single JSON
secret containing the base64 keystore and signing values directly to Secrets
Manager. It never writes a local export and never prints secret material.

For an existing staging signer, use the equivalent `TOASTLIFT_STAGING_` values:

```bash
AWS_REGION=us-east-1 ./scripts/backup-staging-signing.sh
```

## Connect GitHub Actions

Copy the two role ARN outputs from `cdk deploy` into repository **variables**
(never secrets), then set:

| Variable | Value |
| --- | --- |
| `AWS_REGION` | AWS region, e.g. `us-east-1` |
| `AWS_STAGING_SIGNING_ROLE_ARN` | `GitHubActionsStagingSigningRoleArn` output |
| `AWS_PRODUCTION_SIGNING_ROLE_ARN` | `GitHubActionsProductionSigningRoleArn` output |

The workflow retrieves a secret only after the matching GitHub environment has
approved the job. It masks retrieved fields, writes the JKS to a restrictive
temporary directory, and removes it after the build.

Do not place real values in CDK context, source files, shell arguments,
CloudFormation parameters, or GitHub secrets.
