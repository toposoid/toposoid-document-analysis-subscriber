FROM toposoid/toposoid-scala-lib:0.6-SNAPSHOT

WORKDIR /app
ARG TARGET_BRANCH
ENV DEPLOYMENT=local

RUN apt-get update \
&& apt-get -y install git \
&& git clone https://github.com/toposoid/toposoid-document-analysis-subscriber.git \
&& cd toposoid-document-analysis-subscriber \
&& git pull \
&& git fetch origin ${TARGET_BRANCH} \
&& git checkout ${TARGET_BRANCH} \
&& sbt publishLocal \
&& rm -Rf ./target


COPY ./docker-entrypoint.sh /app/
ENTRYPOINT ["/app/docker-entrypoint.sh"]
