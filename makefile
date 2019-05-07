clean :
	rm -f target/uberjar/*.jar rdr-*-standalone rdr.tar.gz
install :
	cp rdr-*-standalone ~/bin/rdr
zip :
	apack rdr.tar.gz ~/bin/rdr
uberjar : clean
	lein uberjar
graal : uberjar
	${GRAALVM_HOME}/bin/native-image -H:+ReportUnsupportedElementsAtRuntime -Dgraal.CompilerConfiguration=economy -H:+ReportExceptionStackTraces --no-server --no-fallback -jar target/uberjar/rdr-*-standalone.jar
graalenterprise : uberjar
	/opt/graal/bin/native-image -H:+ReportUnsupportedElementsAtRuntime -Dgraal.CompilerConfiguration=enterprise -H:+ReportExceptionStackTraces --no-server --no-fallback -jar target/uberjar/rdr*-standalone.jar
build : graal install zip
release : graalenterprise install zip
