def call(String secret='PULL_SECRET'){
    withCredentials([string(credentialsId: secret, variable: 'FILE')]) {
        sh 'set +x; echo  $FILE > $PULL_SECRET_FILE'
    }
}
