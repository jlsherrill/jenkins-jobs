pipeline {
    agent none
    options {
        timeout(time: 1, unit: 'HOURS')
        ansiColor('xterm')
    }

    stages {
        stage('Test') {
            matrix {
                agent any
                axes {
                    axis {
                        name 'ruby'
                        values '2.4', '2.5', '2.7'
                    }
                    axis {
                        name 'PUPPET_VERSION'
                        values '5.0', '6.0', '7.0'
                    }
                }
                excludes {
                    exclude {
                        axis {
                            name 'ruby'
                            notValues '2.4'
                        }
                        axis {
                            name 'PUPPET_VERSION'
                            values '5.0'
                        }
                    }
                    exclude {
                        axis {
                            name 'ruby'
                            notValues '2.5'
                        }
                        axis {
                            name 'PUPPET_VERSION'
                            values '6.0'
                        }
                    }
                    exclude {
                        axis {
                            name 'ruby'
                            notValues '2.7'
                        }
                        axis {
                            name 'PUPPET_VERSION'
                            values '7.0'
                        }
                    }
                }
                stages {
                    stage('Setup Git Repos') {
                        steps {
                            ghprb_git_checkout()
                            sh "cp Gemfile Gemfile.${ruby}-${PUPPET_VERSION}"
                        }
                    }
                    stage("Setup RVM") {
                        steps {
                            configureRVM(ruby, "${ruby}-${PUPPET_VERSION}")
                        }
                    }
                    stage('Install dependencies') {
                        steps {
                            withRVM(["bundle install --gemfile=Gemfile.${ruby}-${PUPPET_VERSION}"], ruby, "${ruby}-${PUPPET_VERSION}")
                        }
                    }
                    stage('Run Rubocop') {
                        steps {
                            withRVM(["BUNDLE_GEMFILE=Gemfile.${ruby}-${PUPPET_VERSION} bundle exec rake rubocop TESTOPTS='-v' --trace"], ruby, "${ruby}-${PUPPET_VERSION}")
                        }
                    }
                    stage('Run Tests') {
                        steps {
                            withRVM(["BUNDLE_GEMFILE=Gemfile.${ruby}-${PUPPET_VERSION} bundle exec rake spec TESTOPTS='-v' --trace"], ruby, "${ruby}-${PUPPET_VERSION}")
                        }
                    }
                    stage('Test installer configuration') {
                        steps {
                            script {
                              install_dir = "${ruby}-${PUPPET_VERSION}"
                            }
                            withRVM(["BUNDLE_GEMFILE=Gemfile.${ruby}-${PUPPET_VERSION} bundle exec rake install PREFIX=${install_dir} --trace"], ruby, "${ruby}-${PUPPET_VERSION}")
                            withRVM(["BUNDLE_GEMFILE=Gemfile.${ruby}-${PUPPET_VERSION} bundle exec ${install_dir}/sbin/foreman-installer --help --scenario foreman --trace"], ruby, "${ruby}-${PUPPET_VERSION}")
                            withRVM(["BUNDLE_GEMFILE=Gemfile.${ruby}-${PUPPET_VERSION} bundle exec ${install_dir}/sbin/foreman-installer --help --scenario foreman-proxy-content --trace"], ruby, "${ruby}-${PUPPET_VERSION}")
                            withRVM(["BUNDLE_GEMFILE=Gemfile.${ruby}-${PUPPET_VERSION} bundle exec ${install_dir}/sbin/foreman-installer --help --scenario katello --trace"], ruby, "${ruby}-${PUPPET_VERSION}")
                            withRVM(["BUNDLE_GEMFILE=Gemfile.${ruby}-${PUPPET_VERSION} bundle exec ${install_dir}/sbin/foreman-proxy-certs-generate --help --trace"], ruby, "${ruby}-${PUPPET_VERSION}")
                            withRVM(["BUNDLE_GEMFILE=Gemfile.${ruby}-${PUPPET_VERSION} bundle exec ${install_dir}/sbin/foreman-proxy-certs-generate --help|grep -q certs-update-server"], ruby, "${ruby}-${PUPPET_VERSION}")
                        }
                    }
                }
                post {
                    always {
                        archiveArtifacts artifacts: "Gemfile*lock"
                        cleanupRVM(ruby, "${ruby}-${PUPPET_VERSION}")
                        deleteDir()
                    }
                }
            }
        }
    }
}
