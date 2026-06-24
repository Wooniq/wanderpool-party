pipeline {
    agent any

    environment {
        HARBOR_REGISTRY = '192.168.200.3:18081'
        HARBOR_PROJECT = 'wanderpool'
        IMAGE_NAME = "${HARBOR_REGISTRY}/${HARBOR_PROJECT}/wanderpool-party"
        GITLAB_MAVEN_URL = 'http://192.168.200.3:18082/api/v4/groups/wanderpool/-/packages/maven'
        GITLAB_MAVEN_TOKEN_NAME = 'Private-Token'
        CI_METRICS_DIR = 'build/ci-metrics'
        GRADLE_USER_HOME = "${env.WORKSPACE}/.gradle"
    }

    stages {
        stage('Test') {
            steps {
                withCredentials([string(credentialsId: 'gitlab-ce-maven-token', variable: 'GITLAB_MAVEN_TOKEN')]) {
                    sh '''
                        mkdir -p "$GRADLE_USER_HOME"
                        printf "gitLabMavenUrl=%s\\ngitLabMavenTokenName=%s\\ngitLabMavenToken=%s\\n" "$GITLAB_MAVEN_URL" "$GITLAB_MAVEN_TOKEN_NAME" "$GITLAB_MAVEN_TOKEN" > "$GRADLE_USER_HOME/gradle.properties"
                        chmod +x ./gradlew
                        TEST_STARTED_AT="$(date -Iseconds)"
                        TEST_START_SECONDS="$(date +%s)"
                        ./gradlew clean test jacocoTestReport jacocoTestCoverageVerification --no-daemon
                        mkdir -p "$CI_METRICS_DIR"
                        TEST_END_SECONDS="$(date +%s)"
                        TEST_FINISHED_AT="$(date -Iseconds)"
                        TEST_DURATION_SECONDS="$((TEST_END_SECONDS - TEST_START_SECONDS))"
                        printf '{"tool":"jenkins","job":"test","build_number":"%s","commit_sha":"%s","branch":"%s","started_at":"%s","finished_at":"%s","duration_seconds":%s}\\n' "$BUILD_NUMBER" "${GIT_COMMIT:-unknown}" "${BRANCH_NAME:-unknown}" "$TEST_STARTED_AT" "$TEST_FINISHED_AT" "$TEST_DURATION_SECONDS" > "$CI_METRICS_DIR/test.json"
                    '''
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'build/test-results/test/TEST-*.xml'
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'build/reports/tests/test/**, build/reports/jacoco/test/html/**, build/ci-metrics/**'
                }
            }
        }

        stage('Docker Build Push') {
            steps {
                withCredentials([
                    string(credentialsId: 'gitlab-ce-maven-token', variable: 'GITLAB_MAVEN_TOKEN'),
                    usernamePassword(credentialsId: 'harbor-credential', usernameVariable: 'HARBOR_USERNAME', passwordVariable: 'HARBOR_PASSWORD')
                ]) {
                    sh '''
                        mkdir -p "$GRADLE_USER_HOME"
                        printf "gitLabMavenUrl=%s\\ngitLabMavenTokenName=%s\\ngitLabMavenToken=%s\\n" "$GITLAB_MAVEN_URL" "$GITLAB_MAVEN_TOKEN_NAME" "$GITLAB_MAVEN_TOKEN" > "$GRADLE_USER_HOME/gradle.properties"
                        chmod +x ./gradlew
                        ./gradlew clean bootJar -x test --no-daemon
                        mkdir -p "$CI_METRICS_DIR"

                        JAR_FILE="$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' | head -n 1)"
                        if [ -z "$JAR_FILE" ]; then
                            echo "No boot jar found in build/libs"
                            exit 1
                        fi

                        printf "%s" "$HARBOR_PASSWORD" | docker login "$HARBOR_REGISTRY" -u "$HARBOR_USERNAME" --password-stdin
                        IMAGE_TAG_SOURCE="${GIT_COMMIT:-$BUILD_NUMBER}"
                        IMAGE_TAG="$(printf "%s" "$IMAGE_TAG_SOURCE" | cut -c1-8)"
                        BUILD_STARTED_AT="$(date -Iseconds)"
                        BUILD_START_SECONDS="$(date +%s)"
                        docker build --build-arg JAR_FILE="$JAR_FILE" -t "$IMAGE_NAME:$IMAGE_TAG" -t "$IMAGE_NAME:latest" .
                        BUILD_END_SECONDS="$(date +%s)"
                        BUILD_FINISHED_AT="$(date -Iseconds)"
                        PUSH_STARTED_AT="$(date -Iseconds)"
                        PUSH_START_SECONDS="$(date +%s)"
                        docker push "$IMAGE_NAME:$IMAGE_TAG"
                        docker push "$IMAGE_NAME:latest"
                        PUSH_END_SECONDS="$(date +%s)"
                        PUSH_FINISHED_AT="$(date -Iseconds)"
                        BUILD_DURATION_SECONDS="$((BUILD_END_SECONDS - BUILD_START_SECONDS))"
                        PUSH_DURATION_SECONDS="$((PUSH_END_SECONDS - PUSH_START_SECONDS))"
                        TOTAL_DURATION_SECONDS="$((PUSH_END_SECONDS - BUILD_START_SECONDS))"
                        printf '{"tool":"jenkins","job":"docker-build-push","build_number":"%s","commit_sha":"%s","branch":"%s","image":"%s","image_tag":"%s","build_started_at":"%s","build_finished_at":"%s","build_duration_seconds":%s,"push_started_at":"%s","push_finished_at":"%s","push_duration_seconds":%s,"total_duration_seconds":%s}\\n' "$BUILD_NUMBER" "${GIT_COMMIT:-unknown}" "${BRANCH_NAME:-unknown}" "$IMAGE_NAME" "$IMAGE_TAG" "$BUILD_STARTED_AT" "$BUILD_FINISHED_AT" "$BUILD_DURATION_SECONDS" "$PUSH_STARTED_AT" "$PUSH_FINISHED_AT" "$PUSH_DURATION_SECONDS" "$TOTAL_DURATION_SECONDS" > "$CI_METRICS_DIR/docker.json"
                    '''
                }
            }
            post {
                always {
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'build/ci-metrics/**'
                }
            }
        }

        stage('Update GitOps Image Tag') {
            steps {
                withCredentials([
                    usernamePassword(credentialsId: 'wanderpool', usernameVariable: 'GITOPS_USERNAME', passwordVariable: 'GITOPS_PASSWORD')
                ]) {
                    sh '''
                        IMAGE_TAG_SOURCE="${GIT_COMMIT:-$BUILD_NUMBER}"
                        IMAGE_TAG="$(printf "%s" "$IMAGE_TAG_SOURCE" | cut -c1-8)"
                        GITOPS_DIR="$WORKSPACE/gitops"
                        GIT_ASKPASS_FILE="$WORKSPACE/.git-askpass"

                        rm -rf "$GITOPS_DIR" "$GIT_ASKPASS_FILE"
                        cat > "$GIT_ASKPASS_FILE" <<'EOF'
#!/bin/sh
case "$1" in
  *Username*) printf '%s\\n' "$GITOPS_USERNAME" ;;
  *Password*) printf '%s\\n' "$GITOPS_PASSWORD" ;;
  *) printf '\\n' ;;
esac
EOF
                        chmod +x "$GIT_ASKPASS_FILE"

                        GIT_ASKPASS="$GIT_ASKPASS_FILE" git clone "http://192.168.200.3:18082/wanderpool/wanderpool-gitops.git" "$GITOPS_DIR"
                        cd "$GITOPS_DIR"

                        git config user.name "jenkins"
                        git config user.email "jenkins@wanderpool.local"

                        ./scripts/update-image.sh party local "$IMAGE_TAG"

                        if git diff --quiet; then
                            echo "GitOps image tag is already up to date: $IMAGE_TAG"
                            exit 0
                        fi

                        git add apps/party/overlays/local/kustomization.yaml
                        git commit -m "chore: update party image tag to $IMAGE_TAG"
                        GIT_ASKPASS="$GIT_ASKPASS_FILE" git push origin main
                    '''
                }
            }
        }
    }
}
