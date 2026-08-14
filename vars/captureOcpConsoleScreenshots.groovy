def call() {
    echo "=== captureOcpConsoleScreenshots STARTED ==="
    ansiColor('xterm') {
        echo ""
    }

    // Verify BASTION_IP is set before doing anything
    if (!env.BASTION_IP?.trim()) {
        echo "ERROR: BASTION_IP is not set — cannot capture screenshots"
        currentBuild.result = 'UNSTABLE'
        return
    }

    echo "BASTION_IP = ${env.BASTION_IP}"

    try {
        sh '''
            set -e

            echo "BASTION_IP=${BASTION_IP}"

            cd ${WORKSPACE}/deploy

            echo "=========================================="
            echo "Copying screenshot script to bastion"
            echo "=========================================="

            scp \
                -o StrictHostKeyChecking=no \
                -i id_rsa \
                ${WORKSPACE}/scripts/ocp-console-screenshot.py \
                root@${BASTION_IP}:/root/

            echo "=========================================="
            echo "Validating screenshot dependencies"
            echo "=========================================="

            set +e
            ssh \
                -o StrictHostKeyChecking=no \
                -i id_rsa \
                root@${BASTION_IP} "
                    echo '===== Dependency Check ====='
                    python3 --version
                    firefox --version
                    geckodriver --version
                    python3 -c 'import selenium; print(selenium.__version__)'
                    oc whoami --show-console
                    ls -l /root/openstack-upi/auth/kubeadmin-password
                "
            DEP_EXIT=$?
            set -e

            if [ $DEP_EXIT -ne 0 ]; then
                echo "WARNING: One or more dependencies missing"
            fi

            echo "=========================================="
            echo "Running screenshot collector"
            echo "=========================================="

            ssh \
                -o StrictHostKeyChecking=no \
                -i id_rsa \
                root@${BASTION_IP} \
                "python3 /root/ocp-console-screenshot.py 2>&1"

            echo "=========================================="
            echo "Copying screenshots back to workspace"
            echo "=========================================="

            scp \
                -o StrictHostKeyChecking=no \
                -i id_rsa \
                root@${BASTION_IP}:/root/ui-screenshots.tar.gz \
                ${WORKSPACE}/deploy/ui-screenshots.tar.gz

            ls -lah ${WORKSPACE}/deploy/ui-screenshots.tar.gz
            tar -tzf ${WORKSPACE}/deploy/ui-screenshots.tar.gz | head -20

            echo "Screenshot stage completed successfully"
        '''

    } catch(err) {

        echo "Screenshot capture failed — marking UNSTABLE"
        echo err.toString()

        sh '''
            set +e
            cd ${WORKSPACE}/deploy
            scp -o StrictHostKeyChecking=no -i id_rsa \
                root@${BASTION_IP}:/root/ui-screenshots.tar.gz \
                ${WORKSPACE}/deploy/ui-screenshots.tar.gz
            scp -o StrictHostKeyChecking=no -i id_rsa \
                root@${BASTION_IP}:/tmp/login_failure.png \
                ${WORKSPACE}/deploy/login_failure.png
            exit 0
        '''

        currentBuild.result = 'UNSTABLE'
    }
}