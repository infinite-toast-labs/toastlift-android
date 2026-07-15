import * as cdk from "aws-cdk-lib";
import { RemovalPolicy, SecretValue } from "aws-cdk-lib";
import * as iam from "aws-cdk-lib/aws-iam";
import * as secretsmanager from "aws-cdk-lib/aws-secretsmanager";
import { Construct } from "constructs";

export interface SigningBackupStackProps extends cdk.StackProps {
  playUploadSecretName: string;
  stagingSecretName: string;
  githubRepository: string;
}

/**
 * Creates only a placeholder. Real signing material is deliberately written by
 * scripts/backup-signing.ts after deployment, never via CDK context or
 * CloudFormation parameters.
 */
export class SigningBackupStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: SigningBackupStackProps) {
    super(scope, id, props);

    const createSigningSecret = (id: string, secretName: string, kind: "release" | "staging") =>
      new secretsmanager.Secret(this, id, {
        secretName,
        description: `ToastLift Android ${kind} signing material. Replace the placeholder only with the local backup script.`,
        removalPolicy: RemovalPolicy.RETAIN,
        secretObjectValue: {
          schemaVersion: SecretValue.unsafePlainText("1"),
          kind: SecretValue.unsafePlainText(`toastlift-android-${kind}-signing`),
          state: SecretValue.unsafePlainText("placeholder"),
          keyStoreFileName: SecretValue.unsafePlainText("DUMMY.jks"),
          keyStoreBase64: SecretValue.unsafePlainText("DUMMY_BASE64_ONLY"),
          storePassword: SecretValue.unsafePlainText("DUMMY_DO_NOT_USE"),
          keyAlias: SecretValue.unsafePlainText("DUMMY_DO_NOT_USE"),
          keyPassword: SecretValue.unsafePlainText("DUMMY_DO_NOT_USE"),
        },
      });

    // The construct ID and secret schema retain their release-era names so a
    // routine CDK deploy never replaces the existing Play upload-key secret.
    const playUploadSigning = createSigningSecret(
      "AndroidReleaseSigning",
      props.playUploadSecretName,
      "release",
    );
    const stagingSigning = createSigningSecret(
      "AndroidStagingSigning",
      props.stagingSecretName,
      "staging",
    );

    const githubOidcProvider = new iam.OpenIdConnectProvider(this, "GitHubActionsOidc", {
      url: "https://token.actions.githubusercontent.com",
      clientIds: ["sts.amazonaws.com"],
    });

    const createGitHubSigningRole = (
      id: string,
      environment: "staging" | "production",
      secret: secretsmanager.ISecret,
    ) => {
      const role = new iam.Role(this, id, {
        description: `Read only the ToastLift ${environment} Android signing secret from the matching GitHub environment.`,
        assumedBy: new iam.WebIdentityPrincipal(githubOidcProvider.openIdConnectProviderArn, {
          StringEquals: {
            "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
            "token.actions.githubusercontent.com:sub": `repo:${props.githubRepository}:environment:${environment}`,
          },
        }),
      });
      role.addToPolicy(new iam.PolicyStatement({
        actions: ["secretsmanager:GetSecretValue"],
        resources: [secret.secretArn],
      }));
      return role;
    };

    const stagingRole = createGitHubSigningRole(
      "GitHubActionsStagingSigningRole",
      "staging",
      stagingSigning,
    );
    const productionRole = createGitHubSigningRole(
      "GitHubActionsProductionSigningRole",
      "production",
      playUploadSigning,
    );

    new cdk.CfnOutput(this, "PlayUploadSigningSecretName", {
      value: props.playUploadSecretName,
    });
    new cdk.CfnOutput(this, "StagingSigningSecretName", {
      value: props.stagingSecretName,
    });
    new cdk.CfnOutput(this, "GitHubActionsStagingSigningRoleArn", {
      value: stagingRole.roleArn,
    });
    new cdk.CfnOutput(this, "GitHubActionsProductionSigningRoleArn", {
      value: productionRole.roleArn,
    });
  }
}
