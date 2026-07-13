import * as cdk from "aws-cdk-lib";
import { RemovalPolicy, SecretValue } from "aws-cdk-lib";
import * as secretsmanager from "aws-cdk-lib/aws-secretsmanager";
import { Construct } from "constructs";

export interface SigningBackupStackProps extends cdk.StackProps {
  secretName: string;
}

/**
 * Creates only a placeholder. Real signing material is deliberately written by
 * scripts/backup-release-signing.ts after deployment, never via CDK context or
 * CloudFormation parameters.
 */
export class SigningBackupStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: SigningBackupStackProps) {
    super(scope, id, props);

    const signingBackup = new secretsmanager.Secret(this, "AndroidReleaseSigning", {
      secretName: props.secretName,
      description: "ToastLift Android signing-material backup. Replace the placeholder with the backup script.",
      removalPolicy: RemovalPolicy.RETAIN,
      secretObjectValue: {
        schemaVersion: SecretValue.unsafePlainText("1"),
        kind: SecretValue.unsafePlainText("toastlift-android-release-signing"),
        state: SecretValue.unsafePlainText("placeholder"),
        keyStoreFileName: SecretValue.unsafePlainText("DUMMY.jks"),
        keyStoreBase64: SecretValue.unsafePlainText("DUMMY_BASE64_ONLY"),
        storePassword: SecretValue.unsafePlainText("DUMMY_DO_NOT_USE"),
        keyAlias: SecretValue.unsafePlainText("DUMMY_DO_NOT_USE"),
        keyPassword: SecretValue.unsafePlainText("DUMMY_DO_NOT_USE"),
      },
    });

    new cdk.CfnOutput(this, "SigningBackupSecretName", {
      value: props.secretName,
    });
  }
}
