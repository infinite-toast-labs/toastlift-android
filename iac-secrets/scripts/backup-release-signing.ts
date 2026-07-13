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

type SigningProfile = "release" | "staging";

function signingProfile(): SigningProfile {
  const configured = process.env.TOASTLIFT_SIGNING_PROFILE?.trim() || "release";
  if (configured === "release" || configured === "staging") return configured;
  throw new Error("TOASTLIFT_SIGNING_PROFILE must be release or staging.");
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

function required(values: Map<string, string>, key: string): string {
  const value = values.get(key)?.trim();
  if (!value) throw new Error(`Missing ${key} in ${repositoryRoot}/.env`);
  return value;
}

async function main(): Promise<void> {
  const region = process.env.AWS_REGION || process.env.AWS_DEFAULT_REGION;
  if (!region) throw new Error("Set AWS_REGION or AWS_DEFAULT_REGION before backing up signing material.");

  const profile = signingProfile();
  const prefix = `TOASTLIFT_${profile.toUpperCase()}`;
  const values = readDotEnv(resolve(repositoryRoot, ".env"));
  const configuredStoreFile = required(values, `${prefix}_STORE_FILE`);
  const storeFile = isAbsolute(configuredStoreFile)
    ? configuredStoreFile
    : resolve(repositoryRoot, configuredStoreFile);
  if (!existsSync(storeFile)) throw new Error(`Keystore file does not exist: ${configuredStoreFile}`);

  const payload = JSON.stringify({
    schemaVersion: 1,
    kind: `toastlift-android-${profile}-signing`,
    state: "backed-up",
    updatedAtUtc: new Date().toISOString(),
    keyStoreFileName: basename(storeFile),
    keyStoreBase64: readFileSync(storeFile).toString("base64"),
    storePassword: required(values, `${prefix}_STORE_PASSWORD`),
    keyAlias: required(values, `${prefix}_KEY_ALIAS`),
    keyPassword: required(values, `${prefix}_KEY_PASSWORD`),
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
