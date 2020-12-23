import groovy.json.JsonSlurperClassic as JSON

def config = [
  vault: "https://vault.saritasa.io/v1/projects/jlp-frontend-develop",
  firebase_id: "1:912991333997:android:70740515a7a4a267bf5964",
  firebase_group: "JusLaw",
  cordova_build_config_path: "artifacts/com.jusglobal.juslaw/build.json",
  keyStorePassword: '', // Will be obtained from vault.saritasa.io. TODO: Replace with Jenkins secret?
  deeplink_host_name: 'app.jus-law.com',
]

node("android") {
  JAVA_HOME=tool(name: 'jdk-latest', type: 'jdk')
  NODE_HOME=tool(name: 'nodejs', type: 'nodejs')
  GRADLE_HOME=tool(name: 'gradle-3.0', type: 'gradle')

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

  // .aab for Play Market, .apk for firestore.
  stage('build-aab') {
    sh('cp ./artifacts/com.jusglobal.juslaw/google-services.json ./development')
    withEnv(["PATH=${JAVA_HOME}/bin:${GRADLE_HOME}/bin:${NODE_HOME}/bin:${env.PATH}", "BUILD_NUMBER=''", "DEEPLINK_HOST_NAME=${config.deeplink_host_name}"]) {
      dir ('development') {
        sh('npm install')
        sh("npx ionic cordova build android --device --release --prod --project=mobile-app \
        --buildConfig=../${config.cordova_build_config_path} \
        -- -- --packageType=bundle --storePassword=\"${config.keyStorePassword}\" \
        --password=\"${config.keyStorePassword}\"")
      }
    }
  }

  stage('artifact-aab') {
    archiveArtifacts 'development/platforms/android/app/build/outputs/bundle/release/app.aab'
  }

  stage('build-apk') {
    withEnv(["PATH=${JAVA_HOME}/bin:${GRADLE_HOME}/bin:${NODE_HOME}/bin:${env.PATH}", "BUILD_NUMBER=''"]) {
      dir ('development') {
        sh("npx ionic cordova build android --device --release --prod --project=mobile-app \
        --buildConfig=../${config.cordova_build_config_path}  \
        -- -- --packageType=apk --storePassword=\"${config.keyStorePassword}\" \
        --password=\"${config.keyStorePassword}\"")
      }
    }
  }

  stage('artifact-apk') {
    archiveArtifacts 'development/platforms/android/app/build/outputs/apk/release/app-release.apk'
  }
}
