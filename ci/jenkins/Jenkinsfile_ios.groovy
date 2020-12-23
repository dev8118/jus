def config = [
  deeplink_host_name: 'jlp-frontend.saritasa-hosting.com',
]

node('xcode') {
  NODE_HOME=tool(name: 'nodejs', type: 'nodejs')
  env.NODE_ENV = "development"
  env.DEVELOPER_DIR = "/Applications/Xcode1121.app/Contents/Developer"

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
      sh("cp ci/keychains/ios/com.saritasa.juslaw_InHouse.mobileprovision '/Users/Shared/Jenkins/Library/MobileDevice/Provisioning Profiles'")
    }
  }
  
  stage('build') {
    withEnv([
      "PATH=${NODE_HOME}/bin:${env.PATH}",
      "NODE_OPTIONS=--max-old-space-size=4096",
      "DEEPLINK_HOST_NAME=${config.deeplink_host_name}"]) {
      dir('development') {
        sh('npm install')
        sh('npm run lint -- mobile-app')
        sh('npx ionic cordova build ios --release --device --project=mobile-app --configuration=public-dev')
      }
    }
  }

  stage('deploy:firebase') {
    env.FIREBASE_APP = '1:912991333997:ios:7b5c91cdf53d3e62bf5964'
    env.FIREBASE_GROUP = 'JusLaw'
    sh("firebase \
    appdistribution:distribute \
    'development/platforms/ios/build/device/JusLaw.ipa' \
    --app ${FIREBASE_APP} \
    --groups '${FIREBASE_GROUP}'")
  }

  stage('deploy:shared-development') {
    sshagent(['jenkins-ssh-credential']) {
      sh("scp \
      -o StrictHostKeyChecking=no \
      -o UserKnownHostsFile=/dev/null \
      development/platforms/ios/build/device/JusLaw.ipa \
      justlaw@shared-dev.saritasa.io:/home/justlaw/web/jlp.saritasa-hosting.com/public_html/apps/osx.ipa")
    }
  }
}
