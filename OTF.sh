./gradlew build customFatJar
java -Xmx15000M -cp build/libs/OTFStandalone-1.1.0.jar OTF.OTFCommandLine "$@"

