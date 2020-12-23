import groovy.json.JsonSlurperClassic as JSON

def config = [
  vault: "https://vault.saritasa.io/v1/projects/jlp-frontend-develop",
  firebase_id: "1:972650587374:android:ebe9297ad6327e88907fff",
  firebase_group: "JusLaw",
  cordova_build_config_path: "artifacts/com.saritasa.juslaw.staging/build.json",
  keyStorePassword: '', // Will be obtained from vault.saritasa.io. TODO: Replace with Jenkins secret?
  deeplink_host_name: 'jlp-stage-frontend.saritasa-hosting.com',
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
  stage('build') {
    sh('cp ./artifacts/com.saritasa.juslaw.staging/google-services.json ./development')
    withEnv(["PATH=${JAVA_HOME}/bin:${GRADLE_HOME}/bin:${NODE_HOME}/bin:${env.PATH}", "DEEPLINK_HOST_NAME=${config.deeplink_host_name}"]) {
      dir ('development') {
        sh('npm install')
        sh("npx ionic cordova build android --device --release --prod --project=mobile-app \
        --buildConfig=../${config.cordova_build_config_path}  \
        --configuration=stage \
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
        --configuration=stage \
        -- -- --packageType=apk --storePassword=\"${config.keyStorePassword}\" \
        --password=\"${config.keyStorePassword}\"")
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

  stage('artifact-apk') {
    archiveArtifacts 'development/platforms/android/app/build/outputs/apk/release/app-release.apk'
  }
}
