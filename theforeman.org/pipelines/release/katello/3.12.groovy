pipeline {
    agent none

    options {
        timestamps()
        timeout(time: 2, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    stages {
        stage('Mash Koji Repositories') {
            agent { label 'sshkey' }

            steps {

                mash("katello-mash-split-3.12.py")

            }
        }
        stage('Candlepin Repoclosure') {
            agent { label 'el' }

            steps {

                repoclosure('candlepin', 'el7')

            }
        }
        stage('Katello Repoclosure') {
            agent { label 'el' }

            steps {

                repoclosure('katello', 'el7')

            }
        }
        stage('Test Suites') {
            steps {
                script {
                    runCicoJobsInParallel([
                        ['name': 'Install test', 'job': 'foreman-katello-3.12-test'],
                        ['name': 'Upgrade test', 'job': 'foreman-katello-upgrade-3.12-test']
                    ])
                }
            }
        }
        stage('Push RPMs') {
            agent { label 'admin && sshkey' }

            steps {
                push_rpms_katello("3.12")
            }
        }
    }
}

void repoclosure(repo, dist, additions = []) {

    node('el') {
        git url: "https://github.com/theforeman/foreman-packaging", branch: "rpm/1.22", poll: false

        def command = [
            "./repoclosure.sh yum_${dist}.conf",
            "http://koji.katello.org/releases/yum/katello-3.12/${repo}/${dist}/x86_64/",
            "-l ${dist}-foreman-1.22",
            "-l ${dist}-foreman-plugins-1.22",
            "-l ${dist}-foreman-rails-1.22",
            "-l ${dist}-base",
            "-l ${dist}-updates",
            "-l ${dist}-epel",
            "-l ${dist}-extras",
            "-l ${dist}-scl",
            "-l ${dist}-puppet-6",
            "-l ${dist}-subscription-manager",
            "-l ${dist}-qpid",
            "-l ${dist}-katello-pulp-3.12",
            "-l ${dist}-katello-candlepin-3.12"
        ]

        command = command + additions

        dir('repoclosure') {
            sh command.join(" ")
        }

        deleteDir()
    }

}
