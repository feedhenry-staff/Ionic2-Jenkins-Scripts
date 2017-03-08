node('rhmap-jenkins-slave'){
    stage ('Checkout Code'){
        sh 'git config --global user.email "rhc-open-innovation-labs@redhat.com"'
        sh 'git config --global user.name "ci-bot"'
        git url: 'git@git.tom.redhatmobile.com:innovation/Holmes-Test-ionic-hello-world.git', branch: 'master'
        sh 'git checkout web-app-build'
        sh 'git rebase master'
    }
    stage ('Build Web App'){
        sh 'npm install'
        sh 'npm run clean'
        sh 'npm run build --prod'
    }
    stage ('Push Web App Build Result to Git Server'){
        isGitCommitNeeded = sh (
                script: 'git status --porcelain | wc -l',
                returnStdout: true
        ).trim()
        if ( "${isGitCommitNeeded}" == '0' ){
            echo "no git commits to be made"
        } else {
            sh 'git add --all www'
            sh "git commit -m 'Jenkins Job ${env.BUILD_NUMBER} npm run build result'"
        }
        // might to merge changes outside of www, like to config.xml
        sh 'git push origin web-app-build -f'
    }
    stage ('Build Mobile Clients'){
        fhcTarget = readFile '/etc/secrets/fhc/target'
        fhcUser = readFile '/etc/secrets/fhc/user'
        fhcPassword = readFile '/etc/secrets/fhc/password'
        sh "fhc target ${fhcTarget}"
        sh "fhc login ${fhcUser} ${fhcPassword}"
        parallel android: {
            sh "fhc build project=gibfv3giclwnzfyczkpfob2b app=gibfv3blcmmcwxd2a33fvald environment=default cloud_app=gibfv3dtp423wrbvhhqjubxt tag=0.0.1 destination=android git-branch=web-app-build"
        }, ios: {
            sh "fhc build project=gibfv3giclwnzfyczkpfob2b app=gibfv3blcmmcwxd2a33fvald environment=default cloud_app=gibfv3dtp423wrbvhhqjubxt tag=0.0.1 destination=ios git-branch=web-app-build bundleId=3qdmddk6frtwba3b6nfmibwg keypass=password config=Distribution"
        }, failFast: false
    }
}