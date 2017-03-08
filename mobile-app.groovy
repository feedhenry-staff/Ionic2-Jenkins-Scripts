node('rhmap-jenkins-slave') {
    stage('Checkout Code') {
        sh 'git config --global user.email "rhc-open-innovation-labs@redhat.com"'
        sh 'git config --global user.name "ci-bot"'
        git url: 'git@git.tom.redhatmobile.com:innovation/easiER-AG--Mobile-App.git', branch: 'develop'
        sh 'git checkout web-app-build'
        sh 'git merge develop --no-commit -X theirs'
    }
    stage('Build Web App') {
        sh 'npm install'
        sh 'npm run clean'
        sh 'npm run build --prod'
    }
    stage("Bump Mobile Version to ${MOBILE_APP_VERSION}") {
        sh "sed -r -i '/<widget/ s#version=\"([0-9]+).([0-9+]).([0-9]+)\"#version=\"${MOBILE_APP_VERSION}\"#' config.xml"
    }
    stage('Push Web App Build Result to Git Server') {
        isGitCommitNeeded = sh(
                script: 'git status --porcelain | wc -l',
                returnStdout: true
        ).trim()
        if ("${isGitCommitNeeded}" == '0') {
            echo "no git commits to be made"
        } else {
            sh 'git add --all www config.xml'
            sh "git commit -m 'Jenkins Job ${env.BUILD_NUMBER} for app version: ${MOBILE_APP_VERSION}'"
        }
        // might to merge changes outside of www, like to config.xml
        sh 'git push origin web-app-build -f'
    }
    stage('Prep RHMAP Client') {
        fhcTarget = readFile '/etc/secrets/fhc/target'
        fhcUser = readFile '/etc/secrets/fhc/user'
        fhcPassword = readFile '/etc/secrets/fhc/password'
        fhcIOSKeypass = readFile '/etc/secrets/fhc/ios-keypass'
        sh "fhc target ${fhcTarget}"
        sh "fhc login ${fhcUser} ${fhcPassword}"
    }
    stage('Build Mobile Clients') {
        parallel android: {
            env.ANDROID_DOWNLOAD_URL = sh(
                    script: "fhc build project=2agquyssx54uvo6npvmit626 app=2agquyre35navhghyn3vyqih environment=default cloud_app=2agquyudsnwu4a4c5jcvi5ga tag=${CLOUD_APP_CONNECTION_TAG} destination=android git-branch=web-app-build | sed -n -e '/Download URL/ s/Download URL: //p'",
                    returnStdout: true
            ).trim()
            if ( ANDROID_DOWNLOAD_URL.isEmpty() ){
                error("something went wrong with your android build in RHMAP. please check the logs in RHMAP.")
            }
        }, ios: {
            env.IOS_DOWNLOAD_URL = sh (
                    script: "fhc build project=2agquyssx54uvo6npvmit626 app=2agquyre35navhghyn3vyqih environment=default cloud_app=2agquyudsnwu4a4c5jcvi5ga tag=${CLOUD_APP_CONNECTION_TAG} destination=ios git-branch=web-app-build bundleId=pmjhydke4a4y465flwmzpfbp keypass=${fhcIOSKeypass} config=Release | sed -n -e '/Download URL/ s/Download URL: //; s/.zip?/.ipa?/p'",
                    returnStdout: true
            ).trim()
            if ( IOS_DOWNLOAD_URL.isEmpty() ){
                error("something went wrong with your iOS build in RHMAP. please check the logs in RHMAP.")
            }
        }, failFast: false
    }
}
node('OSX') {
    stage('Push iOS Client to TestFlight'){
        deleteDir()
        sh "curl -o user-app-${MOBILE_APP_VERSION}.ipa ${env.IOS_DOWNLOAD_URL}"
        sh '/usr/local/bin/fastlane pilot upload -u $FASTLANE_USER'
    }
}