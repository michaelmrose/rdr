clean :
	rm -f target/uberjar/*.jar rdr-*-standalone
install :
	cp rdr-*-standalone ~/bin/rdr
uberjar : clean
	lein uberjar
graal : uberjar
	${GRAALVM_HOME}/bin/native-image -H:+ReportUnsupportedElementsAtRuntime -Dgraal.CompilerConfiguration=economy -H:+ReportExceptionStackTraces --no-server --no-fallback -jar target/uberjar/rdr-*-standalone.jar
graalenterprise : uberjar
	/opt/graal/bin/native-image -H:+ReportUnsupportedElementsAtRuntime -Dgraal.CompilerConfiguration=enterprise -H:+ReportExceptionStackTraces --no-server --no-fallback -jar target/uberjar/rdr*-standalone.jar
build : graal install
release : graalenterprise install
