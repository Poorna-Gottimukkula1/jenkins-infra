def call() {
    script {
        ansiColor('xterm') {
            echo ""
        }
        try {
            env.CLUSTER_ID=""
            env.SERVER_LIST=""
            def retries=0
            env.EXIT_STATUS=0
            bootstrap_reboot="failed to connect to the host via ssh: ssh: connect to host bootstrap"
            worker_reboot="failed to connect to the host via ssh: ssh: connect to host worker"
            master_reboot="failed to connect to the host via ssh: ssh: connect to host master"
            if (env.POWERVS == "false")
            {
               // Initial deployment try
               sh '''
                   set -x
                   cd ${WORKSPACE}/deploy
                   make $TARGET || EXIT_STATUS=$?
                   echo $EXIT_STATUS > ${WORKSPACE}/deploy/exit_status
               '''
               sleep 60
               env.EXIT_STATUS=sh(returnStdout: true, script: "cat \"${WORKSPACE}\"/deploy/exit_status").trim()
                while ( "${retries}" < 4 ) {
                    // Third try
                    if (  "${env.EXIT_STATUS}" != "0" ) {
                        env.EXIT_STATUS = "0"
                        env.CLUSTER_ID=sh(returnStdout: true, script: "cd \"${WORKSPACE}\"/deploy;make terraform:output TERRAFORM_DIR=.\"${TARGET}\" TERRAFORM_OUTPUT_VAR='cluster_id'").trim()
                        if ( env.CLUSTER_ID != "") {
                                def logContent = Jenkins.getInstance().getItemByFullName(env.JOB_NAME).getBuildByNumber(Integer.parseInt(env.BUILD_NUMBER)).logFile.text
                                def logContent_modified=logContent.toLowerCase()
                                env.SERVER_LIST=sh(returnStdout: true, script: "openstack server list --insecure | grep  \"${CLUSTER_ID}\" | grep -v 'bastion' | awk '{print \$4}'").trim()
                                if (logContent_modified.count(bootstrap_reboot) >= 1 && logContent_modified.count(master_reboot) == 0 && logContent_modified.count(worker_reboot) == 0) {
                                    sh(returnStdout: true, script: "echo \"${SERVER_LIST}\" | grep 'bootstrap'| while IFS= read -r line ; do openstack server reboot --insecure \$line; done || true").trim()
                                    sh(returnStdout: true, script: "echo \"${SERVER_LIST}\" | grep 'master'| while IFS= read -r line ; do openstack server reboot --insecure \$line; done || true").trim()
                                    sh(returnStdout: true, script: "echo \"${SERVER_LIST}\" | grep 'worker'| while IFS= read -r line ; do openstack server reboot --insecure \$line; done || true").trim()
                                    sleep 60
                                }
                                if (logContent_modified.count(bootstrap_reboot) >= 0 && logContent_modified.count(master_reboot) >= 1 && logContent_modified.count(worker_reboot) == 0) {
                                    sh(returnStdout: true, script: "echo \"${SERVER_LIST}\" | grep 'master'| while IFS= read -r line ; do openstack server reboot --insecure \$line; done || true").trim()
                                    sh(returnStdout: true, script: "echo \"${SERVER_LIST}\" | grep 'worker'| while IFS= read -r line ; do openstack server reboot --insecure \$line; done || true").trim()
                                    sleep 60
                                    }
                                if (logContent_modified.count(worker_reboot) >= 1) {
                                    sh(returnStdout: true, script: "echo \"${SERVER_LIST}\" | grep 'worker'| while IFS= read -r line ; do openstack server reboot --insecure \$line; done || true").trim()
                                    sleep 60
                                }
                        }
                        sh '''
                        set -x
                        EXIT_STATUS=0
                        cd ${WORKSPACE}/deploy
                        make $TARGET:redeploy|| EXIT_STATUS=$?
                        echo $EXIT_STATUS > ${WORKSPACE}/deploy/exit_status
                        '''
                        sleep 60
                        env.EXIT_STATUS=sh(returnStdout: true, script: "cat \"${WORKSPACE}\"/deploy/exit_status").trim()
                    }
                    retries = retries + 1
                    if ( "${env.EXIT_STATUS}" == "0" ) {
                        break
                    }
                } // While loop for retry the deployment ENDS
                if ( "${retries}" >= 4 && "${env.EXIT_STATUS}" != "0" ) {
                    error "Deployment Failed!"
                }
            } else { // If PowerVS and Script deployment
                sh '''
                    set -x
                    export CLOUD_API_KEY=$IBMCLOUD_API_KEY
                    cd ${WORKSPACE}/deploy
                    make $TARGET
                '''
            }
            if ( env.POWERVS == "true"  ) {
                if (env.SCRIPT_DEPLOYMENT == "true" ){
                    env.BASTION_IP=sh(returnStdout: true, script: "cd ${WORKSPACE}/deploy && make $TARGET:output TERRAFORM_OUTPUT_VAR=bastion_public_ip|grep -Eo '[0-9]{1,3}(\\.[0-9]{1,3}){3}'").trim()
                }
                else{
                    env.BASTION_IP=sh(returnStdout: true, script: "cd ${WORKSPACE}/deploy && make terraform:output TERRAFORM_DIR=.${TARGET} TERRAFORM_OUTPUT_VAR=bastion_public_ip|grep -Eo '[0-9]{1,3}(\\.[0-9]{1,3}){3}'").trim()
                }
            }
            else {
                sh '''#!/bin/bash
                    cd ${WORKSPACE}/deploy
                    OPENSHIFT_POWERVC_DEPLOY_DIR=".${TARGET}/"
                    TERRAFORM_VARS_FILE_POWERVC=".${TARGET}-abi.tfvars"
                    BOOT=$(grep '^bootstrap*' $OPENSHIFT_POWERVC_DEPLOY_DIR/$TERRAFORM_VARS_FILE_POWERVC);BOOT2="${BOOT//= 1/ = 0}"; sed -i -e "s|$BOOT|$BOOT2|g" $OPENSHIFT_POWERVC_DEPLOY_DIR/$TERRAFORM_VARS_FILE_POWERVC
                    make $TARGET:redeploy
                '''
                env.BASTION_IP=sh(returnStdout: true, script: "cd ${WORKSPACE}/deploy && make terraform:output TERRAFORM_DIR=.${TARGET} TERRAFORM_OUTPUT_VAR=bastion_ip | grep -Eo '[0-9]{1,3}(\\.[0-9]{1,3}){3}'").trim()
            }
            env.DEPLOYMENT_STATUS = true
        }
        catch (err) {
            env.FAILED_STAGE=env.STAGE_NAME
	        def timeout_sec=0
            def timeout_hrs =  env.WAIT_FOR_DEBUG.toInteger()
            if ( timeout_hrs != 0 ) {
                timeout_sec=timeout_hrs*60*60
            }
            echo "HOLDING THE Cluster FOR DEBUGGING, FOR $timeout_hrs Hours"
            sleep timeout_sec
            throw err
        }
    }
}
