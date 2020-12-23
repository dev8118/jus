def config = [
  image: '665910599311.dkr.ecr.us-west-2.amazonaws.com/jlp:production-frontend',
  region: 'us-west-2',
  environment: 'production',
  ecsCluster: 'production',
  ecsService: 'frontend',
]

node {
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
      sh("docker build \
      --file ci/docker/Dockerfile \
      --no-cache=${BUILD_CLEAN} \
      --build-arg APP_ENV=${config.environment} \
      --build-arg ENVBUILD=${config.environment} \
      --rm --tag ${config.image} .")
    }
    stage('ecr') {
      sh("\$(aws ecr get-login --region ${config.region} --no-include-email)")
      sh("docker push ${config.image}")
    }
    stage('ecs') {
      sh("aws ecs update-service \
      --region ${config.region} \
      --cluster ${config.ecsCluster} \
      --service ${config.ecsService} \
      --force-new-deployment")
    }
  }
  catch (error) {
    println("ERROR: $error")
    currentBuild.result = 'FAILURE'
    emailext subject: 'Job jlp-frontend failure', body: '$BUILD_URL Job jlp-frontend failure', recipientProviders: [requestor()]
  }
}
