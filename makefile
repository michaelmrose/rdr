clean :
	rm -f target/uberjar/*.jar rdr-*-standalone
uberjar : clean
	lein uberjar
graal : uberjar
	${GRAALVM_HOME}/bin/native-image -H:+ReportUnsupportedElementsAtRuntime -Dgraal.CompilerConfiguration=economy -H:+ReportExceptionStackTraces --no-server --no-fallback -jar target/uberjar/rdr-*-standalone.jar
graalenterprise :
	/opt/graal/bin/native-image -H:+ReportUnsupportedElementsAtRuntime -Dgraal.CompilerConfiguration=enterprise -H:+ReportExceptionStackTraces --no-server --no-fallback -jar target/uberjar/rdr*-standalone.jar
run :
	./rdr-*-standalone -q "title:clojure"
build : graal
release : graalenterprise
java : uberjar
	java -jar target/uberjar/rdr*SNAPSHOT-standalone.jar "authors:herbert"

