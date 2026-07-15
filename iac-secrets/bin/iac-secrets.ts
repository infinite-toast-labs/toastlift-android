#!/usr/bin/env node
import * as cdk from "aws-cdk-lib";
import { SigningBackupStack } from "../lib/signing-backup-stack.js";

const app = new cdk.App();
const playUploadSecretName = (app.node.tryGetContext("playUploadSecretName")
  || app.node.tryGetContext("releaseSecretName")) as string | undefined;
const stagingSecretName = app.node.tryGetContext("stagingSecretName") as string | undefined;
const githubRepository = app.node.tryGetContext("githubRepository") as string | undefined;

new SigningBackupStack(app, "ToastLiftSigningBackup", {
  playUploadSecretName: playUploadSecretName?.trim() || "/toastlift/android/release-signing",
  stagingSecretName: stagingSecretName?.trim() || "/toastlift/android/staging-signing",
  githubRepository: githubRepository?.trim() || "infinite-toast-labs/toastlift-android",
});
