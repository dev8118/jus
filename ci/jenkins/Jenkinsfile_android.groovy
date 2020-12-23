 import groovy.json.JsonSlurperClassic as JSON

def config = [
  vault: "https://vault.saritasa.io/v1/projects/jlp-frontend-develop",
  firebase_id: "1:912991333997:android:70740515a7a4a267bf5964",
  firebase_group: "JusLaw",
  keyStorePassword: '', // Will be obtained from vault.saritasa.io.
  deeplink_host_name: 'jlp-frontend.saritasa-hosting.com',
]

node("android") {
  JAVA_HOME=tool(name: 'jdk-latest', type: 'jdk')
  NODE_HOME=tool(name: 'nodejs', type: 'nodejs')
  GRADLE_HOME=tool(name: 'gradle-3.0', type: 'gradle')
  env.ENVIRONMENT = "dev"

  stage('clean-up') {
    if (env.BUILD_CLEAN == 'true') {
      cleanWs()
    }
  }

  stage('scm') {
    checkout(scm)
  }

  stage('credentials') {
    vaultResponse = httpRequest(
      customHeaders: [[maskValue: true, name: 'X-Vault-Token', value: "${env.VAULT_TOKEN}"]],
      url: config.vault
    )
    config.keyStorePassword = new JSON().parseText(vaultResponse.content).data['KeyStorPassword']
  }

  stage('build') {
    withEnv(["PATH=${JAVA_HOME}/bin:${GRADLE_HOME}/bin:${NODE_HOME}/bin:${env.PATH}", "DEEPLINK_HOST_NAME=${config.deeplink_host_name}"]) {
      dir ('development') {
        sh('npm install')
        sh('npm run lint -- mobile-app')
        sh("npx ionic cordova build android --release --project=mobile-app --configuration=public-dev -- -- --packageType=apk --storePassword=\"${config.keyStorePassword}\" --password=\"${config.keyStorePassword}\"")
      }
    }
  }

  stage('deploy-firebase') {
    sh("firebase \
      appdistribution:distribute \
      development/platforms/android/app/build/outputs/apk/release/app-release.apk \
      --app ${config.firebase_id} \
      --groups '${config.firebase_group}'")
  }

  stage('deploy:shared-development') {
    sshagent(['jenkins-ssh-credential']) {
      sh("scp \
      -o StrictHostKeyChecking=no \
      -o UserKnownHostsFile=/dev/null \
      development/platforms/android/app/build/outputs/apk/release/app-release.apk \
      justlaw@shared-dev.saritasa.io:/home/justlaw/web/jlp.saritasa-hosting.com/public_html/apps/android.apk")
    }
  }
}