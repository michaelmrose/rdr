clean :
	rm -f target/rdr rdr.tar.gz
install :
	cp target/rdr ~/bin/rdr
zip :
	apack rdr.tar.gz ~/bin/rdr
native-image :
	ionice -c 3 lein native-image
build : clean native-image install zip
