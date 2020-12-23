def config = [
  deeplink_host_name: 'jlp-stage-frontend.saritasa-hosting.com',
]

node('xcode') {
  NODE_HOME=tool(name: 'nodejs', type: 'nodejs')
  env.NODE_ENV = "development"
  env.DEVELOPER_DIR = "/Applications/Xcode1121.app/Contents/Developer"

  cordova_build_config_path = "artifacts/com.saritasa.juslaw.staging"

  stage('clean-up') {
    if (env.BUILD_CLEAN == 'true') {
      cleanWs()
    }
  }
  
  stage('SCM') {
    checkout(scm)
  }
  
  stage('keychain') {
    withCredentials([usernamePassword(credentialsId: 'keychain-xcode', passwordVariable: 'PASSWORD', usernameVariable: '')]) {
      sh("security unlock-keychain -p ${PASSWORD} xcode.keychain")
      sh("security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k '${PASSWORD}' xcode.keychain")
      // TODO: Add copying of Provision Profile for com.jusglobal.jus-law.
      sh("cp ci/keychains/ios/comsaritasajuslawstaginginhouse.mobileprovision '/Users/Shared/Jenkins/Library/MobileDevice/Provisioning Profiles'")
    }
  }
  
  stage('build') {
    sh("cp ./${cordova_build_config_path}/GoogleService-Info.plist ./development")
    withEnv(["PATH=${NODE_HOME}/bin:${env.PATH}", "DEEPLINK_HOST_NAME=${config.deeplink_host_name}", "NODE_OPTIONS=--max-old-space-size=4096"]) {
      dir('development') {
        sh('npm install')
        sh("npm run ionic -- cordova build ios \
          --release --device --prod \
          --configuration=stage \
          --project=mobile-app \
          --buildConfig=../${cordova_build_config_path}/build.json")
      }
    }
  }

  stage('deploy:firebase') {
    env.FIREBASE_APP = '1:972650587374:ios:9ea35cc7d98a6dbf907fff'
    env.FIREBASE_GROUP = 'JusLaw'
    sh("firebase \
    appdistribution:distribute \
    'development/platforms/ios/build/device/JusLaw.ipa' \
    --app ${FIREBASE_APP} \
    --groups '${FIREBASE_GROUP}'")
  }

  stage('artifact') {
    archiveArtifacts 'development/platforms/ios/build/device/JusLaw.ipa'
  }
}
