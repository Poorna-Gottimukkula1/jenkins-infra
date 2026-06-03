FROM jenkins/inbound-agent:3355.v388858a_47b_33-21-jdk17
#FROM jenkins/inbound-agent:latest-jdk17
#FROM jenkins/inbound-agent:latest-jdk21
USER root
RUN mkdir -p /etc/ssl/certs/
RUN chmod 755 /etc/ssl/certs/
RUN sed -i 's|http://deb.debian.org|https://deb.debian.org|g' /etc/apt/sources.list.d/debian.sources
RUN apt-get update && apt install curl gnupg2 --yes \
        python3 \
        python3-pip \
        python3-venv \
        make \
        unzip \
        jq \
        pkg-config \
        wget \
        git \
        curl \
        sudo \
        rsync \
        dnsutils \
        python3-openstackclient \
        iproute2
RUN wget https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
    && apt install ./google-chrome-stable_current_amd64.deb -y \
    && rm -rf ./google-chrome-stable_current_amd64.deb \
    && rm -rf /var/lib/apt/lists/*
RUN wget https://download.clis.cloud.ibm.com/ibm-cloud-cli/2.43.1/IBM_Cloud_CLI_2.43.1_amd64.tar.gz \
        && tar -xvzf "./IBM_Cloud_CLI_2.43.1_amd64.tar.gz" \
        &&  ./Bluemix_CLI/install \
        && rm -rf "./IBM_Cloud_CLI_2.43.1_amd64.tar.gz" \
        && rm -rf "./Bluemix_CLI/" \
        && ibmcloud update -f \
        && ibmcloud plugin install cloud-object-storage -f \
        && ibmcloud plugin install container-service -f \
        && ibmcloud plugin install power-iaas -f
RUN wget  --quiet --no-check-certificate https://mirror.openshift.com/pub/openshift-v4/amd64/clients/ocp/latest/openshift-client-linux.tar.gz -O /tmp/oc.tar.gz \
    && tar zvxf /tmp/oc.tar.gz \
    && chmod +x oc \
    && mv oc kubectl /usr/local/bin/ \
    && rm -rf /tmp/oc.tar.gz
RUN python3 -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"
RUN pip3 install --upgrade pip; pip3 install ansible; pip3 install yq
ENV PATH $GOPATH/bin:/usr/local/go/bin:$PATH
RUN ssh-keygen -t rsa -N "" -f ~/.ssh/id_rsa
ENV VERSION="2.3.1"
RUN wget https://github.com/containerd/nerdctl/releases/download/v$VERSION/nerdctl-$VERSION-linux-amd64.tar.gz
RUN sudo tar zxvf  nerdctl-$VERSION-linux-amd64.tar.gz -C  /usr/local/bin/
RUN rm -f nerdctl-$VERSION-linux-amd64.tar.gz
COPY pvm.crt /etc/ssl/certs/pvm.crt
RUN /usr/sbin/update-ca-certificates
RUN echo "check_certificate = off" >> ~/.wgetrc
RUN sed -i '2 a ip link set dev eth0 mtu 1420' /usr/local/bin/jenkins-agent
