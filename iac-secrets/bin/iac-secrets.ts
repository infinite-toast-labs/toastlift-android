#!/usr/bin/env node
import * as cdk from "aws-cdk-lib";
import { SigningBackupStack } from "../lib/signing-backup-stack.js";

const app = new cdk.App();
const secretName = app.node.tryGetContext("secretName") as string | undefined;

new SigningBackupStack(app, "ToastLiftSigningBackup", {
  secretName: secretName?.trim() || "/toastlift/android/release-signing",
});
