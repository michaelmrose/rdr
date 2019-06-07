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
	${GRAALVM_HOME}/bin/native-image -H:+ReportUnsupportedElementsAtRuntime -Dgraal.CompilerConfiguration=enterprise -H:+ReportExceptionStackTraces --no-server --no-fallback -jar target/uberjar/rdr-*-standalone.jar --initialize-at-build-time 
build : graal install zip
release : graalenterprise install zip
remotedeploy :
	rsync --rsh=ssh -am --include '*.clj' --include='*/' --exclude='*' /home/michael/proj/clojure/rdr funtoo:/home/
remoteuberjar :
	ssh funtoo "cd /home/rdr;lein uberjar"
remotegraal :
	ssh funtoo /home/graalvm-ee-19.0.0/bin/native-image -H:+ReportUnsupportedElementsAtRuntime -Dgraal.CompilerConfiguration=enterprise -H:+ReportExceptionStackTraces --no-fallback -jar /home/rdr/target/uberjar/rdr-0.1.0-SNAPSHOT-standalone.jar --initialize-at-build-time
remotefetch :
	rsync --rsh=ssh -avz funtoo:/root/rdr-0.1.0-SNAPSHOT-standalone rdrext
remote : remotedeploy remoteuberjar remotegraal remotefetch
