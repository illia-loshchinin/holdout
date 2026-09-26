# The crank as a machine (T094, research R12): `crank run && crank scripted run`
# every 10 minutes. Wraps the prebuilt JAR — build it first:
#
#   cd kotlin && ./gradlew :crank:jar && cd .. && fly deploy
#
# Keys arrive as Fly secrets (never in the image); scripts/crank-loop.sh writes
# them to files the crank reads. See fly.toml.
FROM eclipse-temurin:21-jre
WORKDIR /crank
COPY kotlin/crank/build/libs/crank.jar /crank/crank.jar
COPY scripts/crank-loop.sh /crank/crank-loop.sh
RUN chmod +x /crank/crank-loop.sh
CMD ["/crank/crank-loop.sh"]
