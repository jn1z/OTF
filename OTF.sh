# If this gets annoying, you don't have to run this every time
./gradlew build customFatJar

# If you also want to run integration tests
# ./gradlew integTest

# Invoked with 15GB memory. Change as you see fit.
java -Xmx15000M -cp build/libs/OTFStandalone-1.1.0.jar OTF.OTFCommandLine "$@"

