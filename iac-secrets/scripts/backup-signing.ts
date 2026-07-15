import { randomUUID } from "node:crypto";
import { existsSync, readFileSync, statSync } from "node:fs";
import { basename, dirname, isAbsolute, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import {
  SecretsManagerClient,
  UpdateSecretCommand,
} from "@aws-sdk/client-secrets-manager";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(scriptDirectory, "../..");
const maxSecretBytes = 65_536;

type SigningProfile = "play-upload" | "staging";

function signingProfile(): SigningProfile {
  const configured = process.env.TOASTLIFT_SIGNING_PROFILE?.trim() || "play-upload";
  if (configured === "play-upload" || configured === "staging") return configured;
  throw new Error("TOASTLIFT_SIGNING_PROFILE must be play-upload or staging.");
}

function readDotEnv(file: string): Map<string, string> {
  if (!existsSync(file)) throw new Error(`Missing signing configuration: ${file}`);
  const entries = new Map<string, string>();
  for (const rawLine of readFileSync(file, "utf8").split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;
    const separator = line.indexOf("=");
    if (separator < 1) continue;
    const key = line.slice(0, separator).trim();
    const value = line.slice(separator + 1).trim().replace(/^"(.*)"$/, "$1");
    entries.set(key, value);
  }
  return entries;
}

function requiredSigningValue(values: Map<string, string>, profile: SigningProfile, suffix: string): string {
  const primary = profile === "play-upload" ? "TOASTLIFT_PLAY_UPLOAD" : "TOASTLIFT_STAGING";
  const value = values.get(`${primary}_${suffix}`)?.trim()
    || (profile === "play-upload" ? values.get(`TOASTLIFT_RELEASE_${suffix}`)?.trim() : undefined);
  if (!value) throw new Error(`Missing ${primary}_${suffix} in ${repositoryRoot}/.env`);
  return value;
}

async function main(): Promise<void> {
  const region = process.env.AWS_REGION || process.env.AWS_DEFAULT_REGION;
  if (!region) throw new Error("Set AWS_REGION or AWS_DEFAULT_REGION before backing up signing material.");

  const profile = signingProfile();
  const values = readDotEnv(resolve(repositoryRoot, ".env"));
  const configuredStoreFile = requiredSigningValue(values, profile, "STORE_FILE");
  const storeFile = isAbsolute(configuredStoreFile)
    ? configuredStoreFile
    : resolve(repositoryRoot, configuredStoreFile);
  if (!existsSync(storeFile)) throw new Error(`Keystore file does not exist: ${configuredStoreFile}`);

  const payload = JSON.stringify({
    schemaVersion: 1,
    // Keep the existing schema identifier until the physical Secrets Manager
    // secret is migrated. It contains the Play upload key, not an app-signing key.
    kind: profile === "play-upload"
      ? "toastlift-android-release-signing"
      : "toastlift-android-staging-signing",
    state: "backed-up",
    updatedAtUtc: new Date().toISOString(),
    keyStoreFileName: basename(storeFile),
    keyStoreBase64: readFileSync(storeFile).toString("base64"),
    storePassword: requiredSigningValue(values, profile, "STORE_PASSWORD"),
    keyAlias: requiredSigningValue(values, profile, "KEY_ALIAS"),
    keyPassword: requiredSigningValue(values, profile, "KEY_PASSWORD"),
  });

  const bytes = Buffer.byteLength(payload, "utf8");
  if (bytes > maxSecretBytes) {
    throw new Error(`Signing backup is ${bytes} bytes; AWS Secrets Manager secrets are limited to ${maxSecretBytes} bytes.`);
  }

  const secretName = process.env.TOASTLIFT_AWS_SECRET_NAME?.trim()
    || (profile === "staging" ? "/toastlift/android/staging-signing" : "/toastlift/android/release-signing");
  const client = new SecretsManagerClient({ region });
  await client.send(new UpdateSecretCommand({
    SecretId: secretName,
    SecretString: payload,
    ClientRequestToken: randomUUID(),
  }));

  // Never print the secret, signing passwords, aliases, or base64 keystore.
  console.log(`Backed up ${basename(storeFile)} to ${secretName} (${bytes} bytes).`);
  console.log(`Source keystore size: ${statSync(storeFile).size} bytes.`);
}

main().catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : "Unable to back up signing material.");
  process.exitCode = 1;
});
