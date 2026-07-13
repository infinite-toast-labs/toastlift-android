# ToastLift signing-material backup

This CDK app creates an AWS Secrets Manager secret named
`/toastlift/android/release-signing` with placeholder values only. The secret
uses `RETAIN` on stack deletion so signing material is not destroyed by a CDK
teardown.

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

Use a different secret name only if needed:

```bash
TOASTLIFT_AWS_SECRET_NAME=/toastlift/android/release-signing ./scripts/deploy.sh
```

## Back up the real Android signing material

After the placeholder exists, run this from `iac-secrets/`:

```bash
AWS_REGION=us-east-1 ./scripts/backup-release-signing.sh
```

The script reads only the ignored Android-root `.env` signing variables and the
ignored keystore file referenced by `TOASTLIFT_RELEASE_STORE_FILE`. It writes a
single JSON secret containing the base64 keystore and signing values directly
to Secrets Manager. It never writes a local export and never prints secret
material.

Do not place real values in CDK context, source files, shell arguments, or
CloudFormation parameters.
