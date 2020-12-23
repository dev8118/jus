def config = [
  deeplink_host_name: 'app.jus-law.com',
]

node('xcode') {
  NODE_HOME=tool(name: 'nodejs', type: 'nodejs')
  env.NODE_ENV = "development"
  env.DEVELOPER_DIR = "/Applications/Xcode1121.app/Contents/Developer"

  cordova_build_config_path = "artifacts/com.jusglobal.jus-law/build.json"

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
      // sh("cp ci/keychains/ios/com.saritasa.juslaw_InHouse.mobileprovision '/Users/Shared/Jenkins/Library/MobileDevice/Provisioning Profiles'")
    }
  }
  
  stage('build') {
    sh('cp ./artifacts/com.jusglobal.jus-law/GoogleService-Info.plist ./development')
    withEnv(["PATH=${NODE_HOME}/bin:${env.PATH}",
    "NODE_OPTIONS=--max-old-space-size=4096",
    "BUILD_NUMBER=''",
    "DEEPLINK_HOST_NAME=${config.deeplink_host_name}"]) {
      dir('development') {
        sh('npm install')
        sh("npx ionic cordova build ios --release --device --prod --project=mobile-app --buildConfig=../${cordova_build_config_path}")
      }
    }
  }

  stage('artifact') {
    // TODO: 
    // archiveArtifacts 'path to ipa file'
  }
}
