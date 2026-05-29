# Re-compile to catch the latest changes
./mvnw clean compile

# Invoked with 15GB memory. Change as you see fit.
MAVEN_OPTS="${MAVEN_OPTS} -Xmx15G" ./mvnw exec:java -Dexec.mainClass="OTF.OTFCommandLine" -Dexec.args="$*"
