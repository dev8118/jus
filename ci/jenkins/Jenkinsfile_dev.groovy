def config = [
  project: 'frontend',
  namespace: 'jlp',
  image: 'registry.saritasa.com/jlp/frontend:develop'
]

node ('docker') {
  try {
    stage('clean') {
      if ("${BUILD_CLEAN}" == "true") {
        cleanWs()
      }
    }
    stage('scm') {
      checkout(scm)
    }
    stage('build') {
      gitlabCommitStatus('build') {
        sh("docker build \
        --file ci/docker/Dockerfile \
        --rm \
        --build-arg ENVBUILD='public-dev' \
        --tag ${config.image} .")
      }
    }
    stage('registry') {
      gitlabCommitStatus('registry') {
        withCredentials([usernamePassword(credentialsId: 'jenkins-gitlab-token', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
          sh("docker login --username ${env.USERNAME} --password ${env.PASSWORD} registry.saritasa.com")
          sh("docker push ${config.image}")
        }
      }
    }
    stage('deploy') {
      gitlabCommitStatus('deploy') {
        withKubeConfig(credentialsId: 'saritasa-k8s-develop-token', serverUrl: "${K8S_DEVELOPMENT_URL}") {
          sh("helm --namespace ${config.namespace} upgrade --cleanup-on-fail --install ${config.project} ci/chart/develop/")
        }
      }
    }
  } catch (error) {
    currentBuild.result = 'FAILURE'
    println("ERROR: $error")
    emailext(
      subject: "Build - ${currentBuild.currentResult}: ${JOB_NAME}: ${BUILD_NUMBER}",
      body: "\
      <h1 style='text-align:center;color:#ecf0f1;background-color:#ff5252;border-color:#ff5252'>${currentBuild.currentResult}</h1>\
      <b>Job url</b>: ${BUILD_URL}<br>",
      recipientProviders: [requestor(), developers(), brokenBuildSuspects(), brokenTestsSuspects()]
    )
  }
}