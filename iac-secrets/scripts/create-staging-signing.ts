import { randomBytes, randomUUID } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, rmSync, statSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { spawn } from "node:child_process";
import {
  GetSecretValueCommand,
  SecretsManagerClient,
  UpdateSecretCommand,
} from "@aws-sdk/client-secrets-manager";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(scriptDirectory, "../..");
const secretName = process.env.TOASTLIFT_AWS_SECRET_NAME?.trim() || "/toastlift/android/staging-signing";
const maximumSecretBytes = 65_536;

function requireRegion(): string {
  const region = process.env.AWS_REGION || process.env.AWS_DEFAULT_REGION;
  if (!region) throw new Error("Set AWS_REGION or AWS_DEFAULT_REGION before creating the staging signer.");
  return region;
}

function secretIsPlaceholder(secretString: string | undefined): boolean {
  if (!secretString) return false;
  try {
    const secret = JSON.parse(secretString) as { kind?: string; state?: string };
    return secret.kind === "toastlift-android-staging-signing" && secret.state === "placeholder";
  } catch {
    return false;
  }
}

async function generateJks(keystore: string, password: string): Promise<void> {
  await new Promise<void>((resolvePromise, reject) => {
    const process = spawn("keytool", [
      "-genkeypair",
      "-alias", "toastlift-staging",
      "-keyalg", "RSA",
      "-keysize", "4096",
      "-validity", "3650",
      "-storetype", "JKS",
      "-keystore", keystore,
      "-dname", "CN=ToastLift Staging, OU=Mobile, O=Infinite Toast Labs, C=US",
    ], { stdio: ["pipe", "ignore", "pipe"] });
    let diagnostics = "";
    process.stderr.on("data", (chunk: Buffer) => { diagnostics += chunk.toString(); });
    process.once("error", () => reject(new Error("Unable to start keytool; install a JDK and retry.")));
    process.once("exit", (code) => {
      if (code === 0) resolvePromise();
      else reject(new Error(`keytool did not create the staging keystore (exit ${code ?? "unknown"}): ${diagnostics.trim()}`));
    });
    // Passwords are supplied on stdin, never an argument list or log.
    process.stdin.end(`${password}\n${password}\n\n`);
  });
}

async function main(): Promise<void> {
  const region = requireRegion();
  const configuredPath = process.env.TOASTLIFT_STAGING_KEYSTORE_PATH?.trim();
  const keystore = configuredPath
    ? resolve(repositoryRoot, configuredPath)
    : resolve(repositoryRoot, "keystore/toastlift-staging.jks");
  if (existsSync(keystore)) {
    throw new Error(`Refusing to overwrite existing staging keystore: ${keystore}`);
  }

  const client = new SecretsManagerClient({ region });
  const existing = await client.send(new GetSecretValueCommand({ SecretId: secretName }));
  if (!secretIsPlaceholder(existing.SecretString)) {
    throw new Error(`Refusing to replace non-placeholder staging secret: ${secretName}`);
  }

  mkdirSync(dirname(keystore), { recursive: true, mode: 0o700 });
  const password = randomBytes(32).toString("base64url");
  try {
    await generateJks(keystore, password);
    const payload = JSON.stringify({
      schemaVersion: 1,
      kind: "toastlift-android-staging-signing",
      state: "backed-up",
      updatedAtUtc: new Date().toISOString(),
      keyStoreFileName: "toastlift-staging.jks",
      keyStoreBase64: readFileSync(keystore).toString("base64"),
      storePassword: password,
      keyAlias: "toastlift-staging",
      keyPassword: password,
    });
    if (Buffer.byteLength(payload, "utf8") > maximumSecretBytes) {
      throw new Error("Generated staging signing secret exceeds the AWS Secrets Manager size limit.");
    }
    await client.send(new UpdateSecretCommand({
      SecretId: secretName,
      SecretString: payload,
      ClientRequestToken: randomUUID(),
    }));
  } catch (error) {
    rmSync(keystore, { force: true });
    throw error;
  }

  console.log(`Created and backed up dedicated staging keystore (${statSync(keystore).size} bytes) to ${secretName}.`);
}

main().catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : "Unable to create the staging signer.");
  process.exitCode = 1;
});
